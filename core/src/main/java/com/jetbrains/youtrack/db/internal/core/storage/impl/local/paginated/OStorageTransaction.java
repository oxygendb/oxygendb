/*
 * Copyright 2010-2013 YouTrackDB LTD (info--at--orientdb.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated;

import com.jetbrains.youtrack.db.internal.core.tx.OTransactionInternal;

/**
 * @since 12.06.13
 */
public class OStorageTransaction {

  private final OTransactionInternal clientTx;

  public OStorageTransaction(OTransactionInternal clientTx) {
    this.clientTx = clientTx;
  }

  public OTransactionInternal getClientTx() {
    return clientTx;
  }
}