package com.orientechnologies.orient.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;

import com.orientechnologies.core.command.OCommandResultListener;
import com.orientechnologies.core.config.YTContextConfiguration;
import com.orientechnologies.core.db.YTDatabaseSessionInternal;
import com.orientechnologies.core.db.record.ORecordOperation;
import com.orientechnologies.core.query.live.OLiveQueryHook;
import com.orientechnologies.core.query.live.OLiveQueryListener;
import com.orientechnologies.core.record.impl.YTEntityImpl;
import com.orientechnologies.core.serialization.serializer.record.binary.ORecordSerializerNetwork;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinaryServer;
import com.orientechnologies.orient.server.network.protocol.binary.OLiveCommandResultListener;
import com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary;
import com.orientechnologies.orient.server.token.OTokenHandlerImpl;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 *
 */
public class OLiveCommandResultListenerTest extends BaseMemoryInternalDatabase {

  @Mock
  private OServer server;
  @Mock
  private OChannelBinaryServer channelBinary;

  @Mock
  private OLiveQueryListener rawListener;

  private ONetworkProtocolBinary protocol;
  private OClientConnection connection;

  private static class TestResultListener implements OCommandResultListener {

    @Override
    public boolean result(YTDatabaseSessionInternal querySession, Object iRecord) {
      return false;
    }

    @Override
    public void end() {
    }

    @Override
    public Object getResult() {
      return null;
    }
  }

  @Before
  public void beforeTests() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(server.getContextConfiguration()).thenReturn(new YTContextConfiguration());

    OClientConnectionManager manager = new OClientConnectionManager(server);
    protocol = new ONetworkProtocolBinary(server);
    protocol.initVariables(server, channelBinary);
    connection = manager.connect(protocol);
    OTokenHandlerImpl tokenHandler = new OTokenHandlerImpl(new YTContextConfiguration());
    Mockito.when(server.getTokenHandler()).thenReturn(tokenHandler);
    byte[] token = tokenHandler.getSignedBinaryToken(db, db.getUser(), connection.getData());
    connection = manager.connect(protocol, connection, token);
    connection.setDatabase(db);
    connection.getData().setSerializationImpl(ORecordSerializerNetwork.NAME);
    Mockito.when(server.getClientConnectionManager()).thenReturn(manager);
  }

  @Test
  public void testSimpleMessageSend() throws IOException {
    OLiveCommandResultListener listener =
        new OLiveCommandResultListener(server, connection, new TestResultListener());
    ORecordOperation op = new ORecordOperation(new YTEntityImpl(), ORecordOperation.CREATED);
    listener.onLiveResult(10, op);
    Mockito.verify(channelBinary, atLeastOnce()).writeBytes(Mockito.any(byte[].class));
  }

  @Test
  public void testNetworkError() throws IOException {
    Mockito.when(channelBinary.writeInt(Mockito.anyInt()))
        .thenThrow(new IOException("Mock Exception"));
    OLiveCommandResultListener listener =
        new OLiveCommandResultListener(server, connection, new TestResultListener());
    OLiveQueryHook.subscribe(10, rawListener, db);
    assertTrue(OLiveQueryHook.getOpsReference(db).getQueueThread().hasToken(10));
    ORecordOperation op = new ORecordOperation(new YTEntityImpl(), ORecordOperation.CREATED);
    listener.onLiveResult(10, op);
    assertFalse(OLiveQueryHook.getOpsReference(db).getQueueThread().hasToken(10));
  }
}
