package com.orientechnologies.orient.core.db;

import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import java.util.List;

/**
 * Created by luigidellaquila on 28/06/17.
 */
public class DistributedQueryContext {

  private String queryId;
  private ODatabaseSessionInternal db;
  private OResultSet resultSet;

  public String getQueryId() {
    return queryId;
  }

  public void setQueryId(String queryId) {
    this.queryId = queryId;
  }

  public ODatabaseSession getDb() {
    return db;
  }

  public void setDb(ODatabaseSession db) {
    this.db = (ODatabaseSessionInternal) db;
  }

  public OResultSet getResultSet() {
    return resultSet;
  }

  public void setResultSet(OResultSet resultSet) {
    this.resultSet = resultSet;
  }

  public List<OResult> fetchNextPage() {
    ODatabaseSessionInternal prev = ODatabaseRecordThreadLocal.instance().getIfDefined();
    try {
      db.activateOnCurrentThread();
      resultSet.close();
      db.close();
    } finally {
      if (prev == null) {
        ODatabaseRecordThreadLocal.instance().remove();
      } else {
        ODatabaseRecordThreadLocal.instance().set(prev);
      }
    }
    return null;
  }

  public void close() {
    ODatabaseSessionInternal prev = ODatabaseRecordThreadLocal.instance().getIfDefined();
    try {
      db.activateOnCurrentThread();
      resultSet.close();
      db.close();
      ((OSharedContextEmbedded) db.getSharedContext())
          .getActiveDistributedQueries()
          .remove(queryId);
    } finally {
      if (prev == null) {
        ODatabaseRecordThreadLocal.instance().remove();
      } else {
        ODatabaseRecordThreadLocal.instance().set(prev);
      }
    }
  }
}
