/*
 *
 *
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *
 *
 */
package com.jetbrains.youtrack.db.internal.core.sql;

import com.jetbrains.youtrack.db.internal.common.exception.YTException;
import com.jetbrains.youtrack.db.internal.common.util.OPatternConst;
import com.jetbrains.youtrack.db.internal.core.collate.OCollate;
import com.jetbrains.youtrack.db.internal.core.command.CommandRequest;
import com.jetbrains.youtrack.db.internal.core.command.CommandRequestText;
import com.jetbrains.youtrack.db.internal.core.command.OCommandDistributedReplicateRequest;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.exception.YTDatabaseException;
import com.jetbrains.youtrack.db.internal.core.index.OIndex;
import com.jetbrains.youtrack.db.internal.core.index.OIndexDefinition;
import com.jetbrains.youtrack.db.internal.core.index.OIndexDefinitionFactory;
import com.jetbrains.youtrack.db.internal.core.index.OIndexFactory;
import com.jetbrains.youtrack.db.internal.core.index.OIndexes;
import com.jetbrains.youtrack.db.internal.core.index.OPropertyMapIndexDefinition;
import com.jetbrains.youtrack.db.internal.core.index.ORuntimeKeyIndexDefinition;
import com.jetbrains.youtrack.db.internal.core.index.OSimpleKeyIndexDefinition;
import com.jetbrains.youtrack.db.internal.core.index.YTIndexException;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClassImpl;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL CREATE INDEX command: Create a new index against a property.
 *
 * <p>
 *
 * <p>Supports following grammar: <br>
 * "CREATE" "INDEX" &lt;indexName&gt; ["ON" &lt;className&gt; "(" &lt;propName&gt; (","
 * &lt;propName&gt;)* ")"] &lt;indexType&gt; [&lt;keyType&gt; ("," &lt;keyType&gt;)*]
 */
@SuppressWarnings("unchecked")
public class CommandExecutorSQLCreateIndex extends CommandExecutorSQLAbstract
    implements OCommandDistributedReplicateRequest {

  public static final String KEYWORD_CREATE = "CREATE";
  public static final String KEYWORD_INDEX = "INDEX";
  public static final String KEYWORD_ON = "ON";
  public static final String KEYWORD_METADATA = "METADATA";
  public static final String KEYWORD_ENGINE = "ENGINE";

  private String indexName;
  private YTClass oClass;
  private String[] fields;
  private YTClass.INDEX_TYPE indexType;
  private YTType[] keyTypes;
  private byte serializerKeyId;
  private String engine;
  private EntityImpl metadataDoc = null;
  private String[] collates;

  public CommandExecutorSQLCreateIndex parse(final CommandRequest iRequest) {
    final CommandRequestText textRequest = (CommandRequestText) iRequest;

    String queryText = textRequest.getText();
    String originalQuery = queryText;
    try {
      queryText = preParse(queryText, iRequest);
      textRequest.setText(queryText);

      init((CommandRequestText) iRequest);

      final StringBuilder word = new StringBuilder();

      int oldPos = 0;
      int pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
      if (pos == -1 || !word.toString().equals(KEYWORD_CREATE)) {
        throw new YTCommandSQLParsingException(
            "Keyword " + KEYWORD_CREATE + " not found. Use " + getSyntax(), parserText, oldPos);
      }

      oldPos = pos;
      pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
      if (pos == -1 || !word.toString().equals(KEYWORD_INDEX)) {
        throw new YTCommandSQLParsingException(
            "Keyword " + KEYWORD_INDEX + " not found. Use " + getSyntax(), parserText, oldPos);
      }

      oldPos = pos;
      pos = nextWord(parserText, parserTextUpperCase, oldPos, word, false);
      if (pos == -1) {
        throw new YTCommandSQLParsingException(
            "Expected index name. Use " + getSyntax(), parserText, oldPos);
      }

      indexName = decodeClassName(word.toString());

      oldPos = pos;
      pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
      if (pos == -1) {
        throw new YTCommandSQLParsingException(
            "Index type requested. Use " + getSyntax(), parserText, oldPos + 1);
      }

      if (word.toString().equals(KEYWORD_ON)) {
        oldPos = pos;
        pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
        if (pos == -1) {
          throw new YTCommandSQLParsingException(
              "Expected class name. Use " + getSyntax(), parserText, oldPos);
        }
        oldPos = pos;
        oClass = findClass(decodeClassName(word.toString()));

        if (oClass == null) {
          throw new YTCommandExecutionException("Class " + word + " not found");
        }

        pos = parserTextUpperCase.indexOf(')');
        if (pos == -1) {
          throw new YTCommandSQLParsingException(
              "No right bracket found. Use " + getSyntax(), parserText, oldPos);
        }

        final String props = parserText.substring(oldPos, pos).trim().substring(1);

        List<String> propList = new ArrayList<String>();
        Collections.addAll(propList, OPatternConst.PATTERN_COMMA_SEPARATED.split(props.trim()));

        fields = new String[propList.size()];
        propList.toArray(fields);

        for (int i = 0; i < fields.length; i++) {
          final String fieldName = fields[i];

          final int collatePos = fieldName.toUpperCase(Locale.ENGLISH).indexOf(" COLLATE ");

          if (collatePos > 0) {
            if (collates == null) {
              collates = new String[fields.length];
            }

            collates[i] =
                fieldName
                    .substring(collatePos + " COLLATE ".length())
                    .toLowerCase(Locale.ENGLISH)
                    .trim();
            fields[i] = fieldName.substring(0, collatePos);
          } else {
            if (collates != null) {
              collates[i] = null;
            }
          }
          fields[i] = decodeClassName(fields[i]);
        }

        for (String propToIndex : fields) {
          checkMapIndexSpecifier(propToIndex, parserText, oldPos);

          propList.add(propToIndex);
        }

        oldPos = pos + 1;
        pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
        if (pos == -1) {
          throw new YTCommandSQLParsingException(
              "Index type requested. Use " + getSyntax(), parserText, oldPos + 1);
        }
      } else {
        if (indexName.indexOf('.') > 0) {
          final String[] parts = indexName.split("\\.");

          oClass = findClass(parts[0]);
          if (oClass == null) {
            throw new YTCommandExecutionException("Class " + parts[0] + " not found");
          }

          fields = new String[]{parts[1]};
        }
      }

      indexType = YTClass.INDEX_TYPE.valueOf(word.toString());

      if (indexType == null) {
        throw new YTCommandSQLParsingException("Index type is null", parserText, oldPos);
      }

      oldPos = pos;
      pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);

      if (word.toString().equals(KEYWORD_ENGINE)) {
        oldPos = pos;
        pos = nextWord(parserText, parserTextUpperCase, oldPos, word, false);
        oldPos = pos;
        engine = word.toString().toUpperCase(Locale.ENGLISH);
      } else {
        parserGoBack();
      }

      final int configPos = parserTextUpperCase.indexOf(KEYWORD_METADATA, oldPos);

      if (configPos > -1) {
        final String configString =
            parserText.substring(configPos + KEYWORD_METADATA.length()).trim();
        metadataDoc = new EntityImpl();
        metadataDoc.fromJSON(configString);
      }

      pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);
      if (pos != -1
          && !word.toString().equalsIgnoreCase("NULL")
          && !word.toString().equalsIgnoreCase(KEYWORD_METADATA)) {
        final String typesString;
        if (configPos > -1) {
          typesString = parserTextUpperCase.substring(oldPos, configPos).trim();
        } else {
          typesString = parserTextUpperCase.substring(oldPos).trim();
        }

        if (word.toString().equalsIgnoreCase("RUNTIME")) {
          oldPos = pos;
          pos = nextWord(parserText, parserTextUpperCase, oldPos, word, true);

          serializerKeyId = Byte.parseByte(word.toString());
        } else {
          ArrayList<YTType> keyTypeList = new ArrayList<YTType>();
          for (String typeName : OPatternConst.PATTERN_COMMA_SEPARATED.split(typesString)) {
            keyTypeList.add(YTType.valueOf(typeName));
          }

          keyTypes = new YTType[keyTypeList.size()];
          keyTypeList.toArray(keyTypes);

          if (fields != null && fields.length != 0 && fields.length != keyTypes.length) {
            throw new YTCommandSQLParsingException(
                "Count of fields does not match with count of property types. "
                    + "Fields: "
                    + Arrays.toString(fields)
                    + "; Types: "
                    + Arrays.toString(keyTypes),
                parserText,
                oldPos);
          }
        }
      }

    } finally {
      textRequest.setText(originalQuery);
    }

    return this;
  }

  /**
   * Execute the CREATE INDEX.
   */
  @SuppressWarnings("rawtypes")
  public Object execute(final Map<Object, Object> iArgs, YTDatabaseSessionInternal querySession) {
    if (indexName == null) {
      throw new YTCommandExecutionException(
          "Cannot execute the command because it has not been parsed yet");
    }

    final YTDatabaseSessionInternal database = getDatabase();
    final OIndex idx;
    List<OCollate> collatesList = null;

    if (collates != null) {
      collatesList = new ArrayList<OCollate>();

      for (String collate : collates) {
        if (collate != null) {
          final OCollate col = OSQLEngine.getCollate(collate);
          collatesList.add(col);
        } else {
          collatesList.add(null);
        }
      }
    }

    if (fields == null || fields.length == 0) {
      OIndexFactory factory = OIndexes.getFactory(indexType.toString(), null);

      if (keyTypes != null) {
        idx =
            database
                .getMetadata()
                .getIndexManagerInternal()
                .createIndex(
                    database,
                    indexName,
                    indexType.toString(),
                    new OSimpleKeyIndexDefinition(keyTypes, collatesList),
                    null,
                    null,
                    metadataDoc,
                    engine);
      } else if (serializerKeyId != 0) {
        idx =
            database
                .getMetadata()
                .getIndexManagerInternal()
                .createIndex(
                    database,
                    indexName,
                    indexType.toString(),
                    new ORuntimeKeyIndexDefinition(serializerKeyId),
                    null,
                    null,
                    metadataDoc,
                    engine);
      } else {
        throw new YTDatabaseException(
            "Impossible to create an index without specify the key type or the associated"
                + " property");
      }
    } else {
      if ((keyTypes == null || keyTypes.length == 0) && collates == null) {
        idx =
            oClass.createIndex(database, indexName, indexType.toString(), null, metadataDoc, engine,
                fields);
      } else {
        final List<YTType> fieldTypeList;
        if (keyTypes == null) {
          for (final String fieldName : fields) {
            if (!fieldName.equals("@rid") && !oClass.existsProperty(fieldName)) {
              throw new YTIndexException(
                  "Index with name : '"
                      + indexName
                      + "' cannot be created on class : '"
                      + oClass.getName()
                      + "' because field: '"
                      + fieldName
                      + "' is absent in class definition.");
            }
          }
          fieldTypeList = ((YTClassImpl) oClass).extractFieldTypes(fields);
        } else {
          fieldTypeList = Arrays.asList(keyTypes);
        }

        final OIndexDefinition idxDef =
            OIndexDefinitionFactory.createIndexDefinition(
                oClass,
                Arrays.asList(fields),
                fieldTypeList,
                collatesList,
                indexType.toString(),
                null);

        idx =
            database
                .getMetadata()
                .getIndexManagerInternal()
                .createIndex(
                    database,
                    indexName,
                    indexType.name(),
                    idxDef,
                    oClass.getPolymorphicClusterIds(),
                    null,
                    metadataDoc,
                    engine);
      }
    }

    if (idx != null) {
      return idx.getInternal().size(database);
    }

    return null;
  }

  @Override
  public QUORUM_TYPE getQuorumType() {
    return QUORUM_TYPE.ALL;
  }

  @Override
  public String getSyntax() {
    return "CREATE INDEX <name> [ON <class-name> (prop-names [COLLATE <collate>])] <type>"
        + " [<key-type>] [ENGINE <engine>] [METADATA {JSON Index Metadata Document}]";
  }

  private YTClass findClass(String part) {
    return getDatabase().getMetadata().getSchema().getClass(part);
  }

  private void checkMapIndexSpecifier(final String fieldName, final String text, final int pos) {
    final String[] fieldNameParts = OPatternConst.PATTERN_SPACES.split(fieldName);
    if (fieldNameParts.length == 1) {
      return;
    }

    if (fieldNameParts.length == 3) {
      if ("by".equals(fieldNameParts[1].toLowerCase(Locale.ENGLISH))) {
        try {
          OPropertyMapIndexDefinition.INDEX_BY.valueOf(
              fieldNameParts[2].toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException iae) {
          throw YTException.wrapException(
              new YTCommandSQLParsingException(
                  "Illegal field name format, should be '<property> [by key|value]' but was '"
                      + fieldName
                      + "'",
                  text,
                  pos),
              iae);
        }
        return;
      }
      throw new YTCommandSQLParsingException(
          "Illegal field name format, should be '<property> [by key|value]' but was '"
              + fieldName
              + "'",
          text,
          pos);
    }

    throw new YTCommandSQLParsingException(
        "Illegal field name format, should be '<property> [by key|value]' but was '"
            + fieldName
            + "'",
        text,
        pos);
  }

  @Override
  public String getUndoCommand() {
    return "drop index " + indexName;
  }
}