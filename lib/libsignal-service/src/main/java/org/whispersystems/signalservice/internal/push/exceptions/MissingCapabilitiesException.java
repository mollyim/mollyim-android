package org.whispersystems.signalservice.internal.push.exceptions;

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public class MissingCapabilitiesException extends NonSuccessfulResponseCodeException {
  public MissingCapabilitiesException() {
    super(409);
  }
}
