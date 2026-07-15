package org.signal.core.util.concurrent;

import org.jetbrains.annotations.NotNull;

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

  public FilteredExecutor(@NotNull ExecutorService executor, @NotNull Filter filter) {
    this.executor = executor;
    this.filter   = filter;
  }

  @Override
  public void execute(@NotNull Runnable runnable) {
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
  @NotNull
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
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return executor.awaitTermination(timeout, unit);
  }

  @Override
  @NotNull
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    return executor.submit(task);
  }

  @Override
  @NotNull
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    return executor.submit(task, result);
  }

  @Override
  @NotNull
  public Future<?> submit(@NotNull Runnable task) {
    return executor.submit(task);
  }

  @Override
  @NotNull
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return executor.invokeAll(tasks);
  }

  @Override
  @NotNull
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return executor.invokeAll(tasks, timeout, unit);
  }

  @Override
  @NotNull
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
    return executor.invokeAny(tasks);
  }

  @Override
  @NotNull
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    return executor.invokeAny(tasks, timeout, unit);
  }

  public interface Filter {
    boolean shouldRunOnExecutor();
  }
}
