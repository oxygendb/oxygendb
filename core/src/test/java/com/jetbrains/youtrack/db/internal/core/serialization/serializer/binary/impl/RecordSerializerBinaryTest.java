/*
 * Copyright 2018 YouTrackDB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.youtrack.db.internal.core.serialization.serializer.binary.impl;

import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDB;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfig;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.BytesContainer;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.ORecordSerializerBinary;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.OVarIntSerializer;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary.YTResultBinary;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 */
@RunWith(Parameterized.class)
public class RecordSerializerBinaryTest {

  private static YTDatabaseSession db = null;
  private static ORecordSerializerBinary serializer;
  private final int serializerVersion;
  private YouTrackDB odb;

  @Parameterized.Parameters
  public static Collection<Object[]> generateParams() {
    List<Object[]> params = new ArrayList<Object[]>();
    // first we want to run tests for all registreted serializers, and then for two network
    // serializers
    // testig for each serializer type has its own index
    for (byte i = 0; i < ORecordSerializerBinary.INSTANCE.getNumberOfSupportedVersions(); i++) {
      params.add(new Object[]{i});
    }
    return params;
  }

  public RecordSerializerBinaryTest(byte serializerIndex) {
    serializerVersion = serializerIndex;
  }

  @Before
  public void before() {
    odb = new YouTrackDB("memory:", YouTrackDBConfig.defaultConfig());
    odb.execute("create database test memory users ( admin identified by 'admin' role admin)");
    db = odb.open("test", "admin", "admin");
    db.createClass("TestClass");
    db.command("create property TestClass.TestEmbedded EMBEDDED").close();
    db.command("create property TestClass.TestPropAny ANY").close();
    serializer = new ORecordSerializerBinary((byte) serializerVersion);
  }

  @After
  public void after() {
    db.close();
    odb.drop("test");
    odb.close();
  }

  @Test
  public void testGetTypedPropertyOfTypeAny() {
    db.begin();
    EntityImpl doc = new EntityImpl("TestClass");
    Integer setValue = 15;
    doc.setProperty("TestPropAny", setValue);
    db.save(doc);
    db.commit();

    doc = db.bindToSession(doc);
    byte[] serializedDoc = serializer.toStream((YTDatabaseSessionInternal) db, doc);
    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, serializedDoc,
            new YTRecordId(-1, -1));
    Integer value = docBinary.getProperty("TestPropAny");
    Assert.assertEquals(setValue, value);
  }

  @Test
  public void testGetTypedFiledSimple() {
    EntityImpl doc = new EntityImpl();
    Integer setValue = 16;
    doc.setProperty("TestField", setValue);
    byte[] serializedDoc = serializer.toStream((YTDatabaseSessionInternal) db, doc);
    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, serializedDoc,
            new YTRecordId(-1, -1));
    Integer value = docBinary.getProperty("TestField");
    Assert.assertEquals(setValue, value);
  }

  protected static String stringFromBytes(final byte[] bytes, final int offset, final int len) {
    return new String(bytes, offset, len, StandardCharsets.UTF_8);
  }

  protected static String readString(final BytesContainer bytes) {
    final int len = OVarIntSerializer.readAsInteger(bytes);
    final String res = stringFromBytes(bytes.bytes, bytes.offset, len);
    bytes.skip(len);
    return res;
  }

  protected static int readInteger(final BytesContainer container) {
    final int value =
        OIntegerSerializer.INSTANCE.deserializeLiteral(container.bytes, container.offset);
    container.offset += OIntegerSerializer.INT_SIZE;
    return value;
  }

  @Test
  public void testGetFieldNamesFromEmbedded() {
    db.begin();
    EntityImpl root = new EntityImpl();
    EntityImpl embedded = new EntityImpl("TestClass");
    Integer setValue = 17;
    embedded.setProperty("TestField", setValue);
    embedded.setProperty("TestField2", "TestValue");

    root.field("TestEmbedded", embedded);
    root.setClassName("TestClass");
    db.save(root);
    db.commit();

    root = db.bindToSession(root);
    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);
    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    YTResultBinary embeddedBytesViaGet = docBinary.getProperty("TestEmbedded");
    Set<String> fieldNames = embeddedBytesViaGet.getPropertyNames();
    Assert.assertTrue(fieldNames.contains("TestField"));
    Assert.assertTrue(fieldNames.contains("TestField2"));

    Object propVal = embeddedBytesViaGet.getProperty("TestField");
    Assert.assertEquals(setValue, propVal);
  }

  @Test
  public void testGetTypedFieldEmbedded() {
    db.begin();
    EntityImpl root = new EntityImpl();
    EntityImpl embedded = new EntityImpl("TestClass");
    Integer setValue = 17;
    embedded.setProperty("TestField", setValue);

    root.field("TestEmbedded", embedded);
    root.setClassName("TestClass");

    db.save(root);
    db.commit();

    root = db.bindToSession(root);
    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);
    embedded = root.field("TestEmbedded");
    byte[] embeddedNativeBytes = serializer.toStream((YTDatabaseSessionInternal) db, embedded);
    // want to update data pointers because first byte will be removed
    decreasePositionsBy(embeddedNativeBytes, 1, false);
    // skip serializer version
    embeddedNativeBytes = Arrays.copyOfRange(embeddedNativeBytes, 1, embeddedNativeBytes.length);
    YTResultBinary resBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    YTResultBinary embeddedBytesViaGet = resBinary.getProperty("TestEmbedded");
    byte[] deserializedBytes =
        Arrays.copyOfRange(
            embeddedBytesViaGet.getBytes(),
            embeddedBytesViaGet.getOffset(),
            embeddedBytesViaGet.getOffset() + embeddedBytesViaGet.getFieldLength());
    BytesContainer container = new BytesContainer(deserializedBytes);
    // if by default serializer doesn't store class name then original
    // value embeddedNativeBytes will not have class name in byes so we want to skip them
    if (!serializer.getCurrentSerializer().isSerializingClassNameByDefault()) {
      int len = OVarIntSerializer.readAsInteger(container);
      container.skip(len);
    }
    decreasePositionsBy(
        deserializedBytes, container.offset + embeddedBytesViaGet.getOffset(), true);
    deserializedBytes =
        Arrays.copyOfRange(deserializedBytes, container.offset, deserializedBytes.length);
    Assert.assertArrayEquals(embeddedNativeBytes, deserializedBytes);
  }

  @Test
  public void testGetTypedFieldFromEmbedded() {
    db.begin();
    EntityImpl root = new EntityImpl();
    EntityImpl embedded = new EntityImpl("TestClass");
    Integer setValue = 17;
    embedded.setProperty("TestField", setValue);

    root.field("TestEmbedded", embedded);
    root.setClassName("TestClass");

    db.save(root);
    db.commit();

    root = db.bindToSession(root);
    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);

    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    YTResultBinary embeddedBytesViaGet = docBinary.getProperty("TestEmbedded");

    Integer testValue = embeddedBytesViaGet.getProperty("TestField");

    Assert.assertEquals(setValue, testValue);
  }

  @Test
  public void testGetTypedEmbeddedFromEmbedded() {
    db.begin();
    EntityImpl root = new EntityImpl("TestClass");
    EntityImpl embedded = new EntityImpl("TestClass");
    EntityImpl embeddedLevel2 = new EntityImpl("TestClass");
    Integer setValue = 17;
    embeddedLevel2.setProperty("InnerTestFields", setValue);
    embedded.setProperty("TestEmbedded", embeddedLevel2);

    root.field("TestEmbedded", embedded);

    db.save(root);
    db.commit();

    root = db.bindToSession(root);
    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);
    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    YTResultBinary embeddedBytesViaGet = docBinary.getProperty("TestEmbedded");
    YTResultBinary embeddedLKevel2BytesViaGet = embeddedBytesViaGet.getProperty("TestEmbedded");
    Integer testValue = embeddedLKevel2BytesViaGet.getProperty("InnerTestFields");
    Assert.assertEquals(setValue, testValue);
  }

  @Test
  public void testGetFieldFromEmbeddedList() {
    EntityImpl root = new EntityImpl();
    EntityImpl embeddedListElement = new EntityImpl();
    Integer setValue = 19;
    Integer setValue2 = 21;
    embeddedListElement.field("InnerTestFields", setValue);

    byte[] rawElementBytes = serializer.toStream((YTDatabaseSessionInternal) db,
        embeddedListElement);

    List embeddedList = new ArrayList();
    embeddedList.add(embeddedListElement);
    embeddedList.add(setValue2);

    root.field("TestEmbeddedList", embeddedList, YTType.EMBEDDEDLIST);

    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);
    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    List<Object> embeddedListFieldValue = docBinary.getProperty("TestEmbeddedList");
    YTResultBinary embeddedListElementBytes = (YTResultBinary) embeddedListFieldValue.get(0);
    Integer deserializedValue = embeddedListElementBytes.getProperty("InnerTestFields");
    Assert.assertEquals(setValue, deserializedValue);

    Integer secondtestVal = (Integer) embeddedListFieldValue.get(1);
    Assert.assertEquals(setValue2, secondtestVal);
  }

  @Test
  public void testGetFieldFromEmbeddedMap() {
    EntityImpl root = new EntityImpl();
    Integer setValue = 23;
    Integer setValue2 = 27;
    Map<String, Object> map = new HashMap<>();
    EntityImpl embeddedListElement = new EntityImpl();
    embeddedListElement.field("InnerTestFields", setValue);
    map.put("first", embeddedListElement);
    map.put("second", setValue2);
    map.put("fake", setValue2);
    map.put("mock", setValue2);
    map.put("embed", "Super Embedded field numbe");
    map.put("nullValue", null);

    root.field("TestEmbeddedMap", map, YTType.EMBEDDEDMAP);
    byte[] rootBytes = serializer.toStream((YTDatabaseSessionInternal) db, root);

    YTResultBinary docBinary =
        (YTResultBinary) serializer.getBinaryResult((YTDatabaseSessionInternal) db, rootBytes,
            new YTRecordId(-1, -1));
    Map deserializedMap = docBinary.getProperty("TestEmbeddedMap");
    YTResultBinary firstValDeserialized = (YTResultBinary) deserializedMap.get("first");
    Integer deserializedValue = firstValDeserialized.getProperty("InnerTestFields");
    Assert.assertEquals(setValue, deserializedValue);

    Integer secondDeserializedValue = (Integer) deserializedMap.get("second");
    Assert.assertEquals(setValue2, secondDeserializedValue);

    Assert.assertTrue(deserializedMap.containsKey("nullValue"));
    Assert.assertNull(deserializedMap.get("nullValue"));
  }

  private void decreasePositionsBy(byte[] recordBytes, int stepSize, boolean isNested) {
    if (serializerVersion > 0) {
      return;
    }

    BytesContainer container = new BytesContainer(recordBytes);
    // for root elements skip serializer version
    if (!isNested) {
      container.offset++;
    }
    if (serializer.getCurrentSerializer().isSerializingClassNameByDefault() || isNested) {
      readString(container);
    }
    int len = 1;
    while (len != 0) {
      len = OVarIntSerializer.readAsInteger(container);
      if (len > 0) {
        // read field name
        container.offset += len;

        // read data pointer
        int pointer = readInteger(container);
        // shift pointer by start ofset
        pointer -= stepSize;
        // write to byte container
        OIntegerSerializer.INSTANCE.serializeLiteral(
            pointer, container.bytes, container.offset - OIntegerSerializer.INT_SIZE);
        // read type
        container.offset++;
      } else if (len < 0) {
        // rtead data pointer
        int pointer = readInteger(container);
        // shift pointer
        pointer -= stepSize;
        // write to byte container
        OIntegerSerializer.INSTANCE.serializeLiteral(
            pointer, container.bytes, container.offset - OIntegerSerializer.INT_SIZE);
      }
    }
  }
}