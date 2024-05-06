package org.whispersystems.signalservice.internal.push;

import okio.ByteString;

import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher;
import org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ProvisioningSocket {

  private final WebSocketConnection connection;

  private boolean connected = false;

  public ProvisioningSocket(SignalServiceConfiguration signalServiceConfiguration, String userAgent) {
    connection = new OkHttpWebSocketConnection(
        "provisioning",
        signalServiceConfiguration,
        Optional.empty(),
        userAgent,
        new HealthMonitor() {
          @Override
          public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket, boolean keepMonitoring) {
          }

          @Override
          public void onMessageError(int status, boolean isIdentifiedWebSocket) {
          }
        },
        "provisioning/",
        false
    );
  }

  public synchronized ProvisioningUuid getProvisioningUuid() throws TimeoutException, IOException {
    if (!connected) {
      connection.connect();
      connected = true;
    }
    ByteString bytes = readRequest();
    return ProvisioningUuid.ADAPTER.decode(bytes);
  }

  public synchronized ProvisionMessage getProvisioningMessage(IdentityKeyPair tempIdentity) throws TimeoutException, IOException {
    if (!connected) {
      throw new IllegalStateException("No UUID requested yet!");
    }
    ByteString bytes = readRequest();
    connection.disconnect();
    connected = false;
    ProvisionMessage msg;
    try {
      msg = new PrimaryProvisioningCipher(null).decrypt(tempIdentity, bytes.toByteArray());
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
    return msg;
  }

  private ByteString readRequest() throws TimeoutException, IOException {
    WebSocketRequestMessage response = connection.readRequest(100000);
    return response.body;
  }
}
