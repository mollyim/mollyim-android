/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

sealed interface MediaCaptureScreenEvent {
  data object ShowCamera : MediaCaptureScreenEvent
  data object ShowTextStory : MediaCaptureScreenEvent
}
