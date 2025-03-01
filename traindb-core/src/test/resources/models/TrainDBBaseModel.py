"""
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
"""

import abc

class TrainDBModel(abc.ABC):
  """Base class for all the ``TrainDB`` models."""

  def train(self, real_data, table_metadata): 
    pass

  def save(self, output_path):
    pass

  def load(self, input_path):
    pass


class TrainDBSynopsisModel(TrainDBModel, abc.ABC):
  """Base class for all the ``TrainDB`` synopsis generation models."""

  def synopsis(self, row_count):
    pass
