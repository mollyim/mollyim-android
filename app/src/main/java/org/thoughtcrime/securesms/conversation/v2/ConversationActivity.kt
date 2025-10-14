package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.enableSavedStateHandles
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.jobs.ConversationShortcutUpdateJob
import org.thoughtcrime.securesms.util.ConfigurationUtil
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.window.WindowSizeClass
import java.util.concurrent.TimeUnit

/**
 * Wrapper activity for ConversationFragment.
 */
open class ConversationActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  companion object {
    private val TAG = tag(ConversationActivity::class.java)

    private const val STATE_WATERMARK = "share_data_watermark"
  }

  private val theme = DynamicNoActionBarTheme()
  private val transitionDebouncer: Debouncer = Debouncer(150, TimeUnit.MILLISECONDS)

  override val voiceNoteMediaController = VoiceNoteMediaController(this, true)

  private val motionEventRelay: MotionEventRelay by viewModels()
  private val shareDataTimestampViewModel: ShareDataTimestampViewModel by viewModels()

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (!ActivityCompat.isLaunchedFromBubble(this) && WindowSizeClass.isLargeScreenSupportEnabled()) {
      startActivity(
        MainActivity.clearTop(this).apply {
          action = ConversationIntents.ACTION
          putExtras(intent)
        }
      )

      if (!ConversationIntents.isConversationIntent(intent)) {
        ConversationShortcutUpdateJob.enqueue()
      }

      finish()
      return
    }

    enableSavedStateHandles()
    supportPostponeEnterTransition()
    transitionDebouncer.publish { supportStartPostponedEnterTransition() }
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

    shareDataTimestampViewModel.setTimestampFromActivityCreation(savedInstanceState, intent)
    setContentView(R.layout.fragment_container)

    if (savedInstanceState == null) {
      replaceFragment()
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  override fun onStop() {
    super.onStop()
    if (isChangingConfigurations) {
      Log.i(TAG, "Conversation recreating due to configuration change")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    transitionDebouncer.clear()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    // Note: We utilize this instead of 'replaceFragment' because there seems to be a bug
    // in constraint-layout which mixes up insets when replacing the fragment via onNewIntent.
    finish()
    startActivity(intent)
  }

  override fun onConfigurationChanged(newConfiguration: Configuration) {
    super.onConfigurationChanged(newConfiguration)
    if (ConfigurationUtil.isUiModeChanged(resources.configuration, newConfiguration)) {
      recreate()
    }
  }

  private fun replaceFragment() {
    val fragment = ConversationFragment().apply {
      arguments = if (ConversationIntents.isBubbleIntentUri(intent.data)) {
        ConversationIntents.createParentFragmentArguments(intent)
      } else {
        intent.extras
      }
    }

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.fragment_container, fragment)
      .disallowAddToBackStack()
      .commitNowAllowingStateLoss()
  }

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    return motionEventRelay.offer(ev) || super.dispatchTouchEvent(ev)
  }
}
