package kotlinx.coroutines.tasks

import com.google.android.gms.tasks.Task

fun <T> Task<T>.await(): T = result as T
