package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class DeviceListFragment extends ListFragment
    implements LoaderManager.LoaderCallbacks<List<Device>>,
               ListView.OnItemClickListener, Button.OnClickListener
{

  private static final String TAG = Log.tag(DeviceListFragment.class);

  private SignalServiceAccountManager accountManager;
  private Locale                      locale;
  private View                        progressContainer;
  private FloatingActionButton        addDeviceButton;
  private Button.OnClickListener      addDeviceButtonListener;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.locale = (Locale) requireArguments().getSerializable(PassphraseRequiredActivity.LOCALE_EXTRA);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    this.accountManager = AppDependencies.getSignalServiceAccountManager();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View view = inflater.inflate(R.layout.device_list_fragment, container, false);

    this.progressContainer = view.findViewById(R.id.progress_container);
    this.addDeviceButton   = view.findViewById(R.id.add_device);
    if (SignalStore.account().isPrimaryDevice()) {
      this.addDeviceButton.setOnClickListener(this);
    } else {
      this.addDeviceButton.setVisibility(View.GONE);
    }

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);
    getLoaderManager().initLoader(0, null, this);
    getListView().setOnItemClickListener(this);
  }

  public void setAddDeviceButtonListener(Button.OnClickListener listener) {
    this.addDeviceButtonListener = listener;
  }

  @Override
  public @NonNull Loader<List<Device>> onCreateLoader(int id, Bundle args) {
    progressContainer.setVisibility(View.VISIBLE);

    return new DeviceListLoader(getActivity(), accountManager);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<List<Device>> loader, List<Device> data) {
    progressContainer.setVisibility(View.GONE);

    if (data == null) {
      handleLoaderFailed();
      return;
    }

    setListAdapter(new DeviceListAdapter(getActivity(), R.layout.device_list_item_view, data, locale, SignalStore.account().getDeviceId()));

    boolean hasLinkedDevices = data.stream().noneMatch(d -> d.getId() != SignalServiceAddress.DEFAULT_DEVICE_ID);
    TextSecurePreferences.setMultiDevice(getActivity(), SignalStore.account().isLinkedDevice() || hasLinkedDevices);
    SignalStore.misc().setHasLinkedDevices(hasLinkedDevices);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<List<Device>> loader) {
    setListAdapter(null);
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    final String deviceName = ((DeviceListItem)view).getDeviceName();
    final long   deviceId   = ((DeviceListItem)view).getDeviceId();

    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireActivity());
    builder.setTitle(getString(R.string.DeviceListActivity_unlink_s, deviceName));
    builder.setMessage(R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive);
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
      if (deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID && deviceId != SignalStore.account().getDeviceId()) {
        handleDisconnectDevice(deviceId);
      }
    });
    builder.show();
  }

  private void handleLoaderFailed() {
    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireActivity());
    builder.setMessage(R.string.DeviceListActivity_network_connection_failed);
    builder.setPositiveButton(R.string.DeviceListActivity_try_again,
        (dialog, which) -> getLoaderManager().restartLoader(0, null, DeviceListFragment.this));

    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> requireActivity().onBackPressed());
    builder.setOnCancelListener(dialog -> requireActivity().onBackPressed());

    builder.show();
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDisconnectDevice(final long deviceId) {
    new ProgressDialogAsyncTask<Void, Void, Boolean>(getActivity(),
                                                     R.string.DeviceListActivity_unlinking_device_no_ellipsis,
                                                     R.string.DeviceListActivity_unlinking_device)
    {
      @Override
      protected Boolean doInBackground(Void... params) {
        try {
          accountManager.removeDevice(deviceId);
          return true;
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }
      }

      @Override
      protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (result) {
          getLoaderManager().restartLoader(0, null, DeviceListFragment.this);
          LinkedDeviceInactiveCheckJob.enqueue();
        } else {
          Toast.makeText(getActivity(), R.string.DeviceListActivity_network_failed, Toast.LENGTH_LONG).show();
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @Override
  public void onClick(View v) {
    if (addDeviceButtonListener != null) addDeviceButtonListener.onClick(v);
  }

  private static class DeviceListAdapter extends ArrayAdapter<Device> {

    private final int    resource;
    private final Locale locale;
    private final long   thisDeviceId;

    public DeviceListAdapter(Context context, int resource, List<Device> objects, Locale locale, long thisDeviceId) {
      super(context, resource, objects);
      this.resource = resource;
      this.locale = locale;
      this.thisDeviceId = thisDeviceId;
    }

    @Override
    public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
      if (convertView == null) {
        convertView = ((Activity)getContext()).getLayoutInflater().inflate(resource, parent, false);
      }

      ((DeviceListItem)convertView).set(getItem(position), locale, thisDeviceId);

      return convertView;
    }

    @Override
    public boolean isEnabled(int position) {
      long deviceId = getItem(position).getId();
      return deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID && deviceId != thisDeviceId;
    }
  }
}
