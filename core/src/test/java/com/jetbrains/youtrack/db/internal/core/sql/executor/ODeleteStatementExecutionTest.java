package com.jetbrains.youtrack.db.internal.core.sql.executor;

import static com.jetbrains.youtrack.db.internal.core.sql.executor.ExecutionPlanPrintUtils.printExecutionPlan;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ODeleteStatementExecutionTest extends DBTestBase {

  @Test
  public void testSimple() {
    String className = "testSimple";
    db.getMetadata().getSchema().createClass(className);
    for (int i = 0; i < 10; i++) {
      db.begin();
      EntityImpl doc = db.newInstance(className);
      doc.setProperty("name", "name" + i);
      doc.save();
      db.commit();
    }

    db.begin();
    YTResultSet result = db.command("delete from  " + className + " where name = 'name4'");
    printExecutionPlan(result);
    for (int i = 0; i < 1; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertEquals((Object) 1L, item.getProperty("count"));
    }
    Assert.assertFalse(result.hasNext());
    db.commit();

    result = db.query("select from " + className);
    for (int i = 0; i < 9; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertNotEquals("name4", item.getProperty("name"));
    }
    Assert.assertFalse(result.hasNext());
    result.close();
  }

  @Test
  public void testUnsafe1() {
    String className = "testUnsafe1";
    YTClass v = db.getMetadata().getSchema().getClass("V");
    if (v == null) {
      db.getMetadata().getSchema().createClass("V");
    }
    db.getMetadata().getSchema().createClass(className, v);
    for (int i = 0; i < 10; i++) {
      db.begin();
      EntityImpl doc = db.newInstance(className);
      doc.setProperty("name", "name" + i);
      doc.save();
      db.commit();
    }
    try {
      YTResultSet result = db.command("delete from  " + className + " where name = 'name4'");
      Assert.fail();
    } catch (YTCommandExecutionException ex) {

    } catch (Exception e) {
      Assert.fail();
    }
  }

  @Test
  public void testUnsafe2() {
    String className = "testUnsafe2";
    YTClass v = db.getMetadata().getSchema().getClass("V");
    if (v == null) {
      db.getMetadata().getSchema().createClass("V");
    }
    db.getMetadata().getSchema().createClass(className, v);
    for (int i = 0; i < 10; i++) {
      db.begin();
      EntityImpl doc = db.newInstance(className);
      doc.setProperty("name", "name" + i);
      doc.save();
      db.commit();
    }

    db.begin();
    YTResultSet result = db.command("delete from  " + className + " where name = 'name4' unsafe");

    printExecutionPlan(result);
    for (int i = 0; i < 1; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertEquals((Object) 1L, item.getProperty("count"));
    }
    Assert.assertFalse(result.hasNext());
    db.commit();

    result = db.query("select from " + className);
    for (int i = 0; i < 9; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertNotEquals("name4", item.getProperty("name"));
    }
    Assert.assertFalse(result.hasNext());
    result.close();
  }

  @Test
  public void testReturnBefore() {
    String className = "testReturnBefore";
    db.getMetadata().getSchema().createClass(className);
    YTRID fourthId = null;

    for (int i = 0; i < 10; i++) {
      db.begin();
      EntityImpl doc = db.newInstance(className);
      doc.setProperty("name", "name" + i);
      if (i == 4) {
        fourthId = doc.getIdentity();
      }

      doc.save();
      db.commit();
    }

    db.begin();
    YTResultSet result =
        db.command("delete from  " + className + " return before where name = 'name4' ");
    printExecutionPlan(result);
    for (int i = 0; i < 1; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertEquals(fourthId, item.getRecordId());
    }
    Assert.assertFalse(result.hasNext());
    db.commit();

    result = db.query("select from " + className);
    for (int i = 0; i < 9; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertNotEquals("name4", item.getProperty("name"));
    }
    Assert.assertFalse(result.hasNext());
    result.close();
  }

  @Test
  public void testLimit() {
    String className = "testLimit";
    db.getMetadata().getSchema().createClass(className);
    for (int i = 0; i < 10; i++) {
      db.begin();
      EntityImpl doc = db.newInstance(className);
      doc.setProperty("name", "name" + i);
      doc.save();
      db.commit();
    }
    db.begin();
    YTResultSet result = db.command("delete from  " + className + " limit 5");
    printExecutionPlan(result);
    for (int i = 0; i < 1; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
      Assert.assertEquals((Object) 5L, item.getProperty("count"));
    }
    Assert.assertFalse(result.hasNext());
    db.commit();

    result = db.query("select from " + className);
    for (int i = 0; i < 5; i++) {
      Assert.assertTrue(result.hasNext());
      YTResult item = result.next();
      Assert.assertNotNull(item);
    }
    Assert.assertFalse(result.hasNext());
    result.close();
  }
}