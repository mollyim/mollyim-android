package org.thoughtcrime.securesms.osm

import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.TileSystem
import org.signal.core.util.logging.Log

private val TAG = Log.tag(MapTileWriterCacheProvider::class.java)

class MapTileWriterCacheProvider(
  tileSource: ITileSource,
  tileWriter: IFilesystemCache,
  threadPoolSize: Int = 2,
  pendingQueueSize: Int = 40,
) : MapTileModuleProviderBase(threadPoolSize, pendingQueueSize) {

  private var currentTileSource: ITileSource? = tileSource

  override fun getName() = "Tile Writer Cache Provider"

  override fun getThreadGroupName() = "filesystem"

  private val tileLoader = object : TileLoader() {
    override fun loadTile(mapTileIndex: Long): Drawable? {
      try {
        return tileWriter.loadTile(currentTileSource ?: return null, mapTileIndex)
      } catch (t: Throwable) {
        Log.w(TAG, "Error loading tile", t)
        return null
      }
    }
  }

  override fun getTileLoader() = tileLoader

  override fun getUsesDataConnection() = false

  override fun getMinimumZoomLevel(): Int =
    currentTileSource?.minimumZoomLevel ?: OpenStreetMapTileProviderConstants.MINIMUM_ZOOMLEVEL

  override fun getMaximumZoomLevel(): Int =
    currentTileSource?.maximumZoomLevel ?: TileSystem.getMaximumZoomLevel()

  override fun setTileSource(tileSource: ITileSource?) {
    currentTileSource = tileSource
  }
}
