package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.BundleExtensions;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.donations.StripeApi;
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar;
import org.thoughtcrime.securesms.components.DeviceSpecificNotificationBottomSheet;
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner;
import org.thoughtcrime.securesms.conversationlist.RelinkDevicesReminderBottomSheetFragment;
import org.thoughtcrime.securesms.conversationlist.RestoreCompleteBottomSheetDialog;
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceExitActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor;
import org.thoughtcrime.securesms.notifications.VitalsViewModel;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabRepository;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SplashScreenUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

import im.molly.unifiedpush.UnifiedPushDistributor;

public class MainActivity extends PassphraseRequiredActivity implements VoiceNoteMediaControllerOwner {

  private static final String KEY_STARTING_TAB      = "STARTING_TAB";
  public static final  int    RESULT_CONFIG_CHANGED = Activity.RESULT_FIRST_USER + 901;

  private final DynamicTheme  dynamicTheme = new DynamicNoActionBarTheme();
  private final MainNavigator navigator    = new MainNavigator(this);

  private VoiceNoteMediaController      mediaController;
  private ConversationListTabsViewModel conversationListTabsViewModel;
  private VitalsViewModel               vitalsViewModel;

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  private boolean onFirstRender = false;

  public static @NonNull Intent clearTop(@NonNull Context context) {
    Intent intent = new Intent(context, MainActivity.class);

    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);

    return intent;
  }

  public static @NonNull Intent clearTopAndOpenTab(@NonNull Context context, @NonNull ConversationListTab startingTab) {
    Intent intent = clearTop(context);
    intent.putExtra(KEY_STARTING_TAB, startingTab);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    AppStartup.getInstance().onCriticalRenderEventStart();
    super.onCreate(savedInstanceState, ready);

    setContentView(R.layout.main_activity);
    final View content = findViewById(android.R.id.content);
    content.getViewTreeObserver().addOnPreDrawListener(
        new ViewTreeObserver.OnPreDrawListener() {
          @Override
          public boolean onPreDraw() {
            // Use pre draw listener to delay drawing frames till conversation list is ready
            if (onFirstRender) {
              content.getViewTreeObserver().removeOnPreDrawListener(this);
              return true;
            } else {
              return false;
            }
          }
        });

    lifecycleDisposable.bindTo(this);

    mediaController = new VoiceNoteMediaController(this, true);


    ConversationListTab startingTab = null;
    if (getIntent().getExtras() != null) {
      startingTab = BundleExtensions.getSerializableCompat(getIntent().getExtras(), KEY_STARTING_TAB, ConversationListTab.class);
    }

    ConversationListTabRepository         repository = new ConversationListTabRepository();
    ConversationListTabsViewModel.Factory factory    = new ConversationListTabsViewModel.Factory(startingTab, repository);

    handleDeeplinkIntent(getIntent());

    CachedInflater.from(this).clear();

    conversationListTabsViewModel = new ViewModelProvider(this, factory).get(ConversationListTabsViewModel.class);
    updateTabVisibility();

    vitalsViewModel = new ViewModelProvider(this).get(VitalsViewModel.class);

    lifecycleDisposable.add(
        vitalsViewModel
            .getVitalsState()
            .subscribe(this::presentVitalsState)
    );
  }

  @SuppressLint("NewApi")
  private void presentVitalsState(VitalsViewModel.State state) {
    switch (state) {
      case NONE:
        break;
      case PROMPT_UNIFIEDPUSH_SELECT_DISTRIBUTOR_DIALOG:
        UnifiedPushDistributor.showSelectDistributorDialog(this);
        break;
      case PROMPT_SPECIFIC_BATTERY_SAVER_DIALOG:
        DeviceSpecificNotificationBottomSheet.show(getSupportFragmentManager());
        break;
      case PROMPT_GENERAL_BATTERY_SAVER_DIALOG:
        PromptBatterySaverDialogFragment.show(getSupportFragmentManager());
        break;
    }
  }

  @Override
  public Intent getIntent() {
    return super.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                      Intent.FLAG_ACTIVITY_NEW_TASK |
                                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleDeeplinkIntent(intent);

    if (intent.getExtras() != null) {
      ConversationListTab startingTab = BundleExtensions.getSerializableCompat(intent.getExtras(), KEY_STARTING_TAB, ConversationListTab.class);
      if (startingTab != null) {
        switch (startingTab) {
          case CHATS -> conversationListTabsViewModel.onChatsSelected();
          case CALLS -> {
            if (TextSecurePreferences.getNavbarShowCalls(this)) {
              conversationListTabsViewModel.onCallsSelected();
            }
          }
          case STORIES -> {
            if (Stories.isFeatureEnabled()) {
              conversationListTabsViewModel.onStoriesSelected();
            }
          }
        }
      }
    }
  }

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);

    if (SignalStore.misc().getShouldShowLinkedDevicesReminder()) {
      SignalStore.misc().setShouldShowLinkedDevicesReminder(false);
      RelinkDevicesReminderBottomSheetFragment.show(getSupportFragmentManager());
    }

    if (SignalStore.registration().isRestoringOnNewDevice()) {
      SignalStore.registration().setRestoringOnNewDevice(false);
      RestoreCompleteBottomSheetDialog.show(getSupportFragmentManager());
    } else if (SignalStore.misc().isOldDeviceTransferLocked()) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(R.string.OldDeviceTransferLockedDialog__complete_registration_on_your_new_device)
          .setMessage(R.string.OldDeviceTransferLockedDialog__your_signal_account_has_been_transferred_to_your_new_device)
          .setPositiveButton(R.string.OldDeviceTransferLockedDialog__done, (d, w) -> OldDeviceExitActivity.exit(this))
          .setNegativeButton(R.string.OldDeviceTransferLockedDialog__cancel_and_activate_this_device, (d, w) -> {
            SignalStore.misc().setOldDeviceTransferLocked(false);
            DeviceTransferBlockingInterceptor.getInstance().unblockNetwork();
          })
          .setCancelable(false)
          .show();
    }

    updateTabVisibility();

    vitalsViewModel.checkSlowNotificationHeuristics();
  }

  @Override
  protected void onStop() {
    super.onStop();
    SplashScreenUtil.setSplashScreenThemeIfNecessary(this, SignalStore.settings().getTheme(), SignalStore.settings().isDynamicColorsEnabled());
  }

  @Override
  public void onBackPressed() {
    if (!navigator.onBackPressed()) {
      super.onBackPressed();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == MainNavigator.REQUEST_CONFIG_CHANGES && resultCode == RESULT_CONFIG_CHANGED) {
      recreate();
    }
  }

  private void updateTabVisibility() {
    final boolean showStories = Stories.isFeatureEnabled();
    final boolean showCalls   = TextSecurePreferences.getNavbarShowCalls(this);
    if (showCalls || showStories) {
      findViewById(R.id.conversation_list_tabs).setVisibility(View.VISIBLE);
      WindowUtil.setNavigationBarColor(this, ThemeUtil.getThemedColor(this, R.attr.navbar_container_color));
    } else {
      findViewById(R.id.conversation_list_tabs).setVisibility(View.GONE);
      WindowUtil.setNavigationBarColor(this, ThemeUtil.getThemedColor(this, com.google.android.material.R.attr.colorSurface));
    }
    ConversationListTab selectedTab = conversationListTabsViewModel.getStateSnapshot().getTab();
    if ((selectedTab == ConversationListTab.CALLS && !showCalls) ||
        (selectedTab == ConversationListTab.STORIES && !showStories))
    {
      conversationListTabsViewModel.onChatsSelected();
    }
  }

  public @NonNull MainNavigator getNavigator() {
    return navigator;
  }

  private void handleDeeplinkIntent(Intent intent) {
    handleGroupLinkInIntent(intent);
    handleSignalMeIntent(intent);
    handleCallLinkInIntent(intent);
    handleDonateReturnIntent(intent);
  }

  private void handleGroupLinkInIntent(Intent intent) {
    Uri data = intent.getData();
    if (data != null) {
      CommunicationActions.handlePotentialGroupLinkUrl(this, data.toString());
    }
  }

  private void handleSignalMeIntent(Intent intent) {
    Uri data = intent.getData();
    if (data != null) {
      CommunicationActions.handlePotentialSignalMeUrl(this, data.toString());
    }
  }

  private void handleCallLinkInIntent(Intent intent) {
    Uri data = intent.getData();
    if (data != null) {
      CommunicationActions.handlePotentialCallLinkUrl(this, data.toString(), () -> {
        YouAreAlreadyInACallSnackbar.show(findViewById(android.R.id.content));
      });
    }
  }

  private void handleDonateReturnIntent(Intent intent) {
    Uri data = intent.getData();
    if (data != null && data.toString().startsWith(StripeApi.RETURN_URL_IDEAL)) {
      startActivity(AppSettingsActivity.manageSubscriptions(this));
    }
  }

  public void onFirstRender() {
    onFirstRender = true;
  }

  @Override
  public @NonNull VoiceNoteMediaController getVoiceNoteMediaController() {
    return mediaController;
  }
}
