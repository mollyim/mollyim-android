package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceId {

  @JsonProperty
  private int deviceId;

  public int getDeviceId() {
    return deviceId;
  }

}
