package im.molly.unifiedpush.model

import android.net.Uri

sealed class MollySocket(val vapid: String) {

  class AirGapped(vapid: String) : MollySocket(vapid)
  class WebServer(vapid: String, val url: String) : MollySocket(vapid)

  companion object {
    fun parseLink(uri: Uri): MollySocket? {
      if (!uri.isHierarchical || uri.scheme != "mollysocket" || uri.authority != "link") {
        return null
      }
      val vapid = uri.getQueryParameter("vapid")
      if (vapid?.length != 87) {
        return null
      }
      val type = uri.getQueryParameter("type")
      return when (type) {
        "airgapped" -> {
          AirGapped(vapid)
        }

        "webserver" -> {
          val url = uri.getQueryParameter("url") ?: return null
          val parsedUrl = Uri.parse(url)
          if (parsedUrl.scheme != "https") return null
          WebServer(vapid = vapid, url = url)
        }

        else -> null
      }
    }
  }
}
