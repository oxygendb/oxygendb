/*
 *
 *  *  Copyright OxygenDB
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *
 *
 */
package com.orientechnologies.orient.core.sql;

import com.orientechnologies.DBTestBase;
import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.OCreateDatabaseUtil;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseSessionInternal;
import com.orientechnologies.orient.core.db.OLiveQueryMonitor;
import com.orientechnologies.orient.core.db.OLiveQueryResultListener;
import com.orientechnologies.orient.core.db.OxygenDB;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class OLiveQueryV2Test {

  class MyLiveQueryListener implements OLiveQueryResultListener {

    public CountDownLatch latch;

    public MyLiveQueryListener(CountDownLatch latch) {
      this.latch = latch;
    }

    public List<OResult> ops = new ArrayList<OResult>();

    @Override
    public void onCreate(ODatabaseSession database, OResult data) {
      ops.add(data);
      latch.countDown();
    }

    @Override
    public void onUpdate(ODatabaseSession database, OResult before, OResult after) {
      ops.add(after);
      latch.countDown();
    }

    @Override
    public void onDelete(ODatabaseSession database, OResult data) {
      ops.add(data);
      latch.countDown();
    }

    @Override
    public void onError(ODatabaseSession database, OException exception) {
    }

    @Override
    public void onEnd(ODatabaseSession database) {
    }
  }

  @Test
  public void testLiveInsert() throws InterruptedException {
    ODatabaseSessionInternal db = new ODatabaseDocumentTx("memory:OLiveQueryV2Test");
    db.activateOnCurrentThread();
    db.create();
    try {
      db.getMetadata().getSchema().createClass("test");
      db.getMetadata().getSchema().createClass("test2");
      MyLiveQueryListener listener = new MyLiveQueryListener(new CountDownLatch(2));

      OLiveQueryMonitor monitor = db.live("select from test", listener);
      Assert.assertNotNull(monitor);

      db.begin();
      db.command("insert into test set name = 'foo', surname = 'bar'").close();
      db.command("insert into test set name = 'foo', surname = 'baz'").close();
      db.command("insert into test2 set name = 'foo'").close();
      db.commit();

      Assert.assertTrue(listener.latch.await(1, TimeUnit.MINUTES));

      monitor.unSubscribe();

      db.begin();
      db.command("insert into test set name = 'foo', surname = 'bax'").close();
      db.command("insert into test2 set name = 'foo'").close();
      db.command("insert into test set name = 'foo', surname = 'baz'").close();
      db.commit();

      Assert.assertEquals(2, listener.ops.size());
      for (OResult doc : listener.ops) {
        Assert.assertEquals("test", doc.getProperty("@class"));
        Assert.assertEquals("foo", doc.getProperty("name"));
        ORID rid = doc.getProperty("@rid");
        Assert.assertTrue(rid.isPersistent());
      }
    } finally {
      db.drop();
    }
  }

  @Test
  public void testLiveInsertOnCluster() {
    final OxygenDB context =
        OCreateDatabaseUtil.createDatabase(
            "testLiveInsertOnCluster", DBTestBase.embeddedDBUrl(getClass()),
            OCreateDatabaseUtil.TYPE_MEMORY);
    try (ODatabaseSessionInternal db =
        (ODatabaseSessionInternal)
            context.open(
                "testLiveInsertOnCluster", "admin", OCreateDatabaseUtil.NEW_ADMIN_PASSWORD)) {

      OClass clazz = db.getMetadata().getSchema().createClass("test");

      int defaultCluster = clazz.getDefaultClusterId();
      String clusterName = db.getStorage().getClusterNameById(defaultCluster);

      OLiveQueryV2Test.MyLiveQueryListener listener =
          new OLiveQueryV2Test.MyLiveQueryListener(new CountDownLatch(1));

      db.live(" select from cluster:" + clusterName, listener);

      db.begin();
      db.command("insert into cluster:" + clusterName + " set name = 'foo', surname = 'bar'");
      db.commit();

      try {
        Assert.assertTrue(listener.latch.await(1, TimeUnit.MINUTES));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      Assert.assertEquals(1, listener.ops.size());
      for (OResult doc : listener.ops) {
        Assert.assertEquals("foo", doc.getProperty("name"));
        ORID rid = doc.getProperty("@rid");
        Assert.assertTrue(rid.isPersistent());
        Assert.assertNotNull(rid);
      }
    }
  }

  @Test
  public void testLiveWithWhereCondition() {
    final OxygenDB context =
        OCreateDatabaseUtil.createDatabase(
            "testLiveWithWhereCondition", DBTestBase.embeddedDBUrl(getClass()),
            OCreateDatabaseUtil.TYPE_MEMORY);
    try (ODatabaseSessionInternal db =
        (ODatabaseSessionInternal)
            context.open(
                "testLiveWithWhereCondition", "admin", OCreateDatabaseUtil.NEW_ADMIN_PASSWORD)) {

      db.getMetadata().getSchema().createClass("test");

      OLiveQueryV2Test.MyLiveQueryListener listener =
          new OLiveQueryV2Test.MyLiveQueryListener(new CountDownLatch(1));

      db.live("select from V where id = 1", listener);

      db.begin();
      db.command("insert into V set id = 1");
      db.commit();

      try {
        Assert.assertTrue(listener.latch.await(1, TimeUnit.MINUTES));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      Assert.assertEquals(1, listener.ops.size());
      for (OResult doc : listener.ops) {
        Assert.assertEquals(doc.getProperty("id"), Integer.valueOf(1));
        ORID rid = doc.getProperty("@rid");
        Assert.assertTrue(rid.isPersistent());
        Assert.assertNotNull(rid);
      }
    }
  }

  @Test
  public void testRestrictedLiveInsert() throws ExecutionException, InterruptedException {
    ODatabaseSessionInternal db = new ODatabaseDocumentTx("memory:OLiveQueryTest");
    db.activateOnCurrentThread();
    db.create();
    try {
      OSchema schema = db.getMetadata().getSchema();
      OClass oRestricted = schema.getClass("ORestricted");
      schema.createClass("test", oRestricted);

      int liveMatch = 2;
      List<ODocument> query =
          db.query(new OSQLSynchQuery("select from OUSer where name = 'reader'"));

      final OIdentifiable reader = query.iterator().next().getIdentity();
      final OIdentifiable current = db.getUser().getIdentity(db);

      ExecutorService executorService = Executors.newSingleThreadExecutor();

      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch dataArrived = new CountDownLatch(liveMatch);
      Future<Integer> future =
          executorService.submit(
              new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                  ODatabaseDocumentTx db = new ODatabaseDocumentTx("memory:OLiveQueryTest");
                  db.open("reader", "reader");

                  final AtomicInteger integer = new AtomicInteger(0);
                  db.live(
                      "live select from test",
                      new OLiveQueryResultListener() {

                        @Override
                        public void onCreate(ODatabaseSession database, OResult data) {
                          integer.incrementAndGet();
                          dataArrived.countDown();
                        }

                        @Override
                        public void onUpdate(
                            ODatabaseSession database, OResult before, OResult after) {
                          integer.incrementAndGet();
                          dataArrived.countDown();
                        }

                        @Override
                        public void onDelete(ODatabaseSession database, OResult data) {
                          integer.incrementAndGet();
                          dataArrived.countDown();
                        }

                        @Override
                        public void onError(ODatabaseSession database, OException exception) {
                        }

                        @Override
                        public void onEnd(ODatabaseSession database) {
                        }
                      });

                  latch.countDown();
                  Assert.assertTrue(dataArrived.await(1, TimeUnit.MINUTES));
                  return integer.get();
                }
              });

      latch.await();

      db.begin();
      db.command("insert into test set name = 'foo', surname = 'bar'").close();
      db.command(
              "insert into test set name = 'foo', surname = 'bar', _allow=?",
              new ArrayList<OIdentifiable>() {
                {
                  add(current);
                  add(reader);
                }
              })
          .close();
      db.commit();

      Integer integer = future.get();
      Assert.assertEquals(liveMatch, integer.intValue());
    } finally {
      db.drop();
    }
  }

  @Test
  public void testLiveProjections() throws InterruptedException {

    ODatabaseSessionInternal db = new ODatabaseDocumentTx("memory:OLiveQueryV2Test");
    db.activateOnCurrentThread();
    db.create();
    try {
      db.getMetadata().getSchema().createClass("test");
      db.getMetadata().getSchema().createClass("test2");
      MyLiveQueryListener listener = new MyLiveQueryListener(new CountDownLatch(2));

      OLiveQueryMonitor monitor = db.live("select @class, @rid as rid, name from test", listener);
      Assert.assertNotNull(monitor);

      db.begin();
      db.command("insert into test set name = 'foo', surname = 'bar'").close();
      db.command("insert into test set name = 'foo', surname = 'baz'").close();
      db.command("insert into test2 set name = 'foo'").close();
      db.commit();

      Assert.assertTrue(listener.latch.await(5, TimeUnit.SECONDS));

      monitor.unSubscribe();

      db.begin();
      db.command("insert into test set name = 'foo', surname = 'bax'").close();
      db.command("insert into test2 set name = 'foo'").close();
      db.command("insert into test set name = 'foo', surname = 'baz'").close();
      db.commit();

      Assert.assertEquals(2, listener.ops.size());
      for (OResult doc : listener.ops) {
        Assert.assertEquals("test", doc.getProperty("@class"));
        Assert.assertEquals("foo", doc.getProperty("name"));
        Assert.assertNull(doc.getProperty("surname"));
        ORID rid = doc.getProperty("rid");
        Assert.assertTrue(rid.isPersistent());
      }
    } finally {
      db.drop();
    }
  }
}
