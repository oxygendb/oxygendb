/*
 *
 *
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.lucene.test;

import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.metadata.schema.YTClass;
import com.orientechnologies.core.metadata.schema.YTType;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.sql.executor.YTResultSet;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class LuceneInheritanceQueryTest extends BaseLuceneTest {

  public LuceneInheritanceQueryTest() {
  }

  @Test
  public void testQuery() {
    createSchema(db);
    YTEntityImpl doc = new YTEntityImpl("C2");
    doc.field("name", "abc");
    db.begin();
    db.save(doc);
    db.commit();

    YTResultSet vertices = db.query("select from C1 where name lucene \"abc\" ");

    Assert.assertEquals(1, vertices.stream().count());
  }

  protected void createSchema(YTDatabaseSessionInternal db) {
    final YTClass c1 = db.createVertexClass("C1");
    c1.createProperty(db, "name", YTType.STRING);
    c1.createIndex(db, "C1.name", "FULLTEXT", null, null, "LUCENE", new String[]{"name"});

    db.createClass("C2", "C1");
  }
}
