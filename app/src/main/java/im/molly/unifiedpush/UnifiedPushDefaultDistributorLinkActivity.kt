package im.molly.unifiedpush

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import org.thoughtcrime.securesms.BaseActivity
import org.signal.core.util.logging.Log
import org.unifiedpush.android.connector.LinkActivityHelper

class UnifiedPushDefaultDistributorLinkActivity : BaseActivity() {
  private val helper = LinkActivityHelper(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    runWhenScreenUnlocked { startLink() }
  }

  private fun startLink() {
    if (!helper.startLinkActivityForResult()) {
      Log.d(TAG, "No distributor with link activity found.")
      setResult(RESULT_OK)
      finish()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    runWhenScreenUnlocked { completeLink(requestCode, resultCode, data) }
  }

  private fun completeLink(requestCode: Int, resultCode: Int, data: Intent?) {
    if (helper.onLinkActivityResult(requestCode, resultCode, data)) {
      // The distributor is saved, you can request registrations with UnifiedPush.registerApp now
      Log.d(TAG, "Found a distributor with link activity found.")
      val intent = Intent().putExtra(KEY_FOUND, true)
      setResult(RESULT_OK, intent)
    } else {
      // An error occurred, consider no distributor found for the moment
      Log.d(TAG, "Found a distributor with link activity found but an error occurred.")
      setResult(RESULT_OK)
    }
    finish()
  }

  class Contract : ActivityResultContract<Unit, Boolean?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(context, UnifiedPushDefaultDistributorLinkActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean? {
      return intent?.let {
        intent.getBooleanExtra(KEY_FOUND, false)
      }
    }
  }

  companion object {
    private const val KEY_FOUND = "found"
    private const val TAG = "UnifiedPushDefaultDistributorLinkActivity"
  }
}