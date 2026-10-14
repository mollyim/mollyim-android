package org.thoughtcrime.securesms.screenlock

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment

/** No PIN or authentication success is saved into Fragment state. */
class ScreenLockPinDialogFragment : DialogFragment() {
  private var listener: BiometricDialogFragment.Listener? = null
  private var firstPin: CharArray? = null
  private var work: Job? = null
  private var input: TextInputEditText? = null
  private var error: TextView? = null
  private var submit: MaterialButton? = null
  private var title: TextView? = null
  private var cancelled = false
  private val generation: Long get() = requireArguments().getLong(GENERATION)
  private val setup: Boolean get() = requireArguments().getBoolean(SETUP)

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_BiometricDialog_FullScreen)
    isCancelable = false
  }

  override fun onCreateDialog(state: Bundle?): Dialog = super.onCreateDialog(state).apply {
    setOnKeyListener { _, code, event ->
      if (code == KeyEvent.KEYCODE_BACK) {
        if (event.action == KeyEvent.ACTION_UP) cancel()
        true
      } else false
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    return inflater.inflate(R.layout.screen_lock_pin_dialog, container, false).also { view ->
      input = view.findViewById<TextInputEditText>(R.id.screen_lock_pin_input).apply {
        isSaveEnabled = false
        filters = arrayOf(InputFilter.LengthFilter(16))
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setOnEditorActionListener { _, action, event ->
          if (action == EditorInfo.IME_ACTION_DONE || event?.let { it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP } == true) {
            submitPin()
            true
          } else false
        }
      }
      error = view.findViewById(R.id.screen_lock_pin_error)
      title = view.findViewById(R.id.screen_lock_pin_title)
      submit = view.findViewById<MaterialButton>(R.id.screen_lock_pin_submit).apply { setOnClickListener { submitPin() } }
      view.findViewById<MaterialButton>(R.id.screen_lock_pin_cancel).setOnClickListener { cancel() }
      view.findViewById<TextView>(R.id.screen_lock_pin_description).setText(
        if (setup) R.string.screen_lock_pin_setup_description else R.string.screen_lock_pin_description
      )
      updateTitle()
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.let {
      it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      it.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      ScreenLockController.showWhenLocked(it)
    }
  }

  override fun onResume() {
    super.onResume()
    if (setup && listener == null) {
      dismissAllowingStateLoss()
      return
    }
    if (!ScreenLockController.isCurrentAuthentication(generation)) {
      if (setup || ScreenLockPreferences.getMode(requireContext()) != ScreenLockMode.PIN) {
        dismissAllowingStateLoss()
      } else {
        requireArguments().putLong(GENERATION, ScreenLockController.authenticationGeneration())
      }
    }
  }

  override fun onPause() {
    work?.cancel()
    input?.text?.clear()
    firstPin?.fill('\u0000')
    firstPin = null
    updateTitle()
    super.onPause()
  }

  private fun updateTitle() {
    title?.setText(if (!setup) R.string.screen_lock_enter_pin else if (firstPin == null) R.string.screen_lock_new_pin else R.string.screen_lock_confirm_pin)
  }

  private fun submitPin() {
    if (work?.isActive == true || cancelled || !isResumed) return
    val editable = input?.text ?: return
    val pin = CharArray(editable.length) { editable[it] }
    editable.clear()
    if (pin.size !in 4..16 || pin.any { it !in '0'..'9' }) {
      pin.fill('\u0000')
      error?.setText(R.string.screen_lock_pin_length)
      return
    }
    if (setup && firstPin == null) {
      firstPin = pin
      error?.text = ""
      updateTitle()
      return
    }
    if (setup) {
      val first = checkNotNull(firstPin)
      val matches = first.contentEquals(pin)
      first.fill('\u0000')
      firstPin = null
      if (!matches) {
        pin.fill('\u0000')
        error?.setText(R.string.screen_lock_pin_mismatch)
        updateTitle()
        return
      }
    }
    val context = requireContext().applicationContext
    val requestGeneration = generation
    submit?.isEnabled = false
    input?.isEnabled = false
    work = viewLifecycleOwner.lifecycleScope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          try {
            if (setup) {
              val job = currentCoroutineContext()[Job]
              if (ScreenLockPinStore.install(context, pin, requestGeneration) { job?.isActive == true }) ScreenLockPinStore.Result.Success
              else ScreenLockPinStore.Result.Unavailable
            } else ScreenLockPinStore.verify(context, pin)
          } catch (_: Exception) {
            ScreenLockPinStore.Result.Unavailable
          } catch (_: AssertionError) {
            ScreenLockPinStore.Result.Unavailable
          } finally { pin.fill('\u0000') }
        }
        if (cancelled || !isResumed || (!setup && !ScreenLockController.isCurrentAuthentication(requestGeneration))) return@launch
        when (result) {
          ScreenLockPinStore.Result.Success -> {
            val callback = listener
            val host = activity
            dismissAllowingStateLoss()
            // Commit dismissal before a settings callback opens another PIN dialog.
            host?.window?.decorView?.post {
              if (!host.isFinishing && !host.isDestroyed) callback?.onSuccess()
            }
          }
          ScreenLockPinStore.Result.Incorrect -> error?.setText(R.string.screen_lock_pin_incorrect)
          ScreenLockPinStore.Result.Unavailable -> error?.setText(R.string.screen_lock_pin_unavailable)
          is ScreenLockPinStore.Result.Blocked -> error?.text = getString(R.string.screen_lock_pin_wait, (result.milliseconds + 999) / 1000)
        }
      } finally {
        pin.fill('\u0000')
        submit?.isEnabled = true
        input?.isEnabled = true
      }
    }
  }

  private fun cancel() {
    cancelled = true
    work?.cancel()
    firstPin?.fill('\u0000')
    firstPin = null
    listener?.onCancel(true)
    dismissAllowingStateLoss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    work?.cancel()
    super.onDismiss(dialog)
  }

  override fun onDestroyView() {
    firstPin?.fill('\u0000')
    firstPin = null
    input?.text?.clear()
    input = null
    submit = null
    error = null
    title = null
    listener = null
    super.onDestroyView()
  }

  companion object {
    private const val TAG = "molly-screen-pin-dialog"
    private const val SETUP = "setup"
    private const val GENERATION = "generation"

    @JvmStatic
    fun show(activity: FragmentActivity, setup: Boolean, listener: BiometricDialogFragment.Listener) {
      val manager = activity.supportFragmentManager
      if (manager.isStateSaved) return
      val existing = manager.findFragmentByTag(TAG) as? ScreenLockPinDialogFragment
      if (existing != null) {
        if (existing.setup != setup || existing.cancelled || (existing.setup && existing.generation != ScreenLockController.authenticationGeneration())) {
          existing.cancelled = true
          existing.work?.cancel()
          existing.dismissAllowingStateLoss()
          Handler(Looper.getMainLooper()).post {
            if (!activity.isFinishing && !activity.isDestroyed) show(activity, setup, listener)
          }
          return
        }
        existing.listener = listener
        return
      }
      ScreenLockPinDialogFragment().apply {
        arguments = Bundle().apply {
          putBoolean(SETUP, setup)
          putLong(GENERATION, ScreenLockController.authenticationGeneration())
        }
        this.listener = listener
      }.showNow(manager, TAG)
    }

    @JvmStatic
    @JvmOverloads
    fun cancelAuthentication(activity: FragmentActivity, includeSetup: Boolean = true) {
      (activity.supportFragmentManager.findFragmentByTag(TAG) as? ScreenLockPinDialogFragment)?.let {
        if (it.setup && !includeSetup) return
        it.cancelled = true
        it.dismissAllowingStateLoss()
      }
    }
  }
}
