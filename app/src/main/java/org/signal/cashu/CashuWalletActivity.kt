package org.signal.cashu

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.signal.cashu.adapter.TransactionAdapter
import org.signal.cashu.model.Transaction
import org.signal.cashu.service.CashuService
import org.signal.cashu.service.DefaultCashuService
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*

class CashuWalletActivity : AppCompatActivity() {
    private lateinit var cashuService: CashuService
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cashu_wallet)

        // Initialize Cashu service
        cashuService = DefaultCashuService(OkHttpClient())

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup transactions RecyclerView
        transactionAdapter = TransactionAdapter(transactions)
        findViewById<RecyclerView>(R.id.transactions_recycler).apply {
            layoutManager = LinearLayoutManager(this@CashuWalletActivity)
            adapter = transactionAdapter
        }

        // Setup add mint button
        findViewById<MaterialButton>(R.id.add_mint_button).setOnClickListener {
            showAddMintDialog()
        }

        // Load transactions
        loadTransactions()
    }

    private fun showAddMintDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_mint, null)
        val mintUrlInput = dialogView.findViewById<TextInputEditText>(R.id.mint_url_input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_mint)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { dialog, _ ->
                val mintUrl = mintUrlInput.text.toString()
                if (mintUrl.isNotEmpty()) {
                    // TODO: Save mint URL and verify connection
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadTransactions() {
        // TODO: Load transactions from database
        // For now, just show some sample transactions
        transactions.addAll(listOf(
            Transaction(
                amount = 1000,
                description = "Received from Alice",
                date = Date()
            ),
            Transaction(
                amount = -500,
                description = "Sent to Bob",
                date = Date(System.currentTimeMillis() - 86400000)
            )
        ))
        transactionAdapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}