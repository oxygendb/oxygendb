/* Generated By:JJTree: Do not edit this line. SQLMultiMatchPathItem.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SQLMultiMatchPathItem extends SQLMatchPathItem {

  protected List<SQLMatchPathItem> items = new ArrayList<SQLMatchPathItem>();

  public SQLMultiMatchPathItem(int id) {
    super(id);
  }

  public SQLMultiMatchPathItem(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public boolean isBidirectional() {
    return false;
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append(".(");
    for (SQLMatchPathItem item : items) {
      item.toString(params, builder);
    }
    builder.append(")");
    if (filter != null) {
      filter.toString(params, builder);
    }
  }

  public void toGenericStatement(StringBuilder builder) {
    builder.append(".(");
    for (SQLMatchPathItem item : items) {
      item.toGenericStatement(builder);
    }
    builder.append(")");
    if (filter != null) {
      filter.toGenericStatement(builder);
    }
  }

  protected Iterable<Identifiable> traversePatternEdge(
      SQLMatchStatement.MatchContext matchContext,
      Identifiable startingPoint,
      CommandContext iCommandContext) {
    Set<Identifiable> result = new HashSet<Identifiable>();
    result.add(startingPoint);
    for (SQLMatchPathItem subItem : items) {
      Set<Identifiable> startingPoints = result;
      result = new HashSet<Identifiable>();
      for (Identifiable sp : startingPoints) {
        Iterable<Identifiable> subResult =
            subItem.executeTraversal(matchContext, iCommandContext, sp, 0);
        if (subResult instanceof Collection) {
          result.addAll((Collection) subResult);
        } else {
          for (Identifiable id : subResult) {
            result.add(id);
          }
        }
      }
    }
    return result;
  }

  @Override
  public SQLMultiMatchPathItem copy() {
    SQLMultiMatchPathItem result = (SQLMultiMatchPathItem) super.copy();
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
    if (!super.equals(o)) {
      return false;
    }

    SQLMultiMatchPathItem that = (SQLMultiMatchPathItem) o;

    return Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (items != null ? items.hashCode() : 0);
    return result;
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
/* JavaCC - OriginalChecksum=f18f107768de80b8941f166d7fafb3c0 (do not edit this line) */
