package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
import org.signal.paging.PagedData;
import org.signal.paging.PagedDataSource;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import org.signal.paging.ProxyPagingController;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;

public class SubmitDebugLogViewModel extends ViewModel {

  private final SubmitDebugLogRepository        repo;
  private final MutableLiveData<Mode>           mode;
  private final ProxyPagingController<Long>     pagingController;
  private final List<LogLine>                   staticLines;
  private final MediatorLiveData<List<LogLine>> lines;
  private final long                            firstViewTime;
  private final byte[]                          trace;


  private SubmitDebugLogViewModel() {
    this.repo             = new SubmitDebugLogRepository();
    this.mode             = new MutableLiveData<>();
    this.trace            = Tracer.getInstance().serialize();
    this.pagingController = new ProxyPagingController<>();
    this.firstViewTime    = System.currentTimeMillis();
    this.staticLines      = new ArrayList<>();
    this.lines            = new MediatorLiveData<>();

    repo.getPrefixLogLines(staticLines -> {
      this.staticLines.addAll(staticLines);

      PagedDataSource<Long, LogLine> dataSource;

      try {
        Log.blockUntilAllWritesFinished();
        LogDatabase.getInstance(ApplicationDependencies.getApplication()).trimToSize();

        dataSource = new LogDataSource(ApplicationDependencies.getApplication(), staticLines, firstViewTime);
      } catch (IllegalStateException e) {
        dataSource = new PagedDataSource<Long, LogLine>() {
          @Override
          public int size() {
            return staticLines.size();
          }

          @Override
          public @NotNull List<LogLine> load(int start, int length, @NotNull CancellationSignal cancellationSignal) {
            return staticLines.subList(start, start + length);
          }

          @Override
          public @Nullable LogLine load(Long aLong) {
            return null;
          }

          @Override
          public @NonNull Long getKey(@NonNull LogLine logLine) {
            return logLine.getId();
          }
        };
      }

      PagingConfig  config     = new PagingConfig.Builder().setPageSize(100)
                                                           .setBufferPages(3)
                                                           .setStartIndex(0)
                                                           .build();

      PagedData<Long, LogLine> pagedData = PagedData.create(dataSource, config);

      ThreadUtil.runOnMain(() -> {
        pagingController.set(pagedData.getController());
        lines.addSource(pagedData.getData(), lines::setValue);
        mode.setValue(Mode.NORMAL);
      });
    });
  }

  @NonNull LiveData<List<LogLine>> getLines() {
    return lines;
  }

  @NonNull PagingController getPagingController() {
    return pagingController;
  }

  @NonNull LiveData<Mode> getMode() {
    return mode;
  }

  @NonNull LiveData<Optional<String>> onSubmitClicked() {
    mode.postValue(Mode.SUBMITTING);

    MutableLiveData<Optional<String>> result = new MutableLiveData<>();

    repo.submitLogWithPrefixLines(firstViewTime, staticLines, trace, value -> {
      mode.postValue(Mode.NORMAL);
      result.postValue(value);
    });

    return result;
  }

  void onQueryUpdated(@NonNull String query) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onSearchClosed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onEditButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onDoneEditingButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onLogDeleted(@NonNull LogLine line) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  boolean onBackPressed() {
    if (mode.getValue() == Mode.EDIT) {
      mode.setValue(Mode.NORMAL);
      return true;
    } else {
      return false;
    }
  }

  enum Mode {
    NORMAL, EDIT, SUBMITTING
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new SubmitDebugLogViewModel());
    }
  }
}
