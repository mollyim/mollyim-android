package org.signal.cashu

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.cashu.service.CashuService
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var cashuService: CashuService

    override fun onCreate() {
        super.onCreate()

        // Initialize default mint URL
        CoroutineScope(Dispatchers.IO).launch {
            cashuService.addMintUrl(
                url = "https://your-cashu-mint-url.com",
                name = "Default Mint",
                isDefault = true
            )
        }
    }
}