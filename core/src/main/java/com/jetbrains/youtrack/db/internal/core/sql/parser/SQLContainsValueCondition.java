/* Generated By:JJTree: Do not edit this line. SQLContainsValueCondition.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.internal.core.sql.executor.IndexSearchInfo;
import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.IndexCandidate;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.IndexFinder;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.MetadataPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SQLContainsValueCondition extends SQLBooleanExpression {

  protected SQLExpression left;
  protected SQLContainsValueOperator operator;
  protected SQLOrBlock condition;
  protected SQLExpression expression;

  public SQLContainsValueCondition(int id) {
    super(id);
  }

  public SQLContainsValueCondition(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public boolean evaluate(Identifiable currentRecord, CommandContext ctx) {
    Object leftValue = left.execute(currentRecord, ctx);
    if (leftValue instanceof Map map) {
      if (condition != null) {
        for (Object o : map.values()) {
          if (condition.evaluate(o, ctx)) {
            return true;
          }
        }
        return false;
      } else {
        Object rightValue = expression.execute(currentRecord, ctx);
        return map.containsValue(rightValue); // TODO type conversions...?
      }
    }
    return false;
  }

  @Override
  public boolean evaluate(Result currentRecord, CommandContext ctx) {
    if (left.isFunctionAny()) {
      return evaluateAny(currentRecord, ctx);
    }

    if (left.isFunctionAll()) {
      return evaluateAllFunction(currentRecord, ctx);
    }

    Object leftValue = left.execute(currentRecord, ctx);
    if (leftValue instanceof Map map) {
      if (condition != null) {
        for (Object o : map.values()) {
          if (condition.evaluate(o, ctx)) {
            return true;
          }
        }
        return false;
      } else {
        Object rightValue = expression.execute(currentRecord, ctx);
        return map.containsValue(rightValue); // TODO type conversions...?
      }
    }
    return false;
  }

  private boolean evaluateAllFunction(Result currentRecord, CommandContext ctx) {
    for (String propertyName : currentRecord.getPropertyNames()) {
      Object leftValue = currentRecord.getProperty(propertyName);
      if (leftValue instanceof Map map) {
        if (condition != null) {
          boolean found = false;
          for (Object o : map.values()) {
            if (condition.evaluate(o, ctx)) {
              found = true;
              break;
            }
          }
          if (!found) {
            return false;
          }
        } else {
          Object rightValue = expression.execute(currentRecord, ctx);
          if (!map.containsValue(rightValue)) {
            return false;
          }
        }
      } else {
        return false;
      }
    }
    return true;
  }

  private boolean evaluateAny(Result currentRecord, CommandContext ctx) {
    for (String propertyName : currentRecord.getPropertyNames()) {
      Object leftValue = currentRecord.getProperty(propertyName);
      if (leftValue instanceof Map map) {
        if (condition != null) {
          for (Object o : map.values()) {
            if (condition.evaluate(o, ctx)) {
              return true;
            }
          }
        } else {
          Object rightValue = expression.execute(currentRecord, ctx);
          if (map.containsValue(rightValue)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {

    left.toString(params, builder);
    builder.append(" CONTAINSVALUE ");
    if (condition != null) {
      builder.append("(");
      condition.toString(params, builder);
      builder.append(")");
    } else {
      expression.toString(params, builder);
    }
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    left.toGenericStatement(builder);
    builder.append(" CONTAINSVALUE ");
    if (condition != null) {
      builder.append("(");
      condition.toGenericStatement(builder);
      builder.append(")");
    } else {
      expression.toGenericStatement(builder);
    }
  }

  @Override
  public boolean supportsBasicCalculation() {
    return true;
  }

  @Override
  protected int getNumberOfExternalCalculations() {
    if (condition == null) {
      return 0;
    }
    return condition.getNumberOfExternalCalculations();
  }

  @Override
  protected List<Object> getExternalCalculationConditions() {
    if (condition == null) {
      return Collections.EMPTY_LIST;
    }
    return condition.getExternalCalculationConditions();
  }

  @Override
  public boolean needsAliases(Set<String> aliases) {
    if (left != null && left.needsAliases(aliases)) {
      return true;
    }
    if (condition != null && condition.needsAliases(aliases)) {
      return true;
    }
    return expression != null && expression.needsAliases(aliases);
  }

  @Override
  public SQLContainsValueCondition copy() {
    SQLContainsValueCondition result = new SQLContainsValueCondition(-1);
    result.left = left.copy();
    result.operator = operator;
    result.condition = condition == null ? null : condition.copy();
    result.expression = expression == null ? null : expression.copy();
    return result;
  }

  @Override
  public void extractSubQueries(SubQueryCollector collector) {
    left.extractSubQueries(collector);
    if (condition != null) {
      condition.extractSubQueries(collector);
    }
    if (expression != null) {
      expression.extractSubQueries(collector);
    }
  }

  @Override
  public boolean refersToParent() {
    if (left != null && left.refersToParent()) {
      return true;
    }
    if (condition != null && condition.refersToParent()) {
      return true;
    }
    return expression != null && condition.refersToParent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SQLContainsValueCondition that = (SQLContainsValueCondition) o;

    if (!Objects.equals(left, that.left)) {
      return false;
    }
    if (!Objects.equals(operator, that.operator)) {
      return false;
    }
    if (!Objects.equals(condition, that.condition)) {
      return false;
    }
    return Objects.equals(expression, that.expression);
  }

  @Override
  public int hashCode() {
    int result = left != null ? left.hashCode() : 0;
    result = 31 * result + (operator != null ? operator.hashCode() : 0);
    result = 31 * result + (condition != null ? condition.hashCode() : 0);
    result = 31 * result + (expression != null ? expression.hashCode() : 0);
    return result;
  }

  @Override
  public List<String> getMatchPatternInvolvedAliases() {
    List<String> leftX = left == null ? null : left.getMatchPatternInvolvedAliases();
    List<String> expressionX =
        expression == null ? null : expression.getMatchPatternInvolvedAliases();
    List<String> conditionX = condition == null ? null : condition.getMatchPatternInvolvedAliases();

    List<String> result = new ArrayList<String>();
    if (leftX != null) {
      result.addAll(leftX);
    }
    if (expressionX != null) {
      result.addAll(expressionX);
    }
    if (conditionX != null) {
      result.addAll(conditionX);
    }

    return result.size() == 0 ? null : result;
  }

  @Override
  public boolean isCacheable(DatabaseSessionInternal session) {
    if (left != null && !left.isCacheable(session)) {
      return false;
    }
    if (condition != null && !condition.isCacheable(session)) {
      return false;
    }
    return expression == null || expression.isCacheable(session);
  }

  public SQLExpression getExpression() {
    return expression;
  }

  public SQLExpression getLeft() {
    return left;
  }

  public SQLContainsValueOperator getOperator() {
    return operator;
  }

  public Optional<IndexCandidate> findIndex(IndexFinder info, CommandContext ctx) {
    Optional<MetadataPath> path = left.getPath();
    if (path.isPresent()) {
      if (expression != null && expression.isEarlyCalculated(ctx)) {
        Object value = expression.execute((Result) null, ctx);
        return info.findByValueIndex(path.get(), value, ctx);
      }
    }

    return Optional.empty();
  }

  public boolean isIndexAware(IndexSearchInfo info) {
    if (left.isBaseIdentifier()) {
      if (info.getField().equals(left.getDefaultAlias().getStringValue())) {
        return expression != null
            && expression.isEarlyCalculated(info.getCtx())
            && info.isMap()
            && info.isIndexByValue();
      }
    }
    return false;
  }

  @Override
  public SQLExpression resolveKeyFrom(SQLBinaryCondition additional) {
    return expression;
  }

  @Override
  public SQLExpression resolveKeyTo(SQLBinaryCondition additional) {
    return expression;
  }
}
/* JavaCC - OriginalChecksum=6fda752f10c8d8731f43efa706e39459 (do not edit this line) */
