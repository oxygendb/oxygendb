/* Generated By:JJTree: Do not edit this line. OAlterSystemRoleStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OServerCommandContext;
import com.orientechnologies.orient.core.db.OSystemDatabase;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.OSecurityInternal;
import com.orientechnologies.orient.core.metadata.security.OSecurityPolicyImpl;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.resultset.OExecutionStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OAlterSystemRoleStatement extends OSimpleExecServerStatement {

  static class Op {

    protected static int TYPE_ADD = 0;
    protected static int TYPE_REMOVE = 1;

    Op(int type, OSecurityResourceSegment resource, OIdentifier policyName) {
      this.type = type;
      this.resource = resource;
      this.policyName = policyName;
    }

    protected final int type;
    protected final OSecurityResourceSegment resource;
    protected final OIdentifier policyName;
  }

  protected OIdentifier name;
  protected List<Op> operations = new ArrayList<>();

  public OAlterSystemRoleStatement(int id) {
    super(id);
  }

  public OAlterSystemRoleStatement(OrientSql p, int id) {
    super(p, id);
  }

  public void addOperation(Op operation) {
    this.operations.add(operation);
  }

  @Override
  public OExecutionStream executeSimple(OServerCommandContext ctx) {

    OSystemDatabase systemDb = ctx.getServer().getSystemDatabase();

    return systemDb.executeWithDB(
        (db) -> {
          List<OResult> rs = new ArrayList<>();

          OSecurityInternal security = db.getSharedContext().getSecurity();

          ORole role = db.getMetadata().getSecurity().getRole(name.getStringValue());
          if (role == null) {
            throw new OCommandExecutionException("role not found: " + name.getStringValue());
          }
          for (Op op : operations) {
            OResultInternal result = new OResultInternal();
            result.setProperty("operation", "alter system role");
            result.setProperty("name", name.getStringValue());
            result.setProperty("resource", op.resource.toString());
            if (op.type == Op.TYPE_ADD) {
              OSecurityPolicyImpl policy =
                  security.getSecurityPolicy(db, op.policyName.getStringValue());
              result.setProperty("operation", "ADD POLICY");
              result.setProperty("policyName", op.policyName.getStringValue());
              try {
                security.setSecurityPolicy(db, role, op.resource.toString(), policy);
                result.setProperty("result", "OK");
              } catch (Exception e) {
                result.setProperty("result", "failure");
              }
            } else {
              result.setProperty("operation", "REMOVE POLICY");
              try {
                security.removeSecurityPolicy(db, role, op.resource.toString());
                result.setProperty("result", "OK");
              } catch (Exception e) {
                result.setProperty("result", "failure");
              }
            }
            rs.add(result);
          }
          return OExecutionStream.resultIterator(rs.iterator());
        });
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("ALTER SYSTEM ROLE ");
    name.toString(params, builder);

    for (Op operation : operations) {
      if (operation.type == OAlterRoleStatement.Op.TYPE_ADD) {
        builder.append(" SET POLICY ");
        operation.policyName.toString(params, builder);
        builder.append(" ON ");
        operation.resource.toString(params, builder);
      } else {
        builder.append(" REMOVE POLICY ON ");
        operation.resource.toString(params, builder);
      }
    }
  }
}
/* JavaCC - OriginalChecksum=50b6859b3a4d19767a526b979554bbdb (do not edit this line) */
