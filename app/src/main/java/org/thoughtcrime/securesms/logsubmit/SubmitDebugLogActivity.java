package org.thoughtcrime.securesms.logsubmit;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ShareCompat;
import androidx.core.text.util.LinkifyCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.LongClickCopySpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.List;

public class SubmitDebugLogActivity extends BaseActivity implements SubmitDebugLogAdapter.Listener {

  private RecyclerView            lineList;
  private SubmitDebugLogAdapter   adapter;
  private SubmitDebugLogViewModel viewModel;

  private View                   warningBanner;
  private View                   editBanner;
  private CircularProgressButton submitButton;
  private AlertDialog            loadingDialog;
  private View                   scrollToBottomButton;
  private View                   scrollToTopButton;

  private MenuItem editMenuItem;
  private MenuItem doneMenuItem;
  private MenuItem searchMenuItem;

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dynamicTheme.onCreate(this);
    setContentView(R.layout.submit_debug_log_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.HelpSettingsFragment__debug_log);

    initView();
    initViewModel();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.submit_debug_log_normal, menu);

    this.editMenuItem   = menu.findItem(R.id.menu_edit_log);
    this.doneMenuItem   = menu.findItem(R.id.menu_done_editing_log);
    this.searchMenuItem = menu.findItem(R.id.menu_search);

    SearchView searchView                        = (SearchView) searchMenuItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        viewModel.onQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        viewModel.onQueryUpdated(query);
        return true;
      }
    };

    searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        searchView.setOnQueryTextListener(null);
        viewModel.onSearchClosed();
        return true;
      }
    });

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home:
        onBackPressed();
        return true;
      case R.id.menu_edit_log:
        viewModel.onEditButtonPressed();
        break;
      case R.id.menu_done_editing_log:
        viewModel.onDoneEditingButtonPressed();
        break;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    if (!viewModel.onBackPressed()) {
      super.onBackPressed();
      final Intent nextIntent = getIntent().getParcelableExtra("next_intent");
      if (nextIntent != null) {
        startActivity(nextIntent);
      }
      finish();
    }
  }

  @Override
  public void onLogDeleted(@NonNull LogLine logLine) {
    viewModel.onLogDeleted(logLine);
  }

  private void initView() {
    this.lineList             = findViewById(R.id.debug_log_lines);
    this.warningBanner        = findViewById(R.id.debug_log_warning_banner);
    this.editBanner           = findViewById(R.id.debug_log_edit_banner);
    this.submitButton         = findViewById(R.id.debug_log_submit_button);
    this.scrollToBottomButton = findViewById(R.id.debug_log_scroll_to_bottom);
    this.scrollToTopButton    = findViewById(R.id.debug_log_scroll_to_top);

    this.adapter = new SubmitDebugLogAdapter(this);

    this.lineList.setLayoutManager(new LinearLayoutManager(this));
    this.lineList.setAdapter(adapter);

    submitButton.setOnClickListener(v -> onSubmitClicked());

    scrollToBottomButton.setOnClickListener(v -> lineList.scrollToPosition(adapter.getItemCount() - 1));
    scrollToTopButton.setOnClickListener(v -> lineList.scrollToPosition(0));

    lineList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() < adapter.getItemCount() - 10) {
          scrollToBottomButton.setVisibility(View.VISIBLE);
        } else {
          scrollToBottomButton.setVisibility(View.GONE);
        }

        if (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() > 10) {
          scrollToTopButton.setVisibility(View.VISIBLE);
        } else {
          scrollToTopButton.setVisibility(View.GONE);
        }
      }
    });

    this.loadingDialog = SimpleProgressDialog.show(this);
  }

  private void initViewModel() {
    this.viewModel = ViewModelProviders.of(this, new SubmitDebugLogViewModel.Factory()).get(SubmitDebugLogViewModel.class);

    viewModel.getLines().observe(this, this::presentLines);
    viewModel.getMode().observe(this, this::presentMode);
  }

  private void presentLines(@NonNull List<LogLine> lines) {
    if (loadingDialog != null && lines.size() > 0) {
      loadingDialog.dismiss();
      loadingDialog = null;

      warningBanner.setVisibility(View.VISIBLE);
      submitButton.setVisibility(View.VISIBLE);
    }

    adapter.setLines(lines);
  }

  private void presentMode(@NonNull SubmitDebugLogViewModel.Mode mode) {
    switch (mode) {
      case NORMAL:
        editBanner.setVisibility(View.GONE);
        adapter.setEditing(false);
        editMenuItem.setVisible(true);
        doneMenuItem.setVisible(false);
        searchMenuItem.setVisible(true);
        break;
      case SUBMITTING:
        editBanner.setVisibility(View.GONE);
        adapter.setEditing(false);
        editMenuItem.setVisible(false);
        doneMenuItem.setVisible(false);
        searchMenuItem.setVisible(false);
        break;
      case EDIT:
        editBanner.setVisibility(View.VISIBLE);
        adapter.setEditing(true);
        editMenuItem.setVisible(false);
        doneMenuItem.setVisible(true);
        searchMenuItem.setVisible(true);
        break;
    }
  }

  private void presentResultDialog(@NonNull String url) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                                                 .setTitle(R.string.SubmitDebugLogActivity_success)
                                                 .setCancelable(false)
                                                 .setNeutralButton(android.R.string.ok, (d, w) -> finish())
                                                 .setPositiveButton(R.string.SubmitDebugLogActivity_share, (d, w) -> {
                                                   ShareCompat.IntentBuilder.from(this)
                                                                            .setText(url)
                                                                            .setType("text/plain")
                                                                            .setEmailTo(new String[] { "benarmstead@protonmail.com" })
                                                                            .startChooser();
                                                 });

    String            dialogText          = getResources().getString(R.string.SubmitDebugLogActivity_copy_this_url_and_add_it_to_your_issue, url);
    SpannableString   spannableDialogText = new SpannableString(dialogText);
    TextView          dialogView          = new TextView(builder.getContext());
    LongClickCopySpan longClickUrl        = new LongClickCopySpan(url);


    LinkifyCompat.addLinks(spannableDialogText, Linkify.WEB_URLS);

    URLSpan[] spans = spannableDialogText.getSpans(0, spannableDialogText.length(), URLSpan.class);
    for (URLSpan span : spans) {
      int start = spannableDialogText.getSpanStart(span);
      int end   = spannableDialogText.getSpanEnd(span);

      spannableDialogText.setSpan(longClickUrl, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    dialogView.setText(spannableDialogText);
    dialogView.setMovementMethod(LongClickMovementMethod.getInstance(this));

    ViewUtil.setPadding(dialogView, (int) ThemeUtil.getThemedDimen(this, R.attr.dialogPreferredPadding));

    builder.setView(dialogView);
    builder.show();
  }

  private void onSubmitClicked() {
    submitButton.setClickable(false);
    submitButton.setIndeterminateProgressMode(true);
    submitButton.setProgress(50);

    viewModel.onSubmitClicked().observe(this, result -> {
      if (result.isPresent()) {
        presentResultDialog(result.get());
      } else {
        Toast.makeText(this, R.string.SubmitDebugLogActivity_failed_to_submit_logs, Toast.LENGTH_LONG).show();
      }

      submitButton.setClickable(true);
      submitButton.setIndeterminateProgressMode(false);
      submitButton.setProgress(0);
    });
  }
}
