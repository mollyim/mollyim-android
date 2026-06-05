package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BidiUtilTest {

  private val replacement = "�"

  @Test
  fun replaceBidiCharacters_nullInput_returnsNull() {
    assertNull(BidiUtil.replaceBidiCharacters(null))
  }

  @Test
  fun replaceBidiCharacters_plainText_isUnchanged() {
    assertEquals("document.txt", BidiUtil.replaceBidiCharacters("document.txt"))
  }

  @Test
  fun replaceBidiCharacters_overrides_areReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‪")) // LRE
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‫")) // RLE
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‬")) // PDF
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‭")) // LRO
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‮")) // RLO
  }

  @Test
  fun replaceBidiCharacters_isolates_areReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("⁦")) // LRI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("⁧")) // RLI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("⁨")) // FSI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("⁩")) // PDI
  }

  @Test
  fun replaceBidiCharacters_directionalIndicator_isReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‏")) // RLM
  }

  @Test
  fun replaceBidiCharacters_spoofedExtension_isNeutralized() {
    // "document<RLO>txt.exe<PDI>" would render as "documentexe.txt" without sanitization.
    val malicious = "document‮txt.exe⁩"
    val cleaned = BidiUtil.replaceBidiCharacters(malicious)

    assertEquals("document${replacement}txt.exe$replacement", cleaned)
    assertEquals(0, cleaned!!.count { it == '‮' || it == '⁩' })
  }

  @Test
  fun replaceBidiCharacters_multipleControls_allReplaced() {
    val input = "a‫b⁦c‮d"
    assertEquals("a${replacement}b${replacement}c${replacement}d", BidiUtil.replaceBidiCharacters(input))
  }
}
