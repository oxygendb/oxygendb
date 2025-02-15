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

package com.jetbrains.youtrack.db.internal.core.command;

/**
 * Base command processing class.
 */
public abstract class CommandProcess<C extends Command, T, R> {

  protected final C command;
  protected T target;

  /**
   * Create the process defining command and target.
   */
  public CommandProcess(final C iCommand, final T iTarget) {
    command = iCommand;
    target = iTarget;
  }

  public abstract R process();

  public T getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return target.toString();
  }
}
