/* Generated By:JJTree: Do not edit this line. SQLParenthesisExpression.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.api.exception.CommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.sql.executor.InsertExecutionPlan;
import com.jetbrains.youtrack.db.internal.core.sql.executor.InternalExecutionPlan;
import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SQLParenthesisExpression extends SQLMathExpression {

  protected SQLExpression expression;
  protected SQLStatement statement;

  public SQLParenthesisExpression(int id) {
    super(id);
  }

  public SQLParenthesisExpression(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public SQLParenthesisExpression(SQLExpression exp) {
    super(-1);
    this.expression = exp;
  }

  @Override
  public Object execute(Identifiable iCurrentRecord, CommandContext ctx) {
    if (expression != null) {
      return expression.execute(iCurrentRecord, ctx);
    }
    if (statement != null) {
      throw new UnsupportedOperationException(
          "Execution of select in parentheses is not supported");
    }
    return super.execute(iCurrentRecord, ctx);
  }

  @Override
  public Object execute(Result iCurrentRecord, CommandContext ctx) {
    if (expression != null) {
      return expression.execute(iCurrentRecord, ctx);
    }
    if (statement != null) {
      InternalExecutionPlan execPlan;
      if (statement.originalStatement == null || statement.originalStatement.contains("?")) {
        // cannot cache statements with positional params, especially when it's in a
        // subquery/expression.
        execPlan = statement.createExecutionPlanNoCache(ctx, false);
      } else {
        execPlan = statement.createExecutionPlan(ctx, false);
      }
      if (execPlan instanceof InsertExecutionPlan) {
        ((InsertExecutionPlan) execPlan).executeInternal();
      }
      LocalResultSet rs = new LocalResultSet(execPlan);
      List<Result> result = new ArrayList<>();
      while (rs.hasNext()) {
        result.add(rs.next());
      }
      //      List<Result> result = rs.stream().collect(Collectors.toList());//TODO streamed...
      rs.close();
      return result;
    }
    return super.execute(iCurrentRecord, ctx);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("(");
    if (expression != null) {
      expression.toString(params, builder);
    } else if (statement != null) {
      statement.toString(params, builder);
    }
    builder.append(")");
  }

  public void toGenericStatement(StringBuilder builder) {
    builder.append("(");
    if (expression != null) {
      expression.toGenericStatement(builder);
    } else if (statement != null) {
      statement.toGenericStatement(builder);
    }
    builder.append(")");
  }

  @Override
  protected boolean supportsBasicCalculation() {
    if (expression != null) {
      return expression.supportsBasicCalculation();
    }
    return true;
  }

  @Override
  public boolean isEarlyCalculated(CommandContext ctx) {
    // TODO implement query execution and early calculation;
    return expression != null && expression.isEarlyCalculated(ctx);
  }

  public boolean needsAliases(Set<String> aliases) {
    return expression.needsAliases(aliases);
  }

  public boolean isExpand() {
    if (expression != null) {
      return expression.isExpand();
    }
    return false;
  }

  public boolean isAggregate(DatabaseSessionInternal session) {
    if (expression != null) {
      return expression.isAggregate(session);
    }
    return false;
  }

  public boolean isCount() {
    if (expression != null) {
      return expression.isCount();
    }
    return false;
  }

  public SimpleNode splitForAggregation(
      AggregateProjectionSplit aggregateProj, CommandContext ctx) {
    if (isAggregate(ctx.getDatabase())) {
      SQLParenthesisExpression result = new SQLParenthesisExpression(-1);
      result.expression = expression.splitForAggregation(aggregateProj, ctx);
      return result;
    } else {
      return this;
    }
  }

  @Override
  public SQLParenthesisExpression copy() {
    SQLParenthesisExpression result = new SQLParenthesisExpression(-1);
    result.expression = expression == null ? null : expression.copy();
    result.statement = statement == null ? null : statement.copy();
    return result;
  }

  public void setStatement(SQLStatement statement) {
    this.statement = statement;
  }

  public void extractSubQueries(SubQueryCollector collector) {
    if (expression != null) {
      expression.extractSubQueries(collector);
    } else if (statement != null) {
      SQLIdentifier alias = collector.addStatement(statement);
      statement = null;
      expression = new SQLExpression(alias);
    }
  }

  public void extractSubQueries(SQLIdentifier letAlias, SubQueryCollector collector) {
    if (expression != null) {
      expression.extractSubQueries(collector);
    } else if (statement != null) {
      SQLIdentifier alias = collector.addStatement(letAlias, statement);
      statement = null;
      expression = new SQLExpression(alias);
    }
  }

  public boolean refersToParent() {
    if (expression != null && expression.refersToParent()) {
      return true;
    }
    return statement != null && statement.refersToParent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    SQLParenthesisExpression that = (SQLParenthesisExpression) o;

    if (!Objects.equals(expression, that.expression)) {
      return false;
    }
    return Objects.equals(statement, that.statement);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (expression != null ? expression.hashCode() : 0);
    result = 31 * result + (statement != null ? statement.hashCode() : 0);
    return result;
  }

  public List<String> getMatchPatternInvolvedAliases() {
    return expression.getMatchPatternInvolvedAliases(); // TODO also check the statement...?
  }

  @Override
  public void applyRemove(ResultInternal result, CommandContext ctx) {
    if (expression != null) {
      expression.applyRemove(result, ctx);
    } else {
      throw new CommandExecutionException("Cannot apply REMOVE " + this);
    }
  }

  public Result serialize(DatabaseSessionInternal db) {
    ResultInternal result = (ResultInternal) super.serialize(db);
    if (expression != null) {
      result.setProperty("expression", expression.serialize(db));
    }
    if (statement != null) {
      result.setProperty("statement", statement.serialize(db));
    }
    return result;
  }

  public void deserialize(Result fromResult) {
    super.deserialize(fromResult);
    if (fromResult.getProperty("expression") != null) {
      expression = new SQLExpression(-1);
      expression.deserialize(fromResult.getProperty("expression"));
    }
    if (fromResult.getProperty("statement") != null) {
      statement = SQLStatement.deserializeFromOResult(fromResult.getProperty("statement"));
    }
  }

  @Override
  public boolean isCacheable(DatabaseSessionInternal session) {
    if (expression != null) {
      return expression.isCacheable(session);
    }
    if (statement != null) {
      return statement.executinPlanCanBeCached(session);
    }
    return true;
  }
}
/* JavaCC - OriginalChecksum=4656e5faf4f54dc3fc45a06d8e375c35 (do not edit this line) */
