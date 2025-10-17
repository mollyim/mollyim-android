package org.thoughtcrime.securesms.longmessage;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ConversationItemFooter;
import org.thoughtcrime.securesms.components.FullScreenDialogFragment;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode;
import org.thoughtcrime.securesms.conversation.colors.ColorizerView;
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemUtils;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.Collections;
import java.util.Locale;

public class LongMessageFragment extends FullScreenDialogFragment {

  private static final String KEY_MESSAGE_ID             = "message_id";
  private static final String KEY_IS_MMS                 = "is_mms";

  private static final int MAX_DISPLAY_LENGTH = 64 * 1024;

  private Stub<ViewGroup>      sentBubble;
  private Stub<ViewGroup>      receivedBubble;
  private ColorizerView        colorizerView;
  private BubbleLayoutListener bubbleLayoutListener;

  private LongMessageViewModel viewModel;

  public static DialogFragment create(long messageId, boolean isMms) {
    DialogFragment fragment = new LongMessageFragment();
    Bundle         args     = new Bundle();

    args.putLong(KEY_MESSAGE_ID, messageId);
    args.putBoolean(KEY_IS_MMS, isMms);

    fragment.setArguments(args);

    return fragment;
  }


  @Override
  protected int getTitle() {
    return -1;
  }

  @Override
  protected int getDialogLayoutResource() {
    return R.layout.longmessage_fragment;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    sentBubble     = new Stub<>(view.findViewById(R.id.longmessage_sent_stub));
    receivedBubble = new Stub<>(view.findViewById(R.id.longmessage_received_stub));
    colorizerView  = view.findViewById(R.id.colorizer);

    bubbleLayoutListener = new BubbleLayoutListener();

    initViewModel(requireArguments().getLong(KEY_MESSAGE_ID, -1), requireArguments().getBoolean(KEY_IS_MMS, false));
  }

  private void initViewModel(long messageId, boolean isMms) {
    viewModel = new ViewModelProvider(this,
                                      new LongMessageViewModel.Factory(requireActivity().getApplication(),
                                                                       new LongMessageRepository(), messageId, isMms))
                                  .get(LongMessageViewModel.class);

    viewModel.getMessage().observe(this, message -> {
      if (message == null) return;

      if (!message.isPresent()) {
        Toast.makeText(requireContext(), R.string.LongMessageActivity_unable_to_find_message, Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
        return;
      }


      if (message.get().getMessageRecord().isOutgoing()) {
        toolbar.setTitle(getString(R.string.LongMessageActivity_your_message));
      } else {
        Recipient recipient = message.get().getMessageRecord().getFromRecipient();
        String    name      = recipient.getDisplayName(requireContext());

        toolbar.setTitle(getString(R.string.LongMessageActivity_message_from_s, name));
      }

      ViewGroup bubble;

      if (message.get().getMessageRecord().isOutgoing()) {
        bubble = sentBubble.get();
        colorizerView.setVisibility(View.VISIBLE);
        colorizerView.setBackground(message.get().getMessageRecord().getToRecipient().getChatColors().getChatBubbleMask());
        bubble.getBackground().setColorFilter(message.get().getMessageRecord().getToRecipient().getChatColors().getChatBubbleColorFilter());
        bubble.addOnLayoutChangeListener(bubbleLayoutListener);
        bubbleLayoutListener.onLayoutChange(bubble, 0, 0, 0, 0, 0, 0, 0, 0);
      } else {
        bubble = receivedBubble.get();
        bubble.getBackground().setColorFilter(ThemeUtil.getThemedColor(requireContext(), R.attr.signal_background_secondary), PorterDuff.Mode.MULTIPLY);
      }

      EmojiTextView          text   = bubble.findViewById(R.id.longmessage_text);
      ConversationItemFooter footer = bubble.findViewById(R.id.longmessage_footer);

      SpannableString body = new SpannableString(getTrimmedBody(message.get().getFullBody(requireContext())));
      V2ConversationItemUtils.linkifyUrlLinks(body,
                                              true,
                                              url -> CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url));

      bubble.setVisibility(View.VISIBLE);
      text.setMovementMethod(LongClickMovementMethod.getInstance(getContext()));
      text.setTextSize(TypedValue.COMPLEX_UNIT_SP, SignalStore.settings().getMessageFontSize());
      text.setTextAsync(body);

      if (!message.get().getMessageRecord().isOutgoing()) {
        text.setMentionBackgroundTint(ContextCompat.getColor(requireContext(), ThemeUtil.isDarkTheme(requireActivity()) ? R.color.core_grey_60 : R.color.core_grey_20));
      } else {
        text.setMentionBackgroundTint(ContextCompat.getColor(requireContext(), R.color.transparent_black_40));
      }
      footer.setMessageRecord(message.get().getMessageRecord(), Locale.getDefault(), ConversationItemDisplayMode.Standard.INSTANCE);
    });
  }

  private CharSequence getTrimmedBody(@NonNull CharSequence text) {
    return text.length() <= MAX_DISPLAY_LENGTH ? text
                                               : text.subSequence(0, MAX_DISPLAY_LENGTH);
  }

  private final class BubbleLayoutListener implements View.OnLayoutChangeListener {
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
      Projection projection = Projection.relativeToViewWithCommonRoot(v, colorizerView, new Projection.Corners(16));

      colorizerView.setProjections(Collections.singletonList(projection));
    }
  }
}
