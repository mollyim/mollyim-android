package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

final class LogSectionKeyPreferences implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "KEY PREFERENCES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Passphrase Lock      : ").append(TextSecurePreferences.isPassphraseLockEnabled(context)).append("\n")
                              .append("Lock Trigger         : ").append(TextSecurePreferences.getPassphraseLockTrigger(context)).append("\n")
                              .append("Lock Timeout         : ").append(TextSecurePreferences.getPassphraseLockTimeout(context)).append("\n")
                              .append("Default SMS          : ").append(Util.isDefaultSmsProvider(context)).append("\n")
                              .append("Prefer Contact Photos: ").append(SignalStore.settings().isPreferSystemContactPhotos()).append("\n")
                              .append("Call Bandwidth Mode  : ").append(SignalStore.settings().getCallBandwidthMode()).append("\n")
                              .append("Client Deprecated    : ").append(SignalStore.misc().isClientDeprecated()).append("\n")
                              .append("Push Registered      : ").append(TextSecurePreferences.isPushRegistered(context)).append("\n")
                              .append("Unauthorized Received: ").append(TextSecurePreferences.isUnauthorizedRecieved(context)).append("\n")
                              .append("self.isRegistered()  : ").append(TextSecurePreferences.getLocalUuid(context) == null ? "false" : Recipient.self().isRegistered()).append("\n")
                              .append("Thread Trimming      : ").append(getThreadTrimmingString()).append("\n");
  }

  private static String getThreadTrimmingString() {
    if (SignalStore.settings().isTrimByLengthEnabled()) {
      return "Enabled - Max length of " + SignalStore.settings().getThreadTrimLength();
    } else if (SignalStore.settings().getKeepMessagesDuration() != KeepMessagesDuration.FOREVER) {
      return "Enabled - Max age of " + SignalStore.settings().getKeepMessagesDuration();
    } else {
      return "Disabled";
    }
  }
}
