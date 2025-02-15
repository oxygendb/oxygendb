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
package com.jetbrains.youtrack.db.internal.core.storage.index.hashindex.local;

import com.jetbrains.youtrack.db.internal.common.hash.MurmurHash3;
import com.jetbrains.youtrack.db.internal.common.serialization.types.BinarySerializer;

/**
 * @since 12.03.13
 */
public class MurmurHash3HashFunction<V> implements HashFunction<V> {

  private static final int SEED = 362498820;

  private final BinarySerializer<V> valueSerializer;

  public MurmurHash3HashFunction(BinarySerializer<V> valueSerializer) {
    this.valueSerializer = valueSerializer;
  }

  @Override
  public long hashCode(final V value) {
    final byte[] serializedValue = new byte[valueSerializer.getObjectSize(value)];
    valueSerializer.serializeNativeObject(value, serializedValue, 0);

    return MurmurHash3.murmurHash3_x64_64(serializedValue, SEED);
  }
}
