/* Generated By:JJTree: Do not edit this line. SQLBinaryCondition.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.core.collate.OCollate;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.sql.executor.OIndexSearchInfo;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResult;
import com.jetbrains.youtrack.db.internal.core.sql.executor.YTResultInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.OIndexCandidate;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.OIndexFinder;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.OPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SQLBinaryCondition extends SQLBooleanExpression {

  protected SQLExpression left;
  protected SQLBinaryCompareOperator operator;
  protected SQLExpression right;

  public SQLBinaryCondition(int id) {
    super(id);
  }

  public SQLBinaryCondition(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public boolean evaluate(YTIdentifiable currentRecord, CommandContext ctx) {
    return operator.execute(left.execute(currentRecord, ctx), right.execute(currentRecord, ctx));
  }

  @Override
  public boolean evaluate(YTResult currentRecord, CommandContext ctx) {
    if (left.isFunctionAny()) {
      return evaluateAny(currentRecord, ctx);
    }

    if (left.isFunctionAll()) {
      return evaluateAllFunction(currentRecord, ctx);
    }
    Object leftVal = left.execute(currentRecord, ctx);
    Object rightVal = right.execute(currentRecord, ctx);
    OCollate collate = left.getCollate(currentRecord, ctx);
    if (collate == null) {
      collate = right.getCollate(currentRecord, ctx);
    }
    if (collate != null) {
      leftVal = collate.transform(leftVal);
      rightVal = collate.transform(rightVal);
    }
    return operator.execute(leftVal, rightVal);
  }

  private boolean evaluateAny(YTResult currentRecord, CommandContext ctx) {
    for (String s : currentRecord.getPropertyNames()) {
      Object leftVal = currentRecord.getProperty(s);
      Object rightVal = right.execute(currentRecord, ctx);

      // TODO collate

      if (operator.execute(leftVal, rightVal)) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateAllFunction(YTResult currentRecord, CommandContext ctx) {
    for (String s : currentRecord.getPropertyNames()) {
      Object leftVal = currentRecord.getProperty(s);
      Object rightVal = right.execute(currentRecord, ctx);

      // TODO collate

      if (!operator.execute(leftVal, rightVal)) {
        return false;
      }
    }
    return true;
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    left.toString(params, builder);
    builder.append(" ");
    builder.append(operator.toString());
    builder.append(" ");
    right.toString(params, builder);
  }

  public void toGenericStatement(StringBuilder builder) {
    left.toGenericStatement(builder);
    builder.append(" ");
    operator.toGenericStatement(builder);
    builder.append(" ");
    right.toGenericStatement(builder);
  }

  protected boolean supportsBasicCalculation() {
    if (!operator.supportsBasicCalculation()) {
      return false;
    }
    return left.supportsBasicCalculation() && right.supportsBasicCalculation();
  }

  @Override
  protected int getNumberOfExternalCalculations() {
    int total = 0;
    if (!operator.supportsBasicCalculation()) {
      total++;
    }
    if (!left.supportsBasicCalculation()) {
      total++;
    }
    if (!right.supportsBasicCalculation()) {
      total++;
    }
    return total;
  }

  @Override
  protected List<Object> getExternalCalculationConditions() {
    List<Object> result = new ArrayList<Object>();
    if (!operator.supportsBasicCalculation()) {
      result.add(this);
    }
    if (!left.supportsBasicCalculation()) {
      result.add(left);
    }
    if (!right.supportsBasicCalculation()) {
      result.add(right);
    }
    return result;
  }

  public SQLBinaryCondition isIndexedFunctionCondition(
      YTClass iSchemaClass, YTDatabaseSessionInternal database) {
    if (left.isIndexedFunctionCal(database)) {
      return this;
    }
    return null;
  }

  public long estimateIndexed(SQLFromClause target, CommandContext context) {
    return left.estimateIndexedFunction(
        target, context, operator, right.execute((YTResult) null, context));
  }

  public Iterable<YTIdentifiable> executeIndexedFunction(
      SQLFromClause target, CommandContext context) {
    return left.executeIndexedFunction(
        target, context, operator, right.execute((YTResult) null, context));
  }

  /**
   * tests if current expression involves an indexed funciton AND that function can also be executed
   * without using the index
   *
   * @param target  the query target
   * @param context the execution context
   * @return true if current expression involves an indexed function AND that function can be used
   * on this target, false otherwise
   */
  public boolean canExecuteIndexedFunctionWithoutIndex(
      SQLFromClause target, CommandContext context) {
    return left.canExecuteIndexedFunctionWithoutIndex(
        target, context, operator, right.execute((YTResult) null, context));
  }

  /**
   * tests if current expression involves an indexed function AND that function can be used on this
   * target
   *
   * @param target  the query target
   * @param context the execution context
   * @return true if current expression involves an indexed function AND that function can be used
   * on this target, false otherwise
   */
  public boolean allowsIndexedFunctionExecutionOnTarget(
      SQLFromClause target, CommandContext context) {
    return left.allowsIndexedFunctionExecutionOnTarget(
        target, context, operator, right.execute((YTResult) null, context));
  }

  /**
   * tests if current expression involves an indexed function AND the function has also to be
   * executed after the index search. In some cases, the index search is accurate, so this condition
   * can be excluded from further evaluation. In other cases the result from the index is a superset
   * of the expected result, so the function has to be executed anyway for further filtering
   *
   * @param target  the query target
   * @param context the execution context
   * @return true if current expression involves an indexed function AND the function has also to be
   * executed after the index search.
   */
  public boolean executeIndexedFunctionAfterIndexSearch(
      SQLFromClause target, CommandContext context) {
    return left.executeIndexedFunctionAfterIndexSearch(
        target, context, operator, right.execute((YTResult) null, context));
  }

  public List<SQLBinaryCondition> getIndexedFunctionConditions(
      YTClass iSchemaClass, YTDatabaseSessionInternal database) {
    if (left.isIndexedFunctionCal(database)) {
      return Collections.singletonList(this);
    }
    return null;
  }

  @Override
  public boolean needsAliases(Set<String> aliases) {
    if (left.needsAliases(aliases)) {
      return true;
    }
    return right.needsAliases(aliases);
  }

  @Override
  public SQLBinaryCondition copy() {
    SQLBinaryCondition result = new SQLBinaryCondition(-1);
    result.left = left.copy();
    result.operator = operator.copy();
    result.right = right.copy();
    return result;
  }

  @Override
  public void extractSubQueries(SubQueryCollector collector) {
    left.extractSubQueries(collector);
    right.extractSubQueries(collector);
  }

  @Override
  public boolean refersToParent() {
    return left.refersToParent() || right.refersToParent();
  }

  @Override
  public Optional<SQLUpdateItem> transformToUpdateItem() {
    if (!checkCanTransformToUpdate()) {
      return Optional.empty();
    }
    if (operator instanceof SQLEqualsCompareOperator) {
      SQLUpdateItem result = new SQLUpdateItem(-1);
      result.operator = SQLUpdateItem.OPERATOR_EQ;
      SQLBaseExpression baseExp = ((SQLBaseExpression) left.mathExpression);
      result.left = baseExp.getIdentifier().suffix.getIdentifier().copy();
      result.leftModifier = baseExp.modifier == null ? null : baseExp.modifier.copy();
      result.right = right.copy();
      return Optional.of(result);
    }
    return super.transformToUpdateItem();
  }

  private boolean checkCanTransformToUpdate() {
    if (left == null
        || left.mathExpression == null
        || !(left.mathExpression instanceof SQLBaseExpression base)) {
      return false;
    }
    return base.getIdentifier() != null
        && base.getIdentifier().suffix != null
        && base.getIdentifier().suffix.getIdentifier() != null;
  }

  public SQLExpression getLeft() {
    return left;
  }

  public SQLBinaryCompareOperator getOperator() {
    return operator;
  }

  public SQLExpression getRight() {
    return right;
  }

  public void setLeft(SQLExpression left) {
    this.left = left;
  }

  public void setOperator(SQLBinaryCompareOperator operator) {
    this.operator = operator;
  }

  public void setRight(SQLExpression right) {
    this.right = right;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SQLBinaryCondition that = (SQLBinaryCondition) o;

    if (!Objects.equals(left, that.left)) {
      return false;
    }
    if (!Objects.equals(operator, that.operator)) {
      return false;
    }
    return Objects.equals(right, that.right);
  }

  @Override
  public int hashCode() {
    int result = left != null ? left.hashCode() : 0;
    result = 31 * result + (operator != null ? operator.hashCode() : 0);
    result = 31 * result + (right != null ? right.hashCode() : 0);
    return result;
  }

  @Override
  public List<String> getMatchPatternInvolvedAliases() {
    List<String> leftX = left.getMatchPatternInvolvedAliases();
    List<String> rightX = right.getMatchPatternInvolvedAliases();
    if (leftX == null) {
      return rightX;
    }
    if (rightX == null) {
      return leftX;
    }

    List<String> result = new ArrayList<String>();
    result.addAll(leftX);
    result.addAll(rightX);
    return result;
  }

  @Override
  public void translateLuceneOperator() {
    if (operator instanceof SQLLuceneOperator) {
      SQLExpression newLeft = new SQLExpression(-1);
      newLeft.mathExpression = new SQLBaseExpression(-1);
      SQLBaseIdentifier identifirer = new SQLBaseIdentifier(-1);
      ((SQLBaseExpression) newLeft.mathExpression).setIdentifier(identifirer);
      identifirer.levelZero = new SQLLevelZeroIdentifier(-1);
      SQLFunctionCall function = new SQLFunctionCall(-1);
      identifirer.levelZero.functionCall = function;
      function.name = new SQLIdentifier("search_fields");
      function.params = new ArrayList<>();
      function.params.add(fieldNamesToStrings(left));
      function.params.add(right);
      left = newLeft;

      operator = new SQLEqualsCompareOperator(-1);
      right = new SQLExpression(-1);
      right.booleanValue = true;
    }
  }

  private SQLExpression fieldNamesToStrings(SQLExpression left) {
    if (left.isBaseIdentifier()) {
      SQLIdentifier identifier =
          ((SQLBaseExpression) left.mathExpression).getIdentifier().suffix.getIdentifier();
      SQLCollection newColl = new SQLCollection(-1);
      newColl.expressions = new ArrayList<>();
      newColl.expressions.add(identifierToStringExpr(identifier));
      SQLExpression result = new SQLExpression(-1);
      SQLBaseExpression newBase = new SQLBaseExpression(-1);
      result.mathExpression = newBase;
      SQLBaseIdentifier newIdentifier = new SQLBaseIdentifier(-1);
      newIdentifier.levelZero = new SQLLevelZeroIdentifier(-1);
      newIdentifier.levelZero.collection = newColl;
      newBase.setIdentifier(newIdentifier);
      return result;
    } else if (left.mathExpression instanceof SQLBaseExpression base) {
      if (base.getIdentifier() != null
          && base.getIdentifier().levelZero != null
          && base.getIdentifier().levelZero.collection != null) {
        SQLCollection coll = base.getIdentifier().levelZero.collection;

        SQLCollection newColl = new SQLCollection(-1);
        newColl.expressions = new ArrayList<>();

        for (SQLExpression exp : coll.expressions) {
          if (exp.isBaseIdentifier()) {
            SQLIdentifier identifier =
                ((SQLBaseExpression) exp.mathExpression).getIdentifier().suffix.getIdentifier();
            SQLExpression val = identifierToStringExpr(identifier);
            newColl.expressions.add(val);
          } else {
            throw new YTCommandExecutionException(
                "Cannot execute because of invalid LUCENE expression");
          }
        }
        SQLExpression result = new SQLExpression(-1);
        SQLBaseExpression newBase = new SQLBaseExpression(-1);
        result.mathExpression = newBase;
        SQLBaseIdentifier newIdentifier = new SQLBaseIdentifier(-1);
        newIdentifier.levelZero = new SQLLevelZeroIdentifier(-1);
        newIdentifier.levelZero.collection = newColl;
        newBase.setIdentifier(newIdentifier);
        return result;
      }
    }
    throw new YTCommandExecutionException("Cannot execute because of invalid LUCENE expression");
  }

  private SQLExpression identifierToStringExpr(SQLIdentifier identifier) {
    SQLBaseExpression bexp = new SQLBaseExpression(identifier.getStringValue());

    SQLExpression result = new SQLExpression(-1);
    result.mathExpression = bexp;
    return result;
  }

  public YTResult serialize(YTDatabaseSessionInternal db) {
    YTResultInternal result = new YTResultInternal(db);
    result.setProperty("left", left.serialize(db));
    result.setProperty("operator", operator.getClass().getName());
    result.setProperty("right", right.serialize(db));
    return result;
  }

  public void deserialize(YTResult fromResult) {
    left = new SQLExpression(-1);
    left.deserialize(fromResult.getProperty("left"));
    try {
      operator =
          (SQLBinaryCompareOperator)
              Class.forName(String.valueOf(fromResult.getProperty("operator"))).newInstance();
    } catch (Exception e) {
      throw YTException.wrapException(new YTCommandExecutionException(""), e);
    }
    right = new SQLExpression(-1);
    right.deserialize(fromResult.getProperty("right"));
  }

  @Override
  public boolean isCacheable(YTDatabaseSessionInternal session) {
    return left.isCacheable(session) && right.isCacheable(session);
  }

  @Override
  public SQLBooleanExpression rewriteIndexChainsAsSubqueries(CommandContext ctx, YTClass clazz) {
    if (operator instanceof SQLEqualsCompareOperator
        && right.isEarlyCalculated(ctx)
        && left.isIndexChain(ctx, clazz)) {
      SQLInCondition result = new SQLInCondition(-1);

      result.left = new SQLExpression(-1);
      SQLBaseExpression base = new SQLBaseExpression(-1);
      SQLBaseIdentifier identifier = new SQLBaseIdentifier(-1);
      identifier.suffix = new SQLSuffixIdentifier(-1);
      identifier.suffix.setIdentifier(
          ((SQLBaseExpression) left.mathExpression).getIdentifier().suffix.getIdentifier());
      base.setIdentifier(identifier);
      result.left.mathExpression = base;

      result.operator = new SQLInOperator(-1);

      YTClass nextClazz =
          clazz
              .getProperty(base.getIdentifier().suffix.getIdentifier().getStringValue())
              .getLinkedClass();
      result.rightStatement =
          indexChainToStatement(
              ((SQLBaseExpression) left.mathExpression).modifier, nextClazz, right, ctx);
      return result;
    }
    return this;
  }

  public static SQLSelectStatement indexChainToStatement(
      SQLModifier modifier, YTClass clazz, SQLExpression right, CommandContext ctx) {
    YTClass queryClass = clazz;

    SQLSelectStatement result = new SQLSelectStatement(-1);
    result.target = new SQLFromClause(-1);
    result.target.setItem(new SQLFromItem(-1));
    result.target.getItem().identifier = new SQLIdentifier(queryClass.getName());

    result.whereClause = new SQLWhereClause(-1);
    SQLBinaryCondition base = new SQLBinaryCondition(-1);
    result.whereClause.baseExpression = new SQLNotBlock(-1);
    ((SQLNotBlock) result.whereClause.baseExpression).sub = base;
    ((SQLNotBlock) result.whereClause.baseExpression).negate = false;

    base.left = new SQLExpression(-1);
    base.left.mathExpression = new SQLBaseExpression(-1);
    ((SQLBaseExpression) base.left.mathExpression)
        .setIdentifier(new SQLBaseIdentifier(modifier.suffix.getIdentifier()));
    ((SQLBaseExpression) base.left.mathExpression).modifier =
        modifier.next == null ? null : modifier.next.copy();

    base.operator = new SQLEqualsCompareOperator(-1);
    base.right = right.copy();

    return result;
  }

  public Optional<OIndexCandidate> findIndex(OIndexFinder info, CommandContext ctx) {
    Optional<OPath> path = left.getPath();
    if (path.isPresent()) {
      OPath p = path.get();
      if (right.isEarlyCalculated(ctx)) {
        Object value = right.execute((YTResult) null, ctx);
        if (operator instanceof SQLEqualsCompareOperator) {
          return info.findExactIndex(p, value, ctx);
        } else if (operator instanceof SQLContainsKeyOperator) {
          return info.findByKeyIndex(p, value, ctx);
        } else if (operator.isRangeOperator()) {
          return info.findAllowRangeIndex(p, operator.getOperation(), value, ctx);
        }
      }
    }

    return Optional.empty();
  }

  public boolean isIndexAware(OIndexSearchInfo info) {
    if (left.isBaseIdentifier()) {
      if (info.getField().equals(left.getDefaultAlias().getStringValue())) {
        if (right.isEarlyCalculated(info.getCtx())) {
          if (operator instanceof SQLEqualsCompareOperator) {
            return true;
          } else if (operator instanceof SQLContainsKeyOperator
              && info.isMap()
              && info.isIndexByKey()) {
            return true;
          } else {
            return info.allowsRange() && operator.isRangeOperator();
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean createRangeWith(SQLBooleanExpression match) {
    if (!(match instanceof SQLBinaryCondition metchingCondition)) {
      return false;
    }
    if (!metchingCondition.left.equals(this.left)) {
      return false;
    }
    SQLBinaryCompareOperator leftOperator = metchingCondition.operator;
    SQLBinaryCompareOperator rightOperator = this.operator;
    if (leftOperator instanceof SQLGeOperator || leftOperator instanceof SQLGtOperator) {
      return rightOperator instanceof SQLLeOperator || rightOperator instanceof SQLLtOperator;
    }
    if (leftOperator instanceof SQLLeOperator || leftOperator instanceof SQLLtOperator) {
      return rightOperator instanceof SQLGeOperator || rightOperator instanceof SQLGtOperator;
    }
    return false;
  }

  @Override
  public SQLExpression resolveKeyFrom(SQLBinaryCondition additional) {
    SQLBinaryCompareOperator operator = this.operator;
    if ((operator instanceof SQLEqualsCompareOperator)
        || (operator instanceof SQLGtOperator)
        || (operator instanceof SQLGeOperator)
        || (operator instanceof SQLContainsKeyOperator)
        || (operator instanceof SQLContainsValueOperator)) {
      return right;
    } else if (additional != null) {
      return additional.right;
    } else {
      return null;
      //      throw new UnsupportedOperationException("Cannot execute index query with " + this);
    }
  }

  @Override
  public SQLExpression resolveKeyTo(SQLBinaryCondition additional) {
    SQLBinaryCompareOperator operator = this.operator;
    if ((operator instanceof SQLEqualsCompareOperator)
        || (operator instanceof SQLLtOperator)
        || (operator instanceof SQLLeOperator)
        || (operator instanceof SQLContainsKeyOperator)
        || (operator instanceof SQLContainsValueOperator)) {
      return right;
    } else if (additional != null) {
      return additional.right;
    } else {
      return null;
      //      throw new UnsupportedOperationException("Cannot execute index query with " + this);
    }
  }
}
/* JavaCC - OriginalChecksum=99ed1dd2812eb730de8e1931b1764da5 (do not edit this line) */