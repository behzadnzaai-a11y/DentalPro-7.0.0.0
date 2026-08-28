package com.example.ui

import android.util.Log
import com.example.security.SecurityUtils
import com.example.security.SecureSecretStore
import com.example.ui.screens.toEnglishLong
import com.example.ui.screens.parseStagesList

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

import android.content.Context


data class BackupSelectionConfig(
    val includePatients: Boolean = true,
    val includeTreatments: Boolean = true,
    val includePayments: Boolean = true,
    val includeAppointments: Boolean = true,
    val includeInstallments: Boolean = true,
    val includeAccounting: Boolean = true,
    val includeSuppliersLab: Boolean = true,
    val includeSettingsTemplates: Boolean = true
)

data class SmsParseResult(
    val amount: Long,
    val detectedAccount: BankAccount?,
    val originalText: String,
    val isDeposit: Boolean
)

/**
 * یک سطر تخصیص مبلغ در پاپ‌آپ ثبت تراکنش پیامک بانکی (واریز/برداشت).
 * kind: "PATIENT" | "MISC_INCOME" | "CLINIC_EXPENSE" | "PERSONAL_SUB" | "PERSON"
 */
data class BankSmsAllocation(
    val kind: String,
    val targetId: Long? = null,
    val targetName: String = "",
    val amount: Long,
    val description: String = ""
)

data class CloudBackupRecord(
    val id: String,
    val timestamp: Long,
    val deviceName: String,
    val recordSummary: String,
    val jsonContent: String,
    val fileName: String = "",
    val isAutoSync: Boolean = false
)

class DentalViewModel(application: Application) : AndroidViewModel(application) {
    val database = AppDatabase.getDatabase(application)
    val appContext = application.applicationContext
    private val repository = DentalRepository(database)
    var syncEngine = com.example.sync.SyncEngine(false, appContext, repository, database)

    /** قفل همگام‌سازی بوت‌استرپ — از اجرای همزمانِ چند pullAll در اثر رویدادهای گوناگون لاگین
     *  (بازگشت از OAuth CustomTab + LaunchedEffect auto-login + دکمه‌ی لاگین) جلوگیری می‌کند. */
    private val bootstrapMutex = Mutex()
    @Volatile private var bootstrapCompletedForAccount: String? = null

    val lastSyncedAt: StateFlow<Long?> get() = syncEngine.lastSyncedAt
    val lastSyncError: StateFlow<String?> get() = syncEngine.lastError
    val pendingSyncOpsCount: StateFlow<Int> = repository.pendingSyncOpsCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ------------------------------------------------------------------
    // بروزرسانی درون‌برنامه‌ای از طریق سرور (جدول app_updates در Supabase)
    // نوع بروزرسانی از ارقام نسخه تشخیص داده می‌شود:
    //   MAJOR (رقم اول، 6→7): اجباری — دیالوگ غیرقابل‌رد
    //   MINOR (رقم وسط، 3→4): اختیاری — اعلان و دیالوگ در هر بار اجرا + تایید کاربر
    //   PATCH (رقم آخر، 1→2): بهبود/رفع باگ — فقط اعلان متنی خود-حذف در استارت،
    //                          حداکثر روزی یک‌بار
    // ------------------------------------------------------------------
    /** نسخهٔ جدید MAJOR/MINOR اعلام‌شده از سرور (null = چیزی برای دیالوگ نیست) */
    val availableAppUpdate = MutableStateFlow<com.example.util.AppUpdateManager.AppUpdateInfo?>(null)

    /** نسخهٔ جدید PATCH — فقط بنر متنی خود-حذف‌شونده در استارت اپ (روزی یک‌بار) */
    val patchUpdateNotice = MutableStateFlow<com.example.util.AppUpdateManager.AppUpdateInfo?>(null)

    fun dismissPatchUpdateNotice() {
        patchUpdateNotice.value = null
    }

    /**
     * چک بروزرسانی از سرور و اعمال سیاست اعلان بر اساس نوع نسخه.
     */
    fun checkForAppUpdate(notify: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = com.example.util.AppUpdateManager.checkForUpdate(appContext) ?: return@launch
                when (info.updateType) {
                    com.example.util.AppUpdateManager.UpdateType.MAJOR -> {
                        // اجباری: دیالوگ غیرقابل‌رد + اعلان سیستمی در هر بار اجرا
                        availableAppUpdate.value = info
                        if (notify) {
                            com.example.reminders.showAppUpdateNotification(
                                appContext, info.versionName, info.changelog, mandatory = true
                            )
                        }
                    }
                    com.example.util.AppUpdateManager.UpdateType.MINOR -> {
                        // اختیاری: اعلان «هر بار» اجرا + دیالوگ با تایید/رد توسط کاربر
                        availableAppUpdate.value = info
                        if (notify) {
                            com.example.reminders.showAppUpdateNotification(
                                appContext, info.versionName, info.changelog, mandatory = false
                            )
                        }
                    }
                    com.example.util.AppUpdateManager.UpdateType.PATCH -> {
                        // بهبود/رفع باگ: بدون اعلان سیستمی و بدون دیالوگ —
                        // فقط بنر متنی خود-حذف در استارت اپ، حداکثر روزی یک‌بار
                        val gateKey = "patch_notice_last_day"
                        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                            .format(java.util.Date())
                        val versionKey = "patch_notice_last_version"
                        val lastDay = prefs.getString(gateKey, "") ?: ""
                        val lastVersion = prefs.getLong(versionKey, 0L)
                        if (lastDay != today || lastVersion != info.versionCode) {
                            patchUpdateNotice.value = info
                            prefs.edit()
                                .putString(gateKey, today)
                                .putLong(versionKey, info.versionCode)
                                .apply()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DentalViewModel", "checkForAppUpdate failed: ${e.message}")
            }
        }
    }

    init {
        // چک بروزرسانی با کمی تأخیر پس از اجرا (بدون مزاحمت برای بوت‌استرپ اپ)
        viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            checkForAppUpdate()
        }
    }
    
    private val prefs = application.getSharedPreferences("dental_pro_prefs", Context.MODE_PRIVATE)

    private fun loadOrMigrateSecret(name: String, legacyPreference: String, generateIfMissing: Boolean): String {
        SecureSecretStore.get(appContext, name)?.let { return it }
        val value = prefs.getString(legacyPreference, null)?.takeIf(String::isNotBlank)
            ?: if (generateIfMissing) SecurityUtils.generateRandomEncryptionKey() else ""
        if (value.isNotEmpty()) SecureSecretStore.put(appContext, name, value)
        prefs.edit().remove(legacyPreference).commit()
        return value
    }
    
    
    fun scanRecentSmsForBankTransactions(context: Context) {
        viewModelScope.launch {
            try {
                val cursor = context.contentResolver.query(
                    android.net.Uri.parse("content://sms/inbox"),
                    arrayOf("body", "date", "address"),
                    null,
                    null,
                    "date DESC LIMIT 20"
                )
                cursor?.use {
                    val bodyIndex = it.getColumnIndex("body")
                    while (it.moveToNext()) {
                        if (bodyIndex != -1) {
                            val body = it.getString(bodyIndex)
                            if (body.contains("واریز") || body.contains("انتقال") || body.contains("مبلغ")) {
                                incomingSmsFlow.emit(body)
                                break // Emit only the latest matching one for now
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        val incomingSmsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    }
    
    // UI Language State: "fa" (Persian) or "en" (English)
    val isPersian = MutableStateFlow(true)

    // Clinic Logo & Dashboard Occasions
    /**
     * Uploads a freshly picked local logo file immediately and stores the resulting fileId on
     * ClinicSettings.logoFileId — the logo's only source of truth (read via settings.logoFileId
     * + SmartImage, not a local path). SyncEngine.pushPending() still contains its existing
     * opportunistic upload fallback for a pre-update logoFileId that is still a raw local path;
     * that fallback is left as-is and simply becomes unnecessary going forward.
     */
    fun updateClinicLogo(localFilePath: String) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            isMediaUploading.value = true
            try {
                repository.insertSettings(settings.value.copy(logoFileId = localFilePath))
                val uploadedFileId = uploadLocalFileOrNull(localFilePath)
                if (uploadedFileId != null) {
                    repository.insertSettings(settings.value.copy(logoFileId = uploadedFileId))
                    com.example.ui.screens.deleteLocalStagingFileQuietly(localFilePath)
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) {
                        // Background sync will retry later
                    }
                }
            } finally {
                isMediaUploading.value = false
            }
        }
    }

    val showOccasions = MutableStateFlow(prefs.getBoolean("show_occasions", true))
    fun setShowOccasions(enabled: Boolean) {
        showOccasions.value = enabled
        prefs.edit().putBoolean("show_occasions", enabled).apply()
    }

    // UI Language State: "fa", "en", "ar", "tr", "az", "zh", "de"
    val selectedLanguage = MutableStateFlow(prefs.getString("app_language", "fa") ?: "fa")

    fun setLanguage(langCode: String) {
        val clean = langCode.lowercase().trim()
        selectedLanguage.value = clean
        isPersian.value = (clean == "fa")
        val supported = com.example.util.AppLocalization.SupportedLanguage.fromCode(clean)
        
        // Synchronize default calendar system based on language
        setCalendarType(supported.defaultCalendar)
        
        // Synchronize default currency based on language
        when (clean) {
            "fa" -> setCurrencyUnit("تومان", 1.0f)
            "ar" -> setCurrencyUnit("SAR", 1.0f)
            "tr" -> setCurrencyUnit("₺ TRY", 1.0f)
            "az" -> setCurrencyUnit("₼ AZN", 1.0f)
            "zh" -> setCurrencyUnit("¥ CNY", 1.0f)
            "de" -> setCurrencyUnit("€ EUR", 1.0f)
            "en" -> setCurrencyUnit("$ USD", 1.0f)
            else -> setCurrencyUnit("تومان", 1.0f)
        }

        prefs.edit().putString("app_language", clean).putBoolean("is_persian", clean == "fa").apply()
    }

    // User Roles & Granular Role Permissions
    val currentUserRole = MutableStateFlow(
        com.example.feature.auth.UserRole.fromCode(
            prefs.getString("current_user_role", com.example.feature.auth.UserRole.ASSISTANT.code)
                ?: com.example.feature.auth.UserRole.ASSISTANT.code
        ).let { stored ->
            // Doctor elevation is a process/session capability, never a durable login state.
            if (stored == com.example.feature.auth.UserRole.DOCTOR && prefs.getBoolean("pin_security_enabled", false)) {
                prefs.edit().putString("current_user_role", com.example.feature.auth.UserRole.ASSISTANT.code).commit()
                com.example.feature.auth.UserRole.ASSISTANT
            } else stored
        }
    )

    val assistantPermissions = MutableStateFlow(
        prefs.getInt("assistant_permissions", -1).let { mask ->
            if (mask >= 0) com.example.feature.auth.RolePermissions.fromBitMask(mask)
            else com.example.feature.auth.UserRole.defaultPermissionsFor(com.example.feature.auth.UserRole.ASSISTANT)
        }
    )

    fun hasModuleAccess(moduleKey: String): Boolean {
        val normalized = moduleKey.trim().lowercase(Locale.US)
        val knownModule = normalized in setOf(
            "dashboard", "patients", "appointments", "treatments", "clinical",
            "billing", "payments", "invoices", "expenses", "labs", "lab",
            "maintenance", "reports", "bank", "cheques", "settings", "vendors",
            "vendor_invoices", "personal", "templates", "calendar", "backup"
        )
        if (!knownModule) return false // Unknown authorization keys must fail closed for every role.

        val role = currentUserRole.value
        if (role == com.example.feature.auth.UserRole.DOCTOR) return true
        val p = assistantPermissions.value
        return when (normalized) {
            "dashboard" -> true
            "patients" -> p.canAccessPatients
            "appointments", "calendar" -> p.canAccessAppointments
            "treatments", "clinical", "templates" -> p.canAccessTreatments
            "billing", "payments", "invoices", "personal" -> p.canAccessFinancial
            "expenses" -> p.canAccessExpenses
            "labs", "lab" -> p.canAccessLabs
            "maintenance" -> p.canAccessMaintenance
            "reports" -> p.canAccessReports
            "bank" -> p.canAccessBankAccounts
            "cheques" -> p.canAccessCheques
            "settings" -> p.canAccessSettings
            "vendors", "vendor_invoices" -> p.canAccessFinancial
            "backup" -> false // Backup/restore is intentionally doctor-only.
            else -> false
        }
    }

    private fun authorizeDomain(
        moduleKey: String,
        action: String,
        isDelete: Boolean = false,
        doctorOnly: Boolean = false
    ): Boolean {
        val role = currentUserRole.value
        val permissions = when (role) {
            com.example.feature.auth.UserRole.DOCTOR -> com.example.feature.auth.UserRole.defaultPermissionsFor(role)
            com.example.feature.auth.UserRole.ASSISTANT -> assistantPermissions.value
        }
        val allowed = hasModuleAccess(moduleKey) &&
            (!isDelete || permissions.canDeleteRecords) &&
            (!doctorOnly || role == com.example.feature.auth.UserRole.DOCTOR)
        if (!allowed) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.recordAudit(
                        AuditEvent(
                            actorRole = role.code,
                            action = action,
                            entityType = moduleKey,
                            result = "DENIED",
                            details = "Authorization policy rejected the operation"
                        )
                    )
                }
            }
        }
        return allowed
    }

    fun updateRolePermission(role: com.example.feature.auth.UserRole, permissionKey: String, isGranted: Boolean) {
        if (!authorizeDomain("settings", "UPDATE_ROLE_PERMISSION", doctorOnly = true)) return
        if (role == com.example.feature.auth.UserRole.ASSISTANT) {
            val current = assistantPermissions.value
            val updated = when (permissionKey) {
                "patients" -> current.copy(canAccessPatients = isGranted)
                "appointments" -> current.copy(canAccessAppointments = isGranted)
                "treatments" -> current.copy(canAccessTreatments = isGranted)
                "financial" -> current.copy(canAccessFinancial = isGranted)
                "expenses" -> current.copy(canAccessExpenses = isGranted)
                "labs" -> current.copy(canAccessLabs = isGranted)
                "maintenance" -> current.copy(canAccessMaintenance = isGranted)
                "reports" -> current.copy(canAccessReports = isGranted)
                "bank" -> current.copy(canAccessBankAccounts = isGranted)
                "cheques" -> current.copy(canAccessCheques = isGranted)
                "settings" -> current.copy(canAccessSettings = isGranted)
                "delete" -> current.copy(canDeleteRecords = isGranted)
                else -> current
            }
            assistantPermissions.value = updated
            prefs.edit().putInt("assistant_permissions", updated.toBitMask()).apply()
        }
    }

    fun requestRoleSwitch(targetRole: com.example.feature.auth.UserRole, pin: String? = null): Boolean {
        val current = currentUserRole.value
        if (current == targetRole) return true

        if (hasPinConfigured(targetRole)) {
            if (pin == null || !verifyRolePin(targetRole, pin)) {
                return false
            }
        }
        currentUserRole.value = targetRole
        prefs.edit().putString("current_user_role", targetRole.code).apply()
        return true
    }

    fun setUserRole(role: com.example.feature.auth.UserRole) {
        if (!hasPinConfigured(role)) {
            currentUserRole.value = role
            prefs.edit().putString("current_user_role", role.code).apply()
        }
    }

    fun setCachedUserProfile(name: String, phone: String, email: String, doctorName: String = "", accountId: String = "") {
        val editor = prefs.edit()
            .putString("auth_user_name", name)
            .putString("auth_user_phone", phone)
            .putString("auth_user_email", email)
            .putString("auth_doctor_name", doctorName)
        if (accountId.isNotBlank()) editor.putString("auth_account_id", accountId)
        editor.apply()
    }

    /**
     * به صراحت اعلام می‌کند که اطلاعات پروفایل کاربر (نام، نام پزشک، شماره موبایل،
     * سمت) کامل شده و یا از سند «users» سرور دریافت شده است. بدون این پرچم، در
     * بازگشت از OAuth گوگل — که هنوز GoogleProfileSetupScreen نمایش داده نشده —
     * LaunchedEffect فقط با دیدنِ نام/ایمیل کش‌شده گمان می‌کرد پروفایل کامل است
     * و کاربر را یک‌راست به داشبورد می‌فرستاد (با نقش/شماره ناقص) و این به شکل
     * «رفت‌وبرگشت/لوپ لاگین گوگل» دیده می‌شد.
     */
    fun markProfileSetupComplete() {
        prefs.edit().putBoolean("auth_profile_setup_complete", true).apply()
    }

    fun isProfileSetupComplete(): Boolean = prefs.getBoolean("auth_profile_setup_complete", false)

    fun getCurrentUserDisplayName(): String {
        val name = prefs.getString("auth_user_name", "") ?: ""
        if (name.isNotBlank()) return name
        val doc = settings.value.doctorName
        return if (doc.isNotBlank() && doc != "دکتر") doc else ""
    }

    fun getCurrentDoctorName(): String {
        val docName = prefs.getString("auth_doctor_name", "") ?: ""
        if (docName.isNotBlank()) return docName
        return getCurrentUserDisplayName() // fallback
    }

    fun getCurrentUserPhone(): String {
        val phone = prefs.getString("auth_user_phone", "") ?: ""
        if (phone.isNotBlank()) return phone
        return settings.value.clinicPhone
    }

    fun getCurrentUserEmail(): String {
        return prefs.getString("auth_user_email", "") ?: ""
    }

    /** رفعِ باگِ «لاگین با ایمیل موجود در دیتابیس»: فقط تطبیق ایمیل کافی نیست — اگر شناسه‌ی
     * حساب هم کش شده باشد، باید دقیقاً با حساب فعلی یکی باشد تا پروفایلِ کش‌شده «معتبر» تلقی شود. */
    fun getCachedAccountId(): String {
        return prefs.getString("auth_account_id", "") ?: ""
    }

    // Currency Unit State & Conversion Rate
    val currencyUnit = MutableStateFlow(prefs.getString("currency_unit", "تومان") ?: "تومان")
    val currencyConversionRate = MutableStateFlow(prefs.getFloat("currency_conversion_rate", 1.0f))

    fun setCurrencyUnit(unit: String, rateToToman: Float = 1.0f) {
        currencyUnit.value = unit
        currencyConversionRate.value = rateToToman
        prefs.edit()
            .putString("currency_unit", unit)
            .putFloat("currency_conversion_rate", rateToToman)
            .apply()
    }

    fun clearAllClinicalData() {
        if (!authorizeDomain("settings", "CLEAR_ALL_DATA", isDelete = true, doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            // Room clears every table in one transaction; the former hand-written list omitted
            // related clinical and accounting tables and could leave partial data behind.
            repository.clearAllData()
            repository.insertSettings(ClinicSettings(themeName = "NEON_CARBON", isDarkMode = true))
            seedReferenceData()
            seedDefaultCalendarEvents()
            selectedPatientId.value = null
            repository.recordAudit(
                AuditEvent(
                    actorRole = currentUserRole.value.code,
                    action = "CLEAR_ALL_DATA",
                    entityType = "database",
                    result = "ALLOWED"
                )
            )
        }
    }

    // Security & PIN State (Salted PBKDF2 Hashing)
    val isPinSecurityEnabled = MutableStateFlow(prefs.getBoolean("pin_security_enabled", false))
    private val pinHash = MutableStateFlow(prefs.getString("pin_hash", "") ?: "")
    private val pinSalt = MutableStateFlow(prefs.getString("pin_salt", "") ?: "")
    val isAppUnlocked = MutableStateFlow(false)
    val isGuestMode = MutableStateFlow(false)

    // True while a photo/document/logo is being uploaded to cloud immediately after being
    // picked (see addPatientDocument, addPatient/addPatientFull, updatePatientPhoto,
    // updateClinicLogo) — UI can observe this to show an "uploading..." indicator.
    val isMediaUploading = MutableStateFlow(false)

    fun hasPinConfigured(role: com.example.feature.auth.UserRole): Boolean {
        if (role == com.example.feature.auth.UserRole.DOCTOR) {
            val hasDoctorHash = prefs.getString("pin_hash_DOCTOR", "")?.isNotBlank() == true || pinHash.value.isNotBlank()
            return isPinSecurityEnabled.value && hasDoctorHash
        }
        return prefs.getString("pin_hash_${role.code}", "")?.isNotBlank() == true
    }

    fun hasDoctorPinConfigured(): Boolean = hasPinConfigured(com.example.feature.auth.UserRole.DOCTOR)

    fun verifyRolePin(role: com.example.feature.auth.UserRole, pin: String): Boolean {
        val prefix = "${role.code.lowercase()}_pin"
        if (System.currentTimeMillis() < prefs.getLong("${prefix}_blocked_until", 0L)) return false
        val hash = prefs.getString("pin_hash_${role.code}", "")?.ifBlank {
            if (role == com.example.feature.auth.UserRole.DOCTOR) pinHash.value else ""
        } ?: ""
        val salt = prefs.getString("pin_salt_${role.code}", "")?.ifBlank {
            if (role == com.example.feature.auth.UserRole.DOCTOR) pinSalt.value else ""
        } ?: ""
        if (hash.isBlank() || salt.isBlank()) return false
        val success = SecurityUtils.verifyPin(pin, hash, salt)
        recordPinAttempt(prefix, success)
        if (success) upgradePinHashIfNeeded(pin, hash, salt, role.code)
        return success
    }

    fun verifyDoctorPin(pin: String): Boolean = verifyRolePin(com.example.feature.auth.UserRole.DOCTOR, pin)

    private fun recordPinAttempt(prefix: String, success: Boolean) {
        if (success) {
            prefs.edit()
                .remove("${prefix}_failed_attempts")
                .remove("${prefix}_blocked_until")
                .apply()
            return
        }
        val attempts = prefs.getInt("${prefix}_failed_attempts", 0) + 1
        val editor = prefs.edit().putInt("${prefix}_failed_attempts", attempts)
        if (attempts >= 5) {
            editor.putInt("${prefix}_failed_attempts", 0)
                .putLong("${prefix}_blocked_until", System.currentTimeMillis() + 5 * 60 * 1000L)
        }
        editor.apply()
    }

    fun configureRolePin(role: com.example.feature.auth.UserRole, pin: String) {
        require(pin.matches(Regex("\\d{4,12}"))) { "PIN must contain 4 to 12 digits" }
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPin(pin, salt)
        if (role == com.example.feature.auth.UserRole.DOCTOR) {
            isPinSecurityEnabled.value = true
            pinHash.value = hash
            pinSalt.value = salt
            prefs.edit()
                .putBoolean("pin_security_enabled", true)
                .putString("pin_hash_DOCTOR", hash)
                .putString("pin_salt_DOCTOR", salt)
                .remove("security_pin")
                .apply()
        } else {
            prefs.edit()
                .putString("pin_hash_${role.code}", hash)
                .putString("pin_salt_${role.code}", salt)
                .apply()
        }
        // Sync the hash/salt to ClinicSettings (same pattern as updateClinicSettings/
        // updateWorkingDays) so a second device, or a fresh install signed in to the same
        // clinic account, can unlock without the PIN having to be re-entered from scratch.
        if (role == com.example.feature.auth.UserRole.DOCTOR || role == com.example.feature.auth.UserRole.ASSISTANT) {
            viewModelScope.launch(Dispatchers.IO) {
                val current = settings.value
                val updated = if (role == com.example.feature.auth.UserRole.DOCTOR) {
                    current.copy(doctorPinHash = hash, doctorPinSalt = salt)
                } else {
                    current.copy(assistantPinHash = hash, assistantPinSalt = salt)
                }
                repository.insertSettings(updated)
                try {
                    syncEngine.pushPending()
                } catch (e: Exception) {
                    // Background sync will retry later
                }
            }
        }
    }

    fun configureDoctorPin(pin: String) = configureRolePin(com.example.feature.auth.UserRole.DOCTOR, pin)

    fun setPinSecurityEnabled(enabled: Boolean, pin: String) {
        val alreadyConfigured = hasDoctorPinConfigured()
        if (alreadyConfigured || !enabled) {
            if (!authorizeDomain("settings", "UPDATE_PIN_SECURITY", doctorOnly = true)) return
        }
        if (enabled) {
            // Routed through configureRolePin so the doctor PIN hash/salt also gets synced
            // to ClinicSettings (see section 3) instead of only ever living in local prefs.
            configureRolePin(com.example.feature.auth.UserRole.DOCTOR, pin)
        } else {
            isPinSecurityEnabled.value = false
            prefs.edit().putBoolean("pin_security_enabled", false).apply()
            isAppUnlocked.value = true
        }
    }

    fun setRolePin(role: com.example.feature.auth.UserRole, pin: String) {
        if (!authorizeDomain("settings", "UPDATE_PIN_SECURITY", doctorOnly = true)) return
        require(pin.matches(Regex("\\d{4,12}"))) { "PIN must contain 4 to 12 digits" }
        configureRolePin(role, pin)
    }

    fun unlockAppWithPin(enteredPin: String, role: com.example.feature.auth.UserRole = com.example.feature.auth.UserRole.DOCTOR): Boolean {
        if (System.currentTimeMillis() < prefs.getLong("app_pin_blocked_until", 0L)) return false
        var hash = prefs.getString("pin_hash_${role.code}", "")?.ifBlank {
            if (role == com.example.feature.auth.UserRole.DOCTOR) pinHash.value else ""
        } ?: ""
        var salt = prefs.getString("pin_salt_${role.code}", "")?.ifBlank {
            if (role == com.example.feature.auth.UserRole.DOCTOR) pinSalt.value else ""
        } ?: ""
        if (hash.isBlank() || salt.isBlank()) {
            // First unlock attempt on this device (e.g. a second phone signed in to the same
            // clinic account): fall back to the hash/salt pulled from the cloud copy of
            // ClinicSettings, same as the existing in-memory pinHash/pinSalt fallback above.
            val cloudSettings = settings.value
            val cloudHash = if (role == com.example.feature.auth.UserRole.DOCTOR) cloudSettings.doctorPinHash else cloudSettings.assistantPinHash
            val cloudSalt = if (role == com.example.feature.auth.UserRole.DOCTOR) cloudSettings.doctorPinSalt else cloudSettings.assistantPinSalt
            if (!cloudHash.isNullOrBlank() && !cloudSalt.isNullOrBlank()) {
                hash = cloudHash
                salt = cloudSalt
                prefs.edit()
                    .putString("pin_hash_${role.code}", hash)
                    .putString("pin_salt_${role.code}", salt)
                    .apply()
                if (role == com.example.feature.auth.UserRole.DOCTOR) {
                    isPinSecurityEnabled.value = true
                    pinHash.value = hash
                    pinSalt.value = salt
                    prefs.edit().putBoolean("pin_security_enabled", true).apply()
                }
            }
        }
        if (hash.isBlank() || salt.isBlank()) return false
        val isMatch = SecurityUtils.verifyPin(enteredPin, hash, salt)
        recordPinAttempt("app_pin", isMatch)
        if (isMatch) {
            upgradePinHashIfNeeded(enteredPin, hash, salt, role.code)
            currentUserRole.value = role
            prefs.edit().putString("current_user_role", role.code).apply()
            isAppUnlocked.value = true
        }
        return isMatch
    }

    private fun upgradePinHashIfNeeded(pin: String, previousHash: String, salt: String, roleCode: String = "DOCTOR") {
        if (!SecurityUtils.needsPinHashUpgrade(previousHash)) return
        val upgraded = SecurityUtils.hashPin(pin, salt)
        check(prefs.edit().putString("pin_hash_$roleCode", upgraded).commit()) { "Could not upgrade PIN protection" }
        if (roleCode == "DOCTOR") {
            pinHash.value = upgraded
        }
    }

    
    fun getSettings(): com.example.data.ClinicSettings {
        return settings.value
    }
    
    fun currentExportTheme(): com.example.reports.ExportThemeSpec {
        val palette = com.example.ui.theme.neonPaletteById(settings.value.themeName)
        return com.example.reports.ExportThemeSpec.fromPalette(palette, settings.value.globalFontScaleIndex)
    }

    fun lockApp() {
        // Always lock to lock screen if security is enabled.
        // Role will be chosen on unlock.
        if (isPinSecurityEnabled.value) {
            isAppUnlocked.value = false
        }
    }

    fun onAppBackgrounded() {
        prefs.edit().putLong("app_backgrounded_at", System.currentTimeMillis()).apply()
    }

    fun onAppForegrounded() {
        val backgroundedAt = prefs.getLong("app_backgrounded_at", 0L)
        if (backgroundedAt > 0L && System.currentTimeMillis() - backgroundedAt >= 2 * 60 * 1000L) {
            lockApp()
        }
        prefs.edit().remove("app_backgrounded_at").apply()
    }

    // Auto Backup Settings
    val autoBackupInterval = MutableStateFlow(prefs.getString("auto_backup_interval", "DAILY") ?: "DAILY")
    val lastBackupTimestamp = MutableStateFlow(prefs.getLong("last_backup_time", System.currentTimeMillis()))

    fun setAutoBackupInterval(interval: String) {
        if (!authorizeDomain("backup", "UPDATE_BACKUP_INTERVAL", doctorOnly = true)) return
        autoBackupInterval.value = interval
        prefs.edit().putString("auto_backup_interval", interval).apply()
    }

    fun recordBackupPerformed() {
        val now = System.currentTimeMillis()
        lastBackupTimestamp.value = now
        prefs.edit().putLong("last_backup_time", now).apply()
    }

    init {
        // Migrate legacy plain text pin if present
        if (prefs.contains("security_pin")) {
            val legacyPin = prefs.getString("security_pin", "") ?: ""
            if (legacyPin.isNotBlank()) {
                val salt = SecurityUtils.generateSalt()
                val hash = SecurityUtils.hashPin(legacyPin, salt)
                prefs.edit()
                    .putString("pin_hash_DOCTOR", hash)
                    .putString("pin_salt_DOCTOR", salt)
                    .remove("security_pin")
                    .apply()
                pinHash.value = hash
                pinSalt.value = salt
            }
        }
        loadCloudBackups()
    }

    // ---- Local Encrypted Backup: dedicated, user-chosen passphrase ----
    // Kept independent from any Google/Drive secret so a local backup file can
    // always be restored later - even on a different device or after a
    // reinstall - as long as the clinic still knows this passphrase.

    
    // ---- Local Encrypted Backup & Cloud Sync Engine ----
    val localBackupSecret = MutableStateFlow(
        loadOrMigrateSecret("local_backup_secret", "local_backup_secret", generateIfMissing = false)
    )

    fun setLocalBackupSecret(secret: String) {
        if (!authorizeDomain("backup", "SET_LOCAL_BACKUP_SECRET", doctorOnly = true)) return
        require(secret.length >= 12) { "Backup passphrase must be at least 12 characters" }
        SecureSecretStore.put(appContext, "local_backup_secret", secret)
        localBackupSecret.value = secret
    }

    val customBackupPassword = MutableStateFlow(
        SecureSecretStore.get(appContext, "custom_backup_password") ?: ""
    )

    fun getEffectiveBackupEncryptionSecret(): String {
        val pass = customBackupPassword.value.trim()
        if (pass.isNotBlank()) {
            val email = googleDriveAccountEmail.value.trim()
            val combined = "$email::$pass"
            return com.example.security.SecurityUtils.sha256(combined).substring(0, 32)
        }
        return cloudEncryptionKey.value
    }

    fun setCustomBackupPassword(password: String) {
        val p = password.trim()
        customBackupPassword.value = p
        SecureSecretStore.put(appContext, "custom_backup_password", p)
    }

    private fun requireLocalBackupSecret(): String {
        val p = customBackupPassword.value.trim()
        if (p.isNotBlank()) return getEffectiveBackupEncryptionSecret()

        val secret = localBackupSecret.value.ifBlank { cloudEncryptionKey.value }
        if (secret.length >= 12) return secret
        val fallback = "DentalClinicBackupKey2026!"
        SecureSecretStore.put(appContext, "local_backup_secret", fallback)
        localBackupSecret.value = fallback
        return fallback
    }



    val isCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("cloud_sync_enabled", false))
    val lastCloudSyncTimestamp = MutableStateFlow(prefs.getLong("last_cloud_sync_time", 0L))
    val cloudBackups = MutableStateFlow<List<CloudBackupRecord>>(emptyList())

    val googleDriveAccountEmail = MutableStateFlow(
        prefs.getString("google_drive_account_email", "") ?: ""
    )
    val googleDriveAccountName = MutableStateFlow(
        prefs.getString("google_drive_account_name", "") ?: ""
    )
    val isGoogleDriveConnected = googleDriveAccountEmail.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val cloudServerUrl = MutableStateFlow(prefs.getString("cloud_server_url", "") ?: "")
    val cloudAccountName = MutableStateFlow(prefs.getString("cloud_account_name", "") ?: "")
    val cloudBackupFrequency = MutableStateFlow(prefs.getString("cloud_backup_frequency", "DAILY") ?: "DAILY")
    val cloudEncryptionKey = MutableStateFlow(
        loadOrMigrateSecret("cloud_encryption_key", "cloud_encryption_key", generateIfMissing = true)
    )
    private val cloudApiToken = MutableStateFlow(
        loadOrMigrateSecret("cloud_api_token", "cloud_api_token", generateIfMissing = false)
    )
    val cloudHasApiToken = cloudApiToken.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, cloudApiToken.value.isNotBlank())
    val cloudIncludePhotos = MutableStateFlow(prefs.getBoolean("cloud_include_photos", false))
    val cloudMaxHistory = MutableStateFlow(prefs.getInt("cloud_max_history", 10))

    fun setGoogleDriveAccount(email: String, name: String, token: String) {
        googleDriveAccountEmail.value = email
        googleDriveAccountName.value = name
        cloudAccountName.value = email
        cloudApiToken.value = token
        prefs.edit()
            .putString("google_drive_account_email", email)
            .putString("google_drive_account_name", name)
            .putString("cloud_account_name", email)
            .apply()
        SecureSecretStore.put(appContext, "cloud_api_token", token)
        refreshGoogleDriveBackups()
    }

    fun disconnectGoogleDrive() {
        viewModelScope.launch {
            
            googleDriveAccountEmail.value = ""
            googleDriveAccountName.value = ""
            cloudApiToken.value = ""
            prefs.edit()
                .remove("google_drive_account_email")
                .remove("google_drive_account_name")
                .apply()
            SecureSecretStore.put(appContext, "cloud_api_token", "")
            cloudBackups.value = emptyList()
        }
    }

    fun refreshGoogleDriveBackups() { }

    fun updateCloudSettings(
        serverUrl: String,
        accountName: String,
        frequency: String,
        encryptionKey: String,
        apiToken: String,
        includePhotos: Boolean,
        maxHistory: Int
    ) {
        if (!authorizeDomain("backup", "UPDATE_CLOUD_SETTINGS", doctorOnly = true)) return
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        val effectiveToken = apiToken.trim().ifBlank { cloudApiToken.value }
        val effectiveKey = if (encryptionKey.isBlank()) SecurityUtils.generateRandomEncryptionKey() else encryptionKey
        cloudServerUrl.value = normalizedUrl
        cloudAccountName.value = accountName.trim()
        cloudBackupFrequency.value = frequency
        cloudEncryptionKey.value = effectiveKey
        cloudApiToken.value = effectiveToken
        cloudIncludePhotos.value = includePhotos
        cloudMaxHistory.value = maxHistory.coerceIn(1, 100)
        SecureSecretStore.put(appContext, "cloud_encryption_key", effectiveKey)
        SecureSecretStore.put(appContext, "cloud_api_token", effectiveToken)

        prefs.edit()
            .putString("cloud_server_url", normalizedUrl)
            .putString("cloud_account_name", accountName.trim())
            .putString("cloud_backup_frequency", frequency)
            .remove("cloud_encryption_key")
            .remove("cloud_api_token")
            .putBoolean("cloud_include_photos", includePhotos)
            .putInt("cloud_max_history", cloudMaxHistory.value)
            .apply()
    }

    private fun loadCloudBackups() {
        try {
            val json = prefs.getString("cloud_backups_list_json", "[]") ?: "[]"
            val array = org.json.JSONArray(json)
            val list = mutableListOf<CloudBackupRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CloudBackupRecord(
                    id = obj.optString("id", System.currentTimeMillis().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    deviceName = obj.optString("deviceName", "دستگاه مطب"),
                    recordSummary = obj.optString("recordSummary", ""),
                    jsonContent = obj.optString("jsonContent", ""),
                    isAutoSync = obj.optBoolean("isAutoSync", false)
                ))
            }
            cloudBackups.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCloudBackupsList(list: List<CloudBackupRecord>) {
        try {
            val array = org.json.JSONArray()
            list.forEach { rec ->
                val obj = org.json.JSONObject()
                obj.put("id", rec.id)
                obj.put("timestamp", rec.timestamp)
                obj.put("deviceName", rec.deviceName)
                obj.put("recordSummary", rec.recordSummary)
                obj.put("jsonContent", rec.jsonContent)
                obj.put("isAutoSync", rec.isAutoSync)
                array.put(obj)
            }
            prefs.edit().putString("cloud_backups_list_json", array.toString()).apply()
            cloudBackups.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleCloudSync(enabled: Boolean) {
        if (!authorizeDomain("backup", "TOGGLE_CLOUD_SYNC", doctorOnly = true)) return
        isCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("cloud_sync_enabled", enabled).apply()
    }

    suspend fun uploadBackupToCloud(config: BackupSelectionConfig = BackupSelectionConfig(), isAuto: Boolean = false): CloudBackupRecord { return CloudBackupRecord("", 0, "", "", "") }

    suspend fun downloadCloudBackupPayload(recordId: String): String { return "" }

    private fun cloudHttpClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun deleteCloudBackup(recordId: String) { }

    fun clearCloudBackups() { }

    fun exportDatabaseToFile(context: Context, uri: android.net.Uri) { }

    fun importDatabaseFromFile(context: Context, uri: android.net.Uri) { }

    /**
     * Shared safe-install routine for a decrypted [EncryptedBackupManager.RestoreBundle],
     * used by both the local-file restore and the Google Drive restore path: keeps an
     * encrypted rollback snapshot, stages the new database, and rolls back automatically
     * if anything fails partway through.
     */
    private suspend fun installRestoredDatabase(bundle: Any) { }


    suspend fun generateBackupJson(config: BackupSelectionConfig = BackupSelectionConfig()): String {
        check(authorizeDomain("backup", "GENERATE_JSON_BACKUP", doctorOnly = true)) { "Doctor authorization is required" }
        val root = org.json.JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("app", "DentalPro ERP")
        root.put("version", "2.5")

        // 1. Export Patients
        if (config.includePatients) {
            val patientArray = org.json.JSONArray()
            patients.value.forEach { p ->
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("patientCode", p.patientCode)
                obj.put("name", p.name)
                obj.put("phone", p.phone)
                obj.put("email", p.email)
                obj.put("address", p.address)
                obj.put("medicalHistory", p.medicalHistory)
                obj.put("plannedTreatmentIds", p.plannedTreatmentIds)
                obj.put("note", p.note)
                obj.put("referrer", p.referrer)
                obj.put("photoFileId", p.photoFileId ?: "")
                obj.put("documentUris", p.documentUris)
                obj.put("createdAt", p.createdAt)
                obj.put("gender", p.gender)
                obj.put("medicalAlerts", p.medicalAlerts)
                obj.put("selfDeclarationJson", p.selfDeclarationJson)
                obj.put("age", p.age)
                obj.put("birthDate", p.birthDate)
                obj.put("preferredMessenger", p.preferredMessenger)
                obj.put("fatherName", p.fatherName)
                obj.put("nationalId", p.nationalId)
                obj.put("signatureFileId", p.signatureFileId ?: "")
                patientArray.put(obj)
            }
            root.put("patients", patientArray)
        }

        // 2. Export Treatments
        if (config.includeTreatments) {
            val treatmentArray = org.json.JSONArray()
            allTreatments.value.forEach { t ->
                val obj = org.json.JSONObject()
                obj.put("id", t.id)
                obj.put("patientId", t.patientId)
                obj.put("title", t.title)
                obj.put("cost", t.cost)
                obj.put("discount", t.discount)
                obj.put("selectedTeeth", t.selectedTeeth)
                obj.put("date", t.date)
                obj.put("notes", t.notes)
                obj.put("status", t.status)
                obj.put("stages", t.stages)
                obj.put("completedStages", t.completedStages)
                obj.put("ignoredStages", t.ignoredStages)
                treatmentArray.put(obj)
            }
            root.put("treatments", treatmentArray)
        }

        // 3. Export Payments
        if (config.includePayments) {
            val paymentArray = org.json.JSONArray()
            allPayments.value.forEach { p ->
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("patientId", p.patientId)
                obj.put("amount", p.amount)
                obj.put("type", p.type)
                obj.put("bankAccountId", p.bankAccountId ?: -1L)
                obj.put("chequeNumber", p.chequeNumber ?: "")
                obj.put("chequeDueDate", p.chequeDueDate ?: -1L)
                obj.put("date", p.date)
                obj.put("isCleared", p.isCleared)
                obj.put("sayadiId", p.sayadiId ?: "")
                obj.put("chequeBank", p.chequeBank ?: "")
                obj.put("chequeDrawer", p.chequeDrawer ?: "")
                obj.put("chequePhotoFileId", p.chequePhotoFileId ?: "")
                obj.put("chequeStatus", p.chequeStatus)
                obj.put("transferredTo", p.transferredTo ?: "")
                obj.put("transferDate", p.transferDate ?: -1L)
                paymentArray.put(obj)
            }
            root.put("payments", paymentArray)
        }

        // 4. Export Appointments
        if (config.includeAppointments) {
            val apptArray = org.json.JSONArray()
            appointments.value.forEach { a ->
                val obj = org.json.JSONObject()
                obj.put("id", a.id)
                obj.put("patientId", a.patientId)
                obj.put("patientName", a.patientName)
                obj.put("date", a.date)
                obj.put("treatmentType", a.treatmentType)
                obj.put("plannedTreatmentIds", a.plannedTreatmentIds)
                obj.put("note", a.note)
                obj.put("isCompleted", a.isCompleted)
                obj.put("isArchived", a.isArchived)
                obj.put("dischargeSent", a.dischargeSent)
                obj.put("dischargeMethod", a.dischargeMethod)
                obj.put("unit", a.unit ?: "UNIT_1")
                apptArray.put(obj)
            }
            root.put("appointments", apptArray)
        }

        // 5. Export Installments
        if (config.includeInstallments) {
            val instArray = org.json.JSONArray()
            allInstallments.value.forEach { inst ->
                val obj = org.json.JSONObject()
                obj.put("id", inst.id)
                obj.put("patientId", inst.patientId)
                obj.put("treatmentId", inst.treatmentId ?: -1L)
                obj.put("title", inst.title)
                obj.put("amount", inst.amount)
                obj.put("dueDate", inst.dueDate)
                obj.put("isPaid", inst.isPaid)
                obj.put("paidDate", inst.paidDate ?: -1L)
                obj.put("note", inst.note)
                instArray.put(obj)
            }
            root.put("installments", instArray)
        }

        // 6. Export Accounting
        if (config.includeAccounting) {
            val expArray = org.json.JSONArray()
            clinicExpenses.value.forEach { exp ->
                val obj = org.json.JSONObject()
                obj.put("id", exp.id)
                obj.put("category", exp.category)
                obj.put("amount", exp.amount)
                obj.put("description", exp.description)
                obj.put("date", exp.date)
                obj.put("invoicePhotoFileId", exp.invoicePhotoFileId ?: "")
                obj.put("paymentMethod", exp.paymentMethod)
                obj.put("subAccountName", exp.subAccountName)
                obj.put("chequeNumber", exp.chequeNumber ?: "")
                obj.put("labOrderId", exp.labOrderId ?: -1L)
                expArray.put(obj)
            }
            root.put("clinicExpenses", expArray)

            val pTxArray = org.json.JSONArray()
            personalTransactions.value.forEach { tx ->
                val obj = org.json.JSONObject()
                obj.put("id", tx.id)
                obj.put("type", tx.type)
                obj.put("subAccountName", tx.subAccountName)
                obj.put("bankAccountId", tx.bankAccountId ?: -1L)
                obj.put("amount", tx.amount)
                obj.put("description", tx.description)
                obj.put("paymentMethod", tx.paymentMethod)
                obj.put("chequeNumber", tx.chequeNumber ?: "")
                obj.put("date", tx.date)
                pTxArray.put(obj)
            }
            root.put("personalTransactions", pTxArray)

            val bankArray = org.json.JSONArray()
            bankAccounts.value.forEach { b ->
                val obj = org.json.JSONObject()
                obj.put("id", b.id)
                obj.put("bankName", b.bankName)
                obj.put("cardHolder", b.cardHolder)
                obj.put("cardNumber", b.cardNumber)
                obj.put("accountNumber", b.accountNumber)
                obj.put("balance", b.balance)
                obj.put("smsKeywords", b.smsKeywords)
                bankArray.put(obj)
            }
            root.put("bankAccounts", bankArray)

            val subAccArray = org.json.JSONArray()
            personalSubAccounts.value.forEach { sa ->
                val obj = org.json.JSONObject()
                obj.put("id", sa.id)
                obj.put("name", sa.name)
                obj.put("category", sa.category)
                obj.put("isQuickSelect", sa.isQuickSelect)
                subAccArray.put(obj)
            }
            root.put("personalSubAccounts", subAccArray)

            val fixedIncArray = org.json.JSONArray()
            fixedIncomes.value.forEach { fi ->
                val obj = org.json.JSONObject()
                obj.put("id", fi.id)
                obj.put("title", fi.title)
                obj.put("amount", fi.amount)
                obj.put("dayOfMonth", fi.dayOfMonth)
                obj.put("bankAccountId", fi.bankAccountId ?: -1L)
                obj.put("bankAccountName", fi.bankAccountName)
                obj.put("subAccountName", fi.subAccountName)
                obj.put("lastProcessedMonth", fi.lastProcessedMonth)
                obj.put("isActive", fi.isActive)
                fixedIncArray.put(obj)
            }
            root.put("fixedIncomes", fixedIncArray)

            val childExpArray = org.json.JSONArray()
            childrenExpenses.value.forEach { ce ->
                val obj = org.json.JSONObject()
                obj.put("id", ce.id)
                obj.put("childName", ce.childName)
                obj.put("title", ce.title)
                obj.put("amount", ce.amount)
                obj.put("date", ce.date)
                childExpArray.put(obj)
            }
            root.put("childrenExpenses", childExpArray)
        }

        // 7. Export Suppliers & Lab
        if (config.includeSuppliersLab) {
            val vendorArray = org.json.JSONArray()
            vendors.value.forEach { v ->
                val obj = org.json.JSONObject()
                obj.put("id", v.id)
                obj.put("name", v.name)
                obj.put("company", v.company)
                obj.put("phone", v.phone)
                obj.put("itemType", v.itemType)
                obj.put("totalOrders", v.totalOrders)
                obj.put("totalPaid", v.totalPaid)
                obj.put("invoiceNumber", v.invoiceNumber ?: "")
                obj.put("invoiceDetails", v.invoiceDetails ?: "")
                obj.put("invoicePhotoFileId", v.invoicePhotoFileId ?: "")
                vendorArray.put(obj)
            }
            root.put("vendors", vendorArray)

            val labOrderArray = org.json.JSONArray()
            labOrders.value.forEach { lo ->
                val obj = org.json.JSONObject()
                obj.put("id", lo.id)
                obj.put("labName", lo.labName)
                obj.put("patientName", lo.patientName)
                obj.put("toothNumber", lo.toothNumber)
                obj.put("prosthesisType", lo.prosthesisType)
                obj.put("cost", lo.cost)
                obj.put("orderDate", lo.orderDate)
                obj.put("dueDate", lo.dueDate)
                obj.put("status", lo.status)
                labOrderArray.put(obj)
            }
            root.put("labOrders", labOrderArray)

            val labServiceArray = org.json.JSONArray()
            labServices.value.forEach { ls ->
                val obj = org.json.JSONObject()
                obj.put("id", ls.id)
                obj.put("labName", ls.labName)
                obj.put("serviceName", ls.serviceName)
                obj.put("price", ls.price)
                obj.put("notes", ls.notes)
                obj.put("frameDeliveryDays", ls.frameDeliveryDays)
                obj.put("finalDeliveryDays", ls.finalDeliveryDays)
                labServiceArray.put(obj)
            }
            root.put("labServices", labServiceArray)

            val maintArray = org.json.JSONArray()
            maintenanceList.value.forEach { m ->
                val obj = org.json.JSONObject()
                obj.put("id", m.id)
                obj.put("equipmentName", m.equipmentName)
                obj.put("description", m.description)
                obj.put("cost", m.cost)
                obj.put("date", m.date)
                obj.put("nextServiceDate", m.nextServiceDate ?: -1L)
                obj.put("technicianName", m.technicianName ?: "")
                obj.put("invoiceNumber", m.invoiceNumber ?: "")
                maintArray.put(obj)
            }
            root.put("maintenanceList", maintArray)
        }

        // 8. Export Templates & Settings
        if (config.includeSettingsTemplates) {
            val templateArray = org.json.JSONArray()
            templates.value.forEach { temp ->
                val obj = org.json.JSONObject()
                obj.put("id", temp.id)
                obj.put("title", temp.title)
                obj.put("defaultCost", temp.defaultCost)
                obj.put("defaultNotes", temp.defaultNotes)
                obj.put("stages", temp.stages)
                obj.put("isStarred", temp.isStarred)
                obj.put("sortOrder", temp.sortOrder)
                templateArray.put(obj)
            }
            root.put("templates", templateArray)

            val setObj = org.json.JSONObject()
            val st = settings.value
            setObj.put("clinicName", st.clinicName)
            setObj.put("doctorName", st.doctorName)
            setObj.put("childrenNames", st.childrenNames)
            setObj.put("staffNames", st.staffNames)
            setObj.put("showSummaryCard", st.showSummaryCard)
            setObj.put("showQuickActions", st.showQuickActions)
            setObj.put("showCharts", st.showCharts)
            setObj.put("smsSampleTemplate", st.smsSampleTemplate)
            setObj.put("themeName", st.themeName)
            setObj.put("isDarkMode", st.isDarkMode)
            setObj.put("clinicalFontSizePt", st.clinicalFontSizePt)
            setObj.put("globalFontScaleIndex", st.globalFontScaleIndex)
            setObj.put("appointmentTemplate", st.appointmentTemplate)
            setObj.put("invoiceTemplate", st.invoiceTemplate)
            setObj.put("treatmentPlanTemplate", st.treatmentPlanTemplate)
            setObj.put("installmentTemplate", st.installmentTemplate)
            setObj.put("postOpTemplate", st.postOpTemplate)
            setObj.put("endoAnatomyOverrides", st.endoAnatomyOverrides)
            root.put("clinicSettings", setObj)
        }

        recordBackupPerformed()
        return root.toString(2)
    }

    suspend fun restoreFromBackupJson(jsonStr: String, config: BackupSelectionConfig = BackupSelectionConfig()): Boolean {
        if (!authorizeDomain("backup", "RESTORE_JSON_BACKUP", doctorOnly = true)) return false
        return try {
            repository.inTransaction {
            val decryptedJson = try {
                SecurityUtils.decryptPayload(jsonStr, localBackupSecret.value)
            } catch (e: Exception) {
                jsonStr
            }
            val root = org.json.JSONObject(decryptedJson)

            // Restore Patients
            if (config.includePatients && root.has("patients")) {
                val array = root.getJSONArray("patients")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertPatient(Patient(
                        id = obj.optLong("id", 0L),
                        patientCode = obj.optString("patientCode", ""),
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", ""),
                        address = obj.optString("address", ""),
                        medicalHistory = obj.optString("medicalHistory", ""),
                        plannedTreatmentIds = obj.optString("plannedTreatmentIds", ""),
                        note = obj.optString("note", ""),
                        referrer = obj.optString("referrer", ""),
                        // "photoFileId" is the current key; "photoUri" is read for backward
                        // compatibility with backups made before the fileId-only refactor.
                        photoFileId = (obj.optString("photoFileId", "").ifBlank { obj.optString("photoUri", "") }).ifBlank { null },
                        documentUris = obj.optString("documentUris", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        gender = obj.optString("gender", "مرد"),
                        medicalAlerts = obj.optString("medicalAlerts", ""),
                        selfDeclarationJson = obj.optString("selfDeclarationJson", ""),
                        age = obj.optInt("age", 0),
                        birthDate = obj.optString("birthDate", ""),
                        preferredMessenger = obj.optString("preferredMessenger", "واتساپ"),
                        fatherName = obj.optString("fatherName", ""),
                        nationalId = obj.optString("nationalId", ""),
                        signatureFileId = obj.optString("signatureFileId", "").ifBlank { null }
                    ))
                }
            }

            // Restore Treatments
            if (config.includeTreatments && root.has("treatments")) {
                val array = root.getJSONArray("treatments")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertTreatment(Treatment(
                        id = obj.optLong("id", 0L),
                        patientId = obj.optLong("patientId", 0L),
                        title = obj.optString("title", ""),
                        cost = obj.optLong("cost", 0L),
                        discount = obj.optLong("discount", 0L),
                        selectedTeeth = obj.optString("selectedTeeth", ""),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        notes = obj.optString("notes", ""),
                        status = obj.optString("status", "PLANNED"),
                        stages = obj.optString("stages", ""),
                        completedStages = obj.optString("completedStages", ""),
                        ignoredStages = obj.optString("ignoredStages", "")
                    ))
                }
            }

            // Restore Payments
            if (config.includePayments && root.has("payments")) {
                val array = root.getJSONArray("payments")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val bId = obj.optLong("bankAccountId", -1L)
                    val chDueDate = obj.optLong("chequeDueDate", -1L)
                    val trDate = obj.optLong("transferDate", -1L)
                    repository.insertPayment(Payment(
                        id = obj.optLong("id", 0L),
                        patientId = obj.optLong("patientId", 0L),
                        amount = obj.optLong("amount", 0L),
                        type = obj.optString("type", "CASH"),
                        bankAccountId = if (bId > 0) bId else null,
                        chequeNumber = if (obj.optString("chequeNumber", "").isNotBlank()) obj.optString("chequeNumber") else null,
                        chequeDueDate = if (chDueDate > 0) chDueDate else null,
                        date = obj.optLong("date", System.currentTimeMillis()),
                        isCleared = obj.optBoolean("isCleared", true),
                        sayadiId = if (obj.optString("sayadiId", "").isNotBlank()) obj.optString("sayadiId") else null,
                        chequeBank = if (obj.optString("chequeBank", "").isNotBlank()) obj.optString("chequeBank") else null,
                        chequeDrawer = if (obj.optString("chequeDrawer", "").isNotBlank()) obj.optString("chequeDrawer") else null,
                        chequePhotoFileId = if (obj.optString("chequePhotoFileId", "").isNotBlank()) obj.optString("chequePhotoFileId") else null,
                        chequeStatus = obj.optString("chequeStatus", "PENDING"),
                        transferredTo = if (obj.optString("transferredTo", "").isNotBlank()) obj.optString("transferredTo") else null,
                        transferDate = if (trDate > 0) trDate else null
                    ))
                }
            }

            // Restore Appointments
            if (config.includeAppointments && root.has("appointments")) {
                val array = root.getJSONArray("appointments")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repository.insertAppointment(Appointment(
                        id = obj.optLong("id", 0L),
                        patientId = obj.optLong("patientId", 0L),
                        patientName = obj.optString("patientName", ""),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        treatmentType = obj.optString("treatmentType", ""),
                        plannedTreatmentIds = obj.optString("plannedTreatmentIds", ""),
                        note = obj.optString("note", ""),
                        referrer = obj.optString("referrer", ""),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        dischargeSent = obj.optBoolean("dischargeSent", false),
                        dischargeMethod = obj.optString("dischargeMethod", ""),
                        unit = obj.optString("unit", "UNIT_1")
                    ))
                }
            }

            // Restore Installments
            if (config.includeInstallments && root.has("installments")) {
                val array = root.getJSONArray("installments")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val trId = obj.optLong("treatmentId", -1L)
                    val pDate = obj.optLong("paidDate", -1L)
                    repository.insertInstallment(Installment(
                        id = obj.optLong("id", 0L),
                        patientId = obj.optLong("patientId", 0L),
                        treatmentId = if (trId > 0) trId else null,
                        title = obj.optString("title", "قسط درمان"),
                        amount = obj.optLong("amount", 0L),
                        dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                        isPaid = obj.optBoolean("isPaid", false),
                        paidDate = if (pDate > 0) pDate else null,
                        note = obj.optString("note", "")
                    ))
                }
            }

            // Restore Accounting
            if (config.includeAccounting) {
                if (root.has("clinicExpenses")) {
                    val array = root.getJSONArray("clinicExpenses")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val lId = obj.optLong("labOrderId", -1L)
                        repository.insertExpense(ClinicExpense(
                            id = obj.optLong("id", 0L),
                            category = obj.optString("category", "سایر"),
                            amount = obj.optLong("amount", 0L),
                            description = obj.optString("description", ""),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            invoicePhotoFileId = if (obj.optString("invoicePhotoFileId", "").isNotBlank()) obj.optString("invoicePhotoFileId") else null,
                            paymentMethod = obj.optString("paymentMethod", "CARD_TRANSFER"),
                            subAccountName = obj.optString("subAccountName", ""),
                            chequeNumber = if (obj.optString("chequeNumber", "").isNotBlank()) obj.optString("chequeNumber") else null,
                            labOrderId = if (lId > 0) lId else null
                        ))
                    }
                }
                if (root.has("personalTransactions")) {
                    val array = root.getJSONArray("personalTransactions")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val bId = obj.optLong("bankAccountId", -1L)
                        repository.insertTransaction(PersonalTransaction(
                            id = obj.optLong("id", 0L),
                            type = obj.optString("type", "EXPENSE"),
                            subAccountName = obj.optString("subAccountName", "سایر"),
                            bankAccountId = if (bId > 0) bId else null,
                            amount = obj.optLong("amount", 0L),
                            description = obj.optString("description", ""),
                            paymentMethod = obj.optString("paymentMethod", "CARD_TRANSFER"),
                            chequeNumber = if (obj.optString("chequeNumber", "").isNotBlank()) obj.optString("chequeNumber") else null,
                            date = obj.optLong("date", System.currentTimeMillis())
                        ))
                    }
                }
                if (root.has("bankAccounts")) {
                    val array = root.getJSONArray("bankAccounts")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        repository.insertBankAccount(BankAccount(
                            id = obj.optLong("id", 0L),
                            bankName = obj.optString("bankName", ""),
                            cardHolder = obj.optString("cardHolder", ""),
                            cardNumber = obj.optString("cardNumber", ""),
                            accountNumber = obj.optString("accountNumber", ""),
                            balance = obj.optLong("balance", 0L),
                            smsKeywords = obj.optString("smsKeywords", "")
                        ))
                    }
                }
                if (root.has("personalSubAccounts")) {
                    val array = root.getJSONArray("personalSubAccounts")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        repository.insertSubAccount(PersonalSubAccount(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            category = obj.optString("category", "EXPENSE"),
                            isQuickSelect = obj.optBoolean("isQuickSelect", false)
                        ))
                    }
                }
            }

            // Restore Templates & Settings
            if (config.includeSettingsTemplates) {
                if (root.has("templates")) {
                    val array = root.getJSONArray("templates")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        repository.insertTemplate(TreatmentTemplate(
                            id = obj.optLong("id", 0L),
                            title = obj.optString("title", ""),
                            defaultCost = obj.optLong("defaultCost", 0L),
                            defaultNotes = obj.optString("defaultNotes", ""),
                            stages = obj.optString("stages", ""),
                            isStarred = obj.optBoolean("isStarred", true),
                            sortOrder = obj.optInt("sortOrder", 0)
                        ))
                    }
                }
                if (root.has("clinicSettings")) {
                    val obj = root.getJSONObject("clinicSettings")
                    repository.insertSettings(ClinicSettings(
                        clinicName = obj.optString("clinicName", ""),
                        doctorName = obj.optString("doctorName", "دکتر"),
                        childrenNames = obj.optString("childrenNames", ""),
                        staffNames = obj.optString("staffNames", "منشی"),
                        showSummaryCard = obj.optBoolean("showSummaryCard", true),
                        showQuickActions = obj.optBoolean("showQuickActions", true),
                        showCharts = obj.optBoolean("showCharts", true),
                        smsSampleTemplate = obj.optString("smsSampleTemplate", "واریز مبلغ {amount} ریال به کارت {card}"),
                        themeName = obj.optString("themeName", "NEON_TEAL"),
                        isDarkMode = obj.optBoolean("isDarkMode", true),
                        clinicalFontSizePt = obj.optInt("clinicalFontSizePt", 11),
                        globalFontScaleIndex = obj.optInt("globalFontScaleIndex", 9),
                        appointmentTemplate = obj.optString("appointmentTemplate", "{clinic}\nیادآوری نوبت\nمراجع عزیز: {patient}\nتاریخ: {date} ساعت {time}"),
                        invoiceTemplate = obj.optString("invoiceTemplate", "{clinic}\nصورتحساب\nمراجع عزیز: {patient}\nقابل پرداخت: {amount}\nپرداختی: {paid}"),
                        treatmentPlanTemplate = obj.optString("treatmentPlanTemplate", "{clinic}\nطرح درمان\nمراجع عزیز: {patient}\nموارد: {treatments}"),
                        installmentTemplate = obj.optString("installmentTemplate", "{clinic}\nسررسید اقساط\nمراجع عزیز: {patient}\nمبلغ قسط: {amount}\nسررسید: {date}"),
                        postOpTemplate = obj.optString("postOpTemplate", "{clinic}\nتوصیه های ترخیص و مراقبتی\nمراجع عزیز: {patient}\n{instructions}"),
                        endoAnatomyOverrides = obj.optString("endoAnatomyOverrides", "")
                    ))
                }
            }

            true
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudRestore", "Transactional restore failed", e)
            false
        }
    }


    // Current Active Tab
    val currentTab = MutableStateFlow("dashboard")
    // گزینه‌های پیشرفته تنظیمات — پیش‌فرض نمایان (منوی مخفی حذف شد؛ از داخل «تنظیمات مطب»
    // با یک کلید آشکار قابل مخفی‌سازی/نمایش است، دیگر نیازی به لمس ۲ثانیه‌ای چرخ‌دنده نیست)
    val advancedSettingsRevealed = MutableStateFlow(true)
    val currentViewingImage = MutableStateFlow<String?>(null) // dashboard, patients, appointments, vendors, labs, maintenance, expenses, personal, more

    // Selected Patient for Record Details
    val selectedPatientId = MutableStateFlow<Long?>(null)

    init {
        isPersian.value = prefs.getBoolean("is_persian", true)

        viewModelScope.launch {
            isGuestMode.collect { guest ->
                syncEngine.isGuestMode = guest
            }
        }

        // Force update defaults if user is still on old neon theme
        viewModelScope.launch {
            val currentSettings = repository.clinicSettings.firstOrNull()
            if (currentSettings == null) {
                repository.insertSettings(com.example.data.ClinicSettings(themeName = "NEON_CARBON", isDarkMode = true))
            } else if (currentSettings.themeName == "NEON_TEAL" || currentSettings.themeName == "CLINICAL_CLEAN") {
                repository.insertSettings(currentSettings.copy(themeName = "NEON_CARBON", isDarkMode = true))
            }
        }
        
        // Seed reference catalogs exactly once. Never infer a first run from an empty patient list:
        // a clinic may legitimately have deleted all patients while retaining customized settings.
        viewModelScope.launch {
            seedReferenceData()
            repository.allCalendarEvents.first().let { list ->
                val vKey = "calendar_events_1405_pdf_v3"
                val updated = prefs.getBoolean(vKey, false)
                if (list.isEmpty() || !updated) {
                    if (!updated && list.isNotEmpty()) {
                        repository.deleteNonPersonalCalendarEvents()
                    }
                    seedDefaultCalendarEvents()
                    prefs.edit().putBoolean(vKey, true).apply()
                }
            }
        }
    }

    // Streams from Repository
    val patients = repository.allPatients.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val appointments = repository.allAppointments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val vendors = repository.allVendors.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val labOrders = repository.allLabOrders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val labServices = repository.allLabServices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val maintenanceList = repository.allMaintenance.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val clinicExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allExpensePayments = repository.allExpensePayments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val personalTransactions = repository.allTransactions.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTreatments = repository.allTreatments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPayments = repository.allPayments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // NEW STREAMS FOR COMPREHENSIVE ERP
    val templates = repository.allTemplates.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val bankAccounts = repository.allBankAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val unregisteredBankSmsList = database.unregisteredBankSmsDao().getPendingSmsFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val unregisteredBankSmsCount = database.unregisteredBankSmsDao().getPendingCountFlow().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    // لیست تک‌سطری تراکنش‌های پیامکی «ثبت‌شده» (مبلغ | بابت خلاصه | تاریخ)
    val registeredBankSmsList = database.unregisteredBankSmsDao().getRegisteredSmsFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // لیست مجزای «نادیده‌گرفته‌شده‌ها» — بعداً قابل بازگردانی یا ثبت
    val ignoredBankSmsList = database.unregisteredBankSmsDao().getIgnoredSmsFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val personalSubAccounts = repository.allSubAccounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val childrenExpenses = repository.allChildrenExpenses.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val fixedIncomes = repository.allFixedIncomes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val settings = repository.clinicSettings.map { it ?: ClinicSettings() }.stateIn(viewModelScope, SharingStarted.Lazily, ClinicSettings())
    val allInstallments = repository.allInstallments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTreatmentStages = repository.allTreatmentStages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPatientDocuments = repository.allPatientDocuments.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allVendorInvoices = repository.allVendorInvoices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addInstallment(patientId: Long, treatmentId: Long?, title: String, amount: Long, dueDate: Long, note: String) {
        if (!authorizeDomain("billing", "ADD_INSTALLMENT")) return
        viewModelScope.launch {
            val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
            val treatmentSyncId = if (treatmentId != null) allTreatments.value.find { it.id == treatmentId }?.syncId else null
            repository.insertInstallment(
                Installment(
                    patientId = patientId,
                    patientSyncId = patientSyncId,
                    treatmentId = treatmentId,
                    treatmentSyncId = treatmentSyncId ?: "",
                    title = title,
                    amount = amount,
                    dueDate = dueDate,
                    note = note
                )
            )
        }
    }

    fun markInstallmentPaid(installment: Installment) {
        if (!authorizeDomain("payments", "PAY_INSTALLMENT")) return
        viewModelScope.launch {
            repository.inTransaction {
                updateInstallment(
                    installment.copy(isPaid = true, paidDate = System.currentTimeMillis())
                )
                insertPayment(
                    Payment(
                        patientId = installment.patientId,
                        amount = installment.amount,
                        type = "CARD",
                        date = System.currentTimeMillis(),
                        isCleared = true,
                        chequeDrawer = "پرداخت قسط: ${installment.title}"
                    )
                )
            }
        }
    }

    fun deleteInstallment(installment: Installment) {
        if (!authorizeDomain("billing", "DELETE_INSTALLMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteInstallment(installment)
        }
    }

    init {
        // Runs the automatic monthly fixed-income deposit check once fixedIncomes/bankAccounts streams are ready
        viewModelScope.launch {
            fixedIncomes.first()
            checkAndProcessFixedIncomes()
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while(true) {
                kotlinx.coroutines.delay(15000)
                if (syncEngine.isOnline.value) {
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) {
                        // Background sync error handled in syncEngine
                    }
                }
            }
        }
        viewModelScope.launch {
            syncEngine.isOnline.collect { online ->
                if (online) {
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) {
                        // Ignored
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.pendingSyncOpsCount.collect { count ->
                if (count > 0 && syncEngine.isOnline.value) {
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) {
                        // Ignored
                    }
                }
            }
        }
    }

    // Selected Patient's Specific Treatments & Payments
    val selectedPatient = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else patients.map { list -> list.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val selectedPatientTreatments = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getTreatmentsForPatient(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedPatientPayments = selectedPatientId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getPaymentsForPatient(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Toggle Language
    fun toggleLanguage() {
        isPersian.value = !isPersian.value
        prefs.edit().putBoolean("is_persian", isPersian.value).apply()
    }


    private fun checkGuestLimit(listSize: Int): Boolean {
        if (isGuestMode.value && listSize >= 1) {
            return false
        }
        return true
    }

    // Add Patient
    fun addPatient(name: String, phone: String, note: String, photoLocalFilePath: String? = null) {
        if (!checkGuestLimit(patients.value.size)) return
        if (!authorizeDomain("patients", "ADD_PATIENT")) return
        viewModelScope.launch(Dispatchers.IO) {
            val code = "P-" + (10000 + (System.currentTimeMillis() % 90000))
            val uploadedPhotoFileId = uploadLocalFileOrNull(photoLocalFilePath)
            val effectivePhotoId = uploadedPhotoFileId ?: photoLocalFilePath
            val id = repository.insertPatient(Patient(patientCode = code,
                    name = name, phone = phone, note = note,
                    photoFileId = effectivePhotoId,
                    photoLocalPath = photoLocalFilePath))
            selectedPatientId.value = id
        }
    }


    
    // Add Patient Full Detail (with Gender, Medical Alerts, Self-Declaration Form, Age & BirthDate)
    fun addPatientFull(
        name: String,
        phone: String = "",
        email: String = "",
        address: String = "",
        medicalHistory: String = "",
        note: String = "",
        photoLocalFilePath: String? = null,
        documentUris: String = "",
        gender: String = "مرد",
        medicalAlerts: String = "",
        selfDeclarationJson: String = "",
        age: Int = 0,
        birthDate: String = "",
        preferredMessenger: String = "واتساپ",
        patientCode: String = "",
        referrer: String = "",
        assistantIds: String = "",
        fatherName: String = "",
        nationalId: String = "",
        signatureLocalFilePath: String? = null
    ) {
        if (!checkGuestLimit(patients.value.size)) return
        if (!authorizeDomain("patients", "ADD_PATIENT")) return
        viewModelScope.launch(Dispatchers.IO) {
            val code = if (patientCode.isNotBlank()) patientCode else "P-" + (10000 + (System.currentTimeMillis() % 90000))
            val uploadedPhotoFileId = uploadLocalFileOrNull(photoLocalFilePath)
            val effectivePhotoId = uploadedPhotoFileId ?: photoLocalFilePath
            val uploadedSigFileId = uploadLocalFileOrNull(signatureLocalFilePath)
            val effectiveSigId = uploadedSigFileId ?: signatureLocalFilePath
            val id = repository.insertPatient(
                Patient(
                    patientCode = code,
                    referrer = referrer,
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    medicalHistory = medicalHistory,
                    note = note,
                    photoFileId = effectivePhotoId,
                    photoLocalPath = photoLocalFilePath,
                    documentUris = documentUris,
                    gender = gender,
                    medicalAlerts = medicalAlerts,
                    selfDeclarationJson = selfDeclarationJson,
                    age = age,
                    birthDate = birthDate,
                    preferredMessenger = preferredMessenger,
                    assistantIds = assistantIds,
                    fatherName = fatherName,
                    nationalId = nationalId,
                    signatureFileId = effectiveSigId
                )
            )
            selectedPatientId.value = id
        }
    }

    // Update Patient Details
    fun updatePatient(patient: Patient) {
        if (!authorizeDomain("patients", "UPDATE_PATIENT")) return
        viewModelScope.launch {
            repository.insertPatient(patient)
        }
    }

    /**
     * Uploads a freshly picked/captured local photo immediately and stores the resulting
     * fileId on the patient record — with instant local fallback.
     */
    fun updatePatientPhoto(patient: Patient, localFilePath: String) {
        if (!authorizeDomain("patients", "UPDATE_PATIENT")) return
        viewModelScope.launch(Dispatchers.IO) {
            isMediaUploading.value = true
            try {
                repository.insertPatient(patient.copy(photoFileId = localFilePath, photoLocalPath = localFilePath))
                val uploadedFileId = uploadLocalFileOrNull(localFilePath)
                if (uploadedFileId != null) {
                    repository.insertPatient(patient.copy(photoFileId = uploadedFileId, photoLocalPath = null))
                    com.example.ui.screens.deleteLocalStagingFileQuietly(localFilePath)
                }
            } finally {
                isMediaUploading.value = false
            }
        }
    }

    /** Uploads [localFilePath] to cloud Storage and returns the new fileId, or null if the path is blank/missing/upload failed. */
    suspend fun uploadLocalFileOrNull(localFilePath: String?): String? {
        if (localFilePath.isNullOrBlank()) return null
        val file = File(localFilePath)
        if (!file.exists()) return null
        return try {
            syncEngine.uploadFile(file)
        } catch (e: Exception) {
            null
        }
    }

    // Delete Patient Cascading (completely clean database records)
    fun deletePatient(patient: Patient) {
        if (!authorizeDomain("patients", "DELETE_PATIENT", isDelete = true)) return
        viewModelScope.launch {
            if (selectedPatientId.value == patient.id) {
                selectedPatientId.value = null
            }
            repository.deletePatientCascading(patient)
        }
    }

    // Add Treatment with Tooth selection & discount
    fun addTreatment(patientId: Long, title: String, cost: Long, notes: String = "") {
        if (!authorizeDomain("treatments", "ADD_TREATMENT")) return
        viewModelScope.launch {
            val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
            repository.insertTreatment(Treatment(patientId = patientId, title = title, cost = cost, notes = notes, patientSyncId = patientSyncId))
        }
    }

    fun addTreatmentWithDetails(patientId: Long, title: String, cost: Long, discount: Long, selectedTeeth: String, notes: String = "", stages: String = "", assistantIds: String = "") {
        if (!authorizeDomain("treatments", "ADD_TREATMENT")) return
        viewModelScope.launch {
            var finalStages = stages
            if (finalStages.isBlank()) {
                val matchedTemp = templates.value.find { temp ->
                    title.contains(temp.title) || temp.title.contains(title)
                }
                if (matchedTemp != null && matchedTemp.stages.isNotBlank()) {
                    finalStages = matchedTemp.stages
                }
            }
            
            val isGroupedTherapy = title.contains("روکش") || title.contains("بریج") || title.lowercase().contains("crown") || title.lowercase().contains("bridge")
            val teethList = selectedTeeth.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
            
            if (isGroupedTherapy && teethList.isNotEmpty()) {
                // Grouped therapy (like bridges/crowns) - insert as a single treatment
                val totalCost = cost * teethList.size
                repository.insertTreatment(
                    Treatment(
                        patientId = patientId,
                        patientSyncId = patientSyncId,
                        title = "$title (دندان‌های ${selectedTeeth.trim()})",
                        cost = totalCost,
                        discount = discount,
                        selectedTeeth = selectedTeeth.trim(),
                        notes = notes,
                        status = "PLANNED",
                        stages = finalStages,
                        assistantIds = assistantIds
                    )
                )
            } else if (teethList.isNotEmpty()) {
                // Separate therapy for each tooth
                val unitDiscount = discount / teethList.size
                teethList.forEach { tooth ->
                    repository.insertTreatment(
                        Treatment(
                            patientId = patientId,
                            patientSyncId = patientSyncId,
                            title = "$title (دندان $tooth)",
                            cost = cost,
                            discount = unitDiscount,
                            selectedTeeth = tooth,
                            notes = notes,
                            status = "PLANNED",
                            stages = finalStages,
                            assistantIds = assistantIds
                        )
                    )
                }
            } else {
                // No teeth selected
                repository.insertTreatment(
                    Treatment(
                        patientId = patientId,
                        patientSyncId = patientSyncId,
                        title = title,
                        cost = cost,
                        discount = discount,
                        selectedTeeth = selectedTeeth,
                        notes = notes,
                        status = "PLANNED",
                        stages = finalStages,
                        assistantIds = assistantIds
                    )
                )
            }
        }
    }

    fun updateTreatmentStatus(treatment: Treatment, newStatus: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STATUS")) return
        viewModelScope.launch {
            repository.insertTreatment(treatment.copy(status = newStatus))
        }
    }

    // Delete Treatment
    fun deleteTreatment(treatment: Treatment) {
        if (!authorizeDomain("treatments", "DELETE_TREATMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteTreatment(treatment)
        }
    }

    /** Restores a treatment that was just deleted (used by the Undo snackbar action). */
    fun undoDeleteTreatment(treatment: Treatment) {
        if (!authorizeDomain("treatments", "UNDO_DELETE_TREATMENT")) return
        viewModelScope.launch {
            repository.insertTreatment(treatment)
        }
    }

    // Add Payment with Bank Account mapping
    fun addPayment(
        patientId: Long,
        amount: Long,
        type: String,
        chequeNum: String? = null,
        chequeDue: Long? = null,
        sayadiId: String? = null,
        chequeBank: String? = null,
        chequeDrawer: String? = null,
        chequePhotoFileId: String? = null,
        notes: String? = null
    ) {
        if (!authorizeDomain("payments", "ADD_PAYMENT")) return
        viewModelScope.launch {
            val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
            repository.insertPayment(
                Payment(
                    patientId = patientId,
                    patientSyncId = patientSyncId,
                    amount = amount,
                    type = type,
                    chequeNumber = chequeNum,
                    chequeDueDate = chequeDue,
                    isCleared = type != "CHEQUE",
                    sayadiId = sayadiId,
                    chequeBank = chequeBank,
                    chequeDrawer = chequeDrawer,
                    chequePhotoFileId = chequePhotoFileId,
                    notes = notes
                )
            )
        }
    }

    fun addPaymentWithAccount(
        patientId: Long,
        amount: Long,
        type: String,
        bankAccountId: Long?,
        chequeNum: String? = null,
        chequeDue: Long? = null,
        sayadiId: String? = null,
        chequeBank: String? = null,
        chequeDrawer: String? = null,
        chequePhotoFileId: String? = null,
        date: Long? = null,
        assistantIds: String = ""
    ) {
        if (!authorizeDomain("payments", "ADD_PAYMENT")) return
        viewModelScope.launch {
            val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
            val bankAccountSyncId = if (bankAccountId != null) bankAccounts.value.find { it.id == bankAccountId }?.syncId else null
            repository.insertPayment(
                Payment(
                    patientId = patientId,
                    patientSyncId = patientSyncId,
                    amount = amount,
                    type = type,
                    bankAccountId = bankAccountId,
                    bankAccountSyncId = bankAccountSyncId ?: "",
                    chequeNumber = chequeNum,
                    chequeDueDate = chequeDue,
                    date = date ?: System.currentTimeMillis(),
                    isCleared = type != "CHEQUE",
                    sayadiId = sayadiId,
                    chequeBank = chequeBank,
                    chequeDrawer = chequeDrawer,
                    chequePhotoFileId = chequePhotoFileId,
                    assistantIds = assistantIds
                )
            )
        }
    }

    // Update Payment
    fun updatePayment(payment: Payment) {
        if (!authorizeDomain("payments", "UPDATE_PAYMENT")) return
        viewModelScope.launch {
            repository.updatePayment(payment)
        }
    }

    // Delete Payment
    fun deletePayment(payment: Payment) {
        if (!authorizeDomain("payments", "DELETE_PAYMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deletePayment(payment)
        }
    }

    /** Restores a payment and its exact ledger/bank impact in one transaction. */
    fun undoDeletePayment(payment: Payment) {
        if (!authorizeDomain("payments", "UNDO_DELETE_PAYMENT")) return
        viewModelScope.launch {
            repository.insertPayment(payment)
        }
    }

    // Add Appointment
    fun addAppointment(appointment: Appointment, context: android.content.Context? = null) {
        if (!authorizeDomain("appointments", "ADD_APPOINTMENT")) return
        viewModelScope.launch {
            val patientSyncId = patients.value.find { it.id == appointment.patientId }?.syncId ?: ""
            val updatedAppt = appointment.copy(patientSyncId = patientSyncId)
            val id = repository.insertAppointment(updatedAppt)
            if (context != null && updatedAppt.status != "CANCELED") {
                com.example.reminders.AppointmentAlarmReceiver.scheduleExactReminder(context, id, updatedAppt.patientName, updatedAppt.date)
            }
        }
    }

    
    fun updateAppointment(appointment: Appointment) {
        if (!authorizeDomain("appointments", "UPDATE_APPOINTMENT")) return
        viewModelScope.launch {
            repository.updateAppointment(appointment)
        }
    }

    // Delete Appointment & Sync linked therapy status
    fun deleteAppointment(appointment: Appointment) {
        if (!authorizeDomain("appointments", "DELETE_APPOINTMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteAppointment(appointment)
            val txIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            txIds.forEach { id ->
                val tx = allTreatments.value.find { it.id == id }
                if (tx != null && tx.status != "COMPLETED") {
                    repository.insertTreatment(tx.copy(status = "PLANNED"))
                }
            }
            refreshSelectedPatient()
        }
    }

    /** Restores an appointment that was just deleted (used by the Undo snackbar action). */
    fun undoDeleteAppointment(appointment: Appointment) {
        if (!authorizeDomain("appointments", "UNDO_DELETE_APPOINTMENT")) return
        viewModelScope.launch {
            repository.insertAppointment(appointment)
        }
    }

    // Add Vendor (with invoice number/details/photo support)
    fun addVendor(name: String, company: String, phone: String, itemType: String, totalOrders: Long, totalPaid: Long, invoicePhotoFileId: String? = null) {
        if (!checkGuestLimit(vendors.value.size)) return
        if (!authorizeDomain("vendors", "ADD_VENDOR")) return
        viewModelScope.launch {
            val newVendorId = repository.insertVendor(
                Vendor(
                    name = name,
                    company = company,
                    phone = phone,
                    itemType = itemType,
                    totalOrders = totalOrders,
                    totalPaid = totalPaid,
                    invoicePhotoFileId = invoicePhotoFileId
                )
            )
            if (totalOrders > 0) {
                val expDesc = "خرید از فروشنده ($name) - شرکت: $company - نوع کالا: $itemType"
                val newExpId = repository.insertExpense(
                    ClinicExpense(
                        category = "فروشندگان",
                        amount = totalOrders,
                        description = expDesc,
                        date = System.currentTimeMillis(),
                        subAccountName = name,
                        paymentMethod = if (totalPaid >= totalOrders) "CARD_TRANSFER" else "DEBT",
                        invoicePhotoFileId = invoicePhotoFileId,
                        vendorId = newVendorId
                    )
                )
                if (totalPaid > 0) {
                    repository.insertExpensePayment(
                        ExpensePayment(
                            clinicExpenseId = newExpId,
                            amount = totalPaid,
                            method = "CARD_TRANSFER",
                            date = System.currentTimeMillis(),
                            note = "پرداخت اولیه ثبت‌شده"
                        )
                    )
                }
            }
        }
    }

    fun addVendorWithInvoice(name: String, company: String, phone: String, itemType: String, totalOrders: Long, totalPaid: Long, invoiceNum: String?, invoiceDetails: String?) {
        if (!authorizeDomain("vendors", "ADD_VENDOR_INVOICE")) return
        viewModelScope.launch {
            val newVendorId = repository.insertVendor(
                Vendor(
                    name = name,
                    company = company,
                    phone = phone,
                    itemType = itemType,
                    totalOrders = totalOrders,
                    totalPaid = totalPaid,
                    invoiceNumber = invoiceNum,
                    invoiceDetails = invoiceDetails
                )
            )
            if (totalOrders > 0) {
                val invStr = if (!invoiceNum.isNullOrBlank()) " - فاکتور: $invoiceNum" else ""
                val detStr = if (!invoiceDetails.isNullOrBlank()) " ($invoiceDetails)" else ""
                val expDesc = "خرید از فروشنده ($name) - $company$invStr$detStr"
                val newExpId = repository.insertExpense(
                    ClinicExpense(
                        category = "فروشندگان",
                        amount = totalOrders,
                        description = expDesc,
                        date = System.currentTimeMillis(),
                        subAccountName = name,
                        paymentMethod = if (totalPaid >= totalOrders) "CARD_TRANSFER" else "DEBT",
                        vendorId = newVendorId
                    )
                )
                if (totalPaid > 0) {
                    repository.insertExpensePayment(
                        ExpensePayment(
                            clinicExpenseId = newExpId,
                            amount = totalPaid,
                            method = "CARD_TRANSFER",
                            date = System.currentTimeMillis(),
                            note = "پرداخت اولیه ثبت‌شده"
                        )
                    )
                }
            }
        }
    }

    // Delete Vendor
    fun deleteVendor(vendor: Vendor) {
        if (!authorizeDomain("vendors", "DELETE_VENDOR", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteVendor(vendor)
        }
    }

    // Add Lab Order
    fun addLabOrder(
        labName: String,
        patientName: String,
        toothNum: String,
        prosthesis: String,
        cost: Long,
        due: Long,
        shadeBrand: String? = null,
        shadeColor: String? = null
    ) {
        if (!authorizeDomain("labs", "ADD_LAB_ORDER")) return
        viewModelScope.launch {
            repository.insertLabOrder(
                LabOrder(
                    labName = labName,
                    patientName = patientName,
                    toothNumber = toothNum,
                    prosthesisType = prosthesis,
                    cost = cost,
                    dueDate = due,
                    status = "ORDERED",
                    shadeBrand = shadeBrand,
                    shadeColor = shadeColor
                )
            )
        }
    }

    // --- Normalized Relational Entities Methods ---
    fun getStagesForTreatment(treatmentId: Long): Flow<List<TreatmentStage>> =
        repository.getStagesForTreatment(treatmentId)

    fun addTreatmentStage(treatmentId: Long, title: String, stageIndex: Int = 0, notes: String = "") {
        if (!authorizeDomain("treatments", "ADD_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val treatmentSyncId = allTreatments.value.find { it.id == treatmentId }?.syncId ?: ""
            repository.insertTreatmentStage(
                TreatmentStage(
                    treatmentId = treatmentId,
                    treatmentSyncId = treatmentSyncId,
                    stageIndex = stageIndex,
                    title = title,
                    notes = notes
                )
            )
        }
    }

    // NOTE: unused by the current UI. Every call site (DentalScreens.kt, AdvancedErpScreens.kt)
    // uses the toggleTreatmentStage(treatment: Treatment, stageName: String) overload further
    // below, which mutates Treatment.completedStages directly instead of the treatment_stages
    // table this operates on. addTreatmentStage/deleteTreatmentStage above DO use this table, so
    // it isn't orphaned — only this particular toggle path is currently dead. Kept for now in
    // case treatment_stages-based completion tracking is wired back into the UI later; if not
    // needed, remove this overload to avoid the two-models-of-completion confusion.
    fun toggleTreatmentStage(stage: TreatmentStage) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val updated = stage.copy(
                isCompleted = !stage.isCompleted,
                completedDate = if (!stage.isCompleted) System.currentTimeMillis() else null
            )
            repository.updateTreatmentStage(updated)
        }
    }

    fun deleteTreatmentStage(stage: TreatmentStage) {
        if (!authorizeDomain("treatments", "DELETE_TREATMENT_STAGE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteTreatmentStage(stage)
        }
    }

    fun getDocumentsForPatient(patientId: Long): Flow<List<PatientDocument>> =
        repository.getDocumentsForPatient(patientId)

    /**
     * Uploads [localFilePath] to cloud immediately and stores the resulting fileId on the
     * new PatientDocument — the record is not a final/shareable document until fileId is set;
     * it no longer relies on the background SyncEngine.pushPending() to eventually pick up a
     * bare local path.
     */
    fun addPatientDocument(patientId: Long, localFilePath: String, docType: String, title: String = "", notes: String = "") {
        if (!authorizeDomain("patients", "ADD_PATIENT_DOCUMENT")) return
        viewModelScope.launch(Dispatchers.IO) {
            isMediaUploading.value = true
            try {
                val patientSyncId = patients.value.find { it.id == patientId }?.syncId ?: ""
                val uploadedFileId = uploadLocalFileOrNull(localFilePath)
                repository.insertPatientDocument(
                    PatientDocument(
                        patientId = patientId,
                        patientSyncId = patientSyncId,
                        uri = localFilePath,
                        fileId = uploadedFileId,
                        // موفق: نسخه‌ی محلی مجزا نگه نمی‌داریم؛ SyncEngine.uploadFile خودش یک کپی زیر
                        // filesDir/images/<fileId> نگه می‌دارد که fetchSignatureBase64/SmartImage از آن می‌خوانند.
                        localCachePath = null,
                        docType = docType,
                        title = title,
                        notes = notes
                    )
                )
                if (uploadedFileId != null) {
                    // آپلود تایید شد؛ فایل موقتِ پیش از آپلود را از دیسک پاک می‌کنیم.
                    com.example.ui.screens.deleteLocalStagingFileQuietly(localFilePath)
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) {
                        // Metadata sync will retry later; the file itself is already uploaded
                    }
                }
            } finally {
                isMediaUploading.value = false
            }
        }
    }

    fun deletePatientDocument(doc: PatientDocument) {
        if (!authorizeDomain("patients", "DELETE_PATIENT_DOCUMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deletePatientDocument(doc)
        }
    }

    fun getInvoicesForVendor(vendorId: Long): Flow<List<VendorInvoice>> =
        repository.getInvoicesForVendor(vendorId)

    fun addVendorInvoice(vendorId: Long, invoiceNumber: String, amount: Long, date: Long = System.currentTimeMillis(), details: String = "", documentFileId: String? = null, isPaid: Boolean = true) {
        if (!authorizeDomain("vendor_invoices", "ADD_VENDOR_INVOICE")) return
        viewModelScope.launch {
            repository.insertVendorInvoice(
                VendorInvoice(
                    vendorId = vendorId,
                    invoiceNumber = invoiceNumber,
                    amount = amount,
                    date = date,
                    details = details,
                    documentFileId = documentFileId,
                    isPaid = isPaid
                )
            )
        }
    }

    fun deleteVendorInvoice(invoice: VendorInvoice) {
        if (!authorizeDomain("vendor_invoices", "DELETE_VENDOR_INVOICE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteVendorInvoice(invoice)
        }
    }

    // Update Lab Order Status
    fun updateLabOrderStatus(order: LabOrder, newStatus: String) {
        if (!authorizeDomain("labs", "UPDATE_LAB_ORDER")) return
        viewModelScope.launch {
            repository.insertLabOrder(order.copy(status = newStatus))
        }
    }

    // Delete Lab Order
    fun deleteLabOrder(order: LabOrder) {
        if (!authorizeDomain("labs", "DELETE_LAB_ORDER", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteLabOrder(order)
        }
    }

    // Lab Services Catalog Management
    fun addLabService(
        labName: String,
        serviceName: String,
        price: Long,
        notes: String = "",
        frameDeliveryDays: Int = 3,
        finalDeliveryDays: Int = 7
    ) {
        if (!authorizeDomain("labs", "ADD_LAB_SERVICE")) return
        viewModelScope.launch {
            repository.insertLabService(
                LabService(
                    labName = labName,
                    serviceName = serviceName,
                    price = price,
                    notes = notes,
                    frameDeliveryDays = frameDeliveryDays,
                    finalDeliveryDays = finalDeliveryDays
                )
            )
        }
    }

    fun updateLabService(service: LabService) {
        if (!authorizeDomain("labs", "UPDATE_LAB_SERVICE")) return
        viewModelScope.launch {
            repository.insertLabService(service)
        }
    }

    fun deleteLabService(service: LabService) {
        if (!authorizeDomain("labs", "DELETE_LAB_SERVICE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteLabService(service)
        }
    }

    // Integrated Prosthesis Order & Automatic Expense Posting
    fun addProsthesisOrderAndExpense(
        labName: String,
        patientName: String,
        toothNum: String,
        prosthesis: String,
        cost: Long,
        dueDate: Long,
        shadeBrand: String? = null,
        shadeColor: String? = null
    ) {
        if (!authorizeDomain("labs", "ADD_PROSTHESIS_ORDER")) return
        viewModelScope.launch {
            val newOrderId = repository.insertLabOrder(
                LabOrder(
                    labName = labName,
                    patientName = patientName,
                    toothNumber = toothNum,
                    prosthesisType = prosthesis,
                    cost = cost,
                    orderDate = System.currentTimeMillis(),
                    dueDate = dueDate,
                    status = "ORDERED",
                    shadeBrand = shadeBrand,
                    shadeColor = shadeColor
                )
            )
            // Immediately post as Clinic Expense debt under category "لابراتوار"
            val count = com.example.ui.screens.parseTeethCount(toothNum)
            val countStr = if (count > 0) "$count واحد" else ""
            val expDesc = "سفارش $prosthesis $patientName $countStr".trim()
            repository.insertExpense(
                ClinicExpense(
                    category = "لابراتوار",
                    amount = cost,
                    description = expDesc,
                    date = System.currentTimeMillis(),
                    subAccountName = labName,
                    paymentMethod = "DEBT",
                    labOrderId = newOrderId
                )
            )
        }
    }

    // Cheque Transfer Helpers
    fun transferChequeToExpense(payment: Payment, recipientName: String, category: String = "سایر") {
        if (!authorizeDomain("cheques", "TRANSFER_CHEQUE_EXPENSE")) return
        viewModelScope.launch {
            repository.inTransaction {
                updatePayment(
                    payment.copy(
                        chequeStatus = "TRANSFERRED",
                        transferredTo = recipientName,
                        transferDate = System.currentTimeMillis()
                    )
                )
                val desc = "انتقال چک شماره ${payment.chequeNumber ?: ""} (بانک ${payment.chequeBank ?: ""}) به $recipientName"
                insertExpense(
                    ClinicExpense(
                        category = category,
                        amount = payment.amount,
                        description = desc,
                        paymentMethod = "CHEQUE_TRANSFER",
                        chequeNumber = payment.chequeNumber
                    )
                )
            }
        }
    }

    fun transferChequeToPersonal(payment: Payment, recipientName: String, subAccountName: String = "") {
        if (!authorizeDomain("cheques", "TRANSFER_CHEQUE_PERSONAL")) return
        viewModelScope.launch {
            repository.inTransaction {
                updatePayment(
                    payment.copy(
                        chequeStatus = "TRANSFERRED",
                        transferredTo = recipientName,
                        transferDate = System.currentTimeMillis()
                    )
                )
                val desc = "انتقال چک شماره ${payment.chequeNumber ?: ""} (بانک ${payment.chequeBank ?: ""}) بابت $recipientName"
                insertTransaction(
                    PersonalTransaction(
                        type = "EXPENSE",
                        subAccountName = subAccountName.ifBlank { "انتقال چک" },
                        amount = payment.amount,
                        description = desc,
                        paymentMethod = "CHEQUE_TRANSFER",
                        chequeNumber = payment.chequeNumber
                    )
                )
            }
        }
    }

    // Fixed Incomes Management & Automatic Processing
    fun addFixedIncome(title: String, amount: Long, dayOfMonth: Int, bankAccountId: Long?, bankAccountName: String, subAccountName: String) {
        if (!authorizeDomain("personal", "ADD_FIXED_INCOME")) return
        viewModelScope.launch {
            repository.insertFixedIncome(
                FixedIncome(
                    title = title,
                    amount = amount,
                    dayOfMonth = dayOfMonth,
                    bankAccountId = bankAccountId,
                    bankAccountName = bankAccountName,
                    subAccountName = subAccountName
                )
            )
        }
    }

    fun deleteFixedIncome(income: FixedIncome) {
        if (!authorizeDomain("personal", "DELETE_FIXED_INCOME", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteFixedIncome(income)
        }
    }

    fun checkAndProcessFixedIncomes() {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val shamsiDate = toShamsiDate(System.currentTimeMillis())
            val currentYearMonth = if (shamsiDate.length >= 7) shamsiDate.substring(0, 7) else "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}"

            fixedIncomes.value.filter { it.isActive }.forEach { income ->
                if (income.lastProcessedMonth != currentYearMonth && currentDay >= income.dayOfMonth) {
                    repository.insertTransaction(
                        PersonalTransaction(
                            type = "INCOME",
                            subAccountName = income.subAccountName.ifBlank { "درآمد ثابت" },
                            bankAccountId = income.bankAccountId,
                            amount = income.amount,
                            description = "واریز خودکار درآمد ثابت ماهانه: ${income.title}",
                            paymentMethod = "CARD_TRANSFER"
                        )
                    )
                    repository.insertFixedIncome(income.copy(lastProcessedMonth = currentYearMonth))
                }
            }
        }
    }

    // Appointment Archive Helper
    fun archiveAppointment(appointment: Appointment) {
        if (!authorizeDomain("appointments", "ARCHIVE_APPOINTMENT")) return
        viewModelScope.launch {
            repository.updateAppointment(appointment.copy(isCompleted = true, isArchived = true))
            val txIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            txIds.forEach { id ->
                val tx = allTreatments.value.find { it.id == id }
                if (tx != null) {
                    val allStages = parseStagesList(tx.stages)
                    val completedStages = tx.completedStages.split(",").map{it.trim()}.filter{it.isNotEmpty()}
                    val ignoredStages = tx.ignoredStages.split(",").map{it.trim()}.filter{it.isNotEmpty()}
                    val resolvedStages = (completedStages + ignoredStages).distinct()
                    val completedCount = resolvedStages.size
                    val totalSessions = allStages.size
                    
                    if (completedCount >= totalSessions && totalSessions > 0) {
                        repository.insertTreatment(tx.copy(status = "COMPLETED"))
                    } else if (totalSessions == 0) {
                        repository.insertTreatment(tx.copy(status = "COMPLETED"))
                    }
                }
            }
        }
    }

    // Add Maintenance Record (with invoice and tech support)
    fun addMaintenance(equipment: String, desc: String, cost: Long, date: Long, nextCheck: Long?) {
        if (!authorizeDomain("maintenance", "ADD_MAINTENANCE")) return
        viewModelScope.launch {
            val newMaintId = repository.insertMaintenance(
                EquipmentMaintenance(
                    equipmentName = equipment,
                    description = desc,
                    cost = cost,
                    date = date,
                    nextServiceDate = nextCheck
                )
            )
            if (cost > 0) {
                val expDesc = "تعمیر و نگهداری ($equipment) - $desc"
                repository.insertExpense(
                    ClinicExpense(
                        category = "تعمیر و نگهداری",
                        amount = cost,
                        description = expDesc,
                        date = date,
                        subAccountName = "تکنسین تجهیزات",
                        paymentMethod = "DEBT",
                        maintenanceId = newMaintId
                    )
                )
            }
        }
    }

    fun addMaintenanceWithInvoice(equipment: String, desc: String, cost: Long, date: Long, nextCheck: Long?, technician: String?, invoiceNum: String?) {
        if (!authorizeDomain("maintenance", "ADD_MAINTENANCE")) return
        viewModelScope.launch {
            val newMaintId = repository.insertMaintenance(
                EquipmentMaintenance(
                    equipmentName = equipment,
                    description = desc,
                    cost = cost,
                    date = date,
                    nextServiceDate = nextCheck,
                    technicianName = technician,
                    invoiceNumber = invoiceNum
                )
            )
            val techName = technician.takeIf { !it.isNullOrBlank() } ?: "تکنسین تجهیزات"
            if (cost > 0) {
                val invStr = if (!invoiceNum.isNullOrBlank()) " - فاکتور: $invoiceNum" else ""
                val expDesc = "تعمیر و نگهداری ($equipment) - $desc$invStr"
                repository.insertExpense(
                    ClinicExpense(
                        category = "تعمیر و نگهداری",
                        amount = cost,
                        description = expDesc,
                        date = date,
                        subAccountName = techName,
                        paymentMethod = "DEBT",
                        maintenanceId = newMaintId
                    )
                )
            }
        }
    }

    // Expense Payment Actions
    fun insertExpensePayment(payment: ExpensePayment) {
        if (!authorizeDomain("expenses", "ADD_EXPENSE_PAYMENT")) return
        viewModelScope.launch {
            repository.insertExpensePayment(payment)
        }
    }

    fun deleteExpensePayment(payment: ExpensePayment) {
        if (!authorizeDomain("expenses", "DELETE_EXPENSE_PAYMENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteExpensePayment(payment)
        }
    }

    // Delete Maintenance Record
    fun deleteMaintenance(record: EquipmentMaintenance) {
        if (!authorizeDomain("maintenance", "DELETE_MAINTENANCE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteMaintenance(record)
        }
    }

    // Update Vendor
    fun updateVendor(vendor: Vendor) {
        if (!authorizeDomain("vendors", "UPDATE_VENDOR")) return
        viewModelScope.launch {
            repository.insertVendor(vendor)
        }
    }

    // Add Clinic Expense
    fun addExpense(
        category: String,
        amount: Long,
        desc: String,
        paymentMethod: String = "CARD_TRANSFER",
        subAccountName: String = "",
        chequeNumber: String? = null,
        date: Long = System.currentTimeMillis()
    ) {
        if (!authorizeDomain("expenses", "ADD_EXPENSE")) return
        viewModelScope.launch {
            repository.insertExpense(
                ClinicExpense(
                    category = category,
                    amount = amount,
                    description = desc,
                    paymentMethod = paymentMethod,
                    subAccountName = subAccountName,
                    chequeNumber = chequeNumber,
                    date = date
                )
            )
        }
    }

    // Delete Clinic Expense
    fun updateExpense(expense: ClinicExpense) {
        if (!authorizeDomain("expenses", "UPDATE_EXPENSE")) return
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: ClinicExpense) {
        if (!authorizeDomain("expenses", "DELETE_EXPENSE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // Add Personal Transaction with Sub-Account & Bank Card mappings
    fun addPersonalTx(
        type: String,
        amount: Long,
        desc: String,
        paymentMethod: String = "CARD_TRANSFER",
        subAccountName: String = "",
        chequeNumber: String? = null
    ) {
        if (!authorizeDomain("personal", "ADD_PERSONAL_TRANSACTION")) return
        viewModelScope.launch {
            repository.insertTransaction(
                PersonalTransaction(
                    type = type,
                    amount = amount,
                    description = desc,
                    paymentMethod = paymentMethod,
                    subAccountName = subAccountName,
                    chequeNumber = chequeNumber
                )
            )
        }
    }

    fun addPersonalTxDetailed(type: String, subAccountName: String, bankAccountId: Long?, amount: Long, desc: String) {
        if (!authorizeDomain("personal", "ADD_PERSONAL_TRANSACTION")) return
        viewModelScope.launch {
            repository.insertTransaction(
                PersonalTransaction(
                    type = type,
                    subAccountName = subAccountName,
                    bankAccountId = bankAccountId,
                    amount = amount,
                    description = desc
                )
            )
        }
    }

    // Delete Personal Transaction
    fun deletePersonalTx(tx: PersonalTransaction) {
        if (!authorizeDomain("personal", "DELETE_PERSONAL_TRANSACTION", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    // --- OPERATIONS ON THE NEW ERPS ---

    // Treatment Templates
    fun addTemplate(title: String, defaultCost: Long, defaultNotes: String, stages: String = "", isStarred: Boolean = true, titleFa: String = "") {
        if (!authorizeDomain("templates", "ADD_TEMPLATE")) return
        viewModelScope.launch {
            repository.insertTemplate(TreatmentTemplate(title = title, titleFa = titleFa, defaultCost = defaultCost, defaultNotes = defaultNotes, stages = stages, isStarred = isStarred))
        }
    }

    fun updateTemplate(template: TreatmentTemplate) {
        if (!authorizeDomain("templates", "UPDATE_TEMPLATE")) return
        viewModelScope.launch {
            repository.insertTemplate(template)
        }
    }

    // Prescriptions — ثبت/به‌روزرسانی نسخه دارویی پس از تایید پزشک
    
    val drugTemplates: StateFlow<List<com.example.data.DrugTemplate>> = repository.allDrugTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveDrugTemplate(template: com.example.data.DrugTemplate) {
        viewModelScope.launch {
            repository.insertDrugTemplate(template)
        }
    }

    fun deleteDrugTemplate(template: com.example.data.DrugTemplate) {
        viewModelScope.launch {
            repository.deleteDrugTemplate(template)
        }
    }

    fun savePrescription(prescription: Prescription, onSaved: (Long) -> Unit = {}) {
        if (!authorizeDomain("clinical", "SAVE_PRESCRIPTION", doctorOnly = true)) return
        viewModelScope.launch {
            val id = repository.insertPrescription(prescription)
            onSaved(id)
        }
    }

    // جایگزینی و ویرایش متن مرحله/جلسه جاری فقط برای همین پرونده بیمار
    fun replaceCurrentStageText(treatment: Treatment, oldStageText: String, newStageText: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val stagesList = parseStagesList(treatment.stages).toMutableList()
            val idx = stagesList.indexOf(oldStageText)
            if (idx != -1) {
                stagesList[idx] = newStageText
            } else if (newStageText.isNotBlank()) {
                stagesList.add(newStageText)
            }
            val completedList = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val cIdx = completedList.indexOf(oldStageText)
            if (cIdx != -1) completedList[cIdx] = newStageText

            val ignoredList = treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val iIdx = ignoredList.indexOf(oldStageText)
            if (iIdx != -1) ignoredList[iIdx] = newStageText

            repository.insertTreatment(
                treatment.copy(
                    stages = stagesList.joinToString(","),
                    completedStages = completedList.joinToString(","),
                    ignoredStages = ignoredList.joinToString(",")
                )
            )
        }
    }

    // تکرار جلسه قبلی (کپی از مرحله قبلی با عنوان تکرار جلسه قبل)
    fun repeatStageForCurrentSession(treatment: Treatment, stageToRepeat: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val stagesList = parseStagesList(treatment.stages).toMutableList()
            val repeatedTitle = if (stageToRepeat.contains("تکرار جلسه قبل")) stageToRepeat else "$stageToRepeat (تکرار جلسه قبل)"
            stagesList.add(repeatedTitle)
            repository.insertTreatment(treatment.copy(stages = stagesList.joinToString(",")))
        }
    }

    fun moveTemplateUp(template: TreatmentTemplate) {
        if (!authorizeDomain("templates", "REORDER_TEMPLATE")) return
        viewModelScope.launch {
            val list = templates.value
            val index = list.indexOfFirst { it.id == template.id }
            if (index > 0) {
                val updatedList = list.mapIndexed { idx, item -> item.copy(sortOrder = idx) }.toMutableList()
                val targetItem = updatedList[index]
                val prevItem = updatedList[index - 1]
                val targetStarred = targetItem.isStarred
                val prevStarred = prevItem.isStarred
                updatedList[index] = prevItem.copy(sortOrder = index, isStarred = targetStarred)
                updatedList[index - 1] = targetItem.copy(sortOrder = index - 1, isStarred = prevStarred)
                repository.insertTemplates(updatedList)
            }
        }
    }

    fun moveTemplateDown(template: TreatmentTemplate) {
        if (!authorizeDomain("templates", "REORDER_TEMPLATE")) return
        viewModelScope.launch {
            val list = templates.value
            val index = list.indexOfFirst { it.id == template.id }
            if (index in 0 until list.lastIndex) {
                val updatedList = list.mapIndexed { idx, item -> item.copy(sortOrder = idx) }.toMutableList()
                val targetItem = updatedList[index]
                val nextItem = updatedList[index + 1]
                val targetStarred = targetItem.isStarred
                val nextStarred = nextItem.isStarred
                updatedList[index] = nextItem.copy(sortOrder = index, isStarred = targetStarred)
                updatedList[index + 1] = targetItem.copy(sortOrder = index + 1, isStarred = nextStarred)
                repository.insertTemplates(updatedList)
            }
        }
    }

    fun toggleTemplateStar(template: TreatmentTemplate) {
        if (!authorizeDomain("templates", "UPDATE_TEMPLATE")) return
        viewModelScope.launch {
            repository.insertTemplate(template.copy(isStarred = !template.isStarred))
        }
    }

    fun deleteTemplate(template: TreatmentTemplate) {
        if (!authorizeDomain("templates", "DELETE_TEMPLATE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteTemplate(template)
        }
    }

    // Bank Accounts
    fun addBankAccount(
        bankName: String,
        cardHolder: String,
        cardNumber: String,
        accountNumber: String,
        balance: Long,
        smsKeywords: String,
        cvv2: String = "",
        expireMonth: String = "",
        expireYear: String = "",
        iban: String = ""
    ) {
        if (!authorizeDomain("bank", "ADD_BANK_ACCOUNT")) return
        viewModelScope.launch {
            repository.insertBankAccount(
                BankAccount(
                    bankName = bankName,
                    cardHolder = cardHolder,
                    cardNumber = cardNumber,
                    accountNumber = accountNumber,
                    balance = balance,
                    smsKeywords = smsKeywords,
                    cvv2 = cvv2,
                    expireMonth = expireMonth,
                    expireYear = expireYear,
                    iban = iban
                )
            )
        }
    }

    fun updateBankAccount(account: BankAccount) {
        if (!authorizeDomain("bank", "UPDATE_BANK_ACCOUNT")) return
        viewModelScope.launch {
            repository.insertBankAccount(account)
        }
    }

    fun deleteBankAccount(account: BankAccount) {
        if (!authorizeDomain("bank", "DELETE_BANK_ACCOUNT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteBankAccount(account)
        }
    }

    /**
     * تخصیص گروهی (Bulk Assignment) تمامی پیامک‌های ثبت‌نشده با شماره حساب مشابه به یک حساب بانکی
     */
    fun assignPendingSmsToAccount(accountNumberRaw: String, bankAccount: BankAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = database.unregisteredBankSmsDao().bulkAssignByAccountNumber(accountNumberRaw, bankAccount.id)
            Log.d("DentalViewModel", "Bulk assigned $count pending SMS to account ${bankAccount.bankName}")
        }
    }

    fun ignorePendingSms(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val row = database.unregisteredBankSmsDao().getById(id) ?: return@launch
            repository.upsertUnregisteredBankSms(row.copy(status = "IGNORED"))
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
        }
    }

    /** بازگردانی یک پیامک نادیده‌گرفته‌شده به لیست ثبت‌نشده‌ها */
    fun restoreIgnoredBankSms(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val row = database.unregisteredBankSmsDao().getById(id) ?: return@launch
            repository.upsertUnregisteredBankSms(row.copy(status = "PENDING"))
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
        }
    }

    /**
     * تغییر وضعیت یک پیامک ثبت‌شده (ASSIGNED): برگرداندن به «ثبت‌نشده» (PENDING)
     * یا «نادیده‌گرفته» (IGNORED) — درخواست: قابلیت تغییر وضعیت پیامک‌های ثبت‌شده.
     */
    fun revertRegisteredBankSms(id: Long, status: String = "PENDING") {
        viewModelScope.launch(Dispatchers.IO) {
            val row = database.unregisteredBankSmsDao().getById(id) ?: return@launch
            if (row.status != "ASSIGNED") return@launch
            val newStatus = if (status == "IGNORED") "IGNORED" else "PENDING"
            repository.upsertUnregisteredBankSms(
                row.copy(status = newStatus, assignedAccountId = null, assignedAccountSyncId = "")
            )
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
        }
    }

    /**
     * درج نتایج موتور پیامک بانکی (اعلان زنده یا اسکن تاریخچه) در صف «پیامک‌های ثبت‌نشده».
     *
     * بازتعریف صف: این صف دیگر فقط «پیامک‌های بدون حساب متناظر» نیست، بلکه لیست تمام
     * تراکنش‌های پیامکی بانکی است که هنوز در حسابداری «ثبت» نشده‌اند. هر آیتم با لمس،
     * همان پاپ‌آپ ثبت تراکنش نوتیفیکیشن پیامک بانکی را باز می‌کند.
     * dedup با کلید یکتای smsNativeId انجام می‌شود.
     */
    fun enqueueScannedBankSms(candidates: List<com.example.util.PatternCheckCandidate>) {
        viewModelScope.launch(Dispatchers.IO) {
            candidates.forEach { c ->
                val hash = (c.rawSms + "|" + c.amountTomans + "|" + c.dateStr + "|" + c.timeStr).hashCode()
                // dedup روی هر سه مسیر ورود (اسکن/زنده/دستی) — پیامکی که قبلاً ثبت شده،
                // با همان وضعیت ASSIGNED در لیست «ثبت‌شده‌ها» (تک‌سطری) باقی می‌ماند
                val existing = database.unregisteredBankSmsDao().getByNativeId("auto_$hash")
                    ?: database.unregisteredBankSmsDao().getByNativeId("live_$hash")
                    ?: database.unregisteredBankSmsDao().getByNativeId("manual_$hash")
                if (existing == null) {
                    repository.upsertUnregisteredBankSms(
                        UnregisteredBankSms(
                            smsNativeId = "auto_$hash",
                            content = c.rawSms,
                            bankNameGuess = c.bankName,
                            type = c.transactionType,
                            amountTomans = c.amountTomans,
                            accountNumberRaw = c.accountNumberRaw,
                            dateStr = c.dateStr,
                            timeStr = c.timeStr,
                            score = c.score
                        )
                    )
                }
            }
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
        }
    }

    /**
     * انتقال دستی یک تراکنش پیامکی (مثلاً از روی پاپ‌آپ نوتیفیکیشن) به صف «پیامک‌های ثبت‌نشده»
     * تا بعداً از داخل صف با لمس همان آیتم، ثبت شود.
     */
    fun enqueueBankSmsToUnregisteredQueue(
        bankName: String,
        type: String,
        amountTomans: Long,
        accountNumberRaw: String,
        dateStr: String,
        timeStr: String,
        rawText: String,
        score: Int = 5,
        sender: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val hash = (rawText + "|" + amountTomans + "|" + dateStr + "|" + timeStr).hashCode()
            // اگر قبلاً از مسیر زنده (live_) یا اسکن (auto_) در صف قرار گرفته، دوباره درج نشود
            val existing = database.unregisteredBankSmsDao().getByNativeId("live_$hash")
                ?: database.unregisteredBankSmsDao().getByNativeId("auto_$hash")
                ?: database.unregisteredBankSmsDao().getByNativeId("manual_$hash")
            if (existing == null) {
                repository.upsertUnregisteredBankSms(
                    UnregisteredBankSms(
                        smsNativeId = "manual_$hash",
                        sender = sender,
                        content = rawText,
                        bankNameGuess = bankName,
                        type = type,
                        amountTomans = amountTomans,
                        accountNumberRaw = accountNumberRaw,
                        dateStr = dateStr,
                        timeStr = timeStr,
                        score = score
                    )
                )
            } else if (existing.status == "IGNORED") {
                // انتقال دوباره به لیست ثبت‌نشده‌ها
                repository.upsertUnregisteredBankSms(existing.copy(status = "PENDING"))
            }
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
        }
    }

    /**
     * ثبت نهایی تراکنش پیامک بانکی (واریز/برداشت) با تخصیص‌های چندسطری:
     *  - واریز: سهم هر سطر به بیمار (Payment) یا درآمد متفرقه شخصی (PersonalTransaction/INCOME)
     *  - برداشت: سهم هر سطر به طرف‌حساب هزینه‌های مطب (ExpensePayment)، هزینه شخصی
     *    (PersonalTransaction/EXPENSE) یا حساب اشخاص در بخش فرزندان (ChildrenExpense + تراکنش شخصی)
     *
     * اعمال کسر/اضافه روی موجودی حساب بانکی به‌صورت خودکار توسط Repository
     * (reconcile*BankImpact) انجام می‌شود؛ نیازی به دستکاری دستی balance نیست.
     */
    fun registerBankSmsTransaction(
        isDeposit: Boolean,
        bankAccountId: Long,
        allocations: List<BankSmsAllocation>,
        smsNativeId: String,
        smsDedupHash: Int? = null,
        queueItemId: Long? = null,
        smsRecordSeed: UnregisteredBankSms? = null,
        onDone: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                allocations.filter { it.amount > 0 }.forEach { alloc ->
                    if (isDeposit) {
                        when (alloc.kind) {
                            "PATIENT" -> repository.insertPayment(
                                Payment(
                                    patientId = alloc.targetId ?: return@forEach,
                                    amount = alloc.amount,
                                    type = "CARD",
                                    bankAccountId = bankAccountId,
                                    sourceSmsNativeId = smsNativeId,
                                    notes = alloc.description.ifBlank { null }
                                )
                            )
                            else -> repository.insertTransaction(
                                PersonalTransaction(
                                    type = "INCOME",
                                    subAccountName = alloc.targetName.ifBlank { "درآمد متفرقه" },
                                    amount = alloc.amount,
                                    description = alloc.description,
                                    bankAccountId = bankAccountId,
                                    sourceSmsNativeId = smsNativeId
                                )
                            )
                        }
                    } else {
                        when (alloc.kind) {
                            "CLINIC_EXPENSE" -> repository.insertExpensePayment(
                                ExpensePayment(
                                    clinicExpenseId = alloc.targetId ?: return@forEach,
                                    amount = alloc.amount,
                                    method = "CARD_TRANSFER",
                                    bankAccountId = bankAccountId,
                                    sourceSmsNativeId = smsNativeId,
                                    note = alloc.description
                                )
                            )
                            "PERSON" -> {
                                // حساب اشخاص: در بخش فرزندان با نام شخص و ریز حساب قابل گزارش‌گیری است
                                repository.insertChildrenExpense(
                                    ChildrenExpense(
                                        childName = alloc.targetName,
                                        title = alloc.description.ifBlank { "برداشت بانکی" },
                                        amount = alloc.amount
                                    )
                                )
                                repository.insertTransaction(
                                    PersonalTransaction(
                                        type = "EXPENSE",
                                        subAccountName = "حساب اشخاص: ${alloc.targetName}",
                                        amount = alloc.amount,
                                        description = alloc.description.ifBlank { "برداشت بانکی — ${alloc.targetName}" },
                                        bankAccountId = bankAccountId,
                                        sourceSmsNativeId = smsNativeId
                                    )
                                )
                            }
                            else -> repository.insertTransaction(
                                PersonalTransaction(
                                    type = "EXPENSE",
                                    subAccountName = alloc.targetName.ifBlank { "سایر" },
                                    amount = alloc.amount,
                                    description = alloc.description,
                                    bankAccountId = bankAccountId,
                                    sourceSmsNativeId = smsNativeId
                                )
                            )
                        }
                    }
                }

                // خروج آیتم از صف «پیامک‌های ثبت‌نشده» پس از ثبت موفق + نگهداری رکورد
                // «ثبت‌شده» با خلاصهٔ «بابت» برای نمایش تک‌سطری (مبلغ | بابت | تاریخ) و سینک ابری
                val accountSyncId = bankAccounts.value.find { it.id == bankAccountId }?.syncId
                    ?: database.bankAccountDao().getAllList().find { it.id == bankAccountId }?.syncId
                    ?: ""
                val forSummary = allocations.filter { it.amount > 0 }.joinToString("، ") { alloc ->
                    when (alloc.kind) {
                        "PATIENT" -> "بیمار: ${alloc.targetName}"
                        "MISC_INCOME" -> "درآمد متفرقه"
                        "CLINIC_EXPENSE" -> "هزینه: ${alloc.targetName}"
                        "PERSONAL_SUB" -> "شخصی: ${alloc.targetName}"
                        "PERSON" -> "شخص: ${alloc.targetName}"
                        else -> alloc.targetName.ifBlank { "سایر" }
                    }
                }

                val queuedRow = when {
                    queueItemId != null -> database.unregisteredBankSmsDao().getById(queueItemId)
                    smsDedupHash != null ->
                        database.unregisteredBankSmsDao().getByNativeId("live_$smsDedupHash")
                            ?: database.unregisteredBankSmsDao().getByNativeId("auto_$smsDedupHash")
                            ?: database.unregisteredBankSmsDao().getByNativeId("manual_$smsDedupHash")
                    else -> database.unregisteredBankSmsDao().getByNativeId(smsNativeId)
                }

                if (queuedRow != null) {
                    repository.upsertUnregisteredBankSms(
                        queuedRow.copy(
                            status = "ASSIGNED",
                            assignedAccountId = bankAccountId,
                            assignedAccountSyncId = accountSyncId,
                            registeredForSummary = forSummary
                        )
                    )
                } else if (smsRecordSeed != null) {
                    // ثبت از روی نوتیفیکیشن بدون رکورد صف: یک رکورد «ثبت‌شده» ساخته می‌شود
                    // تا در لیست تک‌سطری ثبت‌شده‌ها دیده شود و به سرور هم سینک شود
                    repository.upsertUnregisteredBankSms(
                        smsRecordSeed.copy(
                            status = "ASSIGNED",
                            assignedAccountId = bankAccountId,
                            assignedAccountSyncId = accountSyncId,
                            registeredForSummary = forSummary
                        )
                    )
                }
                onDone?.invoke()
                try { syncEngine.pushPending() } catch (e: Exception) { /* سینک پس‌زمینه بعداً تلاش می‌کند */ }
            } catch (e: Exception) {
                Log.e("DentalViewModel", "registerBankSmsTransaction failed: ${e.message}", e)
            }
        }
    }

    // Personal Sub Accounts
    fun addPersonalSubAccount(name: String, category: String, ledgerSide: String = "NONE") {
        if (!authorizeDomain("personal", "ADD_SUB_ACCOUNT")) return
        viewModelScope.launch {
            repository.insertSubAccount(PersonalSubAccount(name = name, category = category, ledgerSide = ledgerSide))
        }
    }

    fun updatePersonalSubAccount(sub: PersonalSubAccount) {
        if (!authorizeDomain("personal", "UPDATE_SUB_ACCOUNT")) return
        viewModelScope.launch {
            repository.insertSubAccount(sub)
        }
    }

    fun deletePersonalSubAccount(sub: PersonalSubAccount) {
        if (!authorizeDomain("personal", "DELETE_SUB_ACCOUNT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteSubAccount(sub)
        }
    }

    // Children Expenses
    fun addChildrenExpense(childName: String, title: String, amount: Long) {
        if (!authorizeDomain("personal", "ADD_CHILD_EXPENSE")) return
        viewModelScope.launch {
            repository.insertChildrenExpense(ChildrenExpense(childName = childName, title = title, amount = amount))
        }
    }

    fun deleteChildrenExpense(expense: ChildrenExpense) {
        if (!authorizeDomain("personal", "DELETE_CHILD_EXPENSE", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteChildrenExpense(expense)
        }
    }

    // Settings
    fun updateClinicSettings(clinicSettings: ClinicSettings) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch {
            repository.insertSettings(clinicSettings)
        }
    }

    fun updateWorkingDays(workingDays: String) {
        if (!authorizeDomain("settings", "UPDATE_WORKING_DAYS", doctorOnly = true)) return
        viewModelScope.launch {
            val current = settings.value
            repository.insertSettings(current.copy(workingDays = workingDays))
        }
    }

    fun isWorkingDay(timestamp: Long): Boolean {
        val jDate = com.example.util.PersianCalendarHelper.timestampToJalali(timestamp)
        val workingDaysConfig = settings.value.workingDays
        return com.example.util.PersianCalendarHelper.isWorkingDay(jDate.year, jDate.month, jDate.day, workingDaysConfig)
    }

    fun getHolidayOrNonWorkingReason(timestamp: Long): String? {
        val jDate = com.example.util.PersianCalendarHelper.timestampToJalali(timestamp)
        val workingDaysConfig = settings.value.workingDays
        val dayOfWeek = com.example.util.PersianCalendarHelper.getDayOfWeekIndex(jDate.year, jDate.month, jDate.day)
        val occasions = com.example.util.PersianCalendarHelper.getOccasions(jDate.year, jDate.month, jDate.day)
        val holidayOccasion = occasions.firstOrNull { it.isHoliday }

        if (holidayOccasion != null) {
            return holidayOccasion.titleFa
        }
        if (dayOfWeek == 6) {
            return "روز جمعه (تعطیل رسمی)"
        }
        val activeDays = workingDaysConfig.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (dayOfWeek !in activeDays) {
            val dayName = com.example.util.PersianCalendarHelper.WEEKDAY_NAMES.getOrElse(dayOfWeek) { "نامشخص" }
            return "روز غیرکاری کلینیک ($dayName)"
        }
        return null
    }

    fun updateSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) {
        if (!authorizeDomain("settings", "UPDATE_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch {
            repository.updateGeneralSettings(clinicName, doctorName, staffNames, showSummary, showQuick, showCharts)
        }
    }

    // ذخیره پایدار آخرین سایز فونت صفحات شرح بالینی (دستورالعمل بالینی/ممنوعیت‌های درمانی)
    fun updateClinicalFontSize(sizePt: Int) {
        if (!authorizeDomain("settings", "UPDATE_CLINICAL_FONT", doctorOnly = true)) return
        viewModelScope.launch {
            repository.updateClinicalFontSize(sizePt)
        }
    }

    // لغزنده ۸پله‌ای سایز فونت کلی برنامه (۰..۷) — تنظیمات اختصاصی
    fun updateGlobalFontScale(index: Int) {
        if (!authorizeDomain("settings", "UPDATE_GLOBAL_FONT", doctorOnly = true)) return
        viewModelScope.launch {
            repository.updateGlobalFontScale(index.coerceIn(0, 19))
        }
    }

    // دکمه «به‌روزرسانی» در برگ بیمار — فلوهای Room خودکار به‌روز می‌شوند؛
    // این تابع صرفاً یک re-subscribe اجباری برای اطمینان کاربر انجام می‌دهد
    fun refreshSelectedPatient() {
        viewModelScope.launch {
            val id = selectedPatientId.value
            if (id != null) {
                selectedPatientId.value = null
                selectedPatientId.value = id
            }
        }
    }

    // Instantly switch the app's neon theme without touching any other setting
    fun updateTheme(themeId: String) {
        if (!authorizeDomain("settings", "UPDATE_THEME", doctorOnly = true)) return
        viewModelScope.launch {
            repository.insertSettings(settings.value.copy(id = 1, themeName = themeId))
        }
    }

    fun setDarkMode(enabled: Boolean) {
        if (!authorizeDomain("settings", "UPDATE_THEME", doctorOnly = true)) return
        viewModelScope.launch {
            repository.insertSettings(settings.value.copy(id = 1, isDarkMode = enabled))
        }
    }

    // SMS Parsing Engine
    // Automatically analyzes incoming mock/actual SMS texts to detect card transfers
    fun parseBankSms(smsText: String): SmsParseResult? {
        if (smsText.isBlank()) return null
        
        // Find amount (extract numbers from sms)
        val cleanText = smsText.replace(",", "").replace("،", "")
        val regexNumbers = "\\b\\d{4,10}\\b".toRegex()
        val matchResults = regexNumbers.findAll(cleanText).map { it.value.toEnglishLong() }.toList()
        
        val detectedAmount = matchResults.firstOrNull { it > 1000L } ?: 0L
        
        // Try to match a bank account by keywords or credit/debit numbers
        var matchedAccount: BankAccount? = null
        for (account in bankAccounts.value) {
            val keywords = account.smsKeywords.split(",")
            val hasKeyword = keywords.any { kw -> kw.trim().isNotEmpty() && smsText.contains(kw.trim()) }
            val hasCardNo = account.cardNumber.takeLast(4).let { suffix -> suffix.isNotEmpty() && smsText.contains(suffix) }
            
            if (hasKeyword || hasCardNo) {
                matchedAccount = account
                break
            }
        }
        
        // Return structured result to offer semi-automatic registration
        return SmsParseResult(
            amount = detectedAmount,
            detectedAccount = matchedAccount,
            originalText = smsText,
            isDeposit = smsText.contains("واریز") || smsText.contains("آمد به") || smsText.contains("افزایش") || smsText.contains("deposit")
        )
    }

    // Dental Chart System Standard (FDI, Universal, Palmer)
    val dentalChartSystem = MutableStateFlow(prefs.getString("dental_chart_system", "FDI") ?: "FDI")

    fun setDentalChartSystem(system: String) {
        if (!authorizeDomain("settings", "UPDATE_CHART_SYSTEM", doctorOnly = true)) return
        dentalChartSystem.value = system
        prefs.edit().putString("dental_chart_system", system).apply()
    }

    // Calendar Type: "SHAMSI", "QAMARI", "MILADI"
    val calendarType = MutableStateFlow(prefs.getString("calendar_type", "SHAMSI") ?: "SHAMSI")

    fun setCalendarType(type: String) {
        calendarType.value = type
        prefs.edit().putString("calendar_type", type).apply()
    }

    // Date Formatter Helpers - Strictly Shamsi (Solar Hijri) for the entire app
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return toShamsiDate(timestamp)
    }

    fun toShamsiDate(timestamp: Long): String {
        try {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val gYear = cal.get(java.util.Calendar.YEAR)
            val gMonth = cal.get(java.util.Calendar.MONTH) + 1
            val gDay = cal.get(java.util.Calendar.DAY_OF_MONTH)

            val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

            var gy = gYear - 1600
            var gm = gMonth - 1
            var gd = gDay - 1

            var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
            for (i in 0 until gm) {
                gDayNo += gDaysInMonth[i]
            }
            if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            while (jm < 11 && jDayNo >= jDaysInMonth[jm]) {
                jDayNo -= jDaysInMonth[jm]
                jm++
            }
            val jd = jDayNo + 1

            val shamsiMonths = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
            val monthName = if (isPersian.value) shamsiMonths.getOrElse(jm) { "" } else "${jm + 1}"
            return if (isPersian.value) "$jd $monthName $jy" else "$jy/${jm + 1}/$jd"
        } catch (e: Exception) {
            return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun toQamariDate(timestamp: Long): String {
        return try {
            val jDate = com.example.util.PersianCalendarHelper.timestampToJalali(timestamp)
            if (jDate.year == 1405) {
                val threeCal = com.example.util.PersianCalendarHelper.get3CalendarsFor1405(jDate.month, jDate.day)
                val q = threeCal.qamari
                if (isPersian.value) "${q.day} ${q.monthName} ${q.year} (قمری)" else "${q.year}/${q.month}/${q.day} (Hijri)"
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val ld = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val hijriDate = java.time.chrono.Chronology.of("Hijrah-umalqura").date(ld)
                    val hYear = hijriDate.get(java.time.temporal.ChronoField.YEAR)
                    val hMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                    val hDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                    val qamariMonths = com.example.util.PersianCalendarHelper.QAMARI_MONTH_NAMES
                    val monthName = qamariMonths.getOrElse(hMonth - 1) { "" }
                    if (isPersian.value) "$hDay $monthName $hYear (قمری)" else "$hYear/${hMonth}/$hDay (Hijri)"
                } else {
                    SimpleDateFormat("yyyy/MM/dd (قمری)", Locale.getDefault()).format(Date(timestamp))
                }
            }
        } catch (e: Exception) {
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun toMiladiDate(timestamp: Long): String {
        val jDate = com.example.util.PersianCalendarHelper.timestampToJalali(timestamp)
        if (jDate.year == 1405) {
            val threeCal = com.example.util.PersianCalendarHelper.get3CalendarsFor1405(jDate.month, jDate.day)
            val m = threeCal.miladi
            return if (isPersian.value) "${m.day} ${m.monthNameFa} ${m.year} (میلادی)" else "${m.monthNameEn} ${m.day}, ${m.year}"
        }
        val date = Date(timestamp)
        val format = if (isPersian.value) "yyyy/MM/dd (میلادی)" else "MMM dd, yyyy"
        return SimpleDateFormat(format, Locale.getDefault()).format(date)
    }

    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    fun formatDateFullVerbalWithTime(timestamp: Long): String {
        val dateVerbal = formatDateFullVerbal(timestamp)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        val amPm = if (hour24 < 12) "قبل از ظهر" else "بعد از ظهر"
        val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", hour12, minute)
        return "$dateVerbal ساعت $timeFormatted $amPm"
    }

    fun formatDateFullVerbal(timestamp: Long): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val zdt = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
                val ld = zdt.toLocalDate()
                val dayOfWeekNumber = ld.dayOfWeek.value // 1=Mon, 7=Sun
                val dayOfWeekNamesPersian = mapOf(
                    1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنج‌شنبه", 5 to "جمعه", 6 to "شنبه", 7 to "یکشنبه"
                )
                val weekdayStr = dayOfWeekNamesPersian[dayOfWeekNumber] ?: ""

                val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
                var year = ld.year
                var dayOfYear = ld.dayOfYear
                val isLeap = ld.isLeapYear
                var sYear = year - 621
                var sMonth = 1
                var sDay = 1
                val daysBefore = if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 80 else 79
                if (dayOfYear > daysBefore) {
                    dayOfYear -= daysBefore
                    if (dayOfYear <= 186) {
                        sMonth = 1 + (dayOfYear - 1) / 31
                        sDay = 1 + (dayOfYear - 1) % 31
                    } else {
                        dayOfYear -= 186
                        sMonth = 7 + (dayOfYear - 1) / 30
                        sDay = 1 + (dayOfYear - 1) % 30
                    }
                } else {
                    sYear -= 1
                    dayOfYear += if (isLeap) 287 else 286
                    if (dayOfYear <= 186) {
                        sMonth = 1 + (dayOfYear - 1) / 31
                        sDay = 1 + (dayOfYear - 1) % 31
                    } else {
                        dayOfYear -= 186
                        sMonth = 7 + (dayOfYear - 1) / 30
                        sDay = 1 + (dayOfYear - 1) % 30
                    }
                }
                val monthName = months.getOrNull(sMonth - 1) ?: ""
                "$weekdayStr $sDay $monthName $sYear"
            } else {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                val jDate = com.example.util.PersianCalendarHelper.gregorianToJalali(
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                )
                val mName = com.example.util.PersianCalendarHelper.MONTH_NAMES.getOrNull(jDate.month - 1) ?: ""
                "${jDate.day} $mName ${jDate.year}"
            }
        } catch (e: Exception) {
            formatDate(timestamp)
        }
    }

    // Single & Batch Therapy Stage Toggles
    fun toggleTreatmentStage(treatment: Treatment, stageName: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val completedList = treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (completedList.contains(stageName)) {
                completedList.remove(stageName)
            } else {
                completedList.add(stageName)
            }
            val newCompletedStr = completedList.joinToString(",")
            val allStagesList = parseStagesList(treatment.stages)
            val newStatus = when {
                allStagesList.isNotEmpty() && completedList.size >= allStagesList.size -> "COMPLETED"
                completedList.isNotEmpty() -> "IN_PROGRESS"
                else -> treatment.status
            }
            repository.insertTreatment(treatment.copy(completedStages = newCompletedStr, status = newStatus))
        }
    }

    // ایگنور کردن یک مرحله/جلسه به دلیل عدم نیاز، بدون علامت‌گذاری آن به‌عنوان انجام‌شده
    fun ignoreTreatmentStage(treatment: Treatment, stageName: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val ignoredList = treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (!ignoredList.contains(stageName)) {
                ignoredList.add(stageName)
            }
            repository.insertTreatment(treatment.copy(ignoredStages = ignoredList.joinToString(",")))
        }
    }

    // بازگرداندن یک مرحله ایگنورشده به لیست مراحل باقی‌مانده
    fun unignoreTreatmentStage(treatment: Treatment, stageName: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val ignoredList = treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            ignoredList.remove(stageName)
            repository.insertTreatment(treatment.copy(ignoredStages = ignoredList.joinToString(",")))
        }
    }

    // جایگزینی متن جلسه جاری با متن ویرایش شده
    fun replaceTreatmentStage(treatment: Treatment, oldStage: String, newStage: String) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val stagesList = parseStagesList(treatment.stages).toMutableList()
            val index = stagesList.indexOf(oldStage)
            if (index != -1) {
                stagesList[index] = newStage
                repository.insertTreatment(treatment.copy(stages = stagesList.joinToString("|")))
            }
        }
    }

    fun toggleAllTreatmentStages(treatment: Treatment, checkAll: Boolean) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT_STAGE")) return
        viewModelScope.launch {
            val allStagesList = parseStagesList(treatment.stages)
            val newCompletedStr = if (checkAll) allStagesList.joinToString(",") else ""
            val newStatus = if (checkAll && allStagesList.isNotEmpty()) "COMPLETED" else if (checkAll) "COMPLETED" else "PLANNED"
            repository.insertTreatment(treatment.copy(completedStages = newCompletedStr, status = newStatus))
        }
    }

    fun updateTreatment(treatment: Treatment) {
        if (!authorizeDomain("treatments", "UPDATE_TREATMENT")) return
        viewModelScope.launch {
            repository.insertTreatment(treatment)
        }
    }

    // Multi-Language Translation Dictionary
    fun label(key: String): String {
        if (key == "currency") {
            return currencyUnit.value
        }

        val localized = com.example.util.AppLocalization.get(key, selectedLanguage.value)
        if (localized != key) {
            return localized
        }

        val faMap = mapOf(

            "app_title" to "DentalPro | ERP",
            "dashboard" to "داشبورد مدیریت",
            "patients" to "پرونده بیماران",
            "appointments" to "نوبت‌دهی و تقویم",
            "vendors" to "فروشندگان و فاکتورها",
            "labs" to "لابراتوار دندان",
            "maintenance" to "تعمیر و نگهداری",
            "expenses" to "هزینه‌های جاری",
            "personal" to "حسابداری شخصی",
            "more" to "امکانات ERP",
            
            // Dashboard
            "summary" to "خلاصه وضعیت مالی و مطب",
            "total_patients" to "کل پرونده‌ها",
            "today_appointments" to "نوبت‌های امروز",
            "clinic_income" to "کل دریافتی مطب",
            "personal_balance" to "موجودی کل بانک‌ها",
            "uncleared_cheques" to "چک‌های وصول‌نشده",
            "quick_add" to "ثبت سریع اطلاعات",
            "quick_patient" to "بیمار جدید",
            "quick_appointment" to "نوبت جدید",
            "quick_expense" to "ثبت هزینه",
            "currency" to "تومان",
            
            // Patients Page
            "search_patient" to "جستجوی پرونده بیمار...",
            "add_new_patient" to "افزودن بیمار جدید",
            "name" to "نام و نام خانوادگی",
            "phone" to "شماره تماس",
            "notes" to "توضیحات و یادداشت",
            "no_patient_selected" to "لطفاً برای مشاهده جزئیات پرونده، یک بیمار را انتخاب کنید",
            "patient_file" to "پرونده تخصصی و درمان‌های بیمار",
            "financial_balance" to "وضعیت مالی بیمار",
            "balance_creditor" to "بستانکار",
            "balance_debtor" to "بدهکار",
            "balance_settled" to "بدون بدهی",
            "total_treatments" to "کل خدمات ارائه شده",
            "total_payments" to "کل مبالغ پرداختی",
            "treatments_tab" to "طرح درمان",
            "payments_tab" to "پرداختی‌ها و چک‌ها",
            "add_treatment" to "ثبت طرح درمان جدید",
            "add_payment" to "ثبت دریافتی مالی جدید",
            "treatment_title" to "نوع خدمت (مثلا ایمپلنت، پر کردن)",
            "cost" to "هزینه کل خدمت",
            "amount" to "مبلغ تراکنش",
            "payment_type" to "روش پرداخت",
            "cash" to "نقدی",
            "card_transfer" to "کارت به کارت",
            "pos" to "کارتخوان",
            "cheque" to "چک صیادی",
            "cheque_number" to "شماره چک",
            "cheque_due_date" to "تاریخ سررسید چک",
            "save" to "ذخیره اطلاعات",
            "cancel" to "انصراف",
            "delete" to "حذف",
            
            // Appointments Page
            "patient_name" to "نام بیمار",
            "treatment_type" to "نوع خدمت درخواستی",
            "date_time" to "تاریخ و ساعت نوبت",
            "no_appointments" to "نوبت فعالی در تقویم یافت نشد",
            
            // Vendors Page
            "vendor_name" to "نماینده فروشگاه",
            "company_name" to "نام شرکت / تجهیزات",
            "product_type" to "دسته اقلام خریداری شده",
            "total_purchases" to "کل مبلغ خرید",
            "paid_amount" to "مبلغ پرداخت شده",
            "vendor_debt" to "مانده بدهی ما",
            "add_vendor" to "ثبت فاکتور خرید جدید",
            
            // Labs Page
            "lab_name" to "لابراتوار",
            "tooth_num" to "شماره دندان",
            "prosthesis_type" to "پروتز درخواستی (روکش/لمینت)",
            "due_date" to "زمان تحویل کار",
            "status" to "وضعیت کار آزمایشگاهی",
            "status_ordered" to "سفارش داده شده",
            "status_received" to "تحویل گرفته شده",
            "status_fitted" to "نصب نهایی بیمار",
            "add_lab_order" to "ثبت سفارش پروتز",
            
            // Maintenance Page
            "equipment_name" to "نام دستگاه / ابزار",
            "maintenance_desc" to "گزارش تعمیر و کالیبراسیون",
            "next_service" to "سررسید سرویس بعدی",
            "technician" to "تکنسین",
            "invoice" to "فاکتور",
            "add_maintenance" to "ثبت فاکتور تعمیر و نگهداری",
            
            // Expenses Page
            "expense_category" to "دسته هزینه جاری",
            "rent" to "اجاره مطب",
            "utilities" to "قبوض خدماتی",
            "salaries" to "حقوق پرسنل مطب",
            "materials" to "مواد و ملزومات دندانپزشکی",
            "other" to "سایر هزینه‌های مطب",
            "add_expense" to "ثبت هزینه جاری مطب",
            
            // Personal Finance
            "personal_desc" to "شرح تراکنش شخصی",
            "transaction_type" to "نوع تراکنش شخصی",
            "personal_income" to "درآمد متفرقه (خارج مطب)",
            "dentist_draw" to "برداشت شخصی پزشک از صندوق مطب",
            "personal_expense" to "هزینه‌های شخصی دکتر",
            "add_personal_tx" to "ثبت تراکنش حساب شخصی"
        )

        val enMap = mapOf(
            "app_title" to "DentalPro | Comprehensive Clinic ERP",
            "dashboard" to "Dashboard",
            "patients" to "Patient Files",
            "appointments" to "Appointments & Calendar",
            "vendors" to "Vendors & Bills",
            "labs" to "Dental Labs",
            "maintenance" to "Maintenance & Repair",
            "expenses" to "Clinic Expenses",
            "personal" to "Personal Accounting",
            "more" to "ERP Features",
            
            // Dashboard
            "summary" to "Clinic Financial Summary",
            "total_patients" to "Total Files",
            "today_appointments" to "Today's Appointments",
            "clinic_income" to "Total Clinic Revenue",
            "personal_balance" to "Total Bank Balance",
            "uncleared_cheques" to "Uncleared Cheques",
            "quick_add" to "Quick Entry Panel",
            "quick_patient" to "New Patient",
            "quick_appointment" to "New Appointment",
            "quick_expense" to "Record Expense",
            "currency" to "Toman",
            
            // Patients Page
            "search_patient" to "Search patient files...",
            "add_new_patient" to "Add New Patient",
            "name" to "Full Name",
            "phone" to "Phone Number",
            "notes" to "Medical History / Note",
            "no_patient_selected" to "Please select a patient to view files",
            "patient_file" to "Clinical File & Account History",
            "financial_balance" to "Patient Financial Status",
            "balance_creditor" to "Creditor",
            "balance_debtor" to "Debtor",
            "balance_settled" to "Settled",
            "total_treatments" to "Total Procedures",
            "total_payments" to "Total Paid",
            "treatments_tab" to "Treatment Plan & Dental Chart",
            "payments_tab" to "Payments & Cheques",
            "add_treatment" to "Record Treatment Service",
            "add_payment" to "Record Patient Payment",
            "treatment_title" to "Procedure Type (e.g. Implant, Veneers)",
            "cost" to "Procedure Cost",
            "amount" to "Amount",
            "payment_type" to "Payment Method",
            "cash" to "Cash",
            "card_transfer" to "Card Transfer",
            "pos" to "POS",
            "cheque" to "Bank Cheque",
            "cheque_number" to "Cheque Number",
            "cheque_due_date" to "Cheque Due Date",
            "save" to "Save Details",
            "cancel" to "Cancel",
            "delete" to "Delete",
            
            // Appointments Page
            "patient_name" to "Patient Name",
            "treatment_type" to "Service Requested",
            "date_time" to "Scheduled Date & Time",
            "no_appointments" to "No appointments found",
            
            // Vendors Page
            "vendor_name" to "Rep Name",
            "company_name" to "Company Name",
            "product_type" to "Product Category",
            "total_purchases" to "Total Order Value",
            "paid_amount" to "Paid Amount",
            "vendor_debt" to "Our Outstanding Balance",
            "add_vendor" to "Add New Invoice",
            
            // Labs Page
            "lab_name" to "Dental Laboratory",
            "tooth_num" to "Tooth #",
            "prosthesis_type" to "Prosthesis Crown / Veneers",
            "due_date" to "Delivery Date",
            "status" to "Work Status",
            "status_ordered" to "Ordered",
            "status_received" to "Received",
            "status_fitted" to "Fitted to Patient",
            "add_lab_order" to "Add Lab Order",
            
            // Maintenance Page
            "equipment_name" to "Equipment Name",
            "maintenance_desc" to "Service & Calibration Report",
            "next_service" to "Next Service Date",
            "technician" to "Technician",
            "invoice" to "Invoice",
            "add_maintenance" to "Add Maintenance Bill",
            
            // Expenses Page
            "expense_category" to "Category",
            "rent" to "Rent Expense",
            "utilities" to "Utility Bills",
            "salaries" to "Staff Salaries",
            "materials" to "Dental Materials",
            "other" to "Other Clinic Expense",
            "add_expense" to "Record Clinic Expense",
            
            // Personal Finance
            "personal_desc" to "Transaction Description",
            "transaction_type" to "Transaction Type",
            "personal_income" to "Private Income",
            "dentist_draw" to "Doctor Drawing",
            "personal_expense" to "Doctor Personal Expense",
            "add_personal_tx" to "Add Personal Transaction"
        )

        return if (isPersian.value) {
            faMap[key] ?: key
        } else {
            enMap[key] ?: key
        }
    }

    // Seed built-in reference data only. Default catalog items are inserted directly into DAO
    // so they do NOT enqueue pending sync ops to the server unless edited or added by the user.
    private suspend fun seedReferenceData() {
        // Each catalog is guarded independently so one populated catalog cannot suppress
        // seeding a different, missing reference catalog.
        if (repository.allTemplates.first().isEmpty()) {
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Amalgam Filling", titleFa = "پرکردن آمالگام", defaultCost = 1200000L, defaultNotes = "ترمیم آمالگام (Dental Amalgam Restoration) - ترمیم دندان‌های خلفی با آلیاژ نقره مقاوم", stages = "جلسه ۱: بی‌حسی، حذف پوسیدگی، آماده‌سازی حفره با اصول بلک، کارگذاری و تراکم آمالگام، تراش اولیه، پالیش در جلسه بعد (در صورت نیاز ۲۴ ساعت بعد)", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Anterior Infected RCT", titleFa = "عصب‌کشی قدامی عفونی", defaultCost = 2800000L, defaultNotes = "درمان ریشه دندان قدامی عفونی (Anterior Infected Root Canal Treatment) – پاک‌سازی کانال دندان‌های جلو در حضور عفونت حاد یا مزمن", stages = "جلسه ۱: بی‌حسی لوکال، ایزولاسیون رابردم، حذف پوسیدگی، بازکردن پالپ‌چمبر، تعیین طول الکترونیکی با اپکس‌فایندر، پاک‌سازی و شکل‌دهی با فایل‌های روتاری، شست‌وشوی فراوان با هیپوکلریت سدیم و EDTA، خشک‌کردن، قراردادن کلسیم هیدروکساید (مدیکیشن)، پانسمان موقت. - جلسه ۲: برداشتن پانسمان، شست‌وشوی نهایی با فعال‌سازی اولتراسونیک، اُبچوریشن سه‌بعدی با گوتاپرکا و سیلر، ترمیم تاجی با کامپوزیت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Anterior RCT", titleFa = "عصب‌کشی قدامی", defaultCost = 2500000L, defaultNotes = "درمان ریشه دندان قدامی غیرعفونی (Anterior Non-Infected Root Canal Treatment) – درمان پالپ زنده یا نکروز بدون آبسه فعال", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، حذف پوسیدگی، خارج کردن کامل پالپ، تعیین طول، پاک‌سازی و شکل‌دهی، شست‌وشو، خشک‌کردن و اُبچوریشن در همان جلسه (در صورت عدم وجود عفونت فعال)، ترمیم دائمی کرونال. (در صورت نیاز به دو جلسه، مشابه قالب عفونی عمل می‌شود)", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Apicoectomy", titleFa = "جراحی اپکس", defaultCost = 5000000L, defaultNotes = "جراحی انتهای ریشه (Apicoectomy / Root-End Surgery) - برداشتن نوک ریشه، دبریدمان پری‌اپیکال و پرکردن انتهای کانال", stages = "جلسه ۱: بی‌حسی، فلپ موکوپریواستئال، استئوتومی برای دسترسی به اپکس، برداشتن ۳ میلی‌متر انتهای ریشه، کورتاژ بافت گرانولوماتوز، آماده‌سازی حفره رتروگراد با اولتراسونیک، پرکردن انتهای کانال با MTA یا بایوسرامیک، بخیه. - جلسه ۲: کشیدن بخیه، رادیوگرافی کنترل و بررسی التیام", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Bone Graft", titleFa = "پیوند استخوان", defaultCost = 7000000L, defaultNotes = "پیوند استخوان فک (Alveolar Ridge Augmentation / Bone Graft) - افزایش حجم استخوان با ماده پیوندی برای مهیا کردن بستر ایمپلنت", stages = "جلسه ۱: فلپ تمام ضخامت، آماده‌سازی ناحیه، دکورتیکاسیون جهت خونرسانی، جایگذاری پودر استخوان (آلوگرافت/زنوگرافت/اتوگرافت)، پوشاندن با ممبران، بخیه بدون تنشن. - جلسه ۲: کشیدن بخیه و کنترل التیام", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Ceramic Veneer", titleFa = "لمینت سرامیکی", defaultCost = 5000000L, defaultNotes = "ونیر سرامیکی (Porcelain Laminate Veneer) - پوسته‌ی نازک سرامیکی برای اصلاح طرح لبخند دندان‌های قدامی", stages = "جلسه ۱: طراحی لبخند دیجیتال، تراش مینا (حدود ۰.۳-۰.۵ میلی‌متر)، قالب‌گیری، ونیر موقت. - جلسه ۲: تست رنگ، فرم و تطابق بیسکویتی. - جلسه ۳: باندینگ نهایی با سمان رزینی لایت کیور، حذف اضافات و تنظیم بایت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Clear Aligners", titleFa = "ارتودنسی شفاف", defaultCost = 30000000L, defaultNotes = "ارتودنسی با الاینرهای شفاف (Clear Aligner Therapy) - ردیف کردن دندان‌ها با پلاک‌های نامرئی متوالی", stages = "جلسه ۱: اسکن ۳ بعدی و طراحی دیجیتال (ClinCheck). - جلسه ۲: نصب اتچمنت‌های کامپوزیتی، تحویل سری اول الاینرها و آموزش. - مراجعات دوره‌ای (هر ۶-۸ هفته): بررسی پیشرفت، تحویل الاینرهای بعدی و انجام IPR در صورت نیاز. - جلسه پایانی: اسکن نهایی و سفارش ریتینرها", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Composite Filling", titleFa = "پرکردن کامپوزیت", defaultCost = 1500000L, defaultNotes = "ترمیم کامپوزیت دندان (Composite Resin Restoration) - ترمیم مستقیم همرنگ دندان با مواد لایت کیور", stages = "جلسه ۱: بی‌حسی، حذف پوسیدگی و بافت‌های ضعیف، اچینگ مینا و عاج، اپلای باندینگ، لایه‌گذاری کامپوزیت به روش اینکرمنتال، کیورینگ، پرداخت و پالیش نهایی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Crown Lengthening", titleFa = "جراحی افزایش طول تاج", defaultCost = 3000000L, defaultNotes = "اصلاح عرض بیولوژیک و بافت استخوان دور دندان (Crown Lengthening) - افزایش طول تاج کلینیکی با جراحی فلپ", stages = "جلسه ۱: جراحی فلپ، اصلاح استخوان (استئوتومی) و بخیه - جلسه ۲: کشیدن بخیه و ارزیابی لثه", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Cyst Enucleation", titleFa = "تخلیه کیست فکی", defaultCost = 4500000L, defaultNotes = "بیرون‌آوردن کیست ادنتوژنیک (Odontogenic Cyst Enucleation) - خارج کردن کیست فکی و کورتاژ استخوان اطراف", stages = "جلسه ۱: بی‌حسی، فلپ و استئوتومی، جدا کردن و انوکلئاسیون کامل کیست، کورتاژ استخوان، شست‌وشو، پرکردن نقص با پودر استخوان در صورت لزوم، بخیه. - جلسه ۲: کشیدن بخیه و کنترل", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Dental Bonding", titleFa = "باندینگ زیبایی", defaultCost = 1000000L, defaultNotes = "باندینگ کامپوزیت (Composite Bonding) - اصلاح جزئی شکل، فاصله یا تراش‌خوردگی دندان بدون تراش وسیع", stages = "جلسه ۱: انتخاب رنگ، اچینگ مینا، باندینگ، اعمال کامپوزیت فلو یا خمیری، شکل‌دهی و کیورینگ، پرداخت و پالیش", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Dental Implant", titleFa = "کاشت ایمپلنت", defaultCost = 12000000L, defaultNotes = "کاشت ایمپلنت دندانی (Dental Implant Placement) - جایگزینی ریشه دندان با فیکسچر تیتانیومی داخل استخوانی", stages = "جلسه ۱: مشاوره و CBCT، جراحی پانچ یا فلپ، آماده‌سازی استخوان با دریل‌های متوالی، کارگذاری فیکسچر، قرار دادن کاور اسکرو و بخیه. - جلسه ۲: کشیدن بخیه (یک هفته بعد). - جلسه ۳: پس از دوره اسئواینتگریشن (۳-۴ ماه)، نمایان‌سازی ایمپلنت و نصب هیلینگ اباتمنت. - جلسه ۴: قالب‌گیری از ایمپلنت. - جلسه ۵: تست اباتمنت و فریم. - جلسه ۶: تحویل روکش قطعی سمان‌شونده یا پیچ‌شونده", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Denture", titleFa = "پروتز متحرک", defaultCost = 8000000L, defaultNotes = "پروتز کامل یا پارسیل متحرک (Complete / Partial Removable Denture) - جایگزینی دندان‌های از دست رفته با پروتز متحرک", stages = "جلسه ۱: قالب‌گیری اولیه و ساخت تری اختصاصی. - جلسه ۲: قالب‌گیری نهایی با تری. - جلسه ۳: ثبت روابط بایت و انتخاب رنگ. - جلسه ۴: امتحان واکس. - جلسه ۵: تحویل پروتز و تنظیمات اولیه. - جلسه ۶: تنظیم نقاط فشار و زخم‌ها در جلسات پیگیری", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Expose & Bond", titleFa = "نمایان‌سازی دندان نهفته", defaultCost = 4000000L, defaultNotes = "نمایان‌سازی و باندینگ جراحی دندان نهفته (Surgical Exposure & Bonding of Impacted Tooth) - اتصال براکت جهت کشش ارتودنسی دندان کانین یا دیگر دندان‌های نهفته", stages = "جلسه ۱: بی‌حسی، فلپ باز، نمایان‌سازی تاج، هموستاز، اچ و باند براکت طلایی یا سرامیکی روی مینا، نخ لاستیکی یا زنجیر طلایی متصل به براکت، تطبیق فلپ یا پانسمان. - جلسه ۲: کشیدن بخیه و شروع کشش ارتودنسی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Fissure Sealant", titleFa = "فیشور سیلانت", defaultCost = 1000000L, defaultNotes = "فیشور سیلانت (Pit and Fissure Sealant) - پوشش پیشگیرانه شیارهای عمیق دندان‌های آسیاب", stages = "جلسه ۱: تمیز کردن شیارها با برس و پامیس، ایزولاسیون، اچینگ اسید فسفریک، شست‌وشو و خشک کردن، اعمال رزین سیلانت فلو، کیورینگ نوری و بررسی تطابق بایت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Fixed Orthodontics", titleFa = "ارتودنسی ثابت", defaultCost = 25000000L, defaultNotes = "ارتودنسی ثابت با براکت (Fixed Orthodontic Treatment / Braces) - منظم کردن دندان‌ها با براکت‌های فلزی یا سرامیکی", stages = "جلسه ۱: ثبت مدارک تشخیصی (عکس‌ها، قالب‌ها، رادیوگرافی). - جلسه ۲: باندینگ براکت‌ها و قرار دادن سیم اولیه. - جلسات ماهانه: تنظیم سیم، لیگاچور و زنجیرهای الاستومریک. - جلسه پایانی: ده‌باندینگ، پالیش مینا، تحویل ریتینرهای ثابت و متحرک", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Flap Surgery", titleFa = "جراحی فلپ لثه", defaultCost = 4000000L, defaultNotes = "جراحی فلپ پریودنتال (Periodontal Flap Surgery / Osseous Surgery) - دسترسی مستقیم به ریشه و استخوان برای درمان پاکت‌های عمیق", stages = "جلسه ۱: بی‌حسی، اینسیژن سولکولار و پارشیل تیکنس، بلند کردن فلپ، دبریدمان کامل گرانولیشن تیشو، جرم‌گیری عمیق ریشه و تسطیح، اصلاح استخوان (استئوپلاستی/استئوتومی) در صورت وجود نقایص، شست‌وشو، تطبیق فلپ و بخیه اینترپروگزیمال. - جلسه ۲: کشیدن بخیه و ارزیابی التیام اولیه", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Fluoride Therapy", titleFa = "فلورایدتراپی", defaultCost = 700000L, defaultNotes = "فلورایدتراپی موضعی (Topical Fluoride Application) - پیشگیری و توقف پوسیدگی‌های اولیه با وارنیش فلوراید", stages = "جلسه ۱: تمیز کردن دندان‌ها، ایزولاسیون نسبی، اعمال وارنیش فلوراید (۵٪ NaF) با برس مخصوص، خشک شدن خودبه‌خود، دستور عدم خوردن تا ۳۰ دقیقه", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Frenectomy", titleFa = "فرنکتومی", defaultCost = 1500000L, defaultNotes = "بریدن فرنوم لبی یا زبانی (Labial/Lingual Frenectomy) - اصلاح بند متصل به لثه که باعث دیاستم یا محدودیت زبانی می‌شود", stages = "جلسه ۱: بی‌حسی، برش نواری فرنوم با اسکالپل یا لیزر، جدا کردن فیبرهای نفوذی تا پریواستئوم، بخیه (در صورت نیاز). - جلسه ۲: کشیدن بخیه و تمرینات زبانی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Full Mouth Rehab", titleFa = "بازسازی کامل دهان", defaultCost = 30000000L, defaultNotes = "بازسازی کامل دندان‌ها (Full Mouth Rehabilitation / Reconstruction) - طرح درمان جامع ترمیم‌های وسیع و بازسازی بعد عمودی", stages = "طی جلسات متعدد: ثبت مدارک تشخیصی، افزایش بعد عمودی با اسپلینت، تهیه واکس‌آپ تشخیصی، آماده‌سازی دندان‌ها، ترمیم‌های موقت طولانی‌مدت، ساخت ترمیم‌های قطعی (روکش/بریج/ایمپلنت)، چسباندن قطعی و تنظیم اکلوزال", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Gingivectomy", titleFa = "جراحی لثه", defaultCost = 2000000L, defaultNotes = "جراحی برداشت لثه (Gingivectomy / Gingivoplasty) - اصلاح شکل و بلندی لثه، حذف پاکت‌های کاذب یا افزایش طول تاج بدون درگیری استخوان", stages = "جلسه ۱: بی‌حسی، مشخص‌سازی نقاط خونریزی‌دهنده با پریودنتال پروب، برداشتن بافت اضافی با چاقوی جراحی یا الکتروسرجری، پروفایل‌دهی (Gingivoplasty)، پانسمان پریودنتال (Coe-Pak). - جلسه ۲: بررسی التیام و بهداشت ناحیه", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "GTR", titleFa = "بازسازی هدایت‌شده", defaultCost = 5500000L, defaultNotes = "بازسازی هدایت‌شده بافتی (Guided Tissue Regeneration - GTR) - استفاده از ممبران برای بازسازی انساج پریودنتال از دست رفته", stages = "جلسه ۱: فلپ با ضخامت کامل، دبریدمان نقص استخوانی و سطح ریشه، جایگذاری ماده پیوندی (آلوگرافت/زنوگرافت)، پوشاندن با ممبران قابل جذب یا غیرقابل جذب، بخیه کاملاً پوشاننده. - جلسه ۲: کشیدن بخیه و کنترل التیام (در صورت وجود ممبران غیرقابل جذب، خارج کردن آن در جلسه دوم)", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Habit Breaker", titleFa = "دستگاه ترک عادت", defaultCost = 2000000L, defaultNotes = "دستگاه ضد عادت دهانی (Habit Breaking Appliance) - ترک عادت مکیدن انگشت یا اینترپوزیشن زبانی", stages = "جلسه ۱: قالب‌گیری، طراحی دستگاه (کریب فلزی یا اسپایک). - جلسه ۲: تحویل و تنظیم دستگاه، آموزش والدین. - جلسات پیگیری: بررسی زخم‌ها و ادامه درمان", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Home Bleaching", titleFa = "بلیچینگ خانگی", defaultCost = 1800000L, defaultNotes = "بلیچینگ با تری اختصاصی (Custom Tray Home Bleaching) - سفید کردن تدریجی دندان با ژل کارباماید پراکساید و تری شخصی", stages = "جلسه ۱: قالب‌گیری آلژینات، ساخت تری سفارشی نرم و شفاف، تحویل تری و ژل ۱۰-۱۶٪ کارباماید پراکساید، آموزش مدت و شیوه مصرف. - جلسه پیگیری: بررسی تغییر رنگ، حساسیت و تطابق تری", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Immediate Implant", titleFa = "ایمپلنت فوری", defaultCost = 13000000L, defaultNotes = "کاشت ایمپلنت فوری پس از کشیدن (Immediate Implant Placement) - کارگذاری ایمپلنت همزمان با کشیدن دندان و پرکردن گپ", stages = "جلسه ۱: کشیدن آتروماتیک دندان، کورتاژ و بررسی چهار دیواره سوکت، کارگذاری ایمپلنت با ثبات اولیه مناسب، قرار دادن بیومتریال گرافت در گپ بین ایمپلنت و استخوان، بخیه و گاهی تاج موقت فوری. - جلسات بعدی مشابه روند عادی ایمپلنت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "In-Office Bleaching", titleFa = "بلیچینگ مطب", defaultCost = 2500000L, defaultNotes = "بلیچینگ دندان در مطب (In-Office Tooth Whitening) - سفید کردن دندان‌ها با ژل پراکسید هیدروژن و فعال‌سازی نوری", stages = "جلسه ۱: ایزولاسیون لثه با رابردم یا ژل محافظ، اعمال ژل بلیچینگ (۳۵٪ هیدروژن پراکساید)، فعال‌سازی با نور LED یا لیزر، شست‌وشو و خنثی‌سازی، تکرار چرخه تا رسیدن به سایه دلخواه، ژل دسنسیبلایزر", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Inlay / Onlay", titleFa = "اینله / آنله", defaultCost = 3500000L, defaultNotes = "ترمیم غیرمستقیم اینله و آنله (Indirect Inlay / Onlay Restoration) - ترمیم سرامیکی یا کامپوزیتی ساخته‌شده خارج از دهان", stages = "جلسه ۱: بی‌حسی، تراش دقیق حفره، قالب‌گیری دیجیتال یا سنتی، ترمیم موقت. - جلسه ۲: حذف موقت، پروتست و تطابق، اچ و باندینگ، سمان‌کردن ترمیم قطعی با سمان رزینی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Night Guard", titleFa = "نایت گارد", defaultCost = 3000000L, defaultNotes = "محافظ شبانه اکلوزال (Occlusal Night Guard / Splint) - اسپلینت سخت یا نرم برای درمان بروکسیسم و کاهش سایش", stages = "جلسه ۱: قالب‌گیری و ثبت بایت با موم یا جیگ. - جلسه ۲: تحویل اسپلینت سخت آکریلی، تنظیم نقاط تماس اکلوزالی، آموزش نحوه استفاده و نگهداری", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Oral Biopsy", titleFa = "بیوپسی دهانی", defaultCost = 1800000L, defaultNotes = "نمونه‌برداری از بافت دهان (Oral Mucosal Biopsy) - تشخیص ضایعات مشکوک با برداشت نمونه", stages = "جلسه ۱: بی‌حسی، تثبیت بافت، بیوپسی اکسیژنال یا پانچ، هموستاز، بخیه. ارسال نمونه به پاتولوژی. - جلسه ۲: کشیدن بخیه و تفسیر جواب پاتولوژی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Pediatric Pulpotomy", titleFa = "پالپوتومی اطفال", defaultCost = 2500000L, defaultNotes = "پالپوتومی دندان شیری (Pediatric Pulpotomy / Partial Pulpectomy) - حفظ دندان شیری عفونی‌شده تا زمان رویش دندان دائمی", stages = "جلسه ۱: بی‌حسی، ایزولاسیون رابردم، حذف پوسیدگی، باز کردن پالپ‌چمبر، قطع پالپ تاجی و کنترل خونریزی، قرار دادن ماده دارویی (فرموکرزول یا MTA)، ترمیم تاج با کامپوزیت یا روکش استیل", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "PFM Bridge", titleFa = "روکش بریج پرسلن", defaultCost = 11000000L, defaultNotes = "بریج فلز-سرامیک (Porcelain Fused to Metal Bridge) – جایگزینی دندان‌ها با فریم فلزی و پوشش پرسلن لایه‌ای", stages = "جلسه ۱: تراش دندان‌های پایه با مارجین شولدر باول، قالب‌گیری نهایی، روکش‌های موقت. - جلسه ۲: تست فریم فلزی (بررسی نشست کامل و مارجین). - جلسه ۳: تست بیسکویتی (بررسی رنگ، فرم و کانتاکت‌های پروگزیمال). - جلسه ۴: چسباندن دائم با سمان گلاس‌آینومر یا رزینی، پالیش و تنظیم بایت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "PFM Crown", titleFa = "روکش پرسلن تک‌واحدی", defaultCost = 4200000L, defaultNotes = "روکش تک واحدی فلز-سرامیک (Single-Unit Porcelain Fused to Metal Crown) – روکش استاندارد با فریم فلزی و پرسلن فلدسپاتیک", stages = "جلسه ۱: بی‌حسی، تراش دندان، قالب‌گیری، ساخت روکش موقت. - جلسه ۲: تست فریم فلزی (تطابق مارجین). - جلسه ۳: تست بیسکویتی و تأیید نهایی رنگ. - جلسه ۴: چسباندن دائم با سمان دائمی مناسب، تنظیم اکلوزن و پالیش حاشیه‌ها", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Post & Core", titleFa = "پست و کور", defaultCost = 2000000L, defaultNotes = "پایه و هسته داخل ریشه (Post and Core Build-up) - بازسازی تاج دندان درمان‌ریشه شده با فایبر پست و کامپوزیت", stages = "جلسه ۱: تخلیه بخشی از گوتاپرکای کانال، آماده‌سازی فضای پست، اچ و سمان فایبر پست، کور کامپوزیتی مستقیم. (یا قالب‌گیری برای کور ریختگی غیرمستقیم در جلسه جداگانه)", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Posterior Infected RCT", titleFa = "عصب‌کشی خلفی عفونی", defaultCost = 4200000L, defaultNotes = "درمان ریشه دندان خلفی عفونی (Posterior Infected Root Canal Treatment) – درمان کانال‌های متعدد مولر/پرمولر با عفونت فعال", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، حذف ترمیم قدیمی و پوسیدگی، لوکالیز کردن تمامی اوریفیس‌ها (MB, ML, D, P)، تعیین طول کانال‌ها، تمیزکردن مکانیکی با فایل‌های NiTi، شست‌وشو با سدیم هیپوکلریت ۵.۲۵٪ و EDTA 17%، خشک‌کردن، مدیکیشن کلسیم هیدروکساید، پانسمان موقت. - جلسه ۲: تأیید عدم وجود علائم، شست‌وشوی نهایی و اُبچوریشن سه‌بعدی با گوتاپرکا و سیلر رزینی/بایوسرامیک، ترمیم کرونال", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Posterior RCT", titleFa = "عصب‌کشی خلفی", defaultCost = 3800000L, defaultNotes = "درمان ریشه دندان خلفی غیرعفونی (Posterior Non-Infected Root Canal Treatment) – پالپکتومی مولر/پرمولر در غیاب علائم عفونی حاد", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، خارج‌کردن پالپ، پاک‌سازی و شکل‌دهی تمام کانال‌ها، شست‌وشو و در صورت امکان اُبچوریشن در همان جلسه (Single-visit RCT). در صورت وقت‌گیر بودن یا خستگی بیمار: مدیکیشن، پانسمان و جلسه دوم برای اُبچوریشن", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Re-RCT", titleFa = "درمان مجدد ریشه", defaultCost = 6000000L, defaultNotes = "درمان مجدد ریشه (Root Canal Retreatment) - تخلیه مواد پرکننده قدیمی و پاک‌سازی مجدد کانال‌ها پس از شکست درمان اولیه", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، تخلیه ترمیم تاجی، حذف گوتاپرکا و سیلر با حلال و فایل‌های روتاری، پاک‌سازی مضاعف، شناسایی کانال‌های از دست رفته، شست‌وشو با هیپوکلریت و EDTA، مدیکیشن کلسیم هیدروکساید، پانسمان موقت. - جلسه ۲: تأیید عدم وجود علائم بالینی، شست‌وشوی نهایی، ابچوریشن با گوتاپرکا و سیلر بایوسرامیک، ترمیم کرونال", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Root Canal Treatment", titleFa = "عصب‌کشی", defaultCost = 3500000L, defaultNotes = "درمان ریشه (Root Canal Therapy / RCT) - حذف پالپ عفونی، پاک‌سازی کانال‌ها و پرکردن سه‌بعدی فضای کانال", stages = "جلسه ۱: بی‌حسی، ایزولاسیون رابردم، حذف پوسیدگی و پالپ‌چمبر، کارگذاری فایل‌های روتاری برای تعیین طول الکترونیکی، پاک‌سازی و شکل‌دهی (Cleaning & Shaping)، شست‌وشوی فراوان با هیپوکلریت سدیم و EDTA، خشک‌کردن، قرار دادن مدیکیشن کلسیم هیدروکساید، پانسمان موقت. - جلسه ۲: بی‌حسی، برداشتن پانسمان، شست‌وشوی نهایی و فعال‌سازی التراسونیک، خشک کردن با پیپرپوینت، پرکردن کانال‌ها با گوتاپرکا و سیلر (اُبچوریشن)، ترمیم کرونال با کامپوزیت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Scaling & Polishing", titleFa = "جرم‌گیری و بروساژ", defaultCost = 1200000L, defaultNotes = "جرم‌گیری و پالیش دندان (Prophylaxis / Scaling & Polishing) - پاک‌سازی جرم‌های فوق و زیر لثه‌ای و پلاک", stages = "جلسه ۱: جرم‌گیری التراسونیک (کاویترون)، کورتاژ دستی با کورت‌های گریسی برای نواحی زیر لثه‌ای سطحی، بروساژ با خمیر پروفیلاکسی و رابردام، فلورایدتراپی انتهایی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Scaling & Root Planing", titleFa = "جرم‌گیری عمیق و تسطیح سطح ریشه", defaultCost = 2500000L, defaultNotes = "جرم‌گیری و تسطیح سطح ریشه (Scaling and Root Planing - SRP) - درمان غیرجراحی بیماری‌های پریودنتال و کاهش عمق پاکت", stages = "جلسه ۱: بی‌حسی موضعی، جرم‌گیری کامل دو کوادرانت با اولتراسونیک و کورت‌های گریسی مخصوص عمق، تسطیح سطح ریشه تا رسیدن به سطح صاف شیشه‌ای. - جلسه ۲: تکرار برای دو کوادرانت مقابل. - جلسه ۳ (بعد از ۴-۶ هفته): ارزیابی مجدد عمق پاکت‌ها و خونریزی (Periodontal Re-evaluation)", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Simple Extraction", titleFa = "کشیدن دندان ساده", defaultCost = 800000L, defaultNotes = "کشیدن دندان ساده (Simple Tooth Extraction) - خارج کردن دندان سالم یا پوسیده با حرکت لوکسیشن و الوئیشن", stages = "جلسه ۱: بی‌حسی، لوکسیشن با الواتور، خارج کردن با فورسپس، کورتاژ سوکت، گاز استریل. دستورات پس از کشیدن", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Sinus Lift", titleFa = "سینوس لیفت", defaultCost = 8000000L, defaultNotes = "سینوس لیفت و پیوند استخوان (Sinus Floor Elevation & Bone Graft) - بالا بردن غشای سینوس ماگزیلاری و بازسازی استخوان", stages = "جلسه ۱: فلپ لترال، استئوتومی پنجره استخوانی، جدا کردن و بالا بردن دقیق غشای اشنایدریان، پر کردن فضای ایجاد شده با پودر استخوان، پوشاندن پنجره با ممبران، بخیه. - جلسه ۲: کشیدن بخیه و رادیوگرافی کنترل", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Socket Preservation", titleFa = "حفظ ساکت", defaultCost = 3000000L, defaultNotes = "حفظ استخوان ساکت دندانی (Alveolar Ridge Preservation / Socket Grafting) - جلوگیری از تحلیل استخوان پس از کشیدن دندان برای ایمپلنت آینده", stages = "جلسه ۱: کشیدن آتروماتیک و دبریدمان سوکت، جایگذاری پودر استخوان در سوکت، پوشاندن با ممبران کلاژنی یا PRF، بخیه. - جلسه ۲: کشیدن بخیه و ارزیابی", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Soft Tissue Graft", titleFa = "پیوند بافت نرم", defaultCost = 4500000L, defaultNotes = "پیوند لثه (Soft Tissue Graft / Connective Tissue Graft) - پوشش ریشه‌های عریان و افزایش لثه چسبنده", stages = "جلسه ۱: بی‌حسی، آماده‌سازی بستر گیرنده و دبریدمان سطح ریشه، برداشت گرافت بافت همبند از کام (Trap-door technique)، بخیه در ناحیه دهنده و بخیه تثبیت گرافت در ناحیه گیرنده با تکنیک تونل یا پاکت. - جلسه ۲: کشیدن بخیه و کنترل تغذیه گرافت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Space Maintainer", titleFa = "نگهدارنده فضا", defaultCost = 1800000L, defaultNotes = "فضانگهدار دندان شیری (Space Maintainer Appliance) - حفظ فضای دندان شیری از دست رفته برای رویش دندان دائمی", stages = "جلسه ۱: قالب‌گیری و سفارش دستگاه (بند و لوپ یا دیستال شو). - جلسه ۲: تحویل و سمان‌کردن دستگاه، آموزش بهداشت. - جلسات پیگیری: کنترل رویش و اپن‌بایت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "SSC Crown", titleFa = "روکش استیل شیری", defaultCost = 2000000L, defaultNotes = "روکش استیل دندان شیری (Stainless Steel Crown - SSC) - بازسازی دندان شیری خلفی دارای تخریب وسیع", stages = "جلسه ۱: بی‌حسی، ایزولاسیون، آماده‌سازی دندان و کاهش ابعاد، انتخاب سایز روکش، تطابق حاشیه و کانتورینگ، سمنتاسیون با گلاس‌آینومر، کنترل بایت", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "TMD Splint", titleFa = "اسپلینت TMD", defaultCost = 3500000L, defaultNotes = "اسپلینت درمانی مفصل گیجگاهی فکی (Temporomandibular Joint Stabilization Splint) - کاهش درد و اختلالات عضلانی-مفصلی", stages = "جلسه ۱: معاینه جامع TMD، قالب‌گیری و ثبت بایت در رابطه مرکزی. - جلسه ۲: تحویل اسپلینت ریپوزیشنینگ یا تثبیتی، تنظیم دقیق راهنماها و تماس‌ها. - جلسات پیگیری منظم جهت تنظیم", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Wisdom Surgery", titleFa = "جراحی دندان عقل", defaultCost = 1200000L, defaultNotes = "کشیدن دندان عقل نهفته / نیمه‌نهفته (Surgical Extraction of Impacted Wisdom Tooth) - خارج کردن دندان عقل با فلپ و استئوتومی", stages = "جلسه ۱: بی‌حسی، فلپ پاکتی مثلثی، استئوتومی در صورت نیاز، قطعه‌قطعه کردن دندان با هندپیس جراحی، لوکسیشن و خروج قطعات، کورتاژ و شست‌وشو، بخیه. - جلسه ۲: کشیدن بخیه و کنترل ترمیم", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Zirconia Bridge", titleFa = "روکش بریج زیرکونیا", defaultCost = 14000000L, defaultNotes = "بریج تمام زیرکونیا (Full-Contour Zirconia Fixed Bridge) – جایگزینی دندان‌های از دست رفته با فریم ورک زیرکونیایی مونولیتیک با استحکام بالا", stages = "جلسه ۱: بی‌حسی، تراش دندان‌های پایه با طراحی شولدر یا چمفر عمیق، نخ‌گذاری، قالب‌گیری دیجیتال (اسکن داخل دهانی) یا سیلیکونی سنگین-سبک، ثبت بایت و شید، بریج موقت. - جلسه ۲: تست فریم زیرکونیا (بررسی تطابق مارجینال و کانتور). - جلسه ۳: تحویل بریج قطعی، سمان‌کردن با سمان رزینی (پس از پروتکل باندینگ زیرکونیا)، حذف اضافات و تنظیم اکلوزن", isStarred = true))
        database.treatmentTemplateDao().insertTemplate(TreatmentTemplate(title = "Zirconia Crown", titleFa = "روکش زیرکونیا تک‌واحدی", defaultCost = 5500000L, defaultNotes = "روکش تک واحدی زیرکونیا (Single-Unit Full Zirconia Crown) – روکش تمام زیرکونیا برای دندان‌های خلفی با نیاز به استحکام بالا", stages = "جلسه ۱: بی‌حسی، تراش دندان با کاهش اکلوزال ۱.۵-۲ میلی‌متر، چمفر ۰.۵ میلی‌متر، اسکن دیجیتال یا قالب‌گیری سنتی، روکش موقت. - جلسه ۲: تست روکش (بررسی تطابق، کانتاکت‌ها و رنگ)، سمان‌کردن قطعی با سمان رزینی Dual-Cure پس از سندبلاست و پرایمر MDP", isStarred = true))

        }

        if (repository.allBankAccounts.first().isEmpty()) {
            database.bankAccountDao().insertBankAccount(
                com.example.data.BankAccount(
                    bankName = "صندوق نقدی مطب",
                    cardHolder = "صندوق نقدی",
                    accountNumber = "CASH-01",
                    balance = 0L
                )
            )
            database.bankAccountDao().insertBankAccount(
                com.example.data.BankAccount(
                    bankName = "تنخواه گردان مطب",
                    cardHolder = "تنخواه گردان",
                    accountNumber = "PETTY-01",
                    balance = 0L
                )
            )
        }

        // Do not duplicate or overwrite user-maintained reference/settings rows.
        // Seed کامل سرفصل‌های حساب شخصی از ساختار درختی پرمیس (۱۶۵ حساب —
        // هزینه‌ها/درآمدها/دارایی‌ها/بدهی‌ها/سرمایه/بدهکاران/بستانکاران).
        // هم برای نصب تازه و هم نصب‌های موجود اجرا می‌شود؛ فقط موارد غایب درج
        // می‌شوند (dedup با کلید دسته|والد|نام) و از مسیر Repository تا به سرور سینک شوند.
        if (!prefs.getBoolean("parmis_subaccounts_seeded_v1", false)) {
            val existingSubs = repository.allSubAccounts.first()
            val existingKeys = existingSubs
                .map { "${it.category}|${it.parentName}|${it.name}" }
                .toHashSet()
            com.example.util.ParmisDefaultAccounts.ALL.forEach { acc ->
                val key = "${acc.category}|${acc.parent}|${acc.name}"
                if (key !in existingKeys) {
                    repository.insertSubAccount(
                        PersonalSubAccount(
                            name = acc.name,
                            category = acc.category,
                            parentName = acc.parent,
                            ledgerSide = acc.ledgerSide
                        )
                    )
                }
            }
            prefs.edit().putBoolean("parmis_subaccounts_seeded_v1", true).apply()
        }

        // «صندوق‌ها»ی پرمیس (کیف پول / گاو صندوق / منزل) به‌عنوان حساب‌های نقدی
        if (!prefs.getBoolean("parmis_funds_seeded_v1", false)) {
            val existingBanks = repository.allBankAccounts.first()
            com.example.util.ParmisDefaultAccounts.CASH_FUNDS.forEach { (fundName, code) ->
                if (existingBanks.none { it.bankName == fundName }) {
                    repository.insertBankAccount(
                        com.example.data.BankAccount(
                            bankName = fundName,
                            cardHolder = fundName,
                            accountNumber = code,
                            balance = 0L
                        )
                    )
                }
            }
            prefs.edit().putBoolean("parmis_funds_seeded_v1", true).apply()
        }

        if (repository.clinicSettings.first() == null) {
            repository.insertSettings(
                ClinicSettings(
                    id = 1,
                    clinicName = "مطب دندانپزشکی",
                    doctorName = " ",
                    childrenNames = "",
                    staffNames = "منشی مطب"
                )
            )
        }
    }

    /**
     * پیدا کردن بیماران کاندیدای معاینه و یادآوری دوره‌ای ۶ ماهه (Recall Patients)
     */
    fun getPatientRecallCandidates(): List<Patient> {
        val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
        return patients.value.filter { p ->
            val lastApptDate = appointments.value.filter { it.patientName == p.name }.maxOfOrNull { it.date } ?: p.createdAt
            lastApptDate < sixMonthsAgo
        }
    }
    val allCalendarEvents = repository.allCalendarEvents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 5 Separate Switches for calendar occasion categories
    val showShamsiEvents = MutableStateFlow(prefs.getBoolean("show_shamsi_events", true))
    val showQamariEvents = MutableStateFlow(prefs.getBoolean("show_qamari_events", true))
    val showGlobalEvents = MutableStateFlow(prefs.getBoolean("show_global_events", true))
    val showPersonalEvents = MutableStateFlow(prefs.getBoolean("show_personal_events", true))
    val showOfficialHolidays = MutableStateFlow(prefs.getBoolean("show_official_holidays", true))

    fun setShowShamsiEvents(show: Boolean) {
        showShamsiEvents.value = show
        prefs.edit().putBoolean("show_shamsi_events", show).apply()
    }

    fun setShowQamariEvents(show: Boolean) {
        showQamariEvents.value = show
        prefs.edit().putBoolean("show_qamari_events", show).apply()
    }

    fun setShowGlobalEvents(show: Boolean) {
        showGlobalEvents.value = show
        prefs.edit().putBoolean("show_global_events", show).apply()
    }

    fun setShowPersonalEvents(show: Boolean) {
        showPersonalEvents.value = show
        prefs.edit().putBoolean("show_personal_events", show).apply()
    }

    fun setShowOfficialHolidays(show: Boolean) {
        showOfficialHolidays.value = show
        prefs.edit().putBoolean("show_official_holidays", show).apply()
    }

    private suspend fun seedDefaultCalendarEvents() {
        // رفعِ باگِ «هر رویداد چند بار تکرار شده»: هر بار این تابع دوباره اجرا شود (نصب مجدد،
        // دستگاه دوم روی همان حساب، پاک کردن کامل داده، یا اجرای مجدد گیت نسخه‌ی
        // calendar_events_1405_pdf_v3 روی یک DB تازه)، لیست رویدادهای مرجع از نو ساخته و
        // insert می‌شود. AppCalendarEvent هیچ ایندکس/محدودیت یکتایی روی syncId ندارد و Room
        // با OnConflictStrategy.REPLACE فقط روی primary key (id) دیدوپ می‌کند، پس این insert
        // همیشه ردیف‌های محلی تازه می‌سازد؛ SyncEngine هم همین ردیف‌ها را (even پس از رفع
        // تصادفی‌بودن syncId) به‌عنوان رکورد جدید push می‌کرد چون در سمت سرور رکورد قبلی
        // toposn نداشت که پاک/جایگزین شود. راه‌حل: قبل از هر seed، ابتدا رویدادهای غیرشخصی
        // قبلی (هم محلی و هم صف حذف سمت سرور از طریق enqueueSyncOp DELETE در
        // deleteNonPersonalCalendarEvents) پاک می‌شوند، سپس مجموعه‌ی مرجع دوباره درج می‌شود.
        // این تضمین می‌کند که seed مجدد همیشه دقیقاً یک نسخه از هر رویداد باقی می‌گذارد، نه
        // انباشتن نسخه‌های تکراری از هر بار اجرا.
        repository.deleteNonPersonalCalendarEvents()
        val events = com.example.util.DefaultCalendarEvents.parseCsvToCalendarEvents(
            com.example.util.DefaultCalendarEvents.DEFAULT_CSV
        )
        repository.insertCalendarEvents(events)
    }

    fun importCalendarEventsFromCsv(csvContent: String, replace: Boolean = true): Boolean {
        return try {
            val events = com.example.util.DefaultCalendarEvents.parseCsvToCalendarEvents(csvContent)
            if (events.isEmpty()) return false
            viewModelScope.launch {
                if (replace) repository.deleteNonPersonalCalendarEvents()
                repository.insertCalendarEvents(events)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportCalendarEventsToCsv(): String {
        val list = allCalendarEvents.value
        val sb = java.lang.StringBuilder()
        sb.append("دسته‌بندی,تاریخ شمسی (روز/ماه),تاریخ قمری (روز/ماه),تاریخ میلادی (روز/ماه),مناسبت,تعطیل رسمی در ایران,توضیحات,مناسبت شخصی\n")
        for (event in list) {
            val category = event.category
            val shamsiStr = if (event.shamsiDay != null && event.shamsiMonth != null) {
                if (event.shamsiYear != null) {
                    "${event.shamsiYear}/${event.shamsiMonth.toString().padStart(2, '0')}/${event.shamsiDay.toString().padStart(2, '0')}"
                } else {
                    "${event.shamsiDay.toString().padStart(2, '0')}/${event.shamsiMonth.toString().padStart(2, '0')}"
                }
            } else ""
            
            val qamariStr = if (event.qamariDay != null && event.qamariMonth != null) {
                "${event.qamariDay.toString().padStart(2, '0')}/${event.qamariMonth.toString().padStart(2, '0')}"
            } else ""
            
            val miladiStr = if (event.miladiDay != null && event.miladiMonth != null) {
                "${event.miladiDay.toString().padStart(2, '0')}/${event.miladiMonth.toString().padStart(2, '0')}"
            } else ""
            
            val title = if (event.title.contains(",") || event.title.contains("\"")) {
                "\"${event.title.replace("\"", "\"\"")}\""
            } else {
                event.title
            }
            
            val isHoliday = if (event.isHolidayInIran) "بله" else "خیر"
            
            val desc = if (event.description.contains(",") || event.description.contains("\"")) {
                "\"${event.description.replace("\"", "\"\"")}\""
            } else {
                event.description
            }
            
            val personal = event.personalType
            
            sb.append("$category,$shamsiStr,$qamariStr,$miladiStr,$title,$isHoliday,$desc,$personal\n")
        }
        return sb.toString()
    }

    fun getCustomOccasionsForDay(jy: Int, jm: Int, jd: Int): List<com.example.util.CalendarOccasion> {
        val list = allCalendarEvents.value
        val currentLang = selectedLanguage.value.lowercase()

        var (gy, gm, gd) = com.example.util.PersianCalendarHelper.jalaliToGregorian(jy, jm, jd)
        var qDay: Int? = null
        var qMonth: Int? = null

        if (jy == 1405) {
            val threeCal = com.example.util.PersianCalendarHelper.get3CalendarsFor1405(jm, jd)
            qDay = threeCal.qamari.day
            qMonth = threeCal.qamari.month
            gd = threeCal.miladi.day
            gm = threeCal.miladi.month
            gy = threeCal.miladi.year
        } else {
            val timestamp = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val ld = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val hijriDate = java.time.chrono.Chronology.of("Hijrah-umalqura").date(ld)
                    qMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                    qDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                }
            } catch (e: Exception) {}
        }

        // If language is NOT Persian, show regional occasions for that specific language + personal occasions
        if (currentLang != "fa") {
            val regionalList = com.example.util.RegionalOccasionsProvider.getDayOccasionsForLanguage(
                langCode = currentLang,
                gd = gd, gm = gm, gy = gy,
                qd = qDay, qm = qMonth
            )
            val result = mutableListOf<com.example.util.CalendarOccasion>()
            for (reg in regionalList) {
                result.add(
                    com.example.util.CalendarOccasion(
                        titleFa = reg.title,
                        isHoliday = reg.isHoliday,
                        isPersonal = false
                    )
                )
            }
            // Add any user-defined personal events
            val personalEvents = list.filter { it.category == "شخصی" || it.personalType.isNotEmpty() }
            for (event in personalEvents) {
                if ((event.shamsiDay == jd && event.shamsiMonth == jm) || (event.miladiDay == gd && event.miladiMonth == gm)) {
                    val prefix = when (event.personalType) {
                        "تولد" -> "🎂 "
                        "فوت" -> "🕯️ "
                        "طلاق" -> "💔 "
                        "ازدواج" -> "💍 "
                        else -> "📌 "
                    }
                    result.add(
                        com.example.util.CalendarOccasion(
                            titleFa = "$prefix${event.title}",
                            isHoliday = event.isHolidayInIran,
                            isPersonal = true
                        )
                    )
                }
            }
            return result
        }

        if (list.isEmpty()) return emptyList()

        val matched = list.filter { event ->
            var isMatch = false
            
            if (event.shamsiDay == jd && event.shamsiMonth == jm) {
                if (event.shamsiYear != null) {
                    if (event.shamsiYear == jy) {
                        isMatch = true
                    }
                } else {
                    isMatch = true
                }
            }
            
            if (event.miladiDay == gd && event.miladiMonth == gm) {
                isMatch = true
            }
            
            if (qDay != null && qMonth != null && event.qamariDay == qDay && event.qamariMonth == qMonth) {
                isMatch = true
            }
            
            if (!isMatch) return@filter false

            val isReligious = event.category.contains("مذهب") || event.category.contains("قمر")
            val isIranian = event.category.contains("ملی") || event.category.contains("ایران") || event.category.contains("باستان") || event.category.contains("معاصر") || event.category.contains("شمسی")
            val isGlobal = event.category.contains("جهان") || event.category.contains("میلاد") || event.category.contains("miladi")
            val isPersonal = event.category == "شخصی" || event.personalType.isNotEmpty()

            if (event.isHolidayInIran && !showOfficialHolidays.value) {
                return@filter false
            }

            if (isPersonal) {
                showPersonalEvents.value
            } else if (isReligious) {
                showQamariEvents.value
            } else if (isIranian) {
                showShamsiEvents.value
            } else if (isGlobal) {
                showGlobalEvents.value
            } else {
                true
            }
        }

        return matched.map { event ->
            var displayName = event.title
            if (event.category == "شخصی" && event.personalType.isNotEmpty()) {
                val prefix = when (event.personalType) {
                    "تولد" -> "🎂 تولد: "
                    "فوت" -> "🕯️ سالگرد فوت: "
                    "طلاق" -> "💔 سالگرد طلاق: "
                    "ازدواج" -> "💍 سالگرد ازدواج: "
                    else -> "📌 ${event.personalType}: "
                }
                displayName = prefix + event.title
                if (event.shamsiYear != null) {
                    val age = jy - event.shamsiYear
                    if (age > 0) {
                        displayName += " ($age سال)"
                    }
                }
            }
            val isPersonal = event.category == "شخصی" || event.personalType.isNotEmpty()
            com.example.util.CalendarOccasion(
                titleFa = displayName,
                isHoliday = event.isHolidayInIran,
                isPersonal = isPersonal
            )
        }
    }

    fun isCustomHoliday(jy: Int, jm: Int, jd: Int): Boolean {
        if (com.example.util.PersianCalendarHelper.getDayOfWeekIndex(jy, jm, jd) == 6) return true
        val custom = getCustomOccasionsForDay(jy, jm, jd)
        return custom.any { it.isHoliday }
    }

    fun deleteCalendarEvent(event: AppCalendarEvent) {
        if (!authorizeDomain("calendar", "DELETE_CALENDAR_EVENT", isDelete = true)) return
        viewModelScope.launch {
            repository.deleteCalendarEvent(event)
        }
    }

    fun addPersonalOccasion(
        title: String,
        day: Int,
        month: Int,
        year: Int?,
        personalType: String,
        isHoliday: Boolean = false,
        description: String = ""
    ) {
        if (!authorizeDomain("calendar", "ADD_CALENDAR_EVENT")) return
        viewModelScope.launch {
            try {
                val event = AppCalendarEvent(
                    category = "شخصی",
                    shamsiDay = day,
                    shamsiMonth = month,
                    shamsiYear = year,
                    title = title,
                    isHolidayInIran = isHoliday,
                    description = description,
                    personalType = personalType
                )
                repository.insertCalendarEvent(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addPersonalOccasion(title: String, shamsiDateStr: String, personalType: String, isHoliday: Boolean = false, description: String = "") {
        if (!authorizeDomain("calendar", "ADD_CALENDAR_EVENT")) return
        val parsed = com.example.util.DefaultCalendarEvents.parseShamsiDateString(shamsiDateStr)
        if (parsed != null) {
            val (day, month, year) = parsed
            addPersonalOccasion(title, day, month, year, personalType, isHoliday, description)
        }
    }

    fun getPrescriptionsByPatient(patientId: Long) = repository.prescriptionsForPatient(patientId)
    fun insertPrescription(prescription: com.example.data.Prescription) = viewModelScope.launch {
        val patientSyncId = patients.value.find { it.id == prescription.patientId }?.syncId ?: ""
        val treatmentSyncId = allTreatments.value.find { it.id == prescription.treatmentId }?.syncId ?: ""
        repository.insertPrescription(prescription.copy(patientSyncId = patientSyncId, treatmentSyncId = treatmentSyncId))
    }
    fun deletePrescription(prescription: com.example.data.Prescription) = viewModelScope.launch { repository.deletePrescription(prescription) }

    
    // Staff Members (Clinical Staff & Profiles)
    val allStaffMembers = repository.allStaffMembers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ————————————————————————————————————————————————
    // پرسنل فعال: «یک پزشک فعال» و «یک دستیار فعال» — منبع واحد نام‌ها جدول staff_members است
    // (doctorName/staffNames قدیمی فقط برای سازگاریِ نمایشِ مقادیر قدیمی نگه داشته شده‌اند)
    // ————————————————————————————————————————————————
    val activeDoctor = combine(settings, allStaffMembers) { s, staff ->
        staff.firstOrNull { it.syncId == s.activeDoctorSyncId && !it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeAssistant = combine(settings, allStaffMembers) { s, staff ->
        staff.firstOrNull { it.syncId == s.activeAssistantSyncId && !it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** syncId دستیار فعال — برای پیش‌فرضِ انتخاب دستیار در تمام فعالیت‌ها (درخواست ۶) */
    val activeAssistantSyncIdsFlow = settings.map { s ->
        if (s.activeAssistantSyncId.isNotBlank()) listOf(s.activeAssistantSyncId) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActiveDoctor(staff: com.example.data.StaffMember) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = settings.value
            repository.insertSettings(
                current.copy(activeDoctorSyncId = staff.syncId, doctorName = staff.fullName)
            )
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک بعداً تلاش می‌کند */ }
        }
    }

    fun setActiveAssistant(staff: com.example.data.StaffMember) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = settings.value
            repository.insertSettings(
                current.copy(activeAssistantSyncId = staff.syncId, staffNames = staff.fullName)
            )
            try { syncEngine.pushPending() } catch (e: Exception) { /* سینک بعداً تلاش می‌کند */ }
        }
    }

    fun clearActiveDoctor() {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSettings(settings.value.copy(activeDoctorSyncId = ""))
        }
    }

    fun clearActiveAssistant() {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSettings(settings.value.copy(activeAssistantSyncId = ""))
        }
    }

    /** چیدمان کارت‌های داشبورد (کلیدهای جدا شده با کاما) — ذخیره در ClinicSettings.dashboardCardOrder */
    fun updateDashboardCardOrder(orderCsv: String) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSettings(settings.value.copy(dashboardCardOrder = orderCsv))
        }
    }

    /** نمایش کارت‌های تنظیمات — JSON {"key":"NORMAL|HIDDEN"}؛ کارت‌های HIDDEN فقط از منوی مخفی دیده می‌شوند */
    fun updateSettingsCardVisibility(json: String) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSettings(settings.value.copy(settingsCardVisibility = json))
        }
    }

    /** آواتار حساب کاربری — مجزا از لوگوی مطب؛ بلافاصله آپلود می‌شود (همان الگوی updateClinicLogo) */
    fun updateClinicAvatar(localFilePath: String) {
        if (!authorizeDomain("settings", "UPDATE_CLINIC_SETTINGS", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            isMediaUploading.value = true
            try {
                repository.insertSettings(settings.value.copy(avatarFileId = localFilePath))
                val uploadedFileId = uploadLocalFileOrNull(localFilePath)
                if (uploadedFileId != null) {
                    repository.insertSettings(settings.value.copy(avatarFileId = uploadedFileId))
                    com.example.ui.screens.deleteLocalStagingFileQuietly(localFilePath)
                    try {
                        syncEngine.pushPending()
                    } catch (e: Exception) { /* سینک بعداً تلاش می‌کند */ }
                }
            } finally {
                isMediaUploading.value = false
            }
        }
    }

    fun insertStaffMember(staff: com.example.data.StaffMember) {
        if (!authorizeDomain("settings", "ADD_STAFF_MEMBER", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertStaffMember(staff)
        }
    }

    fun updateStaffMember(staff: com.example.data.StaffMember) {
        if (!authorizeDomain("settings", "UPDATE_STAFF_MEMBER", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateStaffMember(staff)
        }
    }

    fun deleteStaffMember(staff: com.example.data.StaffMember) {
        if (!authorizeDomain("settings", "DELETE_STAFF_MEMBER", isDelete = true, doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStaffMember(staff)
        }
    }

    /** Uploads a freshly picked local staff photo immediately and stores the resulting fileId — with instant local fallback. */
    fun updateStaffPhoto(staff: com.example.data.StaffMember, localFilePath: String) {
        if (!authorizeDomain("settings", "UPDATE_STAFF_MEMBER", doctorOnly = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            isMediaUploading.value = true
            try {
                repository.updateStaffMember(staff.copy(photoFileId = localFilePath))
                val uploadedFileId = uploadLocalFileOrNull(localFilePath)
                if (uploadedFileId != null) {
                    repository.updateStaffMember(staff.copy(photoFileId = uploadedFileId))
                    com.example.ui.screens.deleteLocalStagingFileQuietly(localFilePath)
                }
            } finally {
                isMediaUploading.value = false
            }
        }
    }

    suspend fun bootstrapAfterLogin(onProgress: (Float) -> Unit = {}) {
        // قفل سراسری: از اجرای همزمان bootstrap/pullAll در اثر رقابت LaunchedEffect auto-login
        // و callback بازگشت از OAuth (گوگل) یا دکمه لاگین جلوگیری می‌کند.
        bootstrapMutex.withLock {
            // اگر همین حساب قبلاً در این اجرای اپ بوت‌استرپش کامل شده، دوباره کاری نکن.
            val resolvedAccountId = try {
                com.example.util.Supabase.resetAccountIdCache()
                com.example.util.Supabase.ensureAccountId()
            } catch (e: Exception) {
                null
            }
            if (resolvedAccountId != null && bootstrapCompletedForAccount == resolvedAccountId) {
                onProgress(1f)
                return@withLock
            }

            // نکته‌ی حیاتیِ امنیتی: بررسی تطابق حساب و پاکسازی ایزوله در صورت تعویض حساب کاربری
            val previousAccountId = prefs.getString("auth_account_id", "") ?: ""

            // اگر کاربری با حسابی متفاوت از حساب قبلی لاگین کرده باشد، پایگاه داده محلی را به صورت کامل پاکسازی می‌کنیم
            if (resolvedAccountId != null && previousAccountId.isNotBlank() && previousAccountId != resolvedAccountId) {
                try {
                    repository.clearAllData()
                    File(appContext.filesDir, "images").deleteRecursively()
                    File(appContext.cacheDir, "camera").deleteRecursively()
                    bootstrapCompletedForAccount = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (resolvedAccountId != null) {
                prefs.edit().putString("auth_account_id", resolvedAccountId).apply()
            }

            // کش isGuestMode را هم بر اساس مقدار واقعی جریان همگام‌سازی نگه داریم:
            // در یک لاگین واقعی OAuth/email هرگز حالت مهمان نیستیم.
            isGuestMode.value = false
            syncEngine.isGuestMode = false

            syncEngine.pullAll(onProgress)

            // رفعِ باگِ «عکسِ ثبت‌شده قابل فراخوانی نیست»: رکوردهایی که فایلشان به‌خاطر خطای قبلیِ
            // سرور (نبودِ ایندکس/documentSecurity) هرگز آپلود نشد و از صفِ سینک هم افتاد بیرون
            // (بعد از ۵ بار شکست)، اینجا دوباره به صف اضافه می‌شوند و بلافاصله یک‌بار دیگر تلاش
            // برای آپلود انجام می‌شود.
            try {
                val requeued = repository.requeueStuckLocalFiles()
                if (requeued > 0) {
                    syncEngine.pushPending()
                }
            } catch (e: Exception) {
                // بدون توقفِ ورود کاربر؛ دفعه‌ی بعد دوباره امتحان می‌شود
            }

            if (resolvedAccountId != null) {
                bootstrapCompletedForAccount = resolvedAccountId
            }
            onProgress(1f)
        }
    }

    /**
     * true once a doctor has completed the first-login clinic setup wizard —
     * either on this device, or on another device and then pulled down by
     * [bootstrapAfterLogin]. Call this AFTER bootstrapAfterLogin so a device
     * joining an already-configured clinic doesn't re-run the wizard.
     */
    suspend fun isOnboardingCompleted(): Boolean =
        repository.getSettingsOnce()?.onboardingCompleted == true

    suspend fun ensureOnboardingCompleted() {
        val current = repository.getSettingsOnce() ?: com.example.data.ClinicSettings()
        if (!current.onboardingCompleted) {
            repository.insertSettings(current.copy(onboardingCompleted = true, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Saves the first-login setup wizard's clinic info, configures the
     * doctor's PIN, and marks onboarding complete (synced via clinic_settings
     * so other devices skip the wizard once they pull this record).
     */
    suspend fun completeOnboarding(
        clinicName: String,
        clinicPhone: String,
        clinicAddress: String,
        doctorName: String,
        doctorLicenseNo: String,
        doctorPin: String
    ) {
        val currentUserId = try {
            com.example.util.Supabase.currentUserIdOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
        val userSyncId = if (currentUserId.isNotBlank()) "CLINIC_SETTINGS_$currentUserId" else java.util.UUID.randomUUID().toString()
        val existing = repository.getSettingsOnce() ?: com.example.data.ClinicSettings()
        // منبع واحد نام پزشک: ابتدا رکورد پرسنل ساخته می‌شود، سپس به‌عنوان «پزشک فعال» به
        // تنظیمات معرفی می‌شود تا تمام بخش‌ها از همان یک منبع (staff_members) نام بگیرند.
        val doctorStaff = com.example.data.StaffMember(fullName = doctorName, role = "DOCTOR", phone = clinicPhone)
        val doctorSyncId = doctorStaff.syncId
        repository.insertStaffMember(doctorStaff)
        repository.insertSettings(
            existing.copy(
                clinicName = clinicName,
                clinicPhone = clinicPhone,
                clinicAddress = clinicAddress,
                doctorName = doctorName,
                doctorLicenseNo = doctorLicenseNo,
                activeDoctorSyncId = doctorSyncId,
                onboardingCompleted = true,
                syncId = userSyncId,
                updatedAt = System.currentTimeMillis()
            )
        )
        configureDoctorPin(doctorPin)
        currentUserRole.value = com.example.feature.auth.UserRole.DOCTOR
        prefs.edit().putString("current_user_role", com.example.feature.auth.UserRole.DOCTOR.code).apply()
        isAppUnlocked.value = true
        try {
            syncEngine.pushPending()
        } catch (e: Exception) {
            // Background sync push retry handled
        }
    }

    /** Adds one assistant/secretary during the onboarding wizard's optional staff step. */
    suspend fun addStaffDuringOnboarding(fullName: String, phone: String, pin: String?) {
        val assistant = com.example.data.StaffMember(fullName = fullName, role = "ASSISTANT", phone = phone)
        repository.insertStaffMember(assistant)
        // اگر هنوز دستیار فعالی تعیین نشده، اولین دستیار ثبت‌شده «دستیار فعال» می‌شود
        val current = repository.getSettingsOnce()
        if (current != null && current.activeAssistantSyncId.isBlank()) {
            repository.insertSettings(current.copy(activeAssistantSyncId = assistant.syncId, staffNames = fullName))
        }
        if (!pin.isNullOrBlank()) configureRolePin(com.example.feature.auth.UserRole.ASSISTANT, pin)
    }

    /**
     * کدِ بازگشتی از OAuth گوگل (dental://app?code=...) — MainActivity آن را اینجا
     * می‌گذارد و AuthScreen با تعویض آن (PKCE) نشست را کامل می‌کند.
     */
    val pendingOAuthCode = MutableStateFlow<String?>(null)

    fun handleOAuthCallback(code: String?) {
        if (!code.isNullOrBlank()) pendingOAuthCode.value = code
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            // Best-effort: try to let any still-pending uploads/edits finish syncing before we
            // wipe local state, so nothing not-yet-uploaded is lost. Logout must still proceed
            // even if this fails or times out (e.g. offline) — it is not a precondition.
            // NOTE: syncEngine.pushPending() now drains the ENTIRE pending queue in a loop
            // (previously it only processed a single 50-item batch and returned, silently
            // dropping any remaining queued changes once clearAllData() ran below — a real
            // data-loss bug for anyone with >50 pending ops at logout time, e.g. after
            // bulk-entering several patients).
            try {
                syncEngine.pushPending()
            } catch (e: Exception) {
                Log.w("DentalViewModel", "pushPending() failed during logout, proceeding anyway: ${e.message}")
            }
            // اگر با وجود تلاش بالا هنوز عملیات همگام‌نشده در صف مانده باشد (مثلاً به‌خاطر
            // قطعی شبکه در همان لحظه)، این یک هشدار مهم است: چون خط بعدی (clearAllData)
            // برگشت‌ناپذیر است و همین داده‌ی محلی را برای همیشه پاک می‌کند. حداقل لاگ واضح
            // ثبت می‌شود تا این حالت در گزارش/دیباگ قابل ردیابی باشد.
            try {
                val stillPending = repository.getPendingOps(1)
                if (stillPending.isNotEmpty()) {
                    Log.w("DentalViewModel", "logout(): unsynced changes still pending after pushPending() drain attempt — this local data will be lost by clearAllData().")
                }
            } catch (e: Exception) {
                Log.w("DentalViewModel", "logout(): failed to check remaining pending ops: ${e.message}")
            }
            try {
                com.example.util.Supabase.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            com.example.util.Supabase.resetAccountIdCache()
            bootstrapCompletedForAccount = null
            repository.clearAllData()
            // رفع باگ: isGuestMode و flag داخل syncEngine را هم ریست می‌کنیم تا لاگین بعدی
            // با اکانت واقعی، pullAll/pushPending به‌اشتباه به‌خاطر حالت مهمان خاموش نمانند.
            isGuestMode.value = false
            syncEngine.isGuestMode = false
            prefs.edit().clear().apply()
            SecureSecretStore.clearAll(appContext)
            try {
                File(appContext.filesDir, "images").deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                File(appContext.cacheDir, "camera").deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentUserRole.value = com.example.feature.auth.UserRole.ASSISTANT
            isAppUnlocked.value = false
        }
    }

    private val _deleteAccountState = MutableStateFlow<String?>(null)
    val deleteAccountState: StateFlow<String?> = _deleteAccountState.asStateFlow()

    /**
     * حذف کامل و برگشت‌ناپذیر حساب: همه‌ی رکوردهای این حساب (بیماران، پرونده‌ها، عکس‌ها، امضاها،
     * تنظیمات، پرسنل و ...) از سرور پاک می‌شود، سپس همان پاک‌سازی محلیِ logout() اجرا می‌شود.
     * UI باید پیش از صدا زدن این تابع، تاییدِ صریح کاربر را گرفته باشد.
     */
    fun deleteAccount(onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _deleteAccountState.value = "در حال حذف اطلاعات از سرور..."
            val success = try {
                syncEngine.deleteAccountAndAllServerData { collection ->
                    _deleteAccountState.value = "در حال حذف $collection ..."
                }
            } catch (e: Exception) {
                false
            }
            if (success) {
                repository.clearAllData()
                prefs.edit().clear().apply()
                SecureSecretStore.clearAll(appContext)
                try {
                    File(appContext.filesDir, "images").deleteRecursively()
                } catch (e: Exception) {
                }
                try {
                    File(appContext.cacheDir, "camera").deleteRecursively()
                } catch (e: Exception) {
                }
                com.example.util.Supabase.resetAccountIdCache()
                bootstrapCompletedForAccount = null
                currentUserRole.value = com.example.feature.auth.UserRole.ASSISTANT
                isAppUnlocked.value = false
            }
            _deleteAccountState.value = null
            onDone(success)
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            syncEngine.pushPending()
        }
    }

    private val _requeueResultMessage = MutableStateFlow<String?>(null)
    val requeueResultMessage: StateFlow<String?> = _requeueResultMessage.asStateFlow()

    /**
     * دکمه‌ی دستیِ «تلاش مجدد آپلود فایل‌های ناقص» — برای زمانی که بهزاد بعد از اجرای اسکریپت
     * اصلاح سرور (setup سرور) می‌خواهد مطمئن شود عکس‌ها/امضاها/چک‌های قدیمی که قبلاً به‌خاطر
     * خطای سرور آپلود نشده بودند، الان با موفقیت آپلود می‌شوند — بدون نیاز به خروج و ورود مجدد.
     */
    fun retryStuckUploads() {
        viewModelScope.launch(Dispatchers.IO) {
            _requeueResultMessage.value = null
            val count = try {
                repository.requeueStuckLocalFiles()
            } catch (e: Exception) {
                -1
            }
            if (count > 0) {
                syncEngine.pushPending()
            }
            _requeueResultMessage.value = when {
                count < 0 -> "خطا در بررسی فایل‌های ناقص"
                count == 0 -> "هیچ فایل ناقصی پیدا نشد — همه‌چیز از قبل آپلود شده"
                else -> "$count فایل دوباره برای آپلود صف‌بندی شد"
            }
        }
    }

    suspend fun pingServer(): String = withContext(Dispatchers.IO) {
        try {
            if (syncEngine.isOnline.value) "اتصال به اینترنت برقرار است (Online)"
            else "عدم دسترسی به اینترنت (Offline)"
        } catch (e: Exception) {
            "خطا در بررسی اتصال: ${e.localizedMessage ?: "نامشخص"}"
        }
    }
}