package com.jetbrains.youtrack.db.internal.core.storage.index.sbtree.singlevalue.v3;

import com.jetbrains.youtrack.db.internal.common.util.RawPair;
import com.jetbrains.youtrack.db.api.record.RID;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.LogSequenceNumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public final class SpliteratorBackward<K> implements Spliterator<RawPair<K, RID>> {

  /**
   *
   */
  private final CellBTreeSingleValueV3<K> btree;

  private final K fromKey;
  private final K toKey;
  private final boolean fromKeyInclusive;
  private final boolean toKeyInclusive;

  private int pageIndex = -1;
  private int itemIndex = -1;

  private LogSequenceNumber lastLSN = null;

  private final List<RawPair<K, RID>> dataCache = new ArrayList<>();
  private Iterator<RawPair<K, RID>> cacheIterator = Collections.emptyIterator();

  public SpliteratorBackward(
      CellBTreeSingleValueV3<K> cellBTreeSingleValueV3,
      final K fromKey,
      final K toKey,
      final boolean fromKeyInclusive,
      final boolean toKeyInclusive) {
    btree = cellBTreeSingleValueV3;
    this.fromKey = fromKey;
    this.toKey = toKey;
    this.fromKeyInclusive = fromKeyInclusive;
    this.toKeyInclusive = toKeyInclusive;
  }

  @Override
  public boolean tryAdvance(Consumer<? super RawPair<K, RID>> action) {
    if (cacheIterator == null) {
      return false;
    }

    if (cacheIterator.hasNext()) {
      action.accept(cacheIterator.next());
      return true;
    }

    btree.fetchBackwardNextCachePortion(this);

    cacheIterator = dataCache.iterator();

    if (cacheIterator.hasNext()) {
      action.accept(cacheIterator.next());
      return true;
    }

    cacheIterator = null;

    return false;
  }

  @Override
  public Spliterator<RawPair<K, RID>> trySplit() {
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
  public Comparator<? super RawPair<K, RID>> getComparator() {
    return (pairOne, pairTwo) -> -btree.comparator.compare(pairOne.first, pairTwo.first);
  }

  public int getItemIndex() {
    return itemIndex;
  }

  public void setItemIndex(int itemIndex) {
    this.itemIndex = itemIndex;
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public void setPageIndex(int pageIndex) {
    this.pageIndex = pageIndex;
  }

  List<RawPair<K, RID>> getDataCache() {
    return dataCache;
  }

  LogSequenceNumber getLastLSN() {
    return lastLSN;
  }

  void setLastLSN(LogSequenceNumber lastLSN) {
    this.lastLSN = lastLSN;
  }

  K getFromKey() {
    return fromKey;
  }

  K getToKey() {
    return toKey;
  }

  boolean isFromKeyInclusive() {
    return fromKeyInclusive;
  }

  boolean isToKeyInclusive() {
    return toKeyInclusive;
  }

  public void setCacheIterator(Iterator<RawPair<K, RID>> cacheIterator) {
    this.cacheIterator = cacheIterator;
  }
}
