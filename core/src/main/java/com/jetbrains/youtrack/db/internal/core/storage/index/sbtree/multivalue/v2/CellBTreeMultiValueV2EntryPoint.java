package com.jetbrains.youtrack.db.internal.core.storage.index.sbtree.multivalue.v2;

import com.jetbrains.youtrack.db.internal.common.serialization.types.OByteSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OLongSerializer;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCacheEntry;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.base.ODurablePage;

public final class CellBTreeMultiValueV2EntryPoint<K> extends ODurablePage {

  private static final int KEY_SERIALIZER_OFFSET = NEXT_FREE_POSITION;
  private static final int KEY_SIZE_OFFSET = KEY_SERIALIZER_OFFSET + OByteSerializer.BYTE_SIZE;
  private static final int TREE_SIZE_OFFSET = KEY_SIZE_OFFSET + OIntegerSerializer.INT_SIZE;
  private static final int PAGES_SIZE_OFFSET = TREE_SIZE_OFFSET + OLongSerializer.LONG_SIZE;
  private static final int ENTRY_ID_OFFSET = PAGES_SIZE_OFFSET + OIntegerSerializer.INT_SIZE;

  public CellBTreeMultiValueV2EntryPoint(final OCacheEntry cacheEntry) {
    super(cacheEntry);
  }

  public void init() {
    setLongValue(TREE_SIZE_OFFSET, 0);
    setIntValue(PAGES_SIZE_OFFSET, 1);
    setLongValue(ENTRY_ID_OFFSET, 0);
  }

  public void setTreeSize(final long size) {
    setLongValue(TREE_SIZE_OFFSET, size);
  }

  public long getTreeSize() {
    return getLongValue(TREE_SIZE_OFFSET);
  }

  public void setPagesSize(final int pages) {
    setIntValue(PAGES_SIZE_OFFSET, pages);
  }

  public int getPagesSize() {
    return getIntValue(PAGES_SIZE_OFFSET);
  }

  public void setEntryId(final long id) {
    setLongValue(ENTRY_ID_OFFSET, id);
  }

  public long getEntryId() {
    return getLongValue(ENTRY_ID_OFFSET);
  }
}