package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DentalRepository(val database: AppDatabase) {

    suspend fun <T> inTransaction(block: suspend DentalRepository.() -> T): T =
        database.withTransaction { block(this@DentalRepository) }

    // --- Foreign-key syncId resolution -------------------------------------
    // Every relational entity carries a local Room `xId: Long` (meaningless on
    // another device) alongside an `xSyncId: String` shadow field (the stable,
    // cross-device identity). These helpers fill that shadow field in from the
    // local id at write time, so SyncEngine can resolve the correct local row
    // on the *other* device during pull instead of trusting the raw local id
    // that travelled in the document.
    private suspend fun patientSyncIdOf(patientId: Long): String =
        database.patientDao().getPatientById(patientId)?.syncId ?: ""

    private suspend fun treatmentSyncIdOf(treatmentId: Long): String =
        database.treatmentDao().getTreatmentById(treatmentId)?.syncId ?: ""

    private suspend fun bankAccountSyncIdOf(bankAccountId: Long?): String =
        bankAccountId?.let { database.bankAccountDao().getBankAccountById(it)?.syncId } ?: ""

    private suspend fun vendorSyncIdOf(vendorId: Long?): String =
        vendorId?.let { database.vendorDao().getVendorById(it)?.syncId } ?: ""

    private suspend fun labOrderSyncIdOf(labOrderId: Long?): String =
        labOrderId?.let { database.labOrderDao().getLabOrderById(it)?.syncId } ?: ""

    private suspend fun maintenanceSyncIdOf(maintenanceId: Long?): String =
        maintenanceId?.let { database.equipmentMaintenanceDao().getMaintenanceById(it)?.syncId } ?: ""

    private suspend fun clinicExpenseSyncIdOf(clinicExpenseId: Long?): String =
        clinicExpenseId?.let { database.clinicExpenseDao().getExpenseById(it)?.syncId } ?: ""

    // Patients
    val allPatients: Flow<List<Patient>> = database.patientDao().getAllPatients()
    suspend fun insertPatient(patient: Patient): Long = database.patientDao().insertPatient(patient).also { enqueueSyncOp("patients", patient.syncId, "UPSERT") }
    suspend fun deletePatient(patient: Patient) = database.patientDao().deletePatient(patient).also { enqueueSyncOp("patients", patient.syncId, "DELETE") }
    suspend fun deletePatientCascading(patient: Patient) {
        // نکته‌ی مهم (رفع باگ): قبلاً این تابع رکوردهای مرتبط را مستقیم از دیتابیس محلی حذف می‌کرد
        // بدون آنکه هیچ عملیات DELETE ای در صف سینک ثبت شود — یعنی حذف هرگز به سرور نمی‌رسید و
        // آن رکوردها روی سرور برای همیشه باقی می‌ماندند. حالا پیش از هر حذف دسته‌ای، رکوردهای
        // مرتبط را می‌خوانیم تا syncId شان را داشته باشیم و برای هرکدام یک DELETE در صف سینک ثبت می‌کنیم.
        database.treatmentDao().getTreatmentsForPatientOnce(patient.id).forEach {
            enqueueSyncOp("treatments", it.syncId, "DELETE")
        }
        database.treatmentDao().deleteTreatmentsForPatient(patient.id)

        database.paymentDao().getPaymentsForPatientOnce(patient.id).forEach { p ->
            val ledger = database.financialLedgerDao().findBySource(SOURCE_PATIENT_PAYMENT, p.id)
            if (ledger != null) enqueueSyncOp("financial_ledger", ledger.syncId, "DELETE")
            enqueueSyncOp("payments", p.syncId, "DELETE")
        }
        database.paymentDao().deletePaymentsForPatient(patient.id)

        database.appointmentDao().getAppointmentsForPatientOnce(patient.id, patient.name).forEach {
            enqueueSyncOp("appointments", it.syncId, "DELETE")
        }
        database.appointmentDao().deleteAppointmentsForPatient(patient.id, patient.name)

        database.installmentDao().getInstallmentsForPatientOnce(patient.id).forEach {
            enqueueSyncOp("installments", it.syncId, "DELETE")
        }
        database.installmentDao().deleteInstallmentsForPatient(patient.id)

        database.prescriptionDao().getPrescriptionsForPatientOnce(patient.id).forEach {
            enqueueSyncOp("prescriptions", it.syncId, "DELETE")
        }
        database.prescriptionDao().deletePrescriptionsForPatient(patient.id)

        database.patientDocumentDao().getDocumentsForPatientOnce(patient.id).forEach {
            enqueueSyncOp("patient_documents", it.syncId, "DELETE")
        }
        database.patientDocumentDao().deleteDocumentsForPatient(patient.id)

        database.patientDao().deletePatient(patient)
        enqueueSyncOp("patients", patient.syncId, "DELETE")
    }

    // Treatment Stages (Relational)
    val allTreatmentStages: Flow<List<TreatmentStage>> = database.treatmentStageDao().getAllStages()
    fun getStagesForTreatment(treatmentId: Long): Flow<List<TreatmentStage>> = database.treatmentStageDao().getStagesForTreatment(treatmentId)
    suspend fun insertTreatmentStage(stage: TreatmentStage): Long {
        val enriched = stage.copy(treatmentSyncId = treatmentSyncIdOf(stage.treatmentId))
        return database.treatmentStageDao().insertStage(enriched).also { enqueueSyncOp("treatment_stages", enriched.syncId, "UPSERT") }
    }
    suspend fun insertTreatmentStages(stages: List<TreatmentStage>) = database.withTransaction {
        val enriched = stages.map { stage ->
            stage.copy(treatmentSyncId = treatmentSyncIdOf(stage.treatmentId))
        }
        database.treatmentStageDao().insertStages(enriched)
        enriched.forEach { enqueueSyncOp("treatment_stages", it.syncId, "UPSERT") }
    }
    suspend fun updateTreatmentStage(stage: TreatmentStage) {
        val enriched = stage.copy(treatmentSyncId = treatmentSyncIdOf(stage.treatmentId))
        database.treatmentStageDao().updateStage(enriched).also { enqueueSyncOp("treatment_stages", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteTreatmentStage(stage: TreatmentStage) = database.treatmentStageDao().deleteStage(stage).also { enqueueSyncOp("treatment_stages", stage.syncId, "DELETE") }
    suspend fun deleteStagesForTreatment(treatmentId: Long) = database.withTransaction {
        val stages = database.treatmentStageDao().getStagesForTreatmentOnce(treatmentId)
        stages.forEach { enqueueSyncOp("treatment_stages", it.syncId, "DELETE") }
        database.treatmentStageDao().deleteStagesForTreatment(treatmentId)
    }

    // Patient Documents (Relational)
    val allPatientDocuments: Flow<List<PatientDocument>> = database.patientDocumentDao().getAllDocuments()
    fun getDocumentsForPatient(patientId: Long): Flow<List<PatientDocument>> = database.patientDocumentDao().getDocumentsForPatient(patientId)
    suspend fun insertPatientDocument(doc: PatientDocument): Long {
        val enriched = doc.copy(patientSyncId = patientSyncIdOf(doc.patientId))
        return database.patientDocumentDao().insertDocument(enriched).also { enqueueSyncOp("patient_documents", enriched.syncId, "UPSERT") }
    }
    suspend fun deletePatientDocument(doc: PatientDocument) = database.patientDocumentDao().deleteDocument(doc).also { enqueueSyncOp("patient_documents", doc.syncId, "DELETE") }

    // Vendor Invoices (Relational)
    val allVendorInvoices: Flow<List<VendorInvoice>> = database.vendorInvoiceDao().getAllInvoices()
    fun getInvoicesForVendor(vendorId: Long): Flow<List<VendorInvoice>> = database.vendorInvoiceDao().getInvoicesForVendor(vendorId)
    suspend fun insertVendorInvoice(invoice: VendorInvoice): Long {
        val enriched = invoice.copy(vendorSyncId = vendorSyncIdOf(invoice.vendorId))
        return database.vendorInvoiceDao().insertInvoice(enriched).also { enqueueSyncOp("vendor_invoices", enriched.syncId, "UPSERT") }
    }
    suspend fun updateVendorInvoice(invoice: VendorInvoice) {
        val enriched = invoice.copy(vendorSyncId = vendorSyncIdOf(invoice.vendorId))
        database.vendorInvoiceDao().updateInvoice(enriched).also { enqueueSyncOp("vendor_invoices", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteVendorInvoice(invoice: VendorInvoice) = database.vendorInvoiceDao().deleteInvoice(invoice).also { enqueueSyncOp("vendor_invoices", invoice.syncId, "DELETE") }

    // Treatments
    val allTreatments: Flow<List<Treatment>> = database.treatmentDao().getAllTreatments()
    fun getTreatmentsForPatient(patientId: Long): Flow<List<Treatment>> = 
        database.treatmentDao().getTreatmentsForPatient(patientId)
    suspend fun insertTreatment(treatment: Treatment): Long {
        val enriched = treatment.copy(patientSyncId = patientSyncIdOf(treatment.patientId))
        return database.treatmentDao().insertTreatment(enriched).also { enqueueSyncOp("treatments", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteTreatment(treatment: Treatment) = database.withTransaction {
        val stages = database.treatmentStageDao().getStagesForTreatmentOnce(treatment.id)
        stages.forEach { enqueueSyncOp("treatment_stages", it.syncId, "DELETE") }
        database.treatmentStageDao().deleteStagesForTreatment(treatment.id)

        val prescriptions = database.prescriptionDao().getPrescriptionsForTreatmentOnce(treatment.id)
        prescriptions.forEach { enqueueSyncOp("prescriptions", it.syncId, "DELETE") }
        database.prescriptionDao().deletePrescriptionsForTreatment(treatment.id)

        database.treatmentDao().deleteTreatment(treatment)
        enqueueSyncOp("treatments", treatment.syncId, "DELETE")
    }

    // Payments — persistence, ledger projection and bank impact are one transaction.
    val allPayments: Flow<List<Payment>> = database.paymentDao().getAllPayments()
    fun getPaymentsForPatient(patientId: Long): Flow<List<Payment>> =
        database.paymentDao().getPaymentsForPatient(patientId)

    suspend fun insertPayment(payment: Payment): Long = database.withTransaction {
        val old = payment.id.takeIf { it != 0L }?.let { database.paymentDao().getPaymentById(it) }
        reconcilePaymentBankImpact(old, payment)
        val enriched = payment.copy(
            patientSyncId = patientSyncIdOf(payment.patientId),
            bankAccountSyncId = bankAccountSyncIdOf(payment.bankAccountId)
        )
        val id = database.paymentDao().insertPayment(enriched)
        val withId = enriched.copy(id = id)
        upsertPaymentLedger(withId)
        enqueueSyncOp("payments", withId.syncId, "UPSERT")
        id
    }

    suspend fun updatePayment(payment: Payment) = database.withTransaction {
        require(payment.id != 0L) { "Cannot update an unsaved payment" }
        val old = requireNotNull(database.paymentDao().getPaymentById(payment.id)) {
            "Payment ${payment.id} does not exist"
        }
        reconcilePaymentBankImpact(old, payment)
        val enriched = payment.copy(
            patientSyncId = patientSyncIdOf(payment.patientId),
            bankAccountSyncId = bankAccountSyncIdOf(payment.bankAccountId)
        )
        database.paymentDao().updatePayment(enriched)
        upsertPaymentLedger(enriched)
        enqueueSyncOp("payments", enriched.syncId, "UPSERT")
    }

    suspend fun deletePayment(payment: Payment) = database.withTransaction {
        val current = database.paymentDao().getPaymentById(payment.id) ?: return@withTransaction
        reconcilePaymentBankImpact(current, null)
        val ledger = database.financialLedgerDao().findBySource(SOURCE_PATIENT_PAYMENT, current.id)
        database.paymentDao().deletePayment(current)
        database.financialLedgerDao().deleteBySource(SOURCE_PATIENT_PAYMENT, current.id)
        if (ledger != null) enqueueSyncOp("financial_ledger", ledger.syncId, "DELETE")
        enqueueSyncOp("payments", current.syncId, "DELETE")
    }

    // Appointments
    val allAppointments: Flow<List<Appointment>> = database.appointmentDao().getAllAppointments()
    suspend fun insertAppointment(appointment: Appointment): Long {
        val enriched = appointment.copy(patientSyncId = patientSyncIdOf(appointment.patientId))
        return database.appointmentDao().insertAppointment(enriched).also { enqueueSyncOp("appointments", enriched.syncId, "UPSERT") }
    }
    suspend fun updateAppointment(appointment: Appointment) {
        val enriched = appointment.copy(patientSyncId = patientSyncIdOf(appointment.patientId))
        database.appointmentDao().updateAppointment(enriched).also { enqueueSyncOp("appointments", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteAppointment(appointment: Appointment) = database.appointmentDao().deleteAppointment(appointment).also { enqueueSyncOp("appointments", appointment.syncId, "DELETE") }

    // Vendors
    val allVendors: Flow<List<Vendor>> = database.vendorDao().getAllVendors()
    suspend fun insertVendor(vendor: Vendor): Long = database.vendorDao().insertVendor(vendor).also { enqueueSyncOp("vendors", vendor.syncId, "UPSERT") }
    suspend fun deleteVendor(vendor: Vendor) = database.withTransaction {
        val invoices = database.vendorInvoiceDao().getInvoicesForVendorOnce(vendor.id)
        invoices.forEach { enqueueSyncOp("vendor_invoices", it.syncId, "DELETE") }
        database.vendorInvoiceDao().deleteInvoicesForVendor(vendor.id)

        database.vendorDao().deleteVendor(vendor)
        enqueueSyncOp("vendors", vendor.syncId, "DELETE")
    }

    // Lab Orders
    val allLabOrders: Flow<List<LabOrder>> = database.labOrderDao().getAllLabOrders()
    suspend fun insertLabOrder(order: LabOrder): Long = database.labOrderDao().insertLabOrder(order).also { enqueueSyncOp("lab_orders", order.syncId, "UPSERT") }
    suspend fun deleteLabOrder(order: LabOrder) = database.labOrderDao().deleteLabOrder(order).also { enqueueSyncOp("lab_orders", order.syncId, "DELETE") }

    // Lab Services (Catalog & Prices)
    val allLabServices: Flow<List<LabService>> = database.labServiceDao().getAllLabServices()
    suspend fun insertLabService(service: LabService): Long = database.labServiceDao().insertLabService(service).also { enqueueSyncOp("lab_services", service.syncId, "UPSERT") }
    suspend fun deleteLabService(service: LabService) = database.labServiceDao().deleteLabService(service).also { enqueueSyncOp("lab_services", service.syncId, "DELETE") }

    // Equipment Maintenance
    val allMaintenance: Flow<List<EquipmentMaintenance>> = database.equipmentMaintenanceDao().getAllMaintenance()
    suspend fun insertMaintenance(maintenance: EquipmentMaintenance): Long = 
        database.equipmentMaintenanceDao().insertMaintenance(maintenance).also { enqueueSyncOp("equipment_maintenance", maintenance.syncId, "UPSERT") }
    suspend fun deleteMaintenance(maintenance: EquipmentMaintenance) = 
        database.equipmentMaintenanceDao().deleteMaintenance(maintenance).also { enqueueSyncOp("equipment_maintenance", maintenance.syncId, "DELETE") }

    // Clinic Expenses
    val allExpenses: Flow<List<ClinicExpense>> = database.clinicExpenseDao().getAllExpenses()
    suspend fun insertExpense(expense: ClinicExpense): Long {
        val enriched = expense.copy(
            labOrderSyncId = labOrderSyncIdOf(expense.labOrderId),
            vendorSyncId = vendorSyncIdOf(expense.vendorId),
            maintenanceSyncId = maintenanceSyncIdOf(expense.maintenanceId)
        )
        return database.clinicExpenseDao().insertExpense(enriched).also { enqueueSyncOp("clinic_expenses", enriched.syncId, "UPSERT") }
    }
    suspend fun updateExpense(expense: ClinicExpense) {
        val enriched = expense.copy(
            labOrderSyncId = labOrderSyncIdOf(expense.labOrderId),
            vendorSyncId = vendorSyncIdOf(expense.vendorId),
            maintenanceSyncId = maintenanceSyncIdOf(expense.maintenanceId)
        )
        database.clinicExpenseDao().updateExpense(enriched).also { enqueueSyncOp("clinic_expenses", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteExpense(expense: ClinicExpense) = database.withTransaction {
        val payments = database.expensePaymentDao().getPaymentsForExpenseOnce(expense.id)
        payments.forEach { p ->
            val ledger = database.financialLedgerDao().findBySource(SOURCE_EXPENSE_PAYMENT, p.id)
            if (ledger?.createdAt != 0L) {
                reconcileSignedBankImpact(p.bankAccountId, -p.amount, null, null)
            }
            database.expensePaymentDao().deleteExpensePayment(p)
            database.financialLedgerDao().deleteBySource(SOURCE_EXPENSE_PAYMENT, p.id)
            if (ledger != null) enqueueSyncOp("financial_ledger", ledger.syncId, "DELETE")
            enqueueSyncOp("expense_payments", p.syncId, "DELETE")
        }
        database.clinicExpenseDao().deleteExpense(expense)
        enqueueSyncOp("clinic_expenses", expense.syncId, "DELETE")
    }

    // Expense payments — a payment (not merely an expense invoice) moves cash.
    val allExpensePayments: Flow<List<ExpensePayment>> = database.expensePaymentDao().getAllExpensePayments()
    fun getPaymentsForExpense(expenseId: Long): Flow<List<ExpensePayment>> = database.expensePaymentDao().getPaymentsForExpense(expenseId)

    suspend fun insertExpensePayment(payment: ExpensePayment): Long = database.withTransaction {
        val old = payment.id.takeIf { it != 0L }?.let { database.expensePaymentDao().getExpensePaymentById(it) }
        reconcileSignedBankImpact(
            oldBankId = old?.bankAccountId,
            oldDelta = old?.let { -it.amount },
            newBankId = payment.bankAccountId,
            newDelta = -payment.amount,
        )
        val enriched = payment.copy(
            clinicExpenseSyncId = clinicExpenseSyncIdOf(payment.clinicExpenseId),
            bankAccountSyncId = bankAccountSyncIdOf(payment.bankAccountId)
        )
        val id = database.expensePaymentDao().insertExpensePayment(enriched)
        val previous = database.financialLedgerDao().findBySource(SOURCE_EXPENSE_PAYMENT, id)
        val ledgerEntry = FinancialLedgerEntry(
            id = previous?.id ?: 0,
            sourceType = SOURCE_EXPENSE_PAYMENT,
            sourceId = id,
            bankAccountId = payment.bankAccountId,
            bankAccountSyncId = enriched.bankAccountSyncId,
            amountDelta = -payment.amount,
            status = "POSTED",
            occurredAt = payment.date,
            description = payment.note.ifBlank { "Expense payment" },
            createdAt = previous?.createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
        database.financialLedgerDao().upsert(ledgerEntry)
        enqueueSyncOp("expense_payments", enriched.syncId, "UPSERT")
        enqueueSyncOp("financial_ledger", ledgerEntry.syncId, "UPSERT")
        id
    }

    suspend fun deleteExpensePayment(payment: ExpensePayment) = database.withTransaction {
        val current = database.expensePaymentDao().getExpensePaymentById(payment.id) ?: return@withTransaction
        val ledger = database.financialLedgerDao().findBySource(SOURCE_EXPENSE_PAYMENT, current.id)
        // Version-32 records did not consistently change bank balances; only reverse entries created by this engine.
        if (ledger?.createdAt != 0L) {
            reconcileSignedBankImpact(current.bankAccountId, -current.amount, null, null)
        }
        database.expensePaymentDao().deleteExpensePayment(current)
        database.financialLedgerDao().deleteBySource(SOURCE_EXPENSE_PAYMENT, current.id)
        if (ledger != null) enqueueSyncOp("financial_ledger", ledger.syncId, "DELETE")
        enqueueSyncOp("expense_payments", current.syncId, "DELETE")
    }

    // Personal transactions
    val allTransactions: Flow<List<PersonalTransaction>> = database.personalTransactionDao().getAllTransactions()

    suspend fun insertTransaction(tx: PersonalTransaction): Long = database.withTransaction {
        val old = tx.id.takeIf { it != 0L }?.let { database.personalTransactionDao().getTransactionById(it) }
        reconcileSignedBankImpact(
            oldBankId = old?.bankAccountId,
            oldDelta = old?.let(::personalBankDelta),
            newBankId = tx.bankAccountId,
            newDelta = personalBankDelta(tx),
        )
        val enriched = tx.copy(bankAccountSyncId = bankAccountSyncIdOf(tx.bankAccountId))
        val id = database.personalTransactionDao().insertTransaction(enriched)
        val previous = database.financialLedgerDao().findBySource(SOURCE_PERSONAL_TRANSACTION, id)
        val ledgerEntry = FinancialLedgerEntry(
            id = previous?.id ?: 0,
            sourceType = SOURCE_PERSONAL_TRANSACTION,
            sourceId = id,
            bankAccountId = tx.bankAccountId,
            bankAccountSyncId = enriched.bankAccountSyncId,
            amountDelta = personalBankDelta(tx),
            status = "POSTED",
            occurredAt = tx.date,
            description = tx.description,
            createdAt = previous?.createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
        database.financialLedgerDao().upsert(ledgerEntry)
        enqueueSyncOp("personal_transactions", enriched.syncId, "UPSERT")
        enqueueSyncOp("financial_ledger", ledgerEntry.syncId, "UPSERT")
        id
    }

    suspend fun deleteTransaction(tx: PersonalTransaction) = database.withTransaction {
        val current = database.personalTransactionDao().getTransactionById(tx.id) ?: return@withTransaction
        reconcileSignedBankImpact(current.bankAccountId, personalBankDelta(current), null, null)
        val ledger = database.financialLedgerDao().findBySource(SOURCE_PERSONAL_TRANSACTION, current.id)
        database.personalTransactionDao().deleteTransaction(current)
        database.financialLedgerDao().deleteBySource(SOURCE_PERSONAL_TRANSACTION, current.id)
        if (ledger != null) enqueueSyncOp("financial_ledger", ledger.syncId, "DELETE")
        enqueueSyncOp("personal_transactions", current.syncId, "DELETE")
    }

    // --- NEW REPO MODULES ---

    // Treatment Templates
    val allTemplates: Flow<List<TreatmentTemplate>> = database.treatmentTemplateDao().getAllTemplates()
    suspend fun insertTemplate(template: TreatmentTemplate): Long = database.treatmentTemplateDao().insertTemplate(template).also { enqueueSyncOp("treatment_templates", template.syncId, "UPSERT") }
    suspend fun insertTemplates(templates: List<TreatmentTemplate>) = database.withTransaction {
        database.treatmentTemplateDao().insertTemplates(templates)
        templates.forEach { enqueueSyncOp("treatment_templates", it.syncId, "UPSERT") }
    }
    suspend fun deleteTemplate(template: TreatmentTemplate) = database.treatmentTemplateDao().deleteTemplate(template).also { enqueueSyncOp("treatment_templates", template.syncId, "DELETE") }

    // Prescriptions
    val allPrescriptions: Flow<List<Prescription>> = database.prescriptionDao().getAllPrescriptions()
    fun prescriptionsForPatient(patientId: Long): Flow<List<Prescription>> = database.prescriptionDao().getPrescriptionsForPatient(patientId)
    fun prescriptionsForTreatment(treatmentId: Long): Flow<List<Prescription>> = database.prescriptionDao().getPrescriptionsForTreatment(treatmentId)
    suspend fun insertPrescription(prescription: Prescription): Long {
        val enriched = prescription.copy(
            patientSyncId = patientSyncIdOf(prescription.patientId),
            treatmentSyncId = treatmentSyncIdOf(prescription.treatmentId)
        )
        return database.prescriptionDao().insertPrescription(enriched).also { enqueueSyncOp("prescriptions", enriched.syncId, "UPSERT") }
    }
    suspend fun deletePrescription(prescription: Prescription) = database.prescriptionDao().deletePrescription(prescription).also { enqueueSyncOp("prescriptions", prescription.syncId, "DELETE") }

    // Bank Accounts
    val allBankAccounts: Flow<List<BankAccount>> = database.bankAccountDao().getAllBankAccounts()
    suspend fun insertBankAccount(account: BankAccount): Long = database.bankAccountDao().insertBankAccount(account).also { enqueueSyncOp("bank_accounts", account.syncId, "UPSERT") }
    suspend fun deleteBankAccount(account: BankAccount) = database.bankAccountDao().deleteBankAccount(account).also { enqueueSyncOp("bank_accounts", account.syncId, "DELETE") }

    // Personal Sub Accounts
    val allSubAccounts: Flow<List<PersonalSubAccount>> = database.personalSubAccountDao().getAllSubAccounts()
    suspend fun insertSubAccount(sub: PersonalSubAccount): Long = database.personalSubAccountDao().insertSubAccount(sub).also { enqueueSyncOp("personal_sub_accounts", sub.syncId, "UPSERT") }
    suspend fun deleteSubAccount(sub: PersonalSubAccount) = database.personalSubAccountDao().deleteSubAccount(sub).also { enqueueSyncOp("personal_sub_accounts", sub.syncId, "DELETE") }

    // Children Expenses
    val allChildrenExpenses: Flow<List<ChildrenExpense>> = database.childrenExpenseDao().getAllChildrenExpenses()
    suspend fun insertChildrenExpense(expense: ChildrenExpense): Long = database.childrenExpenseDao().insertChildrenExpense(expense).also { enqueueSyncOp("children_expenses", expense.syncId, "UPSERT") }
    suspend fun deleteChildrenExpense(expense: ChildrenExpense) = database.childrenExpenseDao().deleteChildrenExpense(expense).also { enqueueSyncOp("children_expenses", expense.syncId, "DELETE") }

    // Unregistered Bank SMS Queue — صف پیامک‌های بانکی (ثبت‌نشده/ثبت‌شده/نادیده‌گرفته)
    // هر درج/تغییر وضعیت از این مسیر در صف سینک قرار می‌گیرد تا رئال‌تایم به سرور برود.
    suspend fun upsertUnregisteredBankSms(sms: UnregisteredBankSms): Long {
        val stamped = sms.copy(updatedAt = System.currentTimeMillis())
        val id = database.unregisteredBankSmsDao().insertOrReplace(stamped)
        enqueueSyncOp("unregistered_bank_sms", stamped.syncId, "UPSERT")
        return id
    }

    suspend fun deleteUnregisteredBankSms(sms: UnregisteredBankSms) {
        database.unregisteredBankSmsDao().deleteById(sms.id)
        enqueueSyncOp("unregistered_bank_sms", sms.syncId, "DELETE")
    }

    // Fixed Incomes
    val allFixedIncomes: Flow<List<FixedIncome>> = database.fixedIncomeDao().getAllFixedIncomes()
    suspend fun insertFixedIncome(income: FixedIncome): Long {
        val enriched = income.copy(bankAccountSyncId = bankAccountSyncIdOf(income.bankAccountId))
        return database.fixedIncomeDao().insertFixedIncome(enriched).also { enqueueSyncOp("fixed_incomes", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteFixedIncome(income: FixedIncome) = database.fixedIncomeDao().deleteFixedIncome(income).also { enqueueSyncOp("fixed_incomes", income.syncId, "DELETE") }

    // Drug Templates
    val allDrugTemplates: Flow<List<DrugTemplate>> = database.drugTemplateDao().getAll()
    suspend fun insertDrugTemplate(template: DrugTemplate): Long = database.drugTemplateDao().insert(template).also { enqueueSyncOp("drug_templates", template.syncId, "UPSERT") }
    suspend fun deleteDrugTemplate(template: DrugTemplate) = database.drugTemplateDao().delete(template).also { enqueueSyncOp("drug_templates", template.syncId, "DELETE") }

    // Settings — single-row table (id = 1).
    val clinicSettings: Flow<ClinicSettings?> = database.clinicSettingsDao().getSettings()
    suspend fun getSettingsOnce(): ClinicSettings? = database.clinicSettingsDao().getSettingsOnce()
    suspend fun insertSettings(settings: ClinicSettings): Long = database.clinicSettingsDao().insertSettings(settings).also { enqueueSyncOp("clinic_settings", settings.syncId, "UPSERT") }

    suspend fun updateGlobalFontScale(index: Int) {
        database.clinicSettingsDao().updateGlobalFontScale(index)
        getSettingsOnce()?.let { enqueueSyncOp("clinic_settings", it.syncId, "UPSERT") }
    }
    suspend fun updateClinicalFontSize(sizePt: Int) {
        database.clinicSettingsDao().updateClinicalFontSize(sizePt)
        getSettingsOnce()?.let { enqueueSyncOp("clinic_settings", it.syncId, "UPSERT") }
    }
    suspend fun updateGeneralSettings(clinicName: String, doctorName: String, staffNames: String, showSummary: Boolean, showQuick: Boolean, showCharts: Boolean) {
        database.clinicSettingsDao().updateGeneralSettings(clinicName, doctorName, staffNames, if(showSummary) 1 else 0, if(showQuick) 1 else 0, if(showCharts) 1 else 0)
        getSettingsOnce()?.let { enqueueSyncOp("clinic_settings", it.syncId, "UPSERT") }
    }

    // Installments
    val allInstallments: Flow<List<Installment>> = database.installmentDao().getAllInstallments()
    fun getInstallmentsForPatient(patientId: Long): Flow<List<Installment>> = database.installmentDao().getInstallmentsForPatient(patientId)
    suspend fun insertInstallment(installment: Installment): Long {
        val enriched = installment.copy(
            patientSyncId = patientSyncIdOf(installment.patientId),
            treatmentSyncId = installment.treatmentId?.let { treatmentSyncIdOf(it) } ?: ""
        )
        return database.installmentDao().insertInstallment(enriched).also { enqueueSyncOp("installments", enriched.syncId, "UPSERT") }
    }
    suspend fun updateInstallment(installment: Installment) {
        val enriched = installment.copy(
            patientSyncId = patientSyncIdOf(installment.patientId),
            treatmentSyncId = installment.treatmentId?.let { treatmentSyncIdOf(it) } ?: ""
        )
        database.installmentDao().updateInstallment(enriched).also { enqueueSyncOp("installments", enriched.syncId, "UPSERT") }
    }
    suspend fun deleteInstallment(installment: Installment) = database.installmentDao().deleteInstallment(installment).also { enqueueSyncOp("installments", installment.syncId, "DELETE") }

    // App Calendar Events
    val allCalendarEvents: Flow<List<AppCalendarEvent>> = database.appCalendarEventDao().getAllCalendarEvents()
    suspend fun insertCalendarEvents(events: List<AppCalendarEvent>) = database.withTransaction {
        database.appCalendarEventDao().insertCalendarEvents(events)
        events.forEach { enqueueSyncOp("app_calendar_events", it.syncId, "UPSERT") }
    }
    suspend fun deleteNonPersonalCalendarEvents() = database.withTransaction {
        val nonPersonal = database.appCalendarEventDao().getNonPersonalCalendarEventsOnce()
        nonPersonal.forEach { enqueueSyncOp("app_calendar_events", it.syncId, "DELETE") }
        database.appCalendarEventDao().deleteNonPersonalCalendarEvents()
    }
    suspend fun insertCalendarEvent(event: AppCalendarEvent): Long = database.appCalendarEventDao().insertCalendarEvent(event).also { enqueueSyncOp("app_calendar_events", event.syncId, "UPSERT") }
    suspend fun deleteCalendarEvent(event: AppCalendarEvent) = database.appCalendarEventDao().deleteCalendarEvent(event).also { enqueueSyncOp("app_calendar_events", event.syncId, "DELETE") }

    val financialLedger: Flow<List<FinancialLedgerEntry>> = database.financialLedgerDao().observeAll()
    val recentAuditEvents: Flow<List<AuditEvent>> = database.auditEventDao().observeRecent()

    suspend fun recordAudit(event: AuditEvent): Long {
        val id = database.auditEventDao().insert(event)
        enqueueSyncOp("audit_events", event.syncId, "UPSERT")
        return id
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Room's generated clearAllTables implementation is transactional and preserves invalidation.
        database.clearAllTables()
    }

    suspend fun createConsistentDatabaseSnapshot(destination: java.io.File) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        destination.delete()
        val encryptedSnapshot = java.io.File(destination.parentFile, "${destination.name}.sqlcipher")
        encryptedSnapshot.delete()
        try {
            // VACUUM INTO is an online, transactionally consistent snapshot. SQLCipher keeps this
            // intermediate encrypted; only the portable backup's outer AES-GCM container receives
            // a plaintext SQLite export.
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(encryptedSnapshot.absolutePath)
            )
            check(encryptedSnapshot.isFile && encryptedSnapshot.length() > 0L) { "SQLite snapshot was not created" }
            com.example.security.DatabaseEncryptionManager.exportPortablePlaintextSnapshot(
                encryptedSnapshot = encryptedSnapshot,
                plaintextDestination = destination,
                passphrase = com.example.security.DatabaseEncryptionManager.currentProcessPassphrase()
            )
        } finally {
            encryptedSnapshot.delete()
        }
    }

    private suspend fun upsertPaymentLedger(payment: Payment) {
        val existing = database.financialLedgerDao().findBySource(SOURCE_PATIENT_PAYMENT, payment.id)
        val posted = isPaymentPosted(payment)
        val status = when {
            payment.chequeStatus == "BOUNCED" -> "BOUNCED"
            posted -> "POSTED"
            else -> "PENDING"
        }
        val ledgerEntry = FinancialLedgerEntry(
            id = existing?.id ?: 0,
            sourceType = SOURCE_PATIENT_PAYMENT,
            sourceId = payment.id,
            patientId = payment.patientId,
            patientSyncId = payment.patientSyncId,
            bankAccountId = payment.bankAccountId,
            bankAccountSyncId = payment.bankAccountSyncId,
            amountDelta = if (posted) payment.amount else 0L,
            status = status,
            occurredAt = payment.date,
            description = if (payment.type == "CHEQUE") {
                "Patient cheque ${payment.chequeNumber.orEmpty()}"
            } else {
                "Patient payment (${payment.type})"
            },
            createdAt = existing?.createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
        database.financialLedgerDao().upsert(ledgerEntry)
        enqueueSyncOp("financial_ledger", ledgerEntry.syncId, "UPSERT")
    }

    private suspend fun reconcilePaymentBankImpact(old: Payment?, new: Payment?) {
        reconcileSignedBankImpact(
            oldBankId = old?.bankAccountId,
            oldDelta = old?.let(::paymentBankDelta),
            newBankId = new?.bankAccountId,
            newDelta = new?.let(::paymentBankDelta),
        )
    }

    private suspend fun reconcileSignedBankImpact(
        oldBankId: Long?,
        oldDelta: Long?,
        newBankId: Long?,
        newDelta: Long?,
    ) {
        if (oldBankId != null && oldDelta != null && oldDelta != 0L) {
            check(database.bankAccountDao().adjustBalance(oldBankId, -oldDelta) == 1) {
                "Bank account $oldBankId does not exist"
            }
            database.bankAccountDao().getBankAccountById(oldBankId)?.let {
                enqueueSyncOp("bank_accounts", it.syncId, "UPSERT")
            }
        }
        if (newBankId != null && newDelta != null && newDelta != 0L) {
            check(database.bankAccountDao().adjustBalance(newBankId, newDelta) == 1) {
                "Bank account $newBankId does not exist"
            }
            database.bankAccountDao().getBankAccountById(newBankId)?.let {
                enqueueSyncOp("bank_accounts", it.syncId, "UPSERT")
            }
        }
    }

    private fun paymentBankDelta(payment: Payment): Long = when {
        payment.bankAccountId == null -> 0L
        payment.type != "CHEQUE" -> payment.amount
        payment.chequeStatus == "CLEARED" || payment.isCleared -> payment.amount
        else -> 0L
    }

    private fun isPaymentPosted(payment: Payment): Boolean =
        payment.type != "CHEQUE" ||
            payment.isCleared ||
            payment.chequeStatus == "CLEARED" ||
            payment.chequeStatus == "TRANSFERRED"

    private fun personalBankDelta(tx: PersonalTransaction): Long =
        if (tx.type == "INCOME" || tx.type == "DRAW") tx.amount else -tx.amount

    private companion object {
        const val SOURCE_PATIENT_PAYMENT = "PATIENT_PAYMENT"
        const val SOURCE_EXPENSE_PAYMENT = "EXPENSE_PAYMENT"
        const val SOURCE_PERSONAL_TRANSACTION = "PERSONAL_TRANSACTION"
    }

    // Staff Members (Clinical Staff / Assistants)
    val allStaffMembers: Flow<List<StaffMember>> = database.staffMemberDao().getAllStaff()
    suspend fun insertStaffMember(staff: StaffMember): Long = database.staffMemberDao().insertStaff(staff).also {
        enqueueSyncOp("staff_members", staff.syncId, "UPSERT")
    }
    suspend fun updateStaffMember(staff: StaffMember) = database.staffMemberDao().updateStaff(staff).also {
        enqueueSyncOp("staff_members", staff.syncId, "UPSERT")
    }
    suspend fun deleteStaffMember(staff: StaffMember) = database.staffMemberDao().deleteStaff(staff).also {
        enqueueSyncOp("staff_members", staff.syncId, "DELETE")
    }

    // Pending Sync Operations Queue
    val pendingSyncOpsCount: Flow<Int> = database.pendingSyncOpDao().getCount()

    suspend fun enqueueSyncOp(table: String, syncId: String, op: String = "UPSERT") {
        if (syncId.isBlank()) return
        try {
            database.pendingSyncOpDao().insert(
                PendingSyncOp(
                    collectionTable = table,
                    recordSyncId = syncId,
                    operation = op
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * رفعِ باگِ «عکسِ ثبت‌شده قابل فراخوانی نیست»: pushPending بعد از ۵ بار شکستِ پیاپی، آن op
     * را برای همیشه از صف حذف می‌کند (تا صف گیر نکند) — اما اگر آن شکست‌ها به‌خاطر یک مشکل
     * موقتِ سمت سرور بوده باشد (مثل نبودِ ایندکس/documentSecurity که با اسکریپت سرور رفع می‌شود)،
     * آن رکورد دیگر هرگز خودکار retry نمی‌شود، درحالی‌که فیلد فایلش هنوز یک مسیرِ محلیِ خام
     * (مثلاً "/data/user/.../images/xxx.jpg") است — یعنی هیچ‌وقت واقعاً آپلود نشده، پس روی
     * سرور یا هر دستگاه دیگری اصلاً وجود ندارد. این تابع تمام رکوردهای هر جدولِ صاحبِ فایل را
     * می‌گردد و هر کدام که فیلد فایلش هنوز با «/» شروع شود (یعنی هنوز آپلود نشده) را دوباره به
     * صف سینک اضافه می‌کند. باید هم دستی (دکمه‌ی «تلاش مجدد آپلودها») و هم دوره‌ای صدا زده شود.
     */
    suspend fun requeueStuckLocalFiles(): Int {
        var requeued = 0
        database.patientDao().getAllList().forEach {
            if (!it.photoFileId.isNullOrBlank() && it.photoFileId.startsWith("/")) {
                enqueueSyncOp("patients", it.syncId, "UPSERT"); requeued++
            }
        }
        database.staffMemberDao().getAllList().forEach {
            if (!it.photoFileId.isNullOrBlank() && it.photoFileId.startsWith("/")) {
                enqueueSyncOp("staff_members", it.syncId, "UPSERT"); requeued++
            }
        }
        database.paymentDao().getAllList().forEach {
            if (!it.chequePhotoFileId.isNullOrBlank() && it.chequePhotoFileId.startsWith("/")) {
                enqueueSyncOp("payments", it.syncId, "UPSERT"); requeued++
            }
        }
        database.patientDocumentDao().getAllDocuments().first().forEach {
            if (it.fileId.isNullOrBlank() && !it.uri.isNullOrBlank() && it.uri.startsWith("/")) {
                enqueueSyncOp("patient_documents", it.syncId, "UPSERT"); requeued++
            }
        }
        database.vendorInvoiceDao().getAllInvoices().first().forEach {
            if (!it.documentFileId.isNullOrBlank() && it.documentFileId.startsWith("/")) {
                enqueueSyncOp("vendor_invoices", it.syncId, "UPSERT"); requeued++
            }
        }
        database.clinicExpenseDao().getAllExpenses().first().forEach {
            if (!it.invoicePhotoFileId.isNullOrBlank() && it.invoicePhotoFileId.startsWith("/")) {
                enqueueSyncOp("clinic_expenses", it.syncId, "UPSERT"); requeued++
            }
        }
        return requeued
    }

    /**
     * صف‌بندی تمامی داده‌های محلی جهت همگام‌سازی کامل با سرور
     */
    suspend fun enqueueAllLocalData(): Int {
        var count = 0
        val settings = database.clinicSettingsDao().getSettingsOnce()
        if (settings != null) { enqueueSyncOp("clinic_settings", settings.syncId, "UPSERT"); count++ }
        database.staffMemberDao().getAllList().forEach { enqueueSyncOp("staff_members", it.syncId, "UPSERT"); count++ }
        database.bankAccountDao().getAllList().forEach { enqueueSyncOp("bank_accounts", it.syncId, "UPSERT"); count++ }
        database.personalSubAccountDao().getAllList().forEach { enqueueSyncOp("personal_sub_accounts", it.syncId, "UPSERT"); count++ }
        database.treatmentTemplateDao().getAllList().forEach { enqueueSyncOp("treatment_templates", it.syncId, "UPSERT"); count++ }
        database.drugTemplateDao().getAllList().forEach { enqueueSyncOp("drug_templates", it.syncId, "UPSERT"); count++ }
        database.labServiceDao().getAllList().forEach { enqueueSyncOp("lab_services", it.syncId, "UPSERT"); count++ }
        database.fixedIncomeDao().getAllList().forEach { enqueueSyncOp("fixed_incomes", it.syncId, "UPSERT"); count++ }
        database.childrenExpenseDao().getAllList().forEach { enqueueSyncOp("children_expenses", it.syncId, "UPSERT"); count++ }
        database.vendorDao().getAllList().forEach { enqueueSyncOp("vendors", it.syncId, "UPSERT"); count++ }
        database.patientDao().getAllList().forEach { enqueueSyncOp("patients", it.syncId, "UPSERT"); count++ }
        database.labOrderDao().getAllList().forEach { enqueueSyncOp("lab_orders", it.syncId, "UPSERT"); count++ }
        database.equipmentMaintenanceDao().getAllList().forEach { enqueueSyncOp("equipment_maintenance", it.syncId, "UPSERT"); count++ }
        database.clinicExpenseDao().getAllList().forEach { enqueueSyncOp("clinic_expenses", it.syncId, "UPSERT"); count++ }
        database.treatmentDao().getAllTreatments().first().forEach { enqueueSyncOp("treatments", it.syncId, "UPSERT"); count++ }
        database.treatmentStageDao().getAllList().forEach { enqueueSyncOp("treatment_stages", it.syncId, "UPSERT"); count++ }
        database.appointmentDao().getAllAppointments().first().forEach { enqueueSyncOp("appointments", it.syncId, "UPSERT"); count++ }
        database.paymentDao().getAllList().forEach { enqueueSyncOp("payments", it.syncId, "UPSERT"); count++ }
        database.expensePaymentDao().getAllList().forEach { enqueueSyncOp("expense_payments", it.syncId, "UPSERT"); count++ }
        database.personalTransactionDao().getAllList().forEach { enqueueSyncOp("personal_transactions", it.syncId, "UPSERT"); count++ }
        database.installmentDao().getAllList().forEach { enqueueSyncOp("installments", it.syncId, "UPSERT"); count++ }
        database.prescriptionDao().getAllList().forEach { enqueueSyncOp("prescriptions", it.syncId, "UPSERT"); count++ }
        database.patientDocumentDao().getAllDocuments().first().forEach { enqueueSyncOp("patient_documents", it.syncId, "UPSERT"); count++ }
        database.vendorInvoiceDao().getAllList().forEach { enqueueSyncOp("vendor_invoices", it.syncId, "UPSERT"); count++ }
        database.appCalendarEventDao().getAllList().forEach { enqueueSyncOp("app_calendar_events", it.syncId, "UPSERT"); count++ }
        database.financialLedgerDao().getAllList().forEach { enqueueSyncOp("financial_ledger", it.syncId, "UPSERT"); count++ }
        database.auditEventDao().getAllList().forEach { enqueueSyncOp("audit_events", it.syncId, "UPSERT"); count++ }
        return count
    }

    suspend fun getPendingOps(limit: Int = 100): List<PendingSyncOp> =
        database.pendingSyncOpDao().getPendingOps(limit)

    suspend fun removePendingOp(id: Long) {
        database.pendingSyncOpDao().deleteById(id)
    }
}
