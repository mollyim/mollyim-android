package org.thoughtcrime.securesms.keyvalue

import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.StringSerializer
import java.security.MessageDigest
import java.security.SecureRandom

class ParentalControlValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_PARENTAL_MODE_ENABLED = "pc.parental_mode_enabled"
    private const val KEY_PARENT_PIN_HASH = "pc.parent_pin_hash"
    private const val KEY_PIN_SALT = "pc.pin_salt"
    private const val KEY_ALLOWED_THREAD_IDS = "pc.allowed_thread_ids"

    fun computePinHash(pin: String, salt: ByteArray): String {
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(salt)
      digest.update(pin.toByteArray(Charsets.UTF_8))
      return digest.digest().joinToString("") { "%02x".format(it) }
    }
  }

  val settingsChanges: PublishSubject<Unit> = PublishSubject.create()

  var parentalModeEnabled: Boolean
    get() = getBoolean(KEY_PARENTAL_MODE_ENABLED, true)
    set(value) {
      putBoolean(KEY_PARENTAL_MODE_ENABLED, value)
      settingsChanges.onNext(Unit)
    }

  var parentPinHash: String by stringValue(KEY_PARENT_PIN_HASH, "")

  fun getPinSalt(): ByteArray {
    return getBlob(KEY_PIN_SALT, null) ?: ByteArray(16).also {
      SecureRandom().nextBytes(it)
      putBlob(KEY_PIN_SALT, it)
    }
  }

  fun getAllowedThreadIds(): Set<Long> {
    return getList(KEY_ALLOWED_THREAD_IDS, LongSerializer).toSet()
  }

  fun setAllowedThreadIds(ids: Set<Long>) {
    putList(KEY_ALLOWED_THREAD_IDS, ids.toList(), LongSerializer)
    settingsChanges.onNext(Unit)
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  private object LongSerializer : StringSerializer<Long> {
    override fun serialize(data: Long): String = data.toString()
    override fun deserialize(data: String): Long = data.toLong()
  }
}
