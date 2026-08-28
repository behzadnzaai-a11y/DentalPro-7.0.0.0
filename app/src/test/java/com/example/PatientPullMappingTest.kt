package com.example

import com.example.data.AppCalendarEvent
import com.example.data.Appointment
import com.example.data.AuditEvent
import com.example.data.BankAccount
import com.example.data.ChildrenExpense
import com.example.data.ClinicExpense
import com.example.data.ClinicSettings
import com.example.data.DrugTemplate
import com.example.data.EquipmentMaintenance
import com.example.data.ExpensePayment
import com.example.data.FinancialLedgerEntry
import com.example.data.FixedIncome
import com.example.data.Installment
import com.example.data.LabOrder
import com.example.data.LabService
import com.example.data.Patient
import com.example.data.PatientDocument
import com.example.data.Payment
import com.example.data.PersonalSubAccount
import com.example.data.PersonalTransaction
import com.example.data.Prescription
import com.example.data.StaffMember
import com.example.data.Treatment
import com.example.data.TreatmentStage
import com.example.data.TreatmentTemplate
import com.example.data.Vendor
import com.example.data.VendorInvoice
import com.example.util.EntityMapper
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * رگرسیون باگ «لیست خالی بعد از pull موفق» برای هر ۲۷ جدول سینک.
 * Gson پیش‌فرض Kotlin را اعمال نمی‌کند؛ فیلد String غیرنال غایب (documentUris، cvv2، …)
 * در runtime null می‌ماند و insert Room با NOT NULL بی‌صدا skip می‌شود.
 */
class PatientPullMappingTest {

    private val gson = Gson()

    private val allSyncEntities: List<Class<*>> = listOf(
        ClinicSettings::class.java,
        StaffMember::class.java,
        BankAccount::class.java,
        PersonalSubAccount::class.java,
        FixedIncome::class.java,
        TreatmentTemplate::class.java,
        DrugTemplate::class.java,
        LabService::class.java,
        AppCalendarEvent::class.java,
        Patient::class.java,
        Vendor::class.java,
        LabOrder::class.java,
        EquipmentMaintenance::class.java,
        Treatment::class.java,
        ClinicExpense::class.java,
        VendorInvoice::class.java,
        Appointment::class.java,
        TreatmentStage::class.java,
        Prescription::class.java,
        PatientDocument::class.java,
        Installment::class.java,
        Payment::class.java,
        ExpensePayment::class.java,
        PersonalTransaction::class.java,
        ChildrenExpense::class.java,
        FinancialLedgerEntry::class.java,
        AuditEvent::class.java
    )

    @Test
    fun allTwentySevenSyncEntitiesAreCovered() {
        assertEquals(27, allSyncEntities.size)
        assertEquals(27, allSyncEntities.toSet().size)
    }

    @Test
    fun preparePulledJson_fillsMissingStringAndStripsId_forEveryEntity() {
        for (clazz in allSyncEntities) {
            val json = """{"syncId":"sid-${clazz.simpleName}","ownerId":"uid-1","updatedAt":1}"""
            val prepared = EntityMapper.preparePulledJson(json, clazz)
            val obj = JsonParser.parseString(prepared).asJsonObject
            assertFalse("${clazz.simpleName} must not keep id", obj.has("id"))
            for (field in clazz.declaredFields) {
                if (field.isSynthetic || field.name.contains('$')) continue
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                if (field.type == String::class.java) {
                    assertFalse(
                        "${clazz.simpleName}.${field.name} still missing after prepare",
                        obj.get(field.name) == null || obj.get(field.name).isJsonNull
                    )
                    if (field.name !in setOf("syncId", "ownerId")) {
                        assertEquals(
                            "${clazz.simpleName}.${field.name} should be empty string",
                            "",
                            obj.get(field.name).asString
                        )
                    }
                }
            }
            val parsed = gson.fromJson(prepared, clazz)
            assertNotNull(clazz.simpleName, parsed)
            val idField = clazz.getDeclaredField("id")
            idField.isAccessible = true
            assertEquals("${clazz.simpleName}.id", 0L, idField.get(parsed) as Long)
        }
    }

    @Test
    fun preparePulledJson_coercesExplicitJsonNullStrings() {
        val json = """{"syncId":"abc","name":"علی","documentUris":null,"cvv2":null}"""
        val patient = gson.fromJson(
            EntityMapper.preparePulledJson(json, Patient::class.java),
            Patient::class.java
        )
        assertEquals("", patient.documentUris)
        val bank = gson.fromJson(
            EntityMapper.preparePulledJson(json, BankAccount::class.java),
            BankAccount::class.java
        )
        assertEquals("", bank.cvv2)
    }

    @Test
    fun preparePulledJson_fillsMissingDocumentUris_andStripsId() {
        val json = """{"syncId":"abc-123","name":"علی رضایی","phone":"09121234567","ownerId":"uid-1","updatedAt":1}"""
        val prepared = EntityMapper.preparePulledJson(json, Patient::class.java)
        val obj = JsonParser.parseString(prepared).asJsonObject
        assertFalse(obj.has("id"))
        assertEquals("", obj.get("documentUris").asString)

        val patient = gson.fromJson(prepared, Patient::class.java)
        assertEquals("علی رضایی", patient.name)
        assertEquals("09121234567", patient.phone)
        assertEquals("", patient.documentUris)
        assertEquals(0L, patient.id)
        assertEquals("abc-123", patient.syncId)
    }

    @Test
    fun preparePulledJson_stripsUuidIdSoGsonDoesNotCrash() {
        val json = """{"id":"550e8400-e29b-41d4-a716-446655440000","syncId":"abc","name":"Test Patient"}"""
        val prepared = EntityMapper.preparePulledJson(json, Patient::class.java)
        val patient = gson.fromJson(prepared, Patient::class.java)
        assertEquals("Test Patient", patient.name)
        assertEquals(0L, patient.id)
        assertEquals("abc", patient.syncId)
    }

    @Test
    fun rawGsonWithoutPrepare_leavesDocumentUrisNull() {
        val json = """{"syncId":"abc","name":"علی رضایی"}"""
        val patient = gson.fromJson(json, Patient::class.java)
        val field = Patient::class.java.getDeclaredField("documentUris")
        field.isAccessible = true
        assertEquals(null, field.get(patient))
    }

    @Test
    fun preparePulledJson_fillsMissingCvv2OnBankAccount() {
        val json = """{"syncId":"bank-1","bankName":"ملی","cardHolder":"دکتر","ownerId":"uid-1"}"""
        val prepared = EntityMapper.preparePulledJson(json, BankAccount::class.java)
        val account = gson.fromJson(prepared, BankAccount::class.java)
        assertEquals("ملی", account.bankName)
        assertEquals("", account.cvv2)
        assertEquals(0L, account.id)
    }

    @Test
    fun overlayLocalOnlyFields_preservesCvv2AndDocumentUrisOnRepull() {
        val pulledPatient = gson.fromJson(
            EntityMapper.preparePulledJson("""{"syncId":"p1","name":"علی"}""", Patient::class.java),
            Patient::class.java
        )
        val localPatient = Patient(id = 42, name = "علی", documentUris = "/data/local/xray.jpg", syncId = "p1")
        EntityMapper.overlayLocalOnlyFields(pulledPatient, localPatient)
        assertEquals("/data/local/xray.jpg", pulledPatient.documentUris)
        assertEquals(42L, pulledPatient.id)

        val pulledBank = gson.fromJson(
            EntityMapper.preparePulledJson("""{"syncId":"b1","bankName":"ملی"}""", BankAccount::class.java),
            BankAccount::class.java
        )
        val localBank = BankAccount(id = 7, bankName = "ملی", cvv2 = "123", syncId = "b1")
        EntityMapper.overlayLocalOnlyFields(pulledBank, localBank)
        assertEquals("123", pulledBank.cvv2)
        assertEquals(7L, pulledBank.id)
    }

    @Test
    fun overlayLocalOnlyFields_preservesPatientDocumentCachePaths() {
        val pulled = gson.fromJson(
            EntityMapper.preparePulledJson("""{"syncId":"d1","title":"رادیو"}""", PatientDocument::class.java),
            PatientDocument::class.java
        )
        val local = PatientDocument(
            id = 9,
            patientId = 1,
            uri = "/data/local/doc.jpg",
            localCachePath = "/data/local/cache.jpg",
            syncId = "d1"
        )
        EntityMapper.overlayLocalOnlyFields(pulled, local)
        assertEquals("/data/local/doc.jpg", pulled.uri)
        assertEquals("/data/local/cache.jpg", pulled.localCachePath)
        assertEquals(9L, pulled.id)
        assertNotEquals("should not copy synced title from local if we only overlay local-only", "رادیو", local.title)
    }

    @Test
    fun preparePulledJson_fillsRequiredStringsOnAppointmentAndTreatment() {
        val appt = gson.fromJson(
            EntityMapper.preparePulledJson("""{"syncId":"a1"}""", Appointment::class.java),
            Appointment::class.java
        )
        assertEquals("", appt.patientName)
        assertEquals("", appt.treatmentType)

        val tx = gson.fromJson(
            EntityMapper.preparePulledJson("""{"syncId":"t1"}""", Treatment::class.java),
            Treatment::class.java
        )
        assertEquals("", tx.title)
        assertEquals(0L, tx.patientId)
        assertEquals(0L, tx.cost)
    }
}
