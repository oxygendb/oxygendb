/**
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * <p>*
 */
package com.jetbrains.youtrack.db.internal.spatial.query;

import com.jetbrains.youtrack.db.internal.lucene.query.LuceneQueryContext;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import java.util.List;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial.query.SpatialArgs;

/**
 *
 */
public class SpatialQueryContext extends LuceneQueryContext {

  public SpatialArgs spatialArgs;

  public SpatialQueryContext(CommandContext context, IndexSearcher searcher, Query query) {
    super(context, searcher, query);
  }

  public SpatialQueryContext(
      CommandContext context, IndexSearcher searcher, Query query, List<SortField> sortFields) {
    super(context, searcher, query, sortFields);
  }

  public SpatialQueryContext setSpatialArgs(SpatialArgs spatialArgs) {
    this.spatialArgs = spatialArgs;
    return this;
  }
}
