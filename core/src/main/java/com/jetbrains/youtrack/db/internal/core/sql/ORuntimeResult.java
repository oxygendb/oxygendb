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
package com.jetbrains.youtrack.db.internal.core.sql;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResultInternal;
import com.jetbrains.youtrack.db.internal.core.sql.functions.OSQLFunctionRuntime;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles runtime results.
 */
public class ORuntimeResult {

  private final Object fieldValue;
  private final Map<String, Object> projections;
  private final YTResultInternal value;
  private final CommandContext context;

  public ORuntimeResult(
      final Object iFieldValue,
      final Map<String, Object> iProjections,
      final int iProgressive,
      final CommandContext iContext) {
    fieldValue = iFieldValue;
    projections = iProjections;
    context = iContext;
    value = new YTResultInternal(iContext.getDatabase());
  }


  private static boolean entriesPersistent(Collection<YTIdentifiable> projectionValue) {
    for (YTIdentifiable rec : projectionValue) {
      if (rec != null && !rec.getIdentity().isPersistent()) {
        return false;
      }
    }

    return true;
  }

  public static YTResultInternal getResult(
      YTDatabaseSessionInternal session, final YTResultInternal iValue,
      final Map<String, Object> iProjections) {
    if (iValue != null) {
      boolean canExcludeResult = false;

      for (Entry<String, Object> projection : iProjections.entrySet()) {
        if (!iValue.hasProperty(projection.getKey())) {
          // ONLY IF NOT ALREADY CONTAINS A VALUE, OTHERWISE HAS BEEN SET MANUALLY (INDEX?)
          final Object v = projection.getValue();
          if (v instanceof OSQLFunctionRuntime f) {
            canExcludeResult = f.filterResult();
            Object fieldValue = f.getResult(session);
            if (fieldValue != null) {
              iValue.setProperty(projection.getKey(), fieldValue);
            }
          }
        }
      }

      if (canExcludeResult && iValue.getPropertyNames().isEmpty()) {
        // RESULT EXCLUDED FOR EMPTY RECORD
        return null;
      }
    }

    return iValue;
  }

  /**
   * Set a single value. This is useful in case of query optimization like with indexes
   *
   * @param iName  Field name
   * @param iValue Field value
   */
  public void applyValue(final String iName, final Object iValue) {
    value.setProperty(iName, iValue);
  }

  public YTResultInternal getResult(YTDatabaseSessionInternal session) {
    return getResult(session, value, projections);
  }

  public Object getFieldValue() {
    return fieldValue;
  }
}