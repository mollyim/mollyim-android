package org.thoughtcrime.securesms.preferences.widgets;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;
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
}
