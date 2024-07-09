package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.ListenableFuture;
import org.signal.core.util.concurrent.SettableFuture;
import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.net.NetworkManager;
import org.thoughtcrime.securesms.net.ProxyType;
import org.thoughtcrime.securesms.net.SocksProxy;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import info.guardianproject.netcipher.proxy.OrbotHelper;

public class NetworkPreferenceFragment extends ListSummaryPreferenceFragment {

  private final NetworkManager networkManager;

  private Preference networkState;

  private SettableFuture<Boolean> connected;

  public NetworkPreferenceFragment() {
    networkManager = AppDependencies.getNetworkManager();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    networkState = requirePreference("pref_network_connection_state");
    clearTestResult(networkState);

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
        clearTestResult(networkState);
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
      clearTestResult(networkState);
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
      clearTestResult(networkState);
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
    TextSecurePreferences.setHasSeenNetworkConfig(requireContext(), true);
    applyProxyChanges(false);
  }

  private void applyProxyChanges(boolean alwaysRestart) {
    final boolean changed = networkManager.applyProxyConfig();
    if (changed || alwaysRestart) {
      networkManager.setNetworkEnabled(true);
      AppDependencies.restartAllNetworkConnections();
    }
  }

  public boolean onTestConnectionClicked(Preference preference) {
    preference.setOnPreferenceClickListener(null);

    if (networkManager.isProxyEnabled()) {
      preference.setSummary(R.string.preferences_connecting_to_proxy);
    } else {
      preference.setSummary(R.string.preferences_network__connecting);
    }

    applyProxyChanges(true);

    connected = new SettableFuture<>();
    connected.addListener(new ListenableFuture.Listener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        preference.setSummary(getTestResultString(result));
      }

      @Override
      public void onFailure(ExecutionException e) {
        preference.setSummary(null);
      }
    });

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> SignalProxyUtil.testWebsocketConnection(TimeUnit.SECONDS.toMillis(10))
        , result -> {
      connected.set(result);
      preference.setOnPreferenceClickListener(this::onTestConnectionClicked);
    });

    return true;
  }

  private void clearTestResult(Preference preference) {
    if (connected != null) {
      connected.cancel(false);
    }

    preference.setSummary(null);
    preference.setOnPreferenceClickListener(this::onTestConnectionClicked);
  }

  private Spannable getTestResultString(boolean succeeded) {
    int statusResId;
    int colorResId;

    if (succeeded) {
      statusResId = R.string.preferences_network__online;
      colorResId  = R.color.signal_accent_green;
    } else {
      statusResId = R.string.preferences_connection_failed;
      colorResId  = R.color.signal_alert_primary;
    }

    Spannable spanned = new SpannableString(getString(statusResId));
    spanned.setSpan(new ForegroundColorSpan(getResources().getColor(colorResId)), 0, spanned.length(), 0);

    return spanned;
  }

  public static CharSequence getSummary(Context context) {
    int proxyTypeResId = TextSecurePreferences.getProxyType(context).getStringResource();
    return context.getString(R.string.ApplicationPreferencesActivity_network_summary, context.getString(proxyTypeResId));
  }

  private void promptToInstallOrbot(@NonNull Context context) {
    final Intent installIntent = OrbotHelper.getOrbotInstallIntent(context);
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.NetworkPreferenceFragment_missing_orbot_app)
        .setMessage(R.string.NetworkPreferenceFragment_molly_wont_connect_without_orbot_which_is_not_installed_on_this_device)
        .setPositiveButton(R.string.NetworkPreferenceFragment_get_orbot, (dialog, which) -> {
          startActivity(installIntent);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
