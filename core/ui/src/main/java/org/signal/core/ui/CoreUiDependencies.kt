/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.app.Application

object CoreUiDependencies {

  private lateinit var _application: Application
  private lateinit var _provider: Provider

  fun init(application: Application, provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  val application: Application
    get() = _application

  val backupBaseDirName: String
    get() = _provider.provideBackupBaseDirName()

  val isIncognitoKeyboardEnabled: Boolean
    get() = _provider.provideIsIncognitoKeyboardEnabled()

  val isScreenSecurityEnabled: Boolean
    get() = _provider.provideIsScreenSecurityEnabled()

  val forceSplitPane: Boolean
    get() = _provider.provideForceSplitPane()

  interface Provider {
    fun provideBackupBaseDirName(): String
    fun provideIsIncognitoKeyboardEnabled(): Boolean
    fun provideIsScreenSecurityEnabled(): Boolean
    fun provideForceSplitPane(): Boolean
  }
}
