package im.molly.lint.checks

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

@Suppress("unused")
class Registry : IssueRegistry() {
  private val signalIssues = org.signal.lint.Registry().issues

  override val issues = signalIssues + listOf()

  override val api: Int = CURRENT_API

  override val minApi: Int = 14

  override val vendor = Vendor(vendorName = "Molly")
}
