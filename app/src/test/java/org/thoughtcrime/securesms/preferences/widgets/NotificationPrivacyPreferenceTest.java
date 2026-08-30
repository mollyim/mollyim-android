package org.thoughtcrime.securesms.preferences.widgets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NotificationPrivacyPreferenceTest {

  @Test
  public void fullModeDisplaysContactAndMessage() {
    NotificationPrivacyPreference preference = new NotificationPrivacyPreference(NotificationPrivacyPreference.MODE_FULL);

    assertTrue(preference.isDisplayNotification());
    assertTrue(preference.isDisplayContact());
    assertTrue(preference.isDisplayMessage());
    assertFalse(preference.isDisplayGeneric());
  }

  @Test
  public void contactModeDisplaysOnlyContact() {
    NotificationPrivacyPreference preference = new NotificationPrivacyPreference(NotificationPrivacyPreference.MODE_CONTACT);

    assertTrue(preference.isDisplayNotification());
    assertTrue(preference.isDisplayContact());
    assertFalse(preference.isDisplayMessage());
  }

  @Test
  public void genericModeHidesSensitiveContent() {
    NotificationPrivacyPreference preference = new NotificationPrivacyPreference(NotificationPrivacyPreference.MODE_GENERIC);

    assertTrue(preference.isDisplayNotification());
    assertFalse(preference.isDisplayContact());
    assertFalse(preference.isDisplayMessage());
    assertTrue(preference.isDisplayGeneric());
    assertTrue(preference.isDisplayNothing());
  }

  @Test
  public void hiddenModeSuppressesNotification() {
    NotificationPrivacyPreference preference = new NotificationPrivacyPreference(NotificationPrivacyPreference.MODE_HIDDEN);

    assertFalse(preference.isDisplayNotification());
    assertFalse(preference.isDisplayContact());
    assertFalse(preference.isDisplayMessage());
    assertTrue(preference.isDisplayNothing());
  }

  @Test
  public void legacyValuesRemainStable() {
    assertTrue(new NotificationPrivacyPreference("all").isDisplayMessage());
    assertTrue(new NotificationPrivacyPreference("contact").isDisplayContact());
    assertFalse(new NotificationPrivacyPreference("none").isDisplayContact());
  }
}
