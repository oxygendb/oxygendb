package com.jetbrains.youtrack.db.internal.core.index;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.document.YTDatabaseDocumentTx;
import com.jetbrains.youtrack.db.internal.core.exception.YTDatabaseException;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class OSimpleKeyIndexDefinitionTest extends DBTestBase {

  private OSimpleKeyIndexDefinition simpleKeyIndexDefinition;

  @Before
  public void beforeMethod() {
    simpleKeyIndexDefinition = new OSimpleKeyIndexDefinition(YTType.INTEGER, YTType.STRING);
  }

  @Test
  public void testGetFields() {
    Assert.assertTrue(simpleKeyIndexDefinition.getFields().isEmpty());
  }

  @Test
  public void testGetClassName() {
    Assert.assertNull(simpleKeyIndexDefinition.getClassName());
  }

  @Test
  public void testCreateValueSimpleKey() {
    final OSimpleKeyIndexDefinition keyIndexDefinition =
        new OSimpleKeyIndexDefinition(YTType.INTEGER);
    final Object result = keyIndexDefinition.createValue(db, "2");
    Assert.assertEquals(result, 2);
  }

  @Test
  public void testCreateValueCompositeKeyListParam() {
    final Object result = simpleKeyIndexDefinition.createValue(db, Arrays.asList("2", "3"));

    final OCompositeKey compositeKey = new OCompositeKey(Arrays.asList(2, "3"));
    Assert.assertEquals(result, compositeKey);
  }

  @Test
  public void testCreateValueCompositeKeyNullListParam() {
    final Object result =
        simpleKeyIndexDefinition.createValue(db, Collections.singletonList(null));

    Assert.assertNull(result);
  }

  @Test
  public void testNullParamListItem() {
    final Object result = simpleKeyIndexDefinition.createValue(db, Arrays.asList("2", null));

    Assert.assertNull(result);
  }

  @Test(expected = YTDatabaseException.class)
  public void testWrongParamTypeListItem() {
    simpleKeyIndexDefinition.createValue(db, Arrays.asList("a", "3"));
  }

  @Test
  public void testCreateValueCompositeKey() {
    final Object result = simpleKeyIndexDefinition.createValue(db, "2", "3");

    final OCompositeKey compositeKey = new OCompositeKey(Arrays.asList(2, "3"));
    Assert.assertEquals(result, compositeKey);
  }

  @Test
  public void testCreateValueCompositeKeyNullParamList() {
    final Object result = simpleKeyIndexDefinition.createValue(db, (List<?>) null);

    Assert.assertNull(result);
  }

  @Test
  public void testCreateValueCompositeKeyNullParam() {
    final Object result = simpleKeyIndexDefinition.createValue(db, (Object) null);

    Assert.assertNull(result);
  }

  @Test
  public void testCreateValueCompositeKeyEmptyList() {
    final Object result = simpleKeyIndexDefinition.createValue(db, Collections.emptyList());

    Assert.assertNull(result);
  }

  @Test
  public void testNullParamItem() {
    final Object result = simpleKeyIndexDefinition.createValue(db, "2", null);

    Assert.assertNull(result);
  }

  @Test(expected = YTDatabaseException.class)
  public void testWrongParamType() {
    simpleKeyIndexDefinition.createValue(db, "a", "3");
  }

  @Test
  public void testParamCount() {
    Assert.assertEquals(simpleKeyIndexDefinition.getParamCount(), 2);
  }

  @Test
  public void testParamCountOneItem() {
    final OSimpleKeyIndexDefinition keyIndexDefinition =
        new OSimpleKeyIndexDefinition(YTType.INTEGER);

    Assert.assertEquals(keyIndexDefinition.getParamCount(), 1);
  }

  @Test
  public void testGetKeyTypes() {
    Assert.assertEquals(
        simpleKeyIndexDefinition.getTypes(), new YTType[]{YTType.INTEGER, YTType.STRING});
  }

  @Test
  public void testGetKeyTypesOneType() {
    final OSimpleKeyIndexDefinition keyIndexDefinition =
        new OSimpleKeyIndexDefinition(YTType.BOOLEAN);

    Assert.assertEquals(keyIndexDefinition.getTypes(), new YTType[]{YTType.BOOLEAN});
  }

  @Test
  public void testReload() {
    final YTDatabaseSessionInternal databaseDocumentTx =
        new YTDatabaseDocumentTx("memory:osimplekeyindexdefinitiontest");
    databaseDocumentTx.create();

    databaseDocumentTx.begin();
    final EntityImpl storeDocument = simpleKeyIndexDefinition.toStream(new EntityImpl());
    storeDocument.save(
        databaseDocumentTx.getClusterNameById(databaseDocumentTx.getDefaultClusterId()));
    databaseDocumentTx.commit();

    final EntityImpl loadDocument = databaseDocumentTx.load(storeDocument.getIdentity());
    final OSimpleKeyIndexDefinition loadedKeyIndexDefinition = new OSimpleKeyIndexDefinition();
    loadedKeyIndexDefinition.fromStream(loadDocument);

    databaseDocumentTx.drop();

    Assert.assertEquals(loadedKeyIndexDefinition, simpleKeyIndexDefinition);
  }

  @Test(expected = YTIndexException.class)
  public void testGetDocumentValueToIndex() {
    simpleKeyIndexDefinition.getDocumentValueToIndex(db, new EntityImpl());
  }
}