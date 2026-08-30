package org.thoughtcrime.securesms.privacy

/** User-selectable notification disclosure policy. */
enum class NotificationPrivacyMode {
  FULL,
  CONTACT_ONLY,
  GENERIC,
  HIDDEN;

  companion object {
    fun fromStored(value: String?): NotificationPrivacyMode =
      values().firstOrNull { it.name == value } ?: CONTACT_ONLY
  }
}
