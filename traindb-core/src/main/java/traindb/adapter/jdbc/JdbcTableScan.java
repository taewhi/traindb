/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package traindb.adapter.jdbc;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.RelHint;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of a table in a JDBC data source.
 */
public class JdbcTableScan extends TableScan implements JdbcRel {
  public final TrainDBJdbcTable jdbcTable;

  protected JdbcTableScan(
      RelOptCluster cluster,
      List<RelHint> hints,
      RelOptTable table,
      TrainDBJdbcTable jdbcTable,
      JdbcConvention jdbcConvention) {
    super(cluster, cluster.traitSetOf(jdbcConvention), hints, table);
    this.jdbcTable = Objects.requireNonNull(jdbcTable, "jdbcTable");
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    assert inputs.isEmpty();
    return new JdbcTableScan(
        getCluster(), getHints(), table, jdbcTable, (JdbcConvention) castNonNull(getConvention()));
  }

  @Override public JdbcImplementor.Result implement(
      JdbcImplementor implementor) {
    return implementor.result(jdbcTable.tableName(),
        ImmutableList.of(JdbcImplementor.Clause.FROM), this, null);
  }

  @Override public RelNode withHints(List<RelHint> hintList) {
    Convention convention = requireNonNull(getConvention(), "getConvention()");
    return new JdbcTableScan(getCluster(), hintList, getTable(), jdbcTable,
        (JdbcConvention) convention);
  }
}
