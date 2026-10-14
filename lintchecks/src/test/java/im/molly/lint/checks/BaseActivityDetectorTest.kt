package im.molly.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class BaseActivityDetectorTest {
  @Test
  fun javaExceptionDoesNotExemptOtherActivities() {
    TestLintTask.lint()
      .files(
        baseActivity,
        suppressLint,
        java(
          """
          package example;
          import android.annotation.SuppressLint;
          import org.thoughtcrime.securesms.BaseActivity;
          @SuppressLint("BaseActivitySubclass")
          class SetupPhotoPicker extends BaseActivity {}
          class OrdinaryActivity extends BaseActivity {}
          """.trimIndent()
        )
      )
      .issues(BaseActivityDetector.ISSUE_DIRECT_SUBCLASS)
      .allowMissingSdk()
      .run()
      .expectErrorCount(1)
      .expectContains("class OrdinaryActivity extends BaseActivity")
  }

  @Test
  fun kotlinExceptionDoesNotExemptOtherActivities() {
    TestLintTask.lint()
      .files(
        baseActivity,
        suppressLint,
        kotlin(
          """
          package example
          import android.annotation.SuppressLint
          import org.thoughtcrime.securesms.BaseActivity
          @SuppressLint("BaseActivitySubclass")
          class AuthenticationGate : BaseActivity()
          class OrdinaryActivity : BaseActivity()
          """.trimIndent()
        )
      )
      .issues(BaseActivityDetector.ISSUE_DIRECT_SUBCLASS)
      .allowMissingSdk()
      .run()
      .expectErrorCount(1)
      .expectContains("class OrdinaryActivity : BaseActivity()")
  }

  @Test
  fun passphraseProtectedActivitiesRemainAllowed() {
    TestLintTask.lint()
      .files(
        baseActivity,
        passphraseActivity,
        passphraseRequiredActivity,
        bundle,
        java(
          """
          package example;
          import android.os.Bundle;
          import org.thoughtcrime.securesms.PassphraseRequiredActivity;
          class OrdinaryActivity extends PassphraseRequiredActivity {
            @Override protected void onCreate(Bundle state, boolean ready) {
              super.onCreate(state, ready);
            }
          }
          """.trimIndent()
        )
      )
      .issues(BaseActivityDetector.ISSUE_DIRECT_SUBCLASS, BaseActivityDetector.ISSUE_SUPER_ON_CREATE_MISSING_READY)
      .allowMissingSdk()
      .run()
      .expectClean()
  }

  @Test
  fun screenLockExceptionDoesNotSuppressMissingReadyArgument() {
    TestLintTask.lint()
      .files(
        baseActivity,
        passphraseActivity,
        passphraseRequiredActivity,
        bundle,
        suppressLint,
        java(
          """
          package example;
          import android.annotation.SuppressLint;
          import android.os.Bundle;
          import org.thoughtcrime.securesms.PassphraseRequiredActivity;
          @SuppressLint("BaseActivitySubclass")
          class OrdinaryActivity extends PassphraseRequiredActivity {
            @Override protected void onCreate(Bundle state, boolean ready) {
              super.onCreate(state);
            }
          }
          """.trimIndent()
        )
      )
      .issues(BaseActivityDetector.ISSUE_DIRECT_SUBCLASS, BaseActivityDetector.ISSUE_SUPER_ON_CREATE_MISSING_READY)
      .allowMissingSdk()
      .run()
      .expectErrorCount(1)
      .expectContains("[SuperOnCreateMissingReady]")
  }

  companion object {
    private val baseActivity = java(
      """
      package org.thoughtcrime.securesms;
      public abstract class BaseActivity {}
      """.trimIndent()
    )

    private val passphraseActivity = java(
      """
      package org.thoughtcrime.securesms;
      public abstract class PassphraseActivity extends BaseActivity {}
      """.trimIndent()
    )

    private val passphraseRequiredActivity = java(
      """
      package org.thoughtcrime.securesms;
      import android.os.Bundle;
      public abstract class PassphraseRequiredActivity extends PassphraseActivity {
        protected final void onCreate(Bundle state) {}
        protected void onCreate(Bundle state, boolean ready) {}
      }
      """.trimIndent()
    )

    private val bundle = java(
      """
      package android.os;
      public class Bundle {}
      """.trimIndent()
    )

    private val suppressLint = java(
      """
      package android.annotation;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.TYPE, ElementType.METHOD})
      public @interface SuppressLint { String[] value(); }
      """.trimIndent()
    )
  }
}
