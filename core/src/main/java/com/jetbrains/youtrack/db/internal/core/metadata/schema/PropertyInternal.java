package com.jetbrains.youtrack.db.internal.core.metadata.schema;

import com.jetbrains.youtrack.db.api.DatabaseSession;
import com.jetbrains.youtrack.db.api.schema.Property;
import com.jetbrains.youtrack.db.internal.core.index.Index;
import java.util.Collection;

public interface PropertyInternal extends Property {

  Collection<Index> getAllIndexesInternal(DatabaseSession session);
}
