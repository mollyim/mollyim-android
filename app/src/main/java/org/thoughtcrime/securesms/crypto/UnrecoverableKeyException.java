package org.thoughtcrime.securesms.crypto;

public class UnrecoverableKeyException extends Exception {
  public UnrecoverableKeyException() {
    super();
  }

  public UnrecoverableKeyException(String message) {
    super(message);
  }

  public UnrecoverableKeyException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnrecoverableKeyException(Throwable cause) {
    super(cause);
  }
}
