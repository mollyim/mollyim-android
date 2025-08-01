/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.logging

import org.signal.core.util.CryptoUtil
import org.signal.core.util.Hex
import org.signal.core.util.isNotNullOrBlank
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Given a [Matcher], update the [StringBuilder] with the scrubbed output you want for a given match. */
private typealias MatchProcessor = (Matcher, StringBuilder) -> Unit

/**
 * Scrub data for possibly sensitive information.
 */
object Scrubber {
  /**
   * The middle group will be censored.
   * Supposedly, the shortest international phone numbers in use contain seven digits.
   * Handles URL encoded +, %2B
   */
  private val E164_PATTERN = Pattern.compile("(KEEP_E164::)?(\\+|%2B)(\\d{7,15})")
  private val E164_ZERO_PATTERN = Pattern.compile("\\b(KEEP_E164::)?0(\\d{10})\\b")

  /** The second group will be censored.*/
  private val CRUDE_EMAIL_PATTERN = Pattern.compile("\\b([^\\s/,()])([^\\s/,()]*@[^\\s]+\\.[^\\s]+)")
  private const val EMAIL_CENSOR = "...@..."

  /** The middle group will be censored. */
  private val GROUP_ID_V1_PATTERN = Pattern.compile("(__textsecure_group__!)([^\\s]+)([^\\s]{3})")

  /** The middle group will be censored. */
  private val GROUP_ID_V2_PATTERN = Pattern.compile("(__signal_group__v2__!)([^\\s]+)([^\\s]{3})")

  /** The middle group will be censored. */
  private val UUID_PATTERN = Pattern.compile("(JOB::)?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{9})([0-9a-f]{3})", Pattern.CASE_INSENSITIVE)
  private const val UUID_CENSOR = "********-****-****-****-*********"

  private val PNI_PATTERN = Pattern.compile("PNI:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{9}[0-9a-f]{3})", Pattern.CASE_INSENSITIVE)

  /**
   * The entire string is censored. Note: left as concatenated strings because kotlin string literals leave trailing newlines, and removing them breaks
   * syntax highlighting.
   */
  private val IPV4_PATTERN = Pattern.compile(
    "\\b" +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
      "\\b"
  )
  private const val IPV4_CENSOR = "...ipv4..."

  /** The entire string is censored. */
  private val IPV6_PATTERN = Pattern.compile("([0-9a-fA-F]{0,4}:){3,7}([0-9a-fA-F]){0,4}")
  private const val IPV6_CENSOR = "...ipv6..."

  /** The domain name and path except for TLD will be censored. */
  private val URL_PATTERN = Pattern.compile("([a-z0-9]+\\.)+([a-z0-9\\-]*[a-z\\-][a-z0-9\\-]*)(/[/a-z0-9\\-_.~:@?&=#%+\\[\\]!$()*,;]*)?", Pattern.CASE_INSENSITIVE)
  private const val URL_CENSOR = "***"
  private val TOP_100_TLDS: Set<String> = setOf(
    "com", "net", "org", "jp", "de", "uk", "fr", "br", "it", "ru", "es", "me", "gov", "pl", "ca", "au", "cn", "co", "in",
    "nl", "edu", "info", "eu", "ch", "id", "at", "kr", "cz", "mx", "be", "tv", "se", "tr", "tw", "al", "ua", "ir", "vn",
    "cl", "sk", "ly", "cc", "to", "no", "fi", "us", "pt", "dk", "ar", "hu", "tk", "gr", "il", "news", "ro", "my", "biz",
    "ie", "za", "nz", "sg", "ee", "th", "io", "xyz", "pe", "bg", "hk", "lt", "link", "ph", "club", "si", "site",
    "mobi", "by", "cat", "wiki", "la", "ga", "xxx", "cf", "hr", "ng", "jobs", "online", "kz", "ug", "gq", "ae", "is",
    "lv", "pro", "fm", "tips", "ms", "sa", "app"
  )

  /** Base16 Call Link Key Pattern */
  private val CALL_LINK_PATTERN = Pattern.compile("([bBcCdDfFgGhHkKmMnNpPqQrRsStTxXzZ]{4})(-[bBcCdDfFgGhHkKmMnNpPqQrRsStTxXzZ]{4}){7}")
  private const val CALL_LINK_CENSOR_SUFFIX = "-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"

  private val CALL_LINK_ROOM_ID_PATTERN = Pattern.compile("([^/])([0-9a-f]{61})([0-9a-f]{3})")

  @JvmStatic
  @Volatile
  var identifierHmacKeyProvider: () -> ByteArray? = { null }

  @JvmStatic
  @Volatile
  private var identifierHmacKey: ByteArray? = null

  @JvmStatic
  fun scrub(input: CharSequence): CharSequence {
    return input
      .scrubE164()
      .scrubE164Zero()
      .scrubEmail()
      .scrubGroupsV1()
      .scrubGroupsV2()
      .scrubPnis()
      .scrubUuids()
      .scrubUrls()
      .scrubIpv4()
      .scrubIpv6()
      .scrubCallLinkKeys()
      .scrubCallLinkRoomIds()
  }

  private fun CharSequence.scrubE164(): CharSequence {
    return scrub(this, E164_PATTERN) { matcher, output ->
      if (matcher.group(1) != null && matcher.group(1)!!.isNotEmpty()) {
        output
          .append("KEEP_E164::")
          .append((matcher.group(2) + matcher.group(3)).censorMiddle(2, 2))
      } else {
        output
          .append("E164:")
          .append(hash(matcher.group(3)))
      }
    }
  }

  private fun CharSequence.scrubE164Zero(): CharSequence {
    return scrub(this, E164_ZERO_PATTERN) { matcher, output ->
      if (matcher.group(1) != null && matcher.group(1)!!.isNotEmpty()) {
        output
          .append("KEEP_E164::")
          .append(("0" + matcher.group(2)).censorMiddle(2, 2))
      } else {
        output
          .append("E164:")
          .append(hash(matcher.group(2)))
      }
    }
  }

  private fun CharSequence.scrubEmail(): CharSequence {
    return scrub(this, CRUDE_EMAIL_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append(EMAIL_CENSOR)
    }
  }

  private fun CharSequence.scrubGroupsV1(): CharSequence {
    return scrub(this, GROUP_ID_V1_PATTERN) { matcher, output ->
      output
        .append("GV1::***")
        .append(matcher.group(3))
    }
  }

  private fun CharSequence.scrubGroupsV2(): CharSequence {
    return scrub(this, GROUP_ID_V2_PATTERN) { matcher, output ->
      output
        .append("GV2::***")
        .append(matcher.group(3))
    }
  }

  private fun CharSequence.scrubPnis(): CharSequence {
    return scrub(this, PNI_PATTERN) { matcher, output ->
      output
        .append("PNI:")
        .append(hash(matcher.group(1)))
    }
  }

  private fun CharSequence.scrubUuids(): CharSequence {
    return scrub(this, UUID_PATTERN) { matcher, output ->
      if (matcher.group(1) != null && matcher.group(1)!!.isNotEmpty()) {
        output
          .append(matcher.group(1))
          .append(matcher.group(2))
          .append(matcher.group(3))
      } else {
        output
          .append(UUID_CENSOR)
          .append(matcher.group(3))
      }
    }
  }

  private fun CharSequence.scrubUrls(): CharSequence {
    return scrub(this, URL_PATTERN) { matcher, output ->
      val match: String = matcher.group(0)!!

      if (
        (matcher.groupCount() == 2 || matcher.groupCount() == 3) &&
        TOP_100_TLDS.contains(matcher.group(2)!!.lowercase()) &&
        !(matcher.group(1).endsWith("signal.") && matcher.group(2) == "org" && !match.contains("cdn")) &&
        !(matcher.group(1).endsWith("molly.") && (matcher.group(2) == "im" || matcher.group(2) == "app")) &&
        !(matcher.group(1).endsWith("debuglogs.") && matcher.group(2) == "org")
      ) {
        output
          .append(URL_CENSOR)
          .append(".")
          .append(matcher.group(2))
          .run {
            if (matcher.groupCount() == 3 && matcher.group(3).isNotNullOrBlank()) {
              append("/")
              append(URL_CENSOR)
            }
          }
      } else {
        output.append(match)
      }
    }
  }

  private fun CharSequence.scrubIpv4(): CharSequence {
    return scrub(this, IPV4_PATTERN) { _, output -> output.append(IPV4_CENSOR) }
  }

  private fun CharSequence.scrubIpv6(): CharSequence {
    return scrub(this, IPV6_PATTERN) { _, output -> output.append(IPV6_CENSOR) }
  }

  private fun CharSequence.scrubCallLinkKeys(): CharSequence {
    return scrub(this, CALL_LINK_PATTERN) { matcher, output ->
      val match = matcher.group(1)
      output
        .append(match)
        .append(CALL_LINK_CENSOR_SUFFIX)
    }
  }

  private fun CharSequence.scrubCallLinkRoomIds(): CharSequence {
    return scrub(this, CALL_LINK_ROOM_ID_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append("*************************************************************")
        .append(matcher.group(3))
    }
  }

  private fun String.censorMiddle(leading: Int, trailing: Int): String {
    val totalKept = leading + trailing
    if (this.length < totalKept) {
      return "*".repeat(this.length)
    }
    val middle = "*".repeat(this.length - totalKept)
    return this.take(leading) + middle + this.takeLast(trailing)
  }

  private fun scrub(input: CharSequence, pattern: Pattern, processMatch: MatchProcessor): CharSequence {
    val output = StringBuilder(input.length)
    val matcher: Matcher = pattern.matcher(input)
    var lastEndingPos = 0

    while (matcher.find()) {
      output.append(input, lastEndingPos, matcher.start())
      processMatch(matcher, output)
      lastEndingPos = matcher.end()
    }

    return if (lastEndingPos == 0) {
      // there were no matches, save copying all the data
      input
    } else {
      output.append(input, lastEndingPos, input.length)
      output
    }
  }

  private fun hash(value: String): String {
    if (identifierHmacKey == null) {
      identifierHmacKey = identifierHmacKeyProvider()
    }

    val key: ByteArray = identifierHmacKey ?: return "<redacted>"
    val hash = CryptoUtil.hmacSha256(key, value.toByteArray())
    return "<${Hex.toStringCondensed(hash).take(5)}>"
  }
}
