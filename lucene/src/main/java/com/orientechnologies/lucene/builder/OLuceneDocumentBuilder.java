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

package com.orientechnologies.lucene.builder;

import static com.orientechnologies.lucene.builder.OLuceneIndexType.createField;
import static com.orientechnologies.lucene.builder.OLuceneIndexType.createFields;
import static com.orientechnologies.lucene.builder.OLuceneIndexType.createIdField;
import static com.orientechnologies.lucene.builder.OLuceneIndexType.createOldIdField;

import com.orientechnologies.core.db.record.YTIdentifiable;
import com.orientechnologies.core.index.OCompositeKey;
import com.orientechnologies.core.index.OIndexDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

/**
 *
 */
public class OLuceneDocumentBuilder {

  public Document build(
      final OIndexDefinition definition,
      final Object key,
      final YTIdentifiable value,
      final Map<String, Boolean> fieldsToStore,
      final Map<String, ?> metadata) {
    final Document doc = new Document();
    this.addDefaultFieldsToDocument(definition, key, value, doc);

    final List<Object> formattedKey = formatKeys(definition, key);
    int counter = 0;
    for (final String field : definition.getFields()) {
      final Object val = formattedKey.get(counter);
      counter++;
      if (val != null) {
        // doc.add(createField(field, val, Field.Store.YES));
        final Boolean sorted = isSorted(field, metadata);
        createFields(field, val, Field.Store.YES, sorted).forEach(f -> doc.add(f));
        // for cross class index
        createFields(definition.getClassName() + "." + field, val, Field.Store.YES, sorted)
            .forEach(f -> doc.add(f));
      }
    }
    return doc;
  }

  private void addDefaultFieldsToDocument(
      OIndexDefinition definition, Object key, YTIdentifiable value, Document doc) {
    if (value != null) {
      doc.add(createOldIdField(value));
      doc.add(createIdField(value, key));
      doc.add(createField("_CLUSTER", "" + value.getIdentity().getClusterId(), Field.Store.YES));
      doc.add(createField("_CLASS", definition.getClassName(), Field.Store.YES));
    }
  }

  private List<Object> formatKeys(OIndexDefinition definition, Object key) {
    List<Object> keys;
    if (key instanceof OCompositeKey) {
      keys = ((OCompositeKey) key).getKeys();
    } else if (key instanceof List) {
      keys = ((List) key);
    } else {
      keys = new ArrayList<Object>();
      keys.add(key);
    }
    // a sort of padding
    for (int i = keys.size(); i < definition.getFields().size(); i++) {
      keys.add("");
    }
    return keys;
  }

  protected Field.Store isToStore(String f, Map<String, Boolean> collectionFields) {
    return collectionFields.get(f) ? Field.Store.YES : Field.Store.NO;
  }

  public static Boolean isSorted(String field, Map<String, ?> metadata) {
    if (metadata == null) {
      return true;
    }
    Boolean sorted = true;
    try {
      Boolean localSorted = (Boolean) metadata.get("*_index_sorted");
      if (localSorted == null) {
        localSorted = (Boolean) metadata.get(field + "_index_sorted");
      }
      if (localSorted != null) {
        sorted = localSorted;
      }
    } catch (Exception e) {
    }
    return sorted;
  }
}
