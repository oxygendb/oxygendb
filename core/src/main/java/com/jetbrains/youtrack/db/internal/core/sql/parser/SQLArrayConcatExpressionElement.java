/* Generated By:JJTree: Do not edit this line. SQLArrayConcatExpressionElement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.jetbrains.youtrack.db.internal.core.sql.parser;

public class SQLArrayConcatExpressionElement extends SQLExpression {

  public SQLArrayConcatExpressionElement(int id) {
    super(id);
  }

  public SQLArrayConcatExpressionElement(YouTrackDBSql p, int id) {
    super(p, id);
  }

  @Override
  public SQLArrayConcatExpressionElement copy() {
    SQLArrayConcatExpressionElement result = new SQLArrayConcatExpressionElement(-1);
    result.singleQuotes = singleQuotes;
    result.doubleQuotes = doubleQuotes;
    result.isNull = isNull;
    result.rid = rid == null ? null : rid.copy();
    result.mathExpression = mathExpression == null ? null : mathExpression.copy();
    result.json = json == null ? null : json.copy();
    result.booleanValue = booleanValue;

    return result;
  }
}
/* JavaCC - OriginalChecksum=a37b12bac47f1771db27ce370d09f2f5 (do not edit this line) */
