package org.thoughtcrime.securesms.sharing.v2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.pm.ShortcutManagerCompat
import com.google.android.material.appbar.MaterialToolbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.Result
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.SignalProgressDialog
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFullScreenDialogFragment
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity.Companion.share
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareDialogs
import org.thoughtcrime.securesms.sharing.MultiShareSender
import org.thoughtcrime.securesms.sharing.MultiShareSender.MultiShareSendResultCollection
import org.thoughtcrime.securesms.sharing.interstitial.ShareInterstitialActivity
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.TimeUnit

class ShareActivity : PassphraseRequiredActivity(), MultiselectForwardFragment.Callback {

  companion object {
    private val TAG = Log.tag(ShareActivity::class.java)

    private const val EXTRA_TITLE = "ShareActivity.extra.title"
    private const val EXTRA_NAVIGATION = "ShareActivity.extra.navigation"

    fun sendSimpleText(context: Context, text: String): Intent {
      return Intent(context, ShareActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(EXTRA_TITLE, R.string.MediaReviewFragment__send_to)
        .putExtra(EXTRA_NAVIGATION, true)
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var finishOnOkResultLauncher: ActivityResultLauncher<Intent>
  private lateinit var unresolvedShareData: UnresolvedShareData

  private val viewModel: ShareViewModel by viewModels {
    ShareViewModel.Factory(unresolvedShareData, ShareRepository(this))
  }

  private val directShareTarget: RecipientId?
    get() = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID).let { ConversationUtil.getRecipientId(it) }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.share_activity_v2)

    val isIntentValid = getUnresolvedShareData().either(
      onSuccess = {
        unresolvedShareData = it
        true
      },
      onFailure = {
        handleIntentError(it)
        false
      }
    )

    if (!isIntentValid) {
      finish()
      return
    }

    finishOnOkResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        finish()
      }
    }

    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

    if (intent?.getBooleanExtra(EXTRA_NAVIGATION, false) == true) {
      toolbar.setTitle(getTitleFromExtras())
      toolbar.setNavigationIcon(R.drawable.symbol_arrow_start_24)
      toolbar.setNavigationOnClickListener { finish() }
    } else {
      toolbar.visible = false
    }

    lifecycleDisposable.bindTo(this)
    lifecycleDisposable += viewModel.events.subscribe { shareEvent ->
      when (shareEvent) {
        is ShareEvent.OpenConversation -> openConversation(shareEvent)
        is ShareEvent.OpenMediaInterstitial -> openMediaInterstitial(shareEvent)
        is ShareEvent.OpenTextInterstitial -> openTextInterstitial(shareEvent)
        is ShareEvent.SendWithoutInterstitial -> sendWithoutInterstitial(shareEvent)
      }
    }

    var dialog: SignalProgressDialog? = null
    viewModel
      .state
      .debounce(500, TimeUnit.MILLISECONDS)
      .onErrorComplete()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { state ->
        if (state.loadState == ShareState.ShareDataLoadState.Init) {
          dialog = SignalProgressDialog.show(this, indeterminate = true)
        } else {
          dialog?.dismiss()
          dialog = null
        }
      }
      .addTo(lifecycleDisposable)

    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { shareState ->
      when (shareState.loadState) {
        ShareState.ShareDataLoadState.Init -> Unit
        ShareState.ShareDataLoadState.Failed -> finish()
        is ShareState.ShareDataLoadState.Loaded -> {
          val directShareTarget = this.directShareTarget
          if (directShareTarget != null) {
            Log.d(TAG, "Encountered a direct share target. Opening conversation with resolved share data.")
            openConversation(
              ShareEvent.OpenConversation(
                shareState.loadState.resolvedShareData,
                ContactSearchKey.RecipientSearchKey(directShareTarget, false)
              )
            )
          } else {
            ensureFragment(shareState.loadState.resolvedShareData)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() = Unit

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    if (bundle.containsKey(MultiselectForwardFragment.RESULT_SENT)) {
      throw AssertionError("Should never happen.")
    }

    if (!bundle.containsKey(MultiselectForwardFragment.RESULT_SELECTION)) {
      throw AssertionError("Expected a recipient selection!")
    }

    val contactSearchKeys: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)!!

    viewModel.onContactSelectionConfirmed(contactSearchKeys)
  }

  override fun getContainer(): ViewGroup = findViewById(R.id.container)

  override fun getDialogBackgroundColor(): Int = ThemeUtil.getThemedColor(this, R.attr.signal_background_primary)

  private fun getUnresolvedShareData(): Result<UnresolvedShareData, IntentError> {
    return when {
      intent.action == Intent.ACTION_SEND_MULTIPLE && intent.hasExtra(Intent.EXTRA_TEXT) -> {
        intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.let { list ->
          val stringBuilder = SpannableStringBuilder()
          list.forEachIndexed { index, text ->
            stringBuilder.append(text)

            if (index != list.lastIndex) {
              stringBuilder.append("\n")
            }
          }

          Result.success(UnresolvedShareData.ExternalPrimitiveShare(stringBuilder))
        } ?: Result.failure(IntentError.SEND_MULTIPLE_TEXT)
      }

      intent.action == Intent.ACTION_SEND_MULTIPLE && intent.hasExtra(Intent.EXTRA_STREAM) -> {
        intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM, Uri::class.java)?.let {
          Result.success(UnresolvedShareData.ExternalMultiShare(it))
        } ?: Result.failure(IntentError.SEND_MULTIPLE_STREAM)
      }

      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_STREAM) -> {
        val uri: Uri? = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri == null) {
          extractSingleExtraTextFromIntent(IntentError.SEND_STREAM)
        } else {
          val text: CharSequence? = if (intent.hasExtra(Intent.EXTRA_TEXT)) intent.getCharSequenceExtra(Intent.EXTRA_TEXT) else null
          Result.success(UnresolvedShareData.ExternalSingleShare(uri, intent.type, text))
        }
      }

      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_TEXT) -> {
        extractSingleExtraTextFromIntent()
      }

      else -> null
    } ?: Result.failure(IntentError.UNKNOWN)
  }

  private fun extractSingleExtraTextFromIntent(fallbackError: IntentError = IntentError.UNKNOWN): Result<UnresolvedShareData, IntentError> {
    return if (intent.hasExtra(Intent.EXTRA_TEXT)) {
      intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let {
        Result.success(UnresolvedShareData.ExternalPrimitiveShare(it))
      } ?: Result.failure(IntentError.SEND_TEXT)
    } else {
      Result.failure(fallbackError)
    }
  }

  private fun ensureFragment(resolvedShareData: ResolvedShareData) {
    if (!supportFragmentManager.isStateSaved && supportFragmentManager.fragments.none { it is MultiselectForwardFullScreenDialogFragment }) {
      supportFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          MultiselectForwardFragment.create(
            MultiselectForwardFragmentArgs(
              multiShareArgs = listOf(resolvedShareData.toMultiShareArgs()),
              title = getTitleFromExtras(),
              forceDisableAddMessage = true,
              forceSelectionOnly = true
            )
          )
        ).commitNow()
    }
  }

  private fun openConversation(shareEvent: ShareEvent.OpenConversation) {
    if (shareEvent.contact.isStory) {
      error("Can't open a conversation for a story!")
    }

    Log.d(TAG, "Opening conversation...")

    val multiShareArgs = shareEvent.getMultiShareArgs()
    lifecycleDisposable += ConversationIntents.createBuilder(this, shareEvent.contact.recipientId, -1L)
      .subscribeBy { conversationIntentBuilder ->
        conversationIntentBuilder
          .withDataUri(multiShareArgs.dataUri)
          .withDataType(multiShareArgs.dataType)
          .withMedia(multiShareArgs.media)
          .withDraftText(multiShareArgs.draftText)
          .withStickerLocator(multiShareArgs.stickerLocator)
          .asBorderless(multiShareArgs.isBorderless)
          .withShareDataTimestamp(System.currentTimeMillis())

        val mainActivityIntent = MainActivity.clearTop(this)
        finish()
        startActivities(arrayOf(mainActivityIntent, conversationIntentBuilder.build()))
      }
  }

  private fun openMediaInterstitial(shareEvent: ShareEvent.OpenMediaInterstitial) {
    Log.d(TAG, "Opening media share interstitial...")

    val multiShareArgs = shareEvent.getMultiShareArgs()
    val media: MutableList<Media> = ArrayList(multiShareArgs.media)
    if (media.isEmpty() && multiShareArgs.dataUri != null) {
      media.add(
        Media(
          uri = multiShareArgs.dataUri,
          contentType = multiShareArgs.dataType,
          date = 0,
          width = 0,
          height = 0,
          size = 0,
          duration = 0,
          isBorderless = false,
          isVideoGif = false,
          bucketId = null,
          caption = null,
          transformProperties = null,
          fileName = null
        )
      )
    }

    val shareAsTextStory = multiShareArgs.allRecipientsAreStories() && media.isEmpty()

    val intent = share(
      this,
      MessageSendType.SignalMessageSendType,
      media,
      multiShareArgs.recipientSearchKeys.toList(),
      multiShareArgs.draftText,
      shareAsTextStory
    )

    finishOnOkResultLauncher.launch(intent)
  }

  private fun openTextInterstitial(shareEvent: ShareEvent.OpenTextInterstitial) {
    Log.d(TAG, "Opening text share interstitial...")

    finishOnOkResultLauncher.launch(ShareInterstitialActivity.createIntent(this, shareEvent.getMultiShareArgs()))
  }

  private fun sendWithoutInterstitial(shareEvent: ShareEvent.SendWithoutInterstitial) {
    Log.d(TAG, "Sending without an interstitial...")

    MultiShareSender.send(shareEvent.getMultiShareArgs()) { results: MultiShareSendResultCollection? ->
      MultiShareDialogs.displayResultDialog(this, results!!) {
        finish()
      }
    }
  }

  private fun handleIntentError(intentError: IntentError) {
    val logEntry = when (intentError) {
      IntentError.SEND_MULTIPLE_TEXT -> "Failed to parse text array from intent for multi-share."
      IntentError.SEND_MULTIPLE_STREAM -> "Failed to parse stream array from intent for multi-share."
      IntentError.SEND_TEXT -> "Failed to parse text from intent for single-share."
      IntentError.SEND_STREAM -> "Failed to parse stream from intent for single-share."
      IntentError.UNKNOWN -> "Failed to parse unknown from intent."
    }

    Log.w(TAG, "$logEntry action: ${intent.action}, type: ${intent.type}")
    Toast.makeText(this, R.string.ShareActivity__could_not_get_share_data_from_intent, Toast.LENGTH_LONG).show()
  }

  private fun getTitleFromExtras(): Int {
    return intent?.getIntExtra(EXTRA_TITLE, R.string.MultiselectForwardFragment__share_with) ?: R.string.MultiselectForwardFragment__share_with
  }

  /**
   * Represents an error with the intent when trying to extract the unresolved share data.
   */
  private enum class IntentError {
    SEND_MULTIPLE_TEXT,
    SEND_MULTIPLE_STREAM,
    SEND_TEXT,
    SEND_STREAM,
    UNKNOWN
  }
}
