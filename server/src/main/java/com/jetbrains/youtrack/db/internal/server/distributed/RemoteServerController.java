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
package com.jetbrains.youtrack.db.internal.server.distributed;

import com.jetbrains.youtrack.db.api.config.GlobalConfiguration;
import java.io.IOException;

/**
 * Remote server controller. It handles the communication with remote servers in HA configuration.
 */
public class RemoteServerController {

  private final RemoteServerChannel[] requestChannels;
  private int requestChannelIndex = 0;

  private final RemoteServerChannel[] responseChannels;
  private int responseChannelIndex = 0;

  private int protocolVersion = -1;
  public static final int CURRENT_PROTOCOL_VERSION = 2;
  public static final int MIN_SUPPORTED_PROTOCOL_VERSION = 2;

  public RemoteServerController(
      final ORemoteServerAvailabilityCheck check,
      String localNodeName,
      final String iServer,
      final String iURL,
      final String user,
      final String passwd)
      throws IOException {
    if (user == null) {
      throw new IllegalArgumentException("User is null");
    }
    if (passwd == null) {
      throw new IllegalArgumentException("Password is null");
    }

    DistributedServerLog.debug(
        this,
        localNodeName,
        iServer,
        DistributedServerLog.DIRECTION.OUT,
        "Creating remote channel(s) to distributed server...");

    requestChannels =
        new RemoteServerChannel
            [GlobalConfiguration.DISTRIBUTED_REQUEST_CHANNELS.getValueAsInteger()];
    for (int i = 0; i < requestChannels.length; ++i) {
      requestChannels[i] =
          new RemoteServerChannel(
              check, localNodeName, iServer, iURL, user, passwd, CURRENT_PROTOCOL_VERSION);
    }

    protocolVersion = requestChannels[0].getDistributedProtocolVersion();

    responseChannels =
        new RemoteServerChannel
            [GlobalConfiguration.DISTRIBUTED_RESPONSE_CHANNELS.getValueAsInteger()];
    for (int i = 0; i < responseChannels.length; ++i) {
      responseChannels[i] =
          new RemoteServerChannel(
              check, localNodeName, iServer, iURL, user, passwd, CURRENT_PROTOCOL_VERSION);
    }
  }

  public void sendRequest(final DistributedRequest req) {
    int idx;
    synchronized (requestChannels) {
      requestChannelIndex++;
      if (requestChannelIndex < 0) {
        requestChannelIndex = 0;
      }
      idx = requestChannelIndex % requestChannels.length;
    }
    requestChannels[idx].sendRequest(req);
  }

  public void sendResponse(final DistributedResponse response) {
    int idx;
    synchronized (responseChannels) {
      responseChannelIndex++;
      if (responseChannelIndex < 0) {
        responseChannelIndex = 0;
      }
      idx = responseChannelIndex % responseChannels.length;
    }
    responseChannels[idx].sendResponse(response);
  }

  public void close() {
    for (int i = 0; i < requestChannels.length; ++i) {
      requestChannels[i].close();
    }

    for (int i = 0; i < responseChannels.length; ++i) {
      responseChannels[i].close();
    }
  }

  public int getProtocolVersion() {
    return protocolVersion;
  }

}
