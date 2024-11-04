package im.molly.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass

class BaseActivityDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val JAVA_IMPL = Implementation(BaseActivityDetector::class.java, Scope.JAVA_FILE_SCOPE)

    val ISSUE_DIRECT_SUBCLASS = Issue.create(
      id = "BaseActivitySubclass",
      briefDescription = "Avoid extending BaseActivity directly",
      explanation =
        """
        Activities should extend `PassphraseRequiredActivity` instead of `BaseActivity` \
        to ensure passphrase lock initialization requirements.
        """,
      category = Category.CORRECTNESS,
      severity = Severity.ERROR,
      implementation = JAVA_IMPL,
    )

    val ISSUE_SUPER_ON_CREATE_MISSING_READY = Issue.create(
      id = "SuperOnCreateMissingReady",
      briefDescription = "Missing `ready` argument in `super.onCreate` call",
      explanation =
        """
        When overriding `onCreate` in classes extending `PassphraseRequiredActivity`, \
        make sure to include the `ready` argument in the call to `super.onCreate(savedInstanceState, ready)`.
        """,
      category = Category.CORRECTNESS,
      severity = Severity.ERROR,
      implementation = JAVA_IMPL,
    )
  }

  override fun applicableSuperClasses() = listOf(BASE_ACTIVITY_CLASS_NAME)

  override fun getApplicableMethodNames() = listOf("onCreate")

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.name == "PassphraseActivity") {
      return
    }
    val superCls = declaration.javaPsi.superClass?.qualifiedName
    if (superCls == BASE_ACTIVITY_CLASS_NAME) {
      context.report(
        issue = ISSUE_DIRECT_SUBCLASS,
        scopeClass = declaration,
        location = context.getNameLocation(declaration),
        message = "This class should not extend `BaseActivity` directly. Use `PassphraseRequiredActivity` instead.",
      )
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (method.containingClass?.name == "PassphraseRequiredActivity" && evaluator.getParameterCount(method) == 1) {
      context.report(
        issue = ISSUE_SUPER_ON_CREATE_MISSING_READY,
        scope = node,
        location = context.getLocation(node),
        message = "Missing `ready` argument in call to `super.onCreate`.",
      )
    }
  }
}

private const val BASE_ACTIVITY_CLASS_NAME = "org.thoughtcrime.securesms.BaseActivity"
