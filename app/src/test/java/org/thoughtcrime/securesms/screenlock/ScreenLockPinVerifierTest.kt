package org.thoughtcrime.securesms.screenlock

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ScreenLockPinVerifierTest {
  private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256")
  private val salt = ByteArray(32) { (it + 32).toByte() }
  private fun tag(pin: String) = ScreenLockPinVerifier.calculate(key, salt, pin.toCharArray())

  @Test fun identicalPinVerifiesAndWrongPinDoesNot() {
    assertTrue(MessageDigest.isEqual(tag("123456"), tag("123456")))
    assertFalse(MessageDigest.isEqual(tag("123456"), tag("123457")))
  }

  @Test fun leadingZerosAndPinLengthAreSignificant() {
    assertFalse(MessageDigest.isEqual(tag("01234"), tag("1234")))
    assertFalse(MessageDigest.isEqual(tag("1234"), tag("12340")))
  }

  @Test fun verifierDependsOnDeviceKeyAndSalt() {
    val otherKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "HmacSHA256")
    assertFalse(MessageDigest.isEqual(tag("123456"), ScreenLockPinVerifier.calculate(otherKey, salt, "123456".toCharArray())))
    assertFalse(MessageDigest.isEqual(tag("123456"), ScreenLockPinVerifier.calculate(key, ByteArray(32), "123456".toCharArray())))
  }

  @Test fun fourAndSixteenDigitPinsAreAccepted() {
    assertEquals(32, tag("0123").size)
    assertEquals(32, tag("0123456789012345").size)
  }

  @Test(expected = IllegalArgumentException::class) fun tooShortPinIsRejected() { tag("123") }
  @Test(expected = IllegalArgumentException::class) fun tooLongPinIsRejected() { tag("12345678901234567") }
  @Test(expected = IllegalArgumentException::class) fun nonNumericPinIsRejected() { tag("123a") }
}
