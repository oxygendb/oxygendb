/*
 *
 *
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

package com.jetbrains.youtrack.db.internal.core.id;

import java.util.Map;

public class YTContextualRecordId extends YTRecordId {

  private Map<String, Object> context;

  public YTContextualRecordId(final String iRecordId) {
    super(iRecordId);
  }

  public YTContextualRecordId setContext(final Map<String, Object> context) {
    this.context = context;
    return this;
  }

  public Map<String, Object> getContext() {
    return context;
  }
}