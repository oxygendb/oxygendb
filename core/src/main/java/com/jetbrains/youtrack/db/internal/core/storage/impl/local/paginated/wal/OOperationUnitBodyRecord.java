package com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal;

/**
 * href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 *
 * @since 31/12/14
 */
public abstract class OOperationUnitBodyRecord extends OOperationUnitRecord {

  protected OOperationUnitBodyRecord() {
  }

  protected OOperationUnitBodyRecord(long operationUnitId) {
    super(operationUnitId);
  }
}