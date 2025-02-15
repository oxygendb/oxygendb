package com.jetbrains.youtrack.db.internal.server;

import com.jetbrains.youtrack.db.api.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.common.parser.SystemVariableResolver;
import com.jetbrains.youtrack.db.internal.core.security.SecurityConfig;
import com.jetbrains.youtrack.db.internal.core.security.Syslog;
import com.jetbrains.youtrack.db.internal.server.config.ServerConfigurationManager;
import com.jetbrains.youtrack.db.internal.server.plugin.ServerPluginInfo;

public class ServerSecurityConfig implements SecurityConfig {

  private final YouTrackDBServer server;
  private final ServerConfigurationManager serverCfg;
  private Syslog sysLog;

  public ServerSecurityConfig(YouTrackDBServer server, ServerConfigurationManager serverCfg) {
    super();
    this.server = server;
    this.serverCfg = serverCfg;
  }

  @Override
  public Syslog getSyslog() {
    if (sysLog == null && server != null) {
      if (server.getPluginManager() != null) {
        ServerPluginInfo syslogPlugin = server.getPluginManager().getPluginByName("syslog");
        if (syslogPlugin != null) {
          sysLog = (Syslog) syslogPlugin.getInstance();
        }
      }
    }
    return sysLog;
  }

  @Override
  public String getConfigurationFile() {
    // Default
    String configFile =
        SystemVariableResolver.resolveSystemVariables("${YOUTRACKDB_HOME}/config/security.json");

    String ssf =
        server
            .getContextConfiguration()
            .getValueAsString(GlobalConfiguration.SERVER_SECURITY_FILE);
    if (ssf != null) {
      configFile = ssf;
    }
    return configFile;
  }
}
