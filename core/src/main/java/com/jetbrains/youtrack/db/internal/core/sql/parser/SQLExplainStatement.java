/* Generated By:JJTree: Do not edit this line. SQLExplainStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.BasicCommandContext;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseStats;
import com.jetbrains.youtrack.db.api.query.ExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.InternalExecutionPlan;
import com.jetbrains.youtrack.db.api.query.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SQLExplainStatement extends SQLStatement {

  protected SQLStatement statement;

  public SQLExplainStatement(int id) {
    super(id);
  }

  public SQLExplainStatement(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("EXPLAIN ");
    statement.toString(params, builder);
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    builder.append("EXPLAIN ");
    statement.toGenericStatement(builder);
  }

  @Override
  public ResultSet execute(
      DatabaseSessionInternal db, Object[] args, CommandContext parentCtx,
      boolean usePlanCache) {
    BasicCommandContext ctx = new BasicCommandContext();
    if (parentCtx != null) {
      ctx.setParentWithoutOverridingChild(parentCtx);
    }
    ctx.setDatabase(db);
    Map<Object, Object> params = new HashMap<>();
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        params.put(i, args[i]);
      }
    }
    ctx.setInputParameters(params);

    ExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = statement.createExecutionPlan(ctx, false);
    } else {
      executionPlan = statement.createExecutionPlanNoCache(ctx, false);
    }

    ExplainResultSet result = new ExplainResultSet(db, executionPlan, new DatabaseStats());
    return result;
  }

  @Override
  public ResultSet execute(
      DatabaseSessionInternal db, Map args, CommandContext parentCtx, boolean usePlanCache) {
    BasicCommandContext ctx = new BasicCommandContext();
    if (parentCtx != null) {
      ctx.setParentWithoutOverridingChild(parentCtx);
    }
    ctx.setDatabase(db);
    ctx.setInputParameters(args);

    ExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = statement.createExecutionPlan(ctx, false);
    } else {
      executionPlan = statement.createExecutionPlanNoCache(ctx, false);
    }

    ExplainResultSet result = new ExplainResultSet(db, executionPlan, new DatabaseStats());
    return result;
  }

  @Override
  public InternalExecutionPlan createExecutionPlan(CommandContext ctx, boolean enableProfiling) {
    return statement.createExecutionPlan(ctx, enableProfiling);
  }

  @Override
  public SQLExplainStatement copy() {
    SQLExplainStatement result = new SQLExplainStatement(-1);
    result.statement = statement == null ? null : statement.copy();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SQLExplainStatement that = (SQLExplainStatement) o;

    return Objects.equals(statement, that.statement);
  }

  @Override
  public int hashCode() {
    return statement != null ? statement.hashCode() : 0;
  }

  @Override
  public boolean isIdempotent() {
    return true;
  }
}
/* JavaCC - OriginalChecksum=9fdd24510993cbee32e38a51c838bdb4 (do not edit this line) */
