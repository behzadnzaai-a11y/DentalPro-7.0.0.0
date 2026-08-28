package com.example.feature.patients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PatientsViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    val patients: StateFlow<List<Patient>> = repository.allPatients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTreatments: StateFlow<List<Treatment>> = repository.allTreatments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<Payment>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInstallments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<PatientDocument>> = repository.allPatientDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStages: StateFlow<List<TreatmentStage>> = repository.allTreatmentStages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTreatmentsForPatient(patientId: Long): Flow<List<Treatment>> =
        repository.getTreatmentsForPatient(patientId)

    fun getPaymentsForPatient(patientId: Long): Flow<List<Payment>> =
        repository.getPaymentsForPatient(patientId)

    fun getInstallmentsForPatient(patientId: Long): Flow<List<Installment>> =
        repository.getInstallmentsForPatient(patientId)

    fun getDocumentsForPatient(patientId: Long): Flow<List<PatientDocument>> =
        repository.getDocumentsForPatient(patientId)

    fun addPatient(patient: Patient, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertPatient(patient)
            onComplete?.invoke(id)
        }
    }

    fun updatePatient(patient: Patient) {
        viewModelScope.launch {
            repository.insertPatient(patient)
        }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch {
            repository.deletePatientCascading(patient)
        }
    }

    fun addDocument(patientId: Long, uri: String, docType: String, title: String = "", notes: String = "") {
        viewModelScope.launch {
            repository.insertPatientDocument(
                PatientDocument(
                    patientId = patientId,
                    uri = uri,
                    docType = docType,
                    title = title,
                    notes = notes
                )
            )
        }
    }

    fun deleteDocument(doc: PatientDocument) {
        viewModelScope.launch {
            repository.deletePatientDocument(doc)
        }
    }
}
