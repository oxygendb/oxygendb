package com.orientechnologies.orient.core.sql.executor;

import static com.orientechnologies.orient.core.sql.executor.ExecutionPlanPrintUtils.printExecutionPlan;

import com.orientechnologies.DBTestBase;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class OBeginStatementExecutionTest extends DBTestBase {

  @Test
  public void testBegin() {
    Assert.assertTrue(db.getTransaction() == null || !db.getTransaction().isActive());
    OResultSet result = db.command("begin");
    printExecutionPlan(null, result);
    Assert.assertNotNull(result);
    Assert.assertTrue(result.hasNext());
    OResult item = result.next();
    Assert.assertEquals("begin", item.getProperty("operation"));
    Assert.assertFalse(result.hasNext());
    Assert.assertFalse(db.getTransaction() == null || !db.getTransaction().isActive());
    db.commit();
  }
}
