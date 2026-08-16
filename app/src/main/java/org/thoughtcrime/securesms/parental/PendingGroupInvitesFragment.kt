package org.thoughtcrime.securesms.parental

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.thoughtcrime.securesms.R

/**
 * PIN-gated bottom sheet that lets a parent view and act on pending group invites.
 * Must only be shown after successful PIN verification via [ParentalPinDialog].
 */
class PendingGroupInvitesFragment : BottomSheetDialogFragment() {

  private val viewModel: PendingGroupInvitesViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val scrollView = ScrollView(requireContext())
    val root = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 48, 48, 48)
    }
    scrollView.addView(root)

    val title = TextView(requireContext()).apply {
      text = getString(R.string.parental_pending_invites)
      textSize = 18f
    }
    root.addView(title)

    viewModel.pendingInvites.observe(viewLifecycleOwner) { invites ->
      root.removeViews(1, root.childCount - 1)

      if (invites.isEmpty()) {
        root.addView(TextView(requireContext()).apply {
          text = getString(R.string.parental_no_pending_invites)
          setPadding(0, 24, 0, 0)
        })
        return@observe
      }

      invites.forEach { item ->
        val row = LinearLayout(requireContext()).apply {
          orientation = LinearLayout.HORIZONTAL
          setPadding(0, 16, 0, 16)
        }

        val name = TextView(requireContext()).apply {
          text = item.groupName
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val acceptBtn = Button(requireContext()).apply {
          text = getString(R.string.parental_invite_accept)
          setOnClickListener {
            isEnabled = false
            viewModel.acceptInvite(item) { reason ->
              requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Failed (${reason.name})", Toast.LENGTH_SHORT).show()
                isEnabled = true
              }
            }
          }
        }

        val declineBtn = Button(requireContext()).apply {
          text = getString(R.string.parental_invite_decline)
          setOnClickListener {
            isEnabled = false
            viewModel.declineInvite(item) { reason ->
              requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Failed (${reason.name})", Toast.LENGTH_SHORT).show()
                isEnabled = true
              }
            }
          }
        }

        row.addView(name)
        row.addView(acceptBtn)
        row.addView(declineBtn)
        root.addView(row)
      }
    }

    viewModel.load()
    return scrollView
  }

  companion object {
    fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
      PendingGroupInvitesFragment().show(fragmentManager, "pending_group_invites")
    }
  }
}
