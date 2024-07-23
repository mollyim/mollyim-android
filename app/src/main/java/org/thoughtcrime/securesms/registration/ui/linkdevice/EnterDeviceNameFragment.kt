package org.thoughtcrime.securesms.registration.ui.linkdevice

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationLinkDeviceNameBinding
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class EnterDeviceNameFragment : LoggingFragment(R.layout.fragment_registration_link_device_name) {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationLinkDeviceNameBinding by ViewBinderDelegate(FragmentRegistrationLinkDeviceNameBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)

    binding.deviceName.input.addTextChangedListener(afterTextChanged = {
      binding.linkButton.isEnabled = !it.isNullOrBlank()
      sharedViewModel.setLinkDeviceName(it.toString())
    })

    binding.deviceName.setText(Build.MODEL)

    binding.linkButton.setOnClickListener {
      findNavController().safeNavigate(EnterDeviceNameFragmentDirections.actionLinkDevice())
    }
  }
}
