package org.thoughtcrime.securesms.screenlock

/** Detects removal of the previous task even when no running service received its callback. */
internal class ScreenLockTaskTracker {
  private var previousTask: Int? = null

  @Synchronized
  fun entering(taskId: Int, existingTasks: () -> Set<Int>): Boolean {
    if (taskId < 0) return false
    val previous = previousTask
    previousTask = taskId
    return previous != null && previous != taskId && previous !in existingTasks()
  }

  @Synchronized
  fun removed(existingTasks: () -> Set<Int>): Boolean {
    val previous = previousTask ?: return false
    if (previous in existingTasks()) return false
    previousTask = null
    return true
  }
}
