package com.jetbrains.youtrack.db.internal.core.metadata.schema;

import static org.junit.Assert.assertEquals;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import org.junit.Test;

public class CreateClassMultipleClusterTest extends DBTestBase {

  @Test
  public void testCreateClassSQL() {
    db.command("drop class V").close();
    db.command("create class V clusters 16").close();
    db.command("create class X extends V clusters 32").close();

    final YTClass clazzV = db.getMetadata().getSchema().getClass("V");
    assertEquals(16, clazzV.getClusterIds().length);

    final YTClass clazzX = db.getMetadata().getSchema().getClass("X");
    assertEquals(32, clazzX.getClusterIds().length);
  }

  @Test
  public void testCreateClassSQLSpecifiedClusters() {
    int s = db.addCluster("second");
    int t = db.addCluster("third");
    db.command("drop class V").close();
    db.command("create class V cluster " + s + "," + t).close();

    final YTClass clazzV = db.getMetadata().getSchema().getClass("V");
    assertEquals(2, clazzV.getClusterIds().length);

    assertEquals(s, clazzV.getClusterIds()[0]);
    assertEquals(t, clazzV.getClusterIds()[1]);
  }
}