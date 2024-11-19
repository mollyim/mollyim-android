/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import im.molly.unifiedpush.MollySocketRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.signal.qr.QrProcessor
import kotlin.jvm.optionals.getOrNull


/**
 * A collection of functions to help with scanning QR codes for MollySocket.
 */
object MollySocketQrScanRepository {
  private const val TAG = "MollySocketQrScanRepository"

  /**
   * Given a URL, will attempt to lookup MollySocket informations, coercing it to a standard set of [QrScanResult]s.
   */
  fun lookupUrl(url: String): Single<QrScanResult> {
    val data = MollySocketLinkData.parse(url).getOrNull()
    if(data == null || (data.type == "webserver" && data.url == null)) {
      return Single.just(QrScanResult.InvalidData)
    }
    if (data.type == "airgapped") {
      return Single.just(QrScanResult.Success(data))
    }
    return checkMollySocketServer(data.url ?: "").map { found ->
      if (found) {
        QrScanResult.Success(data)
      } else {
        // TODO add network check
        QrScanResult.NotFound(
          url = data.url ?: ""
        )
      }
    }.subscribeOn(Schedulers.io())
  }

  private fun checkMollySocketServer(url: String): Single<Boolean> {
    return Single
      .fromCallable {
        runCatching {
          MollySocketRepository.discoverMollySocketServer(url.toHttpUrl())
        }.getOrElse { e ->
          Log.e(TAG, "Cannot discover MollySocket", e)
          false
        }
      }.subscribeOn(Schedulers.io())
  }

  /**
   * Given a URI pointing to an image that may contain a username QR code, this will attempt to lookup the username, coercing it to a standard set of [QrScanResult]s.
   */
  fun scanImageUriForQrCode(context: Context, uri: Uri): Single<QrScanResult> {
    val loadBitmap = Glide.with(context)
      .asBitmap()
      .format(DecodeFormat.PREFER_ARGB_8888)
      .load(uri)
      .submit()

    return Single.fromFuture(loadBitmap)
      .map { QrProcessor().getScannedData(it).toOptional() }
      .flatMap {
        if (it.isPresent) {
          lookupUrl(it.get())
        } else {
          Single.just(QrScanResult.QrNotFound)
        }
      }
      .subscribeOn(Schedulers.io())
  }
}
