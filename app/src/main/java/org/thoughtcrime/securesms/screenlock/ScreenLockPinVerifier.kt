package org.thoughtcrime.securesms.screenlock

import javax.crypto.Mac
import javax.crypto.SecretKey

/** The key stays in Android Keystore in production; neither the key nor PIN is persisted. */
internal object ScreenLockPinVerifier {
  private val domain = "Molly screen lock PIN v1\u0000".toByteArray(Charsets.UTF_8)

  fun calculate(key: SecretKey, salt: ByteArray, pin: CharArray): ByteArray {
    require(salt.size == 32)
    require(pin.size in 4..16 && pin.all { it in '0'..'9' })
    val bytes = ByteArray(pin.size) { pin[it].code.toByte() }
    try {
      return Mac.getInstance("HmacSHA256").apply {
        init(key)
        update(domain)
        update(salt)
      }.doFinal(bytes)
    } finally { bytes.fill(0) }
  }
}
