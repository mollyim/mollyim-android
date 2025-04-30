package org.signal.cashu.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.signal.cashu.R
import org.signal.cashu.databinding.ItemTransactionBinding
import org.signal.cashu.service.Transaction
import org.signal.cashu.service.TransactionStatus
import org.signal.cashu.service.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsAdapter : ListAdapter<Transaction, TransactionsAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(transaction: Transaction) {
            binding.apply {
                amountText.text = transaction.amount.toString()
                dateText.text = dateFormat.format(Date(transaction.timestamp))

                when (transaction.type) {
                    TransactionType.SEND -> {
                        typeText.text = "Sent"
                        typeText.setTextColor(root.context.getColor(R.color.error))
                    }
                    TransactionType.RECEIVE -> {
                        typeText.text = "Received"
                        typeText.setTextColor(root.context.getColor(R.color.success))
                    }
                }

                when (transaction.status) {
                    TransactionStatus.PENDING -> {
                        statusText.text = "Pending"
                        statusText.setTextColor(root.context.getColor(R.color.warning))
                    }
                    TransactionStatus.COMPLETED -> {
                        statusText.text = "Completed"
                        statusText.setTextColor(root.context.getColor(R.color.success))
                    }
                    TransactionStatus.FAILED -> {
                        statusText.text = "Failed"
                        statusText.setTextColor(root.context.getColor(R.color.error))
                    }
                }

                transaction.memo?.let { memo ->
                    memoText.text = memo
                    memoText.visibility = View.VISIBLE
                } ?: run {
                    memoText.visibility = View.GONE
                }
            }
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}