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
package com.jetbrains.youtrack.db.internal.core.db;

import com.jetbrains.youtrack.db.internal.common.log.LogManager;
import com.jetbrains.youtrack.db.internal.common.util.OCallable;
import com.jetbrains.youtrack.db.internal.core.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTSchema;
import com.jetbrains.youtrack.db.internal.core.record.Entity;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.security.ODefaultSecuritySystem;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResultSet;
import java.util.UUID;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;

public class OSystemDatabase {

  public static final String SYSTEM_DB_NAME = "OSystem";

  public static final String SERVER_INFO_CLASS = "ServerInfo";
  public static final String SERVER_ID_PROPERTY = "serverId";

  private final YouTrackDBInternal context;
  private String serverId;

  public OSystemDatabase(final YouTrackDBInternal context) {
    this.context = context;
  }

  public String getSystemDatabaseName() {
    return OSystemDatabase.SYSTEM_DB_NAME;
  }

  /**
   * Adds the specified cluster to the class, if it doesn't already exist.
   */
  public void createCluster(final String className, final String clusterName) {
    final YTDatabaseSessionInternal currentDB = ODatabaseRecordThreadLocal.instance()
        .getIfDefined();
    try {
      final YTDatabaseSessionInternal sysdb = openSystemDatabase();
      try {

        if (!sysdb.existsCluster(clusterName)) {
          YTSchema schema = sysdb.getMetadata().getSchema();
          YTClass cls = schema.getClass(className);

          if (cls != null) {
            cls.addCluster(sysdb, clusterName);
          } else {
            LogManager.instance()
                .error(this, "createCluster() Class name %s does not exist", null, className);
          }
        }

      } finally {
        sysdb.close();
      }

    } finally {
      if (currentDB != null) {
        ODatabaseRecordThreadLocal.instance().set(currentDB);
      } else {
        ODatabaseRecordThreadLocal.instance().remove();
      }
    }
  }

  /**
   * Opens the System Database and returns an YTDatabaseSessionInternal object. The caller is
   * responsible for retrieving any ThreadLocal-stored database before openSystemDatabase() is
   * called and restoring it after the database is closed.
   */
  public YTDatabaseSessionInternal openSystemDatabase() {
    if (!exists()) {
      init();
    }
    return context.openNoAuthorization(SYSTEM_DB_NAME);
  }

  public <R> R execute(
      @Nonnull final BiFunction<YTResultSet, YTDatabaseSession, R> callback, final String sql,
      final Object... args) {
    final YTDatabaseSessionInternal currentDB = ODatabaseRecordThreadLocal.instance()
        .getIfDefined();
    try {
      // BYPASS SECURITY
      try (final YTDatabaseSession db = openSystemDatabase()) {
        try (YTResultSet result = db.command(sql, args)) {
          return callback.apply(result, db);
        }
      }
    } finally {
      if (currentDB != null) {
        ODatabaseRecordThreadLocal.instance().set(currentDB);
      } else {
        ODatabaseRecordThreadLocal.instance().remove();
      }
    }
  }

  public EntityImpl save(final EntityImpl document) {
    return save(document, null);
  }

  public EntityImpl save(final EntityImpl document, final String clusterName) {
    final YTDatabaseSessionInternal currentDB = ODatabaseRecordThreadLocal.instance()
        .getIfDefined();
    try {
      // BYPASS SECURITY
      final YTDatabaseSessionInternal db = openSystemDatabase();
      try {
        if (clusterName != null) {
          return db.save(document, clusterName);
        } else {
          return db.save(document);
        }
      } finally {
        db.close();
      }

    } finally {
      if (currentDB != null) {
        ODatabaseRecordThreadLocal.instance().set(currentDB);
      } else {
        ODatabaseRecordThreadLocal.instance().remove();
      }
    }
  }

  public void init() {
    final ODatabaseRecordThreadLocal tl = ODatabaseRecordThreadLocal.instance();
    final YTDatabaseSessionInternal oldDbInThread = tl != null ? tl.getIfDefined() : null;
    try {
      if (!exists()) {
        LogManager.instance()
            .info(this, "Creating the system database '%s' for current server", SYSTEM_DB_NAME);

        YouTrackDBConfig config =
            YouTrackDBConfig.builder()
                .addConfig(GlobalConfiguration.CREATE_DEFAULT_USERS, false)
                .addConfig(GlobalConfiguration.CLASS_MINIMUM_CLUSTERS, 1)
                .build();
        ODatabaseType type = ODatabaseType.PLOCAL;
        if (context.isMemoryOnly()) {
          type = ODatabaseType.MEMORY;
        }
        context.create(SYSTEM_DB_NAME, null, null, type, config);
        try (var session = context.openNoAuthorization(SYSTEM_DB_NAME)) {
          ODefaultSecuritySystem.createSystemRoles(session);
        }
      }
      checkServerId();

    } finally {
      if (oldDbInThread != null) {
        ODatabaseRecordThreadLocal.instance().set(oldDbInThread);
      } else {
        ODatabaseRecordThreadLocal.instance().remove();
      }
    }
  }

  private synchronized void checkServerId() {
    try (YTDatabaseSessionInternal db = openSystemDatabase()) {
      YTClass clazz = db.getClass(SERVER_INFO_CLASS);
      if (clazz == null) {
        clazz = db.createClass(SERVER_INFO_CLASS);
      }
      var clz = clazz;
      db.executeInTx(
          () -> {
            Entity info;
            if (clz.count(db) == 0) {
              info = db.newEntity(SERVER_INFO_CLASS);
            } else {
              info = db.browseClass(clz.getName()).next();
            }
            this.serverId = info.getProperty(SERVER_ID_PROPERTY);
            if (this.serverId == null) {
              this.serverId = UUID.randomUUID().toString();
              info.setProperty(SERVER_ID_PROPERTY, serverId);
              info.save();
            }
          });
    }
  }

  public void executeInDBScope(OCallable<Void, YTDatabaseSessionInternal> callback) {
    executeWithDB(callback);
  }

  public <T> T executeWithDB(OCallable<T, YTDatabaseSessionInternal> callback) {
    final YTDatabaseSessionInternal currentDB = ODatabaseRecordThreadLocal.instance()
        .getIfDefined();
    try {
      try (final var db = openSystemDatabase()) {
        return callback.call(db);
      }
    } finally {
      if (currentDB != null) {
        ODatabaseRecordThreadLocal.instance().set(currentDB);
      } else {
        ODatabaseRecordThreadLocal.instance().remove();
      }
    }
  }


  public boolean exists() {
    return context.exists(SYSTEM_DB_NAME, null, null);
  }

  public String getServerId() {
    return serverId;
  }
}