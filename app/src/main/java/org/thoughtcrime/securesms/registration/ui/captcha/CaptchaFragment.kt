/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.captcha

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.logging.LoggingFragment
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationCaptchaBinding
import org.thoughtcrime.securesms.registration.fragments.RegistrationConstants

abstract class CaptchaFragment : LoggingFragment(R.layout.fragment_registration_captcha) {

  private val binding: FragmentRegistrationCaptchaBinding by ViewBinderDelegate(FragmentRegistrationCaptchaBinding::bind)

  @SuppressLint("SetJavaScriptEnabled")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Issue #303: users that have disabled / removed the system WebView
    // (low-storage devices, hardened ROMs) cannot complete the captcha
    // because the WebView either fails to inflate or refuses to load.
    // Detect that case and fall back to opening the captcha URL in the
    // user's default browser — the `signalcaptcha://` URI scheme handler
    // brings them back into the app with the token via the existing
    // intent filter.
    val webView = try {
      binding.registrationCaptchaWebView
    } catch (e: Throwable) {
      openCaptchaInBrowser()
      return
    }

    try {
      webView.settings.javaScriptEnabled = true
      webView.clearCache(true)

      webView.webViewClient = object : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
          if (url.startsWith(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME)) {
            val token = url.substring(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME.length)
            handleCaptchaToken(token)
            findNavController().navigateUp()
            return true
          }
          return false
        }
      }
      webView.loadUrl(BuildConfig.SIGNAL_CAPTCHA_URL)
    } catch (e: Throwable) {
      // Catches AndroidRuntimeException("WebView ... not available") thrown
      // by the WebView constructor / settings when the WebView package is
      // disabled at runtime.
      openCaptchaInBrowser()
    }
  }

  private fun openCaptchaInBrowser() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SIGNAL_CAPTCHA_URL))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      // No browser available; let the user back out.
    }
    findNavController().navigateUp()
  }

  abstract fun handleCaptchaToken(token: String)
}
