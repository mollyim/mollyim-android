package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress

internal class AccountValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(AccountValues::class.java)
    // MOLLY: Ensure all keys below are parametrized with the account number
    private const val KEY_SERVICE_PASSWORD = "account.1.service_password"
    private const val KEY_IS_REGISTERED = "account.1.is_registered"
    private const val KEY_REGISTRATION_ID = "account.1.registration_id"
    private const val KEY_FCM_ENABLED = "account.1.fcm_enabled"
    private const val KEY_FCM_TOKEN = "account.1.fcm_token"
    private const val KEY_FCM_TOKEN_VERSION = "account.1.fcm_token_version"
    private const val KEY_FCM_TOKEN_LAST_SET_TIME = "account.1.fcm_token_last_set_time"
    private const val KEY_DEVICE_NAME = "account.1.device_name"
    private const val KEY_DEVICE_ID = "account.1.device_id"
    @VisibleForTesting
    const val KEY_E164 = "account.1.e164"
    @VisibleForTesting
    const val KEY_ACI = "account.1.aci"
    @VisibleForTesting
    const val KEY_PNI = "account.1.pni"
  }

  init {
    migrateFromSharedPrefs()
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return emptyList()
  }

  /** The local user's [ACI]. */
  val aci: ACI?
    get() = ACI.parseOrNull(getString(KEY_ACI, null))

  fun setAci(aci: ACI) {
    putString(KEY_ACI, aci.toString())
  }

  /** The local user's [PNI]. */
  val pni: PNI?
    get() = PNI.parseOrNull(getString(KEY_PNI, null))

  fun setPni(pni: PNI) {
    putString(KEY_PNI, pni.toString())
  }

  /** The local user's E164. */
  val e164: String?
    get() = getString(KEY_E164, null)

  fun setE164(e164: String) {
    putString(KEY_E164, e164)
  }

  /** The password for communicating with the Signal service. */
  val servicePassword: String?
    get() = getString(KEY_SERVICE_PASSWORD, null)

  fun setServicePassword(servicePassword: String) {
    putString(KEY_SERVICE_PASSWORD, servicePassword)
  }

  /** A randomly-generated value that represents this registration instance. Helps the server know if you reinstalled. */
  var registrationId: Int
    get() = getInteger(KEY_REGISTRATION_ID, 0)
    set(value) = putInteger(KEY_REGISTRATION_ID, value)

  /** Indicates whether the user has the ability to receive FCM messages. Largely coupled to whether they have Play Service. */
  var fcmEnabled: Boolean
    @JvmName("isFcmEnabled")
    get() = getBoolean(KEY_FCM_ENABLED, false)
    set(value) = putBoolean(KEY_FCM_ENABLED, value)

  /** The FCM token, which allows the server to send us FCM messages. */
  var fcmToken: String?
    get() {
      val tokenVersion: Int = getInteger(KEY_FCM_TOKEN_VERSION, 0)
      return if (tokenVersion == Util.getSignalCanonicalVersionCode()) {
        getString(KEY_FCM_TOKEN, null)
      } else {
        null
      }
    }
    set(value) {
      store.beginWrite()
        .putString(KEY_FCM_TOKEN, value)
        .putInteger(KEY_FCM_TOKEN_VERSION, Util.getSignalCanonicalVersionCode())
        .putLong(KEY_FCM_TOKEN_LAST_SET_TIME, System.currentTimeMillis())
        .apply()
    }

  /** When we last set the [fcmToken] */
  var fcmTokenLastSetTime: Long
    get() = getLong(KEY_FCM_TOKEN_LAST_SET_TIME, 0)
    set(value) = putLong(KEY_FCM_TOKEN_LAST_SET_TIME, value)

  /** Whether or not the user is registered with the Signal service. */
  val isRegistered: Boolean
    get() = getBoolean(KEY_IS_REGISTERED, false)

  fun setRegistered(registered: Boolean) {
    Log.i(TAG, "Setting push registered: $registered", Throwable())

    val previous = isRegistered

    putBoolean(KEY_IS_REGISTERED, registered)

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
    putString(KEY_SERVICE_PASSWORD, Util.getSecret(18))

    val newProfileKey = ProfileKeyUtil.createNew()
    val self = Recipient.self()

    SignalDatabase.recipients.setProfileKey(self.id, newProfileKey)
    ApplicationDependencies.getGroupsV2Authorization().clear()
  }

  private fun migrateFromSharedPrefs() {
    val sharedPreferences = SecurePreferenceManager.getSecurePreferences(ApplicationDependencies.getApplication())
    if (sharedPreferences.contains("pref_local_uuid")) {
      Log.i(TAG, "Migrating account 1 values from shared prefs")

      putString(KEY_ACI, sharedPreferences.getString("pref_local_uuid", null))
      putString(KEY_E164, sharedPreferences.getString("pref_local_number", null))
      putString(KEY_SERVICE_PASSWORD, sharedPreferences.getString("pref_gcm_password", null))
      putBoolean(KEY_IS_REGISTERED, sharedPreferences.getBoolean("pref_gcm_registered", false))
      putInteger(KEY_REGISTRATION_ID, sharedPreferences.getInt("pref_local_registration_id", 0))
      putBoolean(KEY_FCM_ENABLED, !sharedPreferences.getBoolean("pref_gcm_disabled", false))
      putString(KEY_FCM_TOKEN, sharedPreferences.getString("pref_gcm_registration_id", null))
      putInteger(KEY_FCM_TOKEN_VERSION, sharedPreferences.getInt("pref_gcm_registration_id_version", 0))
      putLong(KEY_FCM_TOKEN_LAST_SET_TIME, sharedPreferences.getLong("pref_gcm_registration_id_last_set_time", 0))

      sharedPreferences.edit().remove("pref_local_uuid").apply()
    }
  }
}
