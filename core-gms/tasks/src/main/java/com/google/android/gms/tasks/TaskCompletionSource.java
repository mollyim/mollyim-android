package com.google.android.gms.tasks;

import androidx.annotation.Keep;

import org.microg.gms.tasks.TaskImpl;

@Keep
public class TaskCompletionSource<TResult> {

  private final TaskImpl<TResult> task = new TaskImpl<>();

  public TaskCompletionSource() {}

  public TaskCompletionSource(CancellationToken token) {
    token.onCanceledRequested(() -> {
      try {
        task.cancel();
      } catch (DuplicateTaskCompletionException ignored) {}
    });
  }

  public Task<TResult> getTask() {
    return task;
  }

  public void setException(Exception e) {
    task.setException(e);
  }

  public void setResult(TResult result) {
    task.setResult(result);
  }

  public boolean trySetException(Exception e) {
    try {
      setException(e);
    } catch (DuplicateTaskCompletionException ignored) {
      return false;
    }
    return true;
  }

  public boolean trySetResult(TResult result) {
    try {
      setResult(result);
    } catch (DuplicateTaskCompletionException ignored) {
      return false;
    }
    return true;
  }
}
