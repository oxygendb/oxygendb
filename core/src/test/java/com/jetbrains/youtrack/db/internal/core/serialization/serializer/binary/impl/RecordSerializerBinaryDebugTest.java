package com.jetbrains.youtrack.db.internal.core.serialization.serializer.binary.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.core.db.document.YTDatabaseSessionAbstract;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.ORecordSerializer;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.ORecordSerializationDebug;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.ORecordSerializerBinary;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.ORecordSerializerBinaryDebug;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RecordSerializerBinaryDebugTest extends DBTestBase {

  private ORecordSerializer previous;

  @Before
  public void before() {
    previous = YTDatabaseSessionAbstract.getDefaultSerializer();
    YTDatabaseSessionAbstract.setDefaultSerializer(new ORecordSerializerBinary());
  }

  @After
  public void after() {
    YTDatabaseSessionAbstract.setDefaultSerializer(previous);
  }

  @Test
  public void testSimpleDocumentDebug() {

    EntityImpl doc = new EntityImpl();
    doc.field("test", "test");
    doc.field("anInt", 2);
    doc.field("anDouble", 2D);

    byte[] bytes = doc.toStream();

    ORecordSerializerBinaryDebug debugger = new ORecordSerializerBinaryDebug();
    ORecordSerializationDebug debug = debugger.deserializeDebug(bytes, db);

    assertEquals(debug.properties.size(), 3);
    assertEquals(debug.properties.get(0).name, "test");
    assertEquals(debug.properties.get(0).type, YTType.STRING);
    assertEquals(debug.properties.get(0).value, "test");

    assertEquals(debug.properties.get(1).name, "anInt");
    assertEquals(debug.properties.get(1).type, YTType.INTEGER);
    assertEquals(debug.properties.get(1).value, 2);

    assertEquals(debug.properties.get(2).name, "anDouble");
    assertEquals(debug.properties.get(2).type, YTType.DOUBLE);
    assertEquals(debug.properties.get(2).value, 2D);
  }

  @Test
  public void testSchemaFullDocumentDebug() {
    YTClass clazz = db.getMetadata().getSchema().createClass("some");
    clazz.createProperty(db, "testP", YTType.STRING);
    clazz.createProperty(db, "theInt", YTType.INTEGER);
    EntityImpl doc = new EntityImpl("some");
    doc.field("testP", "test");
    doc.field("theInt", 2);
    doc.field("anDouble", 2D);

    byte[] bytes = doc.toStream();

    ORecordSerializerBinaryDebug debugger = new ORecordSerializerBinaryDebug();
    ORecordSerializationDebug debug = debugger.deserializeDebug(bytes, db);

    assertEquals(debug.properties.size(), 3);
    assertEquals(debug.properties.get(0).name, "testP");
    assertEquals(debug.properties.get(0).type, YTType.STRING);
    assertEquals(debug.properties.get(0).value, "test");

    assertEquals(debug.properties.get(1).name, "theInt");
    assertEquals(debug.properties.get(1).type, YTType.INTEGER);
    assertEquals(debug.properties.get(1).value, 2);

    assertEquals(debug.properties.get(2).name, "anDouble");
    assertEquals(debug.properties.get(2).type, YTType.DOUBLE);
    assertEquals(debug.properties.get(2).value, 2D);
  }

  @Test
  public void testSimpleBrokenDocumentDebug() {
    EntityImpl doc = new EntityImpl();
    doc.field("test", "test");
    doc.field("anInt", 2);
    doc.field("anDouble", 2D);

    byte[] bytes = doc.toStream();
    byte[] brokenBytes = new byte[bytes.length - 10];
    System.arraycopy(bytes, 0, brokenBytes, 0, bytes.length - 10);

    ORecordSerializerBinaryDebug debugger = new ORecordSerializerBinaryDebug();
    ORecordSerializationDebug debug = debugger.deserializeDebug(brokenBytes, db);

    assertEquals(debug.properties.size(), 3);
    assertEquals(debug.properties.get(0).name, "test");
    assertEquals(debug.properties.get(0).type, YTType.STRING);
    assertTrue(debug.properties.get(0).faildToRead);
    assertNotNull(debug.properties.get(0).readingException);

    assertEquals(debug.properties.get(1).name, "anInt");
    assertEquals(debug.properties.get(1).type, YTType.INTEGER);
    assertTrue(debug.properties.get(1).faildToRead);
    assertNotNull(debug.properties.get(1).readingException);

    assertEquals(debug.properties.get(2).name, "anDouble");
    assertEquals(debug.properties.get(2).type, YTType.DOUBLE);
    assertTrue(debug.properties.get(2).faildToRead);
    assertNotNull(debug.properties.get(2).readingException);
  }

  @Test
  public void testBrokenSchemaFullDocumentDebug() {
    YTClass clazz = db.getMetadata().getSchema().createClass("some");
    clazz.createProperty(db, "testP", YTType.STRING);
    clazz.createProperty(db, "theInt", YTType.INTEGER);
    EntityImpl doc = new EntityImpl("some");
    doc.field("testP", "test");
    doc.field("theInt", 2);
    doc.field("anDouble", 2D);

    byte[] bytes = doc.toStream();
    byte[] brokenBytes = new byte[bytes.length - 10];
    System.arraycopy(bytes, 0, brokenBytes, 0, bytes.length - 10);

    ORecordSerializerBinaryDebug debugger = new ORecordSerializerBinaryDebug();
    ORecordSerializationDebug debug = debugger.deserializeDebug(brokenBytes, db);

    assertEquals(debug.properties.size(), 3);
    assertEquals(debug.properties.get(0).name, "testP");
    assertEquals(debug.properties.get(0).type, YTType.STRING);
    assertTrue(debug.properties.get(0).faildToRead);
    assertNotNull(debug.properties.get(0).readingException);

    assertEquals(debug.properties.get(1).name, "theInt");
    assertEquals(debug.properties.get(1).type, YTType.INTEGER);
    assertTrue(debug.properties.get(1).faildToRead);
    assertNotNull(debug.properties.get(1).readingException);

    assertEquals(debug.properties.get(2).name, "anDouble");
    assertEquals(debug.properties.get(2).type, YTType.DOUBLE);
    assertTrue(debug.properties.get(2).faildToRead);
    assertNotNull(debug.properties.get(2).readingException);
  }
}