package com.google.android.gms.tasks;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutionException;

public final class Tasks {
  public static <TResult> TResult await(@NonNull Task<TResult> task) throws ExecutionException, InterruptedException {
    return task.getResult();
  }
}
