package com.google.android.gms.common.util.concurrent;

import android.os.Process;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Keep
public class NamedThreadFactory implements ThreadFactory {

  private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

  private final String name;

  public NamedThreadFactory(@NonNull String name) {
    this.name = name;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    Runnable wrapper = () -> {
      Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
      runnable.run();
    };

    Thread thread = defaultFactory.newThread(wrapper);
    thread.setName(name);
    return thread;
  }
}
