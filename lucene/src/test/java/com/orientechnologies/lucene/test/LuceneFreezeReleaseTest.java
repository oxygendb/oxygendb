package com.orientechnologies.lucene.test;

import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.db.document.YTDatabaseDocumentTx;
import com.orientechnologies.core.metadata.schema.YTClass;
import com.orientechnologies.core.metadata.schema.YTSchema;
import com.orientechnologies.core.metadata.schema.YTType;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.sql.executor.YTResultSet;
import java.io.File;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class LuceneFreezeReleaseTest {

  @Before
  public void setUp() throws Exception {
    OFileUtils.deleteRecursively(new File("./target/freezeRelease"));
  }

  @Test
  public void freezeReleaseTest() {
    if (isWindows()) {
      return;
    }

    YTDatabaseSessionInternal db = new YTDatabaseDocumentTx("plocal:target/freezeRelease");

    db.create();

    YTSchema schema = db.getMetadata().getSchema();
    YTClass person = schema.createClass("Person");
    person.createProperty(db, "name", YTType.STRING);

    db.command("create index Person.name on Person (name) FULLTEXT ENGINE LUCENE").close();

    db.begin();
    db.save(new YTEntityImpl("Person").field("name", "John"));
    db.commit();

    try {

      YTResultSet results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(1, results.stream().count());
      db.freeze();

      results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(1, results.stream().count());

      db.release();

      db.begin();
      db.save(new YTEntityImpl("Person").field("name", "John"));
      db.commit();

      results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(2, results.stream().count());

    } finally {

      db.drop();
    }
  }

  // With double calling freeze/release
  @Test
  public void freezeReleaseMisUsageTest() {
    if (isWindows()) {
      return;
    }

    YTDatabaseSessionInternal db = new YTDatabaseDocumentTx("plocal:target/freezeRelease");

    db.create();

    YTSchema schema = db.getMetadata().getSchema();
    YTClass person = schema.createClass("Person");
    person.createProperty(db, "name", YTType.STRING);

    db.command("create index Person.name on Person (name) FULLTEXT ENGINE LUCENE").close();

    db.begin();
    db.save(new YTEntityImpl("Person").field("name", "John"));
    db.commit();

    try {

      YTResultSet results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(1, results.stream().count());

      db.freeze();

      db.freeze();

      results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(1, results.stream().count());

      db.release();
      db.release();

      db.begin();
      db.save(new YTEntityImpl("Person").field("name", "John"));
      db.commit();

      results = db.query("select from Person where name lucene 'John'");
      Assert.assertEquals(2, results.stream().count());

    } finally {
      db.drop();
    }
  }

  private boolean isWindows() {
    final String osName = System.getProperty("os.name").toLowerCase();
    return osName.contains("win");
  }
}
