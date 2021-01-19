package org.thoughtcrime.securesms.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.net.NetworkManager;
import org.thoughtcrime.securesms.net.ProxyType;
import org.thoughtcrime.securesms.net.SocksProxy;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import info.guardianproject.netcipher.proxy.OrbotHelper;

public class NetworkPreferenceFragment extends ListSummaryPreferenceFragment {

  private final NetworkManager networkManager;

  public NetworkPreferenceFragment() {
    networkManager = ApplicationDependencies.getNetworkManager();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    ListPreference     proxyType  = requirePreference(TextSecurePreferences.PROXY_TYPE);
    Preference         socksGroup = requirePreference("proxy_socks");
    EditTextPreference socksHost  = requirePreference(TextSecurePreferences.PROXY_SOCKS_HOST);
    EditTextPreference socksPort  = requirePreference(TextSecurePreferences.PROXY_SOCKS_PORT);

    proxyType.setOnPreferenceChangeListener(new ListSummaryListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object value) {
        ProxyType type = ProxyType.fromCode((String) value);
        if (type == ProxyType.ORBOT && !networkManager.isOrbotAvailable()) {
          promptToInstallOrbot(preference.getContext());
          return false;
        }
        networkManager.setProxyChoice(type);
        socksGroup.setVisible(type == ProxyType.SOCKS5);
        return super.onPreferenceChange(preference, value);
      }
    });
    initializeListSummary(proxyType);

    socksGroup.setVisible(TextSecurePreferences.getProxyType(requireContext()) == ProxyType.SOCKS5);

    socksHost.setOnBindEditTextListener(editText -> {
      editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      editText.selectAll();
    });
    socksHost.setOnPreferenceChangeListener(((preference, newValue) -> {
      String host = (String) newValue;
      if (!host.isEmpty()) {
        if (!SocksProxy.isValidHost(host)) {
          Toast.makeText(preference.getContext(), R.string.NetworkPreferenceFragment_the_host_you_typed_is_not_valid, Toast.LENGTH_SHORT).show();
          return false;
        }
      } else {
        host = networkManager.getDefaultProxySocksHost();
      }
      networkManager.setProxySocksHost(host);
      socksHost.setText(host);
      return false;
    }));

    socksPort.setOnBindEditTextListener(editText -> {
      editText.setInputType(InputType.TYPE_CLASS_NUMBER);
      editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(5)});
      editText.selectAll();
    });
    socksPort.setOnPreferenceChangeListener(((preference, newValue) -> {
      String newValueStr = (String) newValue;
      int port;
      if (!newValueStr.isEmpty()) {
        port = Integer.parseInt(newValueStr);
        if (!SocksProxy.isValidPort(port)) {
          Toast.makeText(preference.getContext(), R.string.NetworkPreferenceFragment_a_valid_port_number_is_between_0_and_65535, Toast.LENGTH_SHORT).show();
          return false;
        }
      } else {
        port = networkManager.getDefaultProxySocksPort();
      }
      networkManager.setProxySocksPort(port);
      socksPort.setText(String.valueOf(port));
      return false;
    }));
  }

  @Override
  protected void onCreateEncryptedPreferences(Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_network);
  }

  @Override
  public void onResume() {
    super.onResume();
    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.preferences__network);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    networkManager.applyConfiguration();
  }

  public static CharSequence getSummary(Context context) {
    int proxyTypeResId = TextSecurePreferences.getProxyType(context).getStringResource();
    return context.getString(R.string.ApplicationPreferencesActivity_network_summary, context.getString(proxyTypeResId));
  }

  void promptToInstallOrbot(@NonNull Context context) {
    final Intent installIntent = OrbotHelper.getOrbotInstallIntent(context);
    new AlertDialog.Builder(context)
                   .setTitle(R.string.NetworkPreferenceFragment_missing_orbot_app)
                   .setMessage(R.string.NetworkPreferenceFragment_molly_wont_connect_without_orbot_which_is_not_installed_on_this_device)
                   .setPositiveButton(R.string.NetworkPreferenceFragment_get_orbot, (dialog, which) -> {
                     startActivity(installIntent);
                   })
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
  }
}
