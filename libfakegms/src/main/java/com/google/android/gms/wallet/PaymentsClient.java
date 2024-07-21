package com.google.android.gms.wallet;

import com.google.android.gms.tasks.Task;

public class PaymentsClient {
  public Task<Boolean> isReadyToPay(IsReadyToPayRequest request) {
    return new Task<>();
  }

  public Task<PaymentData> loadPaymentData(PaymentDataRequest request) {
    return new Task<>();
  }
}
