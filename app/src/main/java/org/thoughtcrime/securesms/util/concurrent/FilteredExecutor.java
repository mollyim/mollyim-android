package org.thoughtcrime.securesms.util.concurrent;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Allows you to specify a filter upon which a job will be executed on the provided executor. If
 * it doesn't match the filter, it will be run on the calling thread.
 */
public final class FilteredExecutor implements ExecutorService {

  private final ExecutorService executor;
  private final Filter          filter;

  public FilteredExecutor(@NonNull ExecutorService executor, @NonNull Filter filter) {
    this.executor = executor;
    this.filter   = filter;
  }

  @Override
  public void execute(@NonNull Runnable runnable) {
    if (filter.shouldRunOnExecutor()) {
      executor.execute(runnable);
    } else {
      runnable.run();
    }
  }

  @Override
  public void shutdown() {
    executor.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executor.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executor.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executor.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return executor.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return executor.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return executor.submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return executor.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return executor.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
    return executor.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    return executor.invokeAny(tasks, timeout, unit);
  }

  public interface Filter {
    boolean shouldRunOnExecutor();
  }
}
