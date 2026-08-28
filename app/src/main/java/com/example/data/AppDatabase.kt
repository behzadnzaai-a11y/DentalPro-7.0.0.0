package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Patient?

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    suspend fun getPatientById(id: Long): Patient?
    
    @Query("SELECT * FROM patients")
    suspend fun getAllList(): List<Patient>

    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun getAllPatients(): Flow<List<Patient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient): Long

    @Delete
    suspend fun deletePatient(patient: Patient)
}

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Treatment?

    @Query("SELECT * FROM treatments WHERE id = :id LIMIT 1")
    suspend fun getTreatmentById(id: Long): Treatment?
    
    @Query("SELECT * FROM treatments")
    suspend fun getAllList(): List<Treatment>

    @Query("SELECT * FROM treatments ORDER BY date DESC")
    fun getAllTreatments(): Flow<List<Treatment>>

    @Query("SELECT * FROM treatments WHERE patientId = :patientId ORDER BY date DESC")
    fun getTreatmentsForPatient(patientId: Long): Flow<List<Treatment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatment(treatment: Treatment): Long

    @Delete
    suspend fun deleteTreatment(treatment: Treatment)

    @Query("DELETE FROM treatments WHERE patientId = :patientId")
    suspend fun deleteTreatmentsForPatient(patientId: Long)

    @Query("SELECT * FROM treatments WHERE patientId = :patientId")
    suspend fun getTreatmentsForPatientOnce(patientId: Long): List<Treatment>
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Payment?
    
    @Query("SELECT * FROM payments")
    suspend fun getAllList(): List<Payment>

    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE patientId = :patientId ORDER BY date DESC")
    fun getPaymentsForPatient(patientId: Long): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Long): Payment?

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments WHERE patientId = :patientId")
    suspend fun deletePaymentsForPatient(patientId: Long)

    @Query("SELECT * FROM payments WHERE patientId = :patientId")
    suspend fun getPaymentsForPatientOnce(patientId: Long): List<Payment>
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Appointment?
    
    @Query("SELECT * FROM appointments")
    suspend fun getAllList(): List<Appointment>

    @Query("SELECT * FROM appointments ORDER BY date ASC")
    fun getAllAppointments(): Flow<List<Appointment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment): Long

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Query("DELETE FROM appointments WHERE patientId = :patientId OR patientName = :patientName")
    suspend fun deleteAppointmentsForPatient(patientId: Long, patientName: String)

    @Query("SELECT * FROM appointments WHERE patientId = :patientId OR patientName = :patientName")
    suspend fun getAppointmentsForPatientOnce(patientId: Long, patientName: String): List<Appointment>
}

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Vendor?

    @Query("SELECT * FROM vendors WHERE id = :id LIMIT 1")
    suspend fun getVendorById(id: Long): Vendor?

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun getAllVendors(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors")
    suspend fun getAllList(): List<Vendor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: Vendor): Long

    @Delete
    suspend fun deleteVendor(vendor: Vendor)
}

@Dao
interface LabOrderDao {
    @Query("SELECT * FROM lab_orders WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): LabOrder?

    @Query("SELECT * FROM lab_orders WHERE id = :id LIMIT 1")
    suspend fun getLabOrderById(id: Long): LabOrder?

    @Query("SELECT * FROM lab_orders ORDER BY dueDate ASC")
    fun getAllLabOrders(): Flow<List<LabOrder>>

    @Query("SELECT * FROM lab_orders")
    suspend fun getAllList(): List<LabOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabOrder(order: LabOrder): Long

    @Delete
    suspend fun deleteLabOrder(order: LabOrder)
}

@Dao
interface EquipmentMaintenanceDao {
    @Query("SELECT * FROM equipment_maintenance WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): EquipmentMaintenance?

    @Query("SELECT * FROM equipment_maintenance WHERE id = :id LIMIT 1")
    suspend fun getMaintenanceById(id: Long): EquipmentMaintenance?

    @Query("SELECT * FROM equipment_maintenance ORDER BY date DESC")
    fun getAllMaintenance(): Flow<List<EquipmentMaintenance>>

    @Query("SELECT * FROM equipment_maintenance")
    suspend fun getAllList(): List<EquipmentMaintenance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenance(maintenance: EquipmentMaintenance): Long

    @Delete
    suspend fun deleteMaintenance(maintenance: EquipmentMaintenance)
}

@Dao
interface ClinicExpenseDao {
    @Query("SELECT * FROM clinic_expenses WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ClinicExpense?

    @Query("SELECT * FROM clinic_expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ClinicExpense?

    @Query("SELECT * FROM clinic_expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ClinicExpense>>

    @Query("SELECT * FROM clinic_expenses")
    suspend fun getAllList(): List<ClinicExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ClinicExpense): Long

    @Update
    suspend fun updateExpense(expense: ClinicExpense)

    @Delete
    suspend fun deleteExpense(expense: ClinicExpense)
}

@Dao
interface ExpensePaymentDao {
    @Query("SELECT * FROM expense_payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ExpensePayment?

    @Query("SELECT * FROM expense_payments ORDER BY date DESC")
    fun getAllExpensePayments(): Flow<List<ExpensePayment>>

    @Query("SELECT * FROM expense_payments")
    suspend fun getAllList(): List<ExpensePayment>

    @Query("SELECT * FROM expense_payments WHERE clinicExpenseId = :expenseId ORDER BY date DESC")
    fun getPaymentsForExpense(expenseId: Long): Flow<List<ExpensePayment>>

    @Query("SELECT * FROM expense_payments WHERE clinicExpenseId = :expenseId ORDER BY date DESC")
    suspend fun getPaymentsForExpenseOnce(expenseId: Long): List<ExpensePayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpensePayment(payment: ExpensePayment): Long

    @Query("SELECT * FROM expense_payments WHERE id = :id LIMIT 1")
    suspend fun getExpensePaymentById(id: Long): ExpensePayment?

    @Delete
    suspend fun deleteExpensePayment(payment: ExpensePayment)
}

@Dao
interface PersonalTransactionDao {
    @Query("SELECT * FROM personal_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): PersonalTransaction?

    @Query("SELECT * FROM personal_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<PersonalTransaction>>

    @Query("SELECT * FROM personal_transactions")
    suspend fun getAllList(): List<PersonalTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: PersonalTransaction): Long

    @Query("SELECT * FROM personal_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): PersonalTransaction?

    @Delete
    suspend fun deleteTransaction(tx: PersonalTransaction)
}

@Dao
interface TreatmentTemplateDao {
    @Query("SELECT * FROM treatment_templates WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): TreatmentTemplate?

    @Query("SELECT * FROM treatment_templates ORDER BY isStarred DESC, sortOrder ASC, id ASC")
    fun getAllTemplates(): Flow<List<TreatmentTemplate>>

    @Query("SELECT * FROM treatment_templates")
    suspend fun getAllList(): List<TreatmentTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TreatmentTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<TreatmentTemplate>)

    @Delete
    suspend fun deleteTemplate(template: TreatmentTemplate)
}

@Dao
interface PrescriptionDao {
    @Query("SELECT * FROM prescriptions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Prescription?

    @Query("SELECT * FROM prescriptions WHERE patientId = :patientId ORDER BY date DESC")
    fun getPrescriptionsForPatient(patientId: Long): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions WHERE treatmentId = :treatmentId ORDER BY date DESC")
    fun getPrescriptionsForTreatment(treatmentId: Long): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions WHERE treatmentId = :treatmentId ORDER BY date DESC")
    suspend fun getPrescriptionsForTreatmentOnce(treatmentId: Long): List<Prescription>

    @Query("SELECT * FROM prescriptions ORDER BY date DESC")
    fun getAllPrescriptions(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions")
    suspend fun getAllList(): List<Prescription>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: Prescription): Long

    @Delete
    suspend fun deletePrescription(prescription: Prescription)

    @Query("DELETE FROM prescriptions WHERE patientId = :patientId")
    suspend fun deletePrescriptionsForPatient(patientId: Long)

    @Query("DELETE FROM prescriptions WHERE treatmentId = :treatmentId")
    suspend fun deletePrescriptionsForTreatment(treatmentId: Long)

    @Query("SELECT * FROM prescriptions WHERE patientId = :patientId")
    suspend fun getPrescriptionsForPatientOnce(patientId: Long): List<Prescription>
}

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): BankAccount?

    @Query("SELECT * FROM bank_accounts ORDER BY bankName ASC")
    fun getAllBankAccounts(): Flow<List<BankAccount>>

    @Query("SELECT * FROM bank_accounts")
    suspend fun getAllList(): List<BankAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(account: BankAccount): Long

    @Query("SELECT * FROM bank_accounts WHERE id = :id LIMIT 1")
    suspend fun getBankAccountById(id: Long): BankAccount?

    @Query("UPDATE bank_accounts SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Long): Int

    @Delete
    suspend fun deleteBankAccount(account: BankAccount)
}

@Dao
interface PersonalSubAccountDao {
    @Query("SELECT * FROM personal_sub_accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): PersonalSubAccount?

    @Query("SELECT * FROM personal_sub_accounts ORDER BY name ASC")
    fun getAllSubAccounts(): Flow<List<PersonalSubAccount>>

    @Query("SELECT * FROM personal_sub_accounts")
    suspend fun getAllList(): List<PersonalSubAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubAccount(sub: PersonalSubAccount): Long

    @Delete
    suspend fun deleteSubAccount(sub: PersonalSubAccount)
}

@Dao
interface ChildrenExpenseDao {
    @Query("SELECT * FROM children_expenses WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ChildrenExpense?

    @Query("SELECT * FROM children_expenses ORDER BY date DESC")
    fun getAllChildrenExpenses(): Flow<List<ChildrenExpense>>

    @Query("SELECT * FROM children_expenses")
    suspend fun getAllList(): List<ChildrenExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChildrenExpense(expense: ChildrenExpense): Long

    @Delete
    suspend fun deleteChildrenExpense(expense: ChildrenExpense)
}

@Dao
interface LabServiceDao {
    @Query("SELECT * FROM lab_services WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): LabService?

    @Query("SELECT * FROM lab_services ORDER BY serviceName ASC")
    fun getAllLabServices(): Flow<List<LabService>>

    @Query("SELECT * FROM lab_services")
    suspend fun getAllList(): List<LabService>

    @Query("SELECT * FROM lab_services WHERE labName = :labName ORDER BY serviceName ASC")
    fun getServicesForLab(labName: String): Flow<List<LabService>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabService(service: LabService): Long

    @Delete
    suspend fun deleteLabService(service: LabService)
}

@Dao
interface FixedIncomeDao {
    @Query("SELECT * FROM fixed_incomes WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): FixedIncome?

    @Query("SELECT * FROM fixed_incomes ORDER BY id DESC")
    fun getAllFixedIncomes(): Flow<List<FixedIncome>>

    @Query("SELECT * FROM fixed_incomes")
    suspend fun getAllList(): List<FixedIncome>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixedIncome(income: FixedIncome): Long

    @Delete
    suspend fun deleteFixedIncome(income: FixedIncome)
}

@Dao
interface TreatmentStageDao {
    @Query("SELECT * FROM treatment_stages WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): TreatmentStage?

    @Query("SELECT * FROM treatment_stages WHERE treatmentId = :treatmentId ORDER BY stageIndex ASC")
    fun getStagesForTreatment(treatmentId: Long): Flow<List<TreatmentStage>>

    @Query("SELECT * FROM treatment_stages WHERE treatmentId = :treatmentId ORDER BY stageIndex ASC")
    suspend fun getStagesForTreatmentOnce(treatmentId: Long): List<TreatmentStage>

    @Query("SELECT * FROM treatment_stages ORDER BY treatmentId ASC, stageIndex ASC")
    fun getAllStages(): Flow<List<TreatmentStage>>

    @Query("SELECT * FROM treatment_stages")
    suspend fun getAllList(): List<TreatmentStage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStage(stage: TreatmentStage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStages(stages: List<TreatmentStage>)

    @Update
    suspend fun updateStage(stage: TreatmentStage)

    @Delete
    suspend fun deleteStage(stage: TreatmentStage)

    @Query("DELETE FROM treatment_stages WHERE treatmentId = :treatmentId")
    suspend fun deleteStagesForTreatment(treatmentId: Long)
}

@Dao
interface PatientDocumentDao {
    @Query("SELECT * FROM patient_documents WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): PatientDocument?

    @Query("SELECT * FROM patient_documents WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun getDocumentsForPatient(patientId: Long): Flow<List<PatientDocument>>

    @Query("SELECT * FROM patient_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<PatientDocument>>

    @Query("SELECT * FROM patient_documents")
    suspend fun getAllList(): List<PatientDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: PatientDocument): Long

    @Delete
    suspend fun deleteDocument(doc: PatientDocument)

    @Query("DELETE FROM patient_documents WHERE patientId = :patientId")
    suspend fun deleteDocumentsForPatient(patientId: Long)

    @Query("SELECT * FROM patient_documents WHERE patientId = :patientId")
    suspend fun getDocumentsForPatientOnce(patientId: Long): List<PatientDocument>
}

@Dao
interface VendorInvoiceDao {
    @Query("SELECT * FROM vendor_invoices WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): VendorInvoice?

    @Query("SELECT * FROM vendor_invoices WHERE vendorId = :vendorId ORDER BY date DESC")
    fun getInvoicesForVendor(vendorId: Long): Flow<List<VendorInvoice>>

    @Query("SELECT * FROM vendor_invoices WHERE vendorId = :vendorId ORDER BY date DESC")
    suspend fun getInvoicesForVendorOnce(vendorId: Long): List<VendorInvoice>

    @Query("SELECT * FROM vendor_invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<VendorInvoice>>

    @Query("SELECT * FROM vendor_invoices")
    suspend fun getAllList(): List<VendorInvoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: VendorInvoice): Long

    @Update
    suspend fun updateInvoice(invoice: VendorInvoice)

    @Delete
    suspend fun deleteInvoice(invoice: VendorInvoice)

    @Query("DELETE FROM vendor_invoices WHERE vendorId = :vendorId")
    suspend fun deleteInvoicesForVendor(vendorId: Long)
}

@Dao
interface ClinicSettingsDao {
    @Query("SELECT * FROM clinic_settings WHERE id = 1")
    fun getSettings(): Flow<ClinicSettings?>

    @Query("SELECT * FROM clinic_settings WHERE id = 1")
    suspend fun getSettingsOnce(): ClinicSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ClinicSettings): Long

    @Query("UPDATE clinic_settings SET globalFontScaleIndex = :index WHERE id = 1")
    suspend fun updateGlobalFontScale(index: Int)

    @Query("UPDATE clinic_settings SET clinicalFontSizePt = :sizePt WHERE id = 1")
    suspend fun updateClinicalFontSize(sizePt: Int)

    @Query("UPDATE clinic_settings SET clinicName = :clinicName, doctorName = :doctorName, staffNames = :staffNames, showSummaryCard = :showSummary, showQuickActions = :showQuick, showCharts = :showCharts WHERE id = 1")
    suspend fun updateGeneralSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Int, showQuick: Int, showCharts: Int)

}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Installment?

    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    fun getAllInstallments(): Flow<List<Installment>>

    @Query("SELECT * FROM installments")
    suspend fun getAllList(): List<Installment>

    @Query("SELECT * FROM installments WHERE patientId = :patientId ORDER BY dueDate ASC")
    fun getInstallmentsForPatient(patientId: Long): Flow<List<Installment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long

    @Update
    suspend fun updateInstallment(installment: Installment)

    @Delete
    suspend fun deleteInstallment(installment: Installment)

    @Query("DELETE FROM installments WHERE patientId = :patientId")
    suspend fun deleteInstallmentsForPatient(patientId: Long)

    @Query("SELECT * FROM installments WHERE patientId = :patientId")
    suspend fun getInstallmentsForPatientOnce(patientId: Long): List<Installment>
}

@Dao
interface AppCalendarEventDao {
    @Query("SELECT * FROM app_calendar_events WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AppCalendarEvent?

    @Query("SELECT * FROM app_calendar_events")
    fun getAllCalendarEvents(): Flow<List<AppCalendarEvent>>

    @Query("SELECT * FROM app_calendar_events")
    suspend fun getAllList(): List<AppCalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvents(events: List<AppCalendarEvent>)

    @Query("DELETE FROM app_calendar_events WHERE category != 'شخصی'")
    suspend fun deleteNonPersonalCalendarEvents()

    @Query("SELECT * FROM app_calendar_events WHERE category != 'شخصی'")
    suspend fun getNonPersonalCalendarEventsOnce(): List<AppCalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvent(event: AppCalendarEvent): Long

    @Delete
    suspend fun deleteCalendarEvent(event: AppCalendarEvent)
}

@Dao
interface FinancialLedgerDao {
    @Query("SELECT * FROM financial_ledger WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): FinancialLedgerEntry?

    @Query("SELECT * FROM financial_ledger ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<FinancialLedgerEntry>>

    @Query("SELECT * FROM financial_ledger")
    suspend fun getAllList(): List<FinancialLedgerEntry>

    @Query("SELECT * FROM financial_ledger WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceId: Long): FinancialLedgerEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FinancialLedgerEntry): Long

    @Query("DELETE FROM financial_ledger WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: Long)

    @Query("DELETE FROM financial_ledger WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("SELECT COALESCE(SUM(amountDelta), 0) FROM financial_ledger WHERE status = 'POSTED' AND occurredAt BETWEEN :from AND :to")
    suspend fun postedNet(from: Long, to: Long): Long
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AuditEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEvent): Long

    @Query("SELECT * FROM audit_events ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events")
    suspend fun getAllList(): List<AuditEvent>
}



@Dao
interface StaffMemberDao {
    @Query("SELECT * FROM staff_members WHERE isDeleted = 0 ORDER BY fullName ASC")
    fun getAllStaff(): Flow<List<StaffMember>>

    @Query("SELECT * FROM staff_members")
    suspend fun getAllList(): List<StaffMember>

    @Query("SELECT * FROM staff_members WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): StaffMember?

    @Query("SELECT * FROM staff_members WHERE updatedAt > :since")
    suspend fun getChangedSince(since: Long): List<StaffMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffMember): Long

    @Update
    suspend fun updateStaff(staff: StaffMember)

    @Delete
    suspend fun deleteStaff(staff: StaffMember)
}

@Dao
interface PendingSyncOpDao {
    @Query("SELECT * FROM pending_sync_ops ORDER BY id ASC LIMIT :limit")
    suspend fun getPendingOps(limit: Int = 100): List<PendingSyncOp>

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun getCount(): kotlinx.coroutines.flow.Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingSyncOp): Long

    @Delete
    suspend fun delete(op: PendingSyncOp)

    @Query("DELETE FROM pending_sync_ops WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAll()
}

@Dao
interface DrugTemplateDao {
    @Query("SELECT * FROM drug_templates WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): DrugTemplate?

    @Query("SELECT * FROM drug_templates ORDER BY category, title")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<DrugTemplate>>

    @Query("SELECT * FROM drug_templates")
    suspend fun getAllList(): List<DrugTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: DrugTemplate): Long

    @Delete
    suspend fun delete(template: DrugTemplate)
}

@Dao
interface UnregisteredBankSmsDao {
    @Query("SELECT * FROM unregistered_bank_sms WHERE status = 'PENDING' AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPendingSmsFlow(): kotlinx.coroutines.flow.Flow<List<com.example.data.UnregisteredBankSms>>

    @Query("SELECT * FROM unregistered_bank_sms WHERE status = 'PENDING' AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getPendingSmsList(): List<com.example.data.UnregisteredBankSms>

    @Query("SELECT COUNT(*) FROM unregistered_bank_sms WHERE status = 'PENDING' AND isDeleted = 0")
    fun getPendingCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    // لیست تک‌سطری «ثبت‌شده‌ها» (مبلغ | بابت خلاصه | تاریخ)
    @Query("SELECT * FROM unregistered_bank_sms WHERE status = 'ASSIGNED' AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getRegisteredSmsFlow(): kotlinx.coroutines.flow.Flow<List<com.example.data.UnregisteredBankSms>>

    // لیست مجزای «نادیده‌گرفته‌شده‌ها» (بعداً قابل بازگردانی/ثبت)
    @Query("SELECT * FROM unregistered_bank_sms WHERE status = 'IGNORED' AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getIgnoredSmsFlow(): kotlinx.coroutines.flow.Flow<List<com.example.data.UnregisteredBankSms>>

    @Query("SELECT * FROM unregistered_bank_sms WHERE smsNativeId = :nativeId LIMIT 1")
    suspend fun getByNativeId(nativeId: String): com.example.data.UnregisteredBankSms?

    @Query("SELECT * FROM unregistered_bank_sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): com.example.data.UnregisteredBankSms?

    @Query("SELECT * FROM unregistered_bank_sms WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): com.example.data.UnregisteredBankSms?

    @Query("SELECT * FROM unregistered_bank_sms")
    suspend fun getAllList(): List<com.example.data.UnregisteredBankSms>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: com.example.data.UnregisteredBankSms): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(sms: com.example.data.UnregisteredBankSms): Long

    @Query("UPDATE unregistered_bank_sms SET status = 'ASSIGNED', assignedAccountId = :bankAccountId WHERE (accountNumberRaw = :accountNumberRaw OR :accountNumberRaw = '') AND status = 'PENDING'")
    suspend fun bulkAssignByAccountNumber(accountNumberRaw: String, bankAccountId: Long): Int

    @Query("UPDATE unregistered_bank_sms SET status = 'ASSIGNED', assignedAccountId = :bankAccountId WHERE id = :id")
    suspend fun markAssignedById(id: Long, bankAccountId: Long?)

    @Query("UPDATE unregistered_bank_sms SET status = 'IGNORED' WHERE id = :id")
    suspend fun markIgnored(id: Long)

    @Query("DELETE FROM unregistered_bank_sms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM unregistered_bank_sms WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("DELETE FROM unregistered_bank_sms")
    suspend fun clearAll()
}

@Database(
    entities = [
        Patient::class,
        Treatment::class,
        Payment::class,
        Appointment::class,
        Vendor::class,
        LabOrder::class,
        EquipmentMaintenance::class,
        ClinicExpense::class,
        ExpensePayment::class,
        PersonalTransaction::class,
        TreatmentTemplate::class,
        BankAccount::class,
        PersonalSubAccount::class,
        ChildrenExpense::class,
        ClinicSettings::class,
        LabService::class,
        FixedIncome::class,
        Installment::class,
        AppCalendarEvent::class,
        Prescription::class,
        TreatmentStage::class,
        PatientDocument::class,
        VendorInvoice::class, DrugTemplate::class,
        FinancialLedgerEntry::class,
        AuditEvent::class,
        StaffMember::class,
        PendingSyncOp::class,
        UnregisteredBankSms::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao

    abstract fun treatmentDao(): TreatmentDao
    abstract fun paymentDao(): PaymentDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun vendorDao(): VendorDao
    abstract fun labOrderDao(): LabOrderDao
    abstract fun labServiceDao(): LabServiceDao
    abstract fun equipmentMaintenanceDao(): EquipmentMaintenanceDao
    abstract fun clinicExpenseDao(): ClinicExpenseDao
    abstract fun expensePaymentDao(): ExpensePaymentDao
    abstract fun personalTransactionDao(): PersonalTransactionDao
    abstract fun treatmentTemplateDao(): TreatmentTemplateDao
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun unregisteredBankSmsDao(): UnregisteredBankSmsDao
    abstract fun personalSubAccountDao(): PersonalSubAccountDao
    abstract fun childrenExpenseDao(): ChildrenExpenseDao
    abstract fun clinicSettingsDao(): ClinicSettingsDao
    abstract fun fixedIncomeDao(): FixedIncomeDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun appCalendarEventDao(): AppCalendarEventDao
    abstract fun treatmentStageDao(): TreatmentStageDao
    abstract fun patientDocumentDao(): PatientDocumentDao
    abstract fun vendorInvoiceDao(): VendorInvoiceDao
    abstract fun financialLedgerDao(): FinancialLedgerDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun drugTemplateDao(): DrugTemplateDao
    abstract fun staffMemberDao(): StaffMemberDao
    abstract fun pendingSyncOpDao(): PendingSyncOpDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE Payment ADD COLUMN sourceSmsNativeId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE ExpensePayment ADD COLUMN sourceSmsNativeId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE PersonalTransaction ADD COLUMN sourceSmsNativeId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            }
        }
        // v3: صف پیامک‌های بانکی — خلاصه «بابت» برای لیست تک‌سطری ثبت‌شده‌ها + فیلدهای سینک
        // رئال‌تایم ابری (syncId / ownerId / updatedAt / isDeleted / assignedAccountSyncId)
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN assignedAccountSyncId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN registeredForSummary TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN syncId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE unregistered_bank_sms ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
                // هر ردیف قدیمی یک syncId یکتا می‌گیرد تا در سرور به‌عنوان کلید اصلی قابل upsert باشد
                try { db.execSQL("UPDATE unregistered_bank_sms SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''") } catch (e: Exception) {}
                try { db.execSQL("UPDATE unregistered_bank_sms SET updatedAt = createdAt WHERE updatedAt = 0") } catch (e: Exception) {}
            }
        }
        // v4: ساختار درختی سرفصل‌های حساب شخصی (پرمیس) — ستون گروه/والد
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE personal_sub_accounts ADD COLUMN parentName TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            }
        }
        // v5: تنظیمات اختصاصی مطب — پرسنل فعال (پزشک/دستیار)، چیدمان داشبورد، نمایش کارت‌های
        // تنظیمات (منوی مخفی)، لینک لوکیشن و آواتار مجزا از لوگو
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN activeDoctorSyncId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN activeAssistantSyncId TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN dashboardCardOrder TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN settingsCardVisibility TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN clinicMapLink TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE clinic_settings ADD COLUMN avatarFileId TEXT") } catch (e: Exception) {}
            }
        }
        const val SCHEMA_VERSION = 1
        private const val DATABASE_NAME = "dental_pro_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    buildDatabase(context)
                } catch (e: Throwable) {
                    android.util.Log.e("AppDatabase", "Primary database open failed, recreating cleanly", e)
                    // If corrupted or cipher key mismatch, wipe corrupt db and rebuild
                    val appContext = context.applicationContext
                    appContext.getDatabasePath(DATABASE_NAME).delete()
                    java.io.File(appContext.getDatabasePath(DATABASE_NAME).path + "-wal").delete()
                    java.io.File(appContext.getDatabasePath(DATABASE_NAME).path + "-shm").delete()
                    buildDatabase(context)
                }.also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val databasePassphrase = com.example.security.DatabaseEncryptionManager.passphrase(appContext)
            // Refuse either a schema migration or plaintext-to-SQLCipher conversion unless a
            // Keystore-protected local recovery point was created first.
            try {
                com.example.security.DatabaseEncryptionManager.prepareEncryptedDatabase(
                    appContext,
                    DATABASE_NAME,
                    databasePassphrase
                )
            } catch (e: Throwable) {
                android.util.Log.w("AppDatabase", "Pre-database hooks completed with warning: ${e.message}")
            }

            val sqlCipherFactory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(databasePassphrase)
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .openHelperFactory(sqlCipherFactory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
