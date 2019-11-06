package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.textfield.TextInputLayout;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.PassphraseValidator;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.Arrays;

public class ChangePassphraseDialogFragment extends DialogFragment {

  private static final String TAG = Log.tag(ChangePassphraseDialogFragment.class);

  private static final String KEY_MODE = "mode";

  public static final int MODE_CHANGE  = 0;
  public static final int MODE_ENABLE  = 1;
  public static final int MODE_DISABLE = 2;

  private int mode = MODE_CHANGE;

  private AlertDialog dialog;

  private MasterSecretChangedListener listener;

  private PassphraseValidator validator;

  private TextInputLayout passphraseLayout;
  private EditText        passphraseInput;
  private EditText        newPassphraseInput;
  private EditText        repeatPassphraseInput;
  private View            contentView;
  private View            progressView;
  private AsyncTask       masterSecretTask;

  private int titleResId = R.string.AndroidManifest__change_passphrase;

  public static ChangePassphraseDialogFragment newInstance() {
    return new ChangePassphraseDialogFragment();
  }

  public static ChangePassphraseDialogFragment newInstance(int mode) {
    ChangePassphraseDialogFragment frag = new ChangePassphraseDialogFragment();
    Bundle args = new Bundle();
    args.putInt(KEY_MODE, mode);
    frag.setArguments(args);
    return frag;
  }

  public void setMasterSecretChangedListener(final MasterSecretChangedListener listener) {
    this.listener = listener;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

    if (getArguments() != null) {
      mode = getArguments().getInt(KEY_MODE);
    }

    if (mode == MODE_ENABLE || mode == MODE_CHANGE) {
      validator = new PassphraseValidator();
    }

    int positiveButtonResId = android.R.string.ok;
    int negativeButtonResId = android.R.string.cancel;

    int layoutResId = R.layout.change_passphrase_dialog_view;

    if (mode == MODE_ENABLE) {
      layoutResId = R.layout.create_passphrase_dialog_view;
      titleResId  = R.string.AndroidManifest__create_passphrase;
    } else if (mode == MODE_DISABLE) {
      layoutResId = R.layout.disable_passphrase_dialog_view;
      titleResId  = R.string.ApplicationPreferencesActivity_disable_passphrase;
      positiveButtonResId = R.string.ApplicationPreferencesActivity_disable;
    }

    dialog = builder.setView(setLayout(layoutResId))
                    .setPositiveButton(positiveButtonResId, null)
                    .setNegativeButton(negativeButtonResId, null)
                    .setTitle(getString(titleResId))
                    .create();

    return dialog;
  }

  public void onResume() {
    super.onResume();

    getOkButton().setOnClickListener(v -> {
      hideKeyboard(v);
      onOkClicked();
    });

    contentView.requestFocus();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    listener = null;
  }

  @Override
  public void onCancel(@NonNull DialogInterface dialog) {
    super.onCancel(dialog);

    if (masterSecretTask != null) {
      masterSecretTask.cancel(false);
    }
  }

  private void onOkClicked() {
    if (mode == MODE_ENABLE) {
      handleEnable();
    } else if (mode == MODE_CHANGE) {
      handleChange();
    } else if (mode == MODE_DISABLE) {
      handleDisable();
    }
  }

  private View setLayout(int layoutResId) {
    final LayoutInflater inflater = requireActivity().getLayoutInflater();
    final View view = inflater.inflate(layoutResId, null);

    passphraseLayout      = view.findViewById(R.id.passphrase_layout);
    passphraseInput       = view.findViewById(R.id.passphrase_input);
    newPassphraseInput    = view.findViewById(R.id.new_passphrase_input);
    repeatPassphraseInput = view.findViewById(R.id.repeat_passphrase_input);
    contentView           = view.findViewById(R.id.content_container);
    progressView          = view.findViewById(R.id.progress_container);

    clearErrorOnEdit(passphraseLayout);

    return view;
  }

  private Button getOkButton() {
    return dialog.getButton(Dialog.BUTTON_POSITIVE);
  }

  private Button getCancelButton() {
    return dialog.getButton(Dialog.BUTTON_NEGATIVE);
  }

  private void setError(int resId, final TextInputLayout layout) {
    layout.setError(getString(resId));

    EditText editText = layout.getEditText();
    if (editText != null) {
      editText.selectAll();
      editText.requestFocus();
    }
  }

  private void clearErrorOnEdit(final TextInputLayout layout) {
    if (layout == null || layout.getEditText() == null)
      return;

    layout.getEditText().addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        layout.setError(null);
      }
    });
  }

  private void hideKeyboard(View view) {
    InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  private void showPopup(int resId) {
    Activity activity = getActivity();
    if (activity == null) return;
    Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show();
  }

  private void showProgress(boolean show) {
    int progress, content;

    if (show) {
      progress = View.VISIBLE;
      content  = View.GONE;
    } else {
      progress = View.GONE;
      content  = View.VISIBLE;
    }

    if (progressView != null) {
      progressView.setVisibility(progress);
      contentView.setVisibility(content);
      dialog.setTitle(show ? R.string.please_wait : titleResId);
    }

    getOkButton().setVisibility(content);
    getCancelButton().setVisibility(content);
  }

  private void handleEnable() {
    changePassphrase(MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
  }

  private void handleChange() {
    final char[] oldPassphrase = getEnteredPassphrase(passphraseInput);

    if (oldPassphrase.length == 0) {
      setError(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation, passphraseLayout);
      return;
    }

    changePassphrase(oldPassphrase);
  }

  private void handleDisable() {
    disablePassphrase();
  }

  private void changePassphrase(final char[] oldPassphrase) {
    final char[] newPassphrase    = getEnteredPassphrase(newPassphraseInput);
    final char[] repeatPassphrase = getEnteredPassphrase(repeatPassphraseInput);

    if (newPassphrase.length == 0) {
      showPopup(R.string.PassphraseChangeActivity_enter_new_passphrase_exclamation);
      return;
    } else if (!Arrays.equals(newPassphrase, repeatPassphrase)) {
      showPopup(R.string.PassphraseChangeActivity_passphrases_dont_match_exclamation);
      return;
    }

    PassphraseValidator.Strength strength = validator.estimate(newPassphrase);
    if (!strength.isValid()) {
      String body = getString(R.string.ChangePassphraseDialogFragment_estimated_time_to_crack_suggestion,
              strength.getError(), strength.getTimeToCrack(), strength.getSuggestion());
      new AlertDialog.Builder(requireActivity())
              .setTitle(R.string.ChangePassphraseDialogFragment_weak_passphrase)
              .setIconAttribute(R.attr.dialog_alert_icon)
              .setMessage(body)
              .setPositiveButton(android.R.string.ok, null).show();
      return;
    }

    changeMasterSecret(newPassphrase, oldPassphrase);
  }

  private void disablePassphrase() {
    changeMasterSecret(MasterSecretUtil.UNENCRYPTED_PASSPHRASE, getEnteredPassphrase(passphraseInput));
  }

  private void changeMasterSecret(char[] newPassphrase, char[] oldPassphrase) {
    ChangeMasterSecretTask task = new ChangeMasterSecretTask(newPassphrase, oldPassphrase);
    masterSecretTask = task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private char[] getEnteredPassphrase(final EditText editText) {
    int len = editText.length();
    char[] passphrase = new char[len];
    if (editText.getText() != null) {
      editText.getText().getChars(0, len, passphrase, 0);
    }
    return passphrase;
  }

  public interface MasterSecretChangedListener {
    void onMasterSecretChanged(MasterSecret masterSecret);
  }

  @SuppressLint("StaticFieldLeak")
  private class ChangeMasterSecretTask extends AsyncTask<Void, Void, MasterSecret> {

    private final Context context;
    private final char[]  newPassphrase;
    private final char[]  oldPassphrase;

    ChangeMasterSecretTask(char[] newPassphrase, char[] oldPassphrase) {
      this.context       = requireContext().getApplicationContext();
      this.newPassphrase = newPassphrase;
      this.oldPassphrase = oldPassphrase;
    }

    @Override
    protected void onPreExecute() {
      showProgress(true);
    }

    @Override
    protected MasterSecret doInBackground(Void... voids) {
      setCancelable(false);

      if (isCancelled()) {
        Log.d(TAG, "ChangeMasterSecretTask was canceled");
        return null;
      }

      try {
        if (!MasterSecretUtil.isPassphraseInitialized(context)) {
          return MasterSecretUtil.generateMasterSecret(context, newPassphrase);
        } else {
          return MasterSecretUtil.changeMasterSecretPassphrase(context, oldPassphrase, newPassphrase);
        }
      } catch (InvalidPassphraseException | UnrecoverableKeyException e) {
        if (mode == MODE_ENABLE) {
          throw new AssertionError(e);
        }
        return null;
      }
    }

    @Override
    protected void onPostExecute(MasterSecret masterSecret) {
      setCancelable(true);
      showProgress(false);

      if (masterSecret != null) {
        if (listener != null) {
          listener.onMasterSecretChanged(masterSecret);
        }
        dismissAllowingStateLoss();
      } else {
        setError(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation, passphraseLayout);
      }
    }
  }
}