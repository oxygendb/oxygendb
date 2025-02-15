package com.jetbrains.youtrack.db.internal.core.storage.index.sbtreebonsai.global.btree;

import com.jetbrains.youtrack.db.api.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.WALChanges;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.WALPageChangesPortion;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Assert;
import org.junit.Test;

public class EdgeKeySerializerTest {

  @Test
  public void testSerialization() {
    final EdgeKey edgeKey = new EdgeKey(42, 24, 67);
    final EdgeKeySerializer edgeKeySerializer = new EdgeKeySerializer();

    final int serializedSize = edgeKeySerializer.getObjectSize(edgeKey);
    final byte[] rawKey = new byte[serializedSize + 3];

    edgeKeySerializer.serialize(edgeKey, rawKey, 3);

    Assert.assertEquals(serializedSize, edgeKeySerializer.getObjectSize(rawKey, 3));

    final EdgeKey deserializedKey = edgeKeySerializer.deserialize(rawKey, 3);

    Assert.assertEquals(edgeKey, deserializedKey);
  }

  @Test
  public void testBufferSerialization() {
    final EdgeKey edgeKey = new EdgeKey(42, 24, 67);
    final EdgeKeySerializer edgeKeySerializer = new EdgeKeySerializer();

    final int serializedSize = edgeKeySerializer.getObjectSize(edgeKey);
    final ByteBuffer buffer = ByteBuffer.allocate(serializedSize + 3);

    buffer.position(3);
    edgeKeySerializer.serializeInByteBufferObject(edgeKey, buffer);

    Assert.assertEquals(3 + serializedSize, buffer.position());

    buffer.position(3);
    Assert.assertEquals(serializedSize, edgeKeySerializer.getObjectSizeInByteBuffer(buffer));

    buffer.position(3);
    final EdgeKey deserializedKey = edgeKeySerializer.deserializeFromByteBufferObject(buffer);

    Assert.assertEquals(edgeKey, deserializedKey);
  }

  @Test
  public void testImmutableBufferPositionSerialization() {
    final EdgeKey edgeKey = new EdgeKey(42, 24, 67);
    final EdgeKeySerializer edgeKeySerializer = new EdgeKeySerializer();

    final int serializedSize = edgeKeySerializer.getObjectSize(edgeKey);
    final ByteBuffer buffer = ByteBuffer.allocate(serializedSize + 3);

    buffer.position(3);
    edgeKeySerializer.serializeInByteBufferObject(edgeKey, buffer);

    Assert.assertEquals(3 + serializedSize, buffer.position());

    buffer.position(0);
    Assert.assertEquals(serializedSize, edgeKeySerializer.getObjectSizeInByteBuffer(3, buffer));

    Assert.assertEquals(0, buffer.position());

    final EdgeKey deserializedKey = edgeKeySerializer.deserializeFromByteBufferObject(3, buffer);
    Assert.assertEquals(0, buffer.position());

    Assert.assertEquals(edgeKey, deserializedKey);
  }

  @Test
  public void testChangesSerialization() {
    final EdgeKey edgeKey = new EdgeKey(42, 24, 67);
    final EdgeKeySerializer edgeKeySerializer = new EdgeKeySerializer();

    final int serializedSize = edgeKeySerializer.getObjectSize(edgeKey);

    final WALChanges walChanges = new WALPageChangesPortion();
    final ByteBuffer buffer =
        ByteBuffer.allocate(GlobalConfiguration.DISK_CACHE_PAGE_SIZE.getValueAsInteger() * 1024)
            .order(ByteOrder.nativeOrder());

    final byte[] rawKey = new byte[serializedSize];

    edgeKeySerializer.serialize(edgeKey, rawKey, 0);
    walChanges.setBinaryValue(buffer, rawKey, 3);

    Assert.assertEquals(
        serializedSize, edgeKeySerializer.getObjectSizeInByteBuffer(buffer, walChanges, 3));

    final EdgeKey deserializedKey =
        edgeKeySerializer.deserializeFromByteBufferObject(buffer, walChanges, 3);

    Assert.assertEquals(edgeKey, deserializedKey);
  }
}
