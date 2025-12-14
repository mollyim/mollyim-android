package org.thoughtcrime.securesms.osm

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileAreaBorderComputer
import org.osmdroid.util.MapTileAreaZoomComputer

class MapTileProvider(
  context: Context,
  tileSource: ITileSource,
) : MapTileProviderArray(tileSource, null) {

  private val cacheWriter = SingleSessionDiskTileWriter(context)

  init {
    val networkAvailabilityCheck = NetworkAvailabliltyCheck(context)

    val cacheProvider = MapTileWriterCacheProvider(tileSource, cacheWriter)

    val downloaderProvider = MapTileDownloader(tileSource, cacheWriter, networkAvailabilityCheck)

    val approximationProvider = MapTileApproximater().also {
      it.addProvider(cacheProvider)
    }

    mTileProviderList.add(cacheProvider)
    mTileProviderList.add(approximationProvider)
    mTileProviderList.add(downloaderProvider)

    tileCache.protectedTileComputers.add(MapTileAreaZoomComputer(-1))
    tileCache.protectedTileComputers.add(MapTileAreaBorderComputer(1))
    // Upstream sets setAutoEnsureCapacity to false due to an issue not related to our context
    tileCache.setAutoEnsureCapacity(true)
    tileCache.setStressedMemory(false)

    tileCache.preCache.addProvider(cacheProvider)
    tileCache.preCache.addProvider(downloaderProvider)

    tileCache.protectedTileContainers.add(this)
  }

  override fun getTileWriter(): IFilesystemCache {
    return cacheWriter
  }
}
