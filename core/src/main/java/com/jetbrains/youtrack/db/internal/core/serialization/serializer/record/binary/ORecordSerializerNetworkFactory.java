package com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary;

import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.ORecordSerializer;
import com.jetbrains.youtrack.db.internal.enterprise.channel.binary.OChannelBinaryProtocol;

/**
 *
 */
public class ORecordSerializerNetworkFactory {

  public static ORecordSerializerNetworkFactory INSTANCE = new ORecordSerializerNetworkFactory();

  public ORecordSerializer current() {
    return forProtocol(OChannelBinaryProtocol.CURRENT_PROTOCOL_VERSION);
  }

  public ORecordSerializer forProtocol(int protocolNumber) {

    if (protocolNumber >= OChannelBinaryProtocol.PROTOCOL_VERSION_37) {
      return ORecordSerializerNetworkV37.INSTANCE;
    } else {
      return ORecordSerializerNetwork.INSTANCE;
    }
  }
}