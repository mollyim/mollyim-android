package org.thoughtcrime.securesms.preferences.widgets;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Notification preview privacy policy.
 *
 * <p>The legacy values (all/contact/none) remain supported. New values allow
 * a custom build to distinguish a generic notification from a completely
 * suppressed notification without changing the encrypted messaging layer.</p>
 */
public final class NotificationPrivacyPreference {

  public static final String MODE_FULL = "all";
  public static final String MODE_CONTACT = "contact";
  public static final String MODE_GENERIC = "generic";
  public static final String MODE_HIDDEN = "hidden";

  private final String preference;

  public NotificationPrivacyPreference(String preference) {
    this.preference = normalize(preference);
  }

  private static String normalize(String value) {
    if (MODE_FULL.equals(value) || MODE_CONTACT.equals(value) ||
        MODE_GENERIC.equals(value) || MODE_HIDDEN.equals(value)) {
      return value;
    }

    // Preserve the historical default behavior for unknown/legacy values.
    return MODE_GENERIC;
  }

  public boolean isDisplayContact() {
    return MODE_FULL.equals(preference) || MODE_CONTACT.equals(preference);
  }

  public boolean isDisplayMessage() {
    return MODE_FULL.equals(preference);
  }

  /** Returns true when a notification may be posted at all. */
  public boolean isDisplayNotification() {
    return !MODE_HIDDEN.equals(preference);
  }

  /** Returns true when the notification should contain a generic message. */
  public boolean isDisplayGeneric() {
    return MODE_GENERIC.equals(preference);
  }

  public boolean isDisplayNothing() {
    return !isDisplayContact() && !isDisplayMessage();
  }

  @NonNull
  public String getMode() {
    return preference;
  }

  @Override
  public @NonNull String toString() {
    return preference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final NotificationPrivacyPreference that = (NotificationPrivacyPreference) o;
    return Objects.equals(preference, that.preference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(preference);
  }
}
