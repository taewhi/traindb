/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package traindb.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sql.DataSource;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Table;
import org.apache.calcite.tools.Frameworks;
import traindb.adapter.jdbc.TrainDBJdbcDataSource;
import traindb.catalog.CatalogStore;
import traindb.common.TrainDBLogger;


/**
 * Constructs in-memory schema from metadata in CatalogStore.
 */
public final class SchemaManager {
  private static TrainDBLogger LOG = TrainDBLogger.getLogger(SchemaManager.class);
  private static SchemaManager singletonInstance;
  private final CatalogStore catalogStore;
  private TrainDBJdbcDataSource traindbDataSource;

  private final Map<String, List<TrainDBDataSource>> dataSourceMap;
  private final Map<String, List<TrainDBSchema>> schemaMap;
  private final Map<String, List<TrainDBTable>> tableMap;

  // to synchronize requests for Calcite Schema
  private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();
  private SchemaPlus rootSchema;

  private SchemaManager(CatalogStore catalogStore) {
    this.catalogStore = catalogStore;
    rootSchema = Frameworks.createRootSchema(false);
    traindbDataSource = null;
    dataSourceMap = new HashMap<>();
    schemaMap = new HashMap<>();
    tableMap = new HashMap<>();

    try {
      Class.forName("traindb.engine.calcite.Driver");
    } catch (ClassNotFoundException e) {
      // FIXME
    }
  }

  public static SchemaManager getInstance(CatalogStore catalogStore) {
    if (singletonInstance == null) {
      assert catalogStore != null;
      singletonInstance = new SchemaManager(catalogStore);
    }
    return singletonInstance;
  }

  public void loadDataSource(DataSource dataSource) {
    SchemaPlus newRootSchema = Frameworks.createRootSchema(false);
    TrainDBJdbcDataSource newJdbcDataSource = new TrainDBJdbcDataSource(newRootSchema, dataSource);
    newRootSchema.add(newJdbcDataSource.getName(), newJdbcDataSource);
    addDataSourceToMaps(newJdbcDataSource);

    writeLock.lock();
    this.traindbDataSource = newJdbcDataSource;
    this.rootSchema = newRootSchema;
    writeLock.unlock();
  }

  public void refreshDataSource() {
    writeLock.lock();
    dataSourceMap.clear();
    schemaMap.clear();
    tableMap.clear();
    loadDataSource(traindbDataSource.getDataSource());
    writeLock.unlock();
  }

  private void addDataSourceToMaps(TrainDBDataSource traindbDataSource) {
    addToListMap(dataSourceMap, traindbDataSource.getName(), traindbDataSource);
    for (Schema schema : traindbDataSource.getSubSchemaMap().values()) {
      TrainDBSchema traindbSchema = (TrainDBSchema) schema;
      addToListMap(schemaMap, traindbSchema.getName(), traindbSchema);
      for (Table table : traindbSchema.getTableMap().values()) {
        TrainDBTable traindbTable = (TrainDBTable) table;
        addToListMap(tableMap, traindbTable.getName(), traindbTable);
      }
    }
  }

  private <T> void addToListMap(Map<String, List<T>> map, String key, T value) {
    List<T> values = map.get(key);
    if (values == null) {
      values = new ArrayList<>();
      map.put(key, values);
    }
    values.add(value);
  }

  public SchemaPlus getCurrentSchema() {
    SchemaPlus currentSchema;
    readLock.lock();
    currentSchema = rootSchema;
    readLock.unlock();
    return currentSchema;
  }

  /*
   * lockRead()/unlockRead() are used to protect rootSchema returned by
   * getCurrentSchema() because we don't know how long the schema will be used
   */
  public void lockRead() {
    readLock.lock();
  }

  public void unlockRead() {
    readLock.unlock();
  }

  public List<String> toFullyQualifiedTableName(List<String> names, String defaultSchema) {
    TrainDBDataSource dataSource = null;
    TrainDBSchema schema = null;
    TrainDBTable table = null;

    List<TrainDBDataSource> candidateDataSources;
    List<TrainDBSchema> candidateSchemas;

    switch (names.size()) {
      case 1: // table
        candidateSchemas = schemaMap.get(defaultSchema);
        if (candidateSchemas == null || candidateSchemas.size() != 1) {
          throw new RuntimeException("invalid name: " + defaultSchema + "." + names.get(0));
        }
        schema = candidateSchemas.get(0);
        table = (TrainDBTable) schema.getTable(names.get(0));
        if (table == null) {
          throw new RuntimeException("invalid name: " + defaultSchema + "." + names.get(0));
        }
        dataSource = schema.getDataSource();
        break;
      case 2: // schema.table
        candidateSchemas = schemaMap.get(names.get(0));
        if (candidateSchemas == null || candidateSchemas.size() != 1) {
          throw new RuntimeException("invalid name: " + names.get(0) + "." + names.get(1));
        }
        schema = candidateSchemas.get(0);
        table = (TrainDBTable) schema.getTable(names.get(1));
        if (table == null) {
          throw new RuntimeException("invalid name: " + names.get(0) + "." + names.get(1));
        }
        dataSource = schema.getDataSource();
        break;
      case 3: // dataSource.schema.table
        candidateDataSources = dataSourceMap.get(names.get(0));
        if (candidateDataSources == null || candidateDataSources.size() != 1) {
          throw new RuntimeException(
              "invalid name: " + names.get(0) + "." + names.get(1) + "." + names.get(2));
        }
        dataSource = candidateDataSources.get(0);
        schema = (TrainDBSchema) dataSource.getSubSchemaMap().get(names.get(1));
        if (schema == null) {
          throw new RuntimeException(
              "invalid name: " + names.get(0) + "." + names.get(1) + "." + names.get(2));
        }
        table = (TrainDBTable) schema.getTable(names.get(2));
        if (table == null) {
          throw new RuntimeException(
              "invalid name: " + names.get(0) + "." + names.get(1) + "." + names.get(2));
        }
        break;
      default:
        throw new RuntimeException("invalid identifier length: " + names.size());
    }

    List<String> fqn = new ArrayList<>();
    fqn.add(dataSource.getName());
    fqn.add(schema.getName());
    fqn.add(table.getName());
    return fqn;
  }
}
