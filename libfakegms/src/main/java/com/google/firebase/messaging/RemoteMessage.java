package com.google.firebase.messaging;

import android.os.Bundle;

public class RemoteMessage {
  public static final int PRIORITY_HIGH = 1;

  public RemoteMessage(Bundle bundle) {
  }

  public Data getData() {
    return new Data();
  }

  public long getSentTime() {
    return 0;
  }

  public String getMessageId() {
    return null;
  }

  public int getOriginalPriority() {
    return 0;
  }

  public int getPriority() {
    return PRIORITY_HIGH;
  }

  public static class Data {
    public String get(String challenge) {
      return null;
    }
  }
}
