package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

/**
 * Verifies the parental-control guards introduced in Phase 3:
 *
 *  - New conversation / new call activities finish immediately when parental mode is on.
 *    The guard condition is `parentalModeEnabled == true`; tested here via the data model.
 *
 *  - Incoming calls from non-allowed threads are rejected.
 *    The guard uses [ParentalControlValues.isThreadCallAllowed]; covered here and in
 *    ParentalControlValuesTest.
 */
class ParentalCallGuardTest {

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

  // --- Activity guard condition (NewConversationActivity / NewCallActivity) ---

  @Test
  fun `activity guard - parental mode ON means new conversation should be blocked`() {
    val values = createValues()
    // Default is true; guard is `if (parentalModeEnabled) finish()`
    assertThat(values.parentalModeEnabled).isEqualTo(true)
  }

  @Test
  fun `activity guard - parental mode OFF means new conversation is allowed`() {
    val values = createValues()
    values.parentalModeEnabled = false
    assertThat(values.parentalModeEnabled).isEqualTo(false)
  }

  // --- Incoming call guard (1:1 and group): isThreadCallAllowed ---

  @Test
  fun `call guard - parental mode ON, allowed thread - call permitted`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(10L))
    assertThat(values.isThreadCallAllowed(10L)).isEqualTo(true)
  }

  @Test
  fun `call guard - parental mode ON, non-allowed thread - call blocked`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(10L))
    assertThat(values.isThreadCallAllowed(99L)).isEqualTo(false)
  }

  @Test
  fun `call guard - parental mode ON, no thread exists (id -1) - call blocked`() {
    val values = createValues()
    values.setAllowedThreadIds(setOf(10L))
    assertThat(values.isThreadCallAllowed(-1L)).isEqualTo(false)
  }

  @Test
  fun `call guard - parental mode OFF - any thread allowed regardless of allowlist`() {
    val values = createValues()
    values.parentalModeEnabled = false
    values.setAllowedThreadIds(setOf(10L))
    assertThat(values.isThreadCallAllowed(99L)).isEqualTo(true)
  }

  @Test
  fun `call guard - parental mode ON, empty allowlist - all calls blocked`() {
    val values = createValues()
    values.setAllowedThreadIds(emptySet())
    assertThat(values.isThreadCallAllowed(10L)).isEqualTo(false)
  }
}
