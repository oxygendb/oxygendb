/*
 *
 *
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *
 *
 */
package com.jetbrains.youtrack.db.internal.core.storage.index.sbtree.multivalue.v2;

import com.jetbrains.youtrack.db.internal.common.serialization.types.OByteSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OLongSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OShortSerializer;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCacheEntry;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.base.ODurablePage;
import java.util.ArrayList;
import java.util.List;

/**
 * Bucket which is intended to save values stored in sbtree under <code>null</code> key. Bucket has
 * following layout:
 *
 * <ol>
 *   <li>First byte is flag which indicates presence of value in bucket
 *   <li>Second byte indicates whether value is presented by link to the "bucket list" where actual
 *       value is stored or real value passed be user.
 *   <li>The rest is serialized value whether link or passed in value.
 * </ol>
 *
 * @since 4/15/14
 */
public final class CellBTreeMultiValueV2NullBucket extends ODurablePage {

  private static final int EMBEDDED_RIDS_BOUNDARY = 64;

  private static final int RID_SIZE = OShortSerializer.SHORT_SIZE + OLongSerializer.LONG_SIZE;

  private static final int M_ID_OFFSET = NEXT_FREE_POSITION;
  private static final int EMBEDDED_RIDS_SIZE_OFFSET = M_ID_OFFSET + OLongSerializer.LONG_SIZE;
  private static final int RIDS_SIZE_OFFSET = EMBEDDED_RIDS_SIZE_OFFSET + OByteSerializer.BYTE_SIZE;
  private static final int RIDS_OFFSET = RIDS_SIZE_OFFSET + OIntegerSerializer.INT_SIZE;

  public CellBTreeMultiValueV2NullBucket(final OCacheEntry cacheEntry) {
    super(cacheEntry);
  }

  public void init(final long mId) {
    setLongValue(M_ID_OFFSET, mId);
    setByteValue(EMBEDDED_RIDS_SIZE_OFFSET, (byte) 0);
    setIntValue(RIDS_SIZE_OFFSET, 0);
  }

  public long addValue(final YTRID rid) {
    final int embeddedSize = getByteValue(EMBEDDED_RIDS_SIZE_OFFSET);

    if (embeddedSize < EMBEDDED_RIDS_BOUNDARY) {
      final int position = embeddedSize * RID_SIZE + RIDS_OFFSET;

      setShortValue(position, (short) rid.getClusterId());
      setLongValue(position + OShortSerializer.SHORT_SIZE, rid.getClusterPosition());

      setByteValue(EMBEDDED_RIDS_SIZE_OFFSET, (byte) (embeddedSize + 1));

      final int size = getIntValue(RIDS_SIZE_OFFSET);
      setIntValue(RIDS_SIZE_OFFSET, size + 1);

      return -1;
    } else {
      return getLongValue(M_ID_OFFSET);
    }
  }

  public void incrementSize() {
    setIntValue(RIDS_SIZE_OFFSET, getIntValue(RIDS_SIZE_OFFSET) + 1);
  }

  public void decrementSize() {
    final int size = getIntValue(RIDS_SIZE_OFFSET);
    assert size >= 1;

    setIntValue(RIDS_SIZE_OFFSET, size - 1);
  }

  public List<YTRID> getValues() {
    final int size = getIntValue(RIDS_SIZE_OFFSET);
    final List<YTRID> rids = new ArrayList<>(size);

    final int embeddedSize = getByteValue(EMBEDDED_RIDS_SIZE_OFFSET);
    final int end = embeddedSize * RID_SIZE + RIDS_OFFSET;

    for (int position = RIDS_OFFSET; position < end; position += RID_SIZE) {
      final int clusterId = getShortValue(position);
      final long clusterPosition = getLongValue(position + OShortSerializer.SHORT_SIZE);

      rids.add(new YTRecordId(clusterId, clusterPosition));
    }

    return rids;
  }

  public long getMid() {
    return getLongValue(M_ID_OFFSET);
  }

  public int getSize() {
    return getIntValue(RIDS_SIZE_OFFSET);
  }

  public int removeValue(final YTRID rid) {
    final int size = getIntValue(RIDS_SIZE_OFFSET);

    final int embeddedSize = getByteValue(EMBEDDED_RIDS_SIZE_OFFSET);
    final int end = embeddedSize * RID_SIZE + RIDS_OFFSET;

    for (int position = RIDS_OFFSET; position < end; position += RID_SIZE) {
      final int clusterId = getShortValue(position);
      if (clusterId != rid.getClusterId()) {
        continue;
      }

      final long clusterPosition = getLongValue(position + OShortSerializer.SHORT_SIZE);
      if (clusterPosition == rid.getClusterPosition()) {
        moveData(position + RID_SIZE, position, end - (position + RID_SIZE));
        setByteValue(EMBEDDED_RIDS_SIZE_OFFSET, (byte) (embeddedSize - 1));
        setIntValue(RIDS_SIZE_OFFSET, size - 1);
        return 1;
      }
    }

    if (embeddedSize <= size) {
      return 0;
    }

    return -1;
  }
}