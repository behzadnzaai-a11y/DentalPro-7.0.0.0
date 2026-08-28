package com.example.feature.lab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LabViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    val labOrders: StateFlow<List<LabOrder>> = repository.allLabOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labServices: StateFlow<List<LabService>> = repository.allLabServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLabOrder(order: LabOrder) {
        viewModelScope.launch {
            repository.insertLabOrder(order)
        }
    }

    fun updateLabOrderStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            val order = labOrders.value.firstOrNull { it.id == orderId } ?: return@launch
            repository.insertLabOrder(order.copy(status = newStatus))
        }
    }

    fun deleteLabOrder(order: LabOrder) {
        viewModelScope.launch {
            repository.deleteLabOrder(order)
        }
    }

    fun addLabService(service: LabService) {
        viewModelScope.launch {
            repository.insertLabService(service)
        }
    }

    fun deleteLabService(service: LabService) {
        viewModelScope.launch {
            repository.deleteLabService(service)
        }
    }
}
