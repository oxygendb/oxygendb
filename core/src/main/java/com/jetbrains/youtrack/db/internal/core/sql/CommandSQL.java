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

import com.jetbrains.youtrack.db.internal.core.command.CommandRequestTextAbstract;
import com.jetbrains.youtrack.db.internal.core.replication.AsyncReplicationError;
import com.jetbrains.youtrack.db.internal.core.replication.AsyncReplicationOk;

/**
 * SQL command request implementation. It just stores the request and delegated the execution to the
 * configured CommandExecutor.
 */
@SuppressWarnings("serial")
public class CommandSQL extends CommandRequestTextAbstract {

  public CommandSQL() {
  }

  public CommandSQL(final String iText) {
    super(iText);
  }

  public boolean isIdempotent() {
    return false;
  }

  @Override
  public String toString() {
    return "sql." + text; // IOUtils.getStringMaxLength(text, 50, "...");
  }

  /**
   * Defines a callback to call in case of the asynchronous replication succeed.
   */
  @Override
  public CommandSQL onAsyncReplicationOk(final AsyncReplicationOk iCallback) {
    return (CommandSQL) super.onAsyncReplicationOk(iCallback);
  }

  /**
   * Defines a callback to call in case of error during the asynchronous replication.
   */
  @Override
  public CommandSQL onAsyncReplicationError(final AsyncReplicationError iCallback) {
    return (CommandSQL) super.onAsyncReplicationError(iCallback);
  }
}
