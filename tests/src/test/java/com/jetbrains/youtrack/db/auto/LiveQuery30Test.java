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
package com.jetbrains.youtrack.db.auto;

import com.jetbrains.youtrack.db.api.DatabaseSession;
import com.jetbrains.youtrack.db.api.exception.BaseException;
import com.jetbrains.youtrack.db.api.query.LiveQueryMonitor;
import com.jetbrains.youtrack.db.api.query.LiveQueryResultListener;
import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.api.record.RID;
import com.jetbrains.youtrack.db.internal.common.util.Pair;
import com.jetbrains.youtrack.db.internal.core.command.CommandOutputListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * only for remote usage (it requires registered LiveQuery plugin)
 */
@Test(groups = "Query")
public class LiveQuery30Test extends BaseDBTest implements CommandOutputListener {

  private final CountDownLatch latch = new CountDownLatch(2);
  private final CountDownLatch unLatch = new CountDownLatch(1);

  class MyLiveQueryListener implements LiveQueryResultListener {

    public List<Pair<String, Result>> ops = new ArrayList<>();
    public int unsubscribe;

    @Override
    public void onCreate(DatabaseSession database, Result data) {
      ops.add(new Pair<>("create", data));
      latch.countDown();
    }

    @Override
    public void onUpdate(DatabaseSession database, Result before, Result after) {
      ops.add(new Pair<>("update", after));
      latch.countDown();
    }

    @Override
    public void onDelete(DatabaseSession database, Result data) {
      ops.add(new Pair<>("delete", data));
      latch.countDown();
    }

    @Override
    public void onError(DatabaseSession database, BaseException exception) {
    }

    @Override
    public void onEnd(DatabaseSession database) {
      unsubscribe = 1;
      unLatch.countDown();
    }
  }

  @Parameters(value = {"remote"})
  public LiveQuery30Test(boolean remote) {
    super(remote);
  }

  @Test
  public void checkLiveQuery1() throws IOException, InterruptedException {
    final String className1 = "LiveQuery30Test_checkLiveQuery1_1";
    final String className2 = "LiveQuery30Test_checkLiveQuery1_2";
    database.getMetadata().getSchema().createClass(className1);
    database.getMetadata().getSchema().createClass(className2);

    MyLiveQueryListener listener = new MyLiveQueryListener();

    LiveQueryMonitor monitor = database.live("live select from " + className1, listener);
    Assert.assertNotNull(monitor);

    database.command("insert into " + className1 + " set name = 'foo', surname = 'bar'");
    database.command("insert into  " + className1 + " set name = 'foo', surname = 'baz'");
    database.command("insert into " + className2 + " set name = 'foo'");
    latch.await(1, TimeUnit.MINUTES);

    monitor.unSubscribe();
    database.command("insert into " + className1 + " set name = 'foo', surname = 'bax'");
    Assert.assertEquals(listener.ops.size(), 2);
    for (Pair doc : listener.ops) {
      Assert.assertEquals(doc.getKey(), "create");
      Result res = (Result) doc.getValue();
      Assert.assertEquals((res).getProperty("name"), "foo");
      Assert.assertNotNull(res.getProperty("@rid"));
      Assert.assertTrue(((RID) res.getProperty("@rid")).getClusterPosition() >= 0);
    }
    unLatch.await(1, TimeUnit.MINUTES);
  }

  @Override
  @Test(enabled = false)
  public void onMessage(final String iText) {
    // System.out.print(iText);
    // System.out.flush();
  }
}
