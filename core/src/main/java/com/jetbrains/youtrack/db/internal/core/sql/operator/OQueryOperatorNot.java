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
package com.jetbrains.youtrack.db.internal.core.sql.operator;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.ODocumentSerializer;
import com.jetbrains.youtrack.db.internal.core.sql.filter.OSQLFilterCondition;

/**
 * NOT operator.
 */
public class OQueryOperatorNot extends OQueryOperator {

  private OQueryOperator next;

  public OQueryOperatorNot() {
    super("NOT", 10, true);
    next = null;
  }

  public OQueryOperatorNot(final OQueryOperator iNext) {
    this();
    next = iNext;
  }

  @Override
  public Object evaluateRecord(
      final YTIdentifiable iRecord,
      EntityImpl iCurrentResult,
      final OSQLFilterCondition iCondition,
      final Object iLeft,
      final Object iRight,
      CommandContext iContext,
      final ODocumentSerializer serializer) {
    if (next != null) {
      return !(Boolean)
          next.evaluateRecord(iRecord, null, iCondition, iLeft, iRight, iContext, serializer);
    }

    if (iLeft == null) {
      return false;
    }
    return !(Boolean) iLeft;
  }

  @Override
  public OIndexReuseType getIndexReuseType(final Object iLeft, final Object iRight) {
    return OIndexReuseType.NO_INDEX;
  }

  @Override
  public YTRID getBeginRidRange(YTDatabaseSession session, Object iLeft, Object iRight) {
    if (iLeft instanceof OSQLFilterCondition) {
      final YTRID beginRange = ((OSQLFilterCondition) iLeft).getBeginRidRange(session);
      final YTRID endRange = ((OSQLFilterCondition) iLeft).getEndRidRange(session);

      if (beginRange == null && endRange == null) {
        return null;
      } else if (beginRange == null) {
        return endRange;
      } else if (endRange == null) {
        return null;
      } else {
        return null;
      }
    }

    return null;
  }

  @Override
  public YTRID getEndRidRange(YTDatabaseSession session, Object iLeft, Object iRight) {
    if (iLeft instanceof OSQLFilterCondition) {
      final YTRID beginRange = ((OSQLFilterCondition) iLeft).getBeginRidRange(session);
      final YTRID endRange = ((OSQLFilterCondition) iLeft).getEndRidRange(session);

      if (beginRange == null && endRange == null) {
        return null;
      } else if (beginRange == null) {
        return null;
      } else if (endRange == null) {
        return beginRange;
      } else {
        return null;
      }
    }

    return null;
  }

  public OQueryOperator getNext() {
    return next;
  }
}