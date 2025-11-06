package im.molly.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import im.molly.app.security.SecurityManager
import im.molly.app.ui.security.SecurityHUD
import kotlinx.coroutines.*
import org.thoughtcrime.securesms.R

/**
 * Extensions for integrating EMMA security features into Signal conversations
 *
 * Usage in ConversationActivity:
 *   - Call setupSecurityFeatures() in onCreate()
 *   - Security HUD will auto-show when threat level > 35%
 *   - Long-press conversation title for Intimate Protection toggle
 */

private var securityHUD: SecurityHUD? = null
private var hudScope: CoroutineScope? = null

fun View.setupEMMASecurityFeatures(threadId: Long) {
    val securityManager = SecurityManager.getInstance(context)

    if (!securityManager.isInitialized) {
        securityManager.initialize()
    }

    // Add Security HUD overlay
    addSecurityHUDOverlay()

    // Monitor threat level and show/hide HUD
    hudScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    hudScope?.launch {
        securityManager.threatLevel.collect { analysis ->
            analysis?.let {
                val shouldShow = (it.threatLevel >= 0.35f)
                securityHUD?.isVisible = shouldShow

                // Apply intimate protection if enabled
                if (securityManager.isIntimateProtectionEnabled(threadId)) {
                    // Force maximum security posture
                    securityHUD?.isVisible = true
                }
            }
        }
    }
}

fun View.addSecurityHUDOverlay() {
    val rootView = rootView as? ViewGroup ?: return

    if (securityHUD == null) {
        securityHUD = SecurityHUD(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
                marginStart = 16
                marginEnd = 16
            }
            isVisible = false
        }

        rootView.addView(securityHUD)
    }
}

fun View.showIntimateProtectionDialog(threadId: Long, threadTitle: String) {
    val securityManager = SecurityManager.getInstance(context)
    val isEnabled = securityManager.isIntimateProtectionEnabled(threadId)

    AlertDialog.Builder(context, R.style.Theme_EMMA_MilSpec)
        .setTitle(R.string.emma_intimate_protection)
        .setMessage(
            if (isEnabled) {
                "Intimate Protection is currently ACTIVE for \"$threadTitle\".\n\n" +
                "Maximum security countermeasures are applied:\n" +
                "• 200% chaos intensity\n" +
                "• 90% decoy operations\n" +
                "• Continuous memory scrambling\n" +
                "• Cache poisoning enabled\n" +
                "• Network obfuscation active\n\n" +
                "Disable?"
            } else {
                "Enable Intimate Protection for \"$threadTitle\"?\n\n" +
                "This will apply MAXIMUM security:\n" +
                "• Nuclear threat level (95-100%)\n" +
                "• 200% chaos intensity\n" +
                "• 90% decoy operations\n" +
                "• Continuous countermeasures\n" +
                "• Significant battery impact\n\n" +
                "Use only for highly sensitive conversations."
            }
        )
        .setPositiveButton(if (isEnabled) "Disable" else "Enable") { _, _ ->
            securityManager.enableIntimateProtection(threadId, !isEnabled)

            // Show confirmation
            android.widget.Toast.makeText(
                context,
                if (isEnabled) {
                    "Intimate Protection disabled"
                } else {
                    "Intimate Protection ACTIVATED - Maximum Security"
                },
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        .setNegativeButton("Cancel", null)
        .create()
        .apply {
            window?.setBackgroundDrawableResource(R.color.emma_black_secondary)
        }
        .show()
}

fun View.cleanupEMMASecurityFeatures() {
    hudScope?.cancel()
    hudScope = null

    securityHUD?.let {
        (it.parent as? ViewGroup)?.removeView(it)
        securityHUD = null
    }
}

/**
 * Extension for adding translation button to message compose
 */
fun View.addTranslationButton(onTranslateClick: (String) -> Unit) {
    // Implementation would add button to compose UI
    // When clicked, calls translation engine with message text
    // Returns translated text to replace in compose field
}
