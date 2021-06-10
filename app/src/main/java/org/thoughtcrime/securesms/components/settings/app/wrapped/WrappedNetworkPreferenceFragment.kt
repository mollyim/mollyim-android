package org.thoughtcrime.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.preferences.NetworkPreferenceFragment

class WrappedNetworkPreferenceFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    toolbar.setTitle(R.string.preferences__network)
    return NetworkPreferenceFragment()
  }
}
