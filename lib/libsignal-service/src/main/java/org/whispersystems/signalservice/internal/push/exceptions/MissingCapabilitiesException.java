package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class MissingCapabilitiesException extends NonSuccessfulResponseCodeException {
  public MissingCapabilitiesException() {
    super(409);
  }
}
