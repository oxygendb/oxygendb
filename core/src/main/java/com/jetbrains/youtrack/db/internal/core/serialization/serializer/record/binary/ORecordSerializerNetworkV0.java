/*
 *
 *
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

package com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary;

import com.jetbrains.youtrack.db.internal.common.collection.OMultiValue;
import com.jetbrains.youtrack.db.internal.common.serialization.types.ODecimalSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OLongSerializer;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.record.LinkList;
import com.jetbrains.youtrack.db.internal.core.db.record.LinkMap;
import com.jetbrains.youtrack.db.internal.core.db.record.LinkSet;
import com.jetbrains.youtrack.db.internal.core.db.record.RecordElement;
import com.jetbrains.youtrack.db.internal.core.db.record.TrackedList;
import com.jetbrains.youtrack.db.internal.core.db.record.TrackedMap;
import com.jetbrains.youtrack.db.internal.core.db.record.TrackedSet;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.db.record.ridbag.RidBag;
import com.jetbrains.youtrack.db.internal.core.exception.YTRecordNotFoundException;
import com.jetbrains.youtrack.db.internal.core.exception.YTSerializationException;
import com.jetbrains.youtrack.db.internal.core.exception.YTStorageException;
import com.jetbrains.youtrack.db.internal.core.exception.YTValidationException;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.OGlobalProperty;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTImmutableSchema;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTProperty;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.metadata.security.PropertyEncryption;
import com.jetbrains.youtrack.db.internal.core.record.ORecordInternal;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityEntry;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImplEmbedded;
import com.jetbrains.youtrack.db.internal.core.record.impl.ODocumentInternal;
import com.jetbrains.youtrack.db.internal.core.serialization.ODocumentSerializable;
import com.jetbrains.youtrack.db.internal.core.serialization.OSerializableStream;
import com.jetbrains.youtrack.db.internal.core.util.ODateHelper;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

public class ORecordSerializerNetworkV0 implements ODocumentSerializer {

  private static final String CHARSET_UTF_8 = "UTF-8";
  private static final YTRecordId NULL_RECORD_ID = new YTRecordId(-2, YTRID.CLUSTER_POS_INVALID);
  private static final long MILLISEC_PER_DAY = 86400000;

  public ORecordSerializerNetworkV0() {
  }

  @Override
  public void deserializePartial(
      YTDatabaseSessionInternal db, final EntityImpl document, final BytesContainer bytes,
      final String[] iFields) {
    final String className = readString(bytes);
    if (className.length() != 0) {
      ODocumentInternal.fillClassNameIfNeeded(document, className);
    }

    // TRANSFORMS FIELDS FOM STRINGS TO BYTE[]
    final byte[][] fields = new byte[iFields.length][];
    for (int i = 0; i < iFields.length; ++i) {
      fields[i] = iFields[i].getBytes();
    }

    String fieldName = null;
    int valuePos;
    YTType type;
    int unmarshalledFields = 0;

    while (true) {
      final int len = OVarIntSerializer.readAsInteger(bytes);

      if (len == 0) {
        // SCAN COMPLETED
        break;
      } else if (len > 0) {
        // CHECK BY FIELD NAME SIZE: THIS AVOID EVEN THE UNMARSHALLING OF FIELD NAME
        boolean match = false;
        for (int i = 0; i < iFields.length; ++i) {
          if (iFields[i].length() == len) {
            boolean matchField = true;
            for (int j = 0; j < len; ++j) {
              if (bytes.bytes[bytes.offset + j] != fields[i][j]) {
                matchField = false;
                break;
              }
            }
            if (matchField) {
              fieldName = iFields[i];
              unmarshalledFields++;
              bytes.skip(len);
              match = true;
              break;
            }
          }
        }

        if (!match) {
          // SKIP IT
          bytes.skip(len + OIntegerSerializer.INT_SIZE + 1);
          continue;
        }
        valuePos = readInteger(bytes);
        type = readOType(bytes);
      } else {
        throw new YTStorageException("property id not supported in network serialization");
      }

      if (valuePos != 0) {
        int headerCursor = bytes.offset;
        bytes.offset = valuePos;
        final Object value = deserializeValue(db, bytes, type, document);
        bytes.offset = headerCursor;
        document.field(fieldName, value, type);
      } else {
        document.field(fieldName, null, (YTType[]) null);
      }

      if (unmarshalledFields == iFields.length)
      // ALL REQUESTED FIELDS UNMARSHALLED: EXIT
      {
        break;
      }
    }
  }

  @Override
  public void deserialize(YTDatabaseSessionInternal db, final EntityImpl document,
      final BytesContainer bytes) {
    final String className = readString(bytes);
    if (className.length() != 0) {
      ODocumentInternal.fillClassNameIfNeeded(document, className);
    }

    int last = 0;
    String fieldName;
    int valuePos;
    YTType type;
    while (true) {
      final int len = OVarIntSerializer.readAsInteger(bytes);
      if (len == 0) {
        // SCAN COMPLETED
        break;
      } else if (len > 0) {
        // PARSE FIELD NAME
        fieldName = stringFromBytes(bytes.bytes, bytes.offset, len).intern();
        bytes.skip(len);
        valuePos = readInteger(bytes);
        type = readOType(bytes);
      } else {
        throw new YTStorageException("property id not supported in network serialization");
      }

      if (ODocumentInternal.rawContainsField(document, fieldName)) {
        continue;
      }

      if (valuePos != 0) {
        int headerCursor = bytes.offset;
        bytes.offset = valuePos;
        final Object value = deserializeValue(db, bytes, type, document);
        if (bytes.offset > last) {
          last = bytes.offset;
        }
        bytes.offset = headerCursor;
        document.field(fieldName, value, type);
      } else {
        document.field(fieldName, null, (YTType[]) null);
      }
    }

    ORecordInternal.clearSource(document);

    if (last > bytes.offset) {
      bytes.offset = last;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void serialize(YTDatabaseSessionInternal session, final EntityImpl document,
      final BytesContainer bytes) {
    ORecordInternal.checkForBinding(document);
    YTImmutableSchema schema = ODocumentInternal.getImmutableSchema(document);
    PropertyEncryption encryption = ODocumentInternal.getPropertyEncryption(document);

    serializeClass(document, bytes);

    final List<Entry<String, EntityEntry>> fields = ODocumentInternal.filteredEntries(document);

    final int[] pos = new int[fields.size()];

    int i = 0;

    final Entry<String, EntityEntry>[] values = new Entry[fields.size()];
    for (Entry<String, EntityEntry> entry : fields) {
      EntityEntry docEntry = entry.getValue();
      if (!docEntry.exists()) {
        continue;
      }
      writeString(bytes, entry.getKey());
      pos[i] = bytes.alloc(OIntegerSerializer.INT_SIZE + 1);
      values[i] = entry;
      i++;
    }
    writeEmptyString(bytes);
    int size = i;

    for (i = 0; i < size; i++) {
      int pointer = 0;
      final Object value = values[i].getValue().value;
      if (value != null) {
        final YTType type = getFieldType(values[i].getValue());
        if (type == null) {
          throw new YTSerializationException(
              "Impossible serialize value of type "
                  + value.getClass()
                  + " with the EntityImpl binary serializer");
        }
        pointer =
            serializeValue(session,
                bytes,
                value,
                type,
                getLinkedType(document, type, values[i].getKey()),
                schema, encryption);
        OIntegerSerializer.INSTANCE.serializeLiteral(pointer, bytes.bytes, pos[i]);
        writeOType(bytes, (pos[i] + OIntegerSerializer.INT_SIZE), type);
      }
    }
  }

  @Override
  public String[] getFieldNames(EntityImpl reference, final BytesContainer bytes,
      boolean embedded) {
    // SKIP CLASS NAME
    final int classNameLen = OVarIntSerializer.readAsInteger(bytes);
    bytes.skip(classNameLen);

    final List<String> result = new ArrayList<String>();

    String fieldName;
    while (true) {
      OGlobalProperty prop = null;
      final int len = OVarIntSerializer.readAsInteger(bytes);
      if (len == 0) {
        // SCAN COMPLETED
        break;
      } else if (len > 0) {
        // PARSE FIELD NAME
        fieldName = stringFromBytes(bytes.bytes, bytes.offset, len).intern();
        result.add(fieldName);

        // SKIP THE REST
        bytes.skip(len + OIntegerSerializer.INT_SIZE + 1);
      } else {
        // LOAD GLOBAL PROPERTY BY ID
        final int id = (len * -1) - 1;
        prop = ODocumentInternal.getGlobalPropertyById(reference, id);
        result.add(prop.getName());

        // SKIP THE REST
        bytes.skip(OIntegerSerializer.INT_SIZE + (prop.getType() != YTType.ANY ? 0 : 1));
      }
    }

    return result.toArray(new String[result.size()]);
  }

  protected YTClass serializeClass(final EntityImpl document, final BytesContainer bytes) {
    final YTClass clazz = ODocumentInternal.getImmutableSchemaClass(document);
    String name = null;
    if (clazz != null) {
      name = clazz.getName();
    }
    if (name == null) {
      name = document.getClassName();
    }

    if (name != null) {
      writeString(bytes, name);
    } else {
      writeEmptyString(bytes);
    }
    return clazz;
  }

  protected YTType readOType(final BytesContainer bytes) {
    return YTType.getById(readByte(bytes));
  }

  private void writeOType(BytesContainer bytes, int pos, YTType type) {
    bytes.bytes[pos] = (byte) type.getId();
  }

  public Object deserializeValue(YTDatabaseSessionInternal session, BytesContainer bytes,
      YTType type,
      RecordElement owner) {
    Object value = null;
    switch (type) {
      case INTEGER:
        value = OVarIntSerializer.readAsInteger(bytes);
        break;
      case LONG:
        value = OVarIntSerializer.readAsLong(bytes);
        break;
      case SHORT:
        value = OVarIntSerializer.readAsShort(bytes);
        break;
      case STRING:
        value = readString(bytes);
        break;
      case DOUBLE:
        value = Double.longBitsToDouble(readLong(bytes));
        break;
      case FLOAT:
        value = Float.intBitsToFloat(readInteger(bytes));
        break;
      case BYTE:
        value = readByte(bytes);
        break;
      case BOOLEAN:
        value = readByte(bytes) == 1;
        break;
      case DATETIME:
        value = new Date(OVarIntSerializer.readAsLong(bytes));
        break;
      case DATE:
        long savedTime = OVarIntSerializer.readAsLong(bytes) * MILLISEC_PER_DAY;
        savedTime =
            convertDayToTimezone(
                TimeZone.getTimeZone("GMT"), ODateHelper.getDatabaseTimeZone(), savedTime);
        value = new Date(savedTime);
        break;
      case EMBEDDED:
        value = new EntityImplEmbedded();
        deserialize(session, (EntityImpl) value, bytes);
        if (((EntityImpl) value).containsField(ODocumentSerializable.CLASS_NAME)) {
          String className = ((EntityImpl) value).field(ODocumentSerializable.CLASS_NAME);
          try {
            Class<?> clazz = Class.forName(className);
            ODocumentSerializable newValue = (ODocumentSerializable) clazz.newInstance();
            newValue.fromDocument((EntityImpl) value);
            value = newValue;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        } else {
          ODocumentInternal.addOwner((EntityImpl) value, owner);
        }

        break;
      case EMBEDDEDSET:
        TrackedSet<Object> set = new TrackedSet<Object>(owner);
        value = readEmbeddedCollection(session, bytes, set, set);
        break;
      case EMBEDDEDLIST:
        TrackedList<Object> list = new TrackedList<Object>(owner);
        value = readEmbeddedCollection(session, bytes, list, list);
        break;
      case LINKSET:
        value = readLinkCollection(bytes, new LinkSet(owner));
        break;
      case LINKLIST:
        value = readLinkCollection(bytes, new LinkList(owner));
        break;
      case BINARY:
        value = readBinary(bytes);
        break;
      case LINK:
        value = readOptimizedLink(bytes);
        break;
      case LINKMAP:
        value = readLinkMap(session, bytes, owner);
        break;
      case EMBEDDEDMAP:
        value = readEmbeddedMap(session, bytes, owner);
        break;
      case DECIMAL:
        value = ODecimalSerializer.INSTANCE.deserialize(bytes.bytes, bytes.offset);
        bytes.skip(ODecimalSerializer.INSTANCE.getObjectSize(bytes.bytes, bytes.offset));
        break;
      case LINKBAG:
        RidBag bag = new RidBag(session);
        bag.fromStream(bytes);
        bag.setOwner(owner);
        value = bag;
        break;
      case TRANSIENT:
        break;
      case CUSTOM:
        try {
          String className = readString(bytes);
          Class<?> clazz = Class.forName(className);
          OSerializableStream stream = (OSerializableStream) clazz.newInstance();
          stream.fromStream(readBinary(bytes));
          if (stream instanceof OSerializableWrapper) {
            value = ((OSerializableWrapper) stream).getSerializable();
          } else {
            value = stream;
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        break;
      case ANY:
        break;
    }
    return value;
  }

  private byte[] readBinary(BytesContainer bytes) {
    int n = OVarIntSerializer.readAsInteger(bytes);
    byte[] newValue = new byte[n];
    System.arraycopy(bytes.bytes, bytes.offset, newValue, 0, newValue.length);
    bytes.skip(n);
    return newValue;
  }

  private Map<Object, YTIdentifiable> readLinkMap(
      YTDatabaseSessionInternal db, final BytesContainer bytes, final RecordElement owner) {
    int size = OVarIntSerializer.readAsInteger(bytes);
    LinkMap result = new LinkMap(owner);
    while ((size--) > 0) {
      YTType keyType = readOType(bytes);
      Object key = deserializeValue(db, bytes, keyType, result);
      YTRecordId value = readOptimizedLink(bytes);
      if (value.equals(NULL_RECORD_ID)) {
        result.put(key, null);
      } else {
        result.put(key, value);
      }
    }
    return result;
  }

  private Object readEmbeddedMap(YTDatabaseSessionInternal db, final BytesContainer bytes,
      final RecordElement owner) {
    int size = OVarIntSerializer.readAsInteger(bytes);
    final TrackedMap<Object> result = new TrackedMap<Object>(owner);
    int last = 0;
    while ((size--) > 0) {
      YTType keyType = readOType(bytes);
      Object key = deserializeValue(db, bytes, keyType, result);
      final int valuePos = readInteger(bytes);
      final YTType type = readOType(bytes);
      if (valuePos != 0) {
        int headerCursor = bytes.offset;
        bytes.offset = valuePos;
        Object value = deserializeValue(db, bytes, type, result);
        if (bytes.offset > last) {
          last = bytes.offset;
        }
        bytes.offset = headerCursor;
        result.put(key, value);
      } else {
        result.put(key, null);
      }
    }
    if (last > bytes.offset) {
      bytes.offset = last;
    }
    return result;
  }

  private Collection<YTIdentifiable> readLinkCollection(
      BytesContainer bytes, Collection<YTIdentifiable> found) {
    final int items = OVarIntSerializer.readAsInteger(bytes);
    for (int i = 0; i < items; i++) {
      YTRecordId id = readOptimizedLink(bytes);
      if (id.equals(NULL_RECORD_ID)) {
        found.add(null);
      } else {
        found.add(id);
      }
    }
    return found;
  }

  private YTRecordId readOptimizedLink(final BytesContainer bytes) {
    return new YTRecordId(
        OVarIntSerializer.readAsInteger(bytes), OVarIntSerializer.readAsLong(bytes));
  }

  private Collection<?> readEmbeddedCollection(
      YTDatabaseSessionInternal db, final BytesContainer bytes, final Collection<Object> found,
      final RecordElement owner) {
    final int items = OVarIntSerializer.readAsInteger(bytes);
    YTType type = readOType(bytes);

    if (type == YTType.ANY) {
      for (int i = 0; i < items; i++) {
        YTType itemType = readOType(bytes);
        if (itemType == YTType.ANY) {
          found.add(null);
        } else {
          found.add(deserializeValue(db, bytes, itemType, owner));
        }
      }
      return found;
    }
    // TODO: manage case where type is known
    return null;
  }

  private YTType getLinkedType(EntityImpl document, YTType type, String key) {
    if (type != YTType.EMBEDDEDLIST && type != YTType.EMBEDDEDSET && type != YTType.EMBEDDEDMAP) {
      return null;
    }
    YTClass immutableClass = ODocumentInternal.getImmutableSchemaClass(document);
    if (immutableClass != null) {
      YTProperty prop = immutableClass.getProperty(key);
      if (prop != null) {
        return prop.getLinkedType();
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public int serializeValue(
      YTDatabaseSessionInternal session, final BytesContainer bytes,
      Object value,
      final YTType type,
      final YTType linkedType,
      YTImmutableSchema schema,
      PropertyEncryption encryption) {
    int pointer = 0;
    switch (type) {
      case INTEGER:
      case LONG:
      case SHORT:
        pointer = OVarIntSerializer.write(bytes, ((Number) value).longValue());
        break;
      case STRING:
        pointer = writeString(bytes, value.toString());
        break;
      case DOUBLE:
        long dg = Double.doubleToLongBits((Double) value);
        pointer = bytes.alloc(OLongSerializer.LONG_SIZE);
        OLongSerializer.INSTANCE.serializeLiteral(dg, bytes.bytes, pointer);
        break;
      case FLOAT:
        int fg = Float.floatToIntBits((Float) value);
        pointer = bytes.alloc(OIntegerSerializer.INT_SIZE);
        OIntegerSerializer.INSTANCE.serializeLiteral(fg, bytes.bytes, pointer);
        break;
      case BYTE:
        pointer = bytes.alloc(1);
        bytes.bytes[pointer] = (Byte) value;
        break;
      case BOOLEAN:
        pointer = bytes.alloc(1);
        bytes.bytes[pointer] = ((Boolean) value) ? (byte) 1 : (byte) 0;
        break;
      case DATETIME:
        if (value instanceof Long) {
          pointer = OVarIntSerializer.write(bytes, (Long) value);
        } else {
          pointer = OVarIntSerializer.write(bytes, ((Date) value).getTime());
        }
        break;
      case DATE:
        long dateValue;
        if (value instanceof Long) {
          dateValue = (Long) value;
        } else {
          dateValue = ((Date) value).getTime();
        }
        dateValue =
            convertDayToTimezone(
                ODateHelper.getDatabaseTimeZone(), TimeZone.getTimeZone("GMT"), dateValue);
        pointer = OVarIntSerializer.write(bytes, dateValue / MILLISEC_PER_DAY);
        break;
      case EMBEDDED:
        pointer = bytes.offset;
        if (value instanceof ODocumentSerializable) {
          EntityImpl cur = ((ODocumentSerializable) value).toDocument();
          cur.field(ODocumentSerializable.CLASS_NAME, value.getClass().getName());
          serialize(session, cur, bytes);
        } else {
          serialize(session, (EntityImpl) value, bytes);
        }
        break;
      case EMBEDDEDSET:
      case EMBEDDEDLIST:
        if (value.getClass().isArray()) {
          pointer =
              writeEmbeddedCollection(session,
                  bytes, Arrays.asList(OMultiValue.array(value)), linkedType, schema, encryption);
        } else {
          pointer =
              writeEmbeddedCollection(session, bytes, (Collection<?>) value, linkedType, schema,
                  encryption);
        }
        break;
      case DECIMAL:
        BigDecimal decimalValue = (BigDecimal) value;
        pointer = bytes.alloc(ODecimalSerializer.INSTANCE.getObjectSize(decimalValue));
        ODecimalSerializer.INSTANCE.serialize(decimalValue, bytes.bytes, pointer);
        break;
      case BINARY:
        pointer = writeBinary(bytes, (byte[]) (value));
        break;
      case LINKSET:
      case LINKLIST:
        Collection<YTIdentifiable> ridCollection = (Collection<YTIdentifiable>) value;
        pointer = writeLinkCollection(bytes, ridCollection);
        break;
      case LINK:
        if (!(value instanceof YTIdentifiable)) {
          throw new YTValidationException("Value '" + value + "' is not a YTIdentifiable");
        }

        pointer = writeOptimizedLink(bytes, (YTIdentifiable) value);
        break;
      case LINKMAP:
        pointer = writeLinkMap(bytes, (Map<Object, YTIdentifiable>) value);
        break;
      case EMBEDDEDMAP:
        pointer = writeEmbeddedMap(session, bytes, (Map<Object, Object>) value, schema, encryption);
        break;
      case LINKBAG:
        pointer = ((RidBag) value).toStream(bytes);
        break;
      case CUSTOM:
        if (!(value instanceof OSerializableStream)) {
          value = new OSerializableWrapper((Serializable) value);
        }
        pointer = writeString(bytes, value.getClass().getName());
        writeBinary(bytes, ((OSerializableStream) value).toStream());
        break;
      case TRANSIENT:
        break;
      case ANY:
        break;
    }
    return pointer;
  }

  private int writeBinary(final BytesContainer bytes, final byte[] valueBytes) {
    final int pointer = OVarIntSerializer.write(bytes, valueBytes.length);
    final int start = bytes.alloc(valueBytes.length);
    System.arraycopy(valueBytes, 0, bytes.bytes, start, valueBytes.length);
    return pointer;
  }

  private int writeLinkMap(final BytesContainer bytes, final Map<Object, YTIdentifiable> map) {
    final int fullPos = OVarIntSerializer.write(bytes, map.size());
    for (Entry<Object, YTIdentifiable> entry : map.entrySet()) {
      // TODO:check skip of complex types
      // FIXME: changed to support only string key on map
      final YTType type = YTType.STRING;
      writeOType(bytes, bytes.alloc(1), type);
      writeString(bytes, entry.getKey().toString());
      if (entry.getValue() == null) {
        writeNullLink(bytes);
      } else {
        writeOptimizedLink(bytes, entry.getValue());
      }
    }
    return fullPos;
  }

  @SuppressWarnings("unchecked")
  private int writeEmbeddedMap(
      YTDatabaseSessionInternal session, BytesContainer bytes,
      Map<Object, Object> map,
      YTImmutableSchema schema,
      PropertyEncryption encryption) {
    final int[] pos = new int[map.size()];
    int i = 0;
    Entry<Object, Object>[] values = new Entry[map.size()];
    final int fullPos = OVarIntSerializer.write(bytes, map.size());
    for (Entry<Object, Object> entry : map.entrySet()) {
      // TODO:check skip of complex types
      // FIXME: changed to support only string key on map
      YTType type = YTType.STRING;
      writeOType(bytes, bytes.alloc(1), type);
      writeString(bytes, entry.getKey().toString());
      pos[i] = bytes.alloc(OIntegerSerializer.INT_SIZE + 1);
      values[i] = entry;
      i++;
    }

    for (i = 0; i < values.length; i++) {
      int pointer = 0;
      final Object value = values[i].getValue();
      if (value != null) {
        final YTType type = getTypeFromValueEmbedded(value);
        if (type == null) {
          throw new YTSerializationException(
              "Impossible serialize value of type "
                  + value.getClass()
                  + " with the EntityImpl binary serializer");
        }
        pointer = serializeValue(session, bytes, value, type, null, schema, encryption);
        OIntegerSerializer.INSTANCE.serializeLiteral(pointer, bytes.bytes, pos[i]);
        writeOType(bytes, (pos[i] + OIntegerSerializer.INT_SIZE), type);
      }
    }
    return fullPos;
  }

  private int writeNullLink(final BytesContainer bytes) {
    final int pos = OVarIntSerializer.write(bytes, NULL_RECORD_ID.getIdentity().getClusterId());
    OVarIntSerializer.write(bytes, NULL_RECORD_ID.getIdentity().getClusterPosition());
    return pos;
  }

  private static int writeOptimizedLink(final BytesContainer bytes, YTIdentifiable link) {
    if (!link.getIdentity().isPersistent()) {
      try {
        link = link.getRecord();
      } catch (YTRecordNotFoundException rnf) {
        // ignore it
      }
    }

    final int pos = OVarIntSerializer.write(bytes, link.getIdentity().getClusterId());
    OVarIntSerializer.write(bytes, link.getIdentity().getClusterPosition());
    return pos;
  }

  private int writeLinkCollection(
      final BytesContainer bytes, final Collection<YTIdentifiable> value) {
    final int pos = OVarIntSerializer.write(bytes, value.size());
    for (YTIdentifiable itemValue : value) {
      // TODO: handle the null links
      if (itemValue == null) {
        writeNullLink(bytes);
      } else {
        writeOptimizedLink(bytes, itemValue);
      }
    }

    return pos;
  }

  private int writeEmbeddedCollection(
      YTDatabaseSessionInternal session, final BytesContainer bytes,
      final Collection<?> value,
      final YTType linkedType,
      YTImmutableSchema schema,
      PropertyEncryption encryption) {
    final int pos = OVarIntSerializer.write(bytes, value.size());
    // TODO manage embedded type from schema and auto-determined.
    writeOType(bytes, bytes.alloc(1), YTType.ANY);
    for (Object itemValue : value) {
      // TODO:manage in a better way null entry
      if (itemValue == null) {
        writeOType(bytes, bytes.alloc(1), YTType.ANY);
        continue;
      }
      YTType type;
      if (linkedType == null) {
        type = getTypeFromValueEmbedded(itemValue);
      } else {
        type = linkedType;
      }
      if (type != null) {
        writeOType(bytes, bytes.alloc(1), type);
        serializeValue(session, bytes, itemValue, type, null, schema, encryption);
      } else {
        throw new YTSerializationException(
            "Impossible serialize value of type "
                + value.getClass()
                + " with the EntityImpl binary serializer");
      }
    }
    return pos;
  }

  private YTType getFieldType(final EntityEntry entry) {
    YTType type = entry.type;
    if (type == null) {
      final YTProperty prop = entry.property;
      if (prop != null) {
        type = prop.getType();
      }
    }
    if (type == null || YTType.ANY == type) {
      type = YTType.getTypeByValue(entry.value);
    }
    return type;
  }

  private YTType getTypeFromValueEmbedded(final Object fieldValue) {
    YTType type = YTType.getTypeByValue(fieldValue);
    if (type == YTType.LINK
        && fieldValue instanceof EntityImpl
        && !((EntityImpl) fieldValue).getIdentity().isValid()) {
      type = YTType.EMBEDDED;
    }
    return type;
  }

  protected String readString(final BytesContainer bytes) {
    final int len = OVarIntSerializer.readAsInteger(bytes);
    final String res = stringFromBytes(bytes.bytes, bytes.offset, len);
    bytes.skip(len);
    return res;
  }

  protected int readInteger(final BytesContainer container) {
    final int value =
        OIntegerSerializer.INSTANCE.deserializeLiteral(container.bytes, container.offset);
    container.offset += OIntegerSerializer.INT_SIZE;
    return value;
  }

  private byte readByte(final BytesContainer container) {
    return container.bytes[container.offset++];
  }

  private long readLong(final BytesContainer container) {
    final long value =
        OLongSerializer.INSTANCE.deserializeLiteral(container.bytes, container.offset);
    container.offset += OLongSerializer.LONG_SIZE;
    return value;
  }

  private int writeEmptyString(final BytesContainer bytes) {
    return OVarIntSerializer.write(bytes, 0);
  }

  private int writeString(final BytesContainer bytes, final String toWrite) {
    final byte[] nameBytes = bytesFromString(toWrite);
    final int pointer = OVarIntSerializer.write(bytes, nameBytes.length);
    final int start = bytes.alloc(nameBytes.length);
    System.arraycopy(nameBytes, 0, bytes.bytes, start, nameBytes.length);
    return pointer;
  }

  private byte[] bytesFromString(final String toWrite) {
    return toWrite.getBytes(StandardCharsets.UTF_8);
  }

  protected String stringFromBytes(final byte[] bytes, final int offset, final int len) {
    return new String(bytes, offset, len, StandardCharsets.UTF_8);
  }

  @Override
  public OBinaryField deserializeField(
      final BytesContainer bytes,
      final YTClass iClass,
      final String iFieldName,
      boolean embedded,
      YTImmutableSchema schema,
      PropertyEncryption encryption) {
    // TODO: check if integrate the binary disc binary comparator here
    throw new UnsupportedOperationException("network serializer doesn't support comparators");
  }

  @Override
  public OBinaryComparator getComparator() {
    // TODO: check if integrate the binary disc binary comparator here
    throw new UnsupportedOperationException("network serializer doesn't support comparators");
  }

  private long convertDayToTimezone(TimeZone from, TimeZone to, long time) {
    Calendar fromCalendar = Calendar.getInstance(from);
    fromCalendar.setTimeInMillis(time);
    Calendar toCalendar = Calendar.getInstance(to);
    toCalendar.setTimeInMillis(0);
    toCalendar.set(Calendar.ERA, fromCalendar.get(Calendar.ERA));
    toCalendar.set(Calendar.YEAR, fromCalendar.get(Calendar.YEAR));
    toCalendar.set(Calendar.MONTH, fromCalendar.get(Calendar.MONTH));
    toCalendar.set(Calendar.DAY_OF_MONTH, fromCalendar.get(Calendar.DAY_OF_MONTH));
    toCalendar.set(Calendar.HOUR_OF_DAY, 0);
    toCalendar.set(Calendar.MINUTE, 0);
    toCalendar.set(Calendar.SECOND, 0);
    toCalendar.set(Calendar.MILLISECOND, 0);
    return toCalendar.getTimeInMillis();
  }

  @Override
  public boolean isSerializingClassNameByDefault() {
    return true;
  }

  @Override
  public <RET> RET deserializeFieldTyped(
      YTDatabaseSessionInternal session, BytesContainer record,
      String iFieldName,
      boolean isEmbedded,
      YTImmutableSchema schema,
      PropertyEncryption encryption) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deserializeDebug(
      YTDatabaseSessionInternal db, BytesContainer bytes,
      ORecordSerializationDebug debugInfo,
      YTImmutableSchema schema) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose Tools | Templates.
  }
}