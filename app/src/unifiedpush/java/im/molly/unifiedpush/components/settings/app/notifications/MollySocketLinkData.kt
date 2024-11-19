/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import java.net.URI
import java.util.Optional

data class MollySocketLinkData(
  val vapid: String,
  val type: String,
  val url: String?
) {
  companion object {
    fun parse(url: String): Optional<MollySocketLinkData> {
      val uri = URI(url)
      val params: MutableMap<String, String> = emptyMap<String, String>().toMutableMap()
      uri.rawQuery.split('&').forEach { rawParam ->
        rawParam.split('=').let {
          params[it.getOrElse(0) { "" }] = URI("#${it.getOrElse(1) { "" }}").fragment
        }
      }
      val vapid = params["vapid"]
      val type = params["type"]
      val parsedUrl = params["url"]
      if (
        uri.scheme == "mollysocket"
        && uri.authority == "link"
        && vapid?.length == 87
        && (
          type == "airgapped"
            || (type == "webserver" && URI(parsedUrl).scheme == "https")
          )
      ) {
        return Optional.of(
          MollySocketLinkData(
            vapid = vapid,
            type = type,
            url = parsedUrl
          )
        )
      } else {
        return Optional.empty()
      }
    }
  }
}