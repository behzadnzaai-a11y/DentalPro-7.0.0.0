package com.example.util

import com.example.data.Appointment

data class ConflictResult(
    val hasConflict: Boolean,
    val conflictingAppointment: Appointment? = null,
    val messageFa: String = "",
    val messageEn: String = ""
)

object AppointmentConflictDetector {

    /**
     * Checks if a scheduled appointment overlaps with existing active appointments.
     * @param targetDate Timestamp in milliseconds of the proposed appointment start.
     * @param durationMinutes Estimated duration of the appointment in minutes (default 30 min).
     * @param excludeAppointmentId Id of current appointment if editing (to prevent self-conflict).
     * @param existingAppointments List of all current appointments.
     */
    fun checkConflict(
        targetDate: Long,
        durationMinutes: Int = 30,
        excludeAppointmentId: Long? = null,
        existingAppointments: List<Appointment>
    ): ConflictResult {
        val newStart = targetDate
        val newEnd = newStart + (durationMinutes.coerceAtLeast(10) * 60 * 1000L)

        for (existing in existingAppointments) {
            if (existing.id == excludeAppointmentId) continue
            if (existing.status.equals("CANCELED", ignoreCase = true)) continue

            val existingStart = existing.date
            val existingDuration = 30 // standard slot duration
            val existingEnd = existingStart + (existingDuration * 60 * 1000L)

            // Overlap condition: (StartA < EndB) and (EndA > StartB)
            if (newStart < existingEnd && newEnd > existingStart) {
                return ConflictResult(
                    hasConflict = true,
                    conflictingAppointment = existing,
                    messageFa = "هشدار تداخل زمان: هم‌پوشانی با نوبت «${existing.patientName}» در ساعت تعیین‌شده وجود دارد.",
                    messageEn = "Time Conflict: Overlaps with appointment for '${existing.patientName}'."
                )
            }
        }

        return ConflictResult(hasConflict = false)
    }
}
