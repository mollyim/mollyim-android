package com.google.android.gms.tasks;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;

import java.util.concurrent.Executor;

@Keep
public class TaskExecutors {

  private TaskExecutors() {}

  public static final Executor MAIN_THREAD = new Executor() {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable runnable) {
      if (Looper.getMainLooper() == Looper.myLooper()) {
        runnable.run();
      } else {
        handler.post(runnable);
      }
    }
  };
}
