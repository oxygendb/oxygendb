/**
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * <p>*
 */
package com.orientechnologies.security.auditing;

import com.jetbrains.youtrack.db.internal.common.parser.OVariableParser;
import com.jetbrains.youtrack.db.internal.common.parser.OVariableParserListener;
import com.jetbrains.youtrack.db.internal.core.command.CommandExecutor;
import com.jetbrains.youtrack.db.internal.core.command.CommandRequestText;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.OSystemDatabase;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseListener;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.hook.YTRecordHookAbstract;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTImmutableClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.metadata.security.YTSecurityUser;
import com.jetbrains.youtrack.db.internal.core.record.Record;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.ODocumentInternal;
import com.jetbrains.youtrack.db.internal.core.security.OAuditingOperation;
import com.jetbrains.youtrack.db.internal.core.security.OSecuritySystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Hook to audit database access.
 */
public class YTAuditingHook extends YTRecordHookAbstract implements YTDatabaseListener {

  private final Map<String, OAuditingClassConfig> classes =
      new HashMap<String, OAuditingClassConfig>(20);
  private final OAuditingLoggingThread auditingThread;

  private final Map<YTDatabaseSession, List<EntityImpl>> operations = new ConcurrentHashMap<>();
  private volatile LinkedBlockingQueue<EntityImpl> auditingQueue;
  private final Set<OAuditingCommandConfig> commands = new HashSet<OAuditingCommandConfig>();
  private boolean onGlobalCreate;
  private boolean onGlobalRead;
  private boolean onGlobalUpdate;
  private boolean onGlobalDelete;
  private OAuditingClassConfig defaultConfig = new OAuditingClassConfig();
  private OAuditingSchemaConfig schemaConfig;
  private EntityImpl iConfiguration;

  private static class OAuditingCommandConfig {

    public String regex;
    public String message;

    public OAuditingCommandConfig(final EntityImpl cfg) {
      regex = cfg.field("regex");
      message = cfg.field("message");
    }
  }

  private static class OAuditingClassConfig {

    public boolean polymorphic = true;
    public boolean onCreateEnabled = false;
    public String onCreateMessage;
    public boolean onReadEnabled = false;
    public String onReadMessage;
    public boolean onUpdateEnabled = false;
    public String onUpdateMessage;
    public boolean onUpdateChanges = true;
    public boolean onDeleteEnabled = false;
    public String onDeleteMessage;

    public OAuditingClassConfig() {
    }

    public OAuditingClassConfig(final EntityImpl cfg) {
      if (cfg.containsField("polymorphic")) {
        polymorphic = cfg.field("polymorphic");
      }

      // CREATE
      if (cfg.containsField("onCreateEnabled")) {
        onCreateEnabled = cfg.field("onCreateEnabled");
      }
      if (cfg.containsField("onCreateMessage")) {
        onCreateMessage = cfg.field("onCreateMessage");
      }

      // READ
      if (cfg.containsField("onReadEnabled")) {
        onReadEnabled = cfg.field("onReadEnabled");
      }
      if (cfg.containsField("onReadMessage")) {
        onReadMessage = cfg.field("onReadMessage");
      }

      // UPDATE
      if (cfg.containsField("onUpdateEnabled")) {
        onUpdateEnabled = cfg.field("onUpdateEnabled");
      }
      if (cfg.containsField("onUpdateMessage")) {
        onUpdateMessage = cfg.field("onUpdateMessage");
      }
      if (cfg.containsField("onUpdateChanges")) {
        onUpdateChanges = cfg.field("onUpdateChanges");
      }

      // DELETE
      if (cfg.containsField("onDeleteEnabled")) {
        onDeleteEnabled = cfg.field("onDeleteEnabled");
      }
      if (cfg.containsField("onDeleteMessage")) {
        onDeleteMessage = cfg.field("onDeleteMessage");
      }
    }
  }

  // Handles the auditing-config "schema" configuration.
  private class OAuditingSchemaConfig extends OAuditingConfig {

    private boolean onCreateClassEnabled = false;
    private final String onCreateClassMessage;

    private boolean onDropClassEnabled = false;
    private final String onDropClassMessage;

    public OAuditingSchemaConfig(final EntityImpl cfg) {
      if (cfg.containsField("onCreateClassEnabled")) {
        onCreateClassEnabled = cfg.field("onCreateClassEnabled");
      }

      onCreateClassMessage = cfg.field("onCreateClassMessage");

      if (cfg.containsField("onDropClassEnabled")) {
        onDropClassEnabled = cfg.field("onDropClassEnabled");
      }

      onDropClassMessage = cfg.field("onDropClassMessage");
    }

    @Override
    public String formatMessage(final OAuditingOperation op, final String subject) {
      if (op == OAuditingOperation.CREATEDCLASS) {
        return resolveMessage(onCreateClassMessage, "class", subject);
      } else if (op == OAuditingOperation.DROPPEDCLASS) {
        return resolveMessage(onDropClassMessage, "class", subject);
      }

      return subject;
    }

    @Override
    public boolean isEnabled(OAuditingOperation op) {
      if (op == OAuditingOperation.CREATEDCLASS) {
        return onCreateClassEnabled;
      } else if (op == OAuditingOperation.DROPPEDCLASS) {
        return onDropClassEnabled;
      }

      return false;
    }
  }

  /// / YTAuditingHook
  public YTAuditingHook(final String iConfiguration) {
    this(new EntityImpl().fromJSON(iConfiguration, "noMap"), null);
  }

  public YTAuditingHook(final String iConfiguration, final OSecuritySystem system) {
    this(new EntityImpl().fromJSON(iConfiguration, "noMap"), system);
  }

  public YTAuditingHook(final EntityImpl iConfiguration) {
    this(iConfiguration, null);
  }

  public YTAuditingHook(final EntityImpl iConfiguration, final OSecuritySystem system) {
    this.iConfiguration = iConfiguration;

    onGlobalCreate = onGlobalRead = onGlobalUpdate = onGlobalDelete = false;

    final EntityImpl classesCfg = iConfiguration.field("classes");
    if (classesCfg != null) {
      for (String c : classesCfg.fieldNames()) {
        final OAuditingClassConfig cfg = new OAuditingClassConfig(classesCfg.field(c));
        if (c.equals("*")) {
          defaultConfig = cfg;
        } else {
          classes.put(c, cfg);
        }

        if (cfg.onCreateEnabled) {
          onGlobalCreate = true;
        }
        if (cfg.onReadEnabled) {
          onGlobalRead = true;
        }
        if (cfg.onUpdateEnabled) {
          onGlobalUpdate = true;
        }
        if (cfg.onDeleteEnabled) {
          onGlobalDelete = true;
        }
      }
    }

    final Iterable<EntityImpl> commandCfg = iConfiguration.field("commands");

    if (commandCfg != null) {

      for (EntityImpl cfg : commandCfg) {
        commands.add(new OAuditingCommandConfig(cfg));
      }
    }

    final EntityImpl schemaCfgDoc = iConfiguration.field("schema");
    if (schemaCfgDoc != null) {
      schemaConfig = new OAuditingSchemaConfig(schemaCfgDoc);
    }

    auditingQueue = new LinkedBlockingQueue<EntityImpl>();
    auditingThread =
        new OAuditingLoggingThread(
            ODatabaseRecordThreadLocal.instance().get().getName(),
            auditingQueue,
            system.getContext(),
            system);

    auditingThread.start();
  }

  public YTAuditingHook(final OSecuritySystem server) {
    auditingQueue = new LinkedBlockingQueue<EntityImpl>();
    auditingThread =
        new OAuditingLoggingThread(
            OSystemDatabase.SYSTEM_DB_NAME, auditingQueue, server.getContext(), server);

    auditingThread.start();
  }

  @Override
  public void onCreate(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onDelete(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onOpen(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onBeforeTxBegin(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onBeforeTxRollback(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onAfterTxRollback(YTDatabaseSession iDatabase) {

    synchronized (operations) {
      operations.remove(iDatabase);
    }
  }

  @Override
  public void onBeforeTxCommit(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onAfterTxCommit(YTDatabaseSession iDatabase) {

    List<EntityImpl> oDocuments = null;

    synchronized (operations) {
      oDocuments = operations.remove(iDatabase);
    }
    if (oDocuments != null) {
      for (EntityImpl oDocument : oDocuments) {
        auditingQueue.offer(oDocument);
      }
    }
  }

  @Override
  public void onClose(YTDatabaseSession iDatabase) {
  }

  @Override
  public void onBeforeCommand(CommandRequestText iCommand, CommandExecutor executor) {
  }

  @Override
  public void onAfterCommand(
      CommandRequestText iCommand, CommandExecutor executor, Object result) {
    logCommand(iCommand.getText());
  }

  @Override
  public boolean onCorruptionRepairDatabase(
      YTDatabaseSession iDatabase, String iReason, String iWhatWillbeFixed) {
    return false;
  }

  public EntityImpl getConfiguration() {
    return iConfiguration;
  }

  @Override
  public void onRecordAfterCreate(final Record iRecord) {
    if (!onGlobalCreate) {
      return;
    }

    log(OAuditingOperation.CREATED, iRecord);
  }

  @Override
  public void onRecordAfterRead(final Record iRecord) {
    if (!onGlobalRead) {
      return;
    }

    log(OAuditingOperation.LOADED, iRecord);
  }

  @Override
  public void onRecordAfterUpdate(final Record iRecord) {

    if (iRecord instanceof EntityImpl doc) {
      YTDatabaseSessionInternal db = ODatabaseRecordThreadLocal.instance().get();
      YTImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(db, doc);

      if (clazz.isOuser() && Arrays.asList(doc.getDirtyFields()).contains("password")) {
        String name = doc.getProperty("name");
        String message = String.format("The password for user '%s' has been changed", name);
        log(db, OAuditingOperation.CHANGED_PWD, db.getName(), db.getUser(), message);
      }
    }
    if (!onGlobalUpdate) {
      return;
    }

    log(OAuditingOperation.UPDATED, iRecord);
  }

  @Override
  public void onRecordAfterDelete(final Record iRecord) {
    if (!onGlobalDelete) {
      return;
    }

    log(OAuditingOperation.DELETED, iRecord);
  }

  @Override
  public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
    return DISTRIBUTED_EXECUTION_MODE.SOURCE_NODE;
  }

  protected void logCommand(final String command) {
    if (auditingQueue == null) {
      return;
    }

    for (OAuditingCommandConfig cfg : commands) {
      if (command.matches(cfg.regex)) {
        final YTDatabaseSessionInternal db = ODatabaseRecordThreadLocal.instance().get();

        final EntityImpl doc =
            createLogDocument(db
                , OAuditingOperation.COMMAND,
                db.getName(),
                db.getUser(), formatCommandNote(command, cfg.message));
        auditingQueue.offer(doc);
      }
    }
  }

  private String formatCommandNote(final String command, String message) {
    if (message == null || message.isEmpty()) {
      return command;
    }
    return (String)
        OVariableParser.resolveVariables(
            message,
            "${",
            "}",
            new OVariableParserListener() {
              @Override
              public Object resolve(final String iVariable) {
                if (iVariable.startsWith("command")) {
                  return command;
                }
                return null;
              }
            });
  }

  protected void log(final OAuditingOperation operation, final Record iRecord) {
    if (auditingQueue == null)
    // LOGGING THREAD INACTIVE, SKIP THE LOG
    {
      return;
    }

    final OAuditingClassConfig cfg = getAuditConfiguration(iRecord);
    if (cfg == null)
    // SKIP
    {
      return;
    }

    EntityImpl changes = null;
    String note = null;

    switch (operation) {
      case CREATED:
        if (!cfg.onCreateEnabled)
        // SKIP
        {
          return;
        }
        note = cfg.onCreateMessage;
        break;
      case UPDATED:
        if (!cfg.onUpdateEnabled)
        // SKIP
        {
          return;
        }
        note = cfg.onUpdateMessage;

        if (iRecord instanceof EntityImpl doc && cfg.onUpdateChanges) {
          changes = new EntityImpl();

          for (String f : doc.getDirtyFields()) {
            EntityImpl fieldChanges = new EntityImpl();
            fieldChanges.field("from", doc.getOriginalValue(f));
            fieldChanges.field("to", (Object) doc.rawField(f));
            changes.field(f, fieldChanges, YTType.EMBEDDED);
          }
        }
        break;
      case DELETED:
        if (!cfg.onDeleteEnabled)
        // SKIP
        {
          return;
        }
        note = cfg.onDeleteMessage;
        break;
    }

    final YTDatabaseSessionInternal db = ODatabaseRecordThreadLocal.instance().get();

    final EntityImpl doc =
        createLogDocument(db, operation, db.getName(), db.getUser(), formatNote(iRecord, note));
    doc.field("record", iRecord.getIdentity());
    if (changes != null) {
      doc.field("changes", changes, YTType.EMBEDDED);
    }

    if (db.getTransaction().isActive()) {
      synchronized (operations) {
        List<EntityImpl> oDocuments = operations.get(db);
        if (oDocuments == null) {
          oDocuments = new ArrayList<EntityImpl>();
          operations.put(db, oDocuments);
        }
        oDocuments.add(doc);
      }
    } else {
      auditingQueue.offer(doc);
    }
  }

  private String formatNote(final Record iRecord, final String iNote) {
    if (iNote == null) {
      return null;
    }

    return (String)
        OVariableParser.resolveVariables(
            iNote,
            "${",
            "}",
            new OVariableParserListener() {
              @Override
              public Object resolve(final String iVariable) {
                if (iVariable.startsWith("field.")) {
                  if (iRecord instanceof EntityImpl) {
                    final String fieldName = iVariable.substring("field.".length());
                    return ((EntityImpl) iRecord).field(fieldName);
                  }
                }
                return null;
              }
            });
  }

  private OAuditingClassConfig getAuditConfiguration(final Record iRecord) {
    OAuditingClassConfig cfg = null;

    if (iRecord instanceof EntityImpl) {
      YTClass cls = ((EntityImpl) iRecord).getSchemaClass();
      if (cls != null) {

        if (cls.getName().equals(ODefaultAuditing.AUDITING_LOG_CLASSNAME))
        // SKIP LOG CLASS
        {
          return null;
        }

        cfg = classes.get(cls.getName());

        // BROWSE SUPER CLASSES UP TO ROOT
        while (cfg == null && cls != null) {
          cls = cls.getSuperClass();
          if (cls != null) {
            cfg = classes.get(cls.getName());
            if (cfg != null && !cfg.polymorphic) {
              // NOT POLYMORPHIC: IGNORE IT AND EXIT FROM THE LOOP
              cfg = null;
              break;
            }
          }
        }
      }
    }

    if (cfg == null)
    // ASSIGN DEFAULT CFG (*)
    {
      cfg = defaultConfig;
    }

    return cfg;
  }

  public void shutdown(final boolean waitForAllLogs) {
    if (auditingThread != null) {
      auditingThread.sendShutdown(waitForAllLogs);
      auditingQueue = null;
    }
  }

  /*
    private OAuditingClassConfig getAuditConfiguration(YTClass cls) {
      OAuditingClassConfig cfg = null;

      if (cls != null) {

        cfg = classes.get(cls.getName());

        // BROWSE SUPER CLASSES UP TO ROOT
        while (cfg == null && cls != null) {
          cls = cls.getSuperClass();

          if (cls != null) {
            cfg = classes.get(cls.getName());

            if (cfg != null && !cfg.polymorphic) {
              // NOT POLYMORPHIC: IGNORE IT AND EXIT FROM THE LOOP
              cfg = null;
              break;
            }
          }
        }
      }

      if (cfg == null)
        // ASSIGN DEFAULT CFG (*)
        cfg = defaultConfig;

      return cfg;
    }
  */
  private String formatClassNote(final YTClass cls, final String note) {
    if (note == null || note.isEmpty()) {
      return cls.getName();
    }

    return (String)
        OVariableParser.resolveVariables(
            note,
            "${",
            "}",
            new OVariableParserListener() {
              @Override
              public Object resolve(final String iVariable) {

                if (iVariable.equalsIgnoreCase("class")) {
                  return cls.getName();
                }

                return null;
              }
            });
  }

  protected void logClass(final OAuditingOperation operation, final String note) {
    final YTDatabaseSessionInternal db = ODatabaseRecordThreadLocal.instance().get();

    final YTSecurityUser user = db.getUser();

    final EntityImpl doc = createLogDocument(db, operation, db.getName(), user, note);

    auditingQueue.offer(doc);
  }

  protected void logClass(final OAuditingOperation operation, final YTClass cls) {
    if (schemaConfig != null && schemaConfig.isEnabled(operation)) {
      logClass(operation, schemaConfig.formatMessage(operation, cls.getName()));
    }
  }

  public void onCreateClass(YTClass iClass) {
    logClass(OAuditingOperation.CREATEDCLASS, iClass);
  }

  public void onDropClass(YTClass iClass) {
    logClass(OAuditingOperation.DROPPEDCLASS, iClass);
  }

  public void log(
      YTDatabaseSessionInternal db, final OAuditingOperation operation,
      final String dbName,
      YTSecurityUser user,
      final String message) {
    if (auditingQueue != null) {
      auditingQueue.offer(createLogDocument(db, operation, dbName, user, message));
    }
  }

  private static EntityImpl createLogDocument(
      YTDatabaseSessionInternal session, final OAuditingOperation operation,
      final String dbName,
      YTSecurityUser user,
      final String message) {
    EntityImpl doc = null;

    doc = new EntityImpl();
    doc.field("date", System.currentTimeMillis());
    doc.field("operation", operation.getByte());

    if (user != null) {
      doc.field("user", user.getName(session));
      doc.field("userType", user.getUserType());
    }

    if (message != null) {
      doc.field("note", message);
    }

    if (dbName != null) {
      doc.field("database", dbName);
    }

    return doc;
  }
}