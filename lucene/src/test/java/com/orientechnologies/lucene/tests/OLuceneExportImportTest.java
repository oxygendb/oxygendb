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

package com.orientechnologies.lucene.tests;

import static com.orientechnologies.core.metadata.schema.YTClass.INDEX_TYPE.FULLTEXT;
import static org.assertj.core.api.Assertions.assertThat;

import com.orientechnologies.lucene.OLuceneIndexFactory;
import com.orientechnologies.core.db.tool.ODatabaseExport;
import com.orientechnologies.core.db.tool.ODatabaseImport;
import com.orientechnologies.core.index.OIndex;
import com.orientechnologies.core.metadata.schema.YTClass;
import com.orientechnologies.core.metadata.schema.YTSchema;
import com.orientechnologies.core.metadata.schema.YTType;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.sql.executor.YTResultSet;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class OLuceneExportImportTest extends OLuceneBaseTest {

  @Before
  public void init() {

    YTSchema schema = db.getMetadata().getSchema();
    YTClass city = schema.createClass("City");
    city.createProperty(db, "name", YTType.STRING);

    db.command("create index City.name on City (name) FULLTEXT ENGINE LUCENE");

    YTEntityImpl doc = new YTEntityImpl("City");
    doc.field("name", "Rome");

    db.begin();
    db.save(doc);
    db.commit();
  }

  @Test
  public void testExportImport() throws Throwable {

    String file = "./target/exportTest.json";

    YTResultSet query = db.query("select from City where search_class('Rome')=true");

    assertThat(query).hasSize(1);

    query.close();

    try {

      // export
      new ODatabaseExport(db, file, s -> {
      }).exportDatabase();

      // import
      dropDatabase();
      createDatabase();

      GZIPInputStream stream = new GZIPInputStream(new FileInputStream(file + ".gz"));
      new ODatabaseImport(db, stream, s -> {
      }).importDatabase();

    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }

    assertThat(db.countClass("City")).isEqualTo(1);
    OIndex index = db.getMetadata().getIndexManagerInternal().getIndex(db, "City.name");

    assertThat(index.getType()).isEqualTo(FULLTEXT.toString());

    assertThat(index.getAlgorithm()).isEqualTo(OLuceneIndexFactory.LUCENE_ALGORITHM);

    // redo the query
    query = db.query("select from City where search_class('Rome')=true");

    assertThat(query).hasSize(1);
    query.close();
  }
}
