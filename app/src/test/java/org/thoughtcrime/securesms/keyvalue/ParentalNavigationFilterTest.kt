package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.junit.Test
import org.thoughtcrime.securesms.main.MainNavigationListLocation
import org.thoughtcrime.securesms.main.buildNavEntries

class ParentalNavigationFilterTest {

  @Test
  fun `parental ON and stories feature ON - STORIES absent, CHATS and CALLS present`() {
    val entries = buildNavEntries(isStoriesEnabled = true, parentalModeEnabled = true)
    assertThat(entries).doesNotContain(MainNavigationListLocation.STORIES)
    assertThat(entries).contains(MainNavigationListLocation.CHATS)
    assertThat(entries).contains(MainNavigationListLocation.CALLS)
  }

  @Test
  fun `parental OFF and stories feature ON - STORIES present, CHATS and CALLS present`() {
    val entries = buildNavEntries(isStoriesEnabled = true, parentalModeEnabled = false)
    assertThat(entries).contains(MainNavigationListLocation.STORIES)
    assertThat(entries).contains(MainNavigationListLocation.CHATS)
    assertThat(entries).contains(MainNavigationListLocation.CALLS)
  }

  @Test
  fun `parental ON and stories feature OFF - STORIES absent`() {
    val entries = buildNavEntries(isStoriesEnabled = false, parentalModeEnabled = true)
    assertThat(entries).doesNotContain(MainNavigationListLocation.STORIES)
  }

  @Test
  fun `parental OFF and stories feature OFF - STORIES absent due to feature flag`() {
    val entries = buildNavEntries(isStoriesEnabled = false, parentalModeEnabled = false)
    assertThat(entries).doesNotContain(MainNavigationListLocation.STORIES)
  }
}
