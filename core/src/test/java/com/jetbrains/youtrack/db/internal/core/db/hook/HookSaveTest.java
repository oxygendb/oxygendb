package com.jetbrains.youtrack.db.internal.core.db.hook;

import static org.junit.Assert.assertNotNull;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.core.hook.YTRecordHook;
import com.jetbrains.youtrack.db.internal.core.record.Record;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import org.junit.Test;

/**
 *
 */
public class HookSaveTest extends DBTestBase {

  @Test
  public void testCreatedLinkedInHook() {
    db.registerHook(
        new YTRecordHook() {
          @Override
          public void onUnregister() {
          }

          @Override
          public RESULT onTrigger(TYPE iType, Record iRecord) {
            if (iType != TYPE.BEFORE_CREATE) {
              return RESULT.RECORD_NOT_CHANGED;
            }
            EntityImpl doc = (EntityImpl) iRecord;
            if (doc.containsField("test")) {
              return RESULT.RECORD_NOT_CHANGED;
            }
            EntityImpl doc1 = new EntityImpl("test");
            doc1.field("test", "value");
            doc.field("testNewLinkedRecord", doc1);
            return RESULT.RECORD_CHANGED;
          }

          @Override
          public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
            return null;
          }
        });

    db.getMetadata().getSchema().createClass("test");
    db.begin();
    EntityImpl doc = db.save(new EntityImpl("test"));
    db.commit();

    EntityImpl newRef = db.bindToSession(doc).field("testNewLinkedRecord");
    assertNotNull(newRef);
    assertNotNull(newRef.getIdentity().isPersistent());
  }

  @Test
  public void testCreatedBackLinkedInHook() {
    db.registerHook(
        new YTRecordHook() {
          @Override
          public void onUnregister() {
          }

          @Override
          public RESULT onTrigger(TYPE iType, Record iRecord) {
            if (iType != TYPE.BEFORE_CREATE) {
              return RESULT.RECORD_NOT_CHANGED;
            }
            EntityImpl doc = (EntityImpl) iRecord;
            if (doc.containsField("test")) {
              return RESULT.RECORD_NOT_CHANGED;
            }
            EntityImpl doc1 = new EntityImpl("test");
            doc1.field("test", "value");
            doc.field("testNewLinkedRecord", doc1);
            doc1.field("backLink", doc);
            return RESULT.RECORD_CHANGED;
          }

          @Override
          public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
            return null;
          }
        });

    db.getMetadata().getSchema().createClass("test");
    db.begin();
    EntityImpl doc = db.save(new EntityImpl("test"));
    db.commit();

    EntityImpl newRef = db.bindToSession(doc).field("testNewLinkedRecord");
    assertNotNull(newRef);
    assertNotNull(newRef.getIdentity().isPersistent());
  }
}