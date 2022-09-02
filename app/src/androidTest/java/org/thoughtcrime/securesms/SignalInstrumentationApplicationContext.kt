package org.thoughtcrime.securesms

import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider

/**
 * Application context for running instrumentation tests (aka androidTests).
 */
class SignalInstrumentationApplicationContext : ApplicationContext() {
  override fun onCreate() {
    MasterSecretUtil.generateMasterSecret(this, MasterSecretUtil.getUnencryptedPassphrase())
    super.onCreate()
  }

  override fun initializeAppDependencies() {
    val default = ApplicationDependencyProvider(this)
    ApplicationDependencies.init(this, InstrumentationApplicationDependencyProvider(this, default))
  }
}
