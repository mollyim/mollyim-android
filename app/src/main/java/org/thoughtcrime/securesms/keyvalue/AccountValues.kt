package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress

internal class AccountValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  val context = ApplicationDependencies.getApplication()

  companion object {
    private val TAG = Log.tag(AccountValues::class.java)
    private const val KEY_SERVICE_PASSWORD = "account.service_password"
    private const val KEY_IS_REGISTERED = "account.is_registered"
    private const val KEY_REGISTRATION_ID = "account.registration_id"
    private const val KEY_FCM_ENABLED = "account.fcm_enabled"
    private const val KEY_FCM_TOKEN = "account.fcm_token"
    private const val KEY_FCM_TOKEN_VERSION = "account.fcm_token_version"
    private const val KEY_FCM_TOKEN_LAST_SET_TIME = "account.fcm_token_last_set_time"
    private const val KEY_DEVICE_NAME = "account.device_name"
    private const val KEY_DEVICE_ID = "account.device_id"

    @VisibleForTesting
    const val KEY_E164 = "account.e164"
    @VisibleForTesting
    const val KEY_ACI = "account.aci"
    @VisibleForTesting
    const val KEY_PNI = "account.pni"
  }

  init {
//    if (!store.containsKey(KEY_ACI)) {
//      migrateFromSharedPrefs(ApplicationDependencies.getApplication())
//    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return emptyList()
  }

  /** The local user's [ACI]. */
  val aci: ACI?
    get() = ACI.parseOrNull(TextSecurePreferences.getStringPreference(context, "pref_local_uuid", null))

  fun setAci(aci: ACI) {
    TextSecurePreferences.setStringPreference(context, "pref_local_uuid", aci.toString())
  }

  /** The local user's [PNI]. */
  val pni: PNI?
    get() = PNI.parseOrNull(getString(KEY_PNI, null))

  fun setPni(pni: PNI) {
    putString(KEY_PNI, pni.toString())
  }

  /** The local user's E164. */
  val e164: String?
    get() = TextSecurePreferences.getStringPreference(context, "pref_local_number", null)

  fun setE164(e164: String) {
    TextSecurePreferences.setStringPreference(context, "pref_local_number", e164)
  }

  /** The password for communicating with the Signal service. */
  val servicePassword: String?
    get() = TextSecurePreferences.getStringPreference(context, "pref_gcm_password", null)

  fun setServicePassword(servicePassword: String) {
    TextSecurePreferences.setStringPreference(context, "pref_gcm_password", servicePassword)
  }

  /** A randomly-generated value that represents this registration instance. Helps the server know if you reinstalled. */
  var registrationId: Int
    get() = TextSecurePreferences.getIntegerPreference(context, "pref_local_registration_id", 0)
    set(value) = TextSecurePreferences.setIntegerPrefrence(context, "pref_local_registration_id", value)

  /** Indicates whether the user has the ability to receive FCM messages. Largely coupled to whether they have Play Service. */
  var fcmEnabled: Boolean
    @JvmName("isFcmEnabled")
    get() = !TextSecurePreferences.getBooleanPreference(context, "pref_gcm_disabled", false)
    set(value) = TextSecurePreferences.setBooleanPreference(context, "pref_gcm_disabled", !value)

  /** The FCM token, which allows the server to send us FCM messages. */
  var fcmToken: String?
    get() {
      val tokenVersion: Int = TextSecurePreferences.getIntegerPreference(context, "pref_gcm_registration_id_version", 0)
      return if (tokenVersion == Util.getSignalCanonicalVersionCode()) {
        TextSecurePreferences.getStringPreference(context, "pref_gcm_registration_id", null)
      } else {
        null
      }
    }
    set(value) {
      TextSecurePreferences.setStringPreference(context, "pref_gcm_registration_id", value)
      TextSecurePreferences.setIntegerPrefrence(context, "pref_gcm_registration_id_version", Util.getSignalCanonicalVersionCode())
      TextSecurePreferences.setLongPreference(context, "pref_gcm_registration_id_last_set_time", System.currentTimeMillis())
    }

  /** When we last set the [fcmToken] */
  var fcmTokenLastSetTime: Long
    get() = TextSecurePreferences.getLongPreference(context, "pref_gcm_registration_id_last_set_time", 0)
    set(value) = TextSecurePreferences.setLongPreference(context, "pref_gcm_registration_id_last_set_time", value)

  /** Whether or not the user is registered with the Signal service. */
  val isRegistered: Boolean
    get() = TextSecurePreferences.getBooleanPreference(context, "pref_gcm_registered", false)

  fun setRegistered(registered: Boolean) {
    Log.i(TAG, "Setting push registered: $registered", Throwable())

    val previous = isRegistered

    TextSecurePreferences.setBooleanPreference(context, "pref_gcm_registered", registered)

    ApplicationDependencies.getIncomingMessageObserver().notifyRegistrationChanged()

    if (previous != registered) {
      Recipient.self().live().refresh()
    }

    if (previous && !registered) {
      clearLocalCredentials(ApplicationDependencies.getApplication())
    }
  }

  val deviceName: String?
    get() = getString(KEY_DEVICE_NAME, null)

  fun setDeviceName(deviceName: String) {
    putString(KEY_DEVICE_NAME, deviceName)
  }

  var deviceId: Int by integerValue(KEY_DEVICE_ID, SignalServiceAddress.DEFAULT_DEVICE_ID)

  val isPrimaryDevice: Boolean
    get() = deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID

  val isLinkedDevice: Boolean
    get() = !isPrimaryDevice

  private fun clearLocalCredentials(context: Context) {
    TextSecurePreferences.setStringPreference(context, "pref_gcm_password", Util.getSecret(18))

    val newProfileKey = ProfileKeyUtil.createNew()
    val self = Recipient.self()

    SignalDatabase.recipients.setProfileKey(self.id, newProfileKey)
    ApplicationDependencies.getGroupsV2Authorization().clear()
  }

//  private fun migrateFromSharedPrefs(context: Context) {
//    Log.i(TAG, "Migrating account values from shared prefs.")
//
//    putString(KEY_ACI, TextSecurePreferences.getStringPreference(context, "pref_local_uuid", null))
//    putString(KEY_E164, TextSecurePreferences.getStringPreference(context, "pref_local_number", null))
//    putString(KEY_SERVICE_PASSWORD, TextSecurePreferences.getStringPreference(context, "pref_gcm_password", null))
//    putBoolean(KEY_IS_REGISTERED, TextSecurePreferences.getBooleanPreference(context, "pref_gcm_registered", false))
//    putInteger(KEY_REGISTRATION_ID, TextSecurePreferences.getIntegerPreference(context, "pref_local_registration_id", 0))
//    putBoolean(KEY_FCM_ENABLED, !TextSecurePreferences.getBooleanPreference(context, "pref_gcm_disabled", false))
//    putString(KEY_FCM_TOKEN, TextSecurePreferences.getStringPreference(context, "pref_gcm_registration_id", null))
//    putInteger(KEY_FCM_TOKEN_VERSION, TextSecurePreferences.getIntegerPreference(context, "pref_gcm_registration_id_version", 0))
//    putLong(KEY_FCM_TOKEN_LAST_SET_TIME, TextSecurePreferences.getLongPreference(context, "pref_gcm_registration_id_last_set_time", 0))
//  }
}
