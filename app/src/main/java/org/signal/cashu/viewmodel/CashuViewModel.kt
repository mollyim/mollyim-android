package org.signal.cashu.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.signal.cashu.database.MintUrl
import org.signal.cashu.service.CashuService
import org.signal.cashu.service.Transaction
import org.signal.cashu.service.TransactionStatus

data class CashuUiState(
    val balance: Long = 0,
    val transactions: List<Transaction> = emptyList(),
    val mintUrls: List<MintUrl> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class CashuViewModel(
    private val cashuService: CashuService
) : ViewModel() {
    private val _uiState = MutableStateFlow(CashuUiState())
    val uiState: StateFlow<CashuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val balance = cashuService.getBalance()
            val transactions = cashuService.getTransactions()

            _uiState.value = _uiState.value.copy(
                balance = balance,
                transactions = transactions,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = e.message,
                isLoading = false
            )
        }
    }

    fun sendPayment(amount: Long, recipient: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val success = cashuService.sendPayment(amount, recipient)
                if (success) {
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to send payment",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun receivePayment(token: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val success = cashuService.receivePayment(token)
                if (success) {
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to receive payment",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}