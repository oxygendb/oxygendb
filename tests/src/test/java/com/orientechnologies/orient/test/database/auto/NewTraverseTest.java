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

import com.orientechnologies.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.record.YTVertex;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.sql.executor.YTResult;
import com.orientechnologies.core.sql.executor.YTResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("unused")
public class NewTraverseTest extends DocumentDBBaseTest {

  private int totalElements = 0;
  private YTVertex tomCruise;
  private YTVertex megRyan;
  private YTVertex nicoleKidman;

  @Parameters(value = "remote")
  public NewTraverseTest(boolean remote) {
    super(remote);
  }

  @BeforeClass
  public void init() {
    database.createVertexClass("Actor");
    database.createVertexClass("Movie");

    database.createEdgeClass("actorIn");
    database.createEdgeClass("friend");
    database.createEdgeClass("married");

    database.begin();
    tomCruise = database.newVertex("Actor");
    tomCruise.setProperty("name", "Tom Cruise");
    tomCruise.save();

    totalElements++;
    megRyan = database.newVertex("Actor");
    megRyan.setProperty("name", "Meg Ryan");
    megRyan.save();

    totalElements++;
    nicoleKidman = database.newVertex("Actor");
    nicoleKidman.setProperty("name", "Nicole Kidman");
    nicoleKidman.setProperty("attributeWithDotValue", "a.b");
    nicoleKidman.save();

    totalElements++;

    var topGun = database.newVertex("Movie");
    topGun.setProperty("name", "Top Gun");
    topGun.setProperty("year", 1986);
    topGun.save();

    totalElements++;
    var missionImpossible = database.newVertex("Movie");
    missionImpossible.setProperty("name", "Mission: Impossible");
    missionImpossible.setProperty("year", 1996);
    missionImpossible.save();

    totalElements++;
    var youHaveGotMail = database.newVertex("Movie");
    youHaveGotMail.setProperty("name", "You've Got Mail");
    youHaveGotMail.setProperty("year", 1998);
    youHaveGotMail.save();
    totalElements++;

    var e = database.newEdge(tomCruise, topGun, "actorIn");
    e.save();

    totalElements++;
    e = database.newEdge(megRyan, topGun, "actorIn");
    e.save();

    totalElements++;
    e = database.newEdge(tomCruise, missionImpossible, "actorIn");
    e.save();

    totalElements++;
    e = database.newEdge(megRyan, youHaveGotMail, "actorIn");
    e.save();

    totalElements++;

    e = database.newEdge(tomCruise, megRyan, "friend");
    e.save();

    totalElements++;
    e = database.newEdge(tomCruise, nicoleKidman, "married");

    e.setProperty("year", 1990);
    e.save();

    totalElements++;
    database.commit();
  }

  public void traverseSQLAllFromActorNoWhereBreadthFrirst() {
    YTResultSet result1 =
        database.query("traverse * from " + tomCruise.getIdentity() + " strategy BREADTH_FIRST");

    for (int i = 0; i < totalElements; i++) {
      Assert.assertTrue(result1.hasNext());
      result1.next();
    }
    result1.close();
  }

  public void traverseSQLAllFromActorNoWhereDepthFrirst() {
    YTResultSet result1 =
        database.query("traverse * from " + tomCruise.getIdentity() + " strategy DEPTH_FIRST");

    for (int i = 0; i < totalElements; i++) {
      Assert.assertTrue(result1.hasNext());
      result1.next();
    }
    result1.close();
  }

  @Test
  public void traverseSQLOutFromActor1Depth() {
    YTResultSet result1 =
        database.query("traverse out_ from " + tomCruise.getIdentity() + " while $depth <= 1");

    Assert.assertTrue(result1.hasNext());
    result1.close();
  }

  @Test
  public void traverseSQLMoviesOnly() {
    YTResultSet result1 =
        database.query("select from ( traverse * from Movie ) where @class = 'Movie'");
    Assert.assertTrue(result1.hasNext());
    while (result1.hasNext()) {
      YTResult d = result1.next();

      Assert.assertEquals(d.getEntity().get().getSchemaType().get().getName(), "Movie");
    }
    result1.close();
  }

  @Test
  public void traverseSQLPerClassFields() {
    YTResultSet result1 =
        database.query(
            "select from ( traverse out() from "
                + tomCruise.getIdentity()
                + ") where @class = 'Movie'");
    Assert.assertTrue(result1.hasNext());
    while (result1.hasNext()) {
      YTResult d = result1.next();
      Assert.assertEquals(d.getEntity().get().getSchemaType().get().getName(), "Movie");
    }
    result1.close();
  }

  @Test
  public void traverseSQLMoviesOnlyDepth() {
    YTResultSet result1 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 1 ) where @class = 'Movie'");
    Assert.assertFalse(result1.hasNext());
    result1.close();
    YTResultSet result2 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 ) where @class = 'Movie'");
    Assert.assertTrue(result2.hasNext());
    int size2 = 0;
    while (result2.hasNext()) {
      YTEntityImpl d = result2.next().getEntity().get().getRecord();
      Assert.assertEquals(d.getClassName(), "Movie");
      size2++;
    }
    result2.close();
    YTResultSet result3 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " ) where @class = 'Movie'");
    Assert.assertTrue(result3.hasNext());
    int size3 = 0;
    while (result3.hasNext()) {
      YTEntityImpl d = result3.next().getEntity().get().getRecord();
      Assert.assertEquals(d.getClassName(), "Movie");
      size3++;
    }
    Assert.assertTrue(size3 > size2);
    result3.close();
  }

  @Test
  public void traverseSelect() {
    YTResultSet result1 = database.query("traverse * from ( select from Movie )");
    int tot = 0;
    while (result1.hasNext()) {
      result1.next();
      tot++;
    }

    Assert.assertEquals(tot, totalElements);
    result1.close();
  }

  @Test
  public void traverseSQLSelectAndTraverseNested() {
    YTResultSet result1 =
        database.query(
            "traverse * from ( select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 ) where @class = 'Movie' )");

    int tot = 0;
    while (result1.hasNext()) {
      result1.next();
      tot++;
    }

    Assert.assertEquals(tot, totalElements);
    result1.close();
  }

  @Test
  public void traverseAPISelectAndTraverseNested() {
    YTResultSet result1 =
        database.command(
            "traverse * from ( select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 ) where @class = 'Movie' )");
    int tot = 0;
    while (result1.hasNext()) {
      result1.next();
      tot++;
    }
    Assert.assertEquals(tot, totalElements);
  }

  @Test
  public void traverseAPISelectAndTraverseNestedDepthFirst() {
    YTResultSet result1 =
        database.query(
            "traverse * from ( select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 strategy depth_first ) where @class = 'Movie' )");
    int tot = 0;
    while (result1.hasNext()) {
      result1.next();
      tot++;
    }
    Assert.assertEquals(tot, totalElements);
    result1.close();
  }

  @Test
  public void traverseAPISelectAndTraverseNestedBreadthFirst() {
    YTResultSet result1 =
        database.command(
            "traverse * from ( select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 strategy breadth_first ) where @class = 'Movie' )");
    int tot = 0;
    while (result1.hasNext()) {
      result1.next();
      tot++;
    }
    Assert.assertEquals(tot, totalElements);
  }

  @Test
  public void traverseSelectNoInfluence() {
    YTResultSet result1 = database.query("traverse * from Movie while $depth < 2");
    List<YTResult> list1 = new ArrayList<>();
    while (result1.hasNext()) {
      list1.add(result1.next());
    }
    result1.close();
    YTResultSet result2 = database.query("select from ( traverse * from Movie while $depth < 2 )");
    List<YTResult> list2 = new ArrayList<>();
    while (result2.hasNext()) {
      list2.add(result2.next());
    }
    result2.close();
    YTResultSet result3 =
        database.query("select from ( traverse * from Movie while $depth < 2 ) where true");
    List<YTResult> list3 = new ArrayList<>();
    while (result3.hasNext()) {
      list3.add(result3.next());
    }
    result3.close();
    YTResultSet result4 =
        database.query(
            "select from ( traverse * from Movie while $depth < 2 and ( true = true ) ) where"
                + " true");

    List<YTResult> list4 = new ArrayList<>();
    while (result4.hasNext()) {
      list4.add(result4.next());
    }

    Assert.assertEquals(list1, list2);
    Assert.assertEquals(list1, list3);
    Assert.assertEquals(list1, list4);
    result4.close();
  }

  @Test
  public void traverseNoConditionLimit1() {
    YTResultSet result1 = database.query("traverse * from Movie limit 1");
    Assert.assertTrue(result1.hasNext());
    result1.next();
    Assert.assertFalse(result1.hasNext());
  }

  @Test
  public void traverseAndFilterByAttributeThatContainsDotInValue() {
    // issue #4952
    YTResultSet result1 =
        database.query(
            "select from ( traverse out_married, in[attributeWithDotValue = 'a.b']  from "
                + tomCruise.getIdentity()
                + ")");
    Assert.assertTrue(result1.hasNext());
    boolean found = false;
    while (result1.hasNext()) {
      YTResult doc = result1.next();
      String name = doc.getProperty("name");
      if ("Nicole Kidman".equals(name)) {
        found = true;
        break;
      }
    }
    Assert.assertTrue(found);
    result1.close();
  }

  @Test
  public void traverseAndFilterWithNamedParam() {
    // issue #5225
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("param1", "a.b");
    YTResultSet result1 =
        database.query(
            "select from (traverse out_married, in[attributeWithDotValue = :param1]  from "
                + tomCruise.getIdentity()
                + ")",
            params);
    Assert.assertTrue(result1.hasNext());
    boolean found = false;
    while (result1.hasNext()) {
      YTResult doc = result1.next();
      String name = doc.getProperty("name");
      if ("Nicole Kidman".equals(name)) {
        found = true;
        break;
      }
    }
    Assert.assertTrue(found);
  }

  @Test
  public void traverseAndCheckDepthInSelect() {
    YTResultSet result1 =
        database.query(
            "select *, $depth as d from ( traverse out_married  from "
                + tomCruise.getIdentity()
                + " while $depth < 2)");
    boolean found = false;
    Integer i = 0;
    while (result1.hasNext()) {
      YTResult doc = result1.next();
      Integer depth = doc.getProperty("d");
      Assert.assertEquals(depth, i++);
    }
    Assert.assertEquals(i.intValue(), 2);
    result1.close();
  }

  @Test
  public void traverseAndCheckReturn() {

    try {

      String q = "traverse in('married')  from " + nicoleKidman.getIdentity();
      YTDatabaseSessionInternal db = database.copy();
      ODatabaseRecordThreadLocal.instance().set(db);
      YTResultSet result1 = db.query(q);
      Assert.assertTrue(result1.hasNext());
      boolean found = false;
      Integer i = 0;
      YTResult doc;
      while (result1.hasNext()) {
        doc = result1.next();
        i++;
      }
      Assert.assertEquals(i.intValue(), 2);
      result1.close();
    } finally {
      ODatabaseRecordThreadLocal.instance().set(database);
    }
  }
}
