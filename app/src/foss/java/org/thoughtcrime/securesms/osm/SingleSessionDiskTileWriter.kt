package org.thoughtcrime.securesms.osm

import android.content.Context
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.EncryptedStreamUtils
import org.thoughtcrime.securesms.util.StorageUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID

private val TAG = Log.tag(SingleSessionDiskTileWriter::class.java)

private val RandomSessionId = SecureRandom().nextInt()

class SingleSessionDiskTileWriter(private val context: Context) : IFilesystemCache {

  private val tileCacheDir = StorageUtil.getTileCacheDirectory(context)

  init {
    tileCacheDir.mkdir()
  }

  private fun buildTileFile(tileSource: ITileSource, mapTileIndex: Long): File {
    val uniqueName = tileSource.name() + mapTileIndex.toString(36) + RandomSessionId.toString(36)
    val uuid = UUID.nameUUIDFromBytes(uniqueName.toByteArray())
    return File(tileCacheDir, uuid.toString())
  }

  override fun saveFile(tileSource: ITileSource, mapTileIndex: Long, inputStream: InputStream, expirationTime: Long): Boolean {
    val tileFile = buildTileFile(tileSource, mapTileIndex)
    tileFile.deleteOnExit()
    try {
      inputStream.use { input ->
        val data = input.readBytes()
        val paddedInput = PaddingInputStream(ByteArrayInputStream(data), data.size.toLong())
        EncryptedStreamUtils.getOutputStream(context, tileFile).use { output ->
          paddedInput.copyTo(output)
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to save tile ${tileSource.name()}/$mapTileIndex to cache")
      return false
    }
    return true
  }

  override fun exists(tileSource: ITileSource, mapTileIndex: Long): Boolean =
    buildTileFile(tileSource, mapTileIndex).exists()

  override fun onDetach() = Unit

  override fun remove(tileSource: ITileSource, mapTileIndex: Long): Boolean =
    buildTileFile(tileSource, mapTileIndex).delete()

  override fun getExpirationTimestamp(pTileSource: ITileSource, mapTileIndex: Long): Long? = null

  override fun loadTile(tileSource: ITileSource, mapTileIndex: Long): Drawable? {
    val tileFile = buildTileFile(tileSource, mapTileIndex)
    if (!tileFile.exists()) {
      return null
    }
    EncryptedStreamUtils.getInputStream(context, tileFile).use { input ->
      return tileSource.getDrawable(input)
    }
  }
}
