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
package com.jetbrains.youtrack.db.internal.core.storage.cluster;

import com.jetbrains.youtrack.db.internal.common.exception.YTHighLevelException;
import com.jetbrains.youtrack.db.internal.core.exception.YTCoreException;

/**
 * Exception when any non idempotent operation is executed against the offline cluster
 *
 * @since 2.0
 */
public class YTOfflineClusterException extends YTCoreException implements YTHighLevelException {

  public YTOfflineClusterException(YTOfflineClusterException exception) {
    super(exception);
  }

  public YTOfflineClusterException(final String s) {
    super(s);
  }
}