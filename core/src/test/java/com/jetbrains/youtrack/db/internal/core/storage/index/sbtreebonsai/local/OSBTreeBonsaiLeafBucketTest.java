package com.jetbrains.youtrack.db.internal.core.storage.index.sbtreebonsai.local;

import com.jetbrains.youtrack.db.internal.common.directmemory.OByteBufferPool;
import com.jetbrains.youtrack.db.internal.common.directmemory.ODirectMemoryAllocator.Intention;
import com.jetbrains.youtrack.db.internal.common.directmemory.OPointer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OLongSerializer;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.binary.impl.OLinkSerializer;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCacheEntry;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCacheEntryImpl;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCachePointer;
import com.jetbrains.youtrack.db.internal.core.storage.index.sbtreebonsai.local.OBonsaiBucketPointer;
import com.jetbrains.youtrack.db.internal.core.storage.index.sbtreebonsai.local.OSBTreeBonsaiBucket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
 * @since 09.08.13
 */
public class OSBTreeBonsaiLeafBucketTest {

  @Test
  public void testInitialization() throws Exception {
    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);
    Assert.assertEquals(treeBucket.size(), 0);
    Assert.assertTrue(treeBucket.isLeaf());

    treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);
    Assert.assertEquals(treeBucket.size(), 0);
    Assert.assertTrue(treeBucket.isLeaf());
    Assert.assertFalse(treeBucket.getLeftSibling().isValid());
    Assert.assertFalse(treeBucket.getRightSibling().isValid());

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testSearch() throws Exception {
    long seed = System.currentTimeMillis();
    System.out.println("testSearch seed : " + seed);

    TreeSet<Long> keys = new TreeSet<>();
    Random random = new Random(seed);

    while (keys.size()
        < 2 * OSBTreeBonsaiBucket.MAX_BUCKET_SIZE_BYTES / OLongSerializer.LONG_SIZE) {
      keys.add(random.nextLong());
    }

    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);

    int index = 0;
    Map<Long, Integer> keyIndexMap = new HashMap<>();
    for (Long key : keys) {
      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }
      keyIndexMap.put(key, index);
      index++;
    }

    Assert.assertEquals(treeBucket.size(), keyIndexMap.size());

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      int bucketIndex = treeBucket.find(keyIndexEntry.getKey());
      Assert.assertEquals(bucketIndex, (int) keyIndexEntry.getValue());
    }

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testUpdateValue() throws Exception {
    long seed = System.currentTimeMillis();
    System.out.println("testUpdateValue seed : " + seed);

    TreeSet<Long> keys = new TreeSet<>();
    Random random = new Random(seed);

    while (keys.size()
        < 2 * OSBTreeBonsaiBucket.MAX_BUCKET_SIZE_BYTES / OLongSerializer.LONG_SIZE) {
      keys.add(random.nextLong());
    }

    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);

    Map<Long, Integer> keyIndexMap = new HashMap<>();
    int index = 0;
    for (Long key : keys) {
      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }

      keyIndexMap.put(key, index);
      index++;
    }

    Assert.assertEquals(keyIndexMap.size(), treeBucket.size());

    for (int i = 0; i < treeBucket.size(); i++) {
      treeBucket.updateValue(i, new YTRecordId(i + 5, i + 5));
    }

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable> entry =
          treeBucket.getEntry(keyIndexEntry.getValue());

      Assert.assertEquals(
          entry,
          new OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              keyIndexEntry.getKey(),
              new YTRecordId(keyIndexEntry.getValue() + 5, keyIndexEntry.getValue() + 5)));
      Assert.assertEquals(keyIndexEntry.getKey(), treeBucket.getKey(keyIndexEntry.getValue()));
    }

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testShrink() throws Exception {
    long seed = System.currentTimeMillis();
    System.out.println("testShrink seed : " + seed);

    TreeSet<Long> keys = new TreeSet<>();
    Random random = new Random(seed);

    while (keys.size()
        < 2 * OSBTreeBonsaiBucket.MAX_BUCKET_SIZE_BYTES / OLongSerializer.LONG_SIZE) {
      keys.add(random.nextLong());
    }

    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    cachePointer.incrementReferrer();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);

    int index = 0;
    for (Long key : keys) {
      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }

      index++;
    }

    int originalSize = treeBucket.size();

    treeBucket.shrink(treeBucket.size() / 2);
    Assert.assertEquals(treeBucket.size(), index / 2);

    index = 0;
    final Map<Long, Integer> keyIndexMap = new HashMap<>();

    Iterator<Long> keysIterator = keys.iterator();
    while (keysIterator.hasNext() && index < treeBucket.size()) {
      Long key = keysIterator.next();
      keyIndexMap.put(key, index);
      index++;
    }

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      int bucketIndex = treeBucket.find(keyIndexEntry.getKey());
      Assert.assertEquals(bucketIndex, (int) keyIndexEntry.getValue());
    }

    int keysToAdd = originalSize - treeBucket.size();
    int addedKeys = 0;
    while (keysIterator.hasNext() && index < originalSize) {
      Long key = keysIterator.next();

      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }

      keyIndexMap.put(key, index);
      index++;
      addedKeys++;
    }

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable> entry =
          treeBucket.getEntry(keyIndexEntry.getValue());

      Assert.assertEquals(
          entry,
          new OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              keyIndexEntry.getKey(),
              new YTRecordId(keyIndexEntry.getValue(), keyIndexEntry.getValue())));
    }

    Assert.assertEquals(treeBucket.size(), originalSize);
    Assert.assertEquals(addedKeys, keysToAdd);

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testRemove() throws Exception {
    long seed = System.currentTimeMillis();
    System.out.println("testRemove seed : " + seed);

    TreeSet<Long> keys = new TreeSet<>();
    Random random = new Random(seed);

    while (keys.size()
        < 2 * OSBTreeBonsaiBucket.MAX_BUCKET_SIZE_BYTES / OLongSerializer.LONG_SIZE) {
      keys.add(random.nextLong());
    }

    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);

    int index = 0;
    for (Long key : keys) {
      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }

      index++;
    }

    int originalSize = treeBucket.size();

    int itemsToDelete = originalSize / 2;
    for (int i = 0; i < itemsToDelete; i++) {
      treeBucket.remove(treeBucket.size() - 1);
    }

    Assert.assertEquals(treeBucket.size(), originalSize - itemsToDelete);

    final Map<Long, Integer> keyIndexMap = new HashMap<>();
    Iterator<Long> keysIterator = keys.iterator();

    index = 0;
    while (keysIterator.hasNext() && index < treeBucket.size()) {
      Long key = keysIterator.next();
      keyIndexMap.put(key, index);
      index++;
    }

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      int bucketIndex = treeBucket.find(keyIndexEntry.getKey());
      Assert.assertEquals(bucketIndex, (int) keyIndexEntry.getValue());
    }

    int keysToAdd = originalSize - treeBucket.size();
    int addedKeys = 0;
    while (keysIterator.hasNext() && index < originalSize) {
      Long key = keysIterator.next();

      if (!treeBucket.addEntry(
          index,
          new OSBTreeBonsaiBucket.SBTreeEntry<>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              key,
              new YTRecordId(index, index)),
          true)) {
        break;
      }

      keyIndexMap.put(key, index);
      index++;
      addedKeys++;
    }

    for (Map.Entry<Long, Integer> keyIndexEntry : keyIndexMap.entrySet()) {
      OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable> entry =
          treeBucket.getEntry(keyIndexEntry.getValue());

      Assert.assertEquals(
          entry,
          new OSBTreeBonsaiBucket.SBTreeEntry<Long, YTIdentifiable>(
              OBonsaiBucketPointer.NULL,
              OBonsaiBucketPointer.NULL,
              keyIndexEntry.getKey(),
              new YTRecordId(keyIndexEntry.getValue(), keyIndexEntry.getValue())));
    }

    Assert.assertEquals(treeBucket.size(), originalSize);
    Assert.assertEquals(addedKeys, keysToAdd);

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testSetLeftSibling() throws Exception {
    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);
    final OBonsaiBucketPointer p = new OBonsaiBucketPointer(123, 8192 * 2);
    treeBucket.setLeftSibling(p);
    Assert.assertEquals(treeBucket.getLeftSibling(), p);

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }

  @Test
  public void testSetRightSibling() throws Exception {
    OByteBufferPool bufferPool = OByteBufferPool.instance(null);
    OPointer pointer = bufferPool.acquireDirect(true, Intention.TEST);

    OCachePointer cachePointer = new OCachePointer(pointer, bufferPool, 0, 0);
    cachePointer.incrementReferrer();

    OCacheEntry cacheEntry = new OCacheEntryImpl(0, 0, cachePointer, false, null);
    cacheEntry.acquireExclusiveLock();

    OSBTreeBonsaiBucket<Long, YTIdentifiable> treeBucket =
        new OSBTreeBonsaiBucket<>(
            cacheEntry, 0, true, OLongSerializer.INSTANCE, OLinkSerializer.INSTANCE, null);
    final OBonsaiBucketPointer p = new OBonsaiBucketPointer(123, 8192 * 2);
    treeBucket.setRightSibling(p);
    Assert.assertEquals(treeBucket.getRightSibling(), p);

    cacheEntry.releaseExclusiveLock();
    cachePointer.decrementReferrer();
  }
}