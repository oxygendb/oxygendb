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
package com.jetbrains.youtrack.db.internal.core.sharding.auto;

import static java.lang.Math.abs;

import com.jetbrains.youtrack.db.internal.common.serialization.types.OBinarySerializer;
import com.jetbrains.youtrack.db.internal.core.storage.index.hashindex.local.OMurmurHash3HashFunction;

/**
 * Auto-sharding strategy implementation that uses Murmur hashing.
 *
 * @since 3.0
 */
public final class OAutoShardingMurmurStrategy implements OAutoShardingStrategy {

  private final OMurmurHash3HashFunction hashFunction;

  public OAutoShardingMurmurStrategy(final OBinarySerializer keySerializer) {
    hashFunction = new OMurmurHash3HashFunction<Object>(keySerializer);
  }

  public int getPartitionsId(final Object iKey, final int partitionSize) {
    long hash = hashFunction.hashCode(iKey);
    hash = hash == Long.MIN_VALUE ? 0 : abs(hash);
    return (int) (hash % partitionSize);
  }
}