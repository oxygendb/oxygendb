package com.jetbrains.youtrack.db.auto;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.jetbrains.youtrack.db.api.schema.PropertyType;
import com.jetbrains.youtrack.db.api.schema.Schema;
import com.jetbrains.youtrack.db.api.schema.SchemaClass;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.sql.ChainedIndexProxy;
import com.jetbrains.youtrack.db.internal.core.sql.query.SQLSynchQuery;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Testing functionality of {@link ChainedIndexProxy}.
 *
 * <p>Each test method tests different traverse index combination with different operations.
 *
 * <p>Method name are used to describe test case, first part is chain of types of indexes that are
 * used in test, second part define operation which are tested, and the last part describe whether
 * or not {@code limit} operator are used in query.
 *
 * <p>
 *
 * <p>Prefix "lpirt" in class names means "LinkedPropertyIndexReuseTest".
 */
@SuppressWarnings("SuspiciousMethodCalls")
@Test
public class SQLSelectByLinkedPropertyIndexReuseTest extends AbstractIndexReuseTest {

  @Parameters(value = "remote")
  public SQLSelectByLinkedPropertyIndexReuseTest(@Optional Boolean remote) {
    super(remote != null && remote);
  }

  @BeforeClass
  public void beforeClass() throws Exception {
    super.beforeClass();

    createSchemaForTest();
    fillDataSet();
  }

  @AfterClass
  public void afterClass() throws Exception {
    if (db.isClosed()) {
      db = createSessionInstance();
    }

    db.command("drop class lpirtStudent").close();
    db.command("drop class lpirtGroup").close();
    db.command("drop class lpirtCurator").close();

    super.afterClass();
  }

  @Test
  public void testNotUniqueUniqueNotUniqueEqualsUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.name = 'Someone'"));
    assertEquals(result.size(), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "John Smith"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testNotUniqueUniqueUniqueEqualsUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.salary = 600"));
    assertEquals(result.size(), 3);
    assertEquals(containsDocumentWithFieldValue(result, "name", "James Bell"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "Roger Connor"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "William James"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testNotUniqueUniqueNotUniqueEqualsLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.name = 'Someone else' limit 1"));
    assertEquals(result.size(), 1);
    assertTrue(
        Arrays.asList("Jane Smith", "James Bell", "Roger Connor", "William James")
            .contains(result.get(0).field("name")));

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testNotUniqueUniqueUniqueMinorUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.salary < 1000"));
    assertEquals(result.size(), 4);
    assertEquals(containsDocumentWithFieldValue(result, "name", "Jane Smith"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "James Bell"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "Roger Connor"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "William James"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 5);
  }

  @Test
  public void testNotUniqueUniqueUniqueMinorLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.salary < 1000 limit 2"));
    assertEquals(result.size(), 2);

    final List<String> expectedNames =
        Arrays.asList("Jane Smith", "James Bell", "Roger Connor", "William James");

    for (EntityImpl aResult : result) {
      assertTrue(expectedNames.contains(aResult.field("name")));
    }

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 5);
  }

  @Test
  public void testUniqueNotUniqueMinorEqualsUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>("select from lpirtStudent where diploma.GPA <= 4"));
    assertEquals(result.size(), 3);
    assertEquals(containsDocumentWithFieldValue(result, "name", "John Smith"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "James Bell"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "William James"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testUniqueNotUniqueMinorEqualsLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where diploma.GPA <= 4 limit 1"));
    assertEquals(result.size(), 1);
    assertTrue(
        Arrays.asList("John Smith", "James Bell", "William James")
            .contains(result.get(0).field("name")));

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 2);
  }

  @Test
  public void testNotUniqueUniqueUniqueMajorUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.salary > 1000"));
    assertEquals(result.size(), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "John Smith"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testNotUniqueUniqueUniqueMajorLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where group.curator.salary > 550 limit 1"));
    assertEquals(result.size(), 1);
    final List<String> expectedNames =
        Arrays.asList("John Smith", "James Bell", "Roger Connor", "William James");
    for (EntityImpl aResult : result) {
      assertTrue(expectedNames.contains(aResult.field("name")));
    }

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testUniqueUniqueBetweenUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtGroup where curator.salary between 500 and 1000"));
    assertEquals(result.size(), 2);
    assertEquals(containsDocumentWithFieldValue(result, "name", "PZ-08-2"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "PZ-08-3"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testUniqueUniqueBetweenLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtGroup where curator.salary between 500 and 1000 limit 1"));
    assertEquals(result.size(), 1);

    final List<String> expectedNames = Arrays.asList("PZ-08-2", "PZ-08-3");
    for (EntityImpl aResult : result) {
      assertTrue(expectedNames.contains(aResult.field("name")));
    }

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 2);
  }

  @Test
  public void testUniqueUniqueInUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtGroup where curator.salary in [500, 600]"));
    assertEquals(result.size(), 2);
    assertEquals(containsDocumentWithFieldValue(result, "name", "PZ-08-2"), 1);
    assertEquals(containsDocumentWithFieldValue(result, "name", "PZ-08-3"), 1);

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 3);
  }

  @Test
  public void testUniqueUniqueInLimitUsing() throws Exception {

    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtGroup where curator.salary in [500, 600] limit 1"));
    assertEquals(result.size(), 1);

    final List<String> expectedNames = Arrays.asList("PZ-08-2", "PZ-08-3");
    for (EntityImpl aResult : result) {
      assertTrue(expectedNames.contains(aResult.field("name")));
    }

    assertEquals(profiler.getCounter("db.demo.query.indexUsed"), oldIndexUsage + 2);
  }

  /**
   * When some unique composite index in the chain is queried by partial result, the final result
   * become not unique.
   */
  @Test
  public void testUniquePartialSearch() {
    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>(
                "select from lpirtStudent where diploma.name = 'diploma3'"));

    assertEquals(result.size(), 2);
    final List<String> expectedNames = Arrays.asList("William James", "James Bell");
    for (EntityImpl aResult : result) {
      assertTrue(expectedNames.contains(aResult.field("name")));
    }

    assertEquals(indexUsages(), oldIndexUsage + 2);
  }

  @Test
  public void testHashIndexIsUsedAsBaseIndex() {
    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>("select from lpirtStudent where transcript.id = '1'"));

    assertEquals(result.size(), 1);

    assertEquals(indexUsages(), oldIndexUsage + 2);
  }

  @Test
  public void testCompositeIndex() {
    long oldIndexUsage = indexUsages();

    List<EntityImpl> result =
        db.query(
            new SQLSynchQuery<EntityImpl>("select from lpirtStudent where skill.name = 'math'"));

    assertEquals(result.size(), 1);

    assertEquals(indexUsages(), oldIndexUsage + 2);
  }

  private long indexUsages() {
    final long oldIndexUsage = profiler.getCounter("db.demo.query.indexUsed");
    return oldIndexUsage == -1 ? 0 : oldIndexUsage;
  }

  /**
   * William James and James Bell work together on the same diploma.
   */
  private void fillDataSet() {
    db.begin();
    EntityImpl curator1 = db.newInstance("lpirtCurator");
    curator1.field("name", "Someone");
    curator1.field("salary", 2000);

    final EntityImpl group1 = db.newInstance("lpirtGroup");
    group1.field("name", "PZ-08-1");
    group1.field("curator", curator1);
    group1.save();

    final EntityImpl diploma1 = db.newInstance("lpirtDiploma");
    diploma1.field("GPA", 3.);
    diploma1.field("name", "diploma1");
    diploma1.field(
        "thesis",
        "Researching and visiting universities before making a final decision is very beneficial"
            + " because you student be able to experience the campus, meet the professors, and"
            + " truly understand the traditions of the university.");

    final EntityImpl transcript = db.newInstance("lpirtTranscript");
    transcript.field("id", "1");

    final EntityImpl skill = db.newInstance("lpirtSkill");
    skill.field("name", "math");

    final EntityImpl student1 = db.newInstance("lpirtStudent");
    student1.field("name", "John Smith");
    student1.field("group", group1);
    student1.field("diploma", diploma1);
    student1.field("transcript", transcript);
    student1.field("skill", skill);
    student1.save();

    EntityImpl curator2 = db.newInstance("lpirtCurator");
    curator2.field("name", "Someone else");
    curator2.field("salary", 500);

    final EntityImpl group2 = db.newInstance("lpirtGroup");
    group2.field("name", "PZ-08-2");
    group2.field("curator", curator2);
    group2.save();

    final EntityImpl diploma2 = db.newInstance("lpirtDiploma");
    diploma2.field("GPA", 5.);
    diploma2.field("name", "diploma2");
    diploma2.field(
        "thesis",
        "While both Northerners and Southerners believed they fought against tyranny and"
            + " oppression, Northerners focused on the oppression of slaves while Southerners"
            + " defended their own right to self-government.");

    final EntityImpl student2 = db.newInstance("lpirtStudent");
    student2.field("name", "Jane Smith");
    student2.field("group", group2);
    student2.field("diploma", diploma2);
    student2.save();

    EntityImpl curator3 = db.newInstance("lpirtCurator");
    curator3.field("name", "Someone else");
    curator3.field("salary", 600);

    final EntityImpl group3 = db.newInstance("lpirtGroup");
    group3.field("name", "PZ-08-3");
    group3.field("curator", curator3);
    group3.save();

    final EntityImpl diploma3 = db.newInstance("lpirtDiploma");
    diploma3.field("GPA", 4.);
    diploma3.field("name", "diploma3");
    diploma3.field(
        "thesis",
        "College student shouldn't have to take a required core curriculum, and many core "
            + "courses are graded too stiffly.");

    final EntityImpl student3 = db.newInstance("lpirtStudent");
    student3.field("name", "James Bell");
    student3.field("group", group3);
    student3.field("diploma", diploma3);
    student3.save();

    final EntityImpl student4 = db.newInstance("lpirtStudent");
    student4.field("name", "Roger Connor");
    student4.field("group", group3);
    student4.save();

    final EntityImpl student5 = db.newInstance("lpirtStudent");
    student5.field("name", "William James");
    student5.field("group", group3);
    student5.field("diploma", diploma3);
    student5.save();
    db.commit();
  }

  private void createSchemaForTest() {
    final Schema schema = db.getMetadata().getSchema();
    if (!schema.existsClass("lpirtStudent")) {
      final SchemaClass curatorClass = schema.createClass("lpirtCurator");
      curatorClass.createProperty(db, "name", PropertyType.STRING)
          .createIndex(db, SchemaClass.INDEX_TYPE.NOTUNIQUE);
      curatorClass
          .createProperty(db, "salary", PropertyType.INTEGER)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));
      curatorClass.createIndex(db,
          "curotorCompositeIndex",
          SchemaClass.INDEX_TYPE.UNIQUE.name(),
          null,
          Map.of("ignoreNullValues", true), new String[]{"salary", "name"});

      final SchemaClass groupClass = schema.createClass("lpirtGroup");
      groupClass
          .createProperty(db, "name", PropertyType.STRING)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));
      groupClass
          .createProperty(db, "curator", PropertyType.LINK, curatorClass)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));

      final SchemaClass diplomaClass = schema.createClass("lpirtDiploma");
      diplomaClass.createProperty(db, "GPA", PropertyType.DOUBLE)
          .createIndex(db, SchemaClass.INDEX_TYPE.NOTUNIQUE);
      diplomaClass.createProperty(db, "thesis", PropertyType.STRING);
      diplomaClass
          .createProperty(db, "name", PropertyType.STRING)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));
      diplomaClass.createIndex(db,
          "diplomaThesisUnique",
          SchemaClass.INDEX_TYPE.UNIQUE.name(),
          null,
          Map.of("ignoreNullValues", true), new String[]{"thesis"});

      final SchemaClass transcriptClass = schema.createClass("lpirtTranscript");
      transcriptClass
          .createProperty(db, "id", PropertyType.STRING)
          .createIndex(db,
              SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));

      final SchemaClass skillClass = schema.createClass("lpirtSkill");
      skillClass
          .createProperty(db, "name", PropertyType.STRING)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));

      final SchemaClass studentClass = schema.createClass("lpirtStudent");
      studentClass
          .createProperty(db, "name", PropertyType.STRING)
          .createIndex(db, SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));
      studentClass
          .createProperty(db, "group", PropertyType.LINK, groupClass)
          .createIndex(db, SchemaClass.INDEX_TYPE.NOTUNIQUE);
      studentClass.createProperty(db, "diploma", PropertyType.LINK, diplomaClass);
      studentClass
          .createProperty(db, "transcript", PropertyType.LINK, transcriptClass)
          .createIndex(db,
              SchemaClass.INDEX_TYPE.UNIQUE,
              Map.of("ignoreNullValues", true));
      studentClass.createProperty(db, "skill", PropertyType.LINK, skillClass);

      var metadata = Map.of("ignoreNullValues", false);
      studentClass.createIndex(db,
          "studentDiplomaAndNameIndex",
          SchemaClass.INDEX_TYPE.UNIQUE.toString(),
          null,
          new HashMap<>(metadata), new String[]{"diploma", "name"});
      studentClass.createIndex(db,
          "studentSkillAndGroupIndex",
          SchemaClass.INDEX_TYPE.NOTUNIQUE.toString(),
          null,
          new HashMap<>(metadata), new String[]{"skill", "group"});
    }
  }

  private static int containsDocumentWithFieldValue(
      final List<EntityImpl> docList, final String fieldName, final Object fieldValue) {
    int count = 0;
    for (final EntityImpl docItem : docList) {
      if (fieldValue.equals(docItem.field(fieldName))) {
        count++;
      }
    }
    return count;
  }
}