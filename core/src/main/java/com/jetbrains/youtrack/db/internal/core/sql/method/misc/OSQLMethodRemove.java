/*
 *
 * Copyright 2013 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.youtrack.db.internal.core.sql.method.misc;

import com.jetbrains.youtrack.db.internal.common.collection.OMultiValue;
import com.jetbrains.youtrack.db.internal.common.util.OCallable;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;

/**
 * Remove the first occurrence of elements from a collection.
 *
 * @see OSQLMethodRemoveAll
 */
public class OSQLMethodRemove extends OAbstractSQLMethod {

  public static final String NAME = "remove";

  public OSQLMethodRemove() {
    super(NAME, 1, -1);
  }

  @Override
  public Object execute(
      Object iThis,
      final YTIdentifiable iCurrentRecord,
      final CommandContext iContext,
      Object ioResult,
      Object[] iParams) {
    if (iParams != null && iParams.length > 0 && iParams[0] != null) {
      iParams =
          OMultiValue.array(
              iParams,
              Object.class,
              new OCallable<Object, Object>() {

                @Override
                public Object call(final Object iArgument) {
                  if (iArgument instanceof String && ((String) iArgument).startsWith("$")) {
                    return iContext.getVariable((String) iArgument);
                  }
                  return iArgument;
                }
              });
      for (Object o : iParams) {
        ioResult = OMultiValue.remove(ioResult, o, false);
      }
    }

    return ioResult;
  }

  @Override
  public String getSyntax() {
    return "remove(<item>*)";
  }
}