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

import com.jetbrains.youtrack.db.internal.common.util.OPair;
import com.jetbrains.youtrack.db.internal.core.command.CommandRequest;
import com.jetbrains.youtrack.db.internal.core.command.CommandRequestText;
import com.jetbrains.youtrack.db.internal.core.command.OCommandDistributedReplicateRequest;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.exception.YTCommandExecutionException;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.VertexInternal;
import com.jetbrains.youtrack.db.internal.core.sql.functions.OSQLFunctionRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL CREATE VERTEX command.
 */
public class CommandExecutorSQLCreateVertex extends CommandExecutorSQLSetAware
    implements OCommandDistributedReplicateRequest {

  public static final String NAME = "CREATE VERTEX";
  private YTClass clazz;
  private String clusterName;
  private List<OPair<String, Object>> fields;

  @SuppressWarnings("unchecked")
  public CommandExecutorSQLCreateVertex parse(final CommandRequest iRequest) {

    final CommandRequestText textRequest = (CommandRequestText) iRequest;

    String queryText = textRequest.getText();
    String originalQuery = queryText;
    try {
      queryText = preParse(queryText, iRequest);
      textRequest.setText(queryText);

      final var database = getDatabase();

      init((CommandRequestText) iRequest);

      String className = null;

      parserRequiredKeyword("CREATE");
      parserRequiredKeyword("VERTEX");

      String temp = parseOptionalWord(true);

      while (temp != null) {
        if (temp.equals("CLUSTER")) {
          clusterName = parserRequiredWord(false);

        } else if (temp.equals(KEYWORD_SET)) {
          fields = new ArrayList<OPair<String, Object>>();
          parseSetFields(clazz, fields);

        } else if (temp.equals(KEYWORD_CONTENT)) {
          parseContent();

        } else if (className == null && temp.length() > 0) {
          className = temp;
          if (className == null)
          // ASSIGN DEFAULT CLASS
          {
            className = "V";
          }

          // GET/CHECK CLASS NAME
          clazz = database.getMetadata().getImmutableSchemaSnapshot().getClass(className);
          if (clazz == null) {
            throw new YTCommandSQLParsingException("Class '" + className + "' was not found");
          }
        }

        temp = parserOptionalWord(true);
        if (parserIsEnded()) {
          break;
        }
      }

      if (className == null) {
        // ASSIGN DEFAULT CLASS
        className = "V";

        // GET/CHECK CLASS NAME
        clazz = database.getMetadata().getImmutableSchemaSnapshot().getClass(className);
        if (clazz == null) {
          throw new YTCommandSQLParsingException("Class '" + className + "' was not found");
        }
      }
    } finally {
      textRequest.setText(originalQuery);
    }
    return this;
  }

  /**
   * Execute the command and return the EntityImpl object created.
   */
  public Object execute(final Map<Object, Object> iArgs, YTDatabaseSessionInternal querySession) {
    if (clazz == null) {
      throw new YTCommandExecutionException(
          "Cannot execute the command because it has not been parsed yet");
    }

    // CREATE VERTEX DOES NOT HAVE TO BE IN TX
    final VertexInternal vertex = (VertexInternal) getDatabase().newVertex(clazz);

    if (fields != null)
    // EVALUATE FIELDS
    {
      for (final OPair<String, Object> f : fields) {
        if (f.getValue() instanceof OSQLFunctionRuntime) {
          f.setValue(
              ((OSQLFunctionRuntime) f.getValue()).getValue(vertex.getRecord(), null, context));
        }
      }
    }

    OSQLHelper.bindParameters(vertex.getRecord(), fields, new OCommandParameters(iArgs), context);

    if (content != null) {
      ((EntityImpl) vertex.getRecord()).merge(content, true, false);
    }

    if (clusterName != null) {
      vertex.getBaseDocument().save(clusterName);
    } else {
      vertex.save();
    }

    return vertex.getRecord();
  }

  @Override
  public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
    return DISTRIBUTED_EXECUTION_MODE.LOCAL;
  }

  @Override
  public QUORUM_TYPE getQuorumType() {
    return QUORUM_TYPE.WRITE;
  }

  @Override
  public Set<String> getInvolvedClusters() {
    if (clazz != null) {
      return Collections.singleton(
          getDatabase().getClusterNameById(clazz.getClusterSelection().getCluster(clazz, null)));
    } else if (clusterName != null) {
      return getInvolvedClustersOfClusters(Collections.singleton(clusterName));
    }

    return Collections.EMPTY_SET;
  }

  @Override
  public String getSyntax() {
    return "CREATE VERTEX [<class>] [CLUSTER <cluster>] [SET <field> = <expression>[,]*]";
  }
}