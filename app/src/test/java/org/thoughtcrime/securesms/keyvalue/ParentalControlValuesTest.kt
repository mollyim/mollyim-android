package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import org.junit.Test

class ParentalControlValuesTest {

  private fun createValues(): ParentalControlValues {
    val dataset = KeyValueDataSet()
    val store = KeyValueStore(dataset)
    return ParentalControlValues(store)
  }

  @Test
  fun `fresh defaults - parental mode enabled`() {
    val values = createValues()
    assertThat(values.parentalModeEnabled).isEqualTo(true)
  }

  @Test
  fun `fresh defaults - pin hash empty`() {
    val values = createValues()
    assertThat(values.parentPinHash).isEqualTo("")
  }

  @Test
  fun `fresh defaults - allowed thread ids empty`() {
    val values = createValues()
    assertThat(values.getAllowedThreadIds()).isEmpty()
  }

  @Test
  fun `toggle parental mode enabled to disabled`() {
    val values = createValues()
    values.parentalModeEnabled = false
    assertThat(values.parentalModeEnabled).isEqualTo(false)
  }

  @Test
  fun `toggle parental mode disabled to enabled`() {
    val values = createValues()
    values.parentalModeEnabled = false
    values.parentalModeEnabled = true
    assertThat(values.parentalModeEnabled).isEqualTo(true)
  }

  @Test
  fun `pin hash round trip`() {
    val values = createValues()
    val hash = "abc123def456"
    values.parentPinHash = hash
    assertThat(values.parentPinHash).isEqualTo(hash)
  }

  @Test
  fun `compute pin hash determinism`() {
    val pin = "1234"
    val salt = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    val hash1 = ParentalControlValues.computePinHash(pin, salt)
    val hash2 = ParentalControlValues.computePinHash(pin, salt)
    assertThat(hash1).isEqualTo(hash2)
  }

  @Test
  fun `compute pin hash uniqueness with different salt`() {
    val pin = "1234"
    val salt1 = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    val salt2 = byteArrayOf(1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    val hash1 = ParentalControlValues.computePinHash(pin, salt1)
    val hash2 = ParentalControlValues.computePinHash(pin, salt2)
    assertThat(hash1).isNotEqualTo(hash2)
  }

  @Test
  fun `salt auto generated on first call`() {
    val values = createValues()
    val salt = values.getPinSalt()
    assertThat(salt).isNotEmpty()
    assertThat(salt.size).isEqualTo(16)
  }

  @Test
  fun `salt returns same value on second call`() {
    val values = createValues()
    val salt1 = values.getPinSalt()
    val salt2 = values.getPinSalt()
    assertThat(salt1).isEqualTo(salt2)
  }

  @Test
  fun `allowed thread ids round trip`() {
    val values = createValues()
    val ids = setOf(1L, 2L, 3L)
    values.setAllowedThreadIds(ids)
    assertThat(values.getAllowedThreadIds()).containsExactly(1L, 2L, 3L)
  }

  @Test
  fun `allowed thread ids empty set`() {
    val values = createValues()
    values.setAllowedThreadIds(emptySet())
    assertThat(values.getAllowedThreadIds()).isEmpty()
  }

  @Test
  fun `allowed thread ids clear after set`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    values.setAllowedThreadIds(emptySet())
    assertThat(values.getAllowedThreadIds()).isEmpty()
  }
}
