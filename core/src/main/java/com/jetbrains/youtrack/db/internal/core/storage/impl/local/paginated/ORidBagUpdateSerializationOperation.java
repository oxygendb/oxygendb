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
package com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated;

import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.exception.YTDatabaseException;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.AbstractPaginatedStorage;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.atomicoperations.OAtomicOperation;
import com.jetbrains.youtrack.db.internal.core.storage.index.sbtreebonsai.local.OSBTreeBonsai;
import com.jetbrains.youtrack.db.internal.core.storage.ridbag.sbtree.Change;
import com.jetbrains.youtrack.db.internal.core.storage.ridbag.sbtree.OBonsaiCollectionPointer;
import com.jetbrains.youtrack.db.internal.core.storage.ridbag.sbtree.OSBTreeCollectionManager;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;

/**
 * @since 11/26/13
 */
public class ORidBagUpdateSerializationOperation implements ORecordSerializationOperation {

  private final NavigableMap<YTIdentifiable, Change> changedValues;

  private final OBonsaiCollectionPointer collectionPointer;

  private final OSBTreeCollectionManager collectionManager;

  public ORidBagUpdateSerializationOperation(
      final NavigableMap<YTIdentifiable, Change> changedValues,
      OBonsaiCollectionPointer collectionPointer) {
    this.changedValues = changedValues;
    this.collectionPointer = collectionPointer;

    collectionManager = ODatabaseRecordThreadLocal.instance().get().getSbTreeCollectionManager();
  }

  @Override
  public void execute(
      OAtomicOperation atomicOperation, AbstractPaginatedStorage paginatedStorage) {
    if (changedValues.isEmpty()) {
      return;
    }

    OSBTreeBonsai<YTIdentifiable, Integer> tree = loadTree();
    try {
      for (Map.Entry<YTIdentifiable, Change> entry : changedValues.entrySet()) {
        Integer storedCounter = tree.get(entry.getKey());

        storedCounter = entry.getValue().applyTo(storedCounter);
        if (storedCounter <= 0) {
          tree.remove(atomicOperation, entry.getKey());
        } else {
          tree.put(atomicOperation, entry.getKey(), storedCounter);
        }
      }
    } catch (IOException e) {
      throw YTException.wrapException(new YTDatabaseException("Error during ridbag update"), e);
    } finally {
      releaseTree();
    }

    changedValues.clear();
  }

  private OSBTreeBonsai<YTIdentifiable, Integer> loadTree() {
    return collectionManager.loadSBTree(collectionPointer);
  }

  private void releaseTree() {
    collectionManager.releaseSBTree(collectionPointer);
  }
}