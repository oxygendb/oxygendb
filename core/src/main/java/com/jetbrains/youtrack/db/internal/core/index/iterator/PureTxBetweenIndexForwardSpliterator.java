package com.jetbrains.youtrack.db.internal.core.index.iterator;

import com.jetbrains.youtrack.db.internal.common.comparator.ODefaultComparator;
import com.jetbrains.youtrack.db.internal.common.util.ORawPair;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.index.OIndexOneValue;
import com.jetbrains.youtrack.db.internal.core.index.comparator.AscComparator;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionIndexChanges;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class PureTxBetweenIndexForwardSpliterator implements Spliterator<ORawPair<Object, YTRID>> {

  /**
   *
   */
  private final OIndexOneValue oIndexTxAwareOneValue;

  private final OTransactionIndexChanges indexChanges;
  private Object lastKey;

  private Object nextKey;

  public PureTxBetweenIndexForwardSpliterator(
      OIndexOneValue oIndexTxAwareOneValue,
      Object fromKey,
      boolean fromInclusive,
      Object toKey,
      boolean toInclusive,
      OTransactionIndexChanges indexChanges) {
    this.oIndexTxAwareOneValue = oIndexTxAwareOneValue;
    this.indexChanges = indexChanges;

    if (fromKey != null) {
      fromKey =
          this.oIndexTxAwareOneValue.enhanceFromCompositeKeyBetweenAsc(fromKey, fromInclusive);
    }
    if (toKey != null) {
      toKey = this.oIndexTxAwareOneValue.enhanceToCompositeKeyBetweenAsc(toKey, toInclusive);
    }

    final Object[] keys = indexChanges.firstAndLastKeys(fromKey, fromInclusive, toKey, toInclusive);
    if (keys.length == 0) {
      nextKey = null;
    } else {
      Object firstKey = keys[0];
      lastKey = keys[1];

      nextKey = firstKey;
    }
  }

  @Override
  public boolean tryAdvance(Consumer<? super ORawPair<Object, YTRID>> action) {
    if (nextKey == null) {
      return false;
    }

    ORawPair<Object, YTRID> result;

    do {
      result = this.oIndexTxAwareOneValue.calculateTxIndexEntry(nextKey, null, indexChanges);
      nextKey = indexChanges.getHigherKey(nextKey);

      if (nextKey != null && ODefaultComparator.INSTANCE.compare(nextKey, lastKey) > 0) {
        nextKey = null;
      }

    } while (result == null && nextKey != null);

    if (result == null) {
      return false;
    }

    action.accept(result);
    return true;
  }

  @Override
  public Spliterator<ORawPair<Object, YTRID>> trySplit() {
    return null;
  }

  @Override
  public long estimateSize() {
    return Long.MAX_VALUE;
  }

  @Override
  public int characteristics() {
    return NONNULL | SORTED | ORDERED;
  }

  @Override
  public Comparator<? super ORawPair<Object, YTRID>> getComparator() {
    return AscComparator.INSTANCE;
  }
}