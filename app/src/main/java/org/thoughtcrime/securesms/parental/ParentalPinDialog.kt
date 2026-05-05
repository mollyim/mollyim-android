package org.thoughtcrime.securesms.parental

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * PIN entry dialog that gates parent-only actions.
 * Shows a numeric password input; on correct PIN calls [onSuccess].
 */
object ParentalPinDialog {

  fun show(context: Context, onSuccess: () -> Unit) {
    val pinInput = EditText(context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      hint = context.getString(R.string.parental_pin_dialog_hint)
    }

    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.parental_pin_dialog_title)
      .setView(pinInput)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val entered = pinInput.text.toString()
        if (SignalStore.parentalControl.verifyPin(entered)) {
          onSuccess()
        } else {
          Toast.makeText(context, R.string.parental_incorrect_pin, Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
