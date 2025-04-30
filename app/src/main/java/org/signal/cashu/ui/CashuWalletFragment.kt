package org.signal.cashu.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.signal.cashu.R
import org.signal.cashu.databinding.FragmentCashuWalletBinding
import org.signal.cashu.viewmodel.CashuViewModel

class CashuWalletFragment : Fragment() {
    private var _binding: FragmentCashuWalletBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CashuViewModel by viewModels()
    private val transactionsAdapter = TransactionsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCashuWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            showSendDialog()
        }

        binding.receiveButton.setOnClickListener {
            showReceiveDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.balanceText.text = state.balance.toString()
                transactionsAdapter.submitList(state.transactions)

                binding.progressIndicator.visibility = if (state.isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                state.error?.let { error ->
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                        .setAction("Dismiss") { viewModel.clearError() }
                        .show()
                }
            }
        }
    }

    private fun showSendDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Payment")
            .setView(R.layout.dialog_send_payment)
            .setPositiveButton("Send") { dialog, _ ->
                // TODO: Implement send payment logic
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showReceiveDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Receive Payment")
            .setView(R.layout.dialog_receive_payment)
            .setPositiveButton("Generate") { dialog, _ ->
                // TODO: Implement receive payment logic
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}