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

package com.orientechnologies.orient.client.remote;

import com.orientechnologies.common.concur.resource.OCloseable;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.core.OOrientShutdownListener;
import com.orientechnologies.core.OOrientStartupListener;
import com.orientechnologies.core.YouTrackDBManager;
import com.orientechnologies.core.db.record.YTIdentifiable;
import com.orientechnologies.core.db.record.ridbag.RidBag;
import com.orientechnologies.core.storage.impl.local.paginated.atomicoperations.OAtomicOperation;
import com.orientechnologies.core.storage.index.sbtreebonsai.local.OSBTreeBonsai;
import com.orientechnologies.core.storage.ridbag.sbtree.OBonsaiCollectionPointer;
import com.orientechnologies.core.storage.ridbag.sbtree.OSBTreeCollectionManager;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
public class OSBTreeCollectionManagerRemote
    implements OCloseable,
    OSBTreeCollectionManager,
    OOrientStartupListener,
    OOrientShutdownListener {

  private volatile ThreadLocal<Map<UUID, WeakReference<RidBag>>> pendingCollections =
      new PendingCollectionsThreadLocal();

  public OSBTreeCollectionManagerRemote() {

    YouTrackDBManager.instance().registerWeakOrientStartupListener(this);
    YouTrackDBManager.instance().registerWeakOrientShutdownListener(this);
  }

  @Override
  public void onShutdown() {
    pendingCollections = null;
  }

  @Override
  public void onStartup() {
    if (pendingCollections == null) {
      pendingCollections = new PendingCollectionsThreadLocal();
    }
  }

  protected OSBTreeBonsai<YTIdentifiable, Integer> createEdgeTree(
      OAtomicOperation atomicOperation, final int clusterId) {
    throw new UnsupportedOperationException(
        "Creation of SB-Tree from remote storage is not allowed");
  }

  protected OSBTreeBonsai<YTIdentifiable, Integer> loadTree(
      OBonsaiCollectionPointer collectionPointer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public UUID listenForChanges(RidBag collection) {
    UUID id = collection.getTemporaryId();
    if (id == null) {
      id = UUID.randomUUID();
    }

    pendingCollections.get().put(id, new WeakReference<RidBag>(collection));

    return id;
  }

  @Override
  public void updateCollectionPointer(UUID uuid, OBonsaiCollectionPointer pointer) {
    final WeakReference<RidBag> reference = pendingCollections.get().get(uuid);
    if (reference == null) {
      OLogManager.instance()
          .warn(this, "Update of collection pointer is received but collection is not registered");
      return;
    }

    final RidBag collection = reference.get();

    if (collection != null) {
      collection.notifySaved(pointer);
    }
  }

  @Override
  public void clearPendingCollections() {
    pendingCollections.get().clear();
  }

  @Override
  public Map<UUID, OBonsaiCollectionPointer> changedIds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearChangedIds() {
    throw new UnsupportedOperationException();
  }

  private static class PendingCollectionsThreadLocal
      extends ThreadLocal<Map<UUID, WeakReference<RidBag>>> {

    @Override
    protected Map<UUID, WeakReference<RidBag>> initialValue() {
      return new HashMap<UUID, WeakReference<RidBag>>();
    }
  }

  @Override
  public OSBTreeBonsai<YTIdentifiable, Integer> createAndLoadTree(
      OAtomicOperation atomicOperation, int clusterId) throws IOException {
    return loadSBTree(createSBTree(clusterId, atomicOperation, null));
  }

  @Override
  public OBonsaiCollectionPointer createSBTree(
      int clusterId, OAtomicOperation atomicOperation, UUID ownerUUID) throws IOException {
    OSBTreeBonsai<YTIdentifiable, Integer> tree = createEdgeTree(atomicOperation, clusterId);
    return tree.getCollectionPointer();
  }

  @Override
  public OSBTreeBonsai<YTIdentifiable, Integer> loadSBTree(
      OBonsaiCollectionPointer collectionPointer) {

    final OSBTreeBonsai<YTIdentifiable, Integer> tree;
    tree = loadTree(collectionPointer);

    return tree;
  }

  @Override
  public void releaseSBTree(OBonsaiCollectionPointer collectionPointer) {
  }

  @Override
  public void delete(OBonsaiCollectionPointer collectionPointer) {
  }

  @Override
  public void close() {
    clear();
  }

  public void clear() {
  }

  void clearClusterCache(final long fileId, String fileName) {
  }

  int size() {
    return 0;
  }
}
