package com.jetbrains.youtrack.db.internal.core.sql.executor;

import com.jetbrains.youtrack.db.internal.common.collection.OMultiValue;
import com.jetbrains.youtrack.db.internal.common.concur.YTTimeoutException;
import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.common.util.ORawPair;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.OExecutionThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandInterruptedException;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.index.OCompositeKey;
import com.jetbrains.youtrack.db.internal.core.index.OIndex;
import com.jetbrains.youtrack.db.internal.core.index.OIndexDefinition;
import com.jetbrains.youtrack.db.internal.core.index.OIndexDefinitionMultiValue;
import com.jetbrains.youtrack.db.internal.core.index.OIndexInternal;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.ExecutionStream;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.MultipleExecutionStream;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.OExecutionStreamProducer;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLAndBlock;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLBetweenCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLBinaryCompareOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLBinaryCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLBooleanExpression;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLCollection;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLContainsAnyCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLContainsKeyOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLContainsTextCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLContainsValueCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLContainsValueOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLEqualsCompareOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLExpression;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLGeOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLGtOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLInCondition;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLLeOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLLtOperator;
import com.jetbrains.youtrack.db.internal.core.sql.parser.SQLValueExpression;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class FetchFromIndexStep extends AbstractExecutionStep {

  protected IndexSearchDescriptor desc;

  private boolean orderAsc;

  private long count = 0;

  public FetchFromIndexStep(
      IndexSearchDescriptor desc, boolean orderAsc, CommandContext ctx, boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.desc = desc;
    this.orderAsc = orderAsc;

    YTDatabaseSessionInternal database = ctx.getDatabase();
    database.queryStartUsingViewIndex(desc.getIndex().getName());
  }

  @Override
  public ExecutionStream internalStart(CommandContext ctx) throws YTTimeoutException {
    var prev = this.prev;
    if (prev != null) {
      prev.start(ctx).close(ctx);
    }

    List<Stream<ORawPair<Object, YTRID>>> streams = init(desc, orderAsc, ctx);

    OExecutionStreamProducer res =
        new OExecutionStreamProducer() {
          private final Iterator<Stream<ORawPair<Object, YTRID>>> iter = streams.iterator();

          @Override
          public ExecutionStream next(CommandContext ctx) {
            Stream<ORawPair<Object, YTRID>> s = iter.next();
            return ExecutionStream.resultIterator(
                s.map((nextEntry) -> readResult(ctx, nextEntry)).iterator());
          }

          @Override
          public boolean hasNext(CommandContext ctx) {
            return iter.hasNext();
          }

          @Override
          public void close(CommandContext ctx) {
            while (iter.hasNext()) {
              iter.next().close();
            }
          }
        };
    return new MultipleExecutionStream(res).onClose(this::close);
  }

  private void close(CommandContext context) {
    updateIndexStats();
  }

  private YTResult readResult(CommandContext ctx, ORawPair<Object, YTRID> nextEntry) {
    if (OExecutionThreadLocal.isInterruptCurrentOperation()) {
      throw new YTCommandInterruptedException("The command has been interrupted");
    }
    count++;
    Object key = nextEntry.first;
    YTIdentifiable value = nextEntry.second;

    YTResultInternal result = new YTResultInternal(ctx.getDatabase());
    result.setProperty("key", convertKey(key));
    result.setProperty("rid", value);
    ctx.setVariable("$current", result);
    return result;
  }

  private static Object convertKey(Object key) {
    if (key instanceof OCompositeKey) {
      return new ArrayList<>(((OCompositeKey) key).getKeys());
    }
    return key;
  }

  private void updateIndexStats() {
    // stats
    OQueryStats stats = OQueryStats.get(ctx.getDatabase());
    OIndex index = desc.getIndex();
    SQLBooleanExpression condition = desc.getKeyCondition();
    SQLBinaryCondition additionalRangeCondition = desc.getAdditionalRangeCondition();
    if (index == null) {
      return; // this could happen, if not inited yet
    }
    String indexName = index.getName();
    boolean range = false;
    int size = 0;

    if (condition != null) {
      if (condition instanceof SQLBinaryCondition) {
        size = 1;
      } else if (condition instanceof SQLBetweenCondition) {
        size = 1;
        range = true;
      } else if (condition instanceof SQLAndBlock andBlock) {
        size = andBlock.getSubBlocks().size();
        SQLBooleanExpression lastOp = andBlock.getSubBlocks()
            .get(andBlock.getSubBlocks().size() - 1);
        if (lastOp instanceof SQLBinaryCondition) {
          SQLBinaryCompareOperator op = ((SQLBinaryCondition) lastOp).getOperator();
          range = op.isRangeOperator();
        }
      } else if (condition instanceof SQLInCondition) {
        size = 1;
      }
    }
    stats.pushIndexStats(indexName, size, range, additionalRangeCondition != null, count);
  }

  private static List<Stream<ORawPair<Object, YTRID>>> init(
      IndexSearchDescriptor desc, boolean isOrderAsc, CommandContext ctx) {

    OIndexInternal index = desc.getIndex().getInternal();
    SQLBooleanExpression condition = desc.getKeyCondition();
    SQLBinaryCondition additionalRangeCondition = desc.getAdditionalRangeCondition();

    if (index.getDefinition() == null) {
      return Collections.emptyList();
    }
    if (condition == null) {
      return processFlatIteration(ctx.getDatabase(), index, isOrderAsc);
    } else if (condition instanceof SQLBinaryCondition) {
      return processBinaryCondition(ctx.getDatabase(), index, condition, isOrderAsc, ctx);
    } else if (condition instanceof SQLBetweenCondition) {
      return processBetweenCondition(index, condition, isOrderAsc, ctx);
    } else if (condition instanceof SQLAndBlock) {
      return processAndBlock(index, condition, additionalRangeCondition, isOrderAsc, ctx);
    } else if (condition instanceof SQLInCondition) {
      return processInCondition(index, condition, ctx, isOrderAsc);
    } else {
      // TODO process containsAny
      throw new YTCommandExecutionException(
          "search for index for " + condition + " is not supported yet");
    }
  }

  private static List<Stream<ORawPair<Object, YTRID>>> processInCondition(
      OIndexInternal index, SQLBooleanExpression condition, CommandContext ctx, boolean orderAsc) {
    List<Stream<ORawPair<Object, YTRID>>> streams = new ArrayList<>();
    Set<Stream<ORawPair<Object, YTRID>>> acquiredStreams =
        Collections.newSetFromMap(new IdentityHashMap<>());
    OIndexDefinition definition = index.getDefinition();
    SQLInCondition inCondition = (SQLInCondition) condition;

    SQLExpression left = inCondition.getLeft();
    if (!left.toString().equalsIgnoreCase("key")) {
      throw new YTCommandExecutionException(
          "search for index for " + condition + " is not supported yet");
    }
    Object rightValue = inCondition.evaluateRight((YTResult) null, ctx);
    SQLEqualsCompareOperator equals = new SQLEqualsCompareOperator(-1);
    if (OMultiValue.isMultiValue(rightValue)) {
      for (Object item : OMultiValue.getMultiValueIterable(rightValue)) {
        if (item instanceof YTResult) {
          if (((YTResult) item).isEntity()) {
            item = ((YTResult) item).getEntity().orElseThrow(IllegalStateException::new);
          } else if (((YTResult) item).getPropertyNames().size() == 1) {
            item =
                ((YTResult) item).getProperty(
                    ((YTResult) item).getPropertyNames().iterator().next());
          }
        }

        Stream<ORawPair<Object, YTRID>> localCursor =
            createCursor(ctx.getDatabase(), index, equals, definition, item, orderAsc, condition);

        if (acquiredStreams.add(localCursor)) {
          streams.add(localCursor);
        }
      }
    } else {
      Stream<ORawPair<Object, YTRID>> stream =
          createCursor(
              ctx.getDatabase(), index, equals, definition, rightValue, orderAsc, condition);
      if (acquiredStreams.add(stream)) {
        streams.add(stream);
      }
    }

    return streams;
  }

  /**
   * it's not key = [...] but a real condition on field names, already ordered (field names will be
   * ignored)
   */
  private static List<Stream<ORawPair<Object, YTRID>>> processAndBlock(
      OIndexInternal index,
      SQLBooleanExpression condition,
      SQLBinaryCondition additionalRangeCondition,
      boolean isOrderAsc,
      CommandContext ctx) {
    SQLCollection fromKey = indexKeyFrom((SQLAndBlock) condition, additionalRangeCondition);
    SQLCollection toKey = indexKeyTo((SQLAndBlock) condition, additionalRangeCondition);
    boolean fromKeyIncluded = indexKeyFromIncluded((SQLAndBlock) condition,
        additionalRangeCondition);
    boolean toKeyIncluded = indexKeyToIncluded((SQLAndBlock) condition, additionalRangeCondition);
    return multipleRange(
        index,
        fromKey,
        fromKeyIncluded,
        toKey,
        toKeyIncluded,
        condition,
        isOrderAsc,
        additionalRangeCondition,
        ctx);
  }

  private static List<Stream<ORawPair<Object, YTRID>>> processFlatIteration(
      YTDatabaseSessionInternal session, OIndexInternal index, boolean isOrderAsc) {
    List<Stream<ORawPair<Object, YTRID>>> streams = new ArrayList<>();
    Set<Stream<ORawPair<Object, YTRID>>> acquiredStreams =
        Collections.newSetFromMap(new IdentityHashMap<>());

    var stream = fetchNullKeys(session, index);
    if (stream != null) {
      acquiredStreams.add(stream);
      streams.add(stream);
    }

    stream = isOrderAsc ? index.stream(session) : index.descStream(session);
    if (acquiredStreams.add(stream)) {
      streams.add(stream);
    }
    return streams;
  }

  private static Stream<ORawPair<Object, YTRID>> fetchNullKeys(YTDatabaseSessionInternal session,
      OIndexInternal index) {
    if (index.getDefinition().isNullValuesIgnored()) {
      return null;
    }

    return getStreamForNullKey(session, index);
  }

  private static List<Stream<ORawPair<Object, YTRID>>> multipleRange(
      OIndexInternal index,
      SQLCollection fromKey,
      boolean fromKeyIncluded,
      SQLCollection toKey,
      boolean toKeyIncluded,
      SQLBooleanExpression condition,
      boolean isOrderAsc,
      SQLBinaryCondition additionalRangeCondition,
      CommandContext ctx) {
    var db = ctx.getDatabase();
    List<Stream<ORawPair<Object, YTRID>>> streams = new ArrayList<>();
    Set<Stream<ORawPair<Object, YTRID>>> acquiredStreams =
        Collections.newSetFromMap(new IdentityHashMap<>());
    List<SQLCollection> secondValueCombinations = cartesianProduct(fromKey, ctx);
    List<SQLCollection> thirdValueCombinations = cartesianProduct(toKey, ctx);

    OIndexDefinition indexDef = index.getDefinition();

    for (int i = 0; i < secondValueCombinations.size(); i++) {

      Object secondValue = secondValueCombinations.get(i).execute((YTResult) null, ctx);
      if (secondValue instanceof List
          && ((List<?>) secondValue).size() == 1
          && indexDef.getFields().size() == 1
          && !(indexDef instanceof OIndexDefinitionMultiValue)) {
        secondValue = ((List<?>) secondValue).get(0);
      }
      secondValue = unboxOResult(secondValue);
      // TODO unwind collections!
      Object thirdValue = thirdValueCombinations.get(i).execute((YTResult) null, ctx);
      if (thirdValue instanceof List
          && ((List<?>) thirdValue).size() == 1
          && indexDef.getFields().size() == 1
          && !(indexDef instanceof OIndexDefinitionMultiValue)) {
        thirdValue = ((List<?>) thirdValue).get(0);
      }
      thirdValue = unboxOResult(thirdValue);

      try {
        secondValue = convertToIndexDefinitionTypes(db, condition, secondValue,
            indexDef.getTypes());
        thirdValue = convertToIndexDefinitionTypes(db, condition, thirdValue, indexDef.getTypes());
      } catch (Exception e) {
        // manage subquery that returns a single collection
        if (secondValue instanceof Collection && secondValue.equals(thirdValue)) {
          ((Collection) secondValue)
              .forEach(
                  item -> {
                    Stream<ORawPair<Object, YTRID>> stream;
                    Object itemVal =
                        convertToIndexDefinitionTypes(db, condition, item, indexDef.getTypes());
                    if (index.supportsOrderedIterations()) {

                      Object from = toBetweenIndexKey(db, indexDef, itemVal);
                      Object to = toBetweenIndexKey(db, indexDef, itemVal);
                      if (from == null && to == null) {
                        // manage null value explicitly, as the index API does not seem to work
                        // correctly in this
                        // case
                        stream = getStreamForNullKey(db, index);
                        if (acquiredStreams.add(stream)) {
                          streams.add(stream);
                        }
                      } else {
                        stream =
                            index.streamEntriesBetween(db,
                                from, fromKeyIncluded, to, toKeyIncluded, isOrderAsc);
                        if (acquiredStreams.add(stream)) {
                          streams.add(stream);
                        }
                      }

                    } else if (additionalRangeCondition == null
                        && allEqualities((SQLAndBlock) condition)) {
                      stream =
                          index.streamEntries(db,
                              toIndexKey(db, indexDef, itemVal), isOrderAsc);

                      if (acquiredStreams.add(stream)) {
                        streams.add(stream);
                      }

                    } else if (isFullTextIndex(index)) {
                      stream =
                          index.streamEntries(db,
                              toIndexKey(db, indexDef, itemVal), isOrderAsc);
                      if (acquiredStreams.add(stream)) {
                        streams.add(stream);
                      }
                    } else {
                      throw new UnsupportedOperationException(
                          "Cannot evaluate " + condition + " on index " + index);
                    }
                  });
        }

        // some problems in key conversion, so the params do not match the key types
        continue;
      }
      Stream<ORawPair<Object, YTRID>> stream;
      if (index.supportsOrderedIterations()) {

        Object from = toBetweenIndexKey(db, indexDef, secondValue);
        Object to = toBetweenIndexKey(db, indexDef, thirdValue);

        if (from == null && to == null) {
          // manage null value explicitly, as the index API does not seem to work correctly in this
          // case
          stream = getStreamForNullKey(db, index);
          if (acquiredStreams.add(stream)) {
            streams.add(stream);
          }
        } else {
          stream = index.streamEntriesBetween(db, from, fromKeyIncluded, to, toKeyIncluded,
              isOrderAsc);
          if (acquiredStreams.add(stream)) {
            streams.add(stream);
          }
        }

      } else if (additionalRangeCondition == null && allEqualities((SQLAndBlock) condition)) {
        stream =
            index.streamEntries(db, toIndexKey(ctx.getDatabase(), indexDef, secondValue),
                isOrderAsc);
        if (acquiredStreams.add(stream)) {
          streams.add(stream);
        }
      } else if (isFullTextIndex(index)) {
        stream =
            index.streamEntries(db, toIndexKey(ctx.getDatabase(), indexDef, secondValue),
                isOrderAsc);
        if (acquiredStreams.add(stream)) {
          streams.add(stream);
        }
      } else {
        throw new UnsupportedOperationException(
            "Cannot evaluate " + condition + " on index " + index);
      }
    }
    return streams;
  }

  private static boolean isFullTextIndex(OIndex index) {
    return index.getType().equalsIgnoreCase("FULLTEXT")
        && !index.getAlgorithm().equalsIgnoreCase("LUCENE");
  }

  private static Stream<ORawPair<Object, YTRID>> getStreamForNullKey(
      YTDatabaseSessionInternal session, OIndexInternal index) {
    final Stream<YTRID> stream = index.getRids(session, null);
    return stream.map((rid) -> new ORawPair<>(null, rid));
  }

  /**
   * this is for subqueries, when a YTResult is found
   *
   * <ul>
   *   <li>if it's a projection with a single column, the value is returned
   *   <li>if it's a document, the RID is returned
   * </ul>
   */
  private static Object unboxOResult(Object value) {
    if (value instanceof List) {
      try (Stream<?> stream = ((List<?>) value).stream()) {
        return stream.map(FetchFromIndexStep::unboxOResult).collect(Collectors.toList());
      }
    }
    if (value instanceof YTResult) {
      if (((YTResult) value).isEntity()) {
        return ((YTResult) value).getIdentity().orElse(null);
      }

      var props = ((YTResult) value).getPropertyNames();
      if (props.size() == 1) {
        return ((YTResult) value).getProperty(props.iterator().next());
      }
    }
    return value;
  }

  private static List<SQLCollection> cartesianProduct(SQLCollection key, CommandContext ctx) {
    return cartesianProduct(new SQLCollection(-1), key, ctx); // TODO
  }

  private static List<SQLCollection> cartesianProduct(
      SQLCollection head, SQLCollection key, CommandContext ctx) {
    if (key.getExpressions().isEmpty()) {
      return Collections.singletonList(head);
    }

    var db = ctx.getDatabase();
    SQLExpression nextElementInKey = key.getExpressions().get(0);
    Object value = nextElementInKey.execute(new YTResultInternal(db), ctx);
    if (value instanceof Iterable && !(value instanceof YTIdentifiable)) {
      List<SQLCollection> result = new ArrayList<>();
      for (Object elemInKey : (Collection<?>) value) {
        SQLCollection newHead = new SQLCollection(-1);
        for (SQLExpression exp : head.getExpressions()) {
          newHead.add(exp.copy());
        }
        newHead.add(toExpression(elemInKey));
        SQLCollection tail = key.copy();
        tail.getExpressions().remove(0);
        result.addAll(cartesianProduct(newHead, tail, ctx));
      }
      return result;
    } else {
      SQLCollection newHead = new SQLCollection(-1);
      for (SQLExpression exp : head.getExpressions()) {
        newHead.add(exp.copy());
      }
      newHead.add(nextElementInKey);
      SQLCollection tail = key.copy();
      tail.getExpressions().remove(0);
      return cartesianProduct(newHead, tail, ctx);
    }
  }

  private static SQLExpression toExpression(Object value) {
    return new SQLValueExpression(value);
  }

  private static Object convertToIndexDefinitionTypes(
      YTDatabaseSessionInternal session, SQLBooleanExpression condition, Object val,
      YTType[] types) {
    if (val == null) {
      return null;
    }
    if (OMultiValue.isMultiValue(val)) {
      List<Object> result = new ArrayList<>();
      int i = 0;
      for (Object o : OMultiValue.getMultiValueIterable(val)) {
        result.add(YTType.convert(session, o, types[i++].getDefaultJavaType()));
      }
      if (condition instanceof SQLAndBlock) {

        for (int j = 0; j < ((SQLAndBlock) condition).getSubBlocks().size(); j++) {
          SQLBooleanExpression subExp = ((SQLAndBlock) condition).getSubBlocks().get(j);
          if (subExp instanceof SQLBinaryCondition) {
            if (((SQLBinaryCondition) subExp).getOperator() instanceof SQLContainsKeyOperator) {
              Map<Object, Object> newValue = new HashMap<>();
              newValue.put(result.get(j), "");
              result.set(j, newValue);
            } else if (((SQLBinaryCondition) subExp).getOperator()
                instanceof SQLContainsValueOperator) {
              Map<Object, Object> newValue = new HashMap<>();
              newValue.put("", result.get(j));
              result.set(j, newValue);
            }
          } else if (subExp instanceof SQLContainsValueCondition) {
            Map<Object, Object> newValue = new HashMap<>();
            newValue.put("", result.get(j));
            result.set(j, newValue);
          }
        }
      }
      return result;
    }
    return YTType.convert(session, val, types[0].getDefaultJavaType());
  }

  private static boolean allEqualities(SQLAndBlock condition) {
    if (condition == null) {
      return false;
    }
    for (SQLBooleanExpression exp : condition.getSubBlocks()) {
      if (exp instanceof SQLBinaryCondition) {
        if (!(((SQLBinaryCondition) exp).getOperator() instanceof SQLEqualsCompareOperator)
            && !(((SQLBinaryCondition) exp).getOperator() instanceof SQLContainsKeyOperator)
            && !(((SQLBinaryCondition) exp).getOperator() instanceof SQLContainsValueOperator)) {
          return false;
        }
      } else if (!(exp instanceof SQLInCondition)) {
        return false;
      } // OK
    }
    return true;
  }

  private static List<Stream<ORawPair<Object, YTRID>>> processBetweenCondition(
      OIndexInternal index, SQLBooleanExpression condition, boolean isOrderAsc,
      CommandContext ctx) {
    List<Stream<ORawPair<Object, YTRID>>> streams = new ArrayList<>();

    OIndexDefinition definition = index.getDefinition();
    SQLExpression key = ((SQLBetweenCondition) condition).getFirst();
    if (!key.toString().equalsIgnoreCase("key")) {
      throw new YTCommandExecutionException(
          "search for index for " + condition + " is not supported yet");
    }
    SQLExpression second = ((SQLBetweenCondition) condition).getSecond();
    SQLExpression third = ((SQLBetweenCondition) condition).getThird();

    Object secondValue = second.execute((YTResult) null, ctx);
    secondValue = unboxOResult(secondValue);
    Object thirdValue = third.execute((YTResult) null, ctx);
    thirdValue = unboxOResult(thirdValue);
    var db = ctx.getDatabase();
    Stream<ORawPair<Object, YTRID>> stream =
        index.streamEntriesBetween(db,
            toBetweenIndexKey(db, definition, secondValue),
            true,
            toBetweenIndexKey(db, definition, thirdValue),
            true, isOrderAsc);
    streams.add(stream);
    return streams;
  }

  private static List<Stream<ORawPair<Object, YTRID>>> processBinaryCondition(
      YTDatabaseSessionInternal session,
      OIndexInternal index,
      SQLBooleanExpression condition,
      boolean isOrderAsc,
      CommandContext ctx) {
    List<Stream<ORawPair<Object, YTRID>>> streams = new ArrayList<>();
    Set<Stream<ORawPair<Object, YTRID>>> acquiredStreams =
        Collections.newSetFromMap(new IdentityHashMap<>());

    OIndexDefinition definition = index.getDefinition();
    SQLBinaryCompareOperator operator = ((SQLBinaryCondition) condition).getOperator();
    SQLExpression left = ((SQLBinaryCondition) condition).getLeft();
    if (!left.toString().equalsIgnoreCase("key")) {
      throw new YTCommandExecutionException(
          "search for index for " + condition + " is not supported yet");
    }
    Object rightValue = ((SQLBinaryCondition) condition).getRight().execute((YTResult) null, ctx);
    Stream<ORawPair<Object, YTRID>> stream =
        createCursor(session, index, operator, definition, rightValue, isOrderAsc, condition);
    if (acquiredStreams.add(stream)) {
      streams.add(stream);
    }

    return streams;
  }

  private static Collection<?> toIndexKey(
      YTDatabaseSessionInternal session, OIndexDefinition definition, Object rightValue) {
    if (definition.getFields().size() == 1 && rightValue instanceof Collection) {
      rightValue = ((Collection<?>) rightValue).iterator().next();
    }
    if (rightValue instanceof List) {
      rightValue = definition.createValue(session, (List<?>) rightValue);
    } else if (!(rightValue instanceof OCompositeKey)) {
      rightValue = definition.createValue(session, rightValue);
    }
    if (!(rightValue instanceof Collection)) {
      rightValue = Collections.singleton(rightValue);
    }
    return (Collection<?>) rightValue;
  }

  private static Object toBetweenIndexKey(
      YTDatabaseSessionInternal session, OIndexDefinition definition, Object rightValue) {
    if (definition.getFields().size() == 1 && rightValue instanceof Collection) {
      if (!((Collection<?>) rightValue).isEmpty()) {
        rightValue = ((Collection<?>) rightValue).iterator().next();
      } else {
        rightValue = null;
      }
    }

    if (rightValue instanceof Collection) {
      rightValue = definition.createValue(session, ((Collection<?>) rightValue).toArray());
    } else {
      rightValue = definition.createValue(session, rightValue);
    }

    return rightValue;
  }

  private static Stream<ORawPair<Object, YTRID>> createCursor(
      YTDatabaseSessionInternal session,
      OIndexInternal index,
      SQLBinaryCompareOperator operator,
      OIndexDefinition definition,
      Object value,
      boolean orderAsc,
      SQLBooleanExpression condition) {
    if (operator instanceof SQLEqualsCompareOperator
        || operator instanceof SQLContainsKeyOperator
        || operator instanceof SQLContainsValueOperator) {
      return index.streamEntries(session, toIndexKey(session, definition, value), orderAsc);
    } else if (operator instanceof SQLGeOperator) {
      return index.streamEntriesMajor(session, value, true, orderAsc);
    } else if (operator instanceof SQLGtOperator) {
      return index.streamEntriesMajor(session, value, false, orderAsc);
    } else if (operator instanceof SQLLeOperator) {
      return index.streamEntriesMinor(session, value, true, orderAsc);
    } else if (operator instanceof SQLLtOperator) {
      return index.streamEntriesMinor(session, value, false, orderAsc);
    } else {
      throw new YTCommandExecutionException(
          "search for index for " + condition + " is not supported yet");
    }
  }

  protected boolean isOrderAsc() {
    return orderAsc;
  }

  private static SQLCollection indexKeyFrom(SQLAndBlock keyCondition,
      SQLBinaryCondition additional) {
    SQLCollection result = new SQLCollection(-1);
    for (SQLBooleanExpression exp : keyCondition.getSubBlocks()) {
      SQLExpression res = exp.resolveKeyFrom(additional);
      if (res != null) {
        result.add(res);
      }
    }
    return result;
  }

  private static SQLCollection indexKeyTo(SQLAndBlock keyCondition, SQLBinaryCondition additional) {
    SQLCollection result = new SQLCollection(-1);
    for (SQLBooleanExpression exp : keyCondition.getSubBlocks()) {
      SQLExpression res = exp.resolveKeyTo(additional);
      if (res != null) {
        result.add(res);
      }
    }
    return result;
  }

  private static boolean indexKeyFromIncluded(
      SQLAndBlock keyCondition, SQLBinaryCondition additional) {
    SQLBooleanExpression exp =
        keyCondition.getSubBlocks().get(keyCondition.getSubBlocks().size() - 1);
    SQLBinaryCompareOperator additionalOperator =
        Optional.ofNullable(additional).map(SQLBinaryCondition::getOperator).orElse(null);
    if (exp instanceof SQLBinaryCondition) {
      SQLBinaryCompareOperator operator = ((SQLBinaryCondition) exp).getOperator();
      if (isGreaterOperator(operator)) {
        return isIncludeOperator(operator);
      } else {
        return additionalOperator == null
            || (isIncludeOperator(additionalOperator) && isGreaterOperator(additionalOperator));
      }
    } else if (exp instanceof SQLInCondition || exp instanceof SQLContainsAnyCondition) {
      return additional == null
          || (isIncludeOperator(additionalOperator) && isGreaterOperator(additionalOperator));
    } else if (exp instanceof SQLContainsTextCondition) {
      return true;
    } else if (exp instanceof SQLContainsValueCondition) {
      SQLBinaryCompareOperator operator = ((SQLContainsValueCondition) exp).getOperator();
      if (isGreaterOperator(operator)) {
        return isIncludeOperator(operator);
      } else {
        return additionalOperator == null
            || (isIncludeOperator(additionalOperator) && isGreaterOperator(additionalOperator));
      }
    } else {
      throw new UnsupportedOperationException("Cannot execute index query with " + exp);
    }
  }

  private static boolean isGreaterOperator(SQLBinaryCompareOperator operator) {
    if (operator == null) {
      return false;
    }
    return operator instanceof SQLGeOperator || operator instanceof SQLGtOperator;
  }

  private static boolean isLessOperator(SQLBinaryCompareOperator operator) {
    if (operator == null) {
      return false;
    }
    return operator instanceof SQLLeOperator || operator instanceof SQLLtOperator;
  }

  private static boolean isIncludeOperator(SQLBinaryCompareOperator operator) {
    if (operator == null) {
      return false;
    }
    return operator instanceof SQLGeOperator || operator instanceof SQLLeOperator;
  }

  private static boolean indexKeyToIncluded(SQLAndBlock keyCondition,
      SQLBinaryCondition additional) {
    SQLBooleanExpression exp =
        keyCondition.getSubBlocks().get(keyCondition.getSubBlocks().size() - 1);
    SQLBinaryCompareOperator additionalOperator =
        Optional.ofNullable(additional).map(SQLBinaryCondition::getOperator).orElse(null);
    if (exp instanceof SQLBinaryCondition) {
      SQLBinaryCompareOperator operator = ((SQLBinaryCondition) exp).getOperator();
      if (isLessOperator(operator)) {
        return isIncludeOperator(operator);
      } else {
        return additionalOperator == null
            || (isIncludeOperator(additionalOperator) && isLessOperator(additionalOperator));
      }
    } else if (exp instanceof SQLInCondition || exp instanceof SQLContainsAnyCondition) {
      return additionalOperator == null
          || (isIncludeOperator(additionalOperator) && isLessOperator(additionalOperator));
    } else if (exp instanceof SQLContainsTextCondition) {
      return true;
    } else if (exp instanceof SQLContainsValueCondition) {
      SQLBinaryCompareOperator operator = ((SQLContainsValueCondition) exp).getOperator();
      if (isLessOperator(operator)) {
        return isIncludeOperator(operator);
      } else {
        return additionalOperator == null
            || (isIncludeOperator(additionalOperator) && isLessOperator(additionalOperator));
      }
    } else {
      throw new UnsupportedOperationException("Cannot execute index query with " + exp);
    }
  }

  @Override
  public String prettyPrint(int depth, int indent) {
    String result =
        ExecutionStepInternal.getIndent(depth, indent)
            + "+ FETCH FROM INDEX "
            + desc.getIndex().getName();
    if (profilingEnabled) {
      result += " (" + getCostFormatted() + ")";
    }
    if (desc.getKeyCondition() != null) {
      String additional =
          Optional.ofNullable(desc.getAdditionalRangeCondition())
              .map(rangeCondition -> " and " + rangeCondition)
              .orElse("");
      result +=
          ("\n"
              + ExecutionStepInternal.getIndent(depth, indent)
              + "  "
              + desc.getKeyCondition()
              + additional);
    }

    return result;
  }

  @Override
  public YTResult serialize(YTDatabaseSessionInternal db) {
    YTResultInternal result = ExecutionStepInternal.basicSerialize(db, this);
    result.setProperty("indexName", desc.getIndex().getName());
    if (desc.getKeyCondition() != null) {
      result.setProperty("condition", desc.getKeyCondition().serialize(db));
    }
    if (desc.getAdditionalRangeCondition() != null) {
      result.setProperty(
          "additionalRangeCondition", desc.getAdditionalRangeCondition().serialize(db));
    }
    result.setProperty("orderAsc", orderAsc);
    return result;
  }

  @Override
  public void deserialize(YTResult fromResult) {
    try {
      ExecutionStepInternal.basicDeserialize(fromResult, this);
      String indexName = fromResult.getProperty("indexName");
      SQLBooleanExpression condition = null;
      if (fromResult.getProperty("condition") != null) {
        condition = SQLBooleanExpression.deserializeFromOResult(
            fromResult.getProperty("condition"));
      }
      SQLBinaryCondition additionalRangeCondition = null;
      if (fromResult.getProperty("additionalRangeCondition") != null) {
        additionalRangeCondition = new SQLBinaryCondition(-1);
        additionalRangeCondition.deserialize(fromResult.getProperty("additionalRangeCondition"));
      }
      YTDatabaseSessionInternal db = ODatabaseRecordThreadLocal.instance().get();
      OIndex index = db.getMetadata().getIndexManagerInternal().getIndex(db, indexName);
      desc = new IndexSearchDescriptor(index, condition, additionalRangeCondition, null);
      orderAsc = fromResult.getProperty("orderAsc");
    } catch (Exception e) {
      throw YTException.wrapException(new YTCommandExecutionException(""), e);
    }
  }

  @Override
  public void reset() {
    desc = null;
    count = 0;
  }

  @Override
  public boolean canBeCached() {
    return true;
  }

  @Override
  public ExecutionStep copy(CommandContext ctx) {
    return new FetchFromIndexStep(desc, this.orderAsc, ctx, this.profilingEnabled);
  }

  @Override
  public void close() {
    super.close();
  }

  public String getIndexName() {
    return desc.getIndex().getName();
  }
}