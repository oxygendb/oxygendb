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
package com.jetbrains.youtrack.db.internal.common.types;

/**
 * Binary wrapper to let to byte[] to be managed inside YouTrackDB where comparable is needed, like
 * for indexes.
 *
 *
 * <p>Deprecated sice v2.2
 */
@Deprecated
public class Binary implements Comparable<Binary> {

  private final byte[] value;

  public Binary(final byte[] buffer) {
    value = buffer;
  }

  public int compareTo(final Binary o) {
    final int size = value.length;

    for (int i = 0; i < size; ++i) {
      if (value[i] > o.value[i]) {
        return 1;
      } else if (value[i] < o.value[i]) {
        return -1;
      }
    }
    return 0;
  }

  public byte[] toByteArray() {
    return value;
  }
}
