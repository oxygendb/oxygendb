package com.jetbrains.youtrack.db.internal.core.sql.executor;

import com.jetbrains.youtrack.db.internal.common.concur.YTTimeoutException;
import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.ExecutionStream;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLExpression;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLIdentifier;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLProjectionItem;

/**
 *
 */
public class LetExpressionStep extends AbstractExecutionStep {

  private SQLIdentifier varname;
  private SQLExpression expression;

  public LetExpressionStep(
      SQLIdentifier varName, SQLExpression expression, CommandContext ctx,
      boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.varname = varName;
    this.expression = expression;
  }

  @Override
  public ExecutionStream internalStart(CommandContext ctx) throws YTTimeoutException {
    if (prev == null) {
      throw new YTCommandExecutionException(
          "Cannot execute a local LET on a query without a target");
    }

    return prev.start(ctx).map(this::mapResult);
  }

  private YTResult mapResult(YTResult result, CommandContext ctx) {
    Object value = expression.execute(result, ctx);
    ((YTResultInternal) result)
        .setMetadata(varname.getStringValue(), SQLProjectionItem.convert(value, ctx));
    return result;
  }

  @Override
  public String prettyPrint(int depth, int indent) {
    String spaces = ExecutionStepInternal.getIndent(depth, indent);
    return spaces + "+ LET (for each record)\n" + spaces + "  " + varname + " = " + expression;
  }

  @Override
  public YTResult serialize(YTDatabaseSessionInternal db) {
    YTResultInternal result = ExecutionStepInternal.basicSerialize(db, this);
    if (varname != null) {
      result.setProperty("varname", varname.serialize(db));
    }
    if (expression != null) {
      result.setProperty("expression", expression.serialize(db));
    }
    return result;
  }

  @Override
  public void deserialize(YTResult fromResult) {
    try {
      ExecutionStepInternal.basicDeserialize(fromResult, this);
      if (fromResult.getProperty("varname") != null) {
        varname = SQLIdentifier.deserialize(fromResult.getProperty("varname"));
      }
      if (fromResult.getProperty("expression") != null) {
        expression = new SQLExpression(-1);
        expression.deserialize(fromResult.getProperty("expression"));
      }
      reset();
    } catch (Exception e) {
      throw YTException.wrapException(new YTCommandExecutionException(""), e);
    }
  }
}