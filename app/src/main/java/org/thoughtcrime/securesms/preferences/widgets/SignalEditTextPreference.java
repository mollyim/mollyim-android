package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

public class SignalEditTextPreference extends EditTextPreference {

  private TextView     rightTextView;
  private CharSequence text;

  public SignalEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public SignalEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public SignalEditTextPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SignalEditTextPreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setWidgetLayoutResource(R.layout.preference_right_summary_widget);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder view) {
    super.onBindViewHolder(view);

    rightTextView = (TextView) view.findViewById(R.id.right_summary);
    rightTextView.setText(text);
  }

  @Override
  public void setText(String text) {
    super.setText(text);

    this.text = text;
    if (rightTextView != null) {
      rightTextView.setText(text);
    }
  }
}
