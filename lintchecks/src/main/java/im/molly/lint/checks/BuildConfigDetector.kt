/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

class BuildConfigDetector : Detector(), Detector.UastScanner {

  companion object Issues {
    val VERSION_NAME_USAGE: Issue = Issue.create(
      id = "VersionNameUsage",
      briefDescription = "Avoid BuildConfig.VERSION_NAME: choose app vs compatibility version explicitly",
      explanation =
        """
        Molly tracks two different version identities:
        - App version (`BuildConfig.VERSION_NAME`)
        - Signal compatibility version (`BuildConfig.SIGNAL_CANONICAL_VERSION_NAME`)
        
        In upstream Signal, `BuildConfig.VERSION_NAME` effectively means the Signal compatibility version.
        In this project it means the app version, so merges can compile but introduce a semantic regression.
        
        Use `ApkInfo.versionName` or `ApkInfo.signalCanonicalVersionName` to make the intended version explicit.
        """,
      category = Category.CORRECTNESS,
      severity = Severity.ERROR,
      implementation = JAVA_SCOPE,
    )
  }

  override fun getApplicableReferenceNames() = listOf("VERSION_NAME")

  override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
    if (isBuildConfigVersionName(referenced)) {
      context.report(
        issue = VERSION_NAME_USAGE,
        scope = reference,
        location = context.getLocation(reference),
        message = "BuildConfig.VERSION_NAME is ambiguous here. Use ApkInfo.* instead.",
      )
    }
  }

  private fun isBuildConfigVersionName(referenced: PsiElement): Boolean {
    val field = referenced as? PsiField ?: return false
    val containingClass = field.containingClass ?: return false

    return containingClass.name == "BuildConfig"
  }
}

private val JAVA_SCOPE = Implementation(BuildConfigDetector::class.java, Scope.JAVA_FILE_SCOPE)
