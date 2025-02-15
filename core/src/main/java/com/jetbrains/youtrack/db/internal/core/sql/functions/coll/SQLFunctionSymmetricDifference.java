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
package com.jetbrains.youtrack.db.internal.core.sql.functions.coll;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.api.DatabaseSession;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This operator can work as aggregate or inline. If only one argument is passed than aggregates,
 * otherwise executes, and returns, the SYMMETRIC DIFFERENCE between the collections received as
 * parameters. Works also with no collection values.
 */
public class SQLFunctionSymmetricDifference extends SQLFunctionMultiValueAbstract<Set<Object>> {

  public static final String NAME = "symmetricDifference";

  private Set<Object> rejected;

  public SQLFunctionSymmetricDifference() {
    super(NAME, 1, -1);
  }

  private static void addItemToResult(Object o, Set<Object> accepted, Set<Object> rejected) {
    if (!accepted.contains(o) && !rejected.contains(o)) {
      accepted.add(o);
    } else {
      accepted.remove(o);
      rejected.add(o);
    }
  }

  private static void addItemsToResult(
      Collection<Object> co, Set<Object> accepted, Set<Object> rejected) {
    for (Object o : co) {
      addItemToResult(o, accepted, rejected);
    }
  }

  @SuppressWarnings("unchecked")
  public Object execute(
      Object iThis,
      Identifiable iCurrentRecord,
      Object iCurrentResult,
      final Object[] iParams,
      CommandContext iContext) {
    if (iParams[0] == null) {
      return null;
    }

    Object value = iParams[0];

    if (iParams.length == 1) {
      // AGGREGATION MODE (STATEFUL)
      if (context == null) {
        context = new HashSet<Object>();
        rejected = new HashSet<Object>();
      }
      if (value instanceof Collection<?>) {
        addItemsToResult((Collection<Object>) value, context, rejected);
      } else {
        addItemToResult(value, context, rejected);
      }

      return null;
    } else {
      // IN-LINE MODE (STATELESS)
      final Set<Object> result = new HashSet<Object>();
      final Set<Object> rejected = new HashSet<Object>();

      for (Object iParameter : iParams) {
        if (iParameter instanceof Collection<?>) {
          addItemsToResult((Collection<Object>) iParameter, result, rejected);
        } else {
          addItemToResult(iParameter, result, rejected);
        }
      }

      return result;
    }
  }

  @Override
  public Set<Object> getResult() {
    if (returnDistributedResult()) {
      final Map<String, Object> map = new HashMap<String, Object>();
      map.put("result", context);
      map.put("rejected", rejected);
      return Collections.singleton(map);
    } else {
      return super.getResult();
    }
  }

  public String getSyntax(DatabaseSession session) {
    return "difference(<field>*)";
  }

  @Override
  public Object mergeDistributedResult(List<Object> resultsToMerge) {
    if (returnDistributedResult()) {
      final Set<Object> result = new HashSet<Object>();
      final Set<Object> rejected = new HashSet<Object>();
      for (Object item : resultsToMerge) {
        rejected.addAll(unwrap(item, "rejected"));
      }
      for (Object item : resultsToMerge) {
        addItemsToResult(unwrap(item, "result"), result, rejected);
      }
      return result;
    }

    if (!resultsToMerge.isEmpty()) {
      return resultsToMerge.get(0);
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  private Set<Object> unwrap(Object obj, String field) {
    final Set<Object> objAsSet = (Set<Object>) obj;
    final Map<String, Object> objAsMap = (Map<String, Object>) objAsSet.iterator().next();
    final Set<Object> objAsField = (Set<Object>) objAsMap.get(field);
    return objAsField;
  }
}
