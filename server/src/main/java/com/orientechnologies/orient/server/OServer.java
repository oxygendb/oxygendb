/*
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
 */
package com.orientechnologies.orient.server;

import com.jetbrains.youtrack.db.internal.common.console.ConsoleReader;
import com.jetbrains.youtrack.db.internal.common.console.DefaultConsoleReader;
import com.jetbrains.youtrack.db.internal.common.exception.BaseException;
import com.jetbrains.youtrack.db.internal.common.io.FileUtils;
import com.jetbrains.youtrack.db.internal.common.log.AnsiCode;
import com.jetbrains.youtrack.db.internal.common.log.LogManager;
import com.jetbrains.youtrack.db.internal.common.parser.SystemVariableResolver;
import com.jetbrains.youtrack.db.internal.common.profiler.AbstractProfiler.ProfilerHookValue;
import com.jetbrains.youtrack.db.internal.common.profiler.Profiler.METRIC_TYPE;
import com.jetbrains.youtrack.db.internal.core.YouTrackDBConstants;
import com.jetbrains.youtrack.db.internal.core.YouTrackDBManager;
import com.jetbrains.youtrack.db.internal.core.config.ContextConfiguration;
import com.jetbrains.youtrack.db.internal.core.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseType;
import com.jetbrains.youtrack.db.internal.core.db.SystemDatabase;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDB;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfig;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfigBuilder;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBInternal;
import com.jetbrains.youtrack.db.internal.core.db.document.DatabaseDocumentTxInternal;
import com.jetbrains.youtrack.db.internal.core.exception.ConfigurationException;
import com.jetbrains.youtrack.db.internal.core.exception.DatabaseException;
import com.jetbrains.youtrack.db.internal.core.exception.StorageException;
import com.jetbrains.youtrack.db.internal.core.metadata.security.SecurityUser;
import com.jetbrains.youtrack.db.internal.core.metadata.security.auth.TokenAuthInfo;
import com.jetbrains.youtrack.db.internal.core.security.InvalidPasswordException;
import com.jetbrains.youtrack.db.internal.core.security.ParsedToken;
import com.jetbrains.youtrack.db.internal.core.security.SecuritySystem;
import com.orientechnologies.orient.server.config.OServerConfiguration;
import com.orientechnologies.orient.server.config.OServerConfigurationManager;
import com.orientechnologies.orient.server.config.OServerEntryConfiguration;
import com.orientechnologies.orient.server.config.OServerHandlerConfiguration;
import com.orientechnologies.orient.server.config.OServerNetworkListenerConfiguration;
import com.orientechnologies.orient.server.config.OServerNetworkProtocolConfiguration;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.config.OServerSocketFactoryConfiguration;
import com.orientechnologies.orient.server.config.OServerStorageConfiguration;
import com.orientechnologies.orient.server.config.ServerUserConfiguration;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.handler.ConfigurableHooksManager;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.OServerSocketFactory;
import com.orientechnologies.orient.server.network.protocol.NetworkProtocol;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocolData;
import com.orientechnologies.orient.server.network.protocol.http.HttpSessionManager;
import com.orientechnologies.orient.server.network.protocol.http.NetworkProtocolHttpDb;
import com.orientechnologies.orient.server.plugin.OServerPluginInfo;
import com.orientechnologies.orient.server.plugin.ServerPlugin;
import com.orientechnologies.orient.server.plugin.ServerPluginManager;
import com.orientechnologies.orient.server.token.OTokenHandlerImpl;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class OServer {

  private static final String ROOT_PASSWORD_VAR = "YOU_TRACK_DB_ROOT_PASSWORD";
  private static ThreadGroup threadGroup;
  private static final Map<String, OServer> distributedServers =
      new ConcurrentHashMap<String, OServer>();
  private CountDownLatch startupLatch;
  private CountDownLatch shutdownLatch;
  private final boolean shutdownEngineOnExit;
  protected ReentrantLock lock = new ReentrantLock();
  protected volatile boolean running = false;
  protected volatile boolean rejectRequests = true;
  protected OServerConfigurationManager serverCfg;
  protected ContextConfiguration contextConfiguration;
  protected OServerShutdownHook shutdownHook;
  protected Map<String, Class<? extends NetworkProtocol>> networkProtocols =
      new HashMap<String, Class<? extends NetworkProtocol>>();
  protected Map<String, OServerSocketFactory> networkSocketFactories =
      new HashMap<String, OServerSocketFactory>();
  protected List<OServerNetworkListener> networkListeners = new ArrayList<OServerNetworkListener>();
  protected List<OServerLifecycleListener> lifecycleListeners =
      new ArrayList<OServerLifecycleListener>();
  protected ServerPluginManager pluginManager;
  protected ConfigurableHooksManager hookManager;
  protected ODistributedServerManager distributedManager;
  private final Map<String, Object> variables = new HashMap<String, Object>();
  private String serverRootDirectory;
  private String databaseDirectory;
  private OClientConnectionManager clientConnectionManager;
  private HttpSessionManager httpSessionManager;
  private PushManager pushManager;
  private ClassLoader extensionClassLoader;
  private OTokenHandler tokenHandler;
  private YouTrackDB context;
  private YouTrackDBInternal databases;
  protected Date startedOn = new Date();

  public OServer() {
    this(!YouTrackDBManager.instance().isInsideWebContainer());
  }

  public OServer(boolean shutdownEngineOnExit) {
    final boolean insideWebContainer = YouTrackDBManager.instance().isInsideWebContainer();

    if (insideWebContainer && shutdownEngineOnExit) {
      LogManager.instance()
          .warn(
              this,
              "YouTrackDB instance is running inside of web application, it is highly unrecommended"
                  + " to force to shutdown YouTrackDB engine on server shutdown");
    }

    this.shutdownEngineOnExit = shutdownEngineOnExit;

    serverRootDirectory =
        SystemVariableResolver.resolveSystemVariables(
            "${" + YouTrackDBManager.YOU_TRACK_DB_HOME + "}", ".");

    LogManager.instance().installCustomFormatter();

    defaultSettings();

    threadGroup = new ThreadGroup("YouTrackDB Server");

    System.setProperty("com.sun.management.jmxremote", "true");

    YouTrackDBManager.instance().startup();

    if (GlobalConfiguration.PROFILER_ENABLED.getValueAsBoolean()
        && !YouTrackDBManager.instance().getProfiler().isRecording()) {
      YouTrackDBManager.instance().getProfiler().startRecording();
    }

    if (shutdownEngineOnExit) {
      shutdownHook = new OServerShutdownHook(this);
    }
  }

  public static OServer startFromFileConfig(String config)
      throws ClassNotFoundException, InstantiationException, IOException, IllegalAccessException {
    OServer server = new OServer(false);
    server.startup(config);
    server.activate();
    return server;
  }

  public static OServer startFromClasspathConfig(String config)
      throws ClassNotFoundException, InstantiationException, IOException, IllegalAccessException {
    OServer server = new OServer(false);
    server.startup(Thread.currentThread().getContextClassLoader().getResourceAsStream(config));
    server.activate();
    return server;
  }

  public static OServer startFromStreamConfig(InputStream config)
      throws ClassNotFoundException, InstantiationException, IOException, IllegalAccessException {
    OServer server = new OServer(false);
    server.startup(config);
    server.activate();
    return server;
  }

  public static OServer getInstance(final String iServerId) {
    return distributedServers.get(iServerId);
  }

  public static OServer getInstanceByPath(final String iPath) {
    for (Map.Entry<String, OServer> entry : distributedServers.entrySet()) {
      if (iPath.startsWith(entry.getValue().databaseDirectory)) {
        return entry.getValue();
      }
    }
    return null;
  }

  public static void registerServerInstance(final String iServerId, final OServer iServer) {
    distributedServers.put(iServerId, iServer);
  }

  public static void unregisterServerInstance(final String iServerId) {
    distributedServers.remove(iServerId);
  }

  /**
   * Set the preferred {@link ClassLoader} used to load extensions.
   */
  public void setExtensionClassLoader(/* @Nullable */ final ClassLoader extensionClassLoader) {
    this.extensionClassLoader = extensionClassLoader;
  }

  /**
   * Get the preferred {@link ClassLoader} used to load extensions.
   */
  /* @Nullable */
  public ClassLoader getExtensionClassLoader() {
    return extensionClassLoader;
  }

  public SecuritySystem getSecurity() {
    return databases.getSecuritySystem();
  }

  public boolean isActive() {
    return running;
  }

  public OClientConnectionManager getClientConnectionManager() {
    return clientConnectionManager;
  }

  public HttpSessionManager getHttpSessionManager() {
    return httpSessionManager;
  }

  public PushManager getPushManager() {
    return pushManager;
  }

  public void saveConfiguration() throws IOException {
    serverCfg.saveConfiguration();
  }

  public void restart()
      throws ClassNotFoundException,
      InvocationTargetException,
      InstantiationException,
      NoSuchMethodException,
      IllegalAccessException,
      IOException {
    try {
      deinit();
    } finally {
      YouTrackDBManager.instance().startup();
      startup(serverCfg.getConfiguration());
      activate();
    }
  }

  public SystemDatabase getSystemDatabase() {
    return databases.getSystemDatabase();
  }

  public String getServerId() {
    return getSystemDatabase().getServerId();
  }

  /**
   * Load an extension class by name.
   */
  private Class<?> loadClass(final String name) throws ClassNotFoundException {
    Class<?> loaded = tryLoadClass(extensionClassLoader, name);
    if (loaded == null) {
      loaded = tryLoadClass(Thread.currentThread().getContextClassLoader(), name);
      if (loaded == null) {
        loaded = tryLoadClass(getClass().getClassLoader(), name);
        if (loaded == null) {
          loaded = Class.forName(name);
        }
      }
    }
    return loaded;
  }

  /**
   * Attempt to load a class from givenstar class-loader.
   */
  /* @Nullable */
  private Class<?> tryLoadClass(/* @Nullable */ final ClassLoader classLoader, final String name) {
    if (classLoader != null) {
      try {
        return classLoader.loadClass(name);
      } catch (ClassNotFoundException e) {
        // ignore
      }
    }
    return null;
  }

  public OServer startup() throws ConfigurationException {
    String config = OServerConfiguration.DEFAULT_CONFIG_FILE;
    if (System.getProperty(OServerConfiguration.PROPERTY_CONFIG_FILE) != null) {
      config = System.getProperty(OServerConfiguration.PROPERTY_CONFIG_FILE);
    }

    YouTrackDBManager.instance().startup();

    startup(new File(SystemVariableResolver.resolveSystemVariables(config)));

    return this;
  }

  public OServer startup(final File iConfigurationFile) throws ConfigurationException {
    // Startup function split to allow pre-activation changes
    try {
      serverCfg = new OServerConfigurationManager(iConfigurationFile);
      return startupFromConfiguration();

    } catch (IOException e) {
      final String message =
          "Error on reading server configuration from file: " + iConfigurationFile;
      LogManager.instance().error(this, message, e);
      throw BaseException.wrapException(new ConfigurationException(message), e);
    }
  }

  public OServer startup(final String iConfiguration) throws IOException {
    return startup(new ByteArrayInputStream(iConfiguration.getBytes()));
  }

  public OServer startup(final InputStream iInputStream) throws IOException {
    if (iInputStream == null) {
      throw new ConfigurationException("Configuration file is null");
    }

    serverCfg = new OServerConfigurationManager(iInputStream);

    // Startup function split to allow pre-activation changes
    return startupFromConfiguration();
  }

  public OServer startup(final OServerConfiguration iConfiguration)
      throws IllegalArgumentException, SecurityException, IOException {
    serverCfg = new OServerConfigurationManager(iConfiguration);
    return startupFromConfiguration();
  }

  public OServer startupFromConfiguration() throws IOException {
    LogManager.instance()
        .info(this,
            "YouTrackDB Server v" + YouTrackDBConstants.getVersion() + " is starting up...");

    YouTrackDBManager.instance();

    if (startupLatch == null) {
      startupLatch = new CountDownLatch(1);
    }
    if (shutdownLatch == null) {
      shutdownLatch = new CountDownLatch(1);
    }

    initFromConfiguration();

    clientConnectionManager = new OClientConnectionManager(this);
    httpSessionManager = new HttpSessionManager(this);
    pushManager = new PushManager();
    rejectRequests = false;

    if (contextConfiguration.getValueAsBoolean(
        GlobalConfiguration.ENVIRONMENT_DUMP_CFG_AT_STARTUP)) {
      System.out.println("Dumping environment after server startup...");
      GlobalConfiguration.dumpConfiguration(System.out);
    }

    databaseDirectory =
        contextConfiguration.getValue("server.database.path", serverRootDirectory + "/databases/");
    databaseDirectory =
        FileUtils.getPath(SystemVariableResolver.resolveSystemVariables(databaseDirectory));
    databaseDirectory = databaseDirectory.replace("//", "/");

    // CONVERT IT TO ABSOLUTE PATH
    databaseDirectory = (new File(databaseDirectory)).getCanonicalPath();
    databaseDirectory = FileUtils.getPath(databaseDirectory);

    if (!databaseDirectory.endsWith("/")) {
      databaseDirectory += "/";
    }

    YouTrackDBConfigBuilder builder = YouTrackDBConfig.builder();
    for (ServerUserConfiguration user : serverCfg.getUsers()) {
      builder.addGlobalUser(user.getName(), user.getPassword(), user.getResources());
    }
    YouTrackDBConfig config =
        builder
            .fromContext(contextConfiguration)
            .setSecurityConfig(new ServerSecurityConfig(this, this.serverCfg))
            .build();

    if (contextConfiguration.getValueAsBoolean(
        GlobalConfiguration.SERVER_BACKWARD_COMPATIBILITY)) {

      databases =
          DatabaseDocumentTxInternal.getOrCreateEmbeddedFactory(this.databaseDirectory, config);
    } else {
      databases = YouTrackDBInternal.embedded(this.databaseDirectory, config);
    }

    if (databases instanceof OServerAware) {
      ((OServerAware) databases).init(this);
    }

    context = databases.newYouTrackDb();

    LogManager.instance()
        .info(this, "Databases directory: " + new File(databaseDirectory).getAbsolutePath());

    YouTrackDBManager.instance()
        .getProfiler()
        .registerHookValue(
            "system.databases",
            "List of databases configured in Server",
            METRIC_TYPE.TEXT,
            new ProfilerHookValue() {
              @Override
              public Object getValue() {
                final StringBuilder dbs = new StringBuilder(64);
                for (String dbName : getAvailableStorageNames().keySet()) {
                  if (dbs.length() > 0) {
                    dbs.append(',');
                  }
                  dbs.append(dbName);
                }
                return dbs.toString();
              }
            });

    return this;
  }

  @SuppressWarnings("unchecked")
  public OServer activate()
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    lock.lock();
    try {
      // Checks to see if the YouTrackDB System Database exists and creates it if not.
      // Make sure this happens after setSecurity() is called.
      initSystemDatabase();

      for (OServerLifecycleListener l : lifecycleListeners) {
        l.onBeforeActivate();
      }

      final OServerConfiguration configuration = serverCfg.getConfiguration();

      tokenHandler =
          new OTokenHandlerImpl(
              this.databases.getSecuritySystem().getTokenSign(), this.contextConfiguration);

      if (configuration.network != null) {
        // REGISTER/CREATE SOCKET FACTORIES
        if (configuration.network.sockets != null) {
          for (OServerSocketFactoryConfiguration f : configuration.network.sockets) {
            Class<? extends OServerSocketFactory> fClass =
                (Class<? extends OServerSocketFactory>) loadClass(f.implementation);
            OServerSocketFactory factory = fClass.newInstance();
            try {
              factory.config(f.name, f.parameters);
              networkSocketFactories.put(f.name, factory);
            } catch (ConfigurationException e) {
              LogManager.instance().error(this, "Error creating socket factory", e);
            }
          }
        }

        // REGISTER PROTOCOLS
        for (OServerNetworkProtocolConfiguration p : configuration.network.protocols) {
          networkProtocols.put(
              p.name, (Class<? extends NetworkProtocol>) loadClass(p.implementation));
        }

        // STARTUP LISTENERS
        for (OServerNetworkListenerConfiguration l : configuration.network.listeners) {
          networkListeners.add(
              new OServerNetworkListener(
                  this,
                  networkSocketFactories.get(l.socket),
                  l.ipAddress,
                  l.portRange,
                  l.protocol,
                  networkProtocols.get(l.protocol),
                  l.parameters,
                  l.commands));
        }

      } else {
        LogManager.instance().warn(this, "Network configuration was not found");
      }

      try {
        loadStorages();
        loadUsers();
        loadDatabases();
      } catch (IOException e) {
        final String message = "Error on reading server configuration";
        LogManager.instance().error(this, message, e);

        throw BaseException.wrapException(new ConfigurationException(message), e);
      }

      registerPlugins();

      for (OServerLifecycleListener l : lifecycleListeners) {
        l.onAfterActivate();
      }

      running = true;

      String httpAddress = "localhost:2480";
      boolean ssl = false;
      for (OServerNetworkListener listener : networkListeners) {
        if (listener.getProtocolType().getName().equals(NetworkProtocolHttpDb.class.getName())) {
          httpAddress = listener.getListeningAddress(true);
          ssl = listener.getSocketFactory().isEncrypted();
        }
      }
      String proto;
      if (ssl) {
        proto = "https";
      } else {
        proto = "http";
      }

      LogManager.instance()
          .info(
              this,
              "YouTrackDB Studio available at $ANSI{blue %s://%s/studio/index.html}",
              proto,
              httpAddress);
      LogManager.instance()
          .info(
              this,
              "$ANSI{green:italic YouTrackDB Server is active} v" + YouTrackDBConstants.getVersion()
                  + ".");
    } catch (ClassNotFoundException
             | InstantiationException
             | IllegalAccessException
             | RuntimeException e) {
      deinit();
      throw e;
    } finally {
      lock.unlock();
      startupLatch.countDown();
    }
    if (distributedManager != null) {
      try {
        distributedManager.waitUntilNodeOnline();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    return this;
  }

  public void removeShutdownHook() {
    if (shutdownHook != null) {
      shutdownHook.cancel();
      shutdownHook = null;
    }
  }

  public boolean shutdown() {
    try {
      boolean res = deinit();
      return res;
    } finally {
      startupLatch = null;
      if (shutdownLatch != null) {
        shutdownLatch.countDown();
        shutdownLatch = null;
      }

      if (shutdownEngineOnExit) {
        LogManager.instance().shutdown();
      }
    }
  }

  protected boolean deinit() {
    try {
      running = false;

      LogManager.instance().info(this, "YouTrackDB Server is shutting down...");

      if (shutdownHook != null) {
        shutdownHook.cancel();
      }

      YouTrackDBManager.instance().getProfiler().unregisterHookValue("system.databases");

      for (OServerLifecycleListener l : lifecycleListeners) {
        l.onBeforeDeactivate();
      }

      lock.lock();
      try {
        if (networkListeners.size() > 0) {
          // SHUTDOWN LISTENERS
          LogManager.instance().info(this, "Shutting down listeners:");
          // SHUTDOWN LISTENERS
          for (OServerNetworkListener l : networkListeners) {
            LogManager.instance().info(this, "- %s", l);
            try {
              l.shutdown();
            } catch (Exception e) {
              LogManager.instance().error(this, "Error during shutdown of listener %s.", e, l);
            }
          }
        }

        if (networkProtocols.size() > 0) {
          // PROTOCOL SHUTDOWN
          LogManager.instance().info(this, "Shutting down protocols");
          networkProtocols.clear();
        }

        for (OServerLifecycleListener l : lifecycleListeners) {
          try {
            l.onAfterDeactivate();
          } catch (Exception e) {
            LogManager.instance()
                .error(this, "Error during deactivation of server lifecycle listener %s", e, l);
          }
        }

        rejectRequests = true;
        pushManager.shutdown();
        clientConnectionManager.shutdown();
        httpSessionManager.shutdown();

        if (pluginManager != null) {
          pluginManager.shutdown();
        }

        networkListeners.clear();
      } finally {
        lock.unlock();
      }

      if (shutdownEngineOnExit && !YouTrackDBManager.isRegisterDatabaseByPath()) {
        try {
          LogManager.instance().info(this, "Shutting down databases:");
          YouTrackDBManager.instance().shutdown();
        } catch (Exception e) {
          LogManager.instance().error(this, "Error during YouTrackDB shutdown", e);
        }
      }
      if (!contextConfiguration.getValueAsBoolean(
          GlobalConfiguration.SERVER_BACKWARD_COMPATIBILITY)
          && databases != null) {
        databases.close();
        databases = null;
      }
    } finally {
      LogManager.instance().info(this, "YouTrackDB Server shutdown complete\n");
      LogManager.instance().flush();
    }

    return true;
  }

  public boolean rejectRequests() {
    return rejectRequests;
  }

  public void waitForShutdown() {
    try {
      if (shutdownLatch != null) {
        shutdownLatch.await();
      }
    } catch (InterruptedException e) {
      LogManager.instance().error(this, "Error during waiting for YouTrackDB shutdown", e);
    }
  }

  public Map<String, String> getAvailableStorageNames() {
    Set<String> dbs = listDatabases();
    Map<String, String> toSend = new HashMap<String, String>();
    for (String dbName : dbs) {
      toSend.put(dbName, dbName);
    }

    return toSend;
  }

  /**
   * Opens all the available server's databases.
   */
  protected void loadDatabases() {
    if (!contextConfiguration.getValueAsBoolean(
        GlobalConfiguration.SERVER_OPEN_ALL_DATABASES_AT_STARTUP)) {
      return;
    }
    databases.loadAllDatabases();
  }

  private boolean askForEncryptionKey(final String iDatabaseName) {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
    }

    System.out.println();
    System.out.println();
    System.out.println(
        AnsiCode.format(
            "$ANSI{yellow"
                + " +--------------------------------------------------------------------------+}"));
    System.out.println(
        AnsiCode.format(
            String.format(
                "$ANSI{yellow | INSERT THE KEY FOR THE ENCRYPTED DATABASE %-31s|}",
                "'" + iDatabaseName + "'")));
    System.out.println(
        AnsiCode.format(
            "$ANSI{yellow"
                + " +--------------------------------------------------------------------------+}"));
    System.out.println(
        AnsiCode.format(
            "$ANSI{yellow | To avoid this message set the environment variable or JVM setting      "
                + "  |}"));
    System.out.println(
        AnsiCode.format(
            "$ANSI{yellow | 'storage.encryptionKey' to the key to use.                             "
                + "  |}"));
    System.out.println(
        AnsiCode.format(
            "$ANSI{yellow"
                + " +--------------------------------------------------------------------------+}"));
    System.out.print(
        AnsiCode.format("\n$ANSI{yellow Database encryption key [BLANK=to skip opening]: }"));

    final ConsoleReader reader = new DefaultConsoleReader();
    try {
      String key = reader.readPassword();
      if (key != null) {
        key = key.trim();
        if (!key.isEmpty()) {
          GlobalConfiguration.STORAGE_ENCRYPTION_KEY.setValue(key);
          return true;
        }
      }
    } catch (IOException e) {
    }
    return false;
  }

  public String getDatabaseDirectory() {
    return databaseDirectory;
  }

  public ThreadGroup getServerThreadGroup() {
    return threadGroup;
  }

  /**
   * Authenticate a server user.
   *
   * @param iUserName Username to authenticate
   * @param iPassword Password in clear
   * @return true if authentication is ok, otherwise false
   */
  public boolean authenticate(
      final String iUserName, final String iPassword, final String iResourceToCheck) {
    // FALSE INDICATES WRONG PASSWORD OR NO AUTHORIZATION
    return authenticateUser(iUserName, iPassword, iResourceToCheck) != null;
  }

  // Returns null if the user cannot be authenticated. Otherwise returns the
  // ServerUserConfiguration user.
  public SecurityUser authenticateUser(
      final String iUserName, final String iPassword, final String iResourceToCheck) {
    return databases
        .getSecuritySystem()
        .authenticateAndAuthorize(null, iUserName, iPassword, iResourceToCheck);
  }

  public boolean existsStoragePath(final String iURL) {
    return serverCfg.getConfiguration().getStoragePath(iURL) != null;
  }

  public OServerConfiguration getConfiguration() {
    return serverCfg.getConfiguration();
  }

  public Map<String, Class<? extends NetworkProtocol>> getNetworkProtocols() {
    return networkProtocols;
  }

  public List<OServerNetworkListener> getNetworkListeners() {
    return networkListeners;
  }

  @SuppressWarnings("unchecked")
  public <RET extends OServerNetworkListener> RET getListenerByProtocol(
      final Class<? extends NetworkProtocol> iProtocolClass) {
    for (OServerNetworkListener l : networkListeners) {
      if (iProtocolClass.isAssignableFrom(l.getProtocolType())) {
        return (RET) l;
      }
    }

    return null;
  }

  public Collection<OServerPluginInfo> getPlugins() {
    return pluginManager != null ? pluginManager.getPlugins() : null;
  }

  public ContextConfiguration getContextConfiguration() {
    return contextConfiguration;
  }

  @SuppressWarnings("unchecked")
  public <RET extends ServerPlugin> RET getPluginByClass(final Class<RET> iPluginClass) {
    if (startupLatch == null) {
      throw new DatabaseException("Error on plugin lookup: the server did not start correctly");
    }

    try {
      startupLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!running) {
      throw new DatabaseException("Error on plugin lookup the server did not start correctly.");
    }

    for (OServerPluginInfo h : getPlugins()) {
      if (h.getInstance() != null && h.getInstance().getClass().equals(iPluginClass)) {
        return (RET) h.getInstance();
      }
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  public <RET extends ServerPlugin> RET getPlugin(final String iName) {
    if (startupLatch == null) {
      throw new DatabaseException("Error on plugin lookup: the server did not start correctly");
    }

    try {
      startupLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!running) {
      throw new DatabaseException("Error on plugin lookup: the server did not start correctly");
    }

    final OServerPluginInfo p = pluginManager.getPluginByName(iName);
    if (p != null) {
      return (RET) p.getInstance();
    }
    return null;
  }

  public Object getVariable(final String iName) {
    return variables.get(iName);
  }

  public OServer setVariable(final String iName, final Object iValue) {
    if (iValue == null) {
      variables.remove(iName);
    } else {
      variables.put(iName, iValue);
    }
    return this;
  }

  public void addTemporaryUser(
      final String iName, final String iPassword, final String iPermissions) {
    databases.getSecuritySystem().addTemporaryUser(iName, iPassword, iPermissions);
  }

  public OServer registerLifecycleListener(final OServerLifecycleListener iListener) {
    lifecycleListeners.add(iListener);
    return this;
  }

  public OServer unregisterLifecycleListener(final OServerLifecycleListener iListener) {
    lifecycleListeners.remove(iListener);
    return this;
  }

  public DatabaseSessionInternal openDatabase(final String iDbUrl, final ParsedToken iToken) {
    return databases.open(new TokenAuthInfo(iToken), YouTrackDBConfig.defaultConfig());
  }

  public DatabaseSessionInternal openDatabase(
      final String iDbUrl, final String user, final String password) {
    return openDatabase(iDbUrl, user, password, null);
  }

  public DatabaseSessionInternal openDatabase(
      final String iDbUrl, final String user, final String password, ONetworkProtocolData data) {
    final DatabaseSessionInternal database;
    boolean serverAuth = false;
    database = databases.open(iDbUrl, user, password);
    if (SecurityUser.SERVER_USER_TYPE.equals(database.getUser().getUserType())) {
      serverAuth = true;
    }
    if (serverAuth && data != null) {
      data.serverUser = true;
      data.serverUsername = user;
    } else if (data != null) {
      data.serverUser = false;
      data.serverUsername = null;
    }
    return database;
  }

  public DatabaseSessionInternal openDatabase(String database) {
    return databases.openNoAuthorization(database);
  }

  public ODistributedServerManager getDistributedManager() {
    return distributedManager;
  }

  public void setServerRootDirectory(final String rootDirectory) {
    this.serverRootDirectory = rootDirectory;
  }

  protected void initFromConfiguration() {
    final OServerConfiguration cfg = serverCfg.getConfiguration();

    // FILL THE CONTEXT CONFIGURATION WITH SERVER'S PARAMETERS
    contextConfiguration = new ContextConfiguration();

    if (cfg.properties != null) {
      for (OServerEntryConfiguration prop : cfg.properties) {
        contextConfiguration.setValue(prop.name, prop.value);
      }
    }

    hookManager = new ConfigurableHooksManager(cfg);
  }

  public ConfigurableHooksManager getHookManager() {
    return hookManager;
  }

  protected void loadUsers() throws IOException {
    final OServerConfiguration configuration = serverCfg.getConfiguration();

    if (configuration.isAfterFirstTime) {
      return;
    }

    configuration.isAfterFirstTime = true;

    createDefaultServerUsers();
  }

  /**
   * Load configured storages.
   */
  protected void loadStorages() {
    final OServerConfiguration configuration = serverCfg.getConfiguration();

    if (configuration.storages == null) {
      return;
    }
    for (OServerStorageConfiguration stg : configuration.storages) {
      if (stg.loadOnStartup) {
        String url = stg.path;
        if (url.endsWith("/")) {
          url = url.substring(0, url.length() - 1);
        }
        url = url.replace('\\', '/');

        int typeIndex = url.indexOf(':');
        if (typeIndex <= 0) {
          throw new ConfigurationException(
              "Error in database URL: the engine was not specified. Syntax is: "
                  + YouTrackDBManager.URL_SYNTAX
                  + ". URL was: "
                  + url);
        }

        String remoteUrl = url.substring(typeIndex + 1);
        int index = remoteUrl.lastIndexOf('/');
        String baseUrl;
        if (index > 0) {
          baseUrl = remoteUrl.substring(0, index);
        } else {
          baseUrl = "./";
        }
        databases.initCustomStorage(stg.name, baseUrl, stg.userName, stg.userPassword);
      }
    }
  }

  protected void createDefaultServerUsers() throws IOException {

    if (databases.getSecuritySystem() != null
        && !databases.getSecuritySystem().arePasswordsStored()) {
      return;
    }

    // YOU_TRACK_DB_ROOT_PASSWORD ENV OR JVM SETTING
    String rootPassword = SystemVariableResolver.resolveVariable(ROOT_PASSWORD_VAR);

    if (rootPassword != null) {
      rootPassword = rootPassword.trim();
      if (rootPassword.isEmpty()) {
        rootPassword = null;
      }
    }
    boolean existsRoot =
        existsSystemUser(OServerConfiguration.DEFAULT_ROOT_USER)
            || serverCfg.existsUser(OServerConfiguration.DEFAULT_ROOT_USER);

    if (rootPassword == null && !existsRoot) {
      try {
        // WAIT ANY LOG IS PRINTED
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }

      System.out.println();
      System.out.println();
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow +---------------------------------------------------------------+}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow |                WARNING: FIRST RUN CONFIGURATION               |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow +---------------------------------------------------------------+}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow | This is the first time the server is running. Please type a   |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow | password of your choice for the 'root' user or leave it blank |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow | to auto-generate it.                                          |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow |                                                               |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow | To avoid this message set the environment variable or JVM     |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow | setting YOU_TRACK_DB_ROOT_PASSWORD to the root password to use.   |}"));
      System.out.println(
          AnsiCode.format(
              "$ANSI{yellow +---------------------------------------------------------------+}"));

      final ConsoleReader console = new DefaultConsoleReader();

      // ASK FOR PASSWORD + CONFIRM
      do {
        System.out.print(
            AnsiCode.format("\n$ANSI{yellow Root password [BLANK=auto generate it]: }"));
        rootPassword = console.readPassword();

        if (rootPassword != null) {
          rootPassword = rootPassword.trim();
          if (rootPassword.isEmpty()) {
            rootPassword = null;
          }
        }

        if (rootPassword != null) {
          System.out.print(AnsiCode.format("$ANSI{yellow Please confirm the root password: }"));

          String rootConfirmPassword = console.readPassword();
          if (rootConfirmPassword != null) {
            rootConfirmPassword = rootConfirmPassword.trim();
            if (rootConfirmPassword.isEmpty()) {
              rootConfirmPassword = null;
            }
          }

          if (!rootPassword.equals(rootConfirmPassword)) {
            System.out.println(
                AnsiCode.format(
                    "$ANSI{red ERROR: Passwords don't match, please reinsert both of them, or press"
                        + " ENTER to auto generate it}"));
          } else
          // PASSWORDS MATCH

          {
            try {
              if (getSecurity() != null) {
                getSecurity().validatePassword("root", rootPassword);
              }
              // PASSWORD IS STRONG ENOUGH
              break;
            } catch (InvalidPasswordException ex) {
              System.out.println(
                  AnsiCode.format(
                      "$ANSI{red ERROR: Root password does not match the password policies}"));
              if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
              }
            }
          }
        }

      } while (rootPassword != null);

    } else {
      LogManager.instance()
          .info(
              this,
              "Found YOU_TRACK_DB_ROOT_PASSWORD variable, using this value as root's password",
              rootPassword);
    }

    if (!existsRoot) {
      context.execute(
          "CREATE SYSTEM USER "
              + OServerConfiguration.DEFAULT_ROOT_USER
              + " IDENTIFIED BY ? ROLE root",
          rootPassword);
    }

    if (!existsSystemUser(OServerConfiguration.GUEST_USER)) {
      context.execute(
          "CREATE SYSTEM USER " + OServerConfiguration.GUEST_USER + " IDENTIFIED BY ? ROLE guest",
          OServerConfiguration.DEFAULT_GUEST_PASSWORD);
    }
  }

  private boolean existsSystemUser(String user) {
    return Boolean.TRUE.equals(
        context.execute("EXISTS SYSTEM USER ?", user).next().getProperty("exists"));
  }

  public ServerPluginManager getPluginManager() {
    return pluginManager;
  }

  protected void registerPlugins()
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    pluginManager = new ServerPluginManager();
    pluginManager.config(this);
    pluginManager.startup();

    // PLUGINS CONFIGURED IN XML
    final OServerConfiguration configuration = serverCfg.getConfiguration();

    if (configuration.handlers != null) {
      // ACTIVATE PLUGINS
      final List<ServerPlugin> plugins = new ArrayList<ServerPlugin>();

      for (OServerHandlerConfiguration h : configuration.handlers) {
        if (h.parameters != null) {
          // CHECK IF IT'S ENABLED
          boolean enabled = true;

          for (OServerParameterConfiguration p : h.parameters) {
            if (p.name.equals("enabled")) {
              enabled = false;

              String value = SystemVariableResolver.resolveSystemVariables(p.value);
              if (value != null) {
                value = value.trim();

                if ("true".equalsIgnoreCase(value)) {
                  enabled = true;
                  break;
                }
              }
            }
          }

          if (!enabled)
          // SKIP IT
          {
            continue;
          }
        }

        final ServerPlugin plugin = (ServerPlugin) loadClass(h.clazz).newInstance();

        if (plugin instanceof ODistributedServerManager) {
          distributedManager = (ODistributedServerManager) plugin;
        }

        pluginManager.registerPlugin(
            new OServerPluginInfo(plugin.getName(), null, null, null, plugin, null, 0, null));

        pluginManager.callListenerBeforeConfig(plugin, h.parameters);
        plugin.config(this, h.parameters);
        pluginManager.callListenerAfterConfig(plugin, h.parameters);

        plugins.add(plugin);
      }

      // START ALL THE CONFIGURED PLUGINS
      for (ServerPlugin plugin : plugins) {
        pluginManager.callListenerBeforeStartup(plugin);
        plugin.startup();
        pluginManager.callListenerAfterStartup(plugin);
      }
    }
  }

  protected void defaultSettings() {
  }

  public OTokenHandler getTokenHandler() {
    return tokenHandler;
  }

  public ThreadGroup getThreadGroup() {
    return YouTrackDBManager.instance().getThreadGroup();
  }

  private void initSystemDatabase() {
    databases.getSystemDatabase().init();
  }

  public YouTrackDBInternal getDatabases() {
    return databases;
  }

  public YouTrackDB getContext() {
    return context;
  }

  public void dropDatabase(String databaseName) {
    if (databases.exists(databaseName, null, null)) {
      databases.drop(databaseName, null, null);
    } else {
      throw new StorageException("Database with name '" + databaseName + "' does not exist");
    }
  }

  public boolean existsDatabase(String databaseName) {
    return databases.exists(databaseName, null, null);
  }

  public void createDatabase(String databaseName, DatabaseType type, YouTrackDBConfig config) {
    databases.create(databaseName, null, null, type, config);
  }

  public Set<String> listDatabases() {
    Set<String> dbs = databases.listDatabases(null, null);
    dbs.remove(SystemDatabase.SYSTEM_DB_NAME);
    return dbs;
  }

  public void restore(String name, String path) {
    databases.restore(name, null, null, null, path, YouTrackDBConfig.defaultConfig());
  }

  public Date getStartedOn() {
    return startedOn;
  }
}
