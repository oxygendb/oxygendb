/* Generated By:JJTree: Do not edit this line. SQLDropClusterStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.api.exception.CommandExecutionException;
import com.jetbrains.youtrack.db.api.schema.SchemaClass;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.ExecutionStream;
import java.util.Map;
import java.util.Objects;

public class SQLDropClusterStatement extends DDLStatement {

  protected SQLIdentifier name;
  protected SQLInteger id;
  protected boolean ifExists = false;

  public SQLDropClusterStatement(int id) {
    super(id);
  }

  public SQLDropClusterStatement(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public ExecutionStream executeDDL(CommandContext ctx) {
    var database = ctx.getDatabase();
    // CHECK IF ANY CLASS IS USING IT
    final int clusterId;
    if (id != null) {
      clusterId = id.getValue().intValue();
    } else {
      clusterId = database.getStorage().getClusterIdByName(name.getStringValue());
      if (clusterId < 0) {
        if (ifExists) {
          return ExecutionStream.empty();
        } else {
          throw new CommandExecutionException("Cluster not found: " + name);
        }
      }
    }
    for (SchemaClass iClass : database.getMetadata().getSchema().getClasses()) {
      for (int i : iClass.getClusterIds()) {
        if (i == clusterId) {
          // IN USE
          throw new CommandExecutionException(
              "Cannot drop cluster "
                  + clusterId
                  + " because it's used by class "
                  + iClass.getName());
        }
      }
    }

    // REMOVE CACHE OF COMMAND RESULTS IF ACTIVE
    String clusterName = database.getClusterNameById(clusterId);
    if (clusterName == null) {
      if (ifExists) {
        return ExecutionStream.empty();
      } else {
        throw new CommandExecutionException("Cluster not found: " + clusterId);
      }
    }

    database.dropCluster(clusterId);

    ResultInternal result = new ResultInternal(database);
    result.setProperty("operation", "drop cluster");
    result.setProperty("clusterName", name == null ? null : name.getStringValue());
    result.setProperty("clusterId", id == null ? null : id.getValue());
    return ExecutionStream.singleton(result);
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("DROP CLUSTER ");
    if (name != null) {
      name.toString(params, builder);
    } else {
      id.toString(params, builder);
    }
    if (ifExists) {
      builder.append(" IF EXISTS");
    }
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    builder.append("DROP CLUSTER ");
    if (name != null) {
      name.toGenericStatement(builder);
    } else {
      id.toGenericStatement(builder);
    }
    if (ifExists) {
      builder.append(" IF EXISTS");
    }
  }

  @Override
  public SQLDropClusterStatement copy() {
    SQLDropClusterStatement result = new SQLDropClusterStatement(-1);
    result.name = name == null ? null : name.copy();
    result.id = id == null ? null : id.copy();
    result.ifExists = this.ifExists;
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

    SQLDropClusterStatement that = (SQLDropClusterStatement) o;

    if (ifExists != that.ifExists) {
      return false;
    }
    if (!Objects.equals(name, that.name)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (id != null ? id.hashCode() : 0);
    result = 31 * result + (ifExists ? 1 : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=239ffe92e79e1d5c82976ed9814583ec (do not edit this line) */
