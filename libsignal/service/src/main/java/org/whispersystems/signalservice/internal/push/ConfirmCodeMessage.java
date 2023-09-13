package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.account.AccountAttributes.Capabilities;

public class ConfirmCodeMessage {

  @JsonProperty
  private boolean supportsSms;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private int registrationId;

  @JsonProperty
  private int pniRegistrationId;

  @JsonProperty
  private String name;

  @JsonProperty
  private Capabilities capabilities;

  public ConfirmCodeMessage(boolean supportsSms, boolean fetchesMessages, int registrationId, int pniRegistrationId, String name, Capabilities capabilities) {
    this.supportsSms = supportsSms;
    this.fetchesMessages = fetchesMessages;
    this.registrationId = registrationId;
    this.pniRegistrationId = pniRegistrationId;
    this.name = name;
    this.capabilities = capabilities;
  }
}
