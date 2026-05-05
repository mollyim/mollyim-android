package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Unit tests for the business logic exercised by ParentalControlViewModel.
 * Tests operate directly on ParentalControlValues (the source of truth) since
 * the ViewModel is a thin delegation layer on top of it.
 */
class ParentalControlViewModelTest {

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
  fun `freshInstall noPinSet parentPinHashIsEmpty`() {
    val values = createValues()
    assertThat(values.parentPinHash).isEqualTo("")
  }

  @Test
  fun `setParentalEnabled true stores true`() {
    val values = createValues()
    values.parentalModeEnabled = false
    values.parentalModeEnabled = true
    assertThat(values.parentalModeEnabled).isTrue()
  }

  @Test
  fun `setParentalEnabled false stores false`() {
    val values = createValues()
    values.parentalModeEnabled = false
    assertThat(values.parentalModeEnabled).isFalse()
  }

  @Test
  fun `toggleThread add updatesAllowedSet`() {
    val values = createValues()
    val threadId = 42L
    val current = values.getAllowedThreadIds().toMutableSet()
    current.add(threadId)
    values.setAllowedThreadIds(current)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(threadId))
  }

  @Test
  fun `toggleThread remove updatesAllowedSet`() {
    val values = createValues()
    val threadId = 42L
    values.setAllowedThreadIds(setOf(threadId, 99L))
    val current = values.getAllowedThreadIds().toMutableSet()
    current.remove(threadId)
    values.setAllowedThreadIds(current)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(99L))
  }

  @Test
  fun `changePin updatesStoredHash and verifyPin succeeds`() {
    val values = createValues()
    val newPin = "1234"
    val salt = values.getPinSalt()
    values.parentPinHash = ParentalControlValues.computePinHash(newPin, salt)
    assertThat(values.verifyPin(newPin)).isTrue()
  }
}
