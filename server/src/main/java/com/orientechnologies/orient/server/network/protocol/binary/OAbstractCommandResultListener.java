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
package com.orientechnologies.orient.server.network.protocol.binary;

import com.orientechnologies.core.command.OCommandResultListener;
import com.orientechnologies.core.fetch.OFetchContext;
import com.orientechnologies.core.fetch.OFetchHelper;
import com.orientechnologies.core.fetch.OFetchListener;
import com.orientechnologies.core.fetch.OFetchPlan;
import com.orientechnologies.core.fetch.remote.ORemoteFetchContext;
import com.orientechnologies.core.record.YTRecord;
import com.orientechnologies.orient.client.remote.SimpleValueFetchPlanCommandListener;

/**
 * Abstract class to manage command results.
 */
public abstract class OAbstractCommandResultListener
    implements SimpleValueFetchPlanCommandListener {

  protected final OCommandResultListener wrappedResultListener;

  private OFetchPlan fetchPlan;

  protected OAbstractCommandResultListener(final OCommandResultListener wrappedResultListener) {
    this.wrappedResultListener = wrappedResultListener;
  }

  public abstract boolean isEmpty();

  @Override
  public void end() {
    if (wrappedResultListener != null) {
      wrappedResultListener.end();
    }
  }

  public void setFetchPlan(final String iText) {
    fetchPlan = OFetchHelper.buildFetchPlan(iText);
  }

  protected void fetchRecord(final Object iRecord, final OFetchListener iFetchListener) {
    if (fetchPlan != null
        && fetchPlan != OFetchHelper.DEFAULT_FETCHPLAN
        && iRecord instanceof YTRecord record) {
      final OFetchContext context = new ORemoteFetchContext();
      OFetchHelper.fetch(record, record, fetchPlan, iFetchListener, context, "");
    }
  }

  @Override
  public Object getResult() {
    if (wrappedResultListener != null) {
      return wrappedResultListener.getResult();
    }

    return null;
  }
}
