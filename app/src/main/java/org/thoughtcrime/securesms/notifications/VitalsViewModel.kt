/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 * View model for checking for various app vitals, like slow notifications and crashes.
 */
class VitalsViewModel(private val context: Application) : AndroidViewModel(context) {

  private val checkSubject = BehaviorSubject.create<Unit>()

  val vitalsState: Observable<State>

  init {
    vitalsState = checkSubject
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .throttleFirst(1, TimeUnit.MINUTES)
      .switchMapSingle {
        checkHeuristics()
      }
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun checkSlowNotificationHeuristics() {
    checkSubject.onNext(Unit)
  }

  private fun checkHeuristics(): Single<State> {
    return Single.fromCallable {
      var state = State.NONE
      when (SlowNotificationHeuristics.showCondition()) {
        DeviceSpecificNotificationConfig.ShowCondition.ALWAYS -> {
          if (SlowNotificationHeuristics.shouldShowDialog()) {
            state = State.PROMPT_SPECIFIC_BATTERY_SAVER_DIALOG
          }
        }
        DeviceSpecificNotificationConfig.ShowCondition.HAS_BATTERY_OPTIMIZATION_ON -> {
          if (SlowNotificationHeuristics.isBatteryOptimizationsOn()) {
            state = State.PROMPT_SPECIFIC_BATTERY_SAVER_DIALOG
          }
        }
        DeviceSpecificNotificationConfig.ShowCondition.HAS_SLOW_NOTIFICATIONS -> {
          if (SlowNotificationHeuristics.isHavingDelayedNotifications() && SlowNotificationHeuristics.shouldPromptBatterySaver()) {
            state = State.PROMPT_GENERAL_BATTERY_SAVER_DIALOG
          }
        }
      }

      return@fromCallable state
    }.subscribeOn(Schedulers.io())
  }

  enum class State {
    NONE,
    PROMPT_SPECIFIC_BATTERY_SAVER_DIALOG,
    PROMPT_GENERAL_BATTERY_SAVER_DIALOG,
  }
}
