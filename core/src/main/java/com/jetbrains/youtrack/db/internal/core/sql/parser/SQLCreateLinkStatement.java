/* Generated By:JJTree: Do not edit this line. SQLCreateLinkStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

import com.jetbrains.youtrack.db.api.exception.BaseException;
import com.jetbrains.youtrack.db.api.exception.CommandExecutionException;
import com.jetbrains.youtrack.db.api.exception.CommandSQLParsingException;
import com.jetbrains.youtrack.db.api.query.ResultSet;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.api.record.RID;
import com.jetbrains.youtrack.db.api.schema.PropertyType;
import com.jetbrains.youtrack.db.api.schema.SchemaClass;
import com.jetbrains.youtrack.db.api.schema.SchemaProperty;
import com.jetbrains.youtrack.db.internal.core.command.CommandContext;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.record.LinkList;
import com.jetbrains.youtrack.db.internal.core.db.record.LinkSet;
import com.jetbrains.youtrack.db.internal.core.record.impl.DocumentHelper;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.sql.executor.ResultInternal;
import com.jetbrains.youtrack.db.internal.core.sql.executor.resultset.ExecutionStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SQLCreateLinkStatement extends SQLSimpleExecStatement {

  protected SQLIdentifier name;
  protected SQLIdentifier type;
  protected SQLIdentifier sourceClass;
  protected SQLIdentifier sourceField;
  protected SQLRecordAttribute sourceRecordAttr;
  protected SQLIdentifier destClass;
  protected SQLIdentifier destField;
  protected SQLRecordAttribute destRecordAttr;
  protected boolean inverse = false;

  boolean breakExec = false; // for timeout

  public SQLCreateLinkStatement(int id) {
    super(id);
  }

  public SQLCreateLinkStatement(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public ExecutionStream executeSimple(CommandContext ctx) {
    Object total = execute(ctx);
    ResultInternal result = new ResultInternal(ctx.getDatabase());
    result.setProperty("operation", "create link");
    result.setProperty("name", name.getValue());
    result.setProperty("count", total);
    result.setProperty("fromClass", sourceClass.getStringValue());
    result.setProperty("toClass", destClass.getStringValue());
    return ExecutionStream.singleton(result);
  }

  /**
   * Execute the CREATE LINK.
   */
  private Object execute(CommandContext ctx) {
    if (destField == null) {
      throw new CommandExecutionException(
          "Cannot execute the command because it has not been parsed yet");
    }

    final DatabaseSessionInternal database = ctx.getDatabase();
    if (database.getDatabaseOwner() == null) {
      throw new CommandSQLParsingException(
          "This command supports only the database type DatabaseDocumentTx and type '"
              + database.getClass()
              + "' was found");
    }

    final DatabaseSessionInternal db = database.getDatabaseOwner();

    SchemaClass sourceClass =
        database
            .getMetadata()
            .getImmutableSchemaSnapshot()
            .getClass(this.sourceClass.getStringValue());
    if (sourceClass == null) {
      throw new CommandExecutionException(
          "Source class '" + this.sourceClass.getStringValue() + "' not found");
    }

    SchemaClass destClass =
        database
            .getMetadata()
            .getImmutableSchemaSnapshot()
            .getClass(this.destClass.getStringValue());
    if (destClass == null) {
      throw new CommandExecutionException(
          "Destination class '" + this.destClass.getStringValue() + "' not found");
    }

    String cmd = "select from ";
    if (destField != null && !DocumentHelper.ATTRIBUTE_RID.equals(destField.value)) {
      cmd = "select from " + this.destClass + " where " + destField + " = ";
    }

    long[] total = new long[1];

    String linkName = name == null ? sourceField.getStringValue() : name.getStringValue();

    var documentSourceClass = sourceClass;
    var txCmd = cmd;
    try {
      final boolean[] multipleRelationship = new boolean[1];

      PropertyType linkType = PropertyType.valueOf(
          type.getStringValue().toUpperCase(Locale.ENGLISH));
      if (linkType != null)
      // DETERMINE BASED ON FORCED TYPE
      {
        multipleRelationship[0] =
            linkType == PropertyType.LINKSET || linkType == PropertyType.LINKLIST;
      } else {
        multipleRelationship[0] = false;
      }

      var txLinkType = linkType;
      var txDestClass = destClass;

      List<EntityImpl> result;
      Object oldValue;
      EntityImpl target;

      // BROWSE ALL THE RECORDS OF THE SOURCE CLASS
      for (EntityImpl entity : db.browseClass(documentSourceClass.getName())) {
        if (breakExec) {
          break;
        }
        Object value = entity.getProperty(sourceField.getStringValue());

        if (value != null) {
          if (value instanceof EntityImpl || value instanceof RID) {
            // ALREADY CONVERTED
          } else if (value instanceof Collection<?>) {
            // TODO
          } else {
            // SEARCH THE DESTINATION RECORD
            target = null;

            if (destField != null
                && !DocumentHelper.ATTRIBUTE_RID.equals(destField.value)
                && value instanceof String) {
              if (((String) value).length() == 0) {
                value = null;
              } else {
                value = "'" + value + "'";
              }
            }

            try (ResultSet rs = database.query(txCmd + value)) {
              result = toList(rs);
            }

            if (result == null || result.size() == 0) {
              value = null;
            } else if (result.size() > 1) {
              throw new CommandExecutionException(
                  "Cannot create link because multiple records was found in class '"
                      + txDestClass.getName()
                      + "' with value "
                      + value
                      + " in field '"
                      + destField
                      + "'");
            } else {
              target = result.get(0);
              value = target;
            }

            if (target != null && inverse) {
              // INVERSE RELATIONSHIP
              oldValue = target.getProperty(linkName);

              if (oldValue != null) {
                if (!multipleRelationship[0]) {
                  multipleRelationship[0] = true;
                }

                Collection<EntityImpl> coll;
                if (oldValue instanceof Collection) {
                  // ADD IT IN THE EXISTENT COLLECTION
                  coll = (Collection<EntityImpl>) oldValue;
                  target.setDirty();
                } else {
                  // CREATE A NEW COLLECTION FOR BOTH
                  coll = new ArrayList<EntityImpl>(2);
                  target.setProperty(linkName, coll);
                  coll.add((EntityImpl) oldValue);
                }
                coll.add(entity);
              } else {
                if (txLinkType != null) {
                  if (txLinkType == PropertyType.LINKSET) {
                    value = new LinkSet(target);
                    ((Set<Identifiable>) value).add(entity);
                  } else if (txLinkType == PropertyType.LINKLIST) {
                    value = new LinkList(target);
                    ((LinkList) value).add(entity);
                  } else
                  // IGNORE THE TYPE, SET IT AS LINK
                  {
                    value = entity;
                  }
                } else {
                  value = entity;
                }

                target.setProperty(linkName, value);
              }
              target.save();

            } else {

              // SET THE REFERENCE
              entity.setProperty(linkName, value);
              entity.save();
            }

            total[0]++;
          }
        }
      }

      if (total[0] > 0) {
        if (inverse) {
          // REMOVE THE OLD PROPERTY IF ANY
          SchemaProperty prop = destClass.getProperty(linkName);
          destClass = db.getMetadata().getSchema().getClass(this.destClass.getStringValue());
          if (prop != null) {
            if (linkType != prop.getType()) {
              throw new CommandExecutionException(
                  "Cannot create the link because the property '"
                      + linkName
                      + "' already exists for class "
                      + destClass.getName()
                      + " and has a different type - actual: "
                      + prop.getType()
                      + " expected: "
                      + linkType);
            }
          } else {
            throw new CommandExecutionException(
                "Cannot create the link because the property '"
                    + linkName
                    + "' does not exist in class '"
                    + destClass.getName()
                    + "'");
          }
        } else {
          // REMOVE THE OLD PROPERTY IF ANY
          SchemaProperty prop = sourceClass.getProperty(linkName);
          sourceClass = db.getMetadata().getSchema().getClass(this.destClass.getStringValue());
          if (prop != null) {
            if (prop.getType() != PropertyType.LINK) {
              throw new CommandExecutionException(
                  "Cannot create the link because the property '"
                      + linkName
                      + "' already exists for class "
                      + sourceClass.getName()
                      + " and has a different type - actual: "
                      + prop.getType()
                      + " expected: "
                      + PropertyType.LINK);
            }
          } else {
            throw new CommandExecutionException(
                "Cannot create the link because the property '"
                    + linkName
                    + "' does not exist in class '"
                    + sourceClass.getName()
                    + "'");
          }
        }
      }

    } catch (Exception e) {
      throw BaseException.wrapException(
          new CommandExecutionException("Error on creation of links"), e);
    }
    return total[0];
  }

  private List<EntityImpl> toList(ResultSet rs) {
    if (!rs.hasNext()) {
      return null;
    }
    List<EntityImpl> result = new ArrayList<>();
    while (rs.hasNext()) {
      result.add((EntityImpl) rs.next().getEntity().orElse(null));
    }
    return result;
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("CREATE LINK ");
    name.toString(params, builder);
    builder.append(" TYPE ");
    type.toString(params, builder);
    builder.append(" FROM ");
    sourceClass.toString(params, builder);
    builder.append(".");
    if (sourceField != null) {
      sourceField.toString(params, builder);
    } else {
      sourceRecordAttr.toString(params, builder);
    }
    builder.append(" TO ");
    destClass.toString(params, builder);
    builder.append(".");
    if (destField != null) {
      destField.toString(params, builder);
    } else {
      destRecordAttr.toString(params, builder);
    }
    if (inverse) {
      builder.append(" INVERSE");
    }
  }

  @Override
  public void toGenericStatement(StringBuilder builder) {
    builder.append("CREATE LINK ");
    name.toGenericStatement(builder);
    builder.append(" TYPE ");
    type.toGenericStatement(builder);
    builder.append(" FROM ");
    sourceClass.toGenericStatement(builder);
    builder.append(".");
    if (sourceField != null) {
      sourceField.toGenericStatement(builder);
    } else {
      sourceRecordAttr.toGenericStatement(builder);
    }
    builder.append(" TO ");
    destClass.toGenericStatement(builder);
    builder.append(".");
    if (destField != null) {
      destField.toGenericStatement(builder);
    } else {
      destRecordAttr.toGenericStatement(builder);
    }
    if (inverse) {
      builder.append(" INVERSE");
    }
  }

  @Override
  public SQLCreateLinkStatement copy() {
    SQLCreateLinkStatement result = new SQLCreateLinkStatement(-1);
    result.name = name == null ? null : name.copy();
    result.type = type == null ? null : type.copy();
    result.sourceClass = sourceClass == null ? null : sourceClass.copy();
    result.sourceField = sourceField == null ? null : sourceField.copy();
    result.sourceRecordAttr = sourceRecordAttr == null ? null : sourceRecordAttr.copy();
    result.destClass = destClass == null ? null : destClass.copy();
    result.destField = destField == null ? null : destField.copy();
    result.destRecordAttr = destRecordAttr == null ? null : destRecordAttr.copy();
    result.inverse = inverse;
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

    SQLCreateLinkStatement that = (SQLCreateLinkStatement) o;

    if (inverse != that.inverse) {
      return false;
    }
    if (!Objects.equals(name, that.name)) {
      return false;
    }
    if (!Objects.equals(type, that.type)) {
      return false;
    }
    if (!Objects.equals(sourceClass, that.sourceClass)) {
      return false;
    }
    if (!Objects.equals(sourceField, that.sourceField)) {
      return false;
    }
    if (!Objects.equals(sourceRecordAttr, that.sourceRecordAttr)) {
      return false;
    }
    if (!Objects.equals(destClass, that.destClass)) {
      return false;
    }
    if (!Objects.equals(destField, that.destField)) {
      return false;
    }
    return Objects.equals(destRecordAttr, that.destRecordAttr);
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (sourceClass != null ? sourceClass.hashCode() : 0);
    result = 31 * result + (sourceField != null ? sourceField.hashCode() : 0);
    result = 31 * result + (sourceRecordAttr != null ? sourceRecordAttr.hashCode() : 0);
    result = 31 * result + (destClass != null ? destClass.hashCode() : 0);
    result = 31 * result + (destField != null ? destField.hashCode() : 0);
    result = 31 * result + (destRecordAttr != null ? destRecordAttr.hashCode() : 0);
    result = 31 * result + (inverse ? 1 : 0);
    return result;
  }

  public SQLIdentifier getName() {
    return name;
  }

  public SQLIdentifier getType() {
    return type;
  }

  public SQLIdentifier getSourceClass() {
    return sourceClass;
  }

  public SQLIdentifier getSourceField() {
    return sourceField;
  }

  public SQLRecordAttribute getSourceRecordAttr() {
    return sourceRecordAttr;
  }

  public SQLIdentifier getDestClass() {
    return destClass;
  }

  public SQLIdentifier getDestField() {
    return destField;
  }

  public SQLRecordAttribute getDestRecordAttr() {
    return destRecordAttr;
  }

  public boolean isInverse() {
    return inverse;
  }
}
/* JavaCC - OriginalChecksum=de46c9bdaf3b36691764a78cd89d1c2b (do not edit this line) */
