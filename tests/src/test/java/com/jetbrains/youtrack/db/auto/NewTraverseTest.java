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

import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.api.query.ResultSet;
import com.jetbrains.youtrack.db.api.record.Vertex;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
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
public class NewTraverseTest extends BaseDBTest {

  private int totalElements = 0;
  private Vertex tomCruise;
  private Vertex megRyan;
  private Vertex nicoleKidman;

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

    var e = database.newRegularEdge(tomCruise, topGun, "actorIn");
    e.save();

    totalElements++;
    e = database.newRegularEdge(megRyan, topGun, "actorIn");
    e.save();

    totalElements++;
    e = database.newRegularEdge(tomCruise, missionImpossible, "actorIn");
    e.save();

    totalElements++;
    e = database.newRegularEdge(megRyan, youHaveGotMail, "actorIn");
    e.save();

    totalElements++;

    e = database.newRegularEdge(tomCruise, megRyan, "friend");
    e.save();

    totalElements++;
    e = database.newRegularEdge(tomCruise, nicoleKidman, "married");

    e.setProperty("year", 1990);
    e.save();

    totalElements++;
    database.commit();
  }

  public void traverseSQLAllFromActorNoWhereBreadthFrirst() {
    ResultSet result1 =
        database.query("traverse * from " + tomCruise.getIdentity() + " strategy BREADTH_FIRST");

    for (int i = 0; i < totalElements; i++) {
      Assert.assertTrue(result1.hasNext());
      result1.next();
    }
    result1.close();
  }

  public void traverseSQLAllFromActorNoWhereDepthFrirst() {
    ResultSet result1 =
        database.query("traverse * from " + tomCruise.getIdentity() + " strategy DEPTH_FIRST");

    for (int i = 0; i < totalElements; i++) {
      Assert.assertTrue(result1.hasNext());
      result1.next();
    }
    result1.close();
  }

  @Test
  public void traverseSQLOutFromActor1Depth() {
    ResultSet result1 =
        database.query("traverse out_ from " + tomCruise.getIdentity() + " while $depth <= 1");

    Assert.assertTrue(result1.hasNext());
    result1.close();
  }

  @Test
  public void traverseSQLMoviesOnly() {
    ResultSet result1 =
        database.query("select from ( traverse * from Movie ) where @class = 'Movie'");
    Assert.assertTrue(result1.hasNext());
    while (result1.hasNext()) {
      Result d = result1.next();

      Assert.assertEquals(d.getEntity().get().getSchemaType().get().getName(), "Movie");
    }
    result1.close();
  }

  @Test
  public void traverseSQLPerClassFields() {
    ResultSet result1 =
        database.query(
            "select from ( traverse out() from "
                + tomCruise.getIdentity()
                + ") where @class = 'Movie'");
    Assert.assertTrue(result1.hasNext());
    while (result1.hasNext()) {
      Result d = result1.next();
      Assert.assertEquals(d.getEntity().get().getSchemaType().get().getName(), "Movie");
    }
    result1.close();
  }

  @Test
  public void traverseSQLMoviesOnlyDepth() {
    ResultSet result1 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 1 ) where @class = 'Movie'");
    Assert.assertFalse(result1.hasNext());
    result1.close();
    ResultSet result2 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " while $depth <= 2 ) where @class = 'Movie'");
    Assert.assertTrue(result2.hasNext());
    int size2 = 0;
    while (result2.hasNext()) {
      EntityImpl d = result2.next().getEntity().get().getRecord();
      Assert.assertEquals(d.getClassName(), "Movie");
      size2++;
    }
    result2.close();
    ResultSet result3 =
        database.query(
            "select from ( traverse * from "
                + tomCruise.getIdentity()
                + " ) where @class = 'Movie'");
    Assert.assertTrue(result3.hasNext());
    int size3 = 0;
    while (result3.hasNext()) {
      EntityImpl d = result3.next().getEntity().get().getRecord();
      Assert.assertEquals(d.getClassName(), "Movie");
      size3++;
    }
    Assert.assertTrue(size3 > size2);
    result3.close();
  }

  @Test
  public void traverseSelect() {
    ResultSet result1 = database.query("traverse * from ( select from Movie )");
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
    ResultSet result1 =
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
    ResultSet result1 =
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
    ResultSet result1 =
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
    ResultSet result1 =
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
    ResultSet result1 = database.query("traverse * from Movie while $depth < 2");
    List<Result> list1 = new ArrayList<>();
    while (result1.hasNext()) {
      list1.add(result1.next());
    }
    result1.close();
    ResultSet result2 = database.query("select from ( traverse * from Movie while $depth < 2 )");
    List<Result> list2 = new ArrayList<>();
    while (result2.hasNext()) {
      list2.add(result2.next());
    }
    result2.close();
    ResultSet result3 =
        database.query("select from ( traverse * from Movie while $depth < 2 ) where true");
    List<Result> list3 = new ArrayList<>();
    while (result3.hasNext()) {
      list3.add(result3.next());
    }
    result3.close();
    ResultSet result4 =
        database.query(
            "select from ( traverse * from Movie while $depth < 2 and ( true = true ) ) where"
                + " true");

    List<Result> list4 = new ArrayList<>();
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
    ResultSet result1 = database.query("traverse * from Movie limit 1");
    Assert.assertTrue(result1.hasNext());
    result1.next();
    Assert.assertFalse(result1.hasNext());
  }

  @Test
  public void traverseAndFilterByAttributeThatContainsDotInValue() {
    // issue #4952
    ResultSet result1 =
        database.query(
            "select from ( traverse out_married, in[attributeWithDotValue = 'a.b']  from "
                + tomCruise.getIdentity()
                + ")");
    Assert.assertTrue(result1.hasNext());
    boolean found = false;
    while (result1.hasNext()) {
      Result doc = result1.next();
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
    ResultSet result1 =
        database.query(
            "select from (traverse out_married, in[attributeWithDotValue = :param1]  from "
                + tomCruise.getIdentity()
                + ")",
            params);
    Assert.assertTrue(result1.hasNext());
    boolean found = false;
    while (result1.hasNext()) {
      Result doc = result1.next();
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
    ResultSet result1 =
        database.query(
            "select *, $depth as d from ( traverse out_married  from "
                + tomCruise.getIdentity()
                + " while $depth < 2)");
    boolean found = false;
    Integer i = 0;
    while (result1.hasNext()) {
      Result doc = result1.next();
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
      DatabaseSessionInternal db = database.copy();
      DatabaseRecordThreadLocal.instance().set(db);
      ResultSet result1 = db.query(q);
      Assert.assertTrue(result1.hasNext());
      boolean found = false;
      Integer i = 0;
      Result doc;
      while (result1.hasNext()) {
        doc = result1.next();
        i++;
      }
      Assert.assertEquals(i.intValue(), 2);
      result1.close();
    } finally {
      DatabaseRecordThreadLocal.instance().set(database);
    }
  }
}
