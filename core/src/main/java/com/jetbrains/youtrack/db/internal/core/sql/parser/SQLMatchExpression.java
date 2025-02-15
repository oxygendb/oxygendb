/* Generated By:JJTree: Do not edit this line. SQLMatchExpression.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SQLMatchExpression extends SimpleNode {

  protected SQLMatchFilter origin;
  protected List<SQLMatchPathItem> items = new ArrayList<SQLMatchPathItem>();

  public SQLMatchExpression(int id) {
    super(id);
  }

  public SQLMatchExpression(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    origin.toString(params, builder);
    for (SQLMatchPathItem item : items) {
      item.toString(params, builder);
    }
  }

  public void toGenericStatement(StringBuilder builder) {
    origin.toGenericStatement(builder);
    for (SQLMatchPathItem item : items) {
      item.toGenericStatement(builder);
    }
  }

  @Override
  public SQLMatchExpression copy() {
    SQLMatchExpression result = new SQLMatchExpression(-1);
    result.origin = origin == null ? null : origin.copy();
    result.items =
        items == null ? null : items.stream().map(x -> x.copy()).collect(Collectors.toList());
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

    SQLMatchExpression that = (SQLMatchExpression) o;

    if (!Objects.equals(origin, that.origin)) {
      return false;
    }
    return Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    int result = origin != null ? origin.hashCode() : 0;
    result = 31 * result + (items != null ? items.hashCode() : 0);
    return result;
  }

  public SQLMatchFilter getOrigin() {
    return origin;
  }

  public void setOrigin(SQLMatchFilter origin) {
    this.origin = origin;
  }

  public List<SQLMatchPathItem> getItems() {
    return items;
  }

  public void setItems(List<SQLMatchPathItem> items) {
    this.items = items;
  }

  public void addItem(SQLMatchPathItem item) {
    this.items.add(item);
  }
}
/* JavaCC - OriginalChecksum=73491fb653c32baf66997290db29f370 (do not edit this line) */
