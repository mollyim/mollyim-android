package org.thoughtcrime.securesms;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.util.text.AfterTextChanged;

public class DeviceLinkFragment extends Fragment implements View.OnClickListener {

  private ConstraintLayout    container;
  private LinkClickedListener linkClickedListener;
  private Uri                 uri;
  private EditText            linkInput;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    container = (ConstraintLayout) inflater.inflate(R.layout.device_link_fragment, container, false);

    final View linkButton = container.findViewById(R.id.link_device);
    linkButton.setOnClickListener(this);

    linkInput = container.findViewById(R.id.device_link_input);

    if (uri != null) {
      container.findViewById(R.id.device_input_text).setVisibility(View.GONE);
      container.findViewById(R.id.device_link_input_layout).setVisibility(View.GONE);
    } else {
      linkButton.setEnabled(false);
      linkInput.addTextChangedListener(new AfterTextChanged(editable -> container.findViewById(R.id.link_device).setEnabled(editable.length() > 0)));
    }

    return container;
  }

  public void setLinkClickedListener(Uri uri, LinkClickedListener linkClickedListener) {
    this.uri                 = uri;
    this.linkClickedListener = linkClickedListener;
  }

  @Override
  public void onClick(View v) {
    if (linkClickedListener != null) {
      linkClickedListener.onLink(uri != null ? uri : Uri.parse(linkInput.getText().toString()));
    }
  }

  public interface LinkClickedListener {
    void onLink(Uri uri);
  }
}
