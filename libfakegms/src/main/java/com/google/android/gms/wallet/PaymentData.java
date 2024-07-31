package com.google.android.gms.wallet;

import android.content.Intent;

public final class PaymentData {
  public static PaymentData getFromIntent(Intent intent) {
    return new PaymentData();
  }

  public String toJson() {
    return "{}";
  }
}
