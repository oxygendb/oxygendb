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

import com.jetbrains.youtrack.db.internal.common.collection.OMultiValue;
import com.jetbrains.youtrack.db.internal.common.util.OSupportsContains;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.db.record.ridbag.RidBag;
import com.jetbrains.youtrack.db.internal.core.sql.filter.OSQLFilterItemVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This operator can work as aggregate or inline. If only one argument is passed than aggregates,
 * otherwise executes, and returns, the INTERSECTION of the collections received as parameters.
 */
public class OSQLFunctionIntersect extends OSQLFunctionMultiValueAbstract<Object> {

  public static final String NAME = "intersect";

  public OSQLFunctionIntersect() {
    super(NAME, 1, -1);
  }

  public Object execute(
      Object iThis,
      final YTIdentifiable iCurrentRecord,
      Object iCurrentResult,
      final Object[] iParams,
      CommandContext iContext) {
    Object value = iParams[0];

    if (value instanceof OSQLFilterItemVariable) {
      value = ((OSQLFilterItemVariable) value).getValue(iCurrentRecord, iCurrentResult, iContext);
    }

    if (value == null) {
      return Collections.emptySet();
    }

    if (iParams.length == 1) {
      // AGGREGATION MODE (STATEFUL)
      if (context == null) {
        // ADD ALL THE ITEMS OF THE FIRST COLLECTION
        if (value instanceof Collection) {
          context = ((Collection) value).iterator();
        } else if (value instanceof Iterator) {
          context = value;
        } else if (value instanceof Iterable) {
          context = ((Iterable) value).iterator();
        } else {
          context = List.of(value).iterator();
        }
      } else {
        Iterator contextIterator = null;
        if (context instanceof Iterator) {
          contextIterator = (Iterator) context;
        } else if (OMultiValue.isMultiValue(context)) {
          contextIterator = OMultiValue.getMultiValueIterator(context);
        }
        context = intersectWith(contextIterator, value);
      }
      return null;
    }

    // IN-LINE MODE (STATELESS)
    Iterator iterator = OMultiValue.getMultiValueIterator(value);

    for (int i = 1; i < iParams.length; ++i) {
      value = iParams[i];

      if (value instanceof OSQLFilterItemVariable) {
        value = ((OSQLFilterItemVariable) value).getValue(iCurrentRecord, iCurrentResult, iContext);
      }

      if (value != null) {
        value = intersectWith(iterator, value);
        iterator = OMultiValue.getMultiValueIterator(value);
      } else {
        return Collections.emptyIterator();
      }
    }

    List result = new ArrayList();
    while (iterator.hasNext()) {
      result.add(iterator.next());
    }
    return result;
  }

  @Override
  public Object getResult() {
    return OMultiValue.toSet(context);
  }

  static Collection intersectWith(final Iterator current, Object value) {
    final HashSet tempSet = new HashSet();

    if (!(value instanceof Set)
        && (!(value instanceof OSupportsContains)
        || !((OSupportsContains) value).supportsFastContains())) {
      value = OMultiValue.toSet(value);
    }

    for (Iterator it = current; it.hasNext(); ) {
      final Object curr = it.next();
      if (value instanceof RidBag) {
        if (((RidBag) value).contains((YTIdentifiable) curr)) {
          tempSet.add(curr);
        }
      } else if (value instanceof Collection) {
        if (((Collection) value).contains(curr)) {
          tempSet.add(curr);
        }
      } else if (value instanceof OSupportsContains) {
        if (((OSupportsContains) value).contains(curr)) {
          tempSet.add(curr);
        }
      }
    }

    return tempSet;
  }

  public String getSyntax(YTDatabaseSession session) {
    return "intersect(<field>*)";
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object mergeDistributedResult(List<Object> resultsToMerge) {
    final Collection<Object> result = new HashSet<Object>();
    if (!resultsToMerge.isEmpty()) {
      final Collection<Object> items = (Collection<Object>) resultsToMerge.get(0);
      if (items != null) {
        result.addAll(items);
      }
    }
    for (int i = 1; i < resultsToMerge.size(); i++) {
      final Collection<Object> items = (Collection<Object>) resultsToMerge.get(i);
      if (items != null) {
        result.retainAll(items);
      }
    }
    return result;
  }
}