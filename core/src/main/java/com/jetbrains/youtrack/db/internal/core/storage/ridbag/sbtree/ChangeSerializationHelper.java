package com.jetbrains.youtrack.db.internal.core.storage.ridbag.sbtree;

import com.jetbrains.youtrack.db.internal.common.serialization.types.OBinarySerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OByteSerializer;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.exception.YTRecordNotFoundException;
import com.jetbrains.youtrack.db.internal.core.id.YTRecordId;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.binary.impl.OLinkSerializer;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class ChangeSerializationHelper {

  public static final ChangeSerializationHelper INSTANCE = new ChangeSerializationHelper();

  public static Change createChangeInstance(byte type, int value) {
    switch (type) {
      case AbsoluteChange.TYPE:
        return new AbsoluteChange(value);
      case DiffChange.TYPE:
        return new DiffChange(value);
      default:
        throw new IllegalArgumentException("Change type is incorrect");
    }
  }

  public Change deserializeChange(final byte[] stream, final int offset) {
    int value =
        OIntegerSerializer.INSTANCE.deserializeLiteral(stream, offset + OByteSerializer.BYTE_SIZE);
    return createChangeInstance(OByteSerializer.INSTANCE.deserializeLiteral(stream, offset), value);
  }

  public Map<YTIdentifiable, Change> deserializeChanges(final byte[] stream, int offset) {
    final int count = OIntegerSerializer.INSTANCE.deserializeLiteral(stream, offset);
    offset += OIntegerSerializer.INT_SIZE;

    final HashMap<YTIdentifiable, Change> res = new HashMap<>();
    for (int i = 0; i < count; i++) {
      YTRecordId rid = OLinkSerializer.INSTANCE.deserialize(stream, offset);
      offset += OLinkSerializer.RID_SIZE;
      Change change = ChangeSerializationHelper.INSTANCE.deserializeChange(stream, offset);
      offset += Change.SIZE;

      YTIdentifiable identifiable;
      try {
        if (rid.isTemporary()) {
          identifiable = rid.getRecord();
        } else {
          identifiable = rid;
        }
      } catch (YTRecordNotFoundException rnf) {
        identifiable = rid;
      }

      res.put(identifiable, change);
    }

    return res;
  }

  public <K extends YTIdentifiable> void serializeChanges(
      Map<K, Change> changes, OBinarySerializer<K> keySerializer, byte[] stream, int offset) {
    OIntegerSerializer.INSTANCE.serializeLiteral(changes.size(), stream, offset);
    offset += OIntegerSerializer.INT_SIZE;

    for (Map.Entry<K, Change> entry : changes.entrySet()) {
      K key = entry.getKey();

      if (key.getIdentity().isTemporary())
      //noinspection unchecked
      {
        key = key.getRecord();
      }

      keySerializer.serialize(key, stream, offset);
      offset += keySerializer.getObjectSize(key);

      offset += entry.getValue().serialize(stream, offset);
    }
  }

  public int getChangesSerializedSize(int changesCount) {
    return changesCount * (OLinkSerializer.RID_SIZE + Change.SIZE);
  }
}