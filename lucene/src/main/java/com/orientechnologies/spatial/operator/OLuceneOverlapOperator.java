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
package com.orientechnologies.spatial.operator;

import com.orientechnologies.common.util.ORawPair;
import com.orientechnologies.core.command.OCommandContext;
import com.orientechnologies.core.db.record.YTIdentifiable;
import com.orientechnologies.core.id.YTRID;
import com.orientechnologies.core.index.OIndex;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.serialization.serializer.record.binary.ODocumentSerializer;
import com.orientechnologies.core.sql.filter.OSQLFilterCondition;
import com.orientechnologies.spatial.collections.OSpatialCompositeKey;
import com.orientechnologies.spatial.strategy.SpatialQueryBuilderAbstract;
import com.orientechnologies.spatial.strategy.SpatialQueryBuilderOverlap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.locationtech.spatial4j.shape.Shape;

public class OLuceneOverlapOperator extends OLuceneSpatialOperator {

  public OLuceneOverlapOperator() {
    super("&&", 5, false);
  }

  @Override
  public Stream<ORawPair<Object, YTRID>> executeIndexQuery(
      OCommandContext iContext, OIndex index, List<Object> keyParams, boolean ascSortOrder) {
    Object key;
    key = keyParams.get(0);

    Map<String, Object> queryParams = new HashMap<String, Object>();
    queryParams.put(SpatialQueryBuilderAbstract.GEO_FILTER, SpatialQueryBuilderOverlap.NAME);
    queryParams.put(SpatialQueryBuilderAbstract.SHAPE, key);

    //noinspection resource
    return index
        .getInternal()
        .getRids(iContext.getDatabase(), queryParams)
        .map((rid) -> new ORawPair<>(new OSpatialCompositeKey(keyParams), rid));
  }

  @Override
  public Object evaluateRecord(
      YTIdentifiable iRecord,
      YTEntityImpl iCurrentResult,
      OSQLFilterCondition iCondition,
      Object iLeft,
      Object iRight,
      OCommandContext iContext,
      final ODocumentSerializer serializer) {
    Shape shape = factory.fromDoc((YTEntityImpl) iLeft);

    // TODO { 'shape' : { 'type' : 'LineString' , 'coordinates' : [[1,2],[4,6]]} }
    // TODO is not translated in map but in array[ { 'type' : 'LineString' , 'coordinates' :
    // [[1,2],[4,6]]} ]
    Object filter;
    if (iRight instanceof Collection) {
      filter = ((Collection) iRight).iterator().next();
    } else {
      filter = iRight;
    }
    Shape shape1 = factory.fromObject(filter);

    return SpatialOperation.BBoxIntersects.evaluate(shape, shape1.getBoundingBox());
  }
}
