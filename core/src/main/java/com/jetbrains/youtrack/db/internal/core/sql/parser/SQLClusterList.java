/* Generated By:JJTree: Do not edit this line. SQLClusterList.java Version 4.3 */
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

public class SQLClusterList extends SimpleNode {

  protected List<SQLIdentifier> clusters = new ArrayList<SQLIdentifier>();

  public SQLClusterList(int id) {
    super(id);
  }

  public SQLClusterList(YouTrackDBSql p, int id) {
    super(p, id);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {

    builder.append("cluster:[");
    boolean first = true;
    for (SQLIdentifier id : clusters) {
      if (!first) {
        builder.append(",");
      }
      id.toString(params, builder);
      first = false;
    }
    builder.append("]");
  }

  public void toGenericStatement(StringBuilder builder) {
    builder.append("cluster:[");
    boolean first = true;
    for (SQLIdentifier id : clusters) {
      if (!first) {
        builder.append(",");
      }
      id.toGenericStatement(builder);
      first = false;
    }
    builder.append("]");
  }

  public List<SQLCluster> toListOfClusters() {
    List<SQLCluster> result = new ArrayList<>();
    for (SQLIdentifier id : clusters) {
      SQLCluster cluster = new SQLCluster(-1);
      cluster.clusterName = id.getStringValue();
      result.add(cluster);
    }
    return result;
  }

  public SQLClusterList copy() {
    SQLClusterList result = new SQLClusterList(-1);
    result.clusters = clusters.stream().map(x -> x.copy()).collect(Collectors.toList());
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

    SQLClusterList that = (SQLClusterList) o;

    return Objects.equals(clusters, that.clusters);
  }

  @Override
  public int hashCode() {
    return clusters != null ? clusters.hashCode() : 0;
  }

  public Result serialize(DatabaseSessionInternal db) {
    ResultInternal result = new ResultInternal(db);
    if (clusters != null) {
      result.setProperty(
          "clusters", clusters.stream().map(x -> x.serialize(db)).collect(Collectors.toList()));
    }
    return result;
  }

  public void deserialize(Result fromResult) {
    if (fromResult.getProperty("clusters") != null) {
      clusters = new ArrayList<>();
      List<Result> ser = fromResult.getProperty("clusters");
      for (Result item : ser) {
        SQLIdentifier id = SQLIdentifier.deserialize(item);
        clusters.add(id);
      }
    }
  }

  public void addCluster(SQLIdentifier cluster) {
    this.clusters.add(cluster);
  }
}
/* JavaCC - OriginalChecksum=bd90ffa0b9d17f204b3cf2d47eedb409 (do not edit this line) */
