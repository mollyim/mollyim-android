package org.signal.cashu.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.signal.cashu.R
import org.signal.cashu.databinding.FragmentChatBinding
import org.signal.cashu.viewmodel.ChatViewModel

@AndroidEntryPoint
class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.startsWith("cashu:")) {
                showReceivePaymentDialog(message)
            } else {
                viewModel.sendMessage(message)
            }
            binding.messageInput.text.clear()
        }

        binding.attachButton.setOnClickListener {
            showSendPaymentDialog()
        }
    }

    private fun showSendPaymentDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Payment")
            .setView(R.layout.dialog_send_payment)
            .setPositiveButton("Send") { dialog, _ ->
                val amount = binding.amountInput.text.toString().toLongOrNull()
                val recipient = binding.recipientInput.text.toString()

                if (amount != null && recipient.isNotEmpty()) {
                    viewModel.sendPayment(amount, recipient)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showReceivePaymentDialog(token: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Receive Payment")
            .setMessage("Do you want to receive this payment?")
            .setPositiveButton("Yes") { dialog, _ ->
                viewModel.receivePayment(token)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}