package com.jetbrains.youtrack.db.internal.core.sql.executor;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class OExplainStatementExecutionTest extends DBTestBase {

  @Test
  public void testExplainSelectNoTarget() {
    YTResultSet result = db.query("explain select 1 as one, 2 as two, 2+3");
    Assert.assertTrue(result.hasNext());
    YTResult next = result.next();
    Assert.assertNotNull(next.getProperty("executionPlan"));
    Assert.assertNotNull(next.getProperty("executionPlanAsString"));

    Optional<OExecutionPlan> plan = result.getExecutionPlan();
    Assert.assertTrue(plan.isPresent());
    Assert.assertTrue(plan.get() instanceof OSelectExecutionPlan);

    result.close();
  }
}