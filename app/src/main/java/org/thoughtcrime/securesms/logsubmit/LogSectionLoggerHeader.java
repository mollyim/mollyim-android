package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.service.KeyCachingService;

/**
 * Because the actual contents of this section are paged from the database, this class just has a header and no content.
 */
public class LogSectionLoggerHeader implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGGER";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return "Not initialized yet";
  }

  @Override
  public boolean hasContent() {
    return KeyCachingService.isLocked();
  }
}
