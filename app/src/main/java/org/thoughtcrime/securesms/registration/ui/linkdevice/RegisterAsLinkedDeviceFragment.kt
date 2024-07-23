package org.thoughtcrime.securesms.registration.ui.linkdevice

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationLinkDeviceBinding
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.Util

class RegisterAsLinkedDeviceFragment : LoggingFragment(R.layout.fragment_registration_link_device) {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationLinkDeviceBinding by ViewBinderDelegate(FragmentRegistrationLinkDeviceBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.linkingTextCode.setOnClickListener {
      copyToClipboard(binding.linkingTextCode.text)
    }
    binding.linkRetryButton.setOnClickListener {
      attemptDeviceLink()
    }

    sharedViewModel.uiState.observe(viewLifecycleOwner, ::updateViewState)

    attemptDeviceLink()
  }

  private fun updateViewState(state: RegistrationState) {
    if (state.deviceLinkUrl != null && !state.inProgress) {
      binding.linkingQrImage.setQrText(state.deviceLinkUrl)
      binding.linkingTextCode.text = state.deviceLinkUrl
      binding.linkingQrImage.visibility = View.VISIBLE
      binding.linkingTextCode.visibility = View.VISIBLE
    } else {
      binding.linkingQrImage.visibility = View.GONE
      binding.linkingTextCode.visibility = View.GONE
    }
    if (state.inProgress) {
      binding.linkingLoadingSpinner.visibility = View.VISIBLE
    } else {
      binding.linkingLoadingSpinner.visibility = View.GONE
    }
    if (state.networkError != null) {
      binding.linkingError.visibility = View.VISIBLE
      binding.linkRetryButton.visibility = View.VISIBLE
      // binding.linkingTimeout.visibility = View.GONE
    } else {
      binding.linkingError.visibility = View.GONE
      binding.linkRetryButton.visibility = View.GONE
    }
  }

  private fun attemptDeviceLink() {
    sharedViewModel.clearNetworkError()
    sharedViewModel.attemptDeviceLink(requireContext(), ::registrationErrorHandler)
  }

  private fun copyToClipboard(text: CharSequence) {
    Util.copyToClipboard(requireContext(), text)
    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  private fun registrationErrorHandler(result: RegisterAccountResult) {

  }
}
