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

package com.jetbrains.youtrack.db.internal.core.storage.index.sbtree.local.v1;

import com.jetbrains.youtrack.db.api.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.api.exception.BaseException;
import com.jetbrains.youtrack.db.api.exception.TooBigIndexKeyException;
import com.jetbrains.youtrack.db.api.schema.PropertyType;
import com.jetbrains.youtrack.db.internal.common.comparator.DefaultComparator;
import com.jetbrains.youtrack.db.internal.common.serialization.types.BinarySerializer;
import com.jetbrains.youtrack.db.internal.common.util.RawPair;
import com.jetbrains.youtrack.db.internal.core.index.CompositeKey;
import com.jetbrains.youtrack.db.internal.core.index.IndexKeyUpdater;
import com.jetbrains.youtrack.db.internal.core.index.IndexUpdateAction;
import com.jetbrains.youtrack.db.internal.core.index.comparator.AlwaysGreaterKey;
import com.jetbrains.youtrack.db.internal.core.index.comparator.AlwaysLessKey;
import com.jetbrains.youtrack.db.internal.core.index.engine.IndexEngineValidator;
import com.jetbrains.youtrack.db.internal.core.iterator.EmptyIterator;
import com.jetbrains.youtrack.db.internal.core.storage.cache.CacheEntry;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.AbstractPaginatedStorage;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.atomicoperations.AtomicOperation;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.base.DurableComponent;
import com.jetbrains.youtrack.db.internal.core.storage.index.sbtree.local.SBTree;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This is implementation which is based on B+-tree implementation threaded tree. The main
 * differences are:
 *
 * <ol>
 *   <li>Buckets are not compacted/removed if they are empty after deletion of item. They reused
 *       later when new items are added.
 *   <li>All non-leaf buckets have links to neighbor buckets which contain keys which are less/more
 *       than keys contained in current bucket
 *       <ol/>
 *         There is support of null values for keys, but values itself cannot be null. Null keys
 *         support is switched off by default if null keys are supported value which is related to
 *         null key will be stored in separate file which has only one page. Buckets/pages for usual
 *         (non-null) key-value entries can be considered as sorted array. The first bytes of page
 *         contains such auxiliary information as size of entries contained in bucket, links to
 *         neighbors which contain entries with keys less/more than keys in current bucket. The next
 *         bytes contain sorted array of entries. Array itself is split on two parts. First part is
 *         growing from start to end, and second part is growing from end to start. First part is
 *         array of offsets to real key-value entries which are stored in second part of array which
 *         grows from end to start. This array of offsets is sorted by accessing order according to
 *         key value. So we can use binary search to find requested key. When new key-value pair is
 *         added we append binary presentation of this pair to the second part of array which grows
 *         from end of page to start, remember value of offset for this pair, and find proper
 *         position of this offset inside of first part of array. Such approach allows to minimize
 *         amount of memory involved in performing of operations and as result speed up data
 *         processing.
 *
 * @since 8/7/13
 */
public final class SBTreeV1<K, V> extends DurableComponent
    implements SBTree<K, V> {

  private static final int MAX_KEY_SIZE =
      GlobalConfiguration.SBTREE_MAX_KEY_SIZE.getValueAsInteger();
  private static final int MAX_EMBEDDED_VALUE_SIZE =
      GlobalConfiguration.SBTREE_MAX_EMBEDDED_VALUE_SIZE.getValueAsInteger();
  private static final AlwaysLessKey ALWAYS_LESS_KEY = new AlwaysLessKey();
  private static final AlwaysGreaterKey ALWAYS_GREATER_KEY = new AlwaysGreaterKey();

  private static final int MAX_PATH_LENGTH =
      GlobalConfiguration.SBTREE_MAX_DEPTH.getValueAsInteger();

  private static final long ROOT_INDEX = 0;
  private final Comparator<? super K> comparator = DefaultComparator.INSTANCE;
  private final String nullFileExtension;
  private long fileId;
  private long nullBucketFileId = -1;
  private int keySize;
  private BinarySerializer<K> keySerializer;
  private PropertyType[] keyTypes;
  private BinarySerializer<V> valueSerializer;
  private boolean nullPointerSupport;
  private final AtomicLong bonsayFileId = new AtomicLong(0);

  public SBTreeV1(
      final String name,
      final String dataFileExtension,
      final String nullFileExtension,
      final AbstractPaginatedStorage storage) {
    super(storage, name, dataFileExtension, name + dataFileExtension);
    acquireExclusiveLock();
    try {
      this.nullFileExtension = nullFileExtension;
    } finally {
      releaseExclusiveLock();
    }
  }

  @Override
  public void create(
      final AtomicOperation atomicOperation,
      final BinarySerializer<K> keySerializer,
      final BinarySerializer<V> valueSerializer,
      final PropertyType[] keyTypes,
      final int keySize,
      final boolean nullPointerSupport) {
    assert keySerializer != null;

    executeInsideComponentOperation(
        atomicOperation,
        operation -> {
          acquireExclusiveLock();
          try {
            this.keySize = keySize;
            if (keyTypes != null) {
              this.keyTypes = Arrays.copyOf(keyTypes, keyTypes.length);
            } else {
              this.keyTypes = null;
            }
            this.keySerializer = keySerializer;

            this.valueSerializer = valueSerializer;
            this.nullPointerSupport = nullPointerSupport;

            fileId = addFile(atomicOperation, getFullName());

            if (nullPointerSupport) {
              nullBucketFileId = addFile(atomicOperation, getName() + nullFileExtension);
            }

            try (final CacheEntry rootCacheEntry = addPage(atomicOperation, fileId)) {
              final SBTreeBucketV1<K, V> rootBucket = new SBTreeBucketV1<>(rootCacheEntry);
              rootBucket.init(true);
            }
          } finally {
            releaseExclusiveLock();
          }
        });
  }

  @Override
  public boolean isNullPointerSupport() {
    acquireSharedLock();
    try {
      return nullPointerSupport;
    } finally {
      releaseSharedLock();
    }
  }

  @Override
  public V get(K key) {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        checkNullSupport(key);

        final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();
        if (key != null) {
          key = keySerializer.preprocess(key, (Object[]) keyTypes);

          final BucketSearchResult bucketSearchResult = findBucket(key, atomicOperation);
          if (bucketSearchResult.itemIndex < 0) {
            return null;
          }

          final long pageIndex = bucketSearchResult.getLastPathItem();

          try (final CacheEntry keyBucketCacheEntry =
              loadPageForRead(atomicOperation, fileId, pageIndex)) {
            final SBTreeBucketV1<K, V> keyBucket = new SBTreeBucketV1<>(keyBucketCacheEntry);

            final SBTreeBucketV1.SBTreeEntry<K, V> treeEntry =
                keyBucket.getEntry(
                    bucketSearchResult.itemIndex, keySerializer, valueSerializer);
            return treeEntry.value.getValue();
          }
        } else {
          if (getFilledUpTo(atomicOperation, nullBucketFileId) == 0) {
            return null;
          }

          try (final CacheEntry nullBucketCacheEntry =
              loadPageForRead(atomicOperation, nullBucketFileId, 0)) {
            final SBTreeNullBucketV1<V> nullBucket =
                new SBTreeNullBucketV1<>(nullBucketCacheEntry);
            final SBTreeValue<V> treeValue = nullBucket.getValue(valueSerializer);
            if (treeValue == null) {
              return null;
            }

            return treeValue.getValue();
          }
        }
      } finally {
        releaseSharedLock();
      }
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException("Error during retrieving  of sbtree with name " + getName(), this),
          e);
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  @Override
  public void put(AtomicOperation atomicOperation, final K key, final V value) {
    put(atomicOperation, key, value, null);
  }

  @Override
  public boolean validatedPut(
      AtomicOperation atomicOperation,
      final K key,
      final V value,
      final IndexEngineValidator<K, V> validator) {
    return put(atomicOperation, key, value, validator);
  }

  private boolean put(
      final AtomicOperation atomicOperation,
      final K key,
      final V value,
      final IndexEngineValidator<K, V> validator) {
    return update(
        atomicOperation, key, (x, bonsayFileId) -> IndexUpdateAction.changed(value), validator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean update(
      final AtomicOperation atomicOperation,
      K k,
      final IndexKeyUpdater<V> updater,
      final IndexEngineValidator<K, V> validator) {
    return calculateInsideComponentOperation(
        atomicOperation,
        operation -> {
          acquireExclusiveLock();
          try {
            K key = k;
            checkNullSupport(key);

            if (key != null) {
              key = keySerializer.preprocess(key, (Object[]) keyTypes);

              if (keySize > MAX_KEY_SIZE) {
                throw new TooBigIndexKeyException(
                    "Key size is more than allowed, operation was canceled. Current key size "
                        + keySize
                        + ", allowed  "
                        + MAX_KEY_SIZE,
                    getName());
              }

              BucketSearchResult bucketSearchResult = findBucket(key, atomicOperation);

              CacheEntry keyBucketCacheEntry =
                  loadPageForWrite(
                      atomicOperation, fileId, bucketSearchResult.getLastPathItem(), true);
              SBTreeBucketV1<K, V> keyBucket = new SBTreeBucketV1<>(keyBucketCacheEntry);

              final byte[] oldRawValue;
              if (bucketSearchResult.itemIndex > -1) {
                oldRawValue =
                    keyBucket.getRawValue(
                        bucketSearchResult.itemIndex,
                        keySerializer,
                        valueSerializer);
              } else {
                oldRawValue = null;
              }
              final V oldValue;
              if (oldRawValue == null) {
                oldValue = null;
              } else {
                oldValue = valueSerializer.deserializeNativeObject(oldRawValue, 0);
              }

              final IndexUpdateAction<V> updatedValue = updater.update(oldValue, bonsayFileId);
              if (updatedValue.isChange()) {
                V value = updatedValue.getValue();

                if (validator != null) {
                  boolean failure = true; // assuming validation throws by default
                  boolean ignored = false;

                  try {

                    final Object result = validator.validate(key, oldValue, value);
                    if (result == IndexEngineValidator.IGNORE) {
                      ignored = true;
                      failure = false;
                      return false;
                    }

                    value = (V) result;
                    failure = false;
                  } finally {
                    if (failure || ignored) {
                      keyBucketCacheEntry.close();
                    }
                  }
                }

                final int valueSize = valueSerializer.getObjectSize(value);
                final byte[] serializeValue = new byte[valueSize];
                valueSerializer.serializeNativeObject(value, serializeValue, 0);
                final byte[] rawKey = serializeKey(key);

                final boolean createLinkToTheValue = valueSize > MAX_EMBEDDED_VALUE_SIZE;
                assert !createLinkToTheValue;

                int insertionIndex;
                final int sizeDiff;
                if (bucketSearchResult.itemIndex >= 0) {

                  assert oldRawValue != null;
                  if (oldRawValue.length == serializeValue.length) {
                    keyBucket.updateValue(
                        bucketSearchResult.itemIndex, serializeValue, rawKey.length);
                    keyBucketCacheEntry.close();
                    return true;
                  } else {
                    keyBucket.removeLeafEntry(bucketSearchResult.itemIndex, rawKey, oldRawValue);
                    insertionIndex = bucketSearchResult.itemIndex;
                    sizeDiff = 0;
                  }
                } else {
                  insertionIndex = -bucketSearchResult.itemIndex - 1;
                  sizeDiff = 1;
                }

                while (!keyBucket.addLeafEntry(insertionIndex, rawKey, serializeValue)) {
                  keyBucketCacheEntry.close();

                  bucketSearchResult =
                      splitBucket(bucketSearchResult.path, insertionIndex, key, atomicOperation);

                  insertionIndex = bucketSearchResult.itemIndex;

                  keyBucketCacheEntry =
                      loadPageForWrite(
                          atomicOperation, fileId, bucketSearchResult.getLastPathItem(), true);

                  //noinspection ObjectAllocationInLoop
                  keyBucket = new SBTreeBucketV1<>(keyBucketCacheEntry);
                }

                keyBucketCacheEntry.close();

                if (sizeDiff != 0) {
                  updateSize(sizeDiff, atomicOperation);
                }
              } else if (updatedValue.isRemove()) {
                final byte[] rawKey = serializeKey(key);
                removeKey(atomicOperation, bucketSearchResult, rawKey);
                keyBucketCacheEntry.close();

              } else if (updatedValue.isNothing()) {
                keyBucketCacheEntry.close();
              }
            } else {
              final CacheEntry cacheEntry;
              boolean isNew = false;

              if (getFilledUpTo(atomicOperation, nullBucketFileId) == 0) {
                cacheEntry = addPage(atomicOperation, nullBucketFileId);
                isNew = true;
              } else {
                cacheEntry = loadPageForWrite(atomicOperation, nullBucketFileId, 0, true);
              }

              int sizeDiff = 0;

              try {
                final SBTreeNullBucketV1<V> nullBucket = new SBTreeNullBucketV1<>(cacheEntry);
                if (isNew) {
                  nullBucket.init();
                }

                final byte[] oldRawValue = nullBucket.getRawValue(valueSerializer);
                final V oldValue =
                    Optional.ofNullable(oldRawValue)
                        .map(rawValue -> valueSerializer.deserializeNativeObject(rawValue, 0))
                        .orElse(null);

                final IndexUpdateAction<V> updatedValue = updater.update(oldValue, bonsayFileId);
                if (updatedValue.isChange()) {
                  final V value = updatedValue.getValue();
                  final int valueSize = valueSerializer.getObjectSize(value);

                  if (validator != null) {
                    final Object result = validator.validate(null, oldValue, value);
                    if (result == IndexEngineValidator.IGNORE) {
                      return false;
                    }
                  }

                  if (oldValue != null) {
                    sizeDiff = -1;
                  }

                  final byte[] serializeValue = new byte[valueSize];
                  valueSerializer.serializeNativeObject(value, serializeValue, 0);

                  nullBucket.setValue(serializeValue, valueSerializer);
                } else if (updatedValue.isRemove()) {
                  removeNullBucket(atomicOperation);
                } else //noinspection StatementWithEmptyBody
                  if (updatedValue.isNothing()) {
                    // Do Nothing
                  }
              } finally {
                cacheEntry.close();
              }

              sizeDiff++;
              updateSize(sizeDiff, atomicOperation);
            }

            return true;
          } finally {
            releaseExclusiveLock();
          }
        });
  }

  private byte[] serializeKey(final K key) {
    return keySerializer.serializeNativeAsWhole(key, (Object[]) keyTypes);
  }

  @Override
  public void close(final boolean flush) {
    acquireExclusiveLock();
    try {
      readCache.closeFile(fileId, flush, writeCache);

      if (nullPointerSupport) {
        readCache.closeFile(nullBucketFileId, flush, writeCache);
      }

    } finally {
      releaseExclusiveLock();
    }
  }

  @Override
  public void close() {
    close(true);
  }

  @Override
  public void delete(AtomicOperation atomicOperation) {
    executeInsideComponentOperation(
        atomicOperation,
        operation -> {
          acquireExclusiveLock();
          try {
            deleteFile(atomicOperation, fileId);

            if (nullPointerSupport) {
              deleteFile(atomicOperation, nullBucketFileId);
            }
          } finally {
            releaseExclusiveLock();
          }
        });
  }

  @Override
  public void load(
      final String name,
      final BinarySerializer<K> keySerializer,
      final BinarySerializer<V> valueSerializer,
      final PropertyType[] keyTypes,
      final int keySize,
      final boolean nullPointerSupport) {
    acquireExclusiveLock();
    try {
      this.keySize = keySize;
      if (keyTypes != null) {
        this.keyTypes = Arrays.copyOf(keyTypes, keyTypes.length);
      } else {
        this.keyTypes = null;
      }

      this.nullPointerSupport = nullPointerSupport;
      final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

      fileId = openFile(atomicOperation, getFullName());
      if (nullPointerSupport) {
        nullBucketFileId = openFile(atomicOperation, name + nullFileExtension);
      }

      this.keySerializer = keySerializer;
      this.valueSerializer = valueSerializer;
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException("Exception during loading of sbtree " + name, this), e);
    } finally {
      releaseExclusiveLock();
    }
  }

  @Override
  public long size() {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

        try (final CacheEntry rootCacheEntry =
            loadPageForRead(atomicOperation, fileId, ROOT_INDEX)) {
          final SBTreeBucketV1<K, V> rootBucket = new SBTreeBucketV1<>(rootCacheEntry);
          return rootBucket.getTreeSize();
        }
      } finally {
        releaseSharedLock();
      }
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException("Error during retrieving of size of index " + getName(), this), e);
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  @Override
  public V remove(AtomicOperation atomicOperation, K k) {
    return calculateInsideComponentOperation(
        atomicOperation,
        operation -> {
          K key = k;
          acquireExclusiveLock();
          try {
            final V removedValue;

            if (key != null) {
              key = keySerializer.preprocess(key, (Object[]) keyTypes);

              final BucketSearchResult bucketSearchResult = findBucket(key, atomicOperation);
              if (bucketSearchResult.itemIndex < 0) {
                return null;
              }

              final byte[] rawKey = serializeKey(key);
              final byte[] rawRemovedValue = removeKey(atomicOperation, bucketSearchResult, rawKey);
              removedValue = valueSerializer.deserializeNativeObject(rawRemovedValue, 0);
            } else {
              if (getFilledUpTo(atomicOperation, nullBucketFileId) == 0) {
                return null;
              }

              removedValue = removeNullBucket(atomicOperation);
            }
            return removedValue;
          } finally {
            releaseExclusiveLock();
          }
        });
  }

  private V removeNullBucket(final AtomicOperation atomicOperation) throws IOException {
    V removedValue;
    try (final CacheEntry nullCacheEntry =
        loadPageForWrite(atomicOperation, nullBucketFileId, 0, true)) {
      final SBTreeNullBucketV1<V> nullBucket = new SBTreeNullBucketV1<>(nullCacheEntry);
      final SBTreeValue<V> treeValue = nullBucket.getValue(valueSerializer);

      if (treeValue != null) {
        removedValue = treeValue.getValue();
        nullBucket.removeValue(valueSerializer);
      } else {
        removedValue = null;
      }
    }

    if (removedValue != null) {
      updateSize(-1, atomicOperation);
    }
    return removedValue;
  }

  private byte[] removeKey(
      final AtomicOperation atomicOperation,
      final BucketSearchResult bucketSearchResult,
      final byte[] key)
      throws IOException {
    byte[] removedValue;
    try (final CacheEntry keyBucketCacheEntry =
        loadPageForWrite(atomicOperation, fileId, bucketSearchResult.getLastPathItem(), true)) {
      final SBTreeBucketV1<K, V> keyBucket = new SBTreeBucketV1<>(keyBucketCacheEntry);

      removedValue =
          keyBucket.getRawValue(
              bucketSearchResult.itemIndex, keySerializer, valueSerializer);
      keyBucket.removeLeafEntry(bucketSearchResult.itemIndex, key, removedValue);
    }

    updateSize(-1, atomicOperation);

    return removedValue;
  }

  @Override
  public Stream<RawPair<K, V>> iterateEntriesMinor(
      final K key, final boolean inclusive, final boolean ascSortOrder) {

    if (!ascSortOrder) {
      return StreamSupport.stream(iterateEntriesMinorDesc(key, inclusive), false);
    }

    return StreamSupport.stream(iterateEntriesMinorAsc(key, inclusive), false);
  }

  @Override
  public Stream<RawPair<K, V>> iterateEntriesMajor(
      final K key, final boolean inclusive, final boolean ascSortOrder) {
    if (ascSortOrder) {
      return StreamSupport.stream(iterateEntriesMajorAsc(key, inclusive), false);
    }

    return StreamSupport.stream(iterateEntriesMajorDesc(key, inclusive), false);
  }

  @Override
  public K firstKey() {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

        final BucketSearchResult searchResult = firstItem(atomicOperation);
        if (searchResult == null) {
          return null;
        }

        try (final CacheEntry cacheEntry =
            loadPageForRead(atomicOperation, fileId, searchResult.getLastPathItem())) {
          final SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(cacheEntry);
          return bucket.getKey(searchResult.itemIndex, keySerializer);
        }
      } finally {
        releaseSharedLock();
      }
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException(
              "Error during finding first key in sbtree [" + getName() + "]", this),
          e);
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  @Override
  public K lastKey() {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

        final BucketSearchResult searchResult = lastItem(atomicOperation);
        if (searchResult == null) {
          return null;
        }

        try (final CacheEntry cacheEntry =
            loadPageForRead(atomicOperation, fileId, searchResult.getLastPathItem())) {
          final SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(cacheEntry);
          return bucket.getKey(searchResult.itemIndex, keySerializer);
        }
      } finally {
        releaseSharedLock();
      }
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException("Error during finding last key in sbtree [" + getName() + "]", this),
          e);
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  @Override
  public Stream<K> keyStream() {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();
        final BucketSearchResult searchResult = firstItem(atomicOperation);
        if (searchResult == null) {
          return StreamSupport.stream(Spliterators.emptySpliterator(), false);
        }

        return StreamSupport.stream(
            new SBTreeFullKeyCursor(searchResult.getLastPathItem()), false);
      } finally {
        releaseSharedLock();
      }
    } catch (final IOException e) {
      throw BaseException.wrapException(
          new SBTreeException(
              "Error during finding first key in sbtree [" + getName() + "]", this),
          e);
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  @Override
  public Stream<RawPair<K, V>> iterateEntriesBetween(
      final K keyFrom,
      final boolean fromInclusive,
      final K keyTo,
      final boolean toInclusive,
      final boolean ascSortOrder) {

    if (ascSortOrder) {
      return StreamSupport.stream(
          iterateEntriesBetweenAscOrder(keyFrom, fromInclusive, keyTo, toInclusive), false);
    } else {
      return StreamSupport.stream(
          iterateEntriesBetweenDescOrder(keyFrom, fromInclusive, keyTo, toInclusive), false);
    }
  }

  @Override
  public void flush() {
    atomicOperationsManager.acquireReadLock(this);
    try {
      acquireSharedLock();
      try {
        writeCache.flush();
      } finally {
        releaseSharedLock();
      }
    } finally {
      atomicOperationsManager.releaseReadLock(this);
    }
  }

  /**
   * Acquires exclusive lock in the active atomic operation running on the current thread for this
   * SB-tree.
   */
  @Override
  public void acquireAtomicExclusiveLock() {
    atomicOperationsManager.acquireExclusiveLockTillOperationComplete(this);
  }

  private void checkNullSupport(final K key) {
    if (key == null && !nullPointerSupport) {
      throw new SBTreeException("Null keys are not supported.", this);
    }
  }

  private void updateSize(final long diffSize, final AtomicOperation atomicOperation)
      throws IOException {

    try (final CacheEntry rootCacheEntry =
        loadPageForWrite(atomicOperation, fileId, ROOT_INDEX, true)) {
      final SBTreeBucketV1<K, V> rootBucket = new SBTreeBucketV1<>(rootCacheEntry);
      rootBucket.setTreeSize(rootBucket.getTreeSize() + diffSize);
    }
  }

  private Spliterator<RawPair<K, V>> iterateEntriesMinorDesc(K key, final boolean inclusive) {
    key = keySerializer.preprocess(key, (Object[]) keyTypes);
    key = enhanceCompositeKeyMinorDesc(key, inclusive);

    return new SBTreeCursorBackward(null, key, false, inclusive);
  }

  private Spliterator<RawPair<K, V>> iterateEntriesMinorAsc(K key, final boolean inclusive) {
    key = keySerializer.preprocess(key, (Object[]) keyTypes);
    key = enhanceCompositeKeyMinorAsc(key, inclusive);

    return new SBTreeCursorForward(null, key, false, inclusive);
  }

  private K enhanceCompositeKeyMinorDesc(K key, final boolean inclusive) {
    final PartialSearchMode partialSearchMode;
    if (inclusive) {
      partialSearchMode = PartialSearchMode.HIGHEST_BOUNDARY;
    } else {
      partialSearchMode = PartialSearchMode.LOWEST_BOUNDARY;
    }

    key = enhanceCompositeKey(key, partialSearchMode);
    return key;
  }

  private K enhanceCompositeKeyMinorAsc(K key, final boolean inclusive) {
    final PartialSearchMode partialSearchMode;
    if (inclusive) {
      partialSearchMode = PartialSearchMode.HIGHEST_BOUNDARY;
    } else {
      partialSearchMode = PartialSearchMode.LOWEST_BOUNDARY;
    }

    key = enhanceCompositeKey(key, partialSearchMode);
    return key;
  }

  private Spliterator<RawPair<K, V>> iterateEntriesMajorAsc(K key, final boolean inclusive) {
    key = keySerializer.preprocess(key, (Object[]) keyTypes);
    key = enhanceCompositeKeyMajorAsc(key, inclusive);

    return new SBTreeCursorForward(key, null, inclusive, false);
  }

  private Spliterator<RawPair<K, V>> iterateEntriesMajorDesc(K key, final boolean inclusive) {
    key = keySerializer.preprocess(key, (Object[]) keyTypes);
    key = enhanceCompositeKeyMajorDesc(key, inclusive);

    return new SBTreeCursorBackward(key, null, inclusive, false);
  }

  private K enhanceCompositeKeyMajorAsc(K key, final boolean inclusive) {
    final PartialSearchMode partialSearchMode;
    if (inclusive) {
      partialSearchMode = PartialSearchMode.LOWEST_BOUNDARY;
    } else {
      partialSearchMode = PartialSearchMode.HIGHEST_BOUNDARY;
    }

    key = enhanceCompositeKey(key, partialSearchMode);
    return key;
  }

  private K enhanceCompositeKeyMajorDesc(K key, final boolean inclusive) {
    final PartialSearchMode partialSearchMode;
    if (inclusive) {
      partialSearchMode = PartialSearchMode.LOWEST_BOUNDARY;
    } else {
      partialSearchMode = PartialSearchMode.HIGHEST_BOUNDARY;
    }

    key = enhanceCompositeKey(key, partialSearchMode);
    return key;
  }

  private BucketSearchResult firstItem(final AtomicOperation atomicOperation) throws IOException {
    final LinkedList<PagePathItemUnit> path = new LinkedList<>();

    long bucketIndex = ROOT_INDEX;

    CacheEntry cacheEntry = loadPageForRead(atomicOperation, fileId, bucketIndex);
    int itemIndex = 0;
    try {
      SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(cacheEntry);

      while (true) {
        if (!bucket.isLeaf()) {
          if (bucket.isEmpty() || itemIndex > bucket.size()) {
            if (!path.isEmpty()) {
              final PagePathItemUnit pagePathItemUnit = path.removeLast();

              bucketIndex = pagePathItemUnit.pageIndex;
              itemIndex = pagePathItemUnit.itemIndex + 1;
            } else {
              return null;
            }
          } else {
            //noinspection ObjectAllocationInLoop
            path.add(new PagePathItemUnit(bucketIndex, itemIndex));

            if (itemIndex < bucket.size()) {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1.SBTreeEntry<K, V> entry =
                  bucket.getEntry(itemIndex, keySerializer, valueSerializer);
              bucketIndex = entry.leftChild;
            } else {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1.SBTreeEntry<K, V> entry =
                  bucket.getEntry(itemIndex - 1, keySerializer, valueSerializer);
              bucketIndex = entry.rightChild;
            }

            itemIndex = 0;
          }
        } else {
          if (bucket.isEmpty()) {
            if (!path.isEmpty()) {
              final PagePathItemUnit pagePathItemUnit = path.removeLast();

              bucketIndex = pagePathItemUnit.pageIndex;
              itemIndex = pagePathItemUnit.itemIndex + 1;
            } else {
              return null;
            }
          } else {
            final LongArrayList resultPath = new LongArrayList(path.size() + 1);
            for (final PagePathItemUnit pathItemUnit : path) {
              resultPath.add(pathItemUnit.pageIndex);
            }

            resultPath.add(bucketIndex);
            return new BucketSearchResult(0, resultPath);
          }
        }

        cacheEntry.close();

        cacheEntry = loadPageForRead(atomicOperation, fileId, bucketIndex);

        //noinspection ObjectAllocationInLoop
        bucket = new SBTreeBucketV1<>(cacheEntry);
      }
    } finally {
      cacheEntry.close();
    }
  }

  private BucketSearchResult lastItem(final AtomicOperation atomicOperation) throws IOException {
    final LinkedList<PagePathItemUnit> path = new LinkedList<>();

    long bucketIndex = ROOT_INDEX;

    CacheEntry cacheEntry = loadPageForRead(atomicOperation, fileId, bucketIndex);

    SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(cacheEntry);

    int itemIndex = bucket.size() - 1;
    try {
      while (true) {
        if (!bucket.isLeaf()) {
          if (itemIndex < -1) {
            if (!path.isEmpty()) {
              final PagePathItemUnit pagePathItemUnit = path.removeLast();

              bucketIndex = pagePathItemUnit.pageIndex;
              itemIndex = pagePathItemUnit.itemIndex - 1;
            } else {
              return null;
            }
          } else {
            //noinspection ObjectAllocationInLoop
            path.add(new PagePathItemUnit(bucketIndex, itemIndex));

            if (itemIndex > -1) {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1.SBTreeEntry<K, V> entry =
                  bucket.getEntry(itemIndex, keySerializer, valueSerializer);
              bucketIndex = entry.rightChild;
            } else {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1.SBTreeEntry<K, V> entry =
                  bucket.getEntry(0, keySerializer, valueSerializer);
              bucketIndex = entry.leftChild;
            }

            itemIndex = SBTreeBucketV1.MAX_PAGE_SIZE_BYTES + 1;
          }
        } else {
          if (bucket.isEmpty()) {
            if (!path.isEmpty()) {
              final PagePathItemUnit pagePathItemUnit = path.removeLast();

              bucketIndex = pagePathItemUnit.pageIndex;
              itemIndex = pagePathItemUnit.itemIndex - 1;
            } else {
              return null;
            }
          } else {
            final LongArrayList resultPath = new LongArrayList(path.size() + 1);
            for (final PagePathItemUnit pathItemUnit : path) {
              resultPath.add(pathItemUnit.pageIndex);
            }

            resultPath.add(bucketIndex);

            return new BucketSearchResult(bucket.size() - 1, resultPath);
          }
        }

        cacheEntry.close();

        cacheEntry = loadPageForRead(atomicOperation, fileId, bucketIndex);

        //noinspection ObjectAllocationInLoop
        bucket = new SBTreeBucketV1<>(cacheEntry);
        if (itemIndex == SBTreeBucketV1.MAX_PAGE_SIZE_BYTES + 1) {
          itemIndex = bucket.size() - 1;
        }
      }
    } finally {
      cacheEntry.close();
    }
  }

  private Spliterator<RawPair<K, V>> iterateEntriesBetweenAscOrder(
      K keyFrom, final boolean fromInclusive, K keyTo, final boolean toInclusive) {
    keyFrom = keySerializer.preprocess(keyFrom, (Object[]) keyTypes);
    keyTo = keySerializer.preprocess(keyTo, (Object[]) keyTypes);

    keyFrom = enhanceFromCompositeKeyBetweenAsc(keyFrom, fromInclusive);
    keyTo = enhanceToCompositeKeyBetweenAsc(keyTo, toInclusive);

    return new SBTreeCursorForward(keyFrom, keyTo, fromInclusive, toInclusive);
  }

  private Spliterator<RawPair<K, V>> iterateEntriesBetweenDescOrder(
      K keyFrom, final boolean fromInclusive, K keyTo, final boolean toInclusive) {
    keyFrom = keySerializer.preprocess(keyFrom, (Object[]) keyTypes);
    keyTo = keySerializer.preprocess(keyTo, (Object[]) keyTypes);

    keyFrom = enhanceFromCompositeKeyBetweenDesc(keyFrom, fromInclusive);
    keyTo = enhanceToCompositeKeyBetweenDesc(keyTo, toInclusive);

    return new SBTreeCursorBackward(keyFrom, keyTo, fromInclusive, toInclusive);
  }

  private K enhanceToCompositeKeyBetweenAsc(K keyTo, final boolean toInclusive) {
    final PartialSearchMode partialSearchModeTo;
    if (toInclusive) {
      partialSearchModeTo = PartialSearchMode.HIGHEST_BOUNDARY;
    } else {
      partialSearchModeTo = PartialSearchMode.LOWEST_BOUNDARY;
    }

    keyTo = enhanceCompositeKey(keyTo, partialSearchModeTo);
    return keyTo;
  }

  private K enhanceFromCompositeKeyBetweenAsc(K keyFrom, final boolean fromInclusive) {
    final PartialSearchMode partialSearchModeFrom;
    if (fromInclusive) {
      partialSearchModeFrom = PartialSearchMode.LOWEST_BOUNDARY;
    } else {
      partialSearchModeFrom = PartialSearchMode.HIGHEST_BOUNDARY;
    }

    keyFrom = enhanceCompositeKey(keyFrom, partialSearchModeFrom);
    return keyFrom;
  }

  private K enhanceToCompositeKeyBetweenDesc(K keyTo, final boolean toInclusive) {
    final PartialSearchMode partialSearchModeTo;
    if (toInclusive) {
      partialSearchModeTo = PartialSearchMode.HIGHEST_BOUNDARY;
    } else {
      partialSearchModeTo = PartialSearchMode.LOWEST_BOUNDARY;
    }

    keyTo = enhanceCompositeKey(keyTo, partialSearchModeTo);
    return keyTo;
  }

  private K enhanceFromCompositeKeyBetweenDesc(K keyFrom, final boolean fromInclusive) {
    final PartialSearchMode partialSearchModeFrom;
    if (fromInclusive) {
      partialSearchModeFrom = PartialSearchMode.LOWEST_BOUNDARY;
    } else {
      partialSearchModeFrom = PartialSearchMode.HIGHEST_BOUNDARY;
    }

    keyFrom = enhanceCompositeKey(keyFrom, partialSearchModeFrom);
    return keyFrom;
  }

  private BucketSearchResult splitBucket(
      final LongList path,
      final int keyIndex,
      final K keyToInsert,
      final AtomicOperation atomicOperation)
      throws IOException {
    final long pageIndex = path.getLong(path.size() - 1);

    try (final CacheEntry bucketEntry =
        loadPageForWrite(atomicOperation, fileId, pageIndex, true)) {
      final SBTreeBucketV1<K, V> bucketToSplit = new SBTreeBucketV1<>(bucketEntry);

      final boolean splitLeaf = bucketToSplit.isLeaf();
      final int bucketSize = bucketToSplit.size();

      final int indexToSplit = bucketSize >>> 1;
      final K separationKey = bucketToSplit.getKey(indexToSplit, keySerializer);
      final List<byte[]> rightEntries = new ArrayList<>(indexToSplit);

      final int startRightIndex = splitLeaf ? indexToSplit : indexToSplit + 1;

      for (int i = startRightIndex; i < bucketSize; i++) {
        rightEntries.add(
            bucketToSplit.getRawEntry(i, keySerializer, valueSerializer));
      }

      if (pageIndex != ROOT_INDEX) {
        return splitNonRootBucket(
            path,
            keyIndex,
            keyToInsert,
            pageIndex,
            bucketToSplit,
            splitLeaf,
            indexToSplit,
            separationKey,
            rightEntries,
            atomicOperation);
      } else {
        return splitRootBucket(
            path,
            keyIndex,
            keyToInsert,
            bucketEntry,
            bucketToSplit,
            splitLeaf,
            indexToSplit,
            separationKey,
            rightEntries,
            atomicOperation);
      }
    }
  }

  private BucketSearchResult splitNonRootBucket(
      final LongList path,
      final int keyIndex,
      final K keyToInsert,
      final long pageIndex,
      final SBTreeBucketV1<K, V> bucketToSplit,
      final boolean splitLeaf,
      final int indexToSplit,
      final K separationKey,
      final List<byte[]> rightEntries,
      final AtomicOperation atomicOperation)
      throws IOException {

    long rightPageIndex;
    try (final CacheEntry rightBucketEntry = addPage(atomicOperation, fileId)) {
      rightPageIndex = rightBucketEntry.getPageIndex();
      final SBTreeBucketV1<K, V> newRightBucket = new SBTreeBucketV1<>(rightBucketEntry);
      newRightBucket.init(splitLeaf);
      newRightBucket.addAll(rightEntries);

      bucketToSplit.shrink(indexToSplit, keySerializer, valueSerializer);
      if (splitLeaf) {
        final long rightSiblingPageIndex = bucketToSplit.getRightSibling();

        newRightBucket.setRightSibling(rightSiblingPageIndex);
        newRightBucket.setLeftSibling(pageIndex);

        bucketToSplit.setRightSibling(rightBucketEntry.getPageIndex());

        if (rightSiblingPageIndex >= 0) {

          try (final CacheEntry rightSiblingBucketEntry =
              loadPageForWrite(atomicOperation, fileId, rightSiblingPageIndex, true)) {
            final SBTreeBucketV1<K, V> rightSiblingBucket =
                new SBTreeBucketV1<>(rightSiblingBucketEntry);
            rightSiblingBucket.setLeftSibling(rightBucketEntry.getPageIndex());
          }
        }
      }

      long parentIndex = path.getLong(path.size() - 2);
      CacheEntry parentCacheEntry = loadPageForWrite(atomicOperation, fileId, parentIndex, true);
      try {
        SBTreeBucketV1<K, V> parentBucket = new SBTreeBucketV1<>(parentCacheEntry);
        int insertionIndex = parentBucket.find(separationKey, keySerializer);
        assert insertionIndex < 0;

        insertionIndex = -insertionIndex - 1;
        final byte[] rawSeparationKey = serializeKey(separationKey);
        while (!parentBucket.addNonLeafEntry(
            insertionIndex, rawSeparationKey, pageIndex, rightBucketEntry.getPageIndex(), true)) {
          parentCacheEntry.close();
          final BucketSearchResult bucketSearchResult =
              splitBucket(
                  path.subList(0, path.size() - 1), insertionIndex, separationKey, atomicOperation);

          parentIndex = bucketSearchResult.getLastPathItem();
          parentCacheEntry = loadPageForWrite(atomicOperation, fileId, parentIndex, true);

          insertionIndex = bucketSearchResult.itemIndex;

          //noinspection ObjectAllocationInLoop
          parentBucket = new SBTreeBucketV1<>(parentCacheEntry);
        }

      } finally {
        parentCacheEntry.close();
      }
    }

    final LongArrayList resultPath = new LongArrayList(path.subList(0, path.size() - 1));

    if (comparator.compare(keyToInsert, separationKey) < 0) {
      resultPath.add(pageIndex);
      return new BucketSearchResult(keyIndex, resultPath);
    }

    resultPath.add(rightPageIndex);
    if (splitLeaf) {
      return new BucketSearchResult(keyIndex - indexToSplit, resultPath);
    }

    resultPath.add(rightPageIndex);
    return new BucketSearchResult(keyIndex - indexToSplit - 1, resultPath);
  }

  private BucketSearchResult splitRootBucket(
      final LongList path,
      final int keyIndex,
      final K keyToInsert,
      final CacheEntry bucketEntry,
      SBTreeBucketV1<K, V> bucketToSplit,
      final boolean splitLeaf,
      final int indexToSplit,
      final K separationKey,
      final List<byte[]> rightEntries,
      final AtomicOperation atomicOperation)
      throws IOException {
    final long treeSize = bucketToSplit.getTreeSize();

    final List<byte[]> leftEntries = new ArrayList<>(indexToSplit);

    for (int i = 0; i < indexToSplit; i++) {
      leftEntries.add(
          bucketToSplit.getRawEntry(i, keySerializer, valueSerializer));
    }

    final CacheEntry rightBucketEntry = addPage(atomicOperation, fileId);

    long leftBucketIndex;
    long rightBucketIndex = rightBucketEntry.getPageIndex();

    try (final CacheEntry leftBucketEntry = addPage(atomicOperation, fileId)) {
      leftBucketIndex = leftBucketEntry.getPageIndex();
      final SBTreeBucketV1<K, V> newLeftBucket = new SBTreeBucketV1<>(leftBucketEntry);
      newLeftBucket.init(splitLeaf);
      newLeftBucket.addAll(leftEntries);

      if (splitLeaf) {
        newLeftBucket.setRightSibling(rightBucketEntry.getPageIndex());
      }
    }

    try (CacheEntry entry = rightBucketEntry) {
      final SBTreeBucketV1<K, V> newRightBucket = new SBTreeBucketV1<>(entry);
      newRightBucket.init(splitLeaf);
      newRightBucket.addAll(rightEntries);

      if (splitLeaf) {
        newRightBucket.setLeftSibling(leftBucketIndex);
      }
    } finally {
      rightBucketEntry.close();
    }

    bucketToSplit = new SBTreeBucketV1<>(bucketEntry);
    bucketToSplit.shrink(0, keySerializer, valueSerializer);
    if (splitLeaf) {
      bucketToSplit.switchBucketType();
    }

    bucketToSplit.setTreeSize(treeSize);
    bucketToSplit.addNonLeafEntry(
        0, serializeKey(separationKey), leftBucketIndex, rightBucketIndex, true);

    final LongArrayList resultPath = new LongArrayList(path.subList(0, path.size() - 1));
    if (comparator.compare(keyToInsert, separationKey) < 0) {
      resultPath.add(leftBucketIndex);
      return new BucketSearchResult(keyIndex, resultPath);
    }

    resultPath.add(rightBucketIndex);

    if (splitLeaf) {
      return new BucketSearchResult(keyIndex - indexToSplit, resultPath);
    }

    return new BucketSearchResult(keyIndex - indexToSplit - 1, resultPath);
  }

  private BucketSearchResult findBucket(final K key, final AtomicOperation atomicOperation)
      throws IOException {
    long pageIndex = ROOT_INDEX;
    final LongArrayList path = new LongArrayList(8);

    while (true) {
      if (path.size() > MAX_PATH_LENGTH) {
        throw new SBTreeException(
            "We reached max level of depth of SBTree but still found nothing, seems like tree is in"
                + " corrupted state. You should rebuild index related to given query.",
            this);
      }

      path.add(pageIndex);

      final SBTreeBucketV1.SBTreeEntry<K, V> entry;

      try (final CacheEntry bucketEntry = loadPageForRead(atomicOperation, fileId, pageIndex)) {
        @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1<K, V> keyBucket = new SBTreeBucketV1<>(
            bucketEntry);
        final int index = keyBucket.find(key, keySerializer);

        if (keyBucket.isLeaf()) {
          return new BucketSearchResult(index, path);
        }

        if (index >= 0) {
          //noinspection ObjectAllocationInLoop
          entry = keyBucket.getEntry(index, keySerializer, valueSerializer);
        } else {
          final int insertionIndex = -index - 1;
          if (insertionIndex >= keyBucket.size()) {
            //noinspection ObjectAllocationInLoop
            entry =
                keyBucket.getEntry(insertionIndex - 1, keySerializer, valueSerializer);
          } else {
            //noinspection ObjectAllocationInLoop
            entry = keyBucket.getEntry(insertionIndex, keySerializer, valueSerializer);
          }
        }
      }

      if (comparator.compare(key, entry.key) >= 0) {
        pageIndex = entry.rightChild;
      } else {
        pageIndex = entry.leftChild;
      }
    }
  }

  private K enhanceCompositeKey(final K key, final PartialSearchMode partialSearchMode) {
    if (!(key instanceof CompositeKey compositeKey)) {
      return key;
    }

    if (!(keySize == 1
        || compositeKey.getKeys().size() == keySize
        || partialSearchMode.equals(PartialSearchMode.NONE))) {
      final CompositeKey fullKey = new CompositeKey(compositeKey);
      final int itemsToAdd = keySize - fullKey.getKeys().size();

      final Comparable<?> keyItem;
      if (partialSearchMode.equals(PartialSearchMode.HIGHEST_BOUNDARY)) {
        keyItem = ALWAYS_GREATER_KEY;
      } else {
        keyItem = ALWAYS_LESS_KEY;
      }

      for (int i = 0; i < itemsToAdd; i++) {
        fullKey.addKey(keyItem);
      }

      //noinspection unchecked
      return (K) fullKey;
    }

    return key;
  }

  private RawPair<K, V> convertToMapEntry(final SBTreeBucketV1.SBTreeEntry<K, V> treeEntry) {
    final K key = treeEntry.key;
    final V value = treeEntry.value.getValue();

    return new RawPair<>(key, value);
  }

  /**
   * Indicates search behavior in case of {@link CompositeKey} keys that have less amount of
   * internal keys are used, whether lowest or highest partially matched key should be used.
   */
  private enum PartialSearchMode {
    /**
     * Any partially matched key will be used as search result.
     */
    NONE,
    /**
     * The biggest partially matched key will be used as search result.
     */
    HIGHEST_BOUNDARY,

    /**
     * The smallest partially matched key will be used as search result.
     */
    LOWEST_BOUNDARY
  }

  private static class BucketSearchResult {

    private final int itemIndex;
    private final LongArrayList path;

    private BucketSearchResult(final int itemIndex, final LongArrayList path) {
      this.itemIndex = itemIndex;
      this.path = path;
    }

    long getLastPathItem() {
      return path.getLong(path.size() - 1);
    }
  }

  private static final class PagePathItemUnit {

    private final long pageIndex;
    private final int itemIndex;

    private PagePathItemUnit(final long pageIndex, final int itemIndex) {
      this.pageIndex = pageIndex;
      this.itemIndex = itemIndex;
    }
  }

  private class SBTreeFullKeyCursor implements Spliterator<K> {

    private long pageIndex;
    private int itemIndex;

    private List<K> keysCache = new ArrayList<>();
    private Iterator<K> keysIterator = new EmptyIterator<>();

    private SBTreeFullKeyCursor(final long startPageIndex) {
      pageIndex = startPageIndex;
      itemIndex = 0;
    }

    @Override
    public boolean tryAdvance(Consumer<? super K> action) {
      if (keysIterator == null) {
        return false;
      }

      if (keysIterator.hasNext()) {
        action.accept(keysIterator.next());
        return true;
      }

      keysCache.clear();

      final int prefetchSize = GlobalConfiguration.INDEX_CURSOR_PREFETCH_SIZE.getValueAsInteger();
      atomicOperationsManager.acquireReadLock(SBTreeV1.this);
      try {
        acquireSharedLock();
        try {
          final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

          while (true) {
            if (pageIndex == -1) {
              break;
            }

            if (pageIndex >= getFilledUpTo(atomicOperation, fileId)) {
              pageIndex = -1;
              break;
            }

            try (final CacheEntry cacheEntry =
                loadPageForRead(atomicOperation, fileId, pageIndex)) {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(
                  cacheEntry);

              if (itemIndex >= bucket.size()) {
                pageIndex = bucket.getRightSibling();
                itemIndex = 0;

                if (keysCache.size() >= prefetchSize) {
                  break;
                }

                continue;
              }

              @SuppressWarnings("ObjectAllocationInLoop") final K key =
                  bucket.getEntry(itemIndex, keySerializer, valueSerializer).key;
              itemIndex++;

              keysCache.add(key);
            }
          }
        } finally {
          releaseSharedLock();
        }
      } catch (final IOException e) {
        throw BaseException.wrapException(
            new SBTreeException("Error during entity iteration", SBTreeV1.this), e);
      } finally {
        atomicOperationsManager.releaseReadLock(SBTreeV1.this);
      }

      if (keysCache.isEmpty()) {
        keysCache = null;
        return false;
      }

      keysIterator = keysCache.iterator();
      action.accept(keysIterator.next());
      return true;
    }

    @Override
    public Spliterator<K> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return SORTED | NONNULL | ORDERED;
    }

    @Override
    public Comparator<? super K> getComparator() {
      return comparator;
    }
  }

  private final class SBTreeCursorForward implements Spliterator<RawPair<K, V>> {

    private K fromKey;
    private final K toKey;
    private boolean fromKeyInclusive;
    private final boolean toKeyInclusive;

    private final List<RawPair<K, V>> dataCache = new ArrayList<>();
    private Iterator<RawPair<K, V>> dataCacheIterator = Collections.emptyIterator();

    private SBTreeCursorForward(
        final K fromKey,
        final K toKey,
        final boolean fromKeyInclusive,
        final boolean toKeyInclusive) {
      this.fromKey = fromKey;
      this.toKey = toKey;
      this.fromKeyInclusive = fromKeyInclusive;
      this.toKeyInclusive = toKeyInclusive;

      if (fromKey == null) {
        this.fromKeyInclusive = true;
      }
    }

    @Override
    public boolean tryAdvance(Consumer<? super RawPair<K, V>> action) {
      if (dataCacheIterator == null) {
        return false;
      }

      if (dataCacheIterator.hasNext()) {
        final RawPair<K, V> entry = dataCacheIterator.next();

        fromKey = entry.first;
        fromKeyInclusive = false;

        action.accept(entry);
        return true;
      }

      dataCache.clear();

      final int prefetchSize = GlobalConfiguration.INDEX_CURSOR_PREFETCH_SIZE.getValueAsInteger();

      atomicOperationsManager.acquireReadLock(SBTreeV1.this);
      try {
        acquireSharedLock();
        try {
          final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

          final BucketSearchResult bucketSearchResult;

          if (fromKey != null) {
            bucketSearchResult = findBucket(fromKey, atomicOperation);
          } else {
            bucketSearchResult = firstItem(atomicOperation);
          }

          if (bucketSearchResult == null) {
            dataCacheIterator = null;
            return false;
          }

          long pageIndex = bucketSearchResult.getLastPathItem();
          int itemIndex;

          if (bucketSearchResult.itemIndex >= 0) {
            itemIndex =
                fromKeyInclusive ? bucketSearchResult.itemIndex : bucketSearchResult.itemIndex + 1;
          } else {
            itemIndex = -bucketSearchResult.itemIndex - 1;
          }

          while (dataCache.size() < prefetchSize) {
            if (pageIndex == -1) {
              break;
            }

            try (final CacheEntry cacheEntry =
                loadPageForRead(atomicOperation, fileId, pageIndex)) {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(
                  cacheEntry);

              if (itemIndex >= bucket.size()) {
                pageIndex = bucket.getRightSibling();
                itemIndex = 0;
                continue;
              }

              @SuppressWarnings("ObjectAllocationInLoop") final RawPair<K, V> entry =
                  convertToMapEntry(
                      bucket.getEntry(itemIndex, keySerializer, valueSerializer));
              itemIndex++;

              if (fromKey != null) {
                if (fromKeyInclusive) {
                  if (comparator.compare(entry.first, fromKey) < 0) {
                    continue;
                  }
                } else if (comparator.compare(entry.first, fromKey) <= 0) {
                  continue;
                }
              }

              if (toKey != null) {
                if (toKeyInclusive) {
                  if (comparator.compare(entry.first, toKey) > 0) {
                    break;
                  }
                } else if (comparator.compare(entry.first, toKey) >= 0) {
                  break;
                }
              }

              dataCache.add(entry);
            }
          }
        } finally {
          releaseSharedLock();
        }
      } catch (final IOException e) {
        throw BaseException.wrapException(
            new SBTreeException("Error during entity iteration", SBTreeV1.this), e);
      } finally {
        atomicOperationsManager.releaseReadLock(SBTreeV1.this);
      }

      if (dataCache.isEmpty()) {
        dataCacheIterator = null;
        return false;
      }

      dataCacheIterator = dataCache.iterator();

      final RawPair<K, V> entry = dataCacheIterator.next();

      fromKey = entry.first;
      fromKeyInclusive = false;

      action.accept(entry);
      return true;
    }

    @Override
    public Spliterator<RawPair<K, V>> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return SORTED | NONNULL | ORDERED;
    }

    @Override
    public Comparator<? super RawPair<K, V>> getComparator() {
      return (pairOne, pairTwo) -> comparator.compare(pairOne.first, pairTwo.first);
    }
  }

  private final class SBTreeCursorBackward implements Spliterator<RawPair<K, V>> {

    private final K fromKey;
    private K toKey;
    private final boolean fromKeyInclusive;
    private boolean toKeyInclusive;

    private final List<RawPair<K, V>> dataCache = new ArrayList<>();
    private Iterator<RawPair<K, V>> dataCacheIterator = Collections.emptyIterator();

    private SBTreeCursorBackward(
        final K fromKey,
        final K toKey,
        final boolean fromKeyInclusive,
        final boolean toKeyInclusive) {
      this.fromKey = fromKey;
      this.toKey = toKey;
      this.fromKeyInclusive = fromKeyInclusive;
      this.toKeyInclusive = toKeyInclusive;

      if (toKey == null) {
        this.toKeyInclusive = true;
      }
    }

    @Override
    public boolean tryAdvance(Consumer<? super RawPair<K, V>> action) {
      if (dataCacheIterator == null) {
        return false;
      }

      if (dataCacheIterator.hasNext()) {
        final RawPair<K, V> entry = dataCacheIterator.next();
        toKey = entry.first;

        toKeyInclusive = false;
        action.accept(entry);
        return true;
      }

      dataCache.clear();

      final int prefetchSize = GlobalConfiguration.INDEX_CURSOR_PREFETCH_SIZE.getValueAsInteger();

      atomicOperationsManager.acquireReadLock(SBTreeV1.this);
      try {
        acquireSharedLock();
        try {
          final AtomicOperation atomicOperation = atomicOperationsManager.getCurrentOperation();

          final BucketSearchResult bucketSearchResult;

          if (toKey != null) {
            bucketSearchResult = findBucket(toKey, atomicOperation);
          } else {
            bucketSearchResult = lastItem(atomicOperation);
          }

          if (bucketSearchResult == null) {
            dataCacheIterator = null;
            return false;
          }

          long pageIndex = bucketSearchResult.getLastPathItem();

          int itemIndex;
          if (bucketSearchResult.itemIndex >= 0) {
            itemIndex =
                toKeyInclusive ? bucketSearchResult.itemIndex : bucketSearchResult.itemIndex - 1;
          } else {
            itemIndex = -bucketSearchResult.itemIndex - 2;
          }

          while (dataCache.size() < prefetchSize) {
            if (pageIndex == -1) {
              break;
            }

            try (final CacheEntry cacheEntry =
                loadPageForRead(atomicOperation, fileId, pageIndex)) {
              @SuppressWarnings("ObjectAllocationInLoop") final SBTreeBucketV1<K, V> bucket = new SBTreeBucketV1<>(
                  cacheEntry);

              if (itemIndex >= bucket.size()) {
                itemIndex = bucket.size() - 1;
              }

              if (itemIndex < 0) {
                pageIndex = bucket.getLeftSibling();
                itemIndex = Integer.MAX_VALUE;
                continue;
              }

              @SuppressWarnings("ObjectAllocationInLoop") final RawPair<K, V> entry =
                  convertToMapEntry(
                      bucket.getEntry(itemIndex, keySerializer, valueSerializer));
              itemIndex--;

              if (toKey != null) {
                if (toKeyInclusive) {
                  if (comparator.compare(entry.first, toKey) > 0) {
                    continue;
                  }
                } else if (comparator.compare(entry.first, toKey) >= 0) {
                  continue;
                }
              }

              if (fromKey != null) {
                if (fromKeyInclusive) {
                  if (comparator.compare(entry.first, fromKey) < 0) {
                    break;
                  }
                } else if (comparator.compare(entry.first, fromKey) <= 0) {
                  break;
                }
              }

              dataCache.add(entry);
            }
          }
        } finally {
          releaseSharedLock();
        }
      } catch (final IOException e) {
        throw BaseException.wrapException(
            new SBTreeException("Error during entity iteration", SBTreeV1.this), e);
      } finally {
        atomicOperationsManager.releaseReadLock(SBTreeV1.this);
      }

      if (dataCache.isEmpty()) {
        dataCacheIterator = null;
        return false;
      }

      dataCacheIterator = dataCache.iterator();

      final RawPair<K, V> entry = dataCacheIterator.next();

      toKey = entry.first;
      toKeyInclusive = false;

      action.accept(entry);
      return true;
    }

    @Override
    public Spliterator<RawPair<K, V>> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
      return SORTED | NONNULL | ORDERED;
    }

    @Override
    public Comparator<? super RawPair<K, V>> getComparator() {
      return (pairOne, pairTwo) -> -comparator.compare(pairOne.first, pairTwo.first);
    }
  }
}
