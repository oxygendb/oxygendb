/*
 *
 *  * Copyright 2010-2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.orient.etl.listener;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class ODefaultImporterListener implements OImporterListener {

  public void onBeforeFile(final ODatabaseDocumentTx db, final OCommandContext iContext) {
  }

  public void onAfterFile(final ODatabaseDocumentTx db, final OCommandContext iContext) {
  }

  public boolean onBeforeLine(final ODatabaseDocumentTx db, final OCommandContext iContext) {
    return true;
  }

  public void onAfterLine(final ODatabaseDocumentTx db, final OCommandContext iContext) {
  }

  public void onDump(final ODatabaseDocumentTx db, final OCommandContext iContext) {
  }

  @Override
  public void onJoinNotFound(final ODatabaseDocumentTx db, final OCommandContext iContext, final OIndex<?> iIndex, final Object iKey) {
    iContext.setVariable("joinNotFound", ((Integer) iContext.getVariable("joinNotFound", 0)) + 1);
    OLogManager.instance().warn(this, "     + %d line: join record not found in index '%s' for key='%s'",
        iContext.getVariable("currentLine"), iIndex, iKey);
  }

  @Override
  public void validate(ODatabaseDocumentTx db, OCommandContext iContext, ODocument iRecord) {
  }
}
