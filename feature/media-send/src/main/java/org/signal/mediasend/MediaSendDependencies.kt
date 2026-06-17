/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import org.signal.mediasend.preupload.PreUploadRepository
import org.signal.video.exo.ExoPlayerPool

/**
 * MediaSend Feature Module dependencies
 */
object MediaSendDependencies {
  private lateinit var _application: Application
  private lateinit var _provider: Provider

  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  val application
    get() = _application

  val preUploadRepository: PreUploadRepository
    get() = _provider.providePreUploadRepository()

  val mediaSendRepository: MediaSendRepository
    get() = _provider.provideMediaSendRepository()

  val exoPlayerPool: ExoPlayerPool<ExoPlayer>
    get() = _provider.provideExoPlayerPool()

  interface Provider {
    fun provideMediaSendRepository(): MediaSendRepository
    fun providePreUploadRepository(): PreUploadRepository
    fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer>
  }
}
