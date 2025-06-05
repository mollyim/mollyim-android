package org.thoughtcrime.securesms.settings.export

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
// import org.thoughtcrime.securesms.R // Conceptual R file
import org.thoughtcrime.securesms.export.ui.ManageScheduledExportsFragment

class ChatExportSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val PREF_CHAT_EXPORT_API_URL = "pref_chat_export_api_url"
        const val PREF_CHAT_EXPORT_API_TOKEN = "pref_chat_export_api_token"
        const val PREF_MANAGE_SCHEDULED_EXPORTS = "pref_manage_scheduled_exports"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // In a real app, you would inflate from XML:
        // setPreferencesFromResource(R.xml.chat_export_preferences, rootKey)
        // For this environment, preferences are created programmatically.
        createPreferencesProgrammatically()
    }

    private fun createPreferencesProgrammatically() {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // API Endpoint Configuration Category
        val apiCategory = PreferenceCategory(context).apply {
            title = "API Endpoint Configuration"
            // Use a unique key for the category if needed for ordering or manipulation, e.g.
            // key = "api_endpoint_category"
        }
        screen.addPreference(apiCategory)

        val apiUrlPref = EditTextPreference(context).apply {
            key = PREF_CHAT_EXPORT_API_URL
            title = "Default API Endpoint URL"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter API URL"
            // Optionally, set input type for URI if EditTextPreference supported it directly for its dialog
            // this.setOnBindEditTextListener { editText -> editText.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        }
        apiCategory.addPreference(apiUrlPref)

        val apiTokenPref = EditTextPreference(context).apply {
            key = PREF_CHAT_EXPORT_API_TOKEN
            title = "Default API Token (Optional)"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter API Token"
            // For sensitive data, a custom Preference with proper secure input and storage (e.g., Keystore) is better.
            // EditTextPreference itself doesn't have a direct "password" attribute for its dialog's EditText.
            // One common workaround is to use setOnBindEditTextListener to set inputType.
            setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        apiCategory.addPreference(apiTokenPref)

        // Scheduled Export Management Category
        val scheduleCategory = PreferenceCategory(context).apply {
            title = "Scheduled Export Management"
            // key = "schedule_management_category"
        }
        screen.addPreference(scheduleCategory)

        val manageScheduledExportsPref = Preference(context).apply {
            key = PREF_MANAGE_SCHEDULED_EXPORTS
            title = "Manage Scheduled Exports"
            summary = "View or cancel your scheduled chat exports"
            setOnPreferenceClickListener {
                // Ensure the activity hosting this fragment has a container with a known ID.
                // Using android.R.id.content is a fallback and might not always be the desired container.
                // A dedicated FrameLayout with R.id.settings_container in the Activity's layout is better.
                // For this environment, we'll assume a common scenario where the fragment is hosted in a container
                // that is the first child of android.R.id.content, or a more specific ID if known.
                // This is a fragile way to get a container ID, a dedicated ID is always best.
                var containerId = View.NO_ID
                val activityView = requireActivity().findViewById<View>(android.R.id.content)
                if (activityView is ViewGroup && activityView.childCount > 0) {
                    val firstChild = activityView.getChildAt(0)
                    if (firstChild.id != View.NO_ID) {
                         containerId = firstChild.id
                    }
                }

                if (containerId != View.NO_ID) { // Check if a valid ID was found
                    parentFragmentManager.commit {
                        replace(containerId, ManageScheduledExportsFragment())
                        addToBackStack(null)
                    }
                } else {
                    // Fallback or error handling if container ID can't be found or is not suitable.
                    // Try android.R.id.content directly if no child with ID was found, though this might not be ideal.
                    try {
                        parentFragmentManager.commit {
                            replace(android.R.id.content, ManageScheduledExportsFragment())
                            addToBackStack(null)
                        }
                    } catch (e: IllegalStateException) {
                         Toast.makeText(context, "Error: Cannot navigate (suitable container not found).", Toast.LENGTH_LONG).show()
                    }
                }
                true
            }
        }
        scheduleCategory.addPreference(manageScheduledExportsPref)

        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Example: React to changes if needed, e.g., for validation or complex summaries
        // if (key == PREF_CHAT_EXPORT_API_URL) {
        //     val preference: EditTextPreference? = findPreference(key)
        //     // Perform validation or update other UI based on the new value
        // }
    }
}
