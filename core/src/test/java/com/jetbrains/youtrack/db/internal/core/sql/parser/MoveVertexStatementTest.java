package com.jetbrains.youtrack.db.internal.core.sql.parser;

import org.junit.Test;

public class MoveVertexStatementTest extends ParserTestAbstract {

  @Test
  public void test() {
    checkRightSyntax("move vertex (select from V) to class:newposition");
    checkRightSyntax("move vertex (select from V) to cluster:newposition");
    checkRightSyntax("move vertex (select from V) to class:newposition set name = 'a'");
    checkRightSyntax(
        "move vertex (select from V) to class:newposition set name = 'a', surname = 'b'");
    checkRightSyntax(
        "move vertex (select from V) to class:newposition set name = 'a', surname = 'b' batch"
            + " 1000");

    checkWrongSyntax("move vertex (select from V) to newposition");
  }
}
