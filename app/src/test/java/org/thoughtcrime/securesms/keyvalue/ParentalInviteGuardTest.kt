package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Tests for Phase 4 invite-gating helpers: verifyPin and addAllowedThreadId.
 * In the keyvalue package to access package-private test helpers (per lessons from Phase 3).
 */
class ParentalInviteGuardTest {

  private fun createValues(): ParentalControlValues {
    val storage = object : KeyValuePersistentStorage {
      private val dataSet = KeyValueDataSet()
      override fun writeDataSet(newDataSet: KeyValueDataSet, removes: Collection<String>) {
        dataSet.removeAll(removes)
        dataSet.putAll(newDataSet)
      }
      override fun getDataSet(): KeyValueDataSet = dataSet
    }
    return ParentalControlValues(KeyValueStore(storage))
  }

  @Test
  fun `verifyPin correct pin`() {
    val values = createValues()
    val pin = "4321"
    values.parentPinHash = ParentalControlValues.computePinHash(pin, values.getPinSalt())
    assertThat(values.verifyPin(pin)).isTrue()
  }

  @Test
  fun `verifyPin wrong pin`() {
    val values = createValues()
    values.parentPinHash = ParentalControlValues.computePinHash("1111", values.getPinSalt())
    assertThat(values.verifyPin("9999")).isFalse()
  }

  @Test
  fun `verifyPin empty hash is always false`() {
    val values = createValues()
    assertThat(values.verifyPin("")).isFalse()
    assertThat(values.verifyPin("1234")).isFalse()
  }

  @Test
  fun `addAllowedThreadId appends correctly`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(10L, 20L))
    values.addAllowedThreadId(30L)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(10L, 20L, 30L))
  }

  @Test
  fun `allowedThreadIds includes newly added invite thread`() {
    val values = createValues()
    val inviteThreadId = 99L
    values.addAllowedThreadId(inviteThreadId)
    assertThat(values.getAllowedThreadIds().contains(inviteThreadId)).isTrue()
  }

  @Test
  fun `addAllowedThreadId is idempotent`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(5L))
    values.addAllowedThreadId(5L)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(5L))
  }

  @Test
  fun `verifyPin uses same salt for consistency`() {
    val values = createValues()
    val pin = "5678"
    val salt = values.getPinSalt()
    values.parentPinHash = ParentalControlValues.computePinHash(pin, salt)
    assertThat(values.verifyPin(pin)).isTrue()
    assertThat(values.verifyPin(pin)).isTrue()
  }
}
