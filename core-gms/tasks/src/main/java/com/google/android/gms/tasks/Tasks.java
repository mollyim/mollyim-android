package com.google.android.gms.tasks;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.Preconditions;

import org.microg.gms.tasks.CancellationTokenImpl;
import org.microg.gms.tasks.TaskImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Keep
public final class Tasks {

  private Tasks() {}

  public static <TResult> TResult await(@NonNull Task<TResult> task)
      throws ExecutionException, InterruptedException
  {
    Preconditions.checkNotMainThread();
    Preconditions.checkNotGoogleApiHandlerThread();
    Preconditions.checkNotNull(task, "Task must not be null");

    if (!task.isComplete()) {
      CountDownLatch latch = new CountDownLatch(1);
      task.addOnCompleteListener(Runnable::run, completedTask -> latch.countDown());
      latch.await();
    }

    return getResult(task);
  }

  static <TResult> TResult await(@NonNull Task<TResult> task, long timeout, @NonNull TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException
  {
    Preconditions.checkNotMainThread();
    Preconditions.checkNotGoogleApiHandlerThread();
    Preconditions.checkNotNull(task, "Task must not be null");
    Preconditions.checkNotNull(unit, "TimeUnit must not be null");

    if (!task.isComplete()) {
      CountDownLatch latch = new CountDownLatch(1);
      task.addOnCompleteListener(Runnable::run, completedTask -> latch.countDown());
      boolean completed = latch.await(timeout, unit);
      if (!completed) {
        throw new TimeoutException("Timed out waiting for Task");
      }
    }

    return getResult(task);
  }

  @Deprecated
  @NonNull
  public static <TResult> Task<TResult> call(@NonNull Callable<TResult> callable) {
    return call(TaskExecutors.MAIN_THREAD, callable);
  }

  @Deprecated
  @NonNull
  public static <TResult> Task<TResult> call(@NonNull Executor executor, @NonNull Callable<TResult> callable) {
    Preconditions.checkNotNull(executor, "Executor must not be null");
    Preconditions.checkNotNull(callable, "Callback must not be null");

    TaskCompletionSource<TResult> taskSource = new TaskCompletionSource<>();
    executor.execute(() -> {
      try {
        TResult result = callable.call();
        taskSource.setResult(result);
      } catch (Exception e) {
        taskSource.setException(e);
      } catch (Throwable t) {
        taskSource.setException(new RuntimeException(t));
      }
    });
    return taskSource.getTask();
  }

  @NonNull
  public static <TResult> Task<TResult> forCanceled() {
    TaskImpl<TResult> task = new TaskImpl<>();
    task.cancel();
    return task;
  }

  public static <TResult> Task<TResult> forException(Exception e) {
    TaskImpl<TResult> task = new TaskImpl<>();
    task.setException(e);
    return task;
  }

  @NonNull
  public static <TResult> Task<TResult> forResult(TResult result) {
    TaskImpl<TResult> task = new TaskImpl<>();
    task.setResult(result);
    return task;
  }

  private static <TResult> TResult getResult(Task<TResult> task) throws ExecutionException {
    if (task.isSuccessful()) {
      return task.getResult();
    }
    if (task.isCanceled()) {
      throw new CancellationException("Task is already canceled");
    }
    throw new ExecutionException(task.getException());
  }

  @NonNull
  public static <T> Task<T> withTimeout(@NonNull Task<T> task, long timeout, @NonNull TimeUnit unit) {
    Preconditions.checkNotNull(task, "Task must not be null");
    Preconditions.checkArgument(timeout > 0L, "Timeout must be positive");
    Preconditions.checkNotNull(unit, "TimeUnit must not be null");

    final CancellationTokenImpl   cancellationToken    = new CancellationTokenImpl();
    final TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>(cancellationToken);

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable timeoutRunnable = () ->
        taskCompletionSource.trySetException(new TimeoutException());

    handler.postDelayed(timeoutRunnable, unit.toMillis(timeout));

    task.addOnCompleteListener(completedTask -> {
      handler.removeCallbacks(timeoutRunnable);

      if (completedTask.isSuccessful()) {
        taskCompletionSource.trySetResult(completedTask.getResult());
      } else if (completedTask.isCanceled()) {
        cancellationToken.cancel();
      } else {
        taskCompletionSource.trySetException(completedTask.getException());
      }
    });

    return taskCompletionSource.getTask();
  }
}
