/*
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.test.database.auto;

import com.orientechnologies.core.command.OCommandExecutor;
import com.orientechnologies.core.command.OCommandRequestText;
import com.orientechnologies.core.db.YTDatabaseListener;
import com.orientechnologies.core.db.YTDatabaseSession;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.exception.YTConcurrentModificationException;
import com.orientechnologies.core.exception.YTTransactionException;
import com.orientechnologies.core.metadata.schema.YTClass;
import com.orientechnologies.core.metadata.schema.YTType;
import com.orientechnologies.core.record.ORecordInternal;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.sql.OCommandSQL;
import com.orientechnologies.core.storage.YTRecordDuplicatedException;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

@Test
public class TransactionAtomicTest extends DocumentDBBaseTest {

  @Parameters(value = "remote")
  public TransactionAtomicTest(@Optional Boolean remote) {
    super(remote != null && remote);
  }

  @Test
  public void testTransactionAtomic() {
    YTDatabaseSessionInternal db1 = acquireSession();
    YTDatabaseSessionInternal db2 = acquireSession();

    YTEntityImpl record1 = new YTEntityImpl();

    db2.begin();
    record1
        .field("value", "This is the first version")
        .save(db2.getClusterNameById(db2.getDefaultClusterId()));
    db2.commit();

    // RE-READ THE RECORD
    db2.activateOnCurrentThread();
    db2.begin();
    YTEntityImpl record2 = db2.load(record1.getIdentity());

    record2.field("value", "This is the second version").save();
    db2.commit();

    db2.begin();
    record2 = db2.bindToSession(record2);
    record2.field("value", "This is the third version").save();
    db2.commit();

    db1.activateOnCurrentThread();
    record1 = db1.bindToSession(record1);
    Assert.assertEquals(record1.field("value"), "This is the third version");
    db1.close();

    db2.activateOnCurrentThread();
    db2.close();

    database.activateOnCurrentThread();
  }

  @Test
  public void testMVCC() throws IOException {

    YTEntityImpl doc = new YTEntityImpl("Account");
    database.begin();
    doc.field("version", 0);
    doc.save();
    database.commit();

    database.begin();
    doc = database.bindToSession(doc);
    doc.setDirty();
    doc.field("testmvcc", true);
    ORecordInternal.setVersion(doc, doc.getVersion() + 1);
    try {
      doc.save();
      database.commit();
      Assert.fail();
    } catch (YTConcurrentModificationException e) {
      Assert.assertTrue(true);
    }
  }

  @Test
  public void testTransactionPreListenerRollback() throws IOException {
    YTEntityImpl record1 = new YTEntityImpl();

    database.begin();
    record1
        .field("value", "This is the first version")
        .save(database.getClusterNameById(database.getDefaultClusterId()));
    database.commit();

    final YTDatabaseListener listener =
        new YTDatabaseListener() {

          @Override
          public void onAfterTxCommit(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onAfterTxRollback(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onBeforeTxBegin(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onBeforeTxCommit(YTDatabaseSession iDatabase) {
            throw new RuntimeException("Rollback test");
          }

          @Override
          public void onBeforeTxRollback(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onClose(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onBeforeCommand(OCommandRequestText iCommand, OCommandExecutor executor) {
          }

          @Override
          public void onAfterCommand(
              OCommandRequestText iCommand, OCommandExecutor executor, Object result) {
          }

          @Override
          public void onCreate(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onDelete(YTDatabaseSession iDatabase) {
          }

          @Override
          public void onOpen(YTDatabaseSession iDatabase) {
          }

          @Override
          public boolean onCorruptionRepairDatabase(
              YTDatabaseSession iDatabase, final String iReason, String iWhatWillbeFixed) {
            return true;
          }
        };

    database.registerListener(listener);
    database.begin();

    try {
      database.commit();
      Assert.fail();
    } catch (YTTransactionException e) {
      Assert.assertTrue(true);
    } finally {
      database.unregisterListener(listener);
    }
  }

  @Test
  public void testTransactionWithDuplicateUniqueIndexValues() {
    YTClass fruitClass = database.getMetadata().getSchema().getClass("Fruit");

    if (fruitClass == null) {
      fruitClass = database.getMetadata().getSchema().createClass("Fruit");

      fruitClass.createProperty(database, "name", YTType.STRING);
      fruitClass.createProperty(database, "color", YTType.STRING);

      database
          .getMetadata()
          .getSchema()
          .getClass("Fruit")
          .getProperty("color")
          .createIndex(database, YTClass.INDEX_TYPE.UNIQUE);
    }

    Assert.assertEquals(database.countClusterElements("Fruit"), 0);

    try {
      database.begin();

      YTEntityImpl apple = new YTEntityImpl("Fruit").field("name", "Apple").field("color", "Red");
      YTEntityImpl orange = new YTEntityImpl("Fruit").field("name", "Orange")
          .field("color", "Orange");
      YTEntityImpl banana = new YTEntityImpl("Fruit").field("name", "Banana")
          .field("color", "Yellow");
      YTEntityImpl kumquat = new YTEntityImpl("Fruit").field("name", "Kumquat")
          .field("color", "Orange");

      apple.save();
      orange.save();
      banana.save();
      kumquat.save();

      database.commit();

      Assert.assertEquals(apple.getIdentity().getClusterId(), fruitClass.getDefaultClusterId());
      Assert.assertEquals(orange.getIdentity().getClusterId(), fruitClass.getDefaultClusterId());
      Assert.assertEquals(banana.getIdentity().getClusterId(), fruitClass.getDefaultClusterId());
      Assert.assertEquals(kumquat.getIdentity().getClusterId(), fruitClass.getDefaultClusterId());

      Assert.fail();

    } catch (YTRecordDuplicatedException e) {
      Assert.assertTrue(true);
      database.rollback();
    }

    Assert.assertEquals(database.countClusterElements("Fruit"), 0);
  }

  @Test
  public void testTransactionalSQL() {
    long prev = database.countClass("Account");

    database.begin();
    database
        .command(new OCommandSQL("transactional insert into Account set name = 'txTest1'"))
        .execute(database);
    database.commit();

    Assert.assertEquals(database.countClass("Account"), prev + 1);
  }

  @Test
  public void testTransactionalSQLJoinTx() {
    long prev = database.countClass("Account");

    database.begin();
    database
        .command(new OCommandSQL("transactional insert into Account set name = 'txTest2'"))
        .execute(database);

    Assert.assertTrue(database.getTransaction().isActive());

    if (!remoteDB) {
      Assert.assertEquals(database.countClass("Account"), prev + 1);
    }

    database.commit();

    Assert.assertFalse(database.getTransaction().isActive());
    Assert.assertEquals(database.countClass("Account"), prev + 1);
  }
}
