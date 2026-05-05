package org.thoughtcrime.securesms.parental

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ActivityParentalControlBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ParentalControlActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_SETUP_PIN = "extra_setup_pin"

    fun createIntent(context: Context, setupPin: Boolean = false): Intent =
      Intent(context, ParentalControlActivity::class.java)
        .putExtra(EXTRA_SETUP_PIN, setupPin)
  }

  private lateinit var binding: ActivityParentalControlBinding
  private val viewModel: ParentalControlViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityParentalControlBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.parentalToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    binding.parentalToolbar.setNavigationOnClickListener { finish() }

    binding.parentalModeSwitch.setOnCheckedChangeListener(null)

    viewModel.parentalEnabled.observe(this) { enabled ->
      binding.parentalModeSwitch.setOnCheckedChangeListener(null)
      binding.parentalModeSwitch.isChecked = enabled
      binding.parentalModeSwitch.setOnCheckedChangeListener { _, isChecked ->
        viewModel.setParentalEnabled(isChecked)
      }
    }

    viewModel.threads.observe(this) { threads ->
      populateThreadList(threads)
    }

    binding.parentalChangePinButton.setOnClickListener {
      showChangePinDialog()
    }

    viewModel.load()

    if (intent.getBooleanExtra(EXTRA_SETUP_PIN, false)) {
      showSetupPinDialog()
    }
  }

  private fun populateThreadList(threads: List<ParentalControlViewModel.ThreadItem>) {
    val container = binding.parentalThreadContainer
    container.removeAllViews()
    for (item in threads) {
      val row = layoutInflater.inflate(R.layout.item_parental_thread, container, false)
      row.findViewById<TextView>(R.id.thread_name).text = item.displayName
      val switch = row.findViewById<SwitchMaterial>(R.id.thread_allowed_switch)
      switch.isChecked = item.allowed
      switch.setOnCheckedChangeListener { _, isChecked ->
        viewModel.toggleThread(item.threadId, isChecked)
      }
      container.addView(row)
    }
    if (threads.isEmpty()) {
      val empty = TextView(this).apply {
        text = getString(R.string.parental_no_pending_invites)
        setPadding(0, 8, 0, 8)
      }
      container.addView(empty)
    }
  }

  private fun showSetupPinDialog() {
    showNewPinDialog(isSetup = true)
  }

  private fun showChangePinDialog() {
    if (SignalStore.parentalControl.parentPinHash.isEmpty()) {
      showNewPinDialog(isSetup = true)
    } else {
      ParentalPinDialog.show(this) {
        showNewPinDialog(isSetup = false)
      }
    }
  }

  private fun showNewPinDialog(isSetup: Boolean) {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val pad = resources.getDimensionPixelSize(R.dimen.dsl_settings_gutter)
      setPadding(pad, pad / 2, pad, 0)
    }

    val pinInput = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      hint = getString(R.string.parental_new_pin_title)
    }
    val confirmInput = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      hint = getString(R.string.parental_confirm_pin_title)
    }
    layout.addView(pinInput)
    layout.addView(confirmInput)

    MaterialAlertDialogBuilder(this)
      .setTitle(if (isSetup) R.string.parental_new_pin_title else R.string.parental_change_pin)
      .setView(layout)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val pin = pinInput.text.toString()
        val confirm = confirmInput.text.toString()
        when {
          pin.length < 4 ->
            Toast.makeText(this, R.string.parental_pin_too_short, Toast.LENGTH_SHORT).show()
          pin != confirm ->
            Toast.makeText(this, R.string.parental_pin_mismatch, Toast.LENGTH_SHORT).show()
          else ->
            viewModel.changePin(this, pin)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
