/* Generated By:JJTree: Do not edit this line. SQLModifier.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.api.exception.CommandExecutionException;
import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.internal.common.collection.MultiValue;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.SchemaClassInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.metadata.MetadataPath;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SQLModifier extends SimpleNode {

  protected boolean squareBrackets = false;
  protected SQLArrayRangeSelector arrayRange;
  protected SQLOrBlock condition;
  protected SQLArraySingleValuesSelector arraySingleValues;
  protected SQLRightBinaryCondition rightBinaryCondition;
  protected SQLMethodCall methodCall;
  protected SQLSuffixIdentifier suffix;

  protected SQLModifier next;

  public SQLModifier(int id) {
    super(id);
  }

  public SQLModifier(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public boolean isArraySingleValue() {
    if (this.arraySingleValues != null) {
      return this.arraySingleValues.items.size() == 1;
    } else {
      return false;
    }
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {

    if (squareBrackets) {
      builder.append("[");

      if (arrayRange != null) {
        arrayRange.toString(params, builder);
      } else if (condition != null) {
        condition.toString(params, builder);
      } else if (arraySingleValues != null) {
        arraySingleValues.toString(params, builder);
      } else if (rightBinaryCondition != null) {
        rightBinaryCondition.toString(params, builder);
      }

      builder.append("]");
    } else if (methodCall != null) {
      methodCall.toString(params, builder);
    } else if (suffix != null) {
      builder.append(".");
      suffix.toString(params, builder);
    }
    if (next != null) {
      next.toString(params, builder);
    }
  }

  public void toGenericStatement(StringBuilder builder) {

    if (squareBrackets) {
      builder.append("[");

      if (arrayRange != null) {
        arrayRange.toGenericStatement(builder);
      } else if (condition != null) {
        condition.toGenericStatement(builder);
      } else if (arraySingleValues != null) {
        arraySingleValues.toGenericStatement(builder);
      } else if (rightBinaryCondition != null) {
        rightBinaryCondition.toGenericStatement(builder);
      }

      builder.append("]");
    } else if (methodCall != null) {
      methodCall.toGenericStatement(builder);
    } else if (suffix != null) {
      builder.append(".");
      suffix.toGenericStatement(builder);
    }
    if (next != null) {
      next.toGenericStatement(builder);
    }
  }

  public Object execute(Identifiable iCurrentRecord, Object result, CommandContext ctx) {
    if (ctx.getVariable("$current") == null) {
      ctx.setVariable("$current", iCurrentRecord);
    }
    if (methodCall != null) {
      result = methodCall.execute(result, ctx);
    } else if (suffix != null) {
      result = suffix.execute(result, ctx);
    } else if (arrayRange != null) {
      result = arrayRange.execute(iCurrentRecord, result, ctx);
    } else if (condition != null) {
      result = filterByCondition(result, ctx);
    } else if (arraySingleValues != null) {
      result = arraySingleValues.execute(iCurrentRecord, result, ctx);
    } else if (rightBinaryCondition != null) {
      result = rightBinaryCondition.execute(iCurrentRecord, result, ctx);
    }
    if (next != null) {
      result = next.execute(iCurrentRecord, result, ctx);
    }
    return result;
  }

  public Object execute(Result iCurrentRecord, Object result, CommandContext ctx) {
    if (ctx.getVariable("$current") == null) {
      ctx.setVariable("$current", iCurrentRecord);
    }
    if (methodCall != null) {
      result = methodCall.execute(result, ctx);
    } else if (suffix != null) {
      result = suffix.execute(result, ctx);
    } else if (arrayRange != null) {
      result = arrayRange.execute(iCurrentRecord, result, ctx);
    } else if (condition != null) {
      result = filterByCondition(result, ctx);
    } else if (arraySingleValues != null) {
      result = arraySingleValues.execute(iCurrentRecord, result, ctx);
    } else if (rightBinaryCondition != null) {
      result = rightBinaryCondition.execute(iCurrentRecord, result, ctx);
    }
    if (next != null) {
      result = next.execute(iCurrentRecord, result, ctx);
    }
    return result;
  }

  private Object filterByCondition(Object iResult, CommandContext ctx) {
    if (iResult == null) {
      return null;
    }
    List<Object> result = new ArrayList<Object>();
    if (iResult.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(iResult); i++) {
        Object item = Array.get(iResult, i);
        if (condition.evaluate(item, ctx)) {
          result.add(item);
        }
      }
      return result;
    }
    if (iResult instanceof Identifiable) {
      iResult = Collections.singleton(iResult);
    }
    if (iResult instanceof Iterable) {
      iResult = ((Iterable) iResult).iterator();
    }
    if (iResult instanceof Iterator) {
      while (((Iterator) iResult).hasNext()) {
        Object item = ((Iterator) iResult).next();
        if (condition.evaluate(item, ctx)) {
          result.add(item);
        }
      }
    }
    return result;
  }

  public boolean needsAliases(Set<String> aliases) {
    if (condition != null && condition.needsAliases(aliases)) {
      return true;
    }

    if (arraySingleValues != null && arraySingleValues.needsAliases(aliases)) {
      return true;
    }

    if (arrayRange != null && arrayRange.needsAliases(aliases)) {
      return true;
    }

    if (rightBinaryCondition != null && rightBinaryCondition.needsAliases(aliases)) {
      return true;
    }

    if (methodCall != null && methodCall.needsAliases(aliases)) {
      return true;
    }

    return next != null && next.needsAliases(aliases);
  }

  public SQLModifier copy() {
    SQLModifier result = new SQLModifier(-1);
    result.squareBrackets = squareBrackets;
    result.arrayRange = arrayRange == null ? null : arrayRange.copy();
    result.condition = condition == null ? null : condition.copy();
    result.arraySingleValues = arraySingleValues == null ? null : arraySingleValues.copy();
    result.rightBinaryCondition = rightBinaryCondition == null ? null : rightBinaryCondition.copy();
    result.methodCall = methodCall == null ? null : methodCall.copy();
    result.suffix = suffix == null ? null : suffix.copy();
    result.next = next == null ? null : next.copy();

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

    SQLModifier oModifier = (SQLModifier) o;

    if (squareBrackets != oModifier.squareBrackets) {
      return false;
    }
    if (!Objects.equals(arrayRange, oModifier.arrayRange)) {
      return false;
    }
    if (!Objects.equals(condition, oModifier.condition)) {
      return false;
    }
    if (!Objects.equals(arraySingleValues, oModifier.arraySingleValues)) {
      return false;
    }
    if (!Objects.equals(rightBinaryCondition, oModifier.rightBinaryCondition)) {
      return false;
    }
    if (!Objects.equals(methodCall, oModifier.methodCall)) {
      return false;
    }
    if (!Objects.equals(suffix, oModifier.suffix)) {
      return false;
    }
    return Objects.equals(next, oModifier.next);
  }

  @Override
  public int hashCode() {
    int result = (squareBrackets ? 1 : 0);
    result = 31 * result + (arrayRange != null ? arrayRange.hashCode() : 0);
    result = 31 * result + (condition != null ? condition.hashCode() : 0);
    result = 31 * result + (arraySingleValues != null ? arraySingleValues.hashCode() : 0);
    result = 31 * result + (rightBinaryCondition != null ? rightBinaryCondition.hashCode() : 0);
    result = 31 * result + (methodCall != null ? methodCall.hashCode() : 0);
    result = 31 * result + (suffix != null ? suffix.hashCode() : 0);
    result = 31 * result + (next != null ? next.hashCode() : 0);
    return result;
  }

  public void extractSubQueries(SubQueryCollector collector) {
    if (arrayRange != null) {
      arrayRange.extractSubQueries(collector);
    }
    if (condition != null) {
      condition.extractSubQueries(collector);
    }
    if (arraySingleValues != null) {
      arraySingleValues.extractSubQueries(collector);
    }
    if (rightBinaryCondition != null) {
      rightBinaryCondition.extractSubQueries(collector);
    }
    if (methodCall != null) {
      methodCall.extractSubQueries(collector);
    }
    if (suffix != null) {
      suffix.extractSubQueries(collector);
    }
    if (next != null) {
      next.extractSubQueries(collector);
    }
  }

  public boolean refersToParent() {
    if (arrayRange != null && arrayRange.refersToParent()) {
      return true;
    }
    if (condition != null && condition.refersToParent()) {
      return true;
    }

    if (arraySingleValues != null && arraySingleValues.refersToParent()) {
      return true;
    }
    if (rightBinaryCondition != null && rightBinaryCondition.refersToParent()) {
      return true;
    }
    if (methodCall != null && methodCall.refersToParent()) {
      return true;
    }
    return suffix != null && suffix.refersToParent();
  }

  protected void setValue(Result currentRecord, Object target, Object value,
      CommandContext ctx) {
    if (next == null) {
      doSetValue(currentRecord, target, value, ctx);
    } else {
      Object newTarget = calculateLocal(currentRecord, target, ctx);
      if (newTarget != null) {
        next.setValue(currentRecord, newTarget, value, ctx);
      }
    }
  }

  private void doSetValue(Result currentRecord, Object target, Object value,
      CommandContext ctx) {
    value = SQLUpdateItem.convertResultToDocument(value);
    value = SQLUpdateItem.cleanValue(value);
    if (methodCall != null) {
      // do nothing
    } else if (suffix != null) {
      suffix.setValue(target, value, ctx);
    } else if (arrayRange != null) {
      arrayRange.setValue(target, value, ctx);
    } else if (condition != null) {
      // TODO
      throw new UnsupportedOperationException(
          "SET value on conditional filtering will be supported soon");
    } else if (arraySingleValues != null) {
      arraySingleValues.setValue(currentRecord, target, value, ctx);
    } else if (rightBinaryCondition != null) {
      throw new UnsupportedOperationException(
          "SET value on conditional filtering will be supported soon");
    }
  }

  private Object calculateLocal(Result currentRecord, Object target, CommandContext ctx) {
    if (methodCall != null) {
      return methodCall.execute(target, ctx);
    } else if (suffix != null) {
      return suffix.execute(target, ctx);
    } else if (arrayRange != null) {
      return arrayRange.execute(currentRecord, target, ctx);
    } else if (condition != null) {
      if (target instanceof Result || target instanceof Identifiable || target instanceof Map) {
        if (condition.evaluate(target, ctx)) {
          return target;
        } else {
          return null;
        }
      } else if (MultiValue.isMultiValue(target)) {
        List<Object> result = new ArrayList<>();
        for (Object o : MultiValue.getMultiValueIterable(target)) {
          if (condition.evaluate(o, ctx)) {
            result.add(o);
          }
        }
        return result;
      } else {
        return null;
      }
    } else if (arraySingleValues != null) {
      return arraySingleValues.execute(currentRecord, target, ctx);
    } else if (rightBinaryCondition != null) {
      return rightBinaryCondition.execute(currentRecord, target, ctx);
    }
    return null;
  }

  public void applyRemove(
      Object currentValue, ResultInternal originalRecord, CommandContext ctx) {
    if (next != null) {
      Object val = calculateLocal(originalRecord, currentValue, ctx);
      next.applyRemove(val, originalRecord, ctx);
    } else {
      if (arrayRange != null) {
        arrayRange.applyRemove(currentValue, originalRecord, ctx);
      } else if (condition != null) {
        // TODO
        throw new UnsupportedOperationException(
            "Remove on conditional filtering will be supported soon");
      } else if (arraySingleValues != null) {
        arraySingleValues.applyRemove(currentValue, originalRecord, ctx);
      } else if (rightBinaryCondition != null) {
        throw new UnsupportedOperationException(
            "Remove on conditional filtering will be supported soon");
      } else if (suffix != null) {
        suffix.applyRemove(currentValue, ctx);
      } else {
        throw new CommandExecutionException("cannot apply REMOVE " + this);
      }
    }
  }

  public Result serialize(DatabaseSessionInternal db) {
    ResultInternal result = new ResultInternal(db);
    result.setProperty("squareBrackets", squareBrackets);
    if (arrayRange != null) {
      result.setProperty("arrayRange", arrayRange.serialize(db));
    }
    if (condition != null) {
      result.setProperty("condition", condition.serialize(db));
    }
    if (arraySingleValues != null) {
      result.setProperty("arraySingleValues", arraySingleValues.serialize(db));
    }
    if (rightBinaryCondition != null) {
      result.setProperty("rightBinaryCondition", rightBinaryCondition.serialize(db));
    }
    if (methodCall != null) {
      result.setProperty("methodCall", methodCall.serialize(db));
    }
    if (suffix != null) {
      result.setProperty("suffix", suffix.serialize(db));
    }
    if (next != null) {
      result.setProperty("next", next.serialize(db));
    }
    return result;
  }

  public void deserialize(Result fromResult) {
    squareBrackets = fromResult.getProperty("squareBrackets");

    if (fromResult.getProperty("arrayRange") != null) {
      arrayRange = new SQLArrayRangeSelector(-1);
      arrayRange.deserialize(fromResult.getProperty("arrayRange"));
    }
    if (fromResult.getProperty("condition") != null) {
      condition = new SQLOrBlock(-1);
      condition.deserialize(fromResult.getProperty("condition"));
    }
    if (fromResult.getProperty("arraySingleValues") != null) {
      arraySingleValues = new SQLArraySingleValuesSelector(-1);
      arraySingleValues.deserialize(fromResult.getProperty("arraySingleValues"));
    }
    if (fromResult.getProperty("rightBinaryCondition") != null) {
      rightBinaryCondition = new SQLRightBinaryCondition(-1);
      rightBinaryCondition.deserialize(fromResult.getProperty("arraySingleValues"));
    }
    if (fromResult.getProperty("methodCall") != null) {
      methodCall = new SQLMethodCall(-1);
      methodCall.deserialize(fromResult.getProperty("methodCall"));
    }
    if (fromResult.getProperty("suffix") != null) {
      suffix = new SQLSuffixIdentifier(-1);
      suffix.deserialize(fromResult.getProperty("suffix"));
    }

    if (fromResult.getProperty("next") != null) {
      next = new SQLModifier(-1);
      next.deserialize(fromResult.getProperty("next"));
    }
  }

  public boolean isCacheable(DatabaseSessionInternal session) {
    if (arrayRange != null || arraySingleValues != null || rightBinaryCondition != null) {
      return false; // TODO enhance a bit
    }
    if (condition != null && !condition.isCacheable(session)) {
      return false;
    }
    if (methodCall != null && !methodCall.isCacheable(session)) {
      return false;
    }
    if (suffix != null && !suffix.isCacheable()) {
      return false;
    }
    return next == null || next.isCacheable(session);
  }

  public boolean isIndexChain(CommandContext ctx, SchemaClassInternal clazz) {
    if (suffix != null && suffix.isBaseIdentifier()) {
      var prop = clazz.getPropertyInternal(suffix.getIdentifier().getStringValue());
      if (prop != null
          && prop.getAllIndexesInternal(ctx.getDatabase()).stream()
          .anyMatch(idx -> idx.getDefinition().getFields().size() == 1)) {
        if (next != null) {
          var linkedClazz = (SchemaClassInternal) prop.getLinkedClass();
          return next.isIndexChain(ctx, linkedClazz);
        }
        return true;
      }
    }
    return false;
  }

  public Optional<MetadataPath> getPath() {
    if (this.suffix != null && this.suffix.isBaseIdentifier()) {
      if (this.next != null) {
        Optional<MetadataPath> path = this.next.getPath();
        if (path.isPresent()) {
          path.get().addPre(suffix.identifier.getValue());
        }
        return path;
      } else {
        return Optional.of(new MetadataPath(suffix.identifier.getValue()));
      }
    }
    return Optional.empty();
  }
}
/* JavaCC - OriginalChecksum=39c21495d02f9b5007b4a2d6915496e1 (do not edit this line) */
