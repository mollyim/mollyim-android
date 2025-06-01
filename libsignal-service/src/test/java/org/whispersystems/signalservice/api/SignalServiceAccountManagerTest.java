package org.whispersystems.signalservice.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisioningSocket;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;

@RunWith(JUnit4.class)
public class SignalServiceAccountManagerTest {

    private IdentityKeyPair generateIdentityKeyPair() throws InvalidKeyException {
        ECKeyPair ecKeyPair = Curve.generateKeyPair();
        return new IdentityKeyPair(new IdentityKey(ecKeyPair.getPublicKey()), ecKeyPair.getPrivateKey());
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new java.util.Random().nextBytes(bytes);
        return bytes;
    }

    @Test
    public void provisionDecryptResult_constructorAndGetter() {
        String provisioningCode = "test_code";
        IdentityKeyPair aciIdentity = null;
        IdentityKeyPair pniIdentity = null;
        try {
            aciIdentity = generateIdentityKeyPair();
            pniIdentity = generateIdentityKeyPair();
        } catch (InvalidKeyException e) {
            fail("Key generation failed: " + e.getMessage());
        }
        String number = "+14152223333";
        ServiceId.ACI aci = ServiceId.ACI.from(UUID.randomUUID());
        ServiceId.PNI pni = ServiceId.PNI.from(UUID.randomUUID());
        ProfileKey profileKey = new ProfileKey(generateRandomBytes(32));
        boolean readReceipts = true;
        MasterKey masterKey = new MasterKey(generateRandomBytes(32));
        byte[] peerExtraPublicKey = generateRandomBytes(33);

        SignalServiceAccountManager.ProvisionDecryptResult result = new SignalServiceAccountManager.ProvisionDecryptResult(
                provisioningCode, aciIdentity, pniIdentity, number, aci, pni, profileKey, readReceipts, masterKey, peerExtraPublicKey
        );

        assertEquals(provisioningCode, result.getProvisioningCode());
        assertEquals(aciIdentity, result.getAciIdentity());
        assertEquals(pniIdentity, result.getPniIdentity());
        assertEquals(number, result.getNumber());
        assertEquals(aci, result.getAci());
        assertEquals(pni, result.getPni());
        assertEquals(profileKey, result.getProfileKey());
        assertEquals(readReceipts, result.isReadReceipts());
        assertEquals(masterKey, result.getMasterKey());
        assertArrayEquals(peerExtraPublicKey, result.getPeerExtraPublicKey());
    }

    @Test
    public void provisionDecryptResult_constructorAndGetter_nullPeerExtraPublicKey() {
        // Similar to above, but peerExtraPublicKey is null
        String provisioningCode = "test_code_null";
        IdentityKeyPair aciIdentity = null;
        try {
            aciIdentity = generateIdentityKeyPair();
        } catch (InvalidKeyException e) {
            fail("Key generation failed: " + e.getMessage());
        }
        String number = "+14152223333";
        ServiceId.ACI aci = ServiceId.ACI.from(UUID.randomUUID());
        ProfileKey profileKey = new ProfileKey(generateRandomBytes(32));

        SignalServiceAccountManager.ProvisionDecryptResult result = new SignalServiceAccountManager.ProvisionDecryptResult(
                provisioningCode, aciIdentity, null, number, aci, null, profileKey, false, null, null
        );
        assertNull(result.getPeerExtraPublicKey());
    }


    @Test
    public void getNewDeviceRegistration_withPeerExtraPublicKey() throws IOException, InvalidKeyException {
        ProvisioningSocket mockProvisioningSocket = Mockito.mock(ProvisioningSocket.class);

        IdentityKeyPair tempIdentity = generateIdentityKeyPair();
        byte[] peerExtraPublicKeyBytes = generateRandomBytes(33);

        ProvisionMessage mockProvisionMessage = ProvisionMessage.newBuilder()
                .setAci(ServiceId.ACI.from(UUID.randomUUID()).toString())
                .setNumber("+14152223333")
                .setProvisioningCode("test_prov_code")
                .setAciIdentityKeyPublic(ByteString.copyFrom(tempIdentity.getPublicKey().serialize()))
                .setAciIdentityKeyPrivate(ByteString.copyFrom(tempIdentity.getPrivateKey().serialize()))
                .setPeerExtraPublicKey(ByteString.copyFrom(peerExtraPublicKeyBytes))
                .build();

        when(mockProvisioningSocket.getProvisioningMessage(Mockito.any(IdentityKeyPair.class)))
                .thenReturn(mockProvisionMessage);

        SignalServiceAccountManager accountManager = new SignalServiceAccountManager(
                null, null, Mockito.mock(PushServiceSocket.class), mockProvisioningSocket, Mockito.mock(GroupsV2Operations.class)
        );

        SignalServiceAccountManager.ProvisionDecryptResult result = accountManager.getNewDeviceRegistration(tempIdentity);

        assertNotNull(result);
        assertArrayEquals(peerExtraPublicKeyBytes, result.getPeerExtraPublicKey());
        // Also check a few other fields to ensure basic parsing worked
        assertEquals("+14152223333", result.getNumber());
        assertEquals("test_prov_code", result.getProvisioningCode());
    }
}
