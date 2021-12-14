package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.service.KeyCachingService;

import java.util.List;

interface LogSection {
  /**
   * The title to show at the top of the log section.
   */
  @NonNull String getTitle();

  /**
   * The full content of your log section. We use a {@link CharSequence} instead of a
   * {@link List<LogLine> } for performance reasons. Scrubbing large swaths of text is faster than
   * one line at a time.
   */
  @NonNull CharSequence getContent(@NonNull Context context);

  /**
   * Whether or not this section has content.
   */
  default boolean hasContent() {
    return true;
  }

  default boolean isInitialized() {
    switch (getTitle()) {
      case "SYSINFO":
      case "POWER":
      case "PERMISSIONS":
      case "TRACE":
      case "THREADS":
      case "BLOCKED THREADS":
      case "LOGCAT":
        return true;
      default:
        return !KeyCachingService.isLocked();
    }
  }
}
