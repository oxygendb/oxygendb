package com.jetbrains.youtrack.db.internal.core.sql;

import static org.junit.Assert.assertEquals;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResultSet;
import java.math.BigDecimal;
import org.junit.Test;

public class BigDecimalQuerySupportTest extends DBTestBase {

  @Test
  public void testDecimalPrecision() {
    db.command("CREATE Class Test").close();
    db.command("CREATE Property Test.salary DECIMAL").close();
    db.begin();
    db.command(
            "INSERT INTO Test set salary = ?", new BigDecimal("179999999999.99999999999999999999"))
        .close();
    db.commit();
    try (YTResultSet result = db.query("SELECT * FROM Test")) {
      BigDecimal salary = result.next().getProperty("salary");
      assertEquals(new BigDecimal("179999999999.99999999999999999999"), salary);
    }
  }
}