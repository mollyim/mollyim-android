package com.google.android.gms.cloudmessaging;

import android.os.Bundle;

import androidx.annotation.NonNull;

public class CloudMessageBundle {

  private CloudMessageBundle() {}

  @NonNull
  public static Bundle forMessage(@NonNull CloudMessage message) {
    Bundle bundle = new Bundle();
    bundle.putString("google.message_id", message.getMessageId());

    Integer productId = message.getProductId();
    if (productId != null) {
      bundle.putInt("google.product_id", productId);
    }

    return bundle;
  }
}
