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
package com.orientechnologies.orient.server;

import com.orientechnologies.common.exception.YTException;
import com.orientechnologies.common.exception.YTSystemException;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.db.document.OQueryDatabaseState;
import com.orientechnologies.core.metadata.security.OToken;
import com.orientechnologies.core.metadata.security.YTSecurityUser;
import com.orientechnologies.core.security.OParsedToken;
import com.orientechnologies.core.sql.executor.OExecutionPlan;
import com.orientechnologies.core.sql.executor.OInternalExecutionPlan;
import com.orientechnologies.orient.client.binary.OBinaryRequestExecutor;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinary;
import com.orientechnologies.orient.enterprise.channel.binary.YTTokenSecurityException;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocol;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocolData;
import com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

public class OClientConnection {

  private final int id;
  private final long since;
  private final Set<ONetworkProtocol> protocols =
      Collections.newSetFromMap(new WeakHashMap<ONetworkProtocol, Boolean>());
  private volatile ONetworkProtocol protocol;
  private volatile YTDatabaseSessionInternal database;
  private volatile YTSecurityUser serverUser;
  private ONetworkProtocolData data = new ONetworkProtocolData();
  private final OClientConnectionStats stats = new OClientConnectionStats();
  private final Lock lock = new ReentrantLock();
  private Boolean tokenBased;
  private byte[] tokenBytes;
  private OParsedToken token;
  private boolean disconnectOnAfter;
  private final OBinaryRequestExecutor executor;

  public OClientConnection(final int id, final ONetworkProtocol protocol) {
    this.id = id;
    this.protocol = protocol;
    this.protocols.add(protocol);
    this.since = System.currentTimeMillis();
    this.executor = protocol.executor(this);
  }

  public void close() {
    if (database != null) {
      if (!database.isClosed()) {
        database.activateOnCurrentThread();
        try {
          database.close();
        } catch (Exception e) {
          // IGNORE IT (ALREADY CLOSED?)
        }
      }

      database = null;
    }
  }

  /**
   * Acquires the connection. This is fundamental to manage concurrent requests using the same
   * session id.
   */
  public void acquire() {
    lock.lock();
  }

  /**
   * Releases an acquired connection.
   */
  public void release() {
    lock.unlock();
  }

  @Override
  public String toString() {
    Object address;
    if (protocol != null && protocol.getChannel() != null && protocol.getChannel().socket != null) {
      address = protocol.getChannel().socket.getRemoteSocketAddress();
    } else {
      address = "?";
    }
    return "OClientConnection [id=" + id + ", source=" + address + ", since=" + since + "]";
  }

  /**
   * Returns the remote network address in the format <ip>:<port>.
   */
  public String getRemoteAddress() {
    Socket socket = null;
    if (protocol != null) {
      socket = protocol.getChannel().socket;
    } else {
      for (ONetworkProtocol protocol : this.protocols) {
        socket = protocol.getChannel().socket;
        if (socket != null) {
          break;
        }
      }
    }

    if (socket != null) {
      final InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
      return remoteAddress.getAddress().getHostAddress() + ":" + remoteAddress.getPort();
    }
    return null;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final OClientConnection other = (OClientConnection) obj;
    return id == other.id;
  }

  public OChannelBinary getChannel() {
    return (OChannelBinary) protocol.getChannel();
  }

  public ONetworkProtocol getProtocol() {
    return protocol;
  }

  public byte[] getTokenBytes() {
    return tokenBytes;
  }

  public void validateSession(
      byte[] tokenFromNetwork, OTokenHandler handler, ONetworkProtocolBinary protocol) {
    if (tokenFromNetwork == null || tokenFromNetwork.length == 0) {
      if (!protocols.contains(protocol)) {
        throw new YTTokenSecurityException("No valid session found, provide a token");
      }
    } else {
      // IF the byte from the network are the same of the one i have a don't check them
      if (tokenBytes != null && tokenBytes.length > 0) {
        if (Arrays.equals(
            tokenBytes, tokenFromNetwork)) // SAME SESSION AND TOKEN NO NEED CHECK VALIDITY
        {
          return;
        }
      }

      OParsedToken token = null;
      try {
        if (tokenFromNetwork != null) {
          token = handler.parseOnlyBinary(tokenFromNetwork);
        }
      } catch (Exception e) {
        throw YTException.wrapException(new YTSystemException("Error on token parse"), e);
      }

      if (token == null || !handler.validateBinaryToken(token)) {
        cleanSession();
        protocol.getServer().getClientConnectionManager().disconnect(this);
        throw new YTTokenSecurityException(
            "The token provided is not a valid token, signature does not match");
      }
      if (!handler.validateBinaryToken(token)) {
        cleanSession();
        protocol.getServer().getClientConnectionManager().disconnect(this);
        throw new YTTokenSecurityException("The token provided is expired");
      }
      if (tokenBased == null) {
        tokenBased = Boolean.TRUE;
      }
      if (!Arrays.equals(this.tokenBytes, tokenFromNetwork)) {
        cleanSession();
      }
      this.tokenBytes = tokenFromNetwork;
      this.token = token;
      protocols.add(protocol);
    }
  }

  public void cleanSession() {
    if (database != null && !database.isClosed()) {
      database.activateOnCurrentThread();
      database.close();
    }
    database = null;
    protocols.clear();
  }

  public void endOperation() {
    if (database != null) {
      if (!database.isClosed()
          && !database.getTransaction().isActive()
          && database.getLocalCache() != null) {
        database.getLocalCache().clear();
      }
    }

    stats.lastCommandExecutionTime = System.currentTimeMillis() - stats.lastCommandReceived;
    stats.totalCommandExecutionTime += stats.lastCommandExecutionTime;

    stats.lastCommandInfo = data.commandInfo;
    stats.lastCommandDetail = data.commandDetail;

    data.commandDetail = "-";
    release();
  }

  public void init(final OServer server) {
    if (database == null) {
      data = server.getTokenHandler().getProtocolDataFromToken(this, token.getToken());

      if (data == null) {
        throw new YTTokenSecurityException("missing in token data");
      }

      final String db = token.getToken().getDatabase();
      final String type = token.getToken().getDatabaseType();
      if (db != null && type != null) {
        database = server.openDatabase(db, token);
      }
    }
  }

  public Boolean getTokenBased() {
    return tokenBased;
  }

  public void setTokenBased(Boolean tokenBased) {
    this.tokenBased = tokenBased;
  }

  public void setTokenBytes(byte[] tokenBytes) {
    this.tokenBytes = tokenBytes;
  }

  public OToken getToken() {
    if (token != null) {
      return token.getToken();
    } else {
      return null;
    }
  }

  public int getId() {
    return id;
  }

  public long getSince() {
    return since;
  }

  public void setProtocol(ONetworkProtocol protocol) {
    this.protocol = protocol;
  }

  @Nullable
  public YTDatabaseSessionInternal getDatabase() {
    return database;
  }

  public void activateDatabaseOnCurrentThread() {
    var database = this.database;
    if (database != null) {
      database.activateOnCurrentThread();
    }
  }

  public boolean hasDatabase() {
    if (database != null) {
      return true;
    } else if (token != null) {
      return token.getToken().getDatabase() != null;
    } else {
      return false;
    }
  }

  public String getDatabaseName() {
    if (database != null) {
      return database.getName();
    } else if (token != null) {
      return token.getToken().getDatabase();
    } else {
      return null;
    }
  }

  public void setDatabase(YTDatabaseSessionInternal database) {
    this.database = database;
  }

  public YTSecurityUser getServerUser() {
    return serverUser;
  }

  public void setServerUser(YTSecurityUser serverUser) {
    this.serverUser = serverUser;
  }

  public ONetworkProtocolData getData() {
    return data;
  }

  public void setData(ONetworkProtocolData data) {
    this.data = data;
  }

  public OClientConnectionStats getStats() {
    return stats;
  }

  public void statsUpdate() {
    if (database != null) {
      database.activateOnCurrentThread();
      stats.lastDatabase = database.getName();
      stats.lastUser = database.getUser() != null ? database.getUser().getName(database) : null;
      stats.activeQueries = getActiveQueries(database);
    } else {
      stats.lastDatabase = null;
      stats.lastUser = null;
    }

    ++stats.totalRequests;
    data.commandInfo = "Listening";
    data.commandDetail = "-";
    stats.lastCommandReceived = System.currentTimeMillis();
  }

  private List<String> getActiveQueries(YTDatabaseSessionInternal database) {
    try {
      List<String> result = new ArrayList<>();
      Map<String, OQueryDatabaseState> queries = database.getActiveQueries();
      for (OQueryDatabaseState oResultSet : queries.values()) {
        Optional<OExecutionPlan> plan = oResultSet.getResultSet().getExecutionPlan();
        if (!plan.isPresent()) {
          continue;
        }
        OExecutionPlan p = plan.get();
        if (p instanceof OInternalExecutionPlan) {
          String stm = ((OInternalExecutionPlan) p).getStatement();
          if (stm != null) {
            result.add(stm);
          }
        }
      }
      return result;
    } catch (Exception e) {
    }
    return null;
  }

  public void setDisconnectOnAfter(boolean disconnectOnAfter) {
    this.disconnectOnAfter = disconnectOnAfter;
  }

  public boolean isDisconnectOnAfter() {
    return disconnectOnAfter;
  }

  public OBinaryRequestExecutor getExecutor() {
    return executor;
  }

  public boolean tryAcquireForExpire() {
    try {
      return lock.tryLock(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  public void setToken(OParsedToken parsedToken, byte[] tokenBytes) {
    this.token = parsedToken;
    this.tokenBytes = tokenBytes;
    this.tokenBased = true;
  }
}
