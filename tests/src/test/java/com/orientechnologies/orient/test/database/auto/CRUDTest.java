/*
 * Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
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

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ORecordLazyList;
import com.orientechnologies.orient.core.db.record.ORecordLazySet;
import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.db.record.OTrackedMap;
import com.orientechnologies.orient.core.db.record.OTrackedSet;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.OElement;
import com.orientechnologies.orient.core.record.impl.OBlob;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.sql.OCommandSQLParsingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

@Test
public class CRUDTest extends DocumentDBBaseTest {

  protected long startRecordNumber;

  private OElement rome;

  @Parameters(value = "remote")
  public CRUDTest(@Optional Boolean remote) {
    super(remote != null && remote);
  }

  @BeforeClass
  @Override
  public void beforeClass() throws Exception {
    super.beforeClass();

    createSimpleTestClass();
    createSimpleArrayTestClass();
    createBinaryTestClass();
    createComplexTestClass();
    createPersonClass();
    createEventClass();
    createAgendaClass();
    createNonGenericClass();
    createMediaClass();
    createParentChildClasses();
  }

  @Test
  public void create() {
    startRecordNumber = database.countClass("Account");

    OElement address;

    database.begin();
    var country = database.newElement("Country");
    country.setProperty("name", "Italy");
    country.save();

    rome = database.newElement("City");
    rome.setProperty("name", "Rome");
    rome.setProperty("country", country);
    database.save(rome);

    address = database.newElement("Address");
    address.setProperty("type", "Residence");
    address.setProperty("street", "Piazza Navona, 1");
    address.setProperty("city", rome);
    database.save(address);

    for (long i = startRecordNumber; i < startRecordNumber + TOT_RECORDS_ACCOUNT; ++i) {
      OElement account = database.newElement("Account");
      account.setProperty("id", i);
      account.setProperty("name", "Bill");
      account.setProperty("surname", "Gates");
      account.setProperty("birthDate", new Date());
      account.setProperty("salary", (i + 300.10f));
      account.setProperty("addresses", Collections.singletonList(address));
      database.save(account);
    }
    database.commit();
  }

  @Test(dependsOnMethods = "create")
  public void testCreate() {
    Assert.assertEquals(database.countClass("Account") - startRecordNumber, TOT_RECORDS_ACCOUNT);
  }

  @Test(dependsOnMethods = "testCreate")
  public void testCreateClass() {
    var schema = database.getMetadata().getSchema();
    Assert.assertNull(schema.getClass("Dummy"));
    var dummyClass = schema.createClass("Dummy");
    dummyClass.createProperty("name", OType.STRING);

    Assert.assertEquals(database.countClass("Dummy"), 0);
    Assert.assertNotNull(schema.getClass("Dummy"));
  }

  @Test
  public void testSimpleTypes() {
    OElement element = database.newElement("JavaSimpleTestClass");
    Assert.assertEquals(element.getProperty("text"), "initTest");

    database.begin();
    Date date = new Date();
    element.setProperty("text", "test");
    element.setProperty("numberSimple", 12345);
    element.setProperty("doubleSimple", 12.34d);
    element.setProperty("floatSimple", 123.45f);
    element.setProperty("longSimple", 12345678L);
    element.setProperty("byteSimple", (byte) 1);
    element.setProperty("flagSimple", true);
    element.setProperty("dateField", date);

    database.save(element);
    database.commit();

    ORID id = element.getIdentity();
    database.close();

    database = createSessionInstance();
    database.begin();
    ODocument loadedRecord = database.load(id);
    Assert.assertEquals(loadedRecord.getProperty("text"), "test");
    Assert.assertEquals(loadedRecord.<Integer>getProperty("numberSimple"), 12345);
    Assert.assertEquals(loadedRecord.<Double>getProperty("doubleSimple"), 12.34d);
    Assert.assertEquals(loadedRecord.<Float>getProperty("floatSimple"), 123.45f);
    Assert.assertEquals(loadedRecord.<Long>getProperty("longSimple"), 12345678L);
    Assert.assertEquals(loadedRecord.<Byte>getProperty("byteSimple"), (byte) 1);
    Assert.assertEquals(loadedRecord.<Boolean>getProperty("flagSimple"), true);
    Assert.assertEquals(loadedRecord.getProperty("dateField"), date);
    database.commit();
  }

  @Test(dependsOnMethods = "testSimpleTypes")
  public void testSimpleArrayTypes() {
    OElement element = database.newInstance("JavaSimpleArraysTestClass");
    String[] textArray = new String[10];
    int[] intArray = new int[10];
    long[] longArray = new long[10];
    double[] doubleArray = new double[10];
    float[] floatArray = new float[10];
    boolean[] booleanArray = new boolean[10];
    Date[] dateArray = new Date[10];
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.YEAR, 1900);
    cal.set(Calendar.MONTH, Calendar.JANUARY);
    for (int i = 0; i < 10; i++) {
      textArray[i] = i + "";
      intArray[i] = i;
      longArray[i] = i;
      doubleArray[i] = i;
      floatArray[i] = i;
      booleanArray[i] = (i % 2 == 0);
      cal.set(Calendar.DAY_OF_MONTH, (i + 1));
      dateArray[i] = cal.getTime();
    }

    element.setProperty("text", textArray);
    element.setProperty("dateField", dateArray);
    element.setProperty("doubleSimple", doubleArray);
    element.setProperty("flagSimple", booleanArray);
    element.setProperty("floatSimple", floatArray);
    element.setProperty("longSimple", longArray);
    element.setProperty("numberSimple", intArray);

    Assert.assertNotNull(element.getProperty("text"));
    Assert.assertNotNull(element.getProperty("numberSimple"));
    Assert.assertNotNull(element.getProperty("longSimple"));
    Assert.assertNotNull(element.getProperty("doubleSimple"));
    Assert.assertNotNull(element.getProperty("floatSimple"));
    Assert.assertNotNull(element.getProperty("flagSimple"));
    Assert.assertNotNull(element.getProperty("dateField"));

    database.begin();
    database.save(element);
    database.commit();
    ORID id = element.getIdentity();
    database.close();

    database = createSessionInstance();
    OElement loadedElement = database.load(id);
    Assert.assertNotNull(loadedElement.getProperty("text"));
    Assert.assertNotNull(loadedElement.getProperty("numberSimple"));
    Assert.assertNotNull(loadedElement.getProperty("longSimple"));
    Assert.assertNotNull(loadedElement.getProperty("doubleSimple"));
    Assert.assertNotNull(loadedElement.getProperty("floatSimple"));
    Assert.assertNotNull(loadedElement.getProperty("flagSimple"));
    Assert.assertNotNull(loadedElement.getProperty("dateField"));

    Assert.assertEquals(loadedElement.<List<String>>getProperty("text").size(), 10);
    Assert.assertEquals(loadedElement.<List<Integer>>getProperty("numberSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Long>>getProperty("longSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Double>>getProperty("doubleSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Float>>getProperty("floatSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Boolean>>getProperty("flagSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Date>>getProperty("dateField").size(), 10);

    for (int i = 0; i < 10; i++) {
      Assert.assertEquals(loadedElement.<List<String>>getProperty("text").get(i), i + "");
      Assert.assertEquals(loadedElement.<List<Integer>>getProperty("numberSimple").get(i), i);
      Assert.assertEquals(loadedElement.<List<Long>>getProperty("longSimple").get(i), i);
      Assert.assertEquals(loadedElement.<List<Double>>getProperty("doubleSimple").get(i), i);
      Assert.assertEquals(loadedElement.<List<Float>>getProperty("floatSimple").get(i), (float) i);
      Assert.assertEquals(
          loadedElement.<List<Boolean>>getProperty("flagSimple").get(i), (i % 2 == 0));
      cal.set(Calendar.DAY_OF_MONTH, (i + 1));
      Assert.assertEquals(loadedElement.<List<Date>>getProperty("dateField").get(i), cal.getTime());
    }

    for (int i = 0; i < 10; i++) {
      int j = i + 10;
      textArray[i] = j + "";
      intArray[i] = j;
      longArray[i] = j;
      doubleArray[i] = j;
      floatArray[i] = j;
      booleanArray[i] = (j % 2 == 0);
      cal.set(Calendar.DAY_OF_MONTH, (j + 1));
      dateArray[i] = cal.getTime();
    }
    loadedElement.setProperty("text", textArray);
    loadedElement.setProperty("dateField", dateArray);
    loadedElement.setProperty("doubleSimple", doubleArray);
    loadedElement.setProperty("flagSimple", booleanArray);
    loadedElement.setProperty("floatSimple", floatArray);
    loadedElement.setProperty("longSimple", longArray);
    loadedElement.setProperty("numberSimple", intArray);

    database.begin();
    database.save(loadedElement);
    database.commit();
    database.close();

    database = createSessionInstance();
    loadedElement = database.load(id);
    Assert.assertNotNull(loadedElement.getProperty("text"));
    Assert.assertNotNull(loadedElement.getProperty("numberSimple"));
    Assert.assertNotNull(loadedElement.getProperty("longSimple"));
    Assert.assertNotNull(loadedElement.getProperty("doubleSimple"));
    Assert.assertNotNull(loadedElement.getProperty("floatSimple"));
    Assert.assertNotNull(loadedElement.getProperty("flagSimple"));
    Assert.assertNotNull(loadedElement.getProperty("dateField"));

    Assert.assertEquals(loadedElement.<List<String>>getProperty("text").size(), 10);
    Assert.assertEquals(loadedElement.<List<Integer>>getProperty("numberSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Long>>getProperty("longSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Double>>getProperty("doubleSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Float>>getProperty("floatSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Boolean>>getProperty("flagSimple").size(), 10);
    Assert.assertEquals(loadedElement.<List<Date>>getProperty("dateField").size(), 10);

    for (int i = 0; i < 10; i++) {
      int j = i + 10;
      Assert.assertEquals(loadedElement.<List<String>>getProperty("text").get(i), j + "");
      Assert.assertEquals(loadedElement.<List<Integer>>getProperty("numberSimple").get(i), j);
      Assert.assertEquals(loadedElement.<List<Long>>getProperty("longSimple").get(i), j);
      Assert.assertEquals(loadedElement.<List<Double>>getProperty("doubleSimple").get(i), j);
      Assert.assertEquals(loadedElement.<List<Float>>getProperty("floatSimple").get(i), (float) j);
      Assert.assertEquals(
          loadedElement.<List<Boolean>>getProperty("flagSimple").get(i), (j % 2 == 0));

      cal.set(Calendar.DAY_OF_MONTH, (j + 1));
      Assert.assertEquals(loadedElement.<List<Date>>getProperty("dateField").get(i), cal.getTime());
    }

    database.close();

    database = createSessionInstance();
    loadedElement = database.load(id);

    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("text")).iterator().next() instanceof String);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("numberSimple")).iterator().next()
            instanceof Integer);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("longSimple")).iterator().next()
            instanceof Long);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("doubleSimple")).iterator().next()
            instanceof Double);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("floatSimple")).iterator().next()
            instanceof Float);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("flagSimple")).iterator().next()
            instanceof Boolean);
    Assert.assertTrue(
        ((Collection<?>) loadedElement.getProperty("dateField")).iterator().next() instanceof Date);

    database.begin();
    database.delete(id);
    database.commit();
  }

  @Test(dependsOnMethods = "testSimpleTypes")
  public void testBinaryDataType() {
    OElement element = database.newInstance("JavaBinaryDataTestClass");
    byte[] bytes = new byte[10];
    for (int i = 0; i < 10; i++) {
      bytes[i] = (byte) i;
    }

    element.setProperty("binaryData", bytes);

    String fieldName = "binaryData";
    Assert.assertNotNull(element.getProperty(fieldName));

    database.begin();
    database.save(element);
    database.commit();

    ORID id = element.getIdentity();
    database.close();

    database = createSessionInstance();
    OElement loadedElement = database.load(id);
    Assert.assertNotNull(loadedElement.getProperty(fieldName));

    Assert.assertEquals(loadedElement.<byte[]>getProperty("binaryData").length, 10);
    Assert.assertEquals(loadedElement.getProperty("binaryData"), bytes);

    for (int i = 0; i < 10; i++) {
      int j = i + 10;
      bytes[i] = (byte) j;
    }
    loadedElement.setProperty("binaryData", bytes);

    database.begin();
    database.save(loadedElement);
    database.commit();
    database.close();

    database = createSessionInstance();
    loadedElement = database.load(id);
    Assert.assertNotNull(loadedElement.getProperty(fieldName));

    Assert.assertEquals(loadedElement.<byte[]>getProperty("binaryData").length, 10);
    Assert.assertEquals(loadedElement.getProperty("binaryData"), bytes);

    database.close();

    database = createSessionInstance();

    database.begin();
    database.delete(id);
    database.commit();
  }

  @Test(dependsOnMethods = "testSimpleArrayTypes")
  public void collectionsDocumentTypeTestPhaseOne() {
    database.begin();
    OElement a = database.newInstance("JavaComplexTestClass");

    for (int i = 0; i < 3; i++) {
      var child1 = database.newElement("Child");
      var child2 = database.newElement("Child");
      var child3 = database.newElement("Child");

      a.setProperty("list", Collections.singletonList(child1));
      a.setProperty("set", Collections.singleton(child2));
      a.setProperty("children", Collections.singletonMap("" + i, child3));
    }

    a = database.save(a);
    database.commit();

    ORID rid = a.getIdentity();
    database.close();

    database = createSessionInstance();
    database.begin();
    List<ODocument> agendas = executeQuery("SELECT FROM " + rid);

    ODocument testLoadedEntity = agendas.get(0);

    checkCollectionImplementations(testLoadedEntity);

    database.save(testLoadedEntity);
    database.commit();

    database.freeze(false);
    database.release();

    database.begin();

    testLoadedEntity = database.load(rid);

    checkCollectionImplementations(testLoadedEntity);
    database.commit();
  }

  @Test(dependsOnMethods = "collectionsDocumentTypeTestPhaseOne")
  public void collectionsDocumentTypeTestPhaseTwo() {
    database.begin();
    OElement a = database.newInstance("JavaComplexTestClass");

    for (int i = 0; i < 10; i++) {
      var child1 = database.newElement("Child");
      var child2 = database.newElement("Child");
      var child3 = database.newElement("Child");

      a.setProperty("list", Collections.singletonList(child1));
      a.setProperty("set", Collections.singleton(child2));
      a.setProperty("children", Collections.singletonMap("" + i, child3));
    }

    a = database.save(a);
    database.commit();

    ORID rid = a.getIdentity();

    database.close();

    database = createSessionInstance();
    database.begin();
    List<ODocument> agendas = executeQuery("SELECT FROM " + rid);
    var testLoadedEntity = agendas.get(0);

    checkCollectionImplementations(testLoadedEntity);

    testLoadedEntity = database.save(testLoadedEntity);
    database.commit();

    database.freeze(false);
    database.release();

    database.begin();
    checkCollectionImplementations(database.bindToSession(testLoadedEntity));
    database.commit();
  }

  @Test(dependsOnMethods = "collectionsDocumentTypeTestPhaseTwo")
  public void collectionsDocumentTypeTestPhaseThree() {
    OElement a = database.newInstance("JavaComplexTestClass");

    database.begin();
    for (int i = 0; i < 100; i++) {
      var child1 = database.newElement("Child");
      var child2 = database.newElement("Child");
      var child3 = database.newElement("Child");

      a.setProperty("list", Collections.singletonList(child1));
      a.setProperty("set", Collections.singleton(child2));
      a.setProperty("children", Collections.singletonMap("" + i, child3));
    }
    a = database.save(a);
    database.commit();

    ORID rid = a.getIdentity();
    database.close();

    database = createSessionInstance();
    database.begin();
    List<ODocument> agendas = executeQuery("SELECT FROM " + rid);
    var testLoadedEntity = agendas.get(0);
    checkCollectionImplementations(testLoadedEntity);

    testLoadedEntity = database.save(testLoadedEntity);
    database.commit();

    database.freeze(false);
    database.release();

    database.begin();
    checkCollectionImplementations(database.bindToSession(testLoadedEntity));
    database.rollback();
  }

  protected void checkCollectionImplementations(ODocument doc) {
    Object collectionObj = doc.field("list");
    boolean validImplementation =
        (collectionObj instanceof OTrackedList<?>)
            || (doc.field("list") instanceof ORecordLazyList);
    if (!validImplementation) {
      Assert.fail(
          "Document list implementation "
              + collectionObj.getClass().getName()
              + " not compatible with current Object Database loading management");
    }
    collectionObj = doc.field("set");
    validImplementation =
        (collectionObj instanceof OTrackedSet<?>) || (collectionObj instanceof ORecordLazySet);
    if (!validImplementation) {
      Assert.fail(
          "Document set implementation "
              + collectionObj.getClass().getName()
              + " not compatible with current Object Database management");
    }
    collectionObj = doc.field("children");
    validImplementation = collectionObj instanceof OTrackedMap<?>;
    if (!validImplementation) {
      Assert.fail(
          "Document map implementation "
              + collectionObj.getClass().getName()
              + " not compatible with current Object Database management");
    }
  }

  @Test(dependsOnMethods = "testSimpleTypes")
  public void testDateInTransaction() {
    var element = database.newElement("JavaSimpleTestClass");
    Date date = new Date();
    element.setProperty("dateField", date);
    database.begin();
    element.save();
    database.commit();

    element = database.bindToSession(element);
    Assert.assertEquals(element.<List<Date>>getProperty("dateField"), date);
  }

  @Test(dependsOnMethods = "testCreateClass")
  public void readAndBrowseDescendingAndCheckHoleUtilization() {
    rome = database.bindToSession(rome);
    Set<Integer> ids = new HashSet<>(TOT_RECORDS_ACCOUNT);
    for (int i = 0; i < TOT_RECORDS_ACCOUNT; i++) {
      ids.add(i);
    }

    for (OElement a : database.browseClass("Account")) {
      int id = a.<Integer>getProperty("id");
      Assert.assertTrue(ids.remove(id));

      Assert.assertEquals(a.<Integer>getProperty("id"), id);
      Assert.assertEquals(a.getProperty("name"), "Bill");
      Assert.assertEquals(a.getProperty("surname"), "Gates");
      Assert.assertEquals(a.<Float>getProperty("salary"), id + 300.1f);
      Assert.assertEquals(a.<List<OIdentifiable>>getProperty("addresses").size(), 1);
      Assert.assertEquals(
          a.<List<OIdentifiable>>getProperty("addresses")
              .get(0)
              .<OElement>getRecord()
              .getElementProperty("city")
              .getProperty("name"),
          rome.<String>getProperty("name"));
      Assert.assertEquals(
          a.<List<OIdentifiable>>getProperty("addresses")
              .get(0)
              .<OElement>getRecord()
              .getElementProperty("city")
              .getElementProperty("country")
              .getProperty("name"),
          rome.<OElement>getRecord()
              .<OIdentifiable>getProperty("country")
              .<OElement>getRecord()
              .<String>getProperty("name"));
    }

    Assert.assertTrue(ids.isEmpty());
  }

  @Test(dependsOnMethods = "readAndBrowseDescendingAndCheckHoleUtilization")
  public void mapEnumAndInternalObjects() {
    for (OElement u : database.browseClass("OUser")) {
      database.begin();
      database.bindToSession(u).save();
      database.commit();
    }
  }

  @Test(dependsOnMethods = "mapEnumAndInternalObjects")
  public void mapObjectsLinkTest() {
    var p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    var c = database.newInstance("Child");
    c.setProperty("name", "John");

    var c1 = database.newInstance("Child");
    c1.setProperty("name", "Jack");

    var c2 = database.newInstance("Child");
    c2.setProperty("name", "Bob");

    var c3 = database.newInstance("Child");
    c3.setProperty("name", "Sam");

    var c4 = database.newInstance("Child");
    c4.setProperty("name", "Dean");

    var list = new ArrayList<OIdentifiable>();
    list.add(c1);
    list.add(c2);
    list.add(c3);
    list.add(c4);

    p.setProperty("list", list);

    var children = new HashMap<String, OElement>();
    children.put("first", c);
    p.setProperty("children", children);

    database.begin();
    database.save(p);
    database.commit();

    List<ODocument> cresult = executeQuery("select * from Child");

    Assert.assertFalse(cresult.isEmpty());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    var loaded = database.<OElement>load(rid);

    list = loaded.getProperty("list");
    Assert.assertEquals(list.size(), 4);
    Assert.assertEquals(
        Objects.requireNonNull(list.get(0).<OElement>getRecord().getSchemaClass()).getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(list.get(1).<OElement>getRecord().getSchemaClass()).getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(list.get(2).<OElement>getRecord().getSchemaClass()).getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(list.get(3).<OElement>getRecord().getSchemaClass()).getName(),
        "Child");
    Assert.assertEquals(list.get(0).<OElement>getRecord().getProperty("name"), "Jack");
    Assert.assertEquals(list.get(1).<OElement>getRecord().getProperty("name"), "Bob");
    Assert.assertEquals(list.get(2).<OElement>getRecord().getProperty("name"), "Sam");
    Assert.assertEquals(list.get(3).<OElement>getRecord().getProperty("name"), "Dean");
  }

  @Test(dependsOnMethods = "mapObjectsLinkTest")
  public void listObjectsLinkTest() {
    database.begin();
    var hanSolo = database.newInstance("PersonTest");
    hanSolo.setProperty("firstName", "Han");
    hanSolo = database.save(hanSolo);
    database.commit();

    database.begin();
    var obiWan = database.newInstance("PersonTest");
    obiWan.setProperty("firstName", "Obi-Wan");
    obiWan = database.save(obiWan);

    var luke = database.newInstance("PersonTest");
    luke.setProperty("firstName", "Luke");
    luke = database.save(luke);
    database.commit();

    // ============================== step 1
    // add new information to luke
    database.begin();
    luke = database.bindToSession(luke);
    var friends = new HashSet<OIdentifiable>();
    friends.add(database.bindToSession(hanSolo));

    luke.setProperty("friends", friends);
    database.save(luke);
    database.commit();

    luke = database.bindToSession(luke);
    Assert.assertEquals(luke.<Set<OIdentifiable>>getProperty("friends").size(), 1);
    // ============================== end 1

    // ============================== step 2
    // add new information to luke
    friends = new HashSet<>();
    friends.add(obiWan);
    luke.setProperty("friends", friends);

    database.begin();
    database.save(database.bindToSession(luke));
    database.commit();

    Assert.assertEquals(luke.<Set<OIdentifiable>>getProperty("friends").size(), 1);
    // ============================== end 2
  }

  @Test(dependsOnMethods = "listObjectsLinkTest")
  public void listObjectsIterationTest() {
    var a = database.newInstance("Agenda");

    for (int i = 0; i < 10; i++) {
      a.setProperty("events", Collections.singletonList(database.newInstance("Event")));
    }
    database.begin();
    a = database.save(a);
    database.commit();
    ORID rid = a.getIdentity();

    database.close();

    database = createSessionInstance();
    database.begin();
    List<ODocument> agendas = executeQuery("SELECT FROM " + rid);
    OElement agenda = agendas.get(0);
    //noinspection unused,StatementWithEmptyBody
    for (var e : agenda.<List<OElement>>getProperty("events")) {
      // NO NEED TO DO ANYTHING, JUST NEED TO ITERATE THE LIST
    }

    agenda = database.save(agenda);
    database.commit();

    database.freeze(false);
    database.release();

    database.begin();
    agenda = database.bindToSession(agenda);
    try {
      for (int i = 0; i < agenda.<List<OElement>>getProperty("events").size(); i++) {
        @SuppressWarnings("unused")
        var e = agenda.<List<OElement>>getProperty("events").get(i);
        // NO NEED TO DO ANYTHING, JUST NEED TO ITERATE THE LIST
      }
    } catch (ConcurrentModificationException cme) {
      Assert.fail("Error iterating Object list", cme);
    }

    if (database.getTransaction().isActive()) {
      database.rollback();
    }
  }

  @Test(dependsOnMethods = "listObjectsIterationTest")
  public void mapObjectsListEmbeddedTest() {
    List<ODocument> cresult = executeQuery("select * from Child");

    int childSize = cresult.size();

    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    OElement c = database.newInstance("Child");
    c.setProperty("name", "John");

    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "Jack");

    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Bob");

    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Sam");

    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "Dean");

    var list = new ArrayList<OIdentifiable>();
    list.add(c1);
    list.add(c2);
    list.add(c3);
    list.add(c4);

    p.setProperty("embeddedList", list);
    database.begin();
    database.save(p);
    database.commit();

    cresult = executeQuery("select * from Child");

    Assert.assertEquals(childSize, cresult.size());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    Assert.assertEquals(loaded.<List<OElement>>getProperty("embeddedList").size(), 4);
    Assert.assertTrue(loaded.<List<OElement>>getProperty("embeddedList").get(0).isEmbedded());
    Assert.assertTrue(loaded.<List<OElement>>getProperty("embeddedList").get(1).isEmbedded());
    Assert.assertTrue(loaded.<List<OElement>>getProperty("embeddedList").get(2).isEmbedded());
    Assert.assertTrue(loaded.<List<OElement>>getProperty("embeddedList").get(3).isEmbedded());
    Assert.assertEquals(
        Objects.requireNonNull(
                loaded
                    .<List<OElement>>getProperty("embeddedList")
                    .get(0)
                    .<OElement>getRecord()
                    .getSchemaClass())
            .getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(
                loaded
                    .<List<OElement>>getProperty("embeddedList")
                    .get(1)
                    .<OElement>getRecord()
                    .getSchemaClass())
            .getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(
                loaded
                    .<List<OElement>>getProperty("embeddedList")
                    .get(2)
                    .getElement()
                    .getSchemaClass())
            .getName(),
        "Child");
    Assert.assertEquals(
        Objects.requireNonNull(
                loaded
                    .<List<OElement>>getProperty("embeddedList")
                    .get(3)
                    .getElement()
                    .getSchemaClass())
            .getName(),
        "Child");
    Assert.assertEquals(
        loaded.<List<OElement>>getProperty("embeddedList").get(0).getProperty("name"), "Jack");
    Assert.assertEquals(
        loaded.<List<OElement>>getProperty("embeddedList").get(1).getProperty("name"), "Bob");
    Assert.assertEquals(
        loaded.<List<OElement>>getProperty("embeddedList").get(2).getProperty("name"), "Sam");
    Assert.assertEquals(
        loaded.<List<OElement>>getProperty("embeddedList").get(3).getProperty("name"), "Dean");
  }

  @Test(dependsOnMethods = "mapObjectsListEmbeddedTest")
  public void mapObjectsSetEmbeddedTest() {
    List<ODocument> cresult = executeQuery("select * from Child");

    int childSize = cresult.size();

    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    OElement c = database.newInstance("Child");
    c.setProperty("name", "John");

    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "Jack");

    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Bob");

    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Sam");

    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "Dean");

    var embeddedSet = new HashSet<OElement>();
    embeddedSet.add(c);
    embeddedSet.add(c1);
    embeddedSet.add(c2);
    embeddedSet.add(c3);
    embeddedSet.add(c4);

    p.setProperty("embeddedSet", embeddedSet);

    database.begin();
    database.save(p);
    database.commit();

    cresult = executeQuery("select * from Child");

    Assert.assertEquals(childSize, cresult.size());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    Assert.assertEquals(loaded.<Set<OElement>>getProperty("embeddedSet").size(), 5);
    for (OElement loadedC : loaded.<Set<OElement>>getProperty("embeddedSet")) {
      Assert.assertTrue(loadedC.isEmbedded());
      Assert.assertEquals(loadedC.getClassName(), "Child");
      Assert.assertTrue(
          loadedC.<String>getProperty("name").equals("John")
              || loadedC.<String>getProperty("name").equals("Jack")
              || loadedC.<String>getProperty("name").equals("Bob")
              || loadedC.<String>getProperty("name").equals("Sam")
              || loadedC.<String>getProperty("name").equals("Dean"));
    }
  }

  @Test(dependsOnMethods = "mapObjectsSetEmbeddedTest")
  public void mapObjectsMapEmbeddedTest() {
    List<ODocument> cresult = executeQuery("select * from Child");

    int childSize = cresult.size();

    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    OElement c = database.newInstance("Child");
    c.setProperty("name", "John");

    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "Jack");

    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Bob");

    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Sam");

    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "Dean");

    var embeddedChildren = new HashMap<String, OElement>();
    embeddedChildren.put(c.getProperty("name"), c);
    embeddedChildren.put(c1.getProperty("name"), c1);
    embeddedChildren.put(c2.getProperty("name"), c2);
    embeddedChildren.put(c3.getProperty("name"), c3);
    embeddedChildren.put(c4.getProperty("name"), c4);

    p.setProperty("embeddedChildren", embeddedChildren);

    database.begin();
    database.save(p);
    database.commit();

    cresult = executeQuery("select * from Child");

    Assert.assertEquals(childSize, cresult.size());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    Assert.assertEquals(loaded.<Map<String, OElement>>getProperty("embeddedChildren").size(), 5);
    for (String key : loaded.<Map<String, OElement>>getProperty("embeddedChildren").keySet()) {
      OElement loadedC = loaded.<Map<String, OElement>>getProperty("embeddedChildren").get(key);
      Assert.assertTrue(loadedC.isEmbedded());
      Assert.assertEquals(loadedC.getClassName(), "Child");
      Assert.assertTrue(
          loadedC.<String>getProperty("name").equals("John")
              || loadedC.<String>getProperty("name").equals("Jack")
              || loadedC.<String>getProperty("name").equals("Bob")
              || loadedC.<String>getProperty("name").equals("Sam")
              || loadedC.<String>getProperty("name").equals("Dean"));
    }
  }

  @Test(dependsOnMethods = "mapObjectsLinkTest")
  public void mapObjectsNonExistingKeyTest() {
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    database.begin();
    p = database.save(p);

    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "John");

    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Jack");

    var children = new HashMap<String, OElement>();
    children.put("first", c1);
    children.put("second", c2);

    p.setProperty("children", children);

    database.save(p);
    database.commit();

    database.begin();
    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Olivia");
    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "Peter");

    p = database.bindToSession(p);
    p.<Map<String, OIdentifiable>>getProperty("children").put("third", c3);
    p.<Map<String, OIdentifiable>>getProperty("children").put("fourth", c4);

    database.save(p);
    database.commit();

    List<ODocument> cresult = executeQuery("select * from Child");

    Assert.assertFalse(cresult.isEmpty());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    c1 = database.bindToSession(c1);
    c2 = database.bindToSession(c2);
    c3 = database.bindToSession(c3);
    c4 = database.bindToSession(c4);

    OElement loaded = database.load(rid);

    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("first")
            .getElement()
            .getProperty("name"),
        c1.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("second")
            .getElement()
            .getProperty("name"),
        c2.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("third")
            .getElement()
            .getProperty("name"),
        c3.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("fourth")
            .getElement()
            .getProperty("name"),
        c4.<String>getProperty("name"));
    Assert.assertNull(loaded.<Map<String, OIdentifiable>>getProperty("children").get("fifth"));
  }

  @Test(dependsOnMethods = "mapObjectsLinkTest")
  public void mapObjectsLinkTwoSaveTest() {
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Silvester");

    database.begin();
    p = database.save(p);

    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "John");

    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Jack");

    var children = new HashMap<String, OIdentifiable>();
    children.put("first", c1);
    children.put("second", c2);

    p.setProperty("children", children);

    database.save(p);

    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Olivia");
    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "Peter");

    p.<Map<String, OIdentifiable>>getProperty("children").put("third", c3);
    p.<Map<String, OIdentifiable>>getProperty("children").put("fourth", c4);

    database.save(p);
    database.commit();

    List<ODocument> cresult = executeQuery("select * from Child");
    Assert.assertFalse(cresult.isEmpty());

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    c1 = database.bindToSession(c1);
    c2 = database.bindToSession(c2);
    c3 = database.bindToSession(c3);
    c4 = database.bindToSession(c4);

    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("first")
            .getElement()
            .getProperty("name"),
        c1.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("second")
            .getElement()
            .getProperty("name"),
        c2.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("third")
            .getElement()
            .getProperty("name"),
        c3.<String>getProperty("name"));
    Assert.assertEquals(
        loaded
            .<Map<String, OIdentifiable>>getProperty("children")
            .get("fourth")
            .getElement()
            .getProperty("name"),
        c4.<String>getProperty("name"));
  }

  @Test(dependsOnMethods = "mapObjectsLinkTest")
  public void mapObjectsLinkUpdateDatabaseNewInstanceTest() {
    // TEST WITH NEW INSTANCE
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Fringe");

    OElement c = database.newInstance("Child");
    c.setProperty("name", "Peter");
    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "Walter");
    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Olivia");
    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Astrid");

    Map<String, OIdentifiable> children = new HashMap<>();
    children.put(c.getProperty("name"), c);
    children.put(c1.getProperty("name"), c1);
    children.put(c2.getProperty("name"), c2);
    children.put(c3.getProperty("name"), c3);

    p.setProperty("children", children);

    database.begin();
    database.save(p);
    database.commit();

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    for (String key : loaded.<Map<String, OIdentifiable>>getProperty("children").keySet()) {
      Assert.assertTrue(
          key.equals("Peter")
              || key.equals("Walter")
              || key.equals("Olivia")
              || key.equals("Astrid"));
      Assert.assertEquals(
          loaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get(key)
              .getElement()
              .getClassName(),
          "Child");
      Assert.assertEquals(
          key,
          loaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get(key)
              .getElement()
              .getProperty("name"));
      switch (key) {
        case "Peter" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Peter");
        case "Walter" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Walter");
        case "Olivia" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Olivia");
        case "Astrid" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Astrid");
      }
    }

    for (OElement reloaded : database.browseClass("JavaComplexTestClass")) {
      database.begin();
      reloaded = database.bindToSession(reloaded);
      OElement c4 = database.newInstance("Child");
      c4.setProperty("name", "The Observer");

      children = reloaded.getProperty("children");
      if (children == null) {
        children = new HashMap<>();
        reloaded.setProperty("children", children);
      }

      children.put(c4.getProperty("name"), c4);

      database.save(reloaded);
      database.commit();
    }

    database.close();
    database = createSessionInstance();
    for (OElement reloaded : database.browseClass("JavaComplexTestClass")) {
      Assert.assertTrue(
          reloaded.<Map<String, OIdentifiable>>getProperty("children").containsKey("The Observer"));
      Assert.assertNotNull(
          reloaded.<Map<String, OIdentifiable>>getProperty("children").get("The Observer"));
      Assert.assertEquals(
          reloaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get("The Observer")
              .getElement()
              .getProperty("name"),
          "The Observer");
      Assert.assertTrue(
          reloaded
                  .<Map<String, OIdentifiable>>getProperty("children")
                  .get("The Observer")
                  .getIdentity()
                  .isPersistent()
              && reloaded
                  .<Map<String, OIdentifiable>>getProperty("children")
                  .get("The Observer")
                  .getIdentity()
                  .isValid());
    }
  }

  @Test(dependsOnMethods = "mapObjectsLinkUpdateDatabaseNewInstanceTest")
  public void mapObjectsLinkUpdateJavaNewInstanceTest() {
    // TEST WITH NEW INSTANCE
    database.begin();
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Fringe");

    OElement c = database.newInstance("Child");
    c.setProperty("name", "Peter");
    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "Walter");
    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "Olivia");
    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "Astrid");

    var children = new HashMap<String, OIdentifiable>();
    children.put(c.getProperty("name"), c);
    children.put(c1.getProperty("name"), c1);
    children.put(c2.getProperty("name"), c2);
    children.put(c3.getProperty("name"), c3);

    p.setProperty("children", children);

    p = database.save(p);
    database.commit();

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    for (String key : loaded.<Map<String, OIdentifiable>>getProperty("children").keySet()) {
      Assert.assertTrue(
          key.equals("Peter")
              || key.equals("Walter")
              || key.equals("Olivia")
              || key.equals("Astrid"));
      Assert.assertEquals(
          loaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get(key)
              .getElement()
              .getClassName(),
          "Child");
      Assert.assertEquals(
          key,
          loaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get(key)
              .getElement()
              .getProperty("name"));
      switch (key) {
        case "Peter" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Peter");
        case "Walter" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Walter");
        case "Olivia" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Olivia");
        case "Astrid" ->
            Assert.assertEquals(
                loaded
                    .<Map<String, OIdentifiable>>getProperty("children")
                    .get(key)
                    .getElement()
                    .getProperty("name"),
                "Astrid");
      }
    }

    for (OElement reloaded : database.browseClass("JavaComplexTestClass")) {
      OElement c4 = database.newInstance("Child");
      c4.setProperty("name", "The Observer");

      reloaded.<Map<String, OIdentifiable>>getProperty("children").put(c4.getProperty("name"), c4);

      database.begin();
      database.save(reloaded);
      database.commit();
    }
    database.close();
    database = createSessionInstance();
    for (OElement reloaded : database.browseClass("JavaComplexTestClass")) {
      Assert.assertTrue(
          reloaded.<Map<String, OIdentifiable>>getProperty("children").containsKey("The Observer"));
      Assert.assertNotNull(
          reloaded.<Map<String, OIdentifiable>>getProperty("children").get("The Observer"));
      Assert.assertEquals(
          reloaded
              .<Map<String, OIdentifiable>>getProperty("children")
              .get("The Observer")
              .getElement()
              .getProperty("name"),
          "The Observer");
      Assert.assertTrue(
          reloaded
                  .<Map<String, OIdentifiable>>getProperty("children")
                  .get("The Observer")
                  .getIdentity()
                  .isPersistent()
              && reloaded
                  .<Map<String, OIdentifiable>>getProperty("children")
                  .get("The Observer")
                  .getIdentity()
                  .isValid());
    }
  }

  @Test(dependsOnMethods = "mapObjectsLinkUpdateJavaNewInstanceTest")
  public void mapStringTest() {
    Map<String, String> relatives = new HashMap<>();
    relatives.put("father", "Mike");
    relatives.put("mother", "Julia");

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND HANDLER MANAGEMENT
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");

    var stringMap = new HashMap<String, String>();
    stringMap.put("father", "Mike");
    stringMap.put("mother", "Julia");

    p.setProperty("stringMap", stringMap);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    ORID rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    OElement loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    loaded.<Map<String, String>>getProperty("stringMap").put("brother", "Nike");
    relatives.put("brother", "Nike");

    database.begin();
    database.save(loaded);
    database.commit();

    loaded = database.bindToSession(loaded);
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND MAP DIRECT SET
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");
    p.setProperty("stringMap", relatives);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    loaded.<Map<String, String>>getProperty("stringMap").put("brother", "Nike");
    relatives.put("brother", "Nike");

    database.begin();
    database.save(loaded);
    database.commit();

    for (String referenceRelativ : relatives.keySet()) {
      loaded = database.bindToSession(loaded);
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH JAVA CONSTRUCTOR
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");
    p.setProperty("stringMap", relatives);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }

    database.begin();
    p = database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    loaded.<Map<String, String>>getProperty("stringMap").put("brother", "Nike");
    relatives.put("brother", "Nike");

    database.begin();
    database.save(loaded);
    database.commit();

    for (String referenceRelativ : relatives.keySet()) {
      loaded = database.bindToSession(loaded);
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, String>>getProperty("stringMap"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, String>>getProperty("stringMap").get(referenceRelativ));
    }
    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();
  }

  @Test(dependsOnMethods = "mapStringTest")
  public void setStringTest() {
    database.begin();
    OElement testClass = database.newInstance("JavaComplexTestClass");
    Set<String> roles = new HashSet<>();

    roles.add("manager");
    roles.add("developer");
    testClass.setProperty("stringSet", roles);

    OElement testClassProxy = database.save(testClass);
    database.commit();

    testClassProxy = database.bindToSession(testClassProxy);
    Assert.assertEquals(roles.size(), testClassProxy.<Set<String>>getProperty("stringSet").size());
    for (String referenceRole : roles) {
      testClassProxy = database.bindToSession(testClassProxy);
      Assert.assertTrue(
          testClassProxy.<Set<String>>getProperty("stringSet").contains(referenceRole));
    }

    ORID orid = testClassProxy.getIdentity();
    database.close();
    database = createSessionInstance();

    OElement loadedProxy = database.load(orid);
    Assert.assertEquals(roles.size(), loadedProxy.<Set<String>>getProperty("stringSet").size());
    for (String referenceRole : roles) {
      Assert.assertTrue(loadedProxy.<Set<String>>getProperty("stringSet").contains(referenceRole));
    }

    database.begin();
    database.save(database.bindToSession(loadedProxy));
    database.commit();

    database.begin();
    loadedProxy = database.bindToSession(loadedProxy);
    Assert.assertEquals(roles.size(), loadedProxy.<Set<String>>getProperty("stringSet").size());
    for (String referenceRole : roles) {
      Assert.assertTrue(loadedProxy.<Set<String>>getProperty("stringSet").contains(referenceRole));
    }

    loadedProxy.<Set<String>>getProperty("stringSet").remove("developer");
    roles.remove("developer");
    database.save(loadedProxy);
    database.commit();

    loadedProxy = database.bindToSession(loadedProxy);
    Assert.assertEquals(roles.size(), loadedProxy.<Set<String>>getProperty("stringSet").size());
    for (String referenceRole : roles) {
      Assert.assertTrue(loadedProxy.<Set<String>>getProperty("stringSet").contains(referenceRole));
    }
    database.close();
    database = createSessionInstance();

    loadedProxy = database.bindToSession(loadedProxy);
    Assert.assertEquals(roles.size(), loadedProxy.<Set<String>>getProperty("stringSet").size());
    for (String referenceRole : roles) {
      Assert.assertTrue(loadedProxy.<Set<String>>getProperty("stringSet").contains(referenceRole));
    }
  }

  @Test(dependsOnMethods = "setStringTest")
  public void mapStringListTest() {
    Map<String, List<String>> songAndMovies = new HashMap<>();
    List<String> movies = new ArrayList<>();
    List<String> songs = new ArrayList<>();

    movies.add("Star Wars");
    movies.add("Star Wars: The Empire Strikes Back");
    movies.add("Star Wars: The return of the Jedi");
    songs.add("Metallica - Master of Puppets");
    songs.add("Daft Punk - Harder, Better, Faster, Stronger");
    songs.add("Johnny Cash - Cocaine Blues");
    songs.add("Skrillex - Scary Monsters & Nice Sprites");
    songAndMovies.put("movies", movies);
    songAndMovies.put("songs", songs);

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND HANDLER MANAGEMENT
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");

    p.setProperty("stringListMap", songAndMovies);

    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          p.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    ORID rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    OElement loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, List<String>>>getProperty("stringListMap"));
    for (String reference : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(reference),
          loaded.<Map<String, List<String>>>getProperty("stringListMap").get(reference));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND MAP DIRECT SET
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");
    p.setProperty("stringListMap", songAndMovies);

    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          p.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, List<String>>>getProperty("stringListMap"));
    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          loaded.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH OBJECT DATABASE NEW INSTANCE LIST DIRECT ADD
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");

    var stringListMap = new HashMap<String, List<String>>();
    stringListMap.put("movies", new ArrayList<>());
    stringListMap.get("movies").add("Star Wars");
    stringListMap.get("movies").add("Star Wars: The Empire Strikes Back");
    stringListMap.get("movies").add("Star Wars: The return of the Jedi");

    stringListMap.put("songs", new ArrayList<>());
    stringListMap.get("songs").add("Metallica - Master of Puppets");
    stringListMap.get("songs").add("Daft Punk - Harder, Better, Faster, Stronger");
    stringListMap.get("songs").add("Johnny Cash - Cocaine Blues");
    stringListMap.get("songs").add("Skrillex - Scary Monsters & Nice Sprites");

    p.setProperty("stringListMap", stringListMap);

    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          p.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, List<String>>>getProperty("stringListMap"));
    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          loaded.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH JAVA CONSTRUCTOR
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");
    p.setProperty("stringListMap", songAndMovies);

    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          p.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    p = database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, List<String>>>getProperty("stringListMap"));
    for (String referenceRelativ : songAndMovies.keySet()) {
      Assert.assertEquals(
          songAndMovies.get(referenceRelativ),
          loaded.<Map<String, List<String>>>getProperty("stringListMap").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();
  }

  @Test
  public void embeddedMapObjectTest() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    Map<String, Object> relatives = new HashMap<>();
    relatives.put("father", "Mike");
    relatives.put("mother", "Julia");
    relatives.put("number", 10);
    relatives.put("date", cal.getTime());

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND HANDLER MANAGEMENT
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");

    var mapObject = new HashMap<String, Object>();
    mapObject.put("father", "Mike");
    mapObject.put("mother", "Julia");
    mapObject.put("number", 10);
    mapObject.put("date", cal.getTime());

    p.setProperty("mapObject", mapObject);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    ORID rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    OElement loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }
    loaded.<Map<String, Object>>getProperty("mapObject").put("brother", "Nike");
    relatives.put("brother", "Nike");

    database.begin();
    database.save(loaded);
    database.commit();

    for (String referenceRelativ : relatives.keySet()) {
      loaded = database.bindToSession(loaded);
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    // TEST WITH OBJECT DATABASE NEW INSTANCE AND MAP DIRECT SET
    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");

    p.setProperty("mapObject", relatives);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    loaded.<Map<String, Object>>getProperty("mapObject").put("brother", "Nike");
    relatives.put("brother", "Nike");

    database.begin();
    database.save(loaded);
    database.commit();

    for (String referenceRelativ : relatives.keySet()) {
      loaded = database.bindToSession(loaded);
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();

    p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Chuck");
    p.setProperty("mapObject", relatives);

    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          p.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    p = database.save(p);
    database.commit();

    rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);
    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }
    loaded.<Map<String, Object>>getProperty("mapObject").put("brother", "Nike");

    database.begin();
    relatives.put("brother", "Nike");
    database.save(loaded);
    database.commit();

    for (String referenceRelativ : relatives.keySet()) {
      loaded = database.bindToSession(loaded);
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }
    database.close();
    database = createSessionInstance();
    loaded = database.load(rid);

    Assert.assertNotNull(loaded.<Map<String, Object>>getProperty("mapObject"));
    for (String referenceRelativ : relatives.keySet()) {
      Assert.assertEquals(
          relatives.get(referenceRelativ),
          loaded.<Map<String, Object>>getProperty("mapObject").get(referenceRelativ));
    }

    database.begin();
    database.delete(database.bindToSession(loaded));
    database.commit();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test(dependsOnMethods = "embeddedMapObjectTest")
  public void testNoGenericCollections() {
    var p = database.newInstance("JavaNoGenericCollectionsTestClass");
    OElement c1 = database.newInstance("Child");
    c1.setProperty("name", "1");
    OElement c2 = database.newInstance("Child");
    c2.setProperty("name", "2");
    OElement c3 = database.newInstance("Child");
    c3.setProperty("name", "3");
    OElement c4 = database.newInstance("Child");
    c4.setProperty("name", "4");

    var list = new ArrayList();
    var set = new HashSet();
    var map = new HashMap();

    list.add(c1);
    list.add(c2);
    list.add(c3);
    list.add(c4);

    set.add(c1);
    set.add(c2);
    set.add(c3);
    set.add(c4);

    map.put("1", c1);
    map.put("2", c2);
    map.put("3", c3);
    map.put("4", c4);

    p.setProperty("list", list);
    p.setProperty("set", set);
    p.setProperty("map", map);

    database.begin();
    p = database.save(p);
    database.commit();

    ORID rid = p.getIdentity();
    database.close();
    database = createSessionInstance();
    p = database.load(rid);

    Assert.assertEquals(p.<List>getProperty("list").size(), 4);
    Assert.assertEquals(p.<Set>getProperty("set").size(), 4);
    Assert.assertEquals(p.<Map>getProperty("map").size(), 4);
    for (int i = 0; i < 4; i++) {
      Object o = p.<List>getProperty("list").get(i);
      Assert.assertTrue(o instanceof OElement);
      Assert.assertEquals(((OElement) o).getProperty("name"), (i + 1) + "");
      o = p.<Map>getProperty("map").get((i + 1) + "");
      Assert.assertTrue(o instanceof OElement);
      Assert.assertEquals(((OElement) o).getProperty("name"), (i + 1) + "");
    }
    for (Object o : p.<Set>getProperty("set")) {
      Assert.assertTrue(o instanceof OElement);
      int nameToInt = Integer.parseInt(((OElement) o).getProperty("name"));
      Assert.assertTrue(nameToInt > 0 && nameToInt < 5);
    }

    var other = database.newElement("JavaSimpleTestClass");
    p.<List>getProperty("list").add(other);
    p.<Set>getProperty("set").add(other);
    p.<Map>getProperty("map").put("5", other);

    database.begin();
    database.save(p);
    database.commit();

    database.close();
    database = createSessionInstance();
    p = database.load(rid);
    Assert.assertEquals(p.<List>getProperty("list").size(), 5);
    Object o = p.<List>getProperty("list").get(4);
    Assert.assertTrue(o instanceof OElement);
    o = p.<Map>getProperty("map").get("5");
    Assert.assertTrue(o instanceof OElement);
    boolean hasOther = false;
    for (Object obj : p.<Set>getProperty("set")) {
      hasOther = hasOther || (obj instanceof OElement);
    }
    Assert.assertTrue(hasOther);
  }

  public void oidentifableFieldsTest() {
    OElement p = database.newInstance("JavaComplexTestClass");
    p.setProperty("name", "Dean Winchester");

    ODocument testEmbeddedDocument = new ODocument();
    testEmbeddedDocument.field("testEmbeddedField", "testEmbeddedValue");

    p.setProperty("embeddedDocument", testEmbeddedDocument);

    ODocument testDocument = new ODocument();
    testDocument.field("testField", "testValue");

    database.begin();
    testDocument.save(database.getClusterNameById(database.getDefaultClusterId()));
    database.commit();

    testDocument = database.bindToSession(testDocument);
    p.setProperty("document", testDocument);

    OBlob testRecordBytes =
        new ORecordBytes(
            "this is a bytearray test. if you read this Object database has stored it correctly"
                .getBytes());

    p.setProperty("byteArray", testRecordBytes);

    database.begin();
    database.save(p);
    database.commit();

    ORID rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    OElement loaded = database.load(rid);

    Assert.assertNotNull(loaded.getBlobProperty("byteArray"));
    try {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        loaded.getBlobProperty("byteArray").toOutputStream(out);
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctly"
                .getBytes(),
            out.toByteArray());
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctly",
            out.toString());
      }
    } catch (IOException ioe) {
      Assert.fail();
      OLogManager.instance().error(this, "Error reading byte[]", ioe);
    }
    Assert.assertTrue(loaded.getElementProperty("document") instanceof ODocument);
    Assert.assertEquals(
        "testValue", loaded.getElementProperty("document").getProperty("testField"));
    Assert.assertTrue(loaded.getElementProperty("document").getIdentity().isPersistent());

    Assert.assertTrue(loaded.getElementProperty("embeddedDocument") instanceof ODocument);
    Assert.assertEquals(
        "testEmbeddedValue",
        loaded.getElementProperty("embeddedDocument").getProperty("testEmbeddedField"));
    Assert.assertFalse(loaded.getElementProperty("embeddedDocument").getIdentity().isValid());

    database.close();
    database = createSessionInstance();
    p = database.newInstance("JavaComplexTestClass");
    byte[] thumbnailImageBytes =
        "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
            .getBytes();
    OBlob oRecordBytes = new ORecordBytes(thumbnailImageBytes);

    database.begin();
    oRecordBytes.save();
    p.setProperty("byteArray", oRecordBytes);

    p = database.save(p);
    database.commit();

    p = database.bindToSession(p);
    Assert.assertNotNull(p.getBlobProperty("byteArray"));
    try {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        p.getBlobProperty("byteArray").toOutputStream(out);
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
                .getBytes(),
            out.toByteArray());
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it"
                + " correctlyVERSION2",
            out.toString());
      }
    } catch (IOException ioe) {
      Assert.fail();
      OLogManager.instance().error(this, "Error reading byte[]", ioe);
    }
    rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    loaded = database.load(rid);

    Assert.assertNotNull(loaded.getBlobProperty("byteArray"));
    try {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        loaded.getBlobProperty("byteArray").toOutputStream(out);
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
                .getBytes(),
            out.toByteArray());
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it"
                + " correctlyVERSION2",
            out.toString());
      }
    } catch (IOException ioe) {
      Assert.fail();
      OLogManager.instance().error(this, "Error reading byte[]", ioe);
    }
    database.close();
    database = createSessionInstance();

    database.begin();
    p = database.newInstance("JavaComplexTestClass");
    thumbnailImageBytes =
        "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
            .getBytes();

    oRecordBytes = new ORecordBytes(thumbnailImageBytes);
    oRecordBytes.save();
    p.setProperty("byteArray", oRecordBytes);

    p = database.save(p);
    database.commit();

    p = database.bindToSession(p);
    Assert.assertNotNull(p.getBlobProperty("byteArray"));
    try {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        p.getBlobProperty("byteArray").toOutputStream(out);
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
                .getBytes(),
            out.toByteArray());
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it"
                + " correctlyVERSION2",
            out.toString());
      }
    } catch (IOException ioe) {
      Assert.fail();
      OLogManager.instance().error(this, "Error reading byte[]", ioe);
    }
    rid = p.getIdentity();

    database.close();

    database = createSessionInstance();
    loaded = database.load(rid);

    loaded.getBlobProperty("byteArray");
    try {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        loaded.getBlobProperty("byteArray").toOutputStream(out);
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it correctlyVERSION2"
                .getBytes(),
            out.toByteArray());
        Assert.assertEquals(
            "this is a bytearray test. if you read this Object database has stored it"
                + " correctlyVERSION2",
            out.toString());
      }
    } catch (IOException ioe) {
      Assert.fail();
      OLogManager.instance().error(this, "Error reading byte[]", ioe);
    }
  }

  @Test
  public void testObjectDelete() {
    OElement media = database.newElement("Media");
    OBlob testRecord = database.newBlob("This is a test".getBytes());

    media.setProperty("content", testRecord);

    database.begin();
    media = database.save(media);
    database.commit();

    media = database.bindToSession(media);
    Assert.assertEquals(new String(media.getBlobProperty("content").toStream()), "This is a test");

    // try to delete
    database.begin();
    database.delete(database.bindToSession(media));
    database.commit();
  }

  @Test(dependsOnMethods = "mapEnumAndInternalObjects")
  public void update() {
    int i = 0;
    OElement a;
    for (var o : database.browseClass("Account")) {

      database.begin();
      a = database.bindToSession(o);
      if (i % 2 == 0) {
        var addresses = a.<List<OIdentifiable>>getProperty("addresses");
        var newAddress = database.newElement("Address");

        newAddress.setProperty("street", "Plaza central");
        newAddress.setProperty("type", "work");

        var city = database.newElement("City");
        city.setProperty("name", "Madrid");

        var country = database.newElement("Country");
        country.setProperty("name", "Spain");

        city.setProperty("country", country);
        newAddress.setProperty("city", city);

        addresses.add(0, newAddress);
      }

      a.setProperty("salary", (i + 500.10f));

      database.save(a);
      database.commit();

      i++;
    }
  }

  @Test(dependsOnMethods = "update")
  public void testUpdate() {
    int i = 0;
    OElement a;
    for (var iterator = database.browseClass("Account"); iterator.hasNext(); ) {
      iterator.setFetchPlan("*:1");
      a = iterator.next();

      if (i % 2 == 0) {
        Assert.assertEquals(
            a.<List<OIdentifiable>>getProperty("addresses")
                .get(0)
                .<OElement>getRecord()
                .<OIdentifiable>getProperty("city")
                .<OElement>getRecord()
                .<OElement>getRecord()
                .<OIdentifiable>getProperty("country")
                .<OElement>getRecord()
                .getProperty("name"),
            "Spain");
      } else {
        Assert.assertEquals(
            a.<List<OIdentifiable>>getProperty("addresses")
                .get(0)
                .<OElement>getRecord()
                .<OIdentifiable>getProperty("city")
                .<OElement>getRecord()
                .<OElement>getRecord()
                .<OIdentifiable>getProperty("country")
                .<OElement>getRecord()
                .getProperty("name"),
            "Italy");
      }

      Assert.assertEquals(a.<Float>getProperty("salary"), i + 500.1f);

      i++;
    }
  }

  @Test(dependsOnMethods = "testUpdate")
  public void checkLazyLoadingOff() {
    long profiles = database.countClass("Profile");

    database.begin();
    OElement neo = database.newElement("Profile");
    neo.setProperty("nick", "Neo");
    neo.setProperty("value", 1);

    var address = database.newElement("Address");
    address.setProperty("street", "Rio de Castilla");
    address.setProperty("type", "residence");

    var city = database.newElement("City");
    city.setProperty("name", "Madrid");

    var country = database.newElement("Country");
    country.setProperty("name", "Spain");

    city.setProperty("country", country);
    address.setProperty("city", city);

    var morpheus = database.newElement("Profile");
    morpheus.setProperty("nick", "Morpheus");

    var trinity = database.newElement("Profile");
    trinity.setProperty("nick", "Trinity");

    var followers = new HashSet<>();
    followers.add(trinity);
    followers.add(morpheus);

    neo.setProperty("followers", followers);
    neo.setProperty("location", address);

    database.save(neo);
    database.commit();

    Assert.assertEquals(database.countClass("Profile"), profiles + 3);

    for (OElement obj : database.browseClass("Profile")) {
      var followersList = obj.<Set<OIdentifiable>>getProperty("followers");
      Assert.assertTrue(followersList == null || followersList instanceof ORecordLazySet);
      if (obj.<String>getProperty("nick").equals("Neo")) {
        Assert.assertEquals(obj.<Set<OIdentifiable>>getProperty("followers").size(), 2);
        Assert.assertEquals(
            obj.<Set<OIdentifiable>>getProperty("followers")
                .iterator()
                .next()
                .getElement()
                .getClassName(),
            "Profile");
      } else if (obj.<String>getProperty("nick").equals("Morpheus")
          || obj.<String>getProperty("nick").equals("Trinity")) {
        Assert.assertNull(obj.<Set<OIdentifiable>>getProperty("followers"));
      }
    }
  }

  @Test(dependsOnMethods = "checkLazyLoadingOff")
  public void queryPerFloat() {
    final List<ODocument> result = executeQuery("select * from Account where salary = 500.10");

    Assert.assertFalse(result.isEmpty());

    OElement account;
    for (ODocument entries : result) {
      account = entries;
      Assert.assertEquals(account.<Float>getProperty("salary"), 500.10f);
    }
  }

  @Test(dependsOnMethods = "checkLazyLoadingOff")
  public void queryCross3Levels() {
    final List<ODocument> result =
        executeQuery("select from Profile where location.city.country.name = 'Spain'");

    Assert.assertFalse(result.isEmpty());

    OElement profile;
    for (ODocument entries : result) {
      profile = entries;
      Assert.assertEquals(
          profile
              .getElementProperty("location")
              .<OElement>getRecord()
              .<OIdentifiable>getProperty("city")
              .<OElement>getRecord()
              .<OElement>getRecord()
              .<OIdentifiable>getProperty("country")
              .<OElement>getRecord()
              .getProperty("name"),
          "Spain");
    }
  }

  @Test(dependsOnMethods = "queryCross3Levels")
  public void deleteFirst() {
    startRecordNumber = database.countClass("Account");

    // DELETE ALL THE RECORD IN THE CLASS
    for (OElement obj : database.browseClass("Account")) {
      database.begin();
      database.delete(database.bindToSession(obj));
      database.commit();
      break;
    }

    Assert.assertEquals(database.countClass("Account"), startRecordNumber - 1);
  }

  @Test
  public void commandWithPositionalParameters() {
    List<ODocument> result =
        executeQuery("select from Profile where name = ? and surname = ?", "Barack", "Obama");

    Assert.assertFalse(result.isEmpty());
  }

  @Test
  public void queryWithPositionalParameters() {
    List<ODocument> result =
        executeQuery("select from Profile where name = ? and surname = ?", "Barack", "Obama");

    Assert.assertFalse(result.isEmpty());
  }

  @Test
  public void queryWithRidAsParameters() {
    OElement profile = database.browseClass("Profile").next();
    List<ODocument> result =
        executeQuery("select from Profile where @rid = ?", profile.getIdentity());

    Assert.assertEquals(result.size(), 1);
  }

  @Test
  public void queryWithRidStringAsParameters() {
    OElement profile = database.browseClass("Profile").next();
    List<ODocument> result =
        executeQuery("select from Profile where @rid = ?", profile.getIdentity());

    Assert.assertEquals(result.size(), 1);
  }

  @Test
  public void commandWithNamedParameters() {
    addBarackObamaAndFollowers();

    HashMap<String, String> params = new HashMap<>();
    params.put("name", "Barack");
    params.put("surname", "Obama");

    List<ODocument> result =
        executeQuery("select from Profile where name = :name and surname = :surname", params);
    Assert.assertFalse(result.isEmpty());
  }

  @Test
  public void commandWithWrongNamedParameters() {
    try {
      HashMap<String, String> params = new HashMap<>();
      params.put("name", "Barack");
      params.put("surname", "Obama");

      executeQuery("select from Profile where name = :name and surname = :surname%", params);
      Assert.fail();
    } catch (OCommandSQLParsingException e) {
      Assert.assertTrue(true);
    }
  }

  @Test
  public void queryConcatAttrib() {
    Assert.assertFalse(executeQuery("select from City where country.@class = 'Country'").isEmpty());
    Assert.assertEquals(
        executeQuery("select from City where country.@class = 'Country22'").size(), 0);
  }

  @Test
  public void queryPreparedTwice() {
    try (var db = acquireSession()) {
      db.begin();

      HashMap<String, String> params = new HashMap<>();
      params.put("name", "Barack");
      params.put("surname", "Obama");

      List<OElement> result =
          db.query("select from Profile where name = :name and surname = :surname", params)
              .elementStream()
              .toList();
      Assert.assertFalse(result.isEmpty());

      result =
          db.query("select from Profile where name = :name and surname = :surname", params)
              .elementStream()
              .toList();
      Assert.assertFalse(result.isEmpty());
      db.commit();
    }
  }

  @Test(dependsOnMethods = "oidentifableFieldsTest")
  public void testEmbeddedDeletion() {
    database.begin();
    var parent = database.newInstance("Parent");
    parent.setProperty("name", "Big Parent");

    var embedded = database.newInstance("EmbeddedChild");
    embedded.setProperty("name", "Little Child");

    parent.setProperty("embeddedChild", embedded);

    parent = database.save(parent);

    List<ODocument> presult = executeQuery("select from Parent");
    List<ODocument> cresult = executeQuery("select from EmbeddedChild");
    Assert.assertEquals(presult.size(), 1);
    Assert.assertEquals(cresult.size(), 0);

    var child = database.newInstance("EmbeddedChild");
    child.setProperty("name", "Little Child");
    parent.setProperty("child", child);

    parent = database.save(parent);
    database.commit();

    presult = executeQuery("select from Parent");
    cresult = executeQuery("select from EmbeddedChild");
    Assert.assertEquals(presult.size(), 1);
    Assert.assertEquals(cresult.size(), 0);

    database.begin();
    database.delete(database.bindToSession(parent));
    database.commit();

    presult = executeQuery("select * from Parent");
    cresult = executeQuery("select * from EmbeddedChild");

    Assert.assertEquals(presult.size(), 0);
    Assert.assertEquals(cresult.size(), 0);
  }

  @Test(enabled = false, dependsOnMethods = "testCreate")
  public void testEmbeddedBinary() {
    OElement a = database.newElement("Account");
    a.setProperty("name", "Chris");
    a.setProperty("surname", "Martin");
    a.setProperty("id", 0);
    a.setProperty("thumbnail", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});

    a = database.save(a);
    database.commit();

    database.close();

    database = createSessionInstance();
    OElement aa = database.load(a.getIdentity());
    Assert.assertNotNull(a.getProperty("thumbnail"));
    Assert.assertNotNull(aa.getProperty("thumbnail"));
    byte[] b = aa.getProperty("thumbnail");
    for (int i = 0; i < 10; ++i) {
      Assert.assertEquals(b[i], i);
    }
  }

  @Test
  public void queryById() {
    List<ODocument> result1 = executeQuery("select from Profile limit 1");

    List<ODocument> result2 =
        executeQuery("select from Profile where @rid = ?", result1.get(0).getIdentity());

    Assert.assertFalse(result2.isEmpty());
  }

  @Test
  public void queryByIdNewApi() {
    database.begin();
    database.command("insert into Profile set nick = 'foo', name='foo'").close();
    database.commit();
    List<ODocument> result1 = executeQuery("select from Profile where nick = 'foo'");

    Assert.assertEquals(result1.size(), 1);
    Assert.assertEquals(result1.get(0).getClassName(), "Profile");
    OElement profile = result1.get(0);

    Assert.assertEquals(profile.getProperty("nick"), "foo");
  }

  @Test(dependsOnMethods = "testUpdate")
  public void testSaveMultiCircular() {
    database = createSessionInstance();
    try {
      startRecordNumber = database.countClusterElements("Profile");
      database.begin();
      var bObama = database.newInstance("Profile");
      bObama.setProperty("nick", "TheUSPresident");
      bObama.setProperty("name", "Barack");
      bObama.setProperty("surname", "Obama");

      var address = database.newInstance("Address");
      address.setProperty("type", "Residence");

      var city = database.newInstance("City");
      city.setProperty("name", "Washington");

      var country = database.newInstance("Country");
      country.setProperty("name", "USA");

      city.setProperty("country", country);
      address.setProperty("city", city);

      bObama.setProperty("location", address);

      var presidentSon1 = database.newInstance("Profile");
      presidentSon1.setProperty("nick", "PresidentSon10");
      presidentSon1.setProperty("name", "Malia Ann");
      presidentSon1.setProperty("surname", "Obama");
      presidentSon1.setProperty("invitedBy", bObama);

      var presidentSon2 = database.newInstance("Profile");
      presidentSon2.setProperty("nick", "PresidentSon20");
      presidentSon2.setProperty("name", "Natasha");
      presidentSon2.setProperty("surname", "Obama");
      presidentSon2.setProperty("invitedBy", bObama);

      var followers = new ArrayList<>();
      followers.add(presidentSon1);
      followers.add(presidentSon2);

      bObama.setProperty("followers", followers);

      database.save(bObama);
      database.commit();
    } finally {
      database.close();
    }
  }

  private void createSimpleArrayTestClass() {
    if (database.getSchema().existsClass("JavaSimpleArrayTestClass")) {
      database.getSchema().dropClass("JavaSimpleSimpleArrayTestClass");
    }

    var cls = database.createClass("JavaSimpleArrayTestClass");
    cls.createProperty("text", OType.EMBEDDEDLIST);
    cls.createProperty("numberSimple", OType.EMBEDDEDLIST);
    cls.createProperty("longSimple", OType.EMBEDDEDLIST);
    cls.createProperty("doubleSimple", OType.EMBEDDEDLIST);
    cls.createProperty("floatSimple", OType.EMBEDDEDLIST);
    cls.createProperty("byteSimple", OType.EMBEDDEDLIST);
    cls.createProperty("flagSimple", OType.EMBEDDEDLIST);
    cls.createProperty("dateField", OType.EMBEDDEDLIST);
  }

  private void createBinaryTestClass() {
    if (database.getSchema().existsClass("JavaBinaryTestClass")) {
      database.getSchema().dropClass("JavaBinaryTestClass");
    }

    var cls = database.createClass("JavaBinaryTestClass");
    cls.createProperty("binaryData", OType.BINARY);
  }

  private void createPersonClass() {
    if (database.getClass("PersonTest") == null) {
      var cls = database.createClass("PersonTest");
      cls.createProperty("firstname", OType.STRING);
      cls.createProperty("friends", OType.LINKSET);
    }
  }

  private void createEventClass() {
    if (database.getClass("Event") == null) {
      var cls = database.createClass("Event");
      cls.createProperty("name", OType.STRING);
      cls.createProperty("date", OType.DATE);
    }
  }

  private void createAgendaClass() {
    if (database.getClass("Agenda") == null) {
      var cls = database.createClass("Agenda");
      cls.createProperty("events", OType.EMBEDDEDLIST);
    }
  }

  private void createNonGenericClass() {
    if (database.getClass("JavaNoGenericCollectionsTestClass") == null) {
      var cls = database.createClass("JavaNoGenericCollectionsTestClass");
      cls.createProperty("list", OType.EMBEDDEDLIST);
      cls.createProperty("set", OType.EMBEDDEDSET);
      cls.createProperty("map", OType.EMBEDDEDMAP);
    }
  }

  private void createMediaClass() {
    if (database.getClass("Media") == null) {
      var cls = database.createClass("Media");
      cls.createProperty("content", OType.LINK);
      cls.createProperty("name", OType.STRING);
    }
  }

  private void createParentChildClasses() {
    if (database.getSchema().existsClass("Parent")) {
      database.getSchema().dropClass("Parent");
    }
    if (database.getSchema().existsClass("EmbeddedChild")) {
      database.getSchema().dropClass("EmbeddedChild");
    }

    var parentCls = database.createClass("Parent");
    parentCls.createProperty("name", OType.STRING);
    parentCls.createProperty("child", OType.EMBEDDED, database.getClass("EmbeddedChild"));
    parentCls.createProperty("embeddedChild", OType.EMBEDDED, database.getClass("EmbeddedChild"));

    var childCls = database.createClass("EmbeddedChild");
    childCls.createProperty("name", OType.STRING);
  }
}