package im.molly.lint.checks

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

import org.signal.lint.Registry as SignalRegistry

@Suppress("unused")
class Registry : IssueRegistry() {
  private val signalIssues = SignalRegistry().issues

  override val issues = signalIssues + listOf(
    BaseActivityDetector.ISSUE_DIRECT_SUBCLASS,
    BaseActivityDetector.ISSUE_SUPER_ON_CREATE_MISSING_READY,
  )

  override val api: Int = CURRENT_API

  override val minApi: Int = 14

  override val vendor = Vendor(vendorName = "Molly")
}
