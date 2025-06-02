package org.thoughtcrime.securesms.conversationlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;

import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

class ConversationListAdapter extends ListAdapter<Conversation, RecyclerView.ViewHolder> implements TimestampPayloadSupport {

  private static final int TYPE_THREAD              = 1;
  private static final int TYPE_ACTION              = 2;
  private static final int TYPE_PLACEHOLDER         = 3;
  private static final int TYPE_HEADER              = 4;
  private static final int TYPE_EMPTY               = 5;
  private static final int TYPE_CLEAR_FILTER_FOOTER = 6;
  private static final int TYPE_CLEAR_FILTER_EMPTY  = 7;
  private static final int TYPE_CHAT_FOLDER_EMPTY   = 8;
  private static final int TYPE_EMPTY_ARCHIVED      = 9;
  private static final int TYPE_NOTE_ITEM           = 10; // New type for notes
  private static final int TYPE_EMPTY_NOTES         = 11; // New type for notes empty state

  private enum Payload {
    TYPING_INDICATOR,
    SELECTION,
    TIMESTAMP,
    ACTIVE
  }

  private final LifecycleOwner                                      lifecycleOwner;
  private final RequestManager                                      requestManager;
  private final OnConversationClickListener                         onConversationClickListener;
  private final ClearFilterViewHolder.OnClearFilterClickListener    onClearFilterClicked;
  private final EmptyFolderViewHolder.OnFolderSettingsClickListener onFolderSettingsClicked;
  private final Set<Long>                                           typingSet                     = new HashSet<>();

  private       ConversationSet                                     selectedConversations         = new ConversationSet();
  private       long                                                activeThreadId                = 0;
  private       PagingController                                    pagingController;
  private       DisplayMode                                         currentDisplayMode            = DisplayMode.CHATS; // Default

  protected ConversationListAdapter(@NonNull LifecycleOwner lifecycleOwner,
                                    @NonNull RequestManager requestManager,
                                    @NonNull OnConversationClickListener onConversationClickListener,
                                    @NonNull ClearFilterViewHolder.OnClearFilterClickListener onClearFilterClicked,
                                    @NonNull EmptyFolderViewHolder.OnFolderSettingsClickListener onFolderSettingsClicked)
  {
    super(new ConversationDiffCallback());

    this.lifecycleOwner              = lifecycleOwner;
    this.requestManager              = requestManager;
    this.onConversationClickListener = onConversationClickListener;
    this.onClearFilterClicked        = onClearFilterClicked;
    this.onFolderSettingsClicked     = onFolderSettingsClicked;

    setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
  }

  public void setDisplayMode(@NonNull DisplayMode displayMode) {
    this.currentDisplayMode = displayMode;
    // Potentially notifyDataSetChanged() if the types of items visible might change,
    // but usually this is set before submitting a list.
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == TYPE_ACTION) {
      ConversationViewHolder holder = new ConversationViewHolder(LayoutInflater.from(parent.getContext())
                                                                               .inflate(R.layout.conversation_list_item_action, parent, false));

      holder.itemView.setOnClickListener(v -> {
        if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
          onConversationClickListener.onShowArchiveClick();
        }
      });

      return holder;
    } else if (viewType == TYPE_THREAD) {
      ConversationViewHolder holder = new ConversationViewHolder(CachedInflater.from(parent.getContext())
                                                                               .inflate(R.layout.conversation_list_item_view, parent, false));

      holder.itemView.setOnClickListener(v -> {
        int position = holder.getAdapterPosition();

        if (position != RecyclerView.NO_POSITION) {
          onConversationClickListener.onConversationClick(getItem(position));
        }
      });

      holder.itemView.setOnLongClickListener(v -> {
        int position = holder.getAdapterPosition();

        if (position != RecyclerView.NO_POSITION) {
          return onConversationClickListener.onConversationLongClick(getItem(position), v);
        }

        return false;
      });
      return holder;
    } else if (viewType == TYPE_PLACEHOLDER) {
      View v = new FrameLayout(parent.getContext());
      v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
      return new PlaceholderViewHolder(v);
    } else if (viewType == TYPE_HEADER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.dsl_section_header, parent, false);
      return new HeaderViewHolder(v);
    } else if (viewType == TYPE_EMPTY_ARCHIVED) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_archived_empty_state, parent, false);
      return new HeaderViewHolder(v);
    } else if (viewType == TYPE_EMPTY) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_empty_state, parent, false);
      return new HeaderViewHolder(v);
    } else if (viewType == TYPE_CLEAR_FILTER_FOOTER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_clear_filter, parent, false);
      return new ClearFilterViewHolder(v, onClearFilterClicked);
    } else if (viewType == TYPE_CLEAR_FILTER_EMPTY) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_clear_filter_empty, parent, false);
      return new ClearFilterViewHolder(v, onClearFilterClicked);
    } else if (viewType == TYPE_CHAT_FOLDER_EMPTY) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_folder_empty, parent, false);
      return new EmptyFolderViewHolder(v, onFolderSettingsClicked);
    } else if (viewType == TYPE_NOTE_ITEM) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_list_item, parent, false);
      NoteItemViewHolder holder = new NoteItemViewHolder(v);
      // Set click listener for notes navigation (to be implemented later)
      holder.itemView.setOnClickListener(tv -> {
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
          Conversation item = getItem(position);
          if (item != null && item.getDistributionType() == -100) { // It's a Note
            onConversationClickListener.onNoteClick(item); // New specific method for note clicks
          }
        }
      });
      holder.itemView.setOnLongClickListener(v -> {
        if (currentDisplayMode == DisplayMode.NOTES) {
          int position = holder.getAdapterPosition();
          if (position != RecyclerView.NO_POSITION) {
            Conversation item = getItem(position);
            if (item != null && item.getDistributionType() == -100) { // It's a Note
              return onConversationClickListener.onNoteLongClick(item, v); // New specific method
            }
          }
        }
        return false; // Let default long click proceed if not a note in notes mode
      });
      return holder;
    } else if (viewType == TYPE_EMPTY_NOTES) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.notes_list_empty_state, parent, false);
      return new RecyclerView.ViewHolder(v) {}; // Simple ViewHolder, no binding needed as layout has text
    } else {
      throw new IllegalStateException("Unknown type! " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else if (holder instanceof ConversationViewHolder) {
      for (Object payloadObject : payloads) {
        if (payloadObject instanceof Payload) {
          Payload                payload = (Payload) payloadObject;
          ConversationViewHolder vh      = (ConversationViewHolder) holder;

          switch (payload) {
            case TYPING_INDICATOR -> vh.getConversationListItem().updateTypingIndicator(typingSet);
            case SELECTION -> vh.getConversationListItem().setSelectedConversations(selectedConversations);
            case TIMESTAMP -> vh.getConversationListItem().updateTimestamp();
            case ACTIVE -> vh.getConversationListItem().setActiveThreadId(activeThreadId);
          }
        }
      }
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder.getItemViewType() == TYPE_ACTION || holder.getItemViewType() == TYPE_THREAD) {
      ConversationViewHolder casted       = (ConversationViewHolder) holder;
      Conversation           conversation = Objects.requireNonNull(getItem(position));

      casted.getConversationListItem().bind(lifecycleOwner,
                                            conversation.getThreadRecord(),
                                            requestManager,
                                            Locale.getDefault(),
                                            typingSet,
                                            selectedConversations,
                                            activeThreadId);
    } else if (holder.getItemViewType() == TYPE_HEADER) {
      HeaderViewHolder casted       = (HeaderViewHolder) holder;
      Conversation     conversation = Objects.requireNonNull(getItem(position));
      switch (conversation.getType()) {
        case PINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__pinned);
          break;
        case UNPINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__chats);
          break;
        default:
          throw new IllegalArgumentException();
      }
    } else if (holder.getItemViewType() == TYPE_CLEAR_FILTER_FOOTER || holder.getItemViewType() == TYPE_CLEAR_FILTER_EMPTY) {
      ClearFilterViewHolder casted       = (ClearFilterViewHolder) holder;
      Conversation          conversation = Objects.requireNonNull(getItem(position));

      casted.bind(conversation);
    } else if (holder.getItemViewType() == TYPE_NOTE_ITEM) {
      NoteItemViewHolder noteHolder   = (NoteItemViewHolder) holder;
      Conversation       noteConversation = Objects.requireNonNull(getItem(position));
      // Bind data from noteConversation (which is a transformed NoteEntity)
      // Title was stored in recipient's profile name
      noteHolder.title.setText(noteConversation.getThreadRecord().getRecipient().getProfileName().toString());
      // Snippet was stored in threadRecord's snippet
      noteHolder.snippet.setText(noteConversation.getThreadRecord().getSnippet());

      // Visual feedback for selection
      boolean isSelected = selectedConversations.contains(noteConversation.getThreadRecord().getThreadId());
      noteHolder.itemView.setSelected(isSelected);
      // itemView's background should be a state list drawable that handles android:state_selected="true"
      // For example: android:background="?attr/selectableItemBackground" or a custom one.
      // If not using a state list drawable, you might set a specific background color:
      // if (isSelected) {
      //   noteHolder.itemView.setBackgroundColor(ContextCompat.getColor(noteHolder.itemView.getContext(), R.color.selected_item_background));
      // } else {
      //   noteHolder.itemView.setBackgroundResource(R.drawable.default_item_background); // Or ?attr/selectableItemBackground
      // }


      // Color indicator can be set here if colorId is available and mapped
      // For now, using default. A more complex setup would involve NoteColorEntity.
      // Example: if (noteConversation.getThreadRecord().getColorId() != null) { ... }
    }
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getConversationListItem().unbind();
    }
  }

  @Override
  protected Conversation getItem(int position) {
    if (pagingController != null) {
      pagingController.onDataNeededAroundIndex(position);
    }

    return super.getItem(position);
  }

  @Override
  public void notifyTimestampPayloadUpdate() {
    notifyItemRangeChanged(0, getItemCount(), Payload.TIMESTAMP);
  }

  public void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  void setTypingThreads(@NonNull Set<Long> typingThreadSet) {
    this.typingSet.clear();
    this.typingSet.addAll(typingThreadSet);

    notifyItemRangeChanged(0, getItemCount(), Payload.TYPING_INDICATOR);
  }

  void setSelectedConversations(@NonNull ConversationSet conversations) {
    selectedConversations = conversations;
    notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
  }

  void setActiveThreadId(long activeThreadId) {
    this.activeThreadId = activeThreadId;
    notifyItemRangeChanged(0, getItemCount(), Payload.ACTIVE);
  }

  @Override
  public int getItemViewType(int position) {
    Conversation conversation = getItem(position);
    if (conversation == null) {
      return TYPE_PLACEHOLDER;
    }
    switch (conversation.getType()) {
      case PINNED_HEADER:
      case UNPINNED_HEADER:
        return TYPE_HEADER;
      case ARCHIVED_FOOTER:
        return TYPE_ACTION;
      case CONVERSATION_FILTER_FOOTER:
        return TYPE_CLEAR_FILTER_FOOTER;
      case CONVERSATION_FILTER_EMPTY:
        return TYPE_CLEAR_FILTER_EMPTY;
      case CHAT_FOLDER_EMPTY:
        return TYPE_CHAT_FOLDER_EMPTY;
      case THREAD:
        return TYPE_THREAD;
      case ARCHIVED_EMPTY:
        return TYPE_EMPTY_ARCHIVED;
      case EMPTY:
        return TYPE_EMPTY;
      default:
        // Check for custom note type
        if (conversation.getDistributionType() == -100) { // Matches ViewModel's note type indicator for actual notes
          return TYPE_NOTE_ITEM;
        }
        // Fallback or unknown, potentially an error unless other types also use distributionType -100
        throw new IllegalArgumentException("Unknown conversation type: " + conversation.getType() + " with distribution type: " + conversation.getDistributionType());
      case EMPTY: // This is the generic empty type from Conversation.Type.EMPTY
        if (currentDisplayMode == DisplayMode.NOTES) {
          return TYPE_EMPTY_NOTES;
        } else {
          return TYPE_EMPTY; // Original empty state for chats
        }
      default:
        throw new IllegalArgumentException("Unhandled conversation type: " + conversation.getType());
    }
  }

  static final class ConversationViewHolder extends RecyclerView.ViewHolder {

    private final BindableConversationListItem conversationListItem;

    ConversationViewHolder(@NonNull View itemView) {
      super(itemView);

      conversationListItem = (BindableConversationListItem) itemView;
    }

    public BindableConversationListItem getConversationListItem() {
      return conversationListItem;
    }
  }

  private static final class ConversationDiffCallback extends DiffUtil.ItemCallback<Conversation> {

    @Override
    public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.getThreadRecord().getThreadId() == newItem.getThreadRecord().getThreadId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.equals(newItem);
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    private TextView headerText;

    public HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      headerText = itemView.findViewById(R.id.section_header);
    }
  }

  static final class NoteItemViewHolder extends RecyclerView.ViewHolder {
    final TextView title;
    final TextView snippet;
    // final View colorIndicator; // Optional

    NoteItemViewHolder(@NonNull View itemView) {
      super(itemView);
      title = itemView.findViewById(R.id.note_item_title);
      snippet = itemView.findViewById(R.id.note_item_snippet);
      // colorIndicator = itemView.findViewById(R.id.note_item_color_indicator); // Optional
    }
  }

  static class EmptyFolderViewHolder extends RecyclerView.ViewHolder {

    public EmptyFolderViewHolder(@NonNull View itemView, OnFolderSettingsClickListener listener) {
      super(itemView);
      itemView.findViewById(R.id.folder_settings).setOnClickListener(v -> listener.onFolderSettingsClick());
    }

    interface OnFolderSettingsClickListener {
      void onFolderSettingsClick();
    }
  }

  interface OnConversationClickListener {
    void onConversationClick(@NonNull Conversation conversation);
    boolean onConversationLongClick(@NonNull Conversation conversation, @NonNull View view);
    void onShowArchiveClick();
    // Methods for Notes interaction
    void onNoteClick(@NonNull Conversation noteConversation); // For navigation
    boolean onNoteLongClick(@NonNull Conversation noteConversation, @NonNull View view); // For CAB
  }
}
