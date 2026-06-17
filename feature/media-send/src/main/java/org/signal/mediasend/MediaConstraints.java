/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ContentTypeUtil;
import org.signal.core.util.logging.Log;
import org.signal.core.util.bitmaps.BitmapDecodingException;
import org.signal.core.util.bitmaps.BitmapUtil;
import org.thoughtcrime.securesms.video.TranscodingPreset;

import java.io.IOException;
import java.io.InputStream;

import kotlin.Pair;

public abstract class MediaConstraints {
  private static final String TAG = Log.tag(MediaConstraints.class);

  public abstract int getImageMaxWidth(Context context);
  public abstract int getImageMaxHeight(Context context);
  public abstract int getImageMaxSize(Context context);

  public TranscodingPreset getVideoTranscodingSettings() {
    return TranscodingPreset.LEVEL_1;
  }

  /**
   * Provide a list of dimensions that should be attempted during compression. We will keep moving
   * down the list until the image can be scaled to fit under {@link #getImageMaxSize(Context)}.
   * The first entry in the list should match your max width/height.
   */
  public abstract int[] getImageDimensionTargets(Context context);

  public abstract long getGifMaxSize(Context context);
  public abstract long getVideoMaxSize();

  public @IntRange(from = 0, to = 100) int getImageCompressionQualitySetting(@NonNull Context context) {
    return 70;
  }

  public long getUncompressedVideoMaxSize(Context context) {
    return getVideoMaxSize();
  }

  public long getCompressedVideoMaxSize(Context context) {
    return getVideoMaxSize();
  }

  public abstract long getAudioMaxSize(Context context);
  public abstract long getDocumentMaxSize(Context context);

  public abstract long getMaxAttachmentSize();

  public boolean isSatisfied(@NonNull Context context, @NonNull Uri uri, @NonNull String contentType, long size) {
    try {
      if (size > getMaxAttachmentSize()) {
        return false;
      }
      return (ContentTypeUtil.isGif(contentType)       && size <= getGifMaxSize(context) && isWithinBounds(context, uri))   ||
             (ContentTypeUtil.isImageType(contentType) && size <= getImageMaxSize(context) && isWithinBounds(context, uri)) ||
             (ContentTypeUtil.isAudioType(contentType) && size <= getAudioMaxSize(context))                                 ||
             (ContentTypeUtil.isVideoType(contentType) && size <= getVideoMaxSize())                                        ||
             (ContentTypeUtil.isDocumentType(contentType) && size <= getDocumentMaxSize(context));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  private boolean isWithinBounds(Context context, Uri uri) throws IOException {
    try {
      InputStream is = MediaSendDependencies.INSTANCE.getMediaSendRepository().getAttachmentStream(context, uri);
      Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
return dimensions.getFirst()  > 0 && dimensions.getFirst()  <= getImageMaxWidth(context) &&
              dimensions.getSecond() > 0 && dimensions.getSecond() <= getImageMaxHeight(context);
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public boolean canResize(@Nullable String mediaType) {
    return ContentTypeUtil.isImageType(mediaType) && !ContentTypeUtil.isGif(mediaType) ||
           ContentTypeUtil.isVideoType(mediaType) && isVideoTranscodeAvailable();
  }

  @ChecksSdkIntAtLeast(api = 26)
  public static boolean isVideoTranscodeAvailable() {
    return Build.VERSION.SDK_INT >= 26;
  }
}
