/* Generated By:JJTree: Do not edit this line. SQLForEachBlock.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.BasicCommandContext;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ForEachExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ForEachStep;
import com.jetbrains.youtrack.db.internal.core.sql.executor.GlobalLetExpressionStep;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultSet;
import com.jetbrains.youtrack.db.internal.core.sql.executor.UpdateExecutionPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SQLForEachBlock extends SQLStatement {

  static int FOREACH_VARIABLE_PROGR = 0;

  protected SQLIdentifier loopVariable;
  protected SQLExpression loopValues;
  protected List<SQLStatement> statements = new ArrayList<>();

  public SQLForEachBlock(int id) {
    super(id);
  }

  public SQLForEachBlock(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public void addStatement(SQLStatement statement) {
    if (statements == null) {
      this.statements = new ArrayList<>();
    }
    this.statements.add(statement);
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
    UpdateExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = createExecutionPlan(ctx, false);
    } else {
      executionPlan = (UpdateExecutionPlan) createExecutionPlanNoCache(ctx, false);
    }

    executionPlan.executeInternal();
    return new LocalResultSet(executionPlan);
  }

  @Override
  public ResultSet execute(
      DatabaseSessionInternal db, Map params, CommandContext parentCtx, boolean usePlanCache) {
    BasicCommandContext ctx = new BasicCommandContext();
    if (parentCtx != null) {
      ctx.setParentWithoutOverridingChild(parentCtx);
    }
    ctx.setDatabase(db);
    ctx.setInputParameters(params);

    UpdateExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = createExecutionPlan(ctx, false);
    } else {
      executionPlan = (UpdateExecutionPlan) createExecutionPlanNoCache(ctx, false);
    }

    executionPlan.executeInternal();
    return new LocalResultSet(executionPlan);
  }

  public UpdateExecutionPlan createExecutionPlan(CommandContext ctx, boolean enableProfiling) {
    ForEachExecutionPlan plan = new ForEachExecutionPlan(ctx);
    int nextProg = ++FOREACH_VARIABLE_PROGR;
    if (FOREACH_VARIABLE_PROGR < 0) {
      FOREACH_VARIABLE_PROGR = 0;
    }
    SQLIdentifier varName = new SQLIdentifier("$__YOUTRACKDB_FOREACH_VAR_" + nextProg);
    plan.chain(new GlobalLetExpressionStep(varName, loopValues, ctx, enableProfiling));
    plan.chain(
        new ForEachStep(loopVariable, new SQLExpression(varName), statements, ctx,
            enableProfiling));
    return plan;
  }

  @Override
  public SQLStatement copy() {
    SQLForEachBlock result = new SQLForEachBlock(-1);
    result.loopVariable = loopVariable.copy();
    result.loopValues = loopValues.copy();
    result.statements = statements.stream().map(x -> x.copy()).collect(Collectors.toList());
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

    SQLForEachBlock that = (SQLForEachBlock) o;

    if (!Objects.equals(loopVariable, that.loopVariable)) {
      return false;
    }
    if (!Objects.equals(loopValues, that.loopValues)) {
      return false;
    }
    return Objects.equals(statements, that.statements);
  }

  @Override
  public int hashCode() {
    int result = loopVariable != null ? loopVariable.hashCode() : 0;
    result = 31 * result + (loopValues != null ? loopValues.hashCode() : 0);
    result = 31 * result + (statements != null ? statements.hashCode() : 0);
    return result;
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("FOREACH (");
    loopVariable.toString(params, builder);
    builder.append(" IN ");
    loopValues.toString(params, builder);
    builder.append(") {\n");
    for (SQLStatement stm : statements) {
      stm.toString(params, builder);
      builder.append("\n");
    }
    builder.append("}");
  }

  public void toGenericStatement(StringBuilder builder) {
    builder.append("FOREACH (");
    loopVariable.toGenericStatement(builder);
    builder.append(" IN ");
    loopValues.toGenericStatement(builder);
    builder.append(") {\n");
    for (SQLStatement stm : statements) {
      stm.toGenericStatement(builder);
      builder.append("\n");
    }
    builder.append("}");
  }

  public boolean containsReturn() {
    for (SQLStatement stm : this.statements) {
      if (stm instanceof SQLReturnStatement) {
        return true;
      }
      if (stm instanceof SQLForEachBlock && ((SQLForEachBlock) stm).containsReturn()) {
        return true;
      }
      if (stm instanceof SQLIfStatement && ((SQLIfStatement) stm).containsReturn()) {
        return true;
      }
    }
    return false;
  }
}
/* JavaCC - OriginalChecksum=071053b057a38c57f3c90d28399615d0 (do not edit this line) */
