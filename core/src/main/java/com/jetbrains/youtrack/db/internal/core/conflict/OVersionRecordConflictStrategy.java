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

package com.jetbrains.youtrack.db.internal.core.conflict;

import com.jetbrains.youtrack.db.internal.core.db.record.ORecordOperation;
import com.jetbrains.youtrack.db.internal.core.exception.YTConcurrentModificationException;
import com.jetbrains.youtrack.db.internal.core.exception.YTFastConcurrentModificationException;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.storage.Storage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default strategy that checks the record version number: if the current update has a version
 * different than stored one, then a YTConcurrentModificationException is thrown.
 */
public class OVersionRecordConflictStrategy implements ORecordConflictStrategy {

  public static final String NAME = "version";

  @Override
  public byte[] onUpdate(
      Storage storage,
      final byte iRecordType,
      final YTRecordId rid,
      final int iRecordVersion,
      final byte[] iRecordContent,
      final AtomicInteger iDatabaseVersion) {
    checkVersions(rid, iRecordVersion, iDatabaseVersion.get());
    return null;
  }

  @Override
  public String getName() {
    return NAME;
  }

  protected void checkVersions(
      final YTRecordId rid, final int iRecordVersion, final int iDatabaseVersion) {
    if (YTFastConcurrentModificationException.enabled()) {
      throw YTFastConcurrentModificationException.instance();
    } else {
      throw new YTConcurrentModificationException(
          rid, iDatabaseVersion, iRecordVersion, ORecordOperation.UPDATED);
    }
  }
}