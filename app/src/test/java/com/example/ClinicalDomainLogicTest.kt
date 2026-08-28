package com.example

import com.example.data.Appointment
import com.example.security.SecurityUtils
import com.example.ui.screens.parseTeethCount
import com.example.ui.screens.toEnglishLong
import com.example.util.AppointmentConflictDetector
import com.example.util.PersianCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalDomainLogicTest {

    @Test
    fun testPersianToEnglishLongConversion() {
        val persianNum = "۱۲۳۴۵۶۷۸۹۰"
        val converted = persianNum.toEnglishLong()
        assertEquals(1234567890L, converted)

        val formattedAmount = "۲,۵۰۰,۰۰۰ تومان"
        val cleanAmount = formattedAmount.toEnglishLong()
        assertEquals(2500000L, cleanAmount)
    }

    @Test
    fun testParseTeethCount() {
        assertEquals(1, parseTeethCount("14"))
        assertEquals(2, parseTeethCount("14, 15"))
        assertEquals(4, parseTeethCount("11-14"))
        assertEquals(3, parseTeethCount("21, 22, 23"))
    }

    @Test
    fun testPersianCalendarHelperConversions() {
        // Test 1 Farvardin 1405 conversion (21 March 2026)
        val jDate = PersianCalendarHelper.gregorianToJalali(2026, 3, 21)
        assertEquals(1405, jDate.year)
        assertEquals(1, jDate.month)
        assertEquals(1, jDate.day)

        val (gy, gm, gd) = PersianCalendarHelper.jalaliToGregorian(1405, 1, 1)
        assertEquals(2026, gy)
        assertEquals(3, gm)
        assertEquals(21, gd)

        // Leap year checks
        assertTrue(PersianCalendarHelper.isJalaliLeapYear(1403))
        assertFalse(PersianCalendarHelper.isJalaliLeapYear(1405))
    }

    @Test
    fun testAppointmentConflictDetection() {
        val baseTime = 1770000000000L
        val existingAppointments = listOf(
            Appointment(
                id = 1,
                patientId = 10,
                patientName = "علی رضایی",
                date = baseTime, // start: baseTime, end: baseTime + 30m
                treatmentType = "عصب‌کشی"
            ),
            Appointment(
                id = 2,
                patientId = 11,
                patientName = "مریم حسینی",
                date = baseTime + (60 * 60 * 1000L), // 1 hour later
                treatmentType = "ترمیم"
            )
        )

        // Test 1: Exact overlap
        val overlapResult = AppointmentConflictDetector.checkConflict(
            targetDate = baseTime + (10 * 60 * 1000L), // 10 mins into Ali's slot
            durationMinutes = 30,
            existingAppointments = existingAppointments
        )
        assertTrue(overlapResult.hasConflict)
        assertEquals("علی رضایی", overlapResult.conflictingAppointment?.patientName)

        // Test 2: Free slot (35 mins after first appointment)
        val freeResult = AppointmentConflictDetector.checkConflict(
            targetDate = baseTime + (35 * 60 * 1000L),
            durationMinutes = 20,
            existingAppointments = existingAppointments
        )
        assertFalse(freeResult.hasConflict)

        // Test 3: Same appointment edit (excluding own id)
        val selfEditResult = AppointmentConflictDetector.checkConflict(
            targetDate = baseTime,
            durationMinutes = 30,
            excludeAppointmentId = 1L,
            existingAppointments = existingAppointments
        )
        assertFalse(selfEditResult.hasConflict)
    }

    @Test
    fun testSecurityPinHashingAndVerification() {
        val pin = "7890"
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPin(pin, salt)

        assertTrue(SecurityUtils.verifyPin("7890", hash, salt))
        assertFalse(SecurityUtils.verifyPin("1234", hash, salt))
        assertFalse(SecurityUtils.verifyPin("0000", hash, salt))

        val signaturePayload = "Consent: Ali Rezaei, Doctor: Dr. Dentist, Timestamp: 1770000000"
        val sha256Hash = SecurityUtils.sha256(signaturePayload)
        assertEquals(64, sha256Hash.length) // Standard SHA-256 hex length
    }
}
