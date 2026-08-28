package com.example.feature.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.feature.auth.UserRole
import com.example.security.SecurityUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val repository: DentalRepository = DentalRepository(AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("dental_pro_prefs", Context.MODE_PRIVATE)

    val clinicSettings: StateFlow<ClinicSettings?> = repository.clinicSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentUserRole = MutableStateFlow(
        UserRole.fromCode(prefs.getString("current_user_role", UserRole.DOCTOR.code) ?: UserRole.DOCTOR.code)
    )

    val baseCurrency = MutableStateFlow(prefs.getString("base_currency", "TOMAN") ?: "TOMAN")
    val currencyConversionRate = MutableStateFlow(prefs.getFloat("currency_rate_to_toman", 1.0f))

    fun setUserRole(role: UserRole) {
        currentUserRole.value = role
        prefs.edit().putString("current_user_role", role.code).apply()
    }

    fun setCurrencySettings(base: String, rateToToman: Float) {
        baseCurrency.value = base
        currencyConversionRate.value = rateToToman
        prefs.edit()
            .putString("base_currency", base)
            .putFloat("currency_rate_to_toman", rateToToman)
            .apply()
    }

    fun updateSettings(settings: ClinicSettings) {
        viewModelScope.launch {
            repository.insertSettings(settings)
        }
    }
}
