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

package com.jetbrains.youtrack.db.internal.core.db.document;

import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.common.io.OIOUtils;
import com.jetbrains.youtrack.db.internal.common.log.LogManager;
import com.jetbrains.youtrack.db.internal.core.YouTrackDBManager;
import com.jetbrains.youtrack.db.internal.core.cache.OLocalRecordCache;
import com.jetbrains.youtrack.db.internal.core.command.BasicCommandContext;
import com.jetbrains.youtrack.db.internal.core.command.OScriptExecutor;
import com.jetbrains.youtrack.db.internal.core.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.conflict.ORecordConflictStrategy;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseLifecycleListener;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseStats;
import com.jetbrains.youtrack.db.internal.core.db.OHookReplacedRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.OSharedContext;
import com.jetbrains.youtrack.db.internal.core.db.OSharedContextEmbedded;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseListener;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.YTLiveQueryMonitor;
import com.jetbrains.youtrack.db.internal.core.db.YTLiveQueryResultListener;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfig;
import com.jetbrains.youtrack.db.internal.core.db.record.OClassTrigger;
import com.jetbrains.youtrack.db.internal.core.db.record.ORecordOperation;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.db.viewmanager.ViewManager;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.exception.YTConcurrentModificationException;
import com.jetbrains.youtrack.db.internal.core.exception.YTDatabaseException;
import com.jetbrains.youtrack.db.internal.core.exception.YTSchemaException;
import com.jetbrains.youtrack.db.internal.core.exception.YTSecurityAccessException;
import com.jetbrains.youtrack.db.internal.core.exception.YTSecurityException;
import com.jetbrains.youtrack.db.internal.core.hook.YTRecordHook;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.index.OClassIndexManager;
import com.jetbrains.youtrack.db.internal.core.iterator.ORecordIteratorCluster;
import com.jetbrains.youtrack.db.internal.core.metadata.OMetadataDefault;
import com.jetbrains.youtrack.db.internal.core.metadata.function.OFunctionLibraryImpl;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTImmutableClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTImmutableSchema;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTSchemaProxy;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTView;
import com.jetbrains.youtrack.db.internal.core.metadata.security.ORestrictedAccessHook;
import com.jetbrains.youtrack.db.internal.core.metadata.security.ORestrictedOperation;
import com.jetbrains.youtrack.db.internal.core.metadata.security.ORole;
import com.jetbrains.youtrack.db.internal.core.metadata.security.ORule;
import com.jetbrains.youtrack.db.internal.core.metadata.security.OSecurityInternal;
import com.jetbrains.youtrack.db.internal.core.metadata.security.OSecurityShared;
import com.jetbrains.youtrack.db.internal.core.metadata.security.OToken;
import com.jetbrains.youtrack.db.internal.core.metadata.security.PropertyAccess;
import com.jetbrains.youtrack.db.internal.core.metadata.security.PropertyEncryptionNone;
import com.jetbrains.youtrack.db.internal.core.metadata.security.YTImmutableUser;
import com.jetbrains.youtrack.db.internal.core.metadata.security.YTSecurityUser;
import com.jetbrains.youtrack.db.internal.core.metadata.security.YTUser;
import com.jetbrains.youtrack.db.internal.core.metadata.security.auth.OAuthenticationInfo;
import com.jetbrains.youtrack.db.internal.core.metadata.sequence.OSequenceAction;
import com.jetbrains.youtrack.db.internal.core.metadata.sequence.OSequenceLibraryProxy;
import com.jetbrains.youtrack.db.internal.core.query.live.OLiveQueryHook;
import com.jetbrains.youtrack.db.internal.core.query.live.OLiveQueryHookV2;
import com.jetbrains.youtrack.db.internal.core.query.live.OLiveQueryListenerV2;
import com.jetbrains.youtrack.db.internal.core.query.live.YTLiveQueryMonitorEmbedded;
import com.jetbrains.youtrack.db.internal.core.record.Entity;
import com.jetbrains.youtrack.db.internal.core.record.Record;
import com.jetbrains.youtrack.db.internal.core.record.RecordAbstract;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.ODocumentInternal;
import com.jetbrains.youtrack.db.internal.core.record.impl.EdgeEntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.VertexInternal;
import com.jetbrains.youtrack.db.internal.core.schedule.OScheduledEvent;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.ORecordSerializer;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.ORecordSerializerFactory;
import com.jetbrains.youtrack.db.internal.core.sql.OSQLEngine;
import com.jetbrains.youtrack.db.internal.core.sql.executor.LiveQueryListenerImpl;
import com.jetbrains.youtrack.db.internal.core.sql.executor.OExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.OInternalExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTInternalResultSet;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResultSet;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLStatement;
import com.jetbrains.youtrack.db.internal.core.sql.parser.YTLocalResultSet;
import com.jetbrains.youtrack.db.internal.core.sql.parser.YTLocalResultSetLifecycleDecorator;
import com.jetbrains.youtrack.db.internal.core.storage.ORecordMetadata;
import com.jetbrains.youtrack.db.internal.core.storage.Storage;
import com.jetbrains.youtrack.db.internal.core.storage.OStorageInfo;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.AbstractPaginatedStorage;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.OFreezableStorageComponent;
import com.jetbrains.youtrack.db.internal.core.storage.ridbag.sbtree.OSBTreeCollectionManager;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionAbstract;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionNoTx;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionNoTx.NonTxReadMode;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionOptimistic;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class YTDatabaseSessionEmbedded extends YTDatabaseSessionAbstract
    implements OQueryLifecycleListener {

  private YouTrackDBConfig config;
  private Storage storage;

  private OTransactionNoTx.NonTxReadMode nonTxReadMode;

  public YTDatabaseSessionEmbedded(final Storage storage) {
    activateOnCurrentThread();

    try {
      status = STATUS.CLOSED;

      try {
        var cfg = storage.getConfiguration();
        if (cfg != null) {
          var ctx = cfg.getContextConfiguration();
          if (ctx != null) {
            nonTxReadMode =
                OTransactionNoTx.NonTxReadMode.valueOf(
                    ctx.getValueAsString(GlobalConfiguration.NON_TX_READS_WARNING_MODE));
          } else {
            nonTxReadMode = NonTxReadMode.WARN;
          }
        } else {
          nonTxReadMode = NonTxReadMode.WARN;
        }
      } catch (Exception e) {
        LogManager.instance()
            .warn(
                this,
                "Invalid value for %s, using %s",
                e,
                GlobalConfiguration.NON_TX_READS_WARNING_MODE.getKey(),
                NonTxReadMode.WARN);
        nonTxReadMode = NonTxReadMode.WARN;
      }

      // OVERWRITE THE URL
      url = storage.getURL();
      this.storage = storage;
      this.componentsFactory = storage.getComponentsFactory();

      unmodifiableHooks = Collections.unmodifiableMap(hooks);

      localCache = new OLocalRecordCache();

      init();

      databaseOwner = this;

    } catch (Exception t) {
      ODatabaseRecordThreadLocal.instance().remove();

      throw YTException.wrapException(new YTDatabaseException("Error on opening database "), t);
    }
  }

  public YTDatabaseSession open(final String iUserName, final String iUserPassword) {
    throw new UnsupportedOperationException("Use YouTrackDB");
  }

  public void init(YouTrackDBConfig config, OSharedContext sharedContext) {
    this.sharedContext = sharedContext;
    activateOnCurrentThread();
    this.config = config;
    applyAttributes(config);
    applyListeners(config);
    try {

      status = STATUS.OPEN;
      if (initialized) {
        return;
      }

      ORecordSerializerFactory serializerFactory = ORecordSerializerFactory.instance();
      String serializeName = getStorageInfo().getConfiguration().getRecordSerializer();
      if (serializeName == null) {
        throw new YTDatabaseException(
            "Impossible to open database from version before 2.x use export import instead");
      }
      serializer = serializerFactory.getFormat(serializeName);
      if (serializer == null) {
        throw new YTDatabaseException(
            "RecordSerializer with name '" + serializeName + "' not found ");
      }
      if (getStorageInfo().getConfiguration().getRecordSerializerVersion()
          > serializer.getMinSupportedVersion()) {
        throw new YTDatabaseException(
            "Persistent record serializer version is not support by the current implementation");
      }

      localCache.startup();

      loadMetadata();

      installHooksEmbedded();

      user = null;

      initialized = true;
    } catch (YTException e) {
      ODatabaseRecordThreadLocal.instance().remove();
      throw e;
    } catch (Exception e) {
      ODatabaseRecordThreadLocal.instance().remove();
      throw YTException.wrapException(
          new YTDatabaseException("Cannot open database url=" + getURL()), e);
    }
  }

  public void internalOpen(final OAuthenticationInfo authenticationInfo) {
    try {
      OSecurityInternal security = sharedContext.getSecurity();

      if (user == null || user.getVersion() != security.getVersion(this)) {
        final YTSecurityUser usr;

        usr = security.securityAuthenticate(this, authenticationInfo);
        if (usr != null) {
          user = new YTImmutableUser(this, security.getVersion(this), usr);
        } else {
          user = null;
        }

        checkSecurity(ORule.ResourceGeneric.DATABASE, ORole.PERMISSION_READ);
      }

    } catch (YTException e) {
      ODatabaseRecordThreadLocal.instance().remove();
      throw e;
    } catch (Exception e) {
      ODatabaseRecordThreadLocal.instance().remove();
      throw YTException.wrapException(
          new YTDatabaseException("Cannot open database url=" + getURL()), e);
    }
  }

  public void internalOpen(final String iUserName, final String iUserPassword) {
    internalOpen(iUserName, iUserPassword, true);
  }

  public void internalOpen(
      final String iUserName, final String iUserPassword, boolean checkPassword) {
    executeInTx(
        () -> {
          try {
            OSecurityInternal security = sharedContext.getSecurity();

            if (user == null
                || user.getVersion() != security.getVersion(this)
                || !user.getName(this).equalsIgnoreCase(iUserName)) {
              final YTSecurityUser usr;

              if (checkPassword) {
                usr = security.securityAuthenticate(this, iUserName, iUserPassword);
              } else {
                usr = security.getUser(this, iUserName);
              }
              if (usr != null) {
                user = new YTImmutableUser(this, security.getVersion(this), usr);
              } else {
                user = null;
              }

              checkSecurity(ORule.ResourceGeneric.DATABASE, ORole.PERMISSION_READ);
            }
          } catch (YTException e) {
            ODatabaseRecordThreadLocal.instance().remove();
            throw e;
          } catch (Exception e) {
            ODatabaseRecordThreadLocal.instance().remove();
            throw YTException.wrapException(
                new YTDatabaseException("Cannot open database url=" + getURL()), e);
          }
        });
  }

  private void applyListeners(YouTrackDBConfig config) {
    if (config != null) {
      for (YTDatabaseListener listener : config.getListeners()) {
        registerListener(listener);
      }
    }
  }

  /**
   * Opens a database using an authentication token received as an argument.
   *
   * @param iToken Authentication token
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple
   * methods in chain.
   */
  @Deprecated
  public YTDatabaseSession open(final OToken iToken) {
    throw new UnsupportedOperationException("Deprecated Method");
  }

  @Override
  public YTDatabaseSession create() {
    throw new UnsupportedOperationException("Deprecated Method");
  }

  /**
   * {@inheritDoc}
   */
  public void internalCreate(YouTrackDBConfig config, OSharedContext ctx) {
    ORecordSerializer serializer = ORecordSerializerFactory.instance().getDefaultRecordSerializer();
    if (serializer.toString().equals("ORecordDocument2csv")) {
      throw new YTDatabaseException(
          "Impossible to create the database with ORecordDocument2csv serializer");
    }
    storage.setRecordSerializer(serializer.toString(), serializer.getCurrentVersion());
    storage.setProperty(SQLStatement.CUSTOM_STRICT_SQL, "true");

    this.setSerializer(serializer);

    this.sharedContext = ctx;
    this.status = STATUS.OPEN;
    // THIS IF SHOULDN'T BE NEEDED, CREATE HAPPEN ONLY IN EMBEDDED
    applyAttributes(config);
    applyListeners(config);
    metadata = new OMetadataDefault(this);
    installHooksEmbedded();
    createMetadata(ctx);
  }

  public void callOnCreateListeners() {
    // WAKE UP DB LIFECYCLE LISTENER
    for (Iterator<ODatabaseLifecycleListener> it = YouTrackDBManager.instance()
        .getDbLifecycleListeners();
        it.hasNext(); ) {
      it.next().onCreate(getDatabaseOwner());
    }

    // WAKE UP LISTENERS
    for (YTDatabaseListener listener : browseListeners()) {
      try {
        listener.onCreate(this);
      } catch (Exception ignore) {
      }
    }
  }

  protected void createMetadata(OSharedContext shared) {
    metadata.init(shared);
    ((OSharedContextEmbedded) shared).create(this);
  }

  @Override
  protected void loadMetadata() {
    executeInTx(
        () -> {
          metadata = new OMetadataDefault(this);
          metadata.init(sharedContext);
          sharedContext.load(this);
        });
  }

  private void applyAttributes(YouTrackDBConfig config) {
    if (config != null) {
      for (Entry<ATTRIBUTES, Object> attrs : config.getAttributes().entrySet()) {
        this.set(attrs.getKey(), attrs.getValue());
      }
    }
  }

  @Override
  public void set(final ATTRIBUTES iAttribute, final Object iValue) {
    checkIfActive();

    if (iAttribute == null) {
      throw new IllegalArgumentException("attribute is null");
    }

    final String stringValue = OIOUtils.getStringContent(iValue != null ? iValue.toString() : null);
    final Storage storage = this.storage;
    switch (iAttribute) {
      case STATUS:
        if (stringValue == null) {
          throw new IllegalArgumentException("DB status can't be null");
        }
        setStatus(STATUS.valueOf(stringValue.toUpperCase(Locale.ENGLISH)));
        break;

      case DEFAULTCLUSTERID:
        if (iValue != null) {
          if (iValue instanceof Number) {
            storage.setDefaultClusterId(((Number) iValue).intValue());
          } else {
            storage.setDefaultClusterId(storage.getClusterIdByName(iValue.toString()));
          }
        }
        break;

      case TYPE:
        throw new IllegalArgumentException("Database type cannot be changed at run-time");

      case DATEFORMAT:
        if (stringValue == null) {
          throw new IllegalArgumentException("date format is null");
        }

        // CHECK FORMAT
        new SimpleDateFormat(stringValue).format(new Date());

        storage.setDateFormat(stringValue);
        break;

      case DATETIMEFORMAT:
        if (stringValue == null) {
          throw new IllegalArgumentException("date format is null");
        }

        // CHECK FORMAT
        new SimpleDateFormat(stringValue).format(new Date());

        storage.setDateTimeFormat(stringValue);
        break;

      case TIMEZONE:
        if (stringValue == null) {
          throw new IllegalArgumentException("Timezone can't be null");
        }

        // for backward compatibility, until 2.1.13 YouTrackDB accepted timezones in lowercase as well
        TimeZone timeZoneValue = TimeZone.getTimeZone(stringValue.toUpperCase(Locale.ENGLISH));
        if (timeZoneValue.equals(TimeZone.getTimeZone("GMT"))) {
          timeZoneValue = TimeZone.getTimeZone(stringValue);
        }

        storage.setTimeZone(timeZoneValue);
        break;

      case LOCALECOUNTRY:
        storage.setLocaleCountry(stringValue);
        break;

      case LOCALELANGUAGE:
        storage.setLocaleLanguage(stringValue);
        break;

      case CHARSET:
        storage.setCharset(stringValue);
        break;

      case CUSTOM:
        int indx = stringValue != null ? stringValue.indexOf('=') : -1;
        if (indx < 0) {
          if ("clear".equalsIgnoreCase(stringValue)) {
            clearCustomInternal();
          } else {
            throw new IllegalArgumentException(
                "Syntax error: expected <name> = <value> or clear, instead found: " + iValue);
          }
        } else {
          String customName = stringValue.substring(0, indx).trim();
          String customValue = stringValue.substring(indx + 1).trim();
          if (customValue.isEmpty()) {
            removeCustomInternal(customName);
          } else {
            setCustomInternal(customName, customValue);
          }
        }
        break;

      case CLUSTERSELECTION:
        storage.setClusterSelection(stringValue);
        break;

      case MINIMUMCLUSTERS:
        if (iValue != null) {
          if (iValue instanceof Number) {
            storage.setMinimumClusters(((Number) iValue).intValue());
          } else {
            storage.setMinimumClusters(Integer.parseInt(stringValue));
          }
        } else
        // DEFAULT = 1
        {
          storage.setMinimumClusters(1);
        }

        break;

      case CONFLICTSTRATEGY:
        storage.setConflictStrategy(
            YouTrackDBManager.instance().getRecordConflictStrategy().getStrategy(stringValue));
        break;

      case VALIDATION:
        storage.setValidation(Boolean.parseBoolean(stringValue));
        break;

      default:
        throw new IllegalArgumentException(
            "Option '" + iAttribute + "' not supported on alter database");
    }
  }

  private void clearCustomInternal() {
    storage.clearProperties();
  }

  private void removeCustomInternal(final String iName) {
    setCustomInternal(iName, null);
  }

  private void setCustomInternal(final String iName, final String iValue) {
    final Storage storage = this.storage;
    if (iValue == null || "null".equalsIgnoreCase(iValue))
    // REMOVE
    {
      storage.removeProperty(iName);
    } else
    // SET
    {
      storage.setProperty(iName, iValue);
    }
  }

  public YTDatabaseSession setCustom(final String name, final Object iValue) {
    checkIfActive();

    if ("clear".equalsIgnoreCase(name) && iValue == null) {
      clearCustomInternal();
    } else {
      String customName = name;
      String customValue = iValue == null ? null : "" + iValue;
      if (customName == null || customValue.isEmpty()) {
        removeCustomInternal(customName);
      } else {
        setCustomInternal(customName, customValue);
      }
    }

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public YTDatabaseSession create(String incrementalBackupPath) {
    throw new UnsupportedOperationException("use YouTrackDB");
  }

  @Override
  public YTDatabaseSession create(final Map<GlobalConfiguration, Object> iInitialSettings) {
    throw new UnsupportedOperationException("use YouTrackDB");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void drop() {
    throw new UnsupportedOperationException("use YouTrackDB");
  }

  /**
   * Returns a copy of current database if it's open. The returned instance can be used by another
   * thread without affecting current instance. The database copy is not set in thread local.
   */
  public YTDatabaseSessionInternal copy() {
    var storage = (Storage) getSharedContext().getStorage();
    storage.open(this, null, null, config.getConfigurations());
    YTDatabaseSessionEmbedded database = new YTDatabaseSessionEmbedded(storage);
    database.init(config, this.sharedContext);
    String user;
    if (getUser() != null) {
      user = getUser().getName(this);
    } else {
      user = null;
    }

    database.internalOpen(user, null, false);
    database.callOnOpenListeners();
    this.activateOnCurrentThread();
    return database;
  }

  @Override
  public boolean exists() {
    throw new UnsupportedOperationException("use YouTrackDB");
  }

  @Override
  public boolean isClosed() {
    return status == STATUS.CLOSED || storage.isClosed(this);
  }

  public void rebuildIndexes() {
    if (metadata.getIndexManagerInternal().autoRecreateIndexesAfterCrash(this)) {
      metadata.getIndexManagerInternal().recreateIndexes(this);
    }
  }

  protected void installHooksEmbedded() {
    hooks.clear();
  }

  @Override
  public Storage getStorage() {
    return storage;
  }

  @Override
  public OStorageInfo getStorageInfo() {
    return storage;
  }

  @Override
  public void replaceStorage(Storage iNewStorage) {
    this.getSharedContext().setStorage(iNewStorage);
    storage = iNewStorage;
  }

  @Override
  public YTResultSet query(String query, Object[] args) {
    checkOpenness();
    checkIfActive();
    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    try {
      preQueryStart();
      SQLStatement statement = OSQLEngine.parse(query, this);
      if (!statement.isIdempotent()) {
        throw new YTCommandExecutionException(
            "Cannot execute query on non idempotent statement: " + query);
      }
      YTResultSet original = statement.execute(this, args, true);
      YTLocalResultSetLifecycleDecorator result = new YTLocalResultSetLifecycleDecorator(original);
      queryStarted(result);
      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  @Override
  public YTResultSet query(String query, Map args) {
    checkOpenness();
    checkIfActive();
    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    preQueryStart();
    try {
      SQLStatement statement = OSQLEngine.parse(query, this);
      if (!statement.isIdempotent()) {
        throw new YTCommandExecutionException(
            "Cannot execute query on non idempotent statement: " + query);
      }
      YTResultSet original = statement.execute(this, args, true);
      YTLocalResultSetLifecycleDecorator result = new YTLocalResultSetLifecycleDecorator(original);
      queryStarted(result);
      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  @Override
  public YTResultSet command(String query, Object[] args) {
    checkOpenness();
    checkIfActive();

    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    preQueryStart();
    try {
      SQLStatement statement = OSQLEngine.parse(query, this);
      YTResultSet original = statement.execute(this, args, true);
      YTLocalResultSetLifecycleDecorator result;
      if (!statement.isIdempotent()) {
        // fetch all, close and detach
        YTInternalResultSet prefetched = new YTInternalResultSet();
        original.forEachRemaining(x -> prefetched.add(x));
        original.close();
        queryCompleted();
        result = new YTLocalResultSetLifecycleDecorator(prefetched);
      } else {
        // stream, keep open and attach to the current DB
        result = new YTLocalResultSetLifecycleDecorator(original);
        queryStarted(result);
      }
      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  @Override
  public YTResultSet command(String query, Map args) {
    checkOpenness();
    checkIfActive();

    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    try {
      preQueryStart();

      SQLStatement statement = OSQLEngine.parse(query, this);
      YTResultSet original = statement.execute(this, args, true);
      YTLocalResultSetLifecycleDecorator result;
      if (!statement.isIdempotent()) {
        // fetch all, close and detach
        YTInternalResultSet prefetched = new YTInternalResultSet();
        original.forEachRemaining(x -> prefetched.add(x));
        original.close();
        queryCompleted();
        result = new YTLocalResultSetLifecycleDecorator(prefetched);
      } else {
        // stream, keep open and attach to the current DB
        result = new YTLocalResultSetLifecycleDecorator(original);

        queryStarted(result);
      }

      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  @Override
  public YTResultSet execute(String language, String script, Object... args) {
    checkOpenness();
    checkIfActive();
    if (!"sql".equalsIgnoreCase(language)) {
      checkSecurity(ORule.ResourceGeneric.COMMAND, ORole.PERMISSION_EXECUTE, language);
    }
    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    try {
      preQueryStart();
      OScriptExecutor executor =
          getSharedContext()
              .getYouTrackDB()
              .getScriptManager()
              .getCommandManager()
              .getScriptExecutor(language);

      ((AbstractPaginatedStorage) this.storage).pauseConfigurationUpdateNotifications();
      YTResultSet original;
      try {
        original = executor.execute(this, script, args);
      } finally {
        ((AbstractPaginatedStorage) this.storage).fireConfigurationUpdateNotifications();
      }
      YTLocalResultSetLifecycleDecorator result = new YTLocalResultSetLifecycleDecorator(original);
      queryStarted(result);
      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  private void cleanQueryState() {
    this.queryState.pop();
  }

  private void queryCompleted() {
    OQueryDatabaseState state = this.queryState.peekLast();
    state.closeInternal(this);
  }

  private void queryStarted(YTLocalResultSetLifecycleDecorator result) {
    OQueryDatabaseState state = this.queryState.peekLast();
    state.setResultSet(result);
    this.queryStarted(result.getQueryId(), state);
    result.addLifecycleListener(this);
  }

  private void preQueryStart() {
    this.queryState.push(new OQueryDatabaseState());
  }

  @Override
  public YTResultSet execute(String language, String script, Map<String, ?> args) {
    checkOpenness();
    checkIfActive();
    if (!"sql".equalsIgnoreCase(language)) {
      checkSecurity(ORule.ResourceGeneric.COMMAND, ORole.PERMISSION_EXECUTE, language);
    }
    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    try {
      preQueryStart();
      OScriptExecutor executor =
          sharedContext
              .getYouTrackDB()
              .getScriptManager()
              .getCommandManager()
              .getScriptExecutor(language);
      YTResultSet original;

      ((AbstractPaginatedStorage) this.storage).pauseConfigurationUpdateNotifications();
      try {
        original = executor.execute(this, script, args);
      } finally {
        ((AbstractPaginatedStorage) this.storage).fireConfigurationUpdateNotifications();
      }

      YTLocalResultSetLifecycleDecorator result = new YTLocalResultSetLifecycleDecorator(original);
      queryStarted(result);
      return result;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  public YTLocalResultSetLifecycleDecorator query(OExecutionPlan plan, Map<Object, Object> params) {
    checkOpenness();
    checkIfActive();
    getSharedContext().getYouTrackDB().startCommand(Optional.empty());
    try {
      preQueryStart();
      BasicCommandContext ctx = new BasicCommandContext();
      ctx.setDatabase(this);
      ctx.setInputParameters(params);

      YTLocalResultSet result = new YTLocalResultSet((OInternalExecutionPlan) plan);
      YTLocalResultSetLifecycleDecorator decorator = new YTLocalResultSetLifecycleDecorator(result);
      queryStarted(decorator);

      return decorator;
    } finally {
      cleanQueryState();
      getSharedContext().getYouTrackDB().endCommand();
    }
  }

  public void queryStartUsingViewCluster(int clusterId) {
    OSharedContext sharedContext = getSharedContext();
    ViewManager viewManager = sharedContext.getViewManager();
    viewManager.startUsingViewCluster(clusterId);
    this.queryState.peekLast().addViewUseCluster(clusterId);
  }

  public void queryStartUsingViewIndex(String index) {
    OSharedContext sharedContext = getSharedContext();
    ViewManager viewManager = sharedContext.getViewManager();
    viewManager.startUsingViewIndex(index);
    this.queryState.peekLast().addViewUseIndex(index);
  }

  @Override
  public void queryStarted(String id, YTResultSet resultSet) {
    // to nothing just compatibility
  }

  public YouTrackDBConfig getConfig() {
    return config;
  }

  @Override
  public YTLiveQueryMonitor live(String query, YTLiveQueryResultListener listener, Object... args) {
    checkOpenness();
    checkIfActive();

    OLiveQueryListenerV2 queryListener = new LiveQueryListenerImpl(listener, query, this, args);
    YTDatabaseSessionInternal dbCopy = this.copy();
    this.activateOnCurrentThread();
    YTLiveQueryMonitor monitor = new YTLiveQueryMonitorEmbedded(queryListener.getToken(), dbCopy);
    return monitor;
  }

  @Override
  public YTLiveQueryMonitor live(
      String query, YTLiveQueryResultListener listener, Map<String, ?> args) {
    checkOpenness();
    checkIfActive();

    OLiveQueryListenerV2 queryListener =
        new LiveQueryListenerImpl(listener, query, this, (Map) args);
    YTDatabaseSessionInternal dbCopy = this.copy();
    this.activateOnCurrentThread();
    YTLiveQueryMonitor monitor = new YTLiveQueryMonitorEmbedded(queryListener.getToken(), dbCopy);
    return monitor;
  }

  @Override
  public void recycle(final Record record) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int addBlobCluster(final String iClusterName, final Object... iParameters) {
    int id;
    if (!existsCluster(iClusterName)) {
      id = addCluster(iClusterName, iParameters);
    } else {
      id = getClusterIdByName(iClusterName);
    }
    getMetadata().getSchema().addBlobCluster(id);
    return id;
  }

  @Override
  public YTIdentifiable beforeCreateOperations(YTIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_CREATE, id, iClusterName);

    YTRecordHook.RESULT triggerChanged = null;
    boolean changed = false;
    if (id instanceof EntityImpl doc) {

      if (!getSharedContext().getSecurity().canCreate(this, doc)) {
        throw new YTSecurityException(
            "Cannot update record "
                + doc
                + ": the resource has restricted access due to security policies");
      }

      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_CREATE, clazz.getName());
        if (clazz.isScheduler()) {
          getSharedContext().getScheduler().initScheduleRecord(this, doc);
          changed = true;
        }
        if (clazz.isOuser()) {
          doc.validate();
          changed = YTUser.encodePassword(this, doc);
        }
        if (clazz.isTriggered()) {
          triggerChanged = OClassTrigger.onRecordBeforeCreate(doc, this);
        }
        if (clazz.isRestricted()) {
          changed = ORestrictedAccessHook.onRecordBeforeCreate(doc, this);
        }
        if (clazz.isFunction()) {
          OFunctionLibraryImpl.validateFunctionRecord(doc);
        }
        ODocumentInternal.setPropertyEncryption(doc, PropertyEncryptionNone.instance());
      }
    }

    YTRecordHook.RESULT res = callbackHooks(YTRecordHook.TYPE.BEFORE_CREATE, id);
    if (changed
        || res == YTRecordHook.RESULT.RECORD_CHANGED
        || triggerChanged == YTRecordHook.RESULT.RECORD_CHANGED) {
      if (id instanceof EntityImpl) {
        ((EntityImpl) id).validate();
      }
      return id;
    } else {
      if (res == YTRecordHook.RESULT.RECORD_REPLACED
          || triggerChanged == YTRecordHook.RESULT.RECORD_REPLACED) {
        Record replaced = OHookReplacedRecordThreadLocal.INSTANCE.get();
        if (replaced instanceof EntityImpl) {
          ((EntityImpl) replaced).validate();
        }
        return replaced;
      }
    }
    return null;
  }

  @Override
  public YTIdentifiable beforeUpdateOperations(YTIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_UPDATE, id, iClusterName);

    YTRecordHook.RESULT triggerChanged = null;
    boolean changed = false;
    if (id instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        if (clazz.isScheduler()) {
          getSharedContext().getScheduler().preHandleUpdateScheduleInTx(this, doc);
          changed = true;
        }
        if (clazz.isOuser()) {
          changed = YTUser.encodePassword(this, doc);
        }
        if (clazz.isTriggered()) {
          triggerChanged = OClassTrigger.onRecordBeforeUpdate(doc, this);
        }
        if (clazz.isRestricted()) {
          if (!ORestrictedAccessHook.isAllowed(
              this, doc, ORestrictedOperation.ALLOW_UPDATE, true)) {
            throw new YTSecurityException(
                "Cannot update record "
                    + doc.getIdentity()
                    + ": the resource has restricted access");
          }
        }
        if (clazz.isFunction()) {
          OFunctionLibraryImpl.validateFunctionRecord(doc);
        }
        if (!getSharedContext().getSecurity().canUpdate(this, doc)) {
          throw new YTSecurityException(
              "Cannot update record "
                  + doc.getIdentity()
                  + ": the resource has restricted access due to security policies");
        }
        ODocumentInternal.setPropertyEncryption(doc, PropertyEncryptionNone.instance());
      }
    }
    YTRecordHook.RESULT res = callbackHooks(YTRecordHook.TYPE.BEFORE_UPDATE, id);
    if (res == YTRecordHook.RESULT.RECORD_CHANGED
        || triggerChanged == YTRecordHook.RESULT.RECORD_CHANGED) {
      if (id instanceof EntityImpl) {
        ((EntityImpl) id).validate();
      }
      return id;
    } else {
      if (res == YTRecordHook.RESULT.RECORD_REPLACED
          || triggerChanged == YTRecordHook.RESULT.RECORD_REPLACED) {
        Record replaced = OHookReplacedRecordThreadLocal.INSTANCE.get();
        if (replaced instanceof EntityImpl) {
          ((EntityImpl) replaced).validate();
        }
        return replaced;
      }
    }

    if (changed) {
      return id;
    }
    return null;
  }

  /**
   * Deletes a document. Behavior depends by the current running transaction if any. If no
   * transaction is running then the record is deleted immediately. If an Optimistic transaction is
   * running then the record will be deleted at commit time. The current transaction will continue
   * to see the record as deleted, while others not. If a Pessimistic transaction is running, then
   * an exclusive lock is acquired against the record. Current transaction will continue to see the
   * record as deleted, while others cannot access to it since it's locked.
   *
   * <p>If MVCC is enabled and the version of the document is different by the version stored in
   * the database, then a {@link YTConcurrentModificationException} exception is thrown.
   *
   * @param record record to delete
   */
  public void delete(Record record) {
    checkOpenness();

    if (record == null) {
      throw new YTDatabaseException("Cannot delete null document");
    }

    if (record instanceof Entity) {
      if (((Entity) record).isVertex()) {
        VertexInternal.deleteLinks(((Entity) record).toVertex());
      } else {
        if (((Entity) record).isEdge()) {
          EdgeEntityImpl.deleteLinks(((Entity) record).toEdge());
        }
      }
    }

    // CHECK ACCESS ON SCHEMA CLASS NAME (IF ANY)
    if (record instanceof EntityImpl && ((EntityImpl) record).getClassName() != null) {
      checkSecurity(
          ORule.ResourceGeneric.CLASS,
          ORole.PERMISSION_DELETE,
          ((EntityImpl) record).getClassName());
    }

    try {
      currentTx.deleteRecord((RecordAbstract) record);
    } catch (YTException e) {
      throw e;
    } catch (Exception e) {
      if (record instanceof EntityImpl) {
        throw YTException.wrapException(
            new YTDatabaseException(
                "Error on deleting record "
                    + record.getIdentity()
                    + " of class '"
                    + ((EntityImpl) record).getClassName()
                    + "'"),
            e);
      } else {
        throw YTException.wrapException(
            new YTDatabaseException("Error on deleting record " + record.getIdentity()), e);
      }
    }
  }

  @Override
  public void beforeDeleteOperations(YTIdentifiable id, String iClusterName) {
    checkSecurity(ORole.PERMISSION_DELETE, id, iClusterName);
    if (id instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        if (clazz.isTriggered()) {
          OClassTrigger.onRecordBeforeDelete(doc, this);
        }
        if (clazz.isRestricted()) {
          if (!ORestrictedAccessHook.isAllowed(
              this, doc, ORestrictedOperation.ALLOW_DELETE, true)) {
            throw new YTSecurityException(
                "Cannot delete record "
                    + doc.getIdentity()
                    + ": the resource has restricted access");
          }
        }
        if (!getSharedContext().getSecurity().canDelete(this, doc)) {
          throw new YTSecurityException(
              "Cannot delete record "
                  + doc.getIdentity()
                  + ": the resource has restricted access due to security policies");
        }
      }
    }
    callbackHooks(YTRecordHook.TYPE.BEFORE_DELETE, id);
  }

  public void afterCreateOperations(final YTIdentifiable id) {
    if (id instanceof EntityImpl doc) {
      final YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);

      if (clazz != null) {
        OClassIndexManager.checkIndexesAfterCreate(doc, this);
        if (clazz.isFunction()) {
          this.getSharedContext().getFunctionLibrary().createdFunction(doc);
        }
        if (clazz.isOuser() || clazz.isOrole() || clazz.isSecurityPolicy()) {
          sharedContext.getSecurity().incrementVersion(this);
        }
        if (clazz.isTriggered()) {
          OClassTrigger.onRecordAfterCreate(doc, this);
        }
      }

      OLiveQueryHook.addOp(doc, ORecordOperation.CREATED, this);
      OLiveQueryHookV2.addOp(this, doc, ORecordOperation.CREATED);
    }

    callbackHooks(YTRecordHook.TYPE.AFTER_CREATE, id);
  }

  public void afterUpdateOperations(final YTIdentifiable id) {
    if (id instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        OClassIndexManager.checkIndexesAfterUpdate((EntityImpl) id, this);

        if (clazz.isOuser() || clazz.isOrole() || clazz.isSecurityPolicy()) {
          sharedContext.getSecurity().incrementVersion(this);
        }

        if (clazz.isTriggered()) {
          OClassTrigger.onRecordAfterUpdate(doc, this);
        }

      }

    }
    callbackHooks(YTRecordHook.TYPE.AFTER_UPDATE, id);
  }

  public void afterDeleteOperations(final YTIdentifiable id) {
    if (id instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        OClassIndexManager.checkIndexesAfterDelete(doc, this);
        if (clazz.isFunction()) {
          this.getSharedContext().getFunctionLibrary().droppedFunction(doc);
        }
        if (clazz.isSequence()) {
          ((OSequenceLibraryProxy) getMetadata().getSequenceLibrary())
              .getDelegate()
              .onSequenceDropped(this, doc);
        }
        if (clazz.isScheduler()) {
          final String eventName = doc.field(OScheduledEvent.PROP_NAME);
          getSharedContext().getScheduler().removeEventInternal(eventName);
        }
        if (clazz.isTriggered()) {
          OClassTrigger.onRecordAfterDelete(doc, this);
        }
        getSharedContext().getViewManager().recordDeleted(clazz, doc, this);
      }
      OLiveQueryHook.addOp(doc, ORecordOperation.DELETED, this);
      OLiveQueryHookV2.addOp(this, doc, ORecordOperation.DELETED);
    }
    callbackHooks(YTRecordHook.TYPE.AFTER_DELETE, id);
  }

  @Override
  public void afterReadOperations(YTIdentifiable identifiable) {
    if (identifiable instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        if (clazz.isTriggered()) {
          OClassTrigger.onRecordAfterRead(doc, this);
        }
      }
    }
    callbackHooks(YTRecordHook.TYPE.AFTER_READ, identifiable);
  }

  @Override
  public boolean beforeReadOperations(YTIdentifiable identifiable) {
    if (identifiable instanceof EntityImpl doc) {
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
      if (clazz != null) {
        if (clazz.isTriggered()) {
          YTRecordHook.RESULT val = OClassTrigger.onRecordBeforeRead(doc, this);
          if (val == YTRecordHook.RESULT.SKIP) {
            return true;
          }
        }
        if (clazz.isRestricted()) {
          if (!ORestrictedAccessHook.isAllowed(this, doc, ORestrictedOperation.ALLOW_READ, false)) {
            return true;
          }
        }
        try {
          checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_READ, clazz.getName());
        } catch (YTSecurityException e) {
          return true;
        }

        if (!getSharedContext().getSecurity().canRead(this, doc)) {
          return true;
        }

        ODocumentInternal.setPropertyAccess(
            doc, new PropertyAccess(this, doc, getSharedContext().getSecurity()));
        ODocumentInternal.setPropertyEncryption(doc, PropertyEncryptionNone.instance());
      }
    }
    return callbackHooks(YTRecordHook.TYPE.BEFORE_READ, identifiable) == YTRecordHook.RESULT.SKIP;
  }

  @Override
  public void afterCommitOperations() {
    for (var operation : currentTx.getRecordOperations()) {
      if (operation.type == ORecordOperation.CREATED) {
        var record = operation.record;

        if (record instanceof EntityImpl doc) {
          YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);

          if (clazz != null) {
            if (clazz.isSequence()) {
              ((OSequenceLibraryProxy) getMetadata().getSequenceLibrary())
                  .getDelegate()
                  .onSequenceCreated(this, doc);
            }

            if (clazz.isScheduler()) {
              getMetadata().getScheduler().scheduleEvent(this, new OScheduledEvent(doc, this));
            }
          }
        }
      } else if (operation.type == ORecordOperation.UPDATED) {
        var record = operation.record;

        if (record instanceof EntityImpl doc) {
          YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(this, doc);
          if (clazz != null) {
            if (clazz.isFunction()) {
              this.getSharedContext().getFunctionLibrary().updatedFunction(doc);
            }
            if (clazz.isScheduler()) {
              getSharedContext().getScheduler().postHandleUpdateScheduleAfterTxCommit(this, doc);
            }
          }

          OLiveQueryHook.addOp(doc, ORecordOperation.UPDATED, this);
          OLiveQueryHookV2.addOp(this, doc, ORecordOperation.UPDATED);
        }
      }
    }

    super.afterCommitOperations();

    OLiveQueryHook.notifyForTxChanges(this);
    OLiveQueryHookV2.notifyForTxChanges(this);
  }

  @Override
  protected void afterRollbackOperations() {
    super.afterRollbackOperations();
    OLiveQueryHook.removePendingDatabaseOps(this);
    OLiveQueryHookV2.removePendingDatabaseOps(this);
  }

  public String getClusterName(final Record record) {
    int clusterId = record.getIdentity().getClusterId();
    if (clusterId == YTRID.CLUSTER_ID_INVALID) {
      // COMPUTE THE CLUSTER ID
      YTClass schemaClass = null;
      if (record instanceof EntityImpl) {
        schemaClass = ODocumentInternal.getImmutableSchemaClass(this, (EntityImpl) record);
      }
      if (schemaClass != null) {
        // FIND THE RIGHT CLUSTER AS CONFIGURED IN CLASS
        if (schemaClass.isAbstract()) {
          throw new YTSchemaException(
              "Document belongs to abstract class '"
                  + schemaClass.getName()
                  + "' and cannot be saved");
        }
        clusterId = schemaClass.getClusterForNewInstance((EntityImpl) record);
        return getClusterNameById(clusterId);
      } else {
        return getClusterNameById(storage.getDefaultClusterId());
      }

    } else {
      return getClusterNameById(clusterId);
    }
  }

  @Override
  public YTView getViewFromCluster(int cluster) {
    YTImmutableSchema schema = getMetadata().getImmutableSchemaSnapshot();
    YTView view = schema.getViewByClusterId(cluster);
    if (view == null) {
      String viewName = getSharedContext().getViewManager().getViewFromOldCluster(cluster);
      if (viewName != null) {
        view = schema.getView(viewName);
      }
    }
    return view;
  }

  @Override
  public boolean executeExists(YTRID rid) {
    checkOpenness();
    checkIfActive();
    try {
      checkSecurity(
          ORule.ResourceGeneric.CLUSTER,
          ORole.PERMISSION_READ,
          getClusterNameById(rid.getClusterId()));

      Record record = getTransaction().getRecord(rid);
      if (record == OTransactionAbstract.DELETED_RECORD) {
        // DELETED IN TX
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
      throw YTException.wrapException(
          new YTDatabaseException(
              "Error on retrieving record "
                  + rid
                  + " (cluster: "
                  + storage.getPhysicalClusterNameById(rid.getClusterId())
                  + ")"),
          t);
    }
  }

  @Override
  public <T> T sendSequenceAction(OSequenceAction action)
      throws ExecutionException, InterruptedException {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose Tools | Templates.
  }

  /**
   * {@inheritDoc}
   */
  public void checkSecurity(
      final ORule.ResourceGeneric resourceGeneric,
      final String resourceSpecific,
      final int iOperation) {
    if (user != null) {
      try {
        user.allow(this, resourceGeneric, resourceSpecific, iOperation);
      } catch (YTSecurityAccessException e) {

        if (LogManager.instance().isDebugEnabled()) {
          LogManager.instance()
              .debug(
                  this,
                  "User '%s' tried to access the reserved resource '%s.%s', operation '%s'",
                  getUser(),
                  resourceGeneric,
                  resourceSpecific,
                  iOperation);
        }

        throw e;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkSecurity(
      final ORule.ResourceGeneric iResourceGeneric,
      final int iOperation,
      final Object... iResourcesSpecific) {
    if (iResourcesSpecific == null || iResourcesSpecific.length == 0) {
      checkSecurity(iResourceGeneric, null, iOperation);
    } else {
      for (Object target : iResourcesSpecific) {
        checkSecurity(iResourceGeneric, target == null ? null : target.toString(), iOperation);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkSecurity(
      final ORule.ResourceGeneric iResourceGeneric,
      final int iOperation,
      final Object iResourceSpecific) {
    checkOpenness();
    checkSecurity(
        iResourceGeneric,
        iResourceSpecific == null ? null : iResourceSpecific.toString(),
        iOperation);
  }

  @Override
  @Deprecated
  public void checkSecurity(final String iResource, final int iOperation) {
    final String resourceSpecific = ORule.mapLegacyResourceToSpecificResource(iResource);
    final ORule.ResourceGeneric resourceGeneric =
        ORule.mapLegacyResourceToGenericResource(iResource);

    if (resourceSpecific == null || resourceSpecific.equals("*")) {
      checkSecurity(resourceGeneric, null, iOperation);
    }

    checkSecurity(resourceGeneric, resourceSpecific, iOperation);
  }

  @Override
  @Deprecated
  public void checkSecurity(
      final String iResourceGeneric, final int iOperation, final Object iResourceSpecific) {
    final ORule.ResourceGeneric resourceGeneric =
        ORule.mapLegacyResourceToGenericResource(iResourceGeneric);
    if (iResourceSpecific == null || iResourceSpecific.equals("*")) {
      checkSecurity(resourceGeneric, iOperation, (Object) null);
    }

    checkSecurity(resourceGeneric, iOperation, iResourceSpecific);
  }

  @Override
  @Deprecated
  public void checkSecurity(
      final String iResourceGeneric, final int iOperation, final Object... iResourcesSpecific) {
    final ORule.ResourceGeneric resourceGeneric =
        ORule.mapLegacyResourceToGenericResource(iResourceGeneric);
    checkSecurity(resourceGeneric, iOperation, iResourcesSpecific);
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

  public YTDatabaseSessionEmbedded setConflictStrategy(final String iStrategyName) {
    checkIfActive();
    storage.setConflictStrategy(
        YouTrackDBManager.instance().getRecordConflictStrategy().getStrategy(iStrategyName));
    return this;
  }

  public YTDatabaseSessionEmbedded setConflictStrategy(final ORecordConflictStrategy iResolver) {
    checkIfActive();
    storage.setConflictStrategy(iResolver);
    return this;
  }

  @Override
  public long getClusterRecordSizeByName(final String clusterName) {
    checkIfActive();
    try {
      return storage.getClusterRecordsSizeByName(clusterName);
    } catch (Exception e) {
      throw YTException.wrapException(
          new YTDatabaseException(
              "Error on reading records size for cluster '" + clusterName + "'"),
          e);
    }
  }

  @Override
  public long getClusterRecordSizeById(final int clusterId) {
    checkIfActive();
    try {
      return storage.getClusterRecordsSizeById(clusterId);
    } catch (Exception e) {
      throw YTException.wrapException(
          new YTDatabaseException(
              "Error on reading records size for cluster with id '" + clusterId + "'"),
          e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int iClusterId, boolean countTombstones) {
    final String name = getClusterNameById(iClusterId);
    if (name == null) {
      return 0;
    }
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, name);
    checkIfActive();
    return storage.count(this, iClusterId, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int[] iClusterIds, boolean countTombstones) {
    checkIfActive();
    String name;
    for (int iClusterId : iClusterIds) {
      name = getClusterNameById(iClusterId);
      checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, name);
    }
    return storage.count(this, iClusterIds, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(final String iClusterName) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);
    checkIfActive();

    final int clusterId = getClusterIdByName(iClusterName);
    if (clusterId < 0) {
      throw new IllegalArgumentException("Cluster '" + iClusterName + "' was not found");
    }
    return storage.count(this, clusterId);
  }

  @Override
  public boolean dropCluster(final String iClusterName) {
    checkIfActive();
    final int clusterId = getClusterIdByName(iClusterName);
    YTSchemaProxy schema = metadata.getSchema();
    YTClass clazz = schema.getClassByClusterId(clusterId);
    if (clazz != null) {
      clazz.removeClusterId(this, clusterId);
    }
    if (schema.getBlobClusters().contains(clusterId)) {
      schema.removeBlobCluster(iClusterName);
    }
    getLocalCache().freeCluster(clusterId);
    checkForClusterPermissions(iClusterName);
    return dropClusterInternal(iClusterName);
  }

  protected boolean dropClusterInternal(final String iClusterName) {
    return storage.dropCluster(this, iClusterName);
  }

  @Override
  public boolean dropCluster(final int clusterId) {
    checkIfActive();

    checkSecurity(
        ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_DELETE, getClusterNameById(clusterId));

    YTSchemaProxy schema = metadata.getSchema();
    final YTClass clazz = schema.getClassByClusterId(clusterId);
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

    final ORecordIteratorCluster<Record> iteratorCluster = browseCluster(clusterName);
    if (iteratorCluster == null) {
      return false;
    }

    executeInTxBatches((Iterator<Record>) iteratorCluster, (session, record) -> delete(record));

    return dropClusterInternal(clusterId);
  }

  public boolean dropClusterInternal(int clusterId) {
    return storage.dropCluster(this, clusterId);
  }

  @Override
  public long getSize() {
    checkIfActive();
    return storage.getSize(this);
  }

  public ODatabaseStats getStats() {
    ODatabaseStats stats = new ODatabaseStats();
    stats.loadedRecords = loadedRecordsCount;
    stats.minLoadRecordTimeMs = minRecordLoadMs;
    stats.maxLoadRecordTimeMs = minRecordLoadMs;
    stats.averageLoadRecordTimeMs =
        loadedRecordsCount == 0 ? 0 : (this.totalRecordLoadMs / loadedRecordsCount);

    stats.prefetchedRidbagsCount = ridbagPrefetchCount;
    stats.minRidbagPrefetchTimeMs = minRidbagPrefetchMs;
    stats.maxRidbagPrefetchTimeMs = maxRidbagPrefetchMs;
    stats.ridbagPrefetchTimeMs = totalRidbagPrefetchMs;
    return stats;
  }

  public void addRidbagPrefetchStats(long execTimeMs) {
    this.ridbagPrefetchCount++;
    totalRidbagPrefetchMs += execTimeMs;
    if (this.ridbagPrefetchCount == 1) {
      this.minRidbagPrefetchMs = execTimeMs;
      this.maxRidbagPrefetchMs = execTimeMs;
    } else {
      this.minRidbagPrefetchMs = Math.min(this.minRidbagPrefetchMs, execTimeMs);
      this.maxRidbagPrefetchMs = Math.max(this.maxRidbagPrefetchMs, execTimeMs);
    }
  }

  public void resetRecordLoadStats() {
    this.loadedRecordsCount = 0L;
    this.totalRecordLoadMs = 0L;
    this.minRecordLoadMs = 0L;
    this.maxRecordLoadMs = 0L;
    this.ridbagPrefetchCount = 0L;
    this.totalRidbagPrefetchMs = 0L;
    this.minRidbagPrefetchMs = 0L;
    this.maxRidbagPrefetchMs = 0L;
  }

  @Override
  public String incrementalBackup(final String path) throws UnsupportedOperationException {
    checkOpenness();
    checkIfActive();
    checkSecurity(ORule.ResourceGeneric.DATABASE, "backup", ORole.PERMISSION_EXECUTE);

    return storage.incrementalBackup(this, path, null);
  }

  @Override
  public ORecordMetadata getRecordMetadata(final YTRID rid) {
    checkIfActive();
    return storage.getRecordMetadata(this, rid);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void freeze(final boolean throwException) {
    checkOpenness();
    if (!(storage instanceof OFreezableStorageComponent)) {
      LogManager.instance()
          .error(
              this,
              "Only local paginated storage supports freeze. If you are using remote client please"
                  + " use OServerAdmin instead",
              null);

      return;
    }

    final long startTime = YouTrackDBManager.instance().getProfiler().startChrono();

    final OFreezableStorageComponent storage = getFreezableStorage();
    if (storage != null) {
      storage.freeze(throwException);
    }

    YouTrackDBManager.instance()
        .getProfiler()
        .stopChrono(
            "db." + getName() + ".freeze", "Time to freeze the database", startTime, "db.*.freeze");
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
    if (!(storage instanceof OFreezableStorageComponent)) {
      LogManager.instance()
          .error(
              this,
              "Only local paginated storage supports release. If you are using remote client please"
                  + " use OServerAdmin instead",
              null);
      return;
    }

    final long startTime = YouTrackDBManager.instance().getProfiler().startChrono();

    final OFreezableStorageComponent storage = getFreezableStorage();
    if (storage != null) {
      storage.release();
    }

    YouTrackDBManager.instance()
        .getProfiler()
        .stopChrono(
            "db." + getName() + ".release",
            "Time to release the database",
            startTime,
            "db.*.release");
  }

  private OFreezableStorageComponent getFreezableStorage() {
    Storage s = storage;
    if (s instanceof OFreezableStorageComponent) {
      return (OFreezableStorageComponent) s;
    } else {
      LogManager.instance()
          .error(
              this, "Storage of type " + s.getType() + " does not support freeze operation", null);
      return null;
    }
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
      throw new YTDatabaseException("Cannot reload a closed db");
    }
    metadata.reload();
    storage.reload(this);
  }

  @Override
  public void internalCommit(OTransactionOptimistic transaction) {
    this.storage.commit(transaction);
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
        LogManager.instance().error(this, "Exception during rollback of active transaction", e);
      }

      callOnCloseListeners();

      status = STATUS.CLOSED;
      if (!recycle) {
        sharedContext = null;

        if (storage != null) {
          storage.close(this);
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
    return storage.getLastClusterPosition(clusterId);
  }

  @Override
  public String getClusterRecordConflictStrategy(int clusterId) {
    return storage.getClusterRecordConflictStrategy(clusterId);
  }

  @Override
  public int[] getClustersIds(Set<String> filterClusters) {
    checkIfActive();
    return storage.getClustersIds(filterClusters);
  }

  public void startExclusiveMetadataChange() {
    ((AbstractPaginatedStorage) storage).startDDL();
  }

  public void endExclusiveMetadataChange() {
    ((AbstractPaginatedStorage) storage).endDDL();
  }

  @Override
  public long truncateClass(String name, boolean polimorfic) {
    this.checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_UPDATE);
    YTClass clazz = getClass(name);
    if (clazz.isSubClassOf(OSecurityShared.RESTRICTED_CLASSNAME)) {
      throw new YTSecurityException(
          "Class '"
              + getName()
              + "' cannot be truncated because has record level security enabled (extends '"
              + OSecurityShared.RESTRICTED_CLASSNAME
              + "')");
    }

    int[] clusterIds;
    if (polimorfic) {
      clusterIds = clazz.getPolymorphicClusterIds();
    } else {
      clusterIds = clazz.getClusterIds();
    }
    long count = 0;
    for (int id : clusterIds) {
      if (id < 0) {
        continue;
      }
      final String clusterName = getClusterNameById(id);
      if (clusterName == null) {
        continue;
      }
      count += truncateClusterInternal(clusterName);
    }
    return count;
  }

  @Override
  public void truncateClass(String name) {
    truncateClass(name, true);
  }

  @Override
  public long truncateClusterInternal(String clusterName) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_DELETE, clusterName);
    checkForClusterPermissions(clusterName);

    int id = getClusterIdByName(clusterName);
    if (id == -1) {
      throw new YTDatabaseException("Cluster with name " + clusterName + " does not exist");
    }
    final YTClass clazz = getMetadata().getSchema().getClassByClusterId(id);
    if (clazz != null) {
      checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_DELETE, clazz.getName());
    }

    long count = 0;
    final ORecordIteratorCluster<Record> iteratorCluster =
        new ORecordIteratorCluster<Record>(this, id);

    while (iteratorCluster.hasNext()) {
      executeInTx(
          () -> {
            final Record record = bindToSession(iteratorCluster.next());
            record.delete();
          });
      count++;
    }
    return count;
  }

  @Override
  public NonTxReadMode getNonTxReadMode() {
    return nonTxReadMode;
  }

  @Override
  public void truncateCluster(String clusterName) {
    truncateClusterInternal(clusterName);
  }
}