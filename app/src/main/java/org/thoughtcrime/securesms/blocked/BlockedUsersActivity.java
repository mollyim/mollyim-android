package org.thoughtcrime.securesms.blocked;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Optional;
import java.util.function.Consumer;

public class BlockedUsersActivity extends PassphraseRequiredActivity implements BlockedUsersFragment.Listener, ContactSelectionListFragment.OnContactSelectedListener {

  private static final String CONTACT_SELECTION_FRAGMENT = "Contact.Selection.Fragment";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private BlockedUsersViewModel viewModel;
  private View                  container;

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    lifecycleDisposable.bindTo(this);

    setContentView(R.layout.blocked_users_activity);

    BlockedUsersRepository        repository = new BlockedUsersRepository(this);
    BlockedUsersViewModel.Factory factory    = new BlockedUsersViewModel.Factory(repository);

    viewModel = new ViewModelProvider(this, factory).get(BlockedUsersViewModel.class);

    Toolbar           toolbar           = findViewById(R.id.toolbar);
    ContactFilterView contactFilterView = findViewById(R.id.contact_filter_edit_text);
    container                           = findViewById(R.id.fragment_container);

    toolbar.setNavigationOnClickListener(unused -> onBackPressed());
    contactFilterView.setOnFilterChangedListener(query -> {
      Fragment fragment = getSupportFragmentManager().findFragmentByTag(CONTACT_SELECTION_FRAGMENT);
      if (fragment != null) {
        ((ContactSelectionListFragment) fragment).setQueryFilter(query);
      }
    });
    contactFilterView.setHint(R.string.BlockedUsersActivity__add_blocked_user);

    //noinspection CodeBlock2Expr
    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
        contactFilterView.setVisibility(View.VISIBLE);
        contactFilterView.focusAndShowKeyboard();
      } else {
        contactFilterView.setVisibility(View.GONE);
        ViewUtil.hideKeyboard(this, contactFilterView);
      }
    });

    getSupportFragmentManager().beginTransaction()
                               .add(R.id.fragment_container, new BlockedUsersFragment())
                               .commit();

    lifecycleDisposable.add(
        viewModel
            .getEvents()
            .subscribe(event -> handleEvent(container, event))
    );
  }

  @Override
  protected void onResume() {
    super.onResume();

    dynamicTheme.onResume(this);
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback) {
    Optional<Recipient> resolvedRecipient = recipientId.map(Recipient::resolved);

    final String displayName = resolvedRecipient
        .map(r -> r.getDisplayName(this))
        .orElse(number);

    boolean isSelf = resolvedRecipient
        .map(Recipient::isSelf)
        .orElseGet(() -> Optional.ofNullable(number)
                                 .map(Recipient::external)
                                 .map(Recipient::isSelf)
                                 .orElse(false));

    if (isSelf) {
      Snackbar.make(container, getString(R.string.BlockedUsersActivity__cannot_block_yourself), Snackbar.LENGTH_SHORT).show();
      return;
    }

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

    if (resolvedRecipient.isPresent() && resolvedRecipient.get().isGroup()) {
      Recipient recipient = resolvedRecipient.get();
      if (SignalDatabase.groups().isActive(recipient.requireGroupId())) {
        builder.setTitle(getString(R.string.BlockUnblockDialog_block_and_leave_s, displayName));
        builder.setMessage(R.string.BlockUnblockDialog_you_will_no_longer_receive_messages_or_updates);
      } else {
        builder.setTitle(getString(R.string.BlockUnblockDialog_block_s, displayName));
        builder.setMessage(R.string.BlockUnblockDialog_group_members_wont_be_able_to_add_you);
      }
    } else {
      builder.setTitle(R.string.BlockedUsersActivity__block_user);
      builder.setMessage(getString(R.string.BlockedUserActivity__s_will_not_be_able_to, displayName));
    }

    AlertDialog confirmationDialog = builder
        .setPositiveButton(R.string.BlockedUsersActivity__block, (dialog, which) -> {
          if (recipientId.isPresent()) {
            viewModel.block(recipientId.get());
          } else {
            viewModel.createAndBlock(number);
          }
          dialog.dismiss();
          onBackPressed();
        })
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
        .setCancelable(true)
        .create();

    confirmationDialog.setOnShowListener(dialog -> {
      confirmationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED);
    });

    confirmationDialog.show();

    callback.accept(false);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number, @NonNull Optional<ChatType> chatType) {

  }

  @Override
  public void onSelectionChanged() {
  }

  @Override
  public void handleAddUserToBlockedList() {
    ContactSelectionListFragment fragment = new ContactSelectionListFragment();
    Intent                       intent   = getIntent();

    intent.putExtra(ContactSelectionArguments.REFRESHABLE, false);
    intent.putExtra(ContactSelectionArguments.SELECTION_LIMITS, 1);
    intent.putExtra(ContactSelectionArguments.DISPLAY_MODE,
                    ContactSelectionDisplayMode.FLAG_PUSH |
                    ContactSelectionDisplayMode.FLAG_SMS |
                    ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS |
                    ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS |
                    ContactSelectionDisplayMode.FLAG_BLOCK);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, fragment, CONTACT_SELECTION_FRAGMENT)
                               .addToBackStack(null)
                               .commit();
  }

  private void handleEvent(@NonNull View view, @NonNull BlockedUsersViewModel.Event event) {
    final String displayName;

    if (event.getRecipient() == null) {
      displayName = event.getNumber();
    } else {
      displayName = event.getRecipient().getDisplayName(this);
    }

    final @StringRes int messageResId;
    switch (event.getEventType()) {
      case BLOCK_SUCCEEDED:
        messageResId = R.string.BlockedUsersActivity__s_has_been_blocked;
        break;
      case BLOCK_FAILED:
        messageResId = R.string.BlockedUsersActivity__failed_to_block_s;
        break;
      case UNBLOCK_SUCCEEDED:
        messageResId = R.string.BlockedUsersActivity__s_has_been_unblocked;
        break;
      default:
        throw new IllegalArgumentException("Unsupported event type " + event);
    }

    Snackbar.make(view, getString(messageResId, displayName), Snackbar.LENGTH_SHORT).show();
  }
}
