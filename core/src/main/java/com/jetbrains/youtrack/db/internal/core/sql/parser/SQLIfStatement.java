/* Generated By:JJTree: Do not edit this line. SQLIfStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.BasicCommandContext;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.EmptyStep;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ExecutionStepInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.IfExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.IfStep;
import com.jetbrains.youtrack.db.internal.core.sql.executor.SelectExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.UpdateExecutionPlan;
import com.jetbrains.youtrack.db.api.query.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SQLIfStatement extends SQLStatement {

  protected SQLBooleanExpression expression;
  protected List<SQLStatement> statements = new ArrayList<SQLStatement>();
  protected List<SQLStatement> elseStatements =
      new ArrayList<SQLStatement>(); // TODO support ELSE in the SQL syntax

  public SQLIfStatement(int id) {
    super(id);
  }

  public SQLIfStatement(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public void addStatement(SQLStatement statement) {
    if (this.statements == null) {
      this.statements = new ArrayList<>();
    }
    this.statements.add(statement);
  }

  public void addElse(SQLStatement statement) {
    if (this.elseStatements == null) {
      this.elseStatements = new ArrayList<>();
    }
    this.elseStatements.add(statement);
  }

  @Override
  public boolean isIdempotent() {
    for (SQLStatement stm : statements) {
      if (!stm.isIdempotent()) {
        return false;
      }
    }
    for (SQLStatement stm : elseStatements) {
      if (!stm.isIdempotent()) {
        return false;
      }
    }
    return true;
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

    IfExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = createExecutionPlan(ctx, false);
    } else {
      executionPlan = (IfExecutionPlan) createExecutionPlanNoCache(ctx, false);
    }

    ExecutionStepInternal last = executionPlan.executeUntilReturn();
    if (last == null) {
      last = new EmptyStep(ctx, false);
    }
    if (isIdempotent()) {
      SelectExecutionPlan finalPlan = new SelectExecutionPlan(ctx);
      finalPlan.chain(last);
      return new LocalResultSet(finalPlan);
    } else {
      UpdateExecutionPlan finalPlan = new UpdateExecutionPlan(ctx);
      finalPlan.chain(last);
      finalPlan.executeInternal();
      return new LocalResultSet(finalPlan);
    }
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

    IfExecutionPlan executionPlan;
    if (usePlanCache) {
      executionPlan = createExecutionPlan(ctx, false);
    } else {
      executionPlan = (IfExecutionPlan) createExecutionPlanNoCache(ctx, false);
    }

    ExecutionStepInternal last = executionPlan.executeUntilReturn();
    if (last == null) {
      last = new EmptyStep(ctx, false);
    }
    if (isIdempotent()) {
      SelectExecutionPlan finalPlan = new SelectExecutionPlan(ctx);
      finalPlan.chain(last);
      return new LocalResultSet(finalPlan);
    } else {
      UpdateExecutionPlan finalPlan = new UpdateExecutionPlan(ctx);
      finalPlan.chain(last);
      finalPlan.executeInternal();
      return new LocalResultSet(finalPlan);
    }
  }

  @Override
  public IfExecutionPlan createExecutionPlan(CommandContext ctx, boolean enableProfiling) {

    IfExecutionPlan plan = new IfExecutionPlan(ctx);

    IfStep step = new IfStep(ctx, enableProfiling);
    step.setCondition(this.expression);
    plan.chain(step);

    step.positiveStatements = statements;
    step.negativeStatements = elseStatements;
    return plan;
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("IF(");
    expression.toString(params, builder);
    builder.append("){\n");
    for (SQLStatement stm : statements) {
      stm.toString(params, builder);
      builder.append(";\n");
    }
    builder.append("}");
    if (elseStatements.size() > 0) {
      builder.append("\nELSE {\n");
      for (SQLStatement stm : elseStatements) {
        stm.toString(params, builder);
        builder.append(";\n");
      }
      builder.append("}");
    }
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    builder.append("IF(");
    expression.toGenericStatement(builder);
    builder.append("){\n");
    for (SQLStatement stm : statements) {
      stm.toGenericStatement(builder);
      builder.append(";\n");
    }
    builder.append("}");
    if (elseStatements.size() > 0) {
      builder.append("\nELSE {\n");
      for (SQLStatement stm : elseStatements) {
        stm.toGenericStatement(builder);
        builder.append(";\n");
      }
      builder.append("}");
    }
  }

  @Override
  public SQLIfStatement copy() {
    SQLIfStatement result = new SQLIfStatement(-1);
    result.expression = expression == null ? null : expression.copy();
    result.statements =
        statements == null
            ? null
            : statements.stream().map(SQLStatement::copy).collect(Collectors.toList());
    result.elseStatements =
        elseStatements == null
            ? null
            : elseStatements.stream().map(SQLStatement::copy).collect(Collectors.toList());
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

    SQLIfStatement that = (SQLIfStatement) o;

    if (!Objects.equals(expression, that.expression)) {
      return false;
    }
    if (!Objects.equals(statements, that.statements)) {
      return false;
    }
    return Objects.equals(elseStatements, that.elseStatements);
  }

  @Override
  public int hashCode() {
    int result = expression != null ? expression.hashCode() : 0;
    result = 31 * result + (statements != null ? statements.hashCode() : 0);
    result = 31 * result + (elseStatements != null ? elseStatements.hashCode() : 0);
    return result;
  }

  public List<SQLStatement> getStatements() {
    return statements;
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

    if (elseStatements != null) {
      for (SQLStatement stm : this.elseStatements) {
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
    }
    return false;
  }
}
/* JavaCC - OriginalChecksum=a8cd4fb832a4f3b6e71bb1a12f8d8819 (do not edit this line) */
