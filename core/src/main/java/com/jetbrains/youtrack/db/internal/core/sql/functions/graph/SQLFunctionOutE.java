package com.jetbrains.youtrack.db.internal.core.sql.functions.graph;

import com.jetbrains.youtrack.db.api.DatabaseSession;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.api.record.Direction;

/**
 *
 */
public class SQLFunctionOutE extends SQLFunctionMove {

  public static final String NAME = "outE";

  public SQLFunctionOutE() {
    super(NAME, 0, -1);
  }

  @Override
  protected Object move(
      final DatabaseSession graph, final Identifiable iRecord, final String[] iLabels) {
    return v2e(graph, iRecord, Direction.OUT, iLabels);
  }
}