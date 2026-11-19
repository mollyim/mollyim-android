/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.junit.Test;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BlacklistingTrustManagerTest {

  private static final String AUTH_TYPE = "ECDHE_ECDSA";

  @Test
  public void blacklistMatchesPublishedEmudhraKeys() {
    assertEquals(8, BlacklistingTrustManager.BLACKLISTED_PUBLIC_KEY_SHA256.size());
  }

  @Test
  public void compromisedIntermediateIsRejectedEvenWhenDelegateTrustsIt() throws Exception {
    RecordingTrustManager delegate = new RecordingTrustManager();
    BlacklistingTrustManager trustManager = new BlacklistingTrustManager(delegate);

    try {
      trustManager.checkServerTrusted(new X509Certificate[] { readCertificate("emudhra-impacted-intermediate.pem") }, AUTH_TYPE);
      fail("Expected compromised intermediate to be rejected");
    } catch (CertificateException expected) {
      assertTrue(expected.getMessage().startsWith("Blacklisted public key"));
    }

    assertEquals(0, delegate.serverChecks);
  }

  @Test
  public void nonBlacklistedCertificateIsAcceptedAndDelegateIsConsulted() throws Exception {
    RecordingTrustManager delegate = new RecordingTrustManager();
    BlacklistingTrustManager trustManager = new BlacklistingTrustManager(delegate);

    trustManager.checkServerTrusted(new X509Certificate[] { readCertificate("ias.cert") }, AUTH_TYPE);

    assertEquals(1, delegate.serverChecks);
  }

  private static X509Certificate readCertificate(String resource) throws Exception {
    try (InputStream in = BlacklistingTrustManagerTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + resource);
      }
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    }
  }

  private static final class RecordingTrustManager implements X509TrustManager {
    int serverChecks;

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      serverChecks++;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
