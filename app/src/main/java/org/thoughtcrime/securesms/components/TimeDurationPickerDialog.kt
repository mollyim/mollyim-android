package org.thoughtcrime.securesms.components

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.TimeDurationPickerDialogBinding
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Time duration dialog for selection a duration of hours and minutes. Currently
 * designed specifically for screen lock but could easily be generalized in the future
 * if needed.
 *
 * Uses [setFragmentResult] to pass the provided duration back in milliseconds.
 */
class TimeDurationPickerDialog : DialogFragment(), NumericKeyboardView.Listener {

  private var _binding: TimeDurationPickerDialogBinding? = null
  private val binding: TimeDurationPickerDialogBinding
    get() = _binding!!

  private var duration: String = "0000000"
  private var full: Boolean = false

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = TimeDurationPickerDialogBinding.inflate(layoutInflater)

    binding.durationKeyboard.listener = this

    setDuration(requireArguments().getLong(ARGUMENT_DURATION_SECONDS).seconds)

    return MaterialAlertDialogBuilder(requireContext())
      .setView(binding.root)
      .setPositiveButton(R.string.TimeDurationPickerDialog_positive_button) { _, _ ->
        setFragmentResult(
          RESULT_DURATION,
          bundleOf(
            RESULT_KEY_DURATION_SECONDS to getDuration().inWholeSeconds
          )
        )
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  override fun onKeyPress(keyCode: Int) {
    if (full && keyCode != -1) {
      return
    }

    duration = if (keyCode == -1) {
      "0" + duration.substring(0, 6)
    } else {
      duration.substring(1) + keyCode
    }

    updateDuration()
  }

  private fun updateDuration() {
    binding.durationHour.text = duration.substring(0, 3)
    binding.durationMinute.text = duration.substring(3, 5)
    binding.durationSecond.text = duration.substring(5, 7)
    full = duration.toInt() > 1000000
  }

  private fun setDuration(duration: Duration) {
    duration.toComponents { hours, minutes, seconds, _ ->
      this.duration = String.format("%03d%02d%02d", hours, minutes, seconds)
    }
    updateDuration()
  }

  private fun getDuration(): Duration {
    val hours = duration.substring(0, 3).toInt()
    val minutes = duration.substring(3, 5).toInt()
    val seconds = duration.substring(5, 7).toInt()

    return hours.hours.plus(minutes.minutes).plus(seconds.seconds).coerceAtMost(30.days)
  }

  companion object {
    const val RESULT_DURATION = "RESULT_DURATION"
    const val RESULT_KEY_DURATION_SECONDS = "RESULT_KEY_DURATION_SECONDS"

    private const val ARGUMENT_DURATION_SECONDS = "ARGUMENT_DURATION_SECONDS"

    fun create(duration: Duration): TimeDurationPickerDialog {
      return TimeDurationPickerDialog().apply {
        arguments = bundleOf(
          ARGUMENT_DURATION_SECONDS to duration.inWholeSeconds
        )
      }
    }
  }
}
