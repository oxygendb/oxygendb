package com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.binary;

import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.exception.YTDatabaseException;
import com.jetbrains.youtrack.db.internal.core.exception.YTRecordNotFoundException;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityEntry;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.ODocumentInternal;
import java.util.Collection;
import java.util.Map;

public class ORecordSerializerNetworkDistributed extends ORecordSerializerNetworkV37 {

  public static final ORecordSerializerNetworkDistributed INSTANCE =
      new ORecordSerializerNetworkDistributed();

  protected void writeOptimizedLink(final BytesContainer bytes, YTIdentifiable link) {
    if (!link.getIdentity().isPersistent()) {
      try {
        link = link.getRecord();
      } catch (YTRecordNotFoundException rnf) {
        // IGNORE IT
      }
    }

    if (!link.getIdentity().isPersistent() && !link.getIdentity().isTemporary()) {
      throw new YTDatabaseException(
          "Found not persistent link with no connected record, probably missing save `"
              + link.getIdentity()
              + "` ");
    }
    final int pos = OVarIntSerializer.write(bytes, link.getIdentity().getClusterId());
    OVarIntSerializer.write(bytes, link.getIdentity().getClusterPosition());
  }

  protected Collection<Map.Entry<String, EntityEntry>> fetchEntries(EntityImpl document) {
    return ODocumentInternal.rawEntries(document);
  }
}