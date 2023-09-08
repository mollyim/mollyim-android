package org.whispersystems.signalservice.internal.util;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

import java.util.UUID;

public class DynamicCredentialsProvider implements CredentialsProvider {

  private ACI    aci;
  private PNI    pni;
  private String e164;
  private String password;
  private int    deviceId;

  public DynamicCredentialsProvider(ACI aci, PNI pni, String e164, String password, int deviceId) {
    this.aci = aci;
    this.pni = pni;
    this.e164 = e164;
    this.password = password;
    this.deviceId = deviceId;
  }

  @Override
  public ACI getAci() {
    return aci;
  }

  public void setAci(ACI aci) {
    this.aci = aci;
  }

  @Override
  public PNI getPni() {
    return pni;
  }

  public void setPni(PNI pni) {
    this.pni = pni;
  }

  @Override
  public String getE164() {
    return e164;
  }

  public void setE164(String e164) {
    this.e164 = e164;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public int getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(int deviceId) {
    this.deviceId = deviceId;
  }
}
