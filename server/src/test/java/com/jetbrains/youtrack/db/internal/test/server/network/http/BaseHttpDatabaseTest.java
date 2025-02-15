package com.jetbrains.youtrack.db.internal.test.server.network.http;

import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * Test HTTP "query" command.
 */
public abstract class BaseHttpDatabaseTest extends BaseHttpTest {

  @Before
  public void createDatabase() throws Exception {
    serverDirectory =
        Paths.get(System.getProperty("buildDirectory", "target"))
            .resolve(this.getClass().getSimpleName() + "Server")
            .toFile()
            .getCanonicalPath();

    super.startServer();
    EntityImpl pass = new EntityImpl();
    pass.setProperty("adminPassword", "admin");
    Assert.assertEquals(
        post("database/" + getDatabaseName() + "/memory")
            .payload(pass.toJSON(), CONTENT.JSON)
            .setUserName("root")
            .setUserPassword("root")
            .getResponse()
            .getCode(),
        200);

    onAfterDatabaseCreated();
  }

  @After
  public void dropDatabase() throws Exception {
    Assert.assertEquals(
        delete("database/" + getDatabaseName())
            .setUserName("root")
            .setUserPassword("root")
            .getResponse()
            .getCode(),
        204);
    super.stopServer();

    onAfterDatabaseDropped();
  }

  protected void onAfterDatabaseCreated() throws Exception {
  }

  protected void onAfterDatabaseDropped() throws Exception {
  }
}
