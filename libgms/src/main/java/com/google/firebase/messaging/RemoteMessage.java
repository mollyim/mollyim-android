package com.google.firebase.messaging;

public class RemoteMessage {
  public Data getData() {
    return new Data();
  }

  public long getSentTime() {
    return 0;
  }

  public static class Data {
    public String get(String challenge) {
      return null;
    }
  }
}
