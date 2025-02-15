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
package com.jetbrains.youtrack.db.internal.core.serialization.serializer.record;

import com.jetbrains.youtrack.db.internal.core.YouTrackDBEnginesManager;
import com.jetbrains.youtrack.db.internal.core.YouTrackDBListenerAbstract;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class SerializationThreadLocal extends ThreadLocal<IntSet> {

  public static volatile SerializationThreadLocal INSTANCE = new SerializationThreadLocal();

  static {
    YouTrackDBEnginesManager.instance()
        .registerListener(
            new YouTrackDBListenerAbstract() {
              @Override
              public void onStartup() {
                if (INSTANCE == null) {
                  INSTANCE = new SerializationThreadLocal();
                }
              }

              @Override
              public void onShutdown() {
                INSTANCE = null;
              }
            });
  }

  @Override
  protected IntSet initialValue() {
    return new IntOpenHashSet();
  }
}
