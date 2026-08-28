package com.example.feature.billing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BillingViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    val payments: StateFlow<List<Payment>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clinicExpenses: StateFlow<List<ClinicExpense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expensePayments: StateFlow<List<ExpensePayment>> = repository.allExpensePayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankAccounts: StateFlow<List<BankAccount>> = repository.allBankAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalTransactions: StateFlow<List<PersonalTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fixedIncomes: StateFlow<List<FixedIncome>> = repository.allFixedIncomes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vendorInvoices: StateFlow<List<VendorInvoice>> = repository.allVendorInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPayment(payment: Payment) {
        viewModelScope.launch {
            repository.insertPayment(payment)
        }
    }

    fun updatePayment(payment: Payment) {
        viewModelScope.launch {
            repository.updatePayment(payment)
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
        }
    }

    fun addExpense(expense: ClinicExpense) {
        viewModelScope.launch {
            repository.insertExpense(expense)
        }
    }

    fun addExpensePayment(payment: ExpensePayment) {
        viewModelScope.launch {
            repository.insertExpensePayment(payment)
        }
    }

    fun updateInstallmentStatus(installmentId: Long, isPaid: Boolean) {
        viewModelScope.launch {
            val inst = installments.value.firstOrNull { it.id == installmentId } ?: return@launch
            val updated = inst.copy(
                isPaid = isPaid,
                paidDate = if (isPaid) System.currentTimeMillis() else null
            )
            repository.updateInstallment(updated)
        }
    }
}
