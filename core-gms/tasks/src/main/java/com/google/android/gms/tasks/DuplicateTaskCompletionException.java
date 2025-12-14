package com.google.android.gms.tasks;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Keep
public final class DuplicateTaskCompletionException extends IllegalStateException {

  private DuplicateTaskCompletionException(String message, @Nullable Exception cause) {
    super(message, cause);
  }

  @NonNull
  public static IllegalStateException of(@NonNull Task<?> task) {
    if (!task.isComplete()) {
      return new IllegalStateException("Task is not complete.");
    }

    final String description;
    if (task.getException() != null) {
      description = "failure";
    } else if (task.isSuccessful()) {
      description = "success";
    } else if (task.isCanceled()) {
      description = "cancellation";
    } else {
      description = "unknown state";
    }

    return new DuplicateTaskCompletionException(
        "Already completed: " + description, task.getException()
    );
  }
}
