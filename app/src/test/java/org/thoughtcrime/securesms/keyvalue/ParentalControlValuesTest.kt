package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ParentalControlValuesTest {

  private fun createValues(): ParentalControlValues {
    val storage = object : KeyValuePersistentStorage {
      private val dataSet = KeyValueDataSet()
      override fun writeDataSet(newDataSet: KeyValueDataSet, removes: kotlin.collections.Collection<String>) {
        dataSet.removeAll(removes)
        dataSet.putAll(newDataSet)
      }
      override fun getDataSet(): KeyValueDataSet = dataSet
    }
    return ParentalControlValues(KeyValueStore(storage))
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
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(1L, 2L, 3L))
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

  @Test
  fun `settingsChanges emits when parental mode toggled`() {
    val values = createValues()
    val emissions = CopyOnWriteArrayList<Unit>()
    values.settingsChanges.subscribe { emissions.add(Unit) }
    values.parentalModeEnabled = false
    values.parentalModeEnabled = true
    assertThat(emissions.size).isEqualTo(2)
  }

  @Test
  fun `settingsChanges emits when allowed thread ids updated`() {
    val values = createValues()
    val emissions = CopyOnWriteArrayList<Unit>()
    values.settingsChanges.subscribe { emissions.add(Unit) }
    values.setAllowedThreadIds(setOf(1L, 2L))
    assertThat(emissions.size).isEqualTo(1)
  }

  @Test
  fun `isThreadCallAllowed - parental mode OFF - any thread allowed`() {
    val values = createValues()
    values.parentalModeEnabled = false
    assertThat(values.isThreadCallAllowed(99L)).isEqualTo(true)
  }

  @Test
  fun `isThreadCallAllowed - parental mode ON - allowed thread permitted`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    assertThat(values.isThreadCallAllowed(1L)).isEqualTo(true)
  }

  @Test
  fun `isThreadCallAllowed - parental mode ON - non-allowed thread blocked`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    assertThat(values.isThreadCallAllowed(99L)).isEqualTo(false)
  }

  @Test
  fun `isThreadCallAllowed - parental mode ON - unknown thread (id -1) blocked`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    assertThat(values.isThreadCallAllowed(-1L)).isEqualTo(false)
  }

  @Test
  fun `isThreadCallAllowed - parental mode ON - empty allowed set blocks all`() {
    val values = createValues()
    values.setAllowedThreadIds(emptySet())
    assertThat(values.isThreadCallAllowed(1L)).isEqualTo(false)
  }

  @Test
  fun `verifyPin - correct pin returns true`() {
    val values = createValues()
    val pin = "7890"
    values.parentPinHash = ParentalControlValues.computePinHash(pin, values.getPinSalt())
    assertThat(values.verifyPin(pin)).isEqualTo(true)
  }

  @Test
  fun `verifyPin - wrong pin returns false`() {
    val values = createValues()
    values.parentPinHash = ParentalControlValues.computePinHash("1234", values.getPinSalt())
    assertThat(values.verifyPin("9999")).isEqualTo(false)
  }

  @Test
  fun `verifyPin - empty hash returns false`() {
    val values = createValues()
    assertThat(values.parentPinHash).isEqualTo("")
    assertThat(values.verifyPin("1234")).isEqualTo(false)
  }

  @Test
  fun `addAllowedThreadId - appends without replacing existing`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    values.addAllowedThreadId(3L)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(1L, 2L, 3L))
  }

  @Test
  fun `addAllowedThreadId - idempotent when id already present`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(1L, 2L))
    values.addAllowedThreadId(2L)
    assertThat(values.getAllowedThreadIds()).isEqualTo(setOf(1L, 2L))
  }
}
