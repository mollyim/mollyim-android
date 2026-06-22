/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants;

public final class VideoUtil {

  private VideoUtil() { }

  public static int getMaxVideoRecordDurationInSeconds(@NonNull Context context, @NonNull MediaConstraints mediaConstraints) {
    long allowedSize = mediaConstraints.getCompressedVideoMaxSize(context);
    int duration     = (int) Math.floor((float) allowedSize / VideoConstants.MAX_ALLOWED_BYTES_PER_SECOND);

    return Math.min(duration, VideoConstants.VIDEO_MAX_RECORD_LENGTH_S);
  }
}
