package com.google.android.gms.cloudmessaging;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class GmsOpCode {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = { SEND, BROADCAST_ACK, RPC_ACK, PROXY_RETAIN, PROXY_FETCH })
  @interface Code {}

  static final int SEND          = 1;
  static final int BROADCAST_ACK = 2;
  static final int RPC_ACK       = 3;
  static final int PROXY_RETAIN  = 4;
  static final int PROXY_FETCH   = 5;

  private GmsOpCode() {}
}
