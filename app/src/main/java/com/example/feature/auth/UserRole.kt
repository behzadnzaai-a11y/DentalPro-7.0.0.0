package com.example.feature.auth

data class RolePermissions(
    val canAccessPatients: Boolean = true,
    val canAccessAppointments: Boolean = true,
    val canAccessTreatments: Boolean = true,
    val canAccessFinancial: Boolean = true,
    val canAccessExpenses: Boolean = true,
    val canAccessLabs: Boolean = true,
    val canAccessMaintenance: Boolean = true,
    val canAccessReports: Boolean = true,
    val canAccessBankAccounts: Boolean = true,
    val canAccessCheques: Boolean = true,
    val canAccessSettings: Boolean = true,
    val canDeleteRecords: Boolean = true
) {
    fun toBitMask(): Int = listOf(
        canAccessPatients, canAccessAppointments, canAccessTreatments,
        canAccessFinancial, canAccessExpenses, canAccessLabs, canAccessMaintenance,
        canAccessReports, canAccessBankAccounts, canAccessCheques,
        canAccessSettings, canDeleteRecords
    ).foldIndexed(0) { index, mask, allowed -> if (allowed) mask or (1 shl index) else mask }

    companion object {
        fun fromBitMask(mask: Int): RolePermissions = RolePermissions(
            canAccessPatients = mask and (1 shl 0) != 0,
            canAccessAppointments = mask and (1 shl 1) != 0,
            canAccessTreatments = mask and (1 shl 2) != 0,
            canAccessFinancial = mask and (1 shl 3) != 0,
            canAccessExpenses = mask and (1 shl 4) != 0,
            canAccessLabs = mask and (1 shl 5) != 0,
            canAccessMaintenance = mask and (1 shl 6) != 0,
            canAccessReports = mask and (1 shl 7) != 0,
            canAccessBankAccounts = mask and (1 shl 8) != 0,
            canAccessCheques = mask and (1 shl 9) != 0,
            canAccessSettings = mask and (1 shl 10) != 0,
            canDeleteRecords = mask and (1 shl 11) != 0
        )
    }
}

enum class UserRole(val code: String, val titleFa: String, val titleEn: String) {
    DOCTOR("DOCTOR", "پزشک (مدیر درمان)", "Doctor (Clinical Lead)"),
    ASSISTANT("ASSISTANT", "منشی / دستیار", "Secretary / Assistant");

    val canAccessClinical: Boolean
        get() = this == DOCTOR || this == ASSISTANT

    val canEditTreatmentsAndRx: Boolean
        get() = this == DOCTOR

    val canAccessFinancial: Boolean
        get() = this == DOCTOR

    val canAccessAppointments: Boolean
        get() = this == DOCTOR || this == ASSISTANT

    val canAccessSettings: Boolean
        get() = this == DOCTOR

    val canDeleteRecords: Boolean
        get() = this == DOCTOR

    companion object {
        fun fromCode(code: String): UserRole {
            // Unknown or corrupted role values must fail closed, never grant doctor access.
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ASSISTANT
        }

        fun defaultPermissionsFor(role: UserRole): RolePermissions {
            return when (role) {
                DOCTOR -> RolePermissions(
                    canAccessPatients = true,
                    canAccessAppointments = true,
                    canAccessTreatments = true,
                    canAccessFinancial = true,
                    canAccessExpenses = true,
                    canAccessLabs = true,
                    canAccessMaintenance = true,
                    canAccessReports = true,
                    canAccessBankAccounts = true,
                    canAccessCheques = true,
                    canAccessSettings = true,
                    canDeleteRecords = true
                )
                ASSISTANT -> RolePermissions(
                    canAccessPatients = true,
                    canAccessAppointments = true,
                    canAccessTreatments = false,
                    canAccessFinancial = false,
                    canAccessExpenses = false,
                    canAccessLabs = false,
                    canAccessMaintenance = false,
                    canAccessReports = false,
                    canAccessBankAccounts = false,
                    canAccessCheques = false,
                    canAccessSettings = false,
                    canDeleteRecords = false
                )
            }
        }
    }
}
