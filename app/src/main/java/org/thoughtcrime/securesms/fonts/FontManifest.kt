package org.thoughtcrime.securesms.fonts

import android.content.Context
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.EncryptedStreamUtils
import java.io.File

/**
 * Description of available scripts and fonts for different locales.
 *
 * @param scripts A collection of supported scripts
 */
class FontManifest {
  @JsonProperty
  lateinit var scripts: FontScripts

  /**
   * A collection of supported scripts
   *
   * @param latinExtended LATN Script fonts
   * @param cyrillicExtended CYRL Script fonts
   * @param devanagari DEVA Script fonts
   * @param chineseTraditionalHk Hans / HK Script Fonts
   * @param chineseTraditional Hant Script Fonts
   * @param chineseSimplified Hans Script Fonts
   */
  class FontScripts {
    @JsonProperty("latin-extended")
    lateinit var latinExtended: FontScript

    @JsonProperty("cyrillic-extended")
    lateinit var cyrillicExtended: FontScript

    @JsonProperty
    lateinit var devanagari: FontScript

    @JsonProperty("chinese-traditional-hk")
    lateinit var chineseTraditionalHk: FontScript

    @JsonProperty("chinese-traditional")
    lateinit var chineseTraditional: FontScript

    @JsonProperty("chinese-simplified")
    lateinit var chineseSimplified: FontScript

    @JsonProperty
    lateinit var arabic: FontScript

    @JsonProperty
    lateinit var japanese: FontScript
  }

  /**
   * A collection of fonts for a specific script
   */
  class FontScript {
    @JsonProperty
    lateinit var regular: String

    @JsonProperty
    lateinit var bold: String

    @JsonProperty
    lateinit var serif: String

    @JsonProperty
    lateinit var script: String

    @JsonProperty
    lateinit var condensed: String
  }

  companion object {

    private val TAG = Log.tag(FontManifest::class.java)
    private const val PATH = ".manifest"

    private val objectMapper = ObjectMapper()

    /**
     * Gets the latest manifest object for the given version. This may hit the network, disk, or both, depending on whether we have
     * a cached manifest available for the given version.
     */
    @WorkerThread
    fun get(context: Context, fontVersion: FontVersion): FontManifest? {
      return fromDisk(context, fontVersion) ?: fromNetwork(context, fontVersion)
    }

    @WorkerThread
    private fun fromDisk(context: Context, fontVersion: FontVersion): FontManifest? {
      if (fontVersion.path.isEmpty()) {
        throw AssertionError()
      }

      return try {
        EncryptedStreamUtils.getInputStream(context, File(Fonts.getDirectory(context), fontVersion.manifestPath())).use {
          objectMapper.readValue(it, FontManifest::class.java)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to load manifest from disk")
        return null
      }
    }

    @WorkerThread
    private fun fromNetwork(context: Context, fontVersion: FontVersion): FontManifest? {
      return if (Fonts.downloadAndVerifyLatestManifest(context, fontVersion, fontVersion.manifestPath())) {
        fromDisk(context, fontVersion)
      } else {
        null
      }
    }

    private fun FontVersion.manifestPath(): String {
      return "$path/$PATH"
    }
  }
}
