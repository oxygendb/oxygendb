package com.orientechnologies.orient.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Oxygen;
import com.orientechnologies.orient.server.config.OServerConfiguration;
import com.orientechnologies.orient.server.config.OServerHandlerConfiguration;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class OServerTest {

  private String prevPassword;
  private String prevOrientHome;
  private boolean allowJvmShutdownPrev;
  private OServer server;
  private OServerConfiguration conf;

  @Before
  public void setUp() throws Exception {
    OLogManager.instance().setConsoleLevel(Level.OFF.getName());
    prevPassword = System.setProperty("OXYGENDB_ROOT_PASSWORD", "rootPassword");
    prevOrientHome = System.setProperty("OXYGENDB_HOME", "./target/testhome");

    conf = new OServerConfiguration();

    conf.handlers = new ArrayList<OServerHandlerConfiguration>();
    OServerHandlerConfiguration handlerConfiguration = new OServerHandlerConfiguration();
    handlerConfiguration.clazz = OServerFailingOnStarupPluginStub.class.getName();
    handlerConfiguration.parameters = new OServerParameterConfiguration[0];

    conf.handlers.add(0, handlerConfiguration);
  }

  @After
  public void tearDown() throws Exception {
    if (server.isActive()) {
      server.shutdown();
    }

    Oxygen.instance().shutdown();
    OFileUtils.deleteRecursively(new File("./target/testhome"));

    if (prevOrientHome != null) {
      System.setProperty("OXYGENDB_HOME", prevOrientHome);
    }
    if (prevPassword != null) {
      System.setProperty("OXYGENDB_ROOT_PASSWORD", prevPassword);
    }

    Oxygen.instance().startup();
  }

  @Test
  public void shouldShutdownOnPluginStartupException() {

    try {
      server = new OServer(false);
      server.startup(conf);
      server.activate();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(OException.class);
    }

    assertThat(server.isActive()).isFalse();
    server.shutdown();
  }
}
