/*
 *
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
package com.jetbrains.youtrack.db.internal.core.sql;

import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.sql.functions.OSQLFunction;
import com.jetbrains.youtrack.db.internal.core.sql.functions.OSQLFunctionFactory;
import com.jetbrains.youtrack.db.internal.core.sql.operator.OQueryOperator;
import com.jetbrains.youtrack.db.internal.core.sql.operator.OQueryOperatorFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic sql elements factory.
 */
public class ODynamicSQLElementFactory
    implements OCommandExecutorSQLFactory, OQueryOperatorFactory, OSQLFunctionFactory {
  // Used by SQLEngine to register on the fly new elements
  static final Map<String, Object> FUNCTIONS = new ConcurrentHashMap<String, Object>();
  static final Map<String, Class<? extends CommandExecutorSQLAbstract>> COMMANDS =
      new ConcurrentHashMap<String, Class<? extends CommandExecutorSQLAbstract>>();
  static final Set<OQueryOperator> OPERATORS =
      Collections.synchronizedSet(new HashSet<OQueryOperator>());


  @Override
  public void registerDefaultFunctions(YTDatabaseSessionInternal db) {
    // DO NOTHING
  }

  public Set<String> getFunctionNames() {
    return FUNCTIONS.keySet();
  }

  public boolean hasFunction(final String name) {
    return FUNCTIONS.containsKey(name);
  }

  public OSQLFunction createFunction(final String name) throws YTCommandExecutionException {
    final Object obj = FUNCTIONS.get(name);

    if (obj == null) {
      throw new YTCommandExecutionException("Unknown function name :" + name);
    }

    if (obj instanceof OSQLFunction) {
      return (OSQLFunction) obj;
    } else {
      // it's a class
      final Class<?> clazz = (Class<?>) obj;
      try {
        return (OSQLFunction) clazz.newInstance();
      } catch (Exception e) {
        throw YTException.wrapException(
            new YTCommandExecutionException(
                "Error in creation of function "
                    + name
                    + "(). Probably there is not an empty constructor or the constructor generates"
                    + " errors"),
            e);
      }
    }
  }

  public Set<String> getCommandNames() {
    return COMMANDS.keySet();
  }

  public CommandExecutorSQLAbstract createCommand(final String name)
      throws YTCommandExecutionException {
    final Class<? extends CommandExecutorSQLAbstract> clazz = COMMANDS.get(name);

    if (clazz == null) {
      throw new YTCommandExecutionException("Unknown command name :" + name);
    }

    try {
      return clazz.newInstance();
    } catch (Exception e) {
      throw YTException.wrapException(
          new YTCommandExecutionException(
              "Error in creation of command "
                  + name
                  + "(). Probably there is not an empty constructor or the constructor generates"
                  + " errors"),
          e);
    }
  }

  public Set<OQueryOperator> getOperators() {
    return OPERATORS;
  }
}