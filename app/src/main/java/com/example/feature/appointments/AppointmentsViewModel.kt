package com.example.feature.appointments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppointmentsViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    val appointments: StateFlow<List<Appointment>> = repository.allAppointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<AppCalendarEvent>> = repository.allCalendarEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAppointment(appointment: Appointment, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertAppointment(appointment)
            onComplete?.invoke(id)
        }
    }

    fun updateAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.updateAppointment(appointment)
        }
    }

    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.deleteAppointment(appointment)
        }
    }

    fun updateAppointmentStatus(appointmentId: Long, newStatus: String) {
        viewModelScope.launch {
            val currentList = appointments.value
            val existing = currentList.firstOrNull { it.id == appointmentId } ?: return@launch
            val updated = existing.copy(
                status = newStatus,
                isCompleted = (newStatus == "COMPLETED")
            )
            repository.updateAppointment(updated)
        }
    }
}
