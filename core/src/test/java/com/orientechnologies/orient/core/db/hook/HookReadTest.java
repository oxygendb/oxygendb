package com.orientechnologies.orient.core.db.hook;

import static org.junit.Assert.assertEquals;

import com.orientechnologies.DBTestBase;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.metadata.security.OSecurityPolicy;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import org.junit.Test;

public class HookReadTest extends DBTestBase {

  @Test
  public void testSelectChangedInHook() {
    db.registerHook(
        new ORecordHook() {
          @Override
          public void onUnregister() {
          }

          @Override
          public RESULT onTrigger(TYPE iType, ORecord iRecord) {
            if (iType == TYPE.AFTER_READ
                && !((ODocument) iRecord)
                .getClassName()
                .equalsIgnoreCase(OSecurityPolicy.class.getSimpleName())) {
              ((ODocument) iRecord).field("read", "test");
            }
            return RESULT.RECORD_CHANGED;
          }

          @Override
          public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
            return null;
          }
        });

    db.getMetadata().getSchema().createClass("TestClass");
    db.begin();
    db.save(new ODocument("TestClass"));
    db.commit();

    db.begin();
    OResultSet res = db.query("select from TestClass");
    assertEquals(res.next().getProperty("read"), "test");
    db.commit();
  }
}
