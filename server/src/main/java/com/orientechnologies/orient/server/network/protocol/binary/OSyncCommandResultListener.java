/*
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
 */

package com.orientechnologies.orient.server.network.protocol.binary;

import com.orientechnologies.orient.client.remote.OFetchPlanResults;
import com.orientechnologies.core.command.OCommandResultListener;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.db.record.YTIdentifiable;
import com.orientechnologies.core.exception.YTFetchException;
import com.orientechnologies.core.fetch.OFetchContext;
import com.orientechnologies.core.fetch.OFetchHelper;
import com.orientechnologies.core.fetch.remote.ORemoteFetchContext;
import com.orientechnologies.core.fetch.remote.ORemoteFetchListener;
import com.orientechnologies.core.id.YTRecordId;
import com.orientechnologies.core.record.YTRecord;
import com.orientechnologies.core.record.YTRecordAbstract;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import java.util.HashSet;
import java.util.Set;

/**
 * Synchronous command result manager.
 */
public class OSyncCommandResultListener extends OAbstractCommandResultListener
    implements OFetchPlanResults {

  private final Set<YTRecord> fetchedRecordsToSend = new HashSet<YTRecord>();
  private final Set<YTRecord> alreadySent = new HashSet<YTRecord>();

  public OSyncCommandResultListener(final OCommandResultListener wrappedResultListener) {
    super(wrappedResultListener);
  }

  @Override
  public boolean result(YTDatabaseSessionInternal querySession, final Object iRecord) {
    if (iRecord instanceof YTRecord) {
      alreadySent.add((YTRecord) iRecord);
      fetchedRecordsToSend.remove(iRecord);
    }

    if (wrappedResultListener != null)
    // NOTIFY THE WRAPPED LISTENER
    {
      wrappedResultListener.result(querySession, iRecord);
    }

    fetchRecord(
        iRecord,
        new ORemoteFetchListener() {
          @Override
          protected void sendRecord(YTRecordAbstract iLinked) {
            if (!alreadySent.contains(iLinked)) {
              fetchedRecordsToSend.add(iLinked);
            }
          }
        });
    return true;
  }

  public Set<YTRecord> getFetchedRecordsToSend() {
    return fetchedRecordsToSend;
  }

  public boolean isEmpty() {
    return false;
  }

  @Override
  public void linkdedBySimpleValue(YTEntityImpl doc) {

    ORemoteFetchListener listener =
        new ORemoteFetchListener() {
          @Override
          protected void sendRecord(YTRecordAbstract iLinked) {
            if (!alreadySent.contains(iLinked)) {
              fetchedRecordsToSend.add(iLinked);
            }
          }

          @Override
          public void parseLinked(
              YTEntityImpl iRootRecord,
              YTIdentifiable iLinked,
              Object iUserObject,
              String iFieldName,
              OFetchContext iContext)
              throws YTFetchException {
            if (!(iLinked instanceof YTRecordId)) {
              sendRecord(iLinked.getRecord());
            }
          }

          @Override
          public void parseLinkedCollectionValue(
              YTEntityImpl iRootRecord,
              YTIdentifiable iLinked,
              Object iUserObject,
              String iFieldName,
              OFetchContext iContext)
              throws YTFetchException {

            if (!(iLinked instanceof YTRecordId)) {
              sendRecord(iLinked.getRecord());
            }
          }
        };
    final OFetchContext context = new ORemoteFetchContext();
    OFetchHelper.fetch(doc, doc, OFetchHelper.buildFetchPlan(""), listener, context, "");
  }
}
