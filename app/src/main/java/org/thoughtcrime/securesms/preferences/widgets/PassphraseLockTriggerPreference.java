package org.thoughtcrime.securesms.preferences.widgets;

import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.util.Util;

import java.util.Set;

public class PassphraseLockTriggerPreference {

  private final Set<String> triggers;

  public PassphraseLockTriggerPreference(Set<String> triggers) {
    this.triggers = triggers;
  }

  public boolean isTimeoutEnabled() {
    return triggers.contains("timeout");
  }

  public Set<String> getTriggers() {
    return triggers;
  }

  @NonNull
  @Override
  public String toString() {
    return triggers.isEmpty() ? "none" : Util.join(triggers, " ");
  }
}
