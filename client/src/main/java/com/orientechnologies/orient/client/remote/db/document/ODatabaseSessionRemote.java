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

package com.orientechnologies.orient.client.remote.db.document;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.client.remote.OLiveQueryClientListener;
import com.orientechnologies.orient.client.remote.ORemoteQueryResult;
import com.orientechnologies.orient.client.remote.OStorageRemote;
import com.orientechnologies.orient.client.remote.OStorageRemoteSession;
import com.orientechnologies.orient.client.remote.message.ORemoteResultSet;
import com.orientechnologies.orient.client.remote.metadata.schema.OSchemaRemote;
import com.orientechnologies.orient.core.Oxygen;
import com.orientechnologies.orient.core.cache.OLocalRecordCache;
import com.orientechnologies.orient.core.command.script.OCommandScriptException;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.conflict.ORecordConflictStrategy;
import com.orientechnologies.orient.core.db.ODatabaseListener;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseSessionInternal;
import com.orientechnologies.orient.core.db.OHookReplacedRecordThreadLocal;
import com.orientechnologies.orient.core.db.OLiveQueryMonitor;
import com.orientechnologies.orient.core.db.OLiveQueryResultListener;
import com.orientechnologies.orient.core.db.OSharedContext;
import com.orientechnologies.orient.core.db.OxygenDBConfig;
import com.orientechnologies.orient.core.db.document.ODatabaseSessionAbstract;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OClassIndexManager;
import com.orientechnologies.orient.core.index.OIndexManagerRemote;
import com.orientechnologies.orient.core.iterator.ORecordIteratorCluster;
import com.orientechnologies.orient.core.metadata.OMetadataDefault;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OImmutableClass;
import com.orientechnologies.orient.core.metadata.schema.OSchemaProxy;
import com.orientechnologies.orient.core.metadata.security.OImmutableUser;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.OToken;
import com.orientechnologies.orient.core.metadata.security.OUser;
import com.orientechnologies.orient.core.metadata.sequence.OSequenceAction;
import com.orientechnologies.orient.core.record.OEdge;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordAbstract;
import com.orientechnologies.orient.core.record.OVertex;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.record.impl.OEdgeDocument;
import com.orientechnologies.orient.core.record.impl.OVertexInternal;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.serialization.serializer.record.binary.ORecordSerializerNetworkV37Client;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.orientechnologies.orient.core.storage.ORecordMetadata;
import com.orientechnologies.orient.core.storage.OStorage;
import com.orientechnologies.orient.core.storage.OStorageInfo;
import com.orientechnologies.orient.core.storage.ridbag.sbtree.OSBTreeCollectionManager;
import com.orientechnologies.orient.core.tx.OTransactionAbstract;
import com.orientechnologies.orient.core.tx.OTransactionIndexChanges;
import com.orientechnologies.orient.core.tx.OTransactionNoTx;
import com.orientechnologies.orient.core.tx.OTransactionNoTx.NonTxReadMode;
import com.orientechnologies.orient.core.tx.OTransactionOptimistic;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class ODatabaseSessionRemote extends ODatabaseSessionAbstract {

  protected OStorageRemoteSession sessionMetadata;
  private OxygenDBConfig config;
  private OStorageRemote storage;
  private OTransactionNoTx.NonTxReadMode nonTxReadMode;

  public ODatabaseSessionRemote(final OStorageRemote storage, OSharedContext sharedContext) {
    activateOnCurrentThread();

    try {
      status = STATUS.CLOSED;

      // OVERWRITE THE URL
      url = storage.getURL();
      this.storage = storage;
      this.sharedContext = sharedContext;
      this.componentsFactory = storage.getComponentsFactory();

      unmodifiableHooks = Collections.unmodifiableMap(hooks);

      localCache = new OLocalRecordCache();

      try {
        var cfg = storage.getConfiguration();
        if (cfg != null) {
          var ctx = cfg.getContextConfiguration();
          if (ctx != null) {
            nonTxReadMode =
                OTransactionNoTx.NonTxReadMode.valueOf(
                    ctx.getValueAsString(OGlobalConfiguration.NON_TX_READS_WARNING_MODE));
          } else {
            nonTxReadMode = NonTxReadMode.WARN;
          }
        } else {
          nonTxReadMode = NonTxReadMode.WARN;
        }
      } catch (Exception e) {
        OLogManager.instance()
            .warn(
                this,
                "Invalid value for %s, using %s",
                e,
                OGlobalConfiguration.NON_TX_READS_WARNING_MODE.getKey(),
                NonTxReadMode.WARN);
        nonTxReadMode = NonTxReadMode.WARN;
      }

      init();

      databaseOwner = this;
    } catch (Exception t) {
      ODatabaseRecordThreadLocal.instance().remove();

      throw OException.wrapException(new ODatabaseException("Error on opening database "), t);
    }
  }

  public ODatabaseSession open(final String iUserName, final String iUserPassword) {
    throw new UnsupportedOperationException("Use OxygenDB");
  }

  @Deprecated
  public ODatabaseSession open(final OToken iToken) {
    throw new UnsupportedOperationException("Deprecated Method");
  }

  @Override
  public ODatabaseSession create() {
    throw new UnsupportedOperationException("Deprecated Method");
  }

  @Override
  public ODatabaseSession create(String incrementalBackupPath) {
    throw new UnsupportedOperationException("use OxygenDB");
  }

  @Override
  public ODatabaseSession create(final Map<OGlobalConfiguration, Object> iInitialSettings) {
    throw new UnsupportedOperationException("use OxygenDB");
  }

  @Override
  public void drop() {
    throw new UnsupportedOperationException("use OxygenDB");
  }

  @Override
  public void set(ATTRIBUTES iAttribute, Object iValue) {
    if (iAttribute == ATTRIBUTES.CUSTOM) {
      String stringValue = iValue.toString();
      int indx = stringValue != null ? stringValue.indexOf('=') : -1;
      if (indx < 0) {
        if ("clear".equalsIgnoreCase(stringValue)) {
          String query = "alter database CUSTOM 'clear'";
          // Bypass the database command for avoid transaction management
          ORemoteQueryResult result = storage.command(this, query, new Object[]{iValue});
          result.getResult().close();
        } else {
          throw new IllegalArgumentException(
              "Syntax error: expected <name> = <value> or clear, instead found: " + iValue);
        }
      } else {
        String customName = stringValue.substring(0, indx).trim();
        String customValue = stringValue.substring(indx + 1).trim();
        setCustom(customName, customValue);
      }
    } else {
      String query = "alter database " + iAttribute.name() + " ? ";
      // Bypass the database command for avoid transaction management
      ORemoteQueryResult result = storage.command(this, query, new Object[]{iValue});
      result.getResult().close();
      storage.reload(this);
    }
  }

  @Override
  public ODatabaseSession setCustom(String name, Object iValue) {
    if ("clear".equals(name) && iValue == null) {
      String query = "alter database CUSTOM 'clear'";
      // Bypass the database command for avoid transaction management
      ORemoteQueryResult result = storage.command(this, query, new Object[]{});
      result.getResult().close();
    } else {
      String query = "alter database CUSTOM  " + name + " = ?";
      // Bypass the database command for avoid transaction management
      ORemoteQueryResult result = storage.command(this, query, new Object[]{iValue});
      result.getResult().close();
      storage.reload(this);
    }
    return this;
  }

  public ODatabaseSessionInternal copy() {
    ODatabaseSessionRemote database = new ODatabaseSessionRemote(storage, this.sharedContext);
    database.storage = storage.copy(this, database);
    database.storage.addUser();
    database.status = STATUS.OPEN;
    database.applyAttributes(config);
    database.initAtFirstOpen();
    database.user = this.user;
    this.activateOnCurrentThread();
    return database;
  }

  @Override
  public boolean exists() {
    throw new UnsupportedOperationException("use OxygenDB");
  }

  public void internalOpen(String user, String password, OxygenDBConfig config) {
    this.config = config;
    applyAttributes(config);
    applyListeners(config);
    try {

      storage.open(user, password, config.getConfigurations());

      status = STATUS.OPEN;

      initAtFirstOpen();
      this.user =
          new OImmutableUser(this,
              -1, new OUser(this, user, password)); // .addRole(new ORole("passthrough", null,
      // ORole.ALLOW_MODES.ALLOW_ALL_BUT)));

      // WAKE UP LISTENERS
      callOnOpenListeners();

    } catch (OException e) {
      close();
      ODatabaseRecordThreadLocal.instance().remove();
      throw e;
    } catch (Exception e) {
      close();
      ODatabaseRecordThreadLocal.instance().remove();
      throw OException.wrapException(
          new ODatabaseException("Cannot open database url=" + getURL()), e);
    }
  }

  private void applyAttributes(OxygenDBConfig config) {
    for (Entry<ATTRIBUTES, Object> attrs : config.getAttributes().entrySet()) {
      this.set(attrs.getKey(), attrs.getValue());
    }
  }

  private void initAtFirstOpen() {
    if (initialized) {
      return;
    }

    ORecordSerializerFactory serializerFactory = ORecordSerializerFactory.instance();
    serializer = serializerFactory.getFormat(ORecordSerializerNetworkV37Client.NAME);
    localCache.startup();
    componentsFactory = storage.getComponentsFactory();
    user = null;

    loadMetadata();

    initialized = true;
  }

  @Override
  protected void loadMetadata() {
    metadata = new OMetadataDefault(this);
    metadata.init(sharedContext);
    sharedContext.load(this);
  }

  private void applyListeners(OxygenDBConfig config) {
    for (ODatabaseListener listener : config.getListeners()) {
      registerListener(listener);
    }
  }

  public OStorageRemoteSession getSessionMetadata() {
    return sessionMetadata;
  }

  public void setSessionMetadata(OStorageRemoteSession sessionMetadata) {
    this.sessionMetadata = sessionMetadata;
  }

  @Override
  public OStorage getStorage() {
    return storage;
  }

  public OStorageRemote getStorageRemote() {
    return storage;
  }

  @Override
  public OStorageInfo getStorageInfo() {
    return storage;
  }

  @Override
  public void replaceStorage(OStorage iNewStorage) {
    throw new UnsupportedOperationException("unsupported replace of storage for remote database");
  }

  private void checkAndSendTransaction() {

    if (this.currentTx.isActive() && ((OTransactionOptimistic) this.currentTx).isChanged()) {
      var optimistic = (OTransactionOptimistic) this.currentTx;

      if (((OTransactionOptimistic) this.getTransaction()).isAlreadyCleared()) {
        storage.reBeginTransaction(this, optimistic);
      } else {
        storage.beginTransaction(this, optimistic);
      }

      optimistic.resetChangesTracking();
      optimistic.setSentToServer(true);
    }
  }

  @Override
  public int begin() {
    final int counter = super.begin();

    var optimistic = (OTransactionOptimistic) this.currentTx;
    storage.beginTransaction(this, optimistic);

    optimistic.resetChangesTracking();
    optimistic.setSentToServer(true);

    return counter;
  }

  @Override
  public OResultSet query(String query, Object... args) {
    checkOpenness();
    checkAndSendTransaction();

    ORemoteQueryResult result = storage.query(this, query, args);
    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  public OResultSet query(String query, Map args) {
    checkOpenness();
    checkAndSendTransaction();

    ORemoteQueryResult result = storage.query(this, query, args);
    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  public OResultSet indexQuery(String indexName, String query, Object... args) {
    checkOpenness();

    if (getTransaction().isActive()) {
      OTransactionIndexChanges changes = getTransaction().getIndexChanges(indexName);
      Set<String> changedIndexes =
          ((OTransactionOptimisticClient) getTransaction()).getIndexChanged();
      if (changedIndexes.contains(indexName) || changes != null) {
        checkAndSendTransaction();
      }
    }

    ORemoteQueryResult result = storage.command(this, query, args);
    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  public OResultSet command(String query, Object... args) {
    checkOpenness();
    checkAndSendTransaction();

    ORemoteQueryResult result = storage.command(this, query, args);
    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  public OResultSet command(String query, Map args) {
    checkOpenness();
    checkAndSendTransaction();

    ORemoteQueryResult result = storage.command(this, query, args);

    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  protected OTransactionOptimistic newTxInstance() {
    return new OTransactionOptimisticClient(this);
  }

  @Override
  public OResultSet execute(String language, String script, Object... args)
      throws OCommandExecutionException, OCommandScriptException {
    checkOpenness();
    checkAndSendTransaction();
    ORemoteQueryResult result = storage.execute(this, language, script, args);

    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  @Override
  public OResultSet execute(String language, String script, Map<String, ?> args)
      throws OCommandExecutionException, OCommandScriptException {
    checkOpenness();
    checkAndSendTransaction();

    ORemoteQueryResult result = storage.execute(this, language, script, args);

    if (result.isReloadMetadata()) {
      reload();
    }

    return result.getResult();
  }

  public void closeQuery(String queryId) {
    storage.closeQuery(this, queryId);
    queryClosed(queryId);
  }

  public void fetchNextPage(ORemoteResultSet rs) {
    checkOpenness();
    checkAndSendTransaction();
    storage.fetchNextPage(this, rs);
  }

  @Override
  public OLiveQueryMonitor live(String query, OLiveQueryResultListener listener, Object... args) {
    return storage.liveQuery(
        this, query, new OLiveQueryClientListener(this.copy(), listener), args);
  }

  @Override
  public OLiveQueryMonitor live(
      String query, OLiveQueryResultListener listener, Map<String, ?> args) {
    return storage.liveQuery(
        this, query, new OLiveQueryClientListener(this.copy(), listener), args);
  }

  @Override
  public void recycle(ORecord record) {
    throw new UnsupportedOperationException();
  }

  public static void updateSchema(ODatabaseSessionInternal session, OStorageRemote storage,
      ODocument schema) {
    //    storage.get
    OSharedContext shared = storage.getSharedContext();
    if (shared != null) {
      ((OSchemaRemote) shared.getSchema()).update(session, schema);
    }
  }

  public static void updateIndexManager(OStorageRemote storage, ODocument indexManager) {
    OSharedContext shared = storage.getSharedContext();
    if (shared != null) {
      ((OIndexManagerRemote) shared.getIndexManager()).update(indexManager);
    }
  }

  public static void updateFunction(OStorageRemote storage) {
    OSharedContext shared = storage.getSharedContext();
    if (shared != null) {
      (shared.getFunctionLibrary()).update();
    }
  }

  public static void updateSequences(OStorageRemote storage) {
    OSharedContext shared = storage.getSharedContext();
    if (shared != null) {
      (shared.getSequenceLibrary()).update();
    }
  }

  @Override
  public int addBlobCluster(final String iClusterName, final Object... iParameters) {
    int id;
    try (OResultSet resultSet = command("create blob cluster :1", iClusterName)) {
      assert resultSet.hasNext();
      OResult result = resultSet.next();
      assert result.getProperty("value") != null;
      id = result.getProperty("value");
      return id;
    }
  }

  @Override
  public OIdentifiable beforeCreateOperations(OIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_CREATE, id, iClusterName);
    ORecordHook.RESULT res = callbackHooks(ORecordHook.TYPE.BEFORE_CREATE, id);
    if (res == ORecordHook.RESULT.RECORD_CHANGED) {
      if (id instanceof ODocument) {
        ((ODocument) id).validate();
      }
      return id;
    } else {
      if (res == ORecordHook.RESULT.RECORD_REPLACED) {
        ORecord replaced = OHookReplacedRecordThreadLocal.INSTANCE.get();
        if (replaced instanceof ODocument) {
          ((ODocument) replaced).validate();
        }
        return replaced;
      }
    }
    return null;
  }

  @Override
  public OIdentifiable beforeUpdateOperations(OIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_UPDATE, id, iClusterName);
    ORecordHook.RESULT res = callbackHooks(ORecordHook.TYPE.BEFORE_UPDATE, id);
    if (res == ORecordHook.RESULT.RECORD_CHANGED) {
      if (id instanceof ODocument) {
        ((ODocument) id).validate();
      }
      return id;
    } else {
      if (res == ORecordHook.RESULT.RECORD_REPLACED) {
        ORecord replaced = OHookReplacedRecordThreadLocal.INSTANCE.get();
        if (replaced instanceof ODocument) {
          ((ODocument) replaced).validate();
        }
        return replaced;
      }
    }
    return null;
  }

  @Override
  public void beforeDeleteOperations(OIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_DELETE, id, iClusterName);
    callbackHooks(ORecordHook.TYPE.BEFORE_DELETE, id);
  }

  public void afterUpdateOperations(final OIdentifiable id) {
    callbackHooks(ORecordHook.TYPE.AFTER_UPDATE, id);
    if (id instanceof ODocument doc) {
      OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null && getTransaction().isActive()) {
        OClassIndexManager.processIndexOnUpdate(this, doc);
      }
    }
  }

  public void afterCreateOperations(final OIdentifiable id) {
    callbackHooks(ORecordHook.TYPE.AFTER_CREATE, id);
    if (id instanceof ODocument doc) {
      OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null && getTransaction().isActive()) {
        OClassIndexManager.processIndexOnCreate(this, doc);
      }
    }
  }

  public void afterDeleteOperations(final OIdentifiable id) {
    callbackHooks(ORecordHook.TYPE.AFTER_DELETE, id);
    if (id instanceof ODocument doc) {
      OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null && getTransaction().isActive()) {
        OClassIndexManager.processIndexOnDelete(this, doc);
      }
    }
  }

  @Override
  public boolean beforeReadOperations(OIdentifiable identifiable) {
    return callbackHooks(ORecordHook.TYPE.BEFORE_READ, identifiable) == ORecordHook.RESULT.SKIP;
  }

  @Override
  public void afterReadOperations(OIdentifiable identifiable) {
    callbackHooks(ORecordHook.TYPE.AFTER_READ, identifiable);
  }

  @Override
  public boolean executeExists(ORID rid) {
    checkOpenness();
    checkIfActive();

    try {
      ORecord record = getTransaction().getRecord(rid);
      if (record == OTransactionAbstract.DELETED_RECORD) {
        return false;
      }
      if (record != null) {
        return true;
      }

      if (!rid.isPersistent()) {
        return false;
      }

      if (localCache.findRecord(rid) != null) {
        return true;
      }

      return storage.recordExists(this, rid);
    } catch (Exception t) {
      throw OException.wrapException(
          new ODatabaseException(
              "Error on retrieving record "
                  + rid
                  + " (cluster: "
                  + getStorage().getPhysicalClusterNameById(rid.getClusterId())
                  + ")"),
          t);
    }
  }

  public String getClusterName(final ORecord record) {
    // DON'T ASSIGN CLUSTER WITH REMOTE: SERVER KNOWS THE RIGHT CLUSTER BASED ON LOCALITY
    return null;
  }

  @Override
  public <T> T sendSequenceAction(OSequenceAction action)
      throws ExecutionException, InterruptedException {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose Tools | Templates.
  }

  public void delete(final ORecord record) {
    checkOpenness();
    if (record == null) {
      throw new ODatabaseException("Cannot delete null document");
    }
    if (record instanceof OVertex) {
      OVertexInternal.deleteLinks((OVertex) record);
    } else {
      if (record instanceof OEdge) {
        OEdgeDocument.deleteLinks((OEdge) record);
      }
    }

    try {
      currentTx.deleteRecord((ORecordAbstract) record);
    } catch (OException e) {
      throw e;
    } catch (Exception e) {
      if (record instanceof ODocument) {
        throw OException.wrapException(
            new ODatabaseException(
                "Error on deleting record "
                    + record.getIdentity()
                    + " of class '"
                    + ((ODocument) record).getClassName()
                    + "'"),
            e);
      } else {
        throw OException.wrapException(
            new ODatabaseException("Error on deleting record " + record.getIdentity()), e);
      }
    }
  }

  @Override
  public int addCluster(final String iClusterName, final Object... iParameters) {
    checkIfActive();
    return storage.addCluster(this, iClusterName, iParameters);
  }

  @Override
  public int addCluster(final String iClusterName, final int iRequestedId) {
    checkIfActive();
    return storage.addCluster(this, iClusterName, iRequestedId);
  }

  public ORecordConflictStrategy getConflictStrategy() {
    checkIfActive();
    return getStorageInfo().getRecordConflictStrategy();
  }

  public ODatabaseSessionAbstract setConflictStrategy(final String iStrategyName) {
    checkIfActive();
    storage.setConflictStrategy(
        Oxygen.instance().getRecordConflictStrategy().getStrategy(iStrategyName));
    return this;
  }

  public ODatabaseSessionAbstract setConflictStrategy(final ORecordConflictStrategy iResolver) {
    checkIfActive();
    storage.setConflictStrategy(iResolver);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int iClusterId, boolean countTombstones) {
    checkIfActive();
    return storage.count(this, iClusterId, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int[] iClusterIds, boolean countTombstones) {
    checkIfActive();
    return storage.count(this, iClusterIds, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(final String iClusterName) {
    checkIfActive();

    final int clusterId = getClusterIdByName(iClusterName);
    if (clusterId < 0) {
      throw new IllegalArgumentException("Cluster '" + iClusterName + "' was not found");
    }
    return storage.count(this, clusterId);
  }

  @Override
  public long getClusterRecordSizeByName(final String clusterName) {
    checkIfActive();
    try {
      return storage.getClusterRecordsSizeByName(clusterName);
    } catch (Exception e) {
      throw OException.wrapException(
          new ODatabaseException("Error on reading records size for cluster '" + clusterName + "'"),
          e);
    }
  }

  @Override
  public boolean dropCluster(final String iClusterName) {
    checkIfActive();
    final int clusterId = getClusterIdByName(iClusterName);
    OSchemaProxy schema = metadata.getSchema();
    OClass clazz = schema.getClassByClusterId(clusterId);
    if (clazz != null) {
      clazz.removeClusterId(this, clusterId);
    }
    if (schema.getBlobClusters().contains(clusterId)) {
      schema.removeBlobCluster(iClusterName);
    }
    getLocalCache().freeCluster(clusterId);
    checkForClusterPermissions(iClusterName);
    return storage.dropCluster(this, iClusterName);
  }

  @Override
  public boolean dropCluster(final int clusterId) {
    checkIfActive();

    OSchemaProxy schema = metadata.getSchema();
    final OClass clazz = schema.getClassByClusterId(clusterId);
    if (clazz != null) {
      clazz.removeClusterId(this, clusterId);
    }
    getLocalCache().freeCluster(clusterId);
    if (schema.getBlobClusters().contains(clusterId)) {
      schema.removeBlobCluster(getClusterNameById(clusterId));
    }

    checkForClusterPermissions(getClusterNameById(clusterId));

    final String clusterName = getClusterNameById(clusterId);
    if (clusterName == null) {
      return false;
    }

    final ORecordIteratorCluster<ORecord> iteratorCluster = browseCluster(clusterName);
    if (iteratorCluster == null) {
      return false;
    }

    executeInTxBatches((Iterator<ORecord>) iteratorCluster,
        (session, record) -> record.delete());

    return storage.dropCluster(this, clusterId);
  }

  public boolean dropClusterInternal(int clusterId) {
    return storage.dropCluster(this, clusterId);
  }

  @Override
  public long getClusterRecordSizeById(final int clusterId) {
    checkIfActive();
    try {
      return storage.getClusterRecordsSizeById(clusterId);
    } catch (Exception e) {
      throw OException.wrapException(
          new ODatabaseException(
              "Error on reading records size for cluster with id '" + clusterId + "'"),
          e);
    }
  }

  @Override
  public long getSize() {
    checkIfActive();
    return storage.getSize(this);
  }

  @Override
  public void checkSecurity(
      ORule.ResourceGeneric resourceGeneric, String resourceSpecific, int iOperation) {
  }

  @Override
  public void checkSecurity(
      ORule.ResourceGeneric iResourceGeneric, int iOperation, Object iResourceSpecific) {
  }

  @Override
  public void checkSecurity(
      ORule.ResourceGeneric iResourceGeneric, int iOperation, Object... iResourcesSpecific) {
  }

  @Override
  public void checkSecurity(String iResource, int iOperation) {
  }

  @Override
  public void checkSecurity(String iResourceGeneric, int iOperation, Object iResourceSpecific) {
  }

  @Override
  public void checkSecurity(
      String iResourceGeneric, int iOperation, Object... iResourcesSpecific) {
  }

  @Override
  public boolean isRemote() {
    return true;
  }

  @Override
  public String incrementalBackup(final String path) throws UnsupportedOperationException {
    checkOpenness();
    checkIfActive();
    checkSecurity(ORule.ResourceGeneric.DATABASE, "backup", ORole.PERMISSION_EXECUTE);

    return storage.incrementalBackup(this, path, null);
  }

  @Override
  public ORecordMetadata getRecordMetadata(final ORID rid) {
    checkIfActive();
    return storage.getRecordMetadata(this, rid);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void freeze(final boolean throwException) {
    checkOpenness();
    OLogManager.instance()
        .error(
            this,
            "Only local paginated storage supports freeze. If you are using remote client please"
                + " use OxygenDB instance instead",
            null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void freeze() {
    freeze(false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void release() {
    checkOpenness();
    OLogManager.instance()
        .error(
            this,
            "Only local paginated storage supports release. If you are using remote client please"
                + " use OxygenDB instance instead",
            null);
  }

  /**
   * {@inheritDoc}
   */
  public OSBTreeCollectionManager getSbTreeCollectionManager() {
    return storage.getSBtreeCollectionManager();
  }

  @Override
  public void reload() {
    checkIfActive();

    if (this.isClosed()) {
      throw new ODatabaseException("Cannot reload a closed db");
    }
    metadata.reload();
    storage.reload(this);
  }

  @Override
  public void internalCommit(OTransactionOptimistic transaction) {
    this.storage.commit(transaction);
  }

  @Override
  public boolean isClosed() {
    return status == STATUS.CLOSED || storage.isClosed();
  }

  public void internalClose(boolean recycle) {
    if (status != STATUS.OPEN) {
      return;
    }

    checkIfActive();

    try {
      closeActiveQueries();
      localCache.shutdown();

      if (isClosed()) {
        status = STATUS.CLOSED;
        return;
      }

      try {
        rollback(true);
      } catch (Exception e) {
        OLogManager.instance().error(this, "Exception during rollback of active transaction", e);
      }

      callOnCloseListeners();

      status = STATUS.CLOSED;
      if (!recycle) {
        sharedContext = null;

        if (storage != null) {
          storage.close();
        }
      }

    } finally {
      // ALWAYS RESET TL
      ODatabaseRecordThreadLocal.instance().remove();
    }
  }

  @Override
  public long[] getClusterDataRange(int currentClusterId) {
    return storage.getClusterDataRange(this, currentClusterId);
  }

  @Override
  public void setDefaultClusterId(int addCluster) {
    storage.setDefaultClusterId(addCluster);
  }

  @Override
  public long getLastClusterPosition(int clusterId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getClusterRecordConflictStrategy(int clusterId) {
    throw new UnsupportedOperationException();
  }

  public OTransactionOptimisticClient getActiveTx() {
    if (currentTx.isActive()) {
      return (OTransactionOptimisticClient) currentTx;
    } else {
      currentTx = new OTransactionOptimisticClient(this);
      currentTx.begin();
      return (OTransactionOptimisticClient) currentTx;
    }
  }

  @Override
  public int[] getClustersIds(Set<String> filterClusters) {
    checkIfActive();
    return filterClusters.stream().map((c) -> getClusterIdByName(c)).mapToInt(i -> i).toArray();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void truncateCluster(String clusterName) {
    command("truncate cluster " + clusterName).close();
  }

  @Override
  public void truncateClass(String name) {
    command("truncate class " + name).close();
  }

  @Override
  public long truncateClusterInternal(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long truncateClass(String name, boolean polimorfic) {
    long count = 0;
    if (polimorfic) {
      try (OResultSet result = command("truncate class " + name + " polymorphic ")) {
        while (result.hasNext()) {
          count += result.next().<Long>getProperty("count");
        }
      }
    } else {
      try (OResultSet result = command("truncate class " + name)) {
        while (result.hasNext()) {
          count += result.next().<Long>getProperty("count");
        }
      }
    }
    return count;
  }

  @Override
  public NonTxReadMode getNonTxReadMode() {
    return nonTxReadMode;
  }
}