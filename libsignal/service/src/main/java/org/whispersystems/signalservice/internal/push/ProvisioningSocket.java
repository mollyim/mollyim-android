package org.whispersystems.signalservice.internal.push;

import okio.ByteString;

import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisioningUuid;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ProvisioningSocket {

  private WebSocketConnection connection;
  private boolean             connected = false;

  public ProvisioningSocket(SignalServiceConfiguration signalServiceConfiguration, String userAgent) {
    connection = new WebSocketConnection(
        "provisioning",
        signalServiceConfiguration,
        Optional.empty(),
        userAgent,
        new HealthMonitor() {
          @Override
          public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket) {
          }

          @Override
          public void onMessageError(int status, boolean isIdentifiedWebSocket) {
          }
        },
        "provisioning/",
        false
    );
  }

  public ProvisioningUuid getProvisioningUuid() throws TimeoutException, IOException {
    if (!connected) {
      connection.connect();
      connected = true;
    }
    ByteString       bytes = readRequest();
    ProvisioningUuid msg   = ProvisioningUuid.ADAPTER.decode(bytes);
    return msg;
  }

  public ProvisionMessage getProvisioningMessage(IdentityKeyPair tempIdentity) throws TimeoutException, IOException {
    if (!connected) {
      throw new IllegalStateException("No UUID requested yet!");
    }
    ByteString bytes = readRequest();
    connection.disconnect();
    connected = false;
    ProvisionMessage msg;
    try {
      msg = new PrimaryProvisioningCipher(null).decrypt(tempIdentity, bytes.toByteArray());
      return msg;
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private ByteString readRequest() throws TimeoutException, IOException {
    WebSocketRequestMessage response = connection.readRequest(100000);
    ByteString              bytes    = response.body;
    return bytes;
  }

}
