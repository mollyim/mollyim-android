package org.signal.cashu.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.signal.cashu.R
import org.signal.cashu.model.Transaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(private val transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val amountText: TextView = view.findViewById(R.id.amount_text)
        val dateText: TextView = view.findViewById(R.id.date_text)
        val descriptionText: TextView = view.findViewById(R.id.description_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactions[position]

        // Format amount with sign
        val amountText = if (transaction.amount > 0) {
            "+${transaction.amount} sats"
        } else {
            "${transaction.amount} sats"
        }
        holder.amountText.text = amountText

        // Format date
        holder.dateText.text = dateFormat.format(transaction.date)

        // Set description
        holder.descriptionText.text = transaction.description
    }

    override fun getItemCount() = transactions.size
}