/* Generated By:JJTree: Do not edit this line. SQLFetchPlan.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.api.query.Result;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SQLFetchPlan extends SimpleNode {

  protected List<SQLFetchPlanItem> items = new ArrayList<SQLFetchPlanItem>();

  public SQLFetchPlan(int id) {
    super(id);
  }

  public SQLFetchPlan(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public void addItem(SQLFetchPlanItem item) {
    this.items.add(item);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("FETCHPLAN ");
    boolean first = true;
    for (SQLFetchPlanItem item : items) {
      if (!first) {
        builder.append(" ");
      }

      item.toString(params, builder);
      first = false;
    }
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    builder.append("FETCHPLAN ");
    boolean first = true;
    for (SQLFetchPlanItem item : items) {
      if (!first) {
        builder.append(" ");
      }

      item.toGenericStatement(builder);
      first = false;
    }
  }

  public SQLFetchPlan copy() {
    SQLFetchPlan result = new SQLFetchPlan(-1);
    result.items = items.stream().map(x -> x.copy()).collect(Collectors.toList());
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

    SQLFetchPlan that = (SQLFetchPlan) o;

    return Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return items != null ? items.hashCode() : 0;
  }

  public Result serialize(DatabaseSessionInternal db) {
    ResultInternal result = new ResultInternal(db);
    if (items != null) {
      result.setProperty(
          "items", items.stream().map(oFetchPlanItem -> oFetchPlanItem.serialize(db))
              .collect(Collectors.toList()));
    }
    return result;
  }

  public void deserialize(Result fromResult) {

    if (fromResult.getProperty("items") != null) {
      List<Result> ser = fromResult.getProperty("items");
      items = new ArrayList<>();
      for (Result r : ser) {
        SQLFetchPlanItem exp = new SQLFetchPlanItem(-1);
        exp.deserialize(r);
        items.add(exp);
      }
    }
  }
}
/* JavaCC - OriginalChecksum=b4cd86f2c6e8fc5e9dce8912389a1167 (do not edit this line) */
