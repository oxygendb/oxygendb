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

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import java.util.Locale;

/**
 *
 */
public class SQLMethodToUpperCase extends AbstractSQLMethod {

  public static final String NAME = "touppercase";

  public SQLMethodToUpperCase() {
    super(NAME);
  }

  @Override
  public Object execute(
      Object iThis,
      Identifiable iCurrentRecord,
      CommandContext iContext,
      Object ioResult,
      Object[] iParams) {
    ioResult = ioResult != null ? ioResult.toString().toUpperCase(Locale.ENGLISH) : null;
    return ioResult;
  }
}
