package com.example.feature.rx

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RxViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    val prescriptions: StateFlow<List<Prescription>> = repository.allPrescriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<TreatmentTemplate>> = repository.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun prescriptionsForPatient(patientId: Long): Flow<List<Prescription>> =
        repository.prescriptionsForPatient(patientId)

    fun prescriptionsForTreatment(treatmentId: Long): Flow<List<Prescription>> =
        repository.prescriptionsForTreatment(treatmentId)

    fun addPrescription(prescription: Prescription, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertPrescription(prescription)
            onComplete?.invoke(id)
        }
    }

    fun deletePrescription(prescription: Prescription) {
        viewModelScope.launch {
            repository.deletePrescription(prescription)
        }
    }

    fun addTemplate(template: TreatmentTemplate) {
        viewModelScope.launch {
            repository.insertTemplate(template)
        }
    }

    fun deleteTemplate(template: TreatmentTemplate) {
        viewModelScope.launch {
            repository.deleteTemplate(template)
        }
    }
}
