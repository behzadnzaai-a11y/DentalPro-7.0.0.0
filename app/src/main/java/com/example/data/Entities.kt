package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientCode: String = "", // e.g. "P-10482"
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val medicalHistory: String = "", // e.g., "Allergies, Diabetes, High Blood Pressure"
    val plannedTreatmentIds: String = "",
    val note: String = "",
    val referrer: String = "",
    val photoLocalPath: String? = null, // Local disk cache path only, keyed by photoFileId; rebuilt on demand, never the source of truth
    val photoFileId: String? = null, // Supabase Storage object path — the ONLY source of truth for the patient profile photo
    val documentUris: String = "", // Comma-separated paths of clinical files/radiographs
    val createdAt: Long = System.currentTimeMillis(),
    val gender: String = "مرد", // "مرد" or "زن"
    val medicalAlerts: String = "", // Comma-separated alerts e.g. "دیابت, فشارخون, حساسیت به پنسلین"
    val selfDeclarationJson: String = "", // Interactive Dental Self-Declaration form answers
    val age: Int = 0, // Age of patient
    val birthDate: String = "", // Birth date string e.g. "1375/04/10" or "1375"
    val preferredMessenger: String = "واتساپ", // "واتساپ", "تلگرام", "روبیکا", "بله", "پیامک"
    val fatherName: String = "",
    val nationalId: String = "",
    val signatureFileId: String? = null,
    val assistantIds: String = "", // Comma-separated StaffMember.syncId of assistant(s) who handled reception/intake (syncId, not local id, so this stays valid across devices)
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "treatments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["date"])
    ]
)
data class Treatment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val title: String, // e.g., "Filling", "Root Canal", "Implant"
    val cost: Long,
    val discount: Long = 0L, // Discount amount
    val selectedTeeth: String = "", // Comma-separated list of selected tooth numbers from the 32-tooth chart
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val status: String = "PLANNED",
    val stages: String = "",
    val completedStages: String = "",
    val ignoredStages: String = "", // مراحلی که به‌دلیل عدم نیاز ایگنور شده‌اند (comma-separated)
    val assistantIds: String = "", // Comma-separated StaffMember.syncId of assistant(s) who performed/assisted this treatment (syncId, not local id, so this stays valid across devices)
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["date"]),
        Index(value = ["chequeDueDate"])
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val amount: Long,
    val type: String, // CASH, CHEQUE, CARD
    val bankAccountId: Long? = null, // Linked bank card/account
    val bankAccountSyncId: String = "",
    val sourceSmsNativeId: String = "",
    val chequeNumber: String? = null,
    val chequeDueDate: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val isCleared: Boolean = true,
    val sayadiId: String? = null,
    val chequeBank: String? = null,
    val chequeDrawer: String? = null,
    @androidx.room.ColumnInfo(name = "chequePhotoBase64") val chequePhotoFileId: String? = null,
    val chequeStatus: String = "PENDING", // PENDING, CLEARED, BOUNCED, TRANSFERRED
    val transferredTo: String? = null,
    val transferDate: Long? = null,
    val assistantIds: String = "", // Comma-separated StaffMember.syncId of assistant(s) who handled this payment (syncId, not local id, so this stays valid across devices)
    val notes: String? = null, // یادداشت تکمیلی — مثلاً مبلغ‌به‌حروف/کدملی استخراج‌شده توسط AI از روی عکس چک
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val patientName: String,
    val date: Long, // timestamp
    val treatmentType: String,
    val plannedTreatmentIds: String = "",
    val note: String = "",
    val referrer: String = "",
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val dischargeSent: Boolean = false,
    val dischargeMethod: String = "",
    val status: String = "PENDING", // PENDING, IN_OFFICE, COMPLETED, CANCELED
    // یونیت/صندلی درمانی این نوبت — UNIT_1 (پیش‌فرض), UNIT_2, HYGIENE
    // Nullable تا سینک ابری با رکوردهای قدیمی (بدون این ستون) از طریق Gson بدون NPE کار کند.
    val unit: String? = "UNIT_1",
    val assistantIds: String = "", // Comma-separated StaffMember.syncId of assistant(s) assigned to this appointment (syncId, not local id, so this stays valid across devices)
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "vendors")
data class Vendor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String,
    val phone: String,
    val itemType: String, // e.g., "Instruments", "Consumables"
    val totalOrders: Long = 0L,
    val totalPaid: Long = 0L,
    val invoiceNumber: String? = null,
    val invoiceDetails: String? = null,
    @androidx.room.ColumnInfo(name = "invoicePhotoBase64") val invoicePhotoFileId: String? = null,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "lab_orders")
data class LabOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labName: String,
    val patientName: String,
    val toothNumber: String,
    val prosthesisType: String, // Crown, Bridge, Denture
    val cost: Long,
    val orderDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val status: String, // ORDERED, RECEIVED, FITTED
    val shadeBrand: String? = null,
    val shadeColor: String? = null,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "equipment_maintenance")
data class EquipmentMaintenance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentName: String,
    val description: String,
    val cost: Long,
    val date: Long = System.currentTimeMillis(),
    val nextServiceDate: Long? = null,
    val technicianName: String? = null,
    val invoiceNumber: String? = null,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "clinic_expenses")
data class ClinicExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Rent, Consumables, Utilities, Salaries
    val amount: Long,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(name = "invoicePhotoBase64") val invoicePhotoFileId: String? = null,
    val paymentMethod: String = "CARD_TRANSFER",
    val subAccountName: String = "",
    val chequeNumber: String? = null,
    val labOrderId: Long? = null,
    val labOrderSyncId: String = "",
    val vendorId: Long? = null,
    val vendorSyncId: String = "",
    val maintenanceId: Long? = null,
    val maintenanceSyncId: String = "",
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "expense_payments",
    indices = [
        Index(value = ["clinicExpenseId"]),
        Index(value = ["date"])
    ]
)
data class ExpensePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clinicExpenseId: Long,
    val clinicExpenseSyncId: String = "",
    val amount: Long,
    val method: String = "CARD_TRANSFER", // CARD_TRANSFER, CASH, CHEQUE, POS
    val bankAccountId: Long? = null,
    val bankAccountSyncId: String = "",
    val sourceSmsNativeId: String = "",
    val chequeNumber: String? = null,
    val chequeDueDate: Long? = null,
    val chequeBank: String? = null,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "personal_transactions")
data class PersonalTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME (non-clinic), DRAW (from clinic), EXPENSE (personal expense)
    val subAccountName: String = "سایر", // Sub-account name (Customizable)
    val bankAccountId: Long? = null, // Linked bank card/account
    val bankAccountSyncId: String = "",
    val sourceSmsNativeId: String = "",
    val amount: Long,
    val description: String = "",
    val paymentMethod: String = "CARD_TRANSFER", // CARD_TRANSFER, CASH, CHEQUE_TRANSFER, POS
    val chequeNumber: String? = null,
    val date: Long = System.currentTimeMillis(),
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "lab_services")
data class LabService(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labName: String,
    val serviceName: String,
    val price: Long,
    val notes: String = "",
    val frameDeliveryDays: Int = 3,
    val finalDeliveryDays: Int = 7,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "installments",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["dueDate"])
    ]
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val treatmentId: Long? = null,
    val treatmentSyncId: String = "",
    val title: String = "قسط درمان",
    val amount: Long,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val note: String = "",
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "treatment_templates")
data class TreatmentTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, // نام انگلیسی تراپی (English therapy name)
    val titleFa: String = "", // عنوان — نام فارسی تراپی (Persian therapy name)
    val defaultCost: Long,
    val defaultNotes: String = "",
    val stages: String = "",
    val isStarred: Boolean = true,
    val sortOrder: Int = 0,
    val category: String = "", // عصب‌کشی, روکش, کشیدن, ترمیمی, سایر موارد
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "prescriptions",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["treatmentId"])
    ]
)
data class Prescription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val treatmentId: Long,
    val treatmentSyncId: String = "",
    val treatmentTitle: String = "", // عنوان تراپی/درمان در لحظه ثبت
    val stageName: String = "", // نام جلسه/مرحله تکمیل‌شده
    val analgesic: String = "", // مسکن (دوز)
    val antibiotic: String = "", // آنتی‌بیوتیک (دوز)
    val mouthwash: String = "", // دهان‌شویه
    val otherMeds: String = "", // سایر داروها
    val notes: String = "", // نکات مهم، موارد منع مصرف و تداخلات
    val publicNote: String = "", // یادداشت برای بیمار در نسخه چاپی/ارسال
    val durationDays: Int = 5, // طول درمان بر حسب روز
    val doctorApproved: Boolean = false,
    val date: Long = System.currentTimeMillis(),
    val sentSms: Boolean = false,
    val sentWhatsApp: Boolean = false,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val cardHolder: String = "",
    val cardNumber: String = "", // e.g. "6037-xxxx-xxxx-xxxx"
    val accountNumber: String = "",
    val balance: Long = 0L,
    val smsKeywords: String = "", // Comma-separated list like "واریز,بانک ملی"
    val cvv2: String = "",
    val expireMonth: String = "",
    val expireYear: String = "",
    val iban: String = "", // Shaba IR...
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "personal_sub_accounts")
data class PersonalSubAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "EXPENSE", // INCOME, EXPENSE, DRAW, ASSET, DEBT, CAPITAL, PERSONS
    val parentName: String = "", // سرفصل والد (ساختار درختی پرمیس؛ خالی = بدون گروه/ریشه)
    val isQuickSelect: Boolean = false,
    val ledgerSide: String = "NONE", // DEBTOR (بدهکار), CREDITOR (بستانکار), NONE
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "unregistered_bank_sms",
    indices = [
        Index(value = ["smsNativeId"], unique = true),
        Index(value = ["accountNumberRaw"]),
        Index(value = ["status"])
    ]
)
data class UnregisteredBankSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsNativeId: String, // Unique key for idempotency
    val sender: String = "",
    val content: String,
    val bankNameGuess: String,
    val type: String, // "DEPOSIT" (واریز) or "WITHDRAWAL" (برداشت)
    val amountTomans: Long,
    val accountNumberRaw: String, // Cleaned raw account number
    val dateStr: String,
    val timeStr: String,
    val score: Int = 5, // Score 0 to 5
    val status: String = "PENDING", // PENDING, ASSIGNED, IGNORED
    val assignedAccountId: Long? = null,
    val assignedAccountSyncId: String = "", // syncId حساب بانکی (برای سازگاری بین دستگاه‌ها)
    val registeredForSummary: String = "", // خلاصه «بابت» پس از ثبت (نمایش تک‌سطری ثبت‌شده‌ها)
    val createdAt: Long = System.currentTimeMillis(),
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "fixed_incomes")
data class FixedIncome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int = 1, // 1 to 31
    val bankAccountId: Long? = null,
    val bankAccountSyncId: String = "",
    val sourceSmsNativeId: String = "",
    val bankAccountName: String = "",
    val subAccountName: String = "درآمد ثابت",
    val lastProcessedMonth: String = "", // e.g. "1405/05"
    val isActive: Boolean = true,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "children_expenses")
data class ChildrenExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childName: String,
    val title: String,
    val amount: Long,
    val date: Long = System.currentTimeMillis(),
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "clinic_settings")
data class ClinicSettings(
    @PrimaryKey val id: Long = 1,
    val clinicName: String = "",
    val clinicPhone: String = "",
    val clinicAddress: String = "",
    val doctorName: String = "دکتر",
    val doctorLicenseNo: String = "",
    val childrenNames: String = "",
    val staffNames: String = "منشی",
    val showSummaryCard: Boolean = true,
    val showQuickActions: Boolean = true,
    val showCharts: Boolean = true,
    val smsSampleTemplate: String = "واریز مبلغ {amount} ریال به کارت {card}",
    val themeName: String = "NEON_CARBON",
    val isDarkMode: Boolean = true,
    val clinicalFontSizePt: Int = 11,
    val globalFontScaleIndex: Int = 9,
    val appointmentTemplate: String = "{clinic}\nیادآوری نوبت\nمراجع عزیز: {patient}\nتاریخ: {date} ساعت {time}",
    val invoiceTemplate: String = "{clinic}\nصورتحساب\nمراجع عزیز: {patient}\nقابل پرداخت: {amount}\nپرداختی: {paid}",
    val treatmentPlanTemplate: String = "{clinic}\nطرح درمان\nمراجع عزیز: {patient}\nموارد: {treatments}",
    val installmentTemplate: String = "{clinic}\nسررسید اقساط\nمراجع عزیز: {patient}\nمبلغ قسط: {amount}\nسررسید: {date}",
    val overdueInstallmentTemplate: String = "{clinic}\nاخطار تاخیر در پرداخت قسط\nمراجع عزیز: {patient}\nمبلغ قسط: {amount}\nسررسید: {date}\nتعداد روز تاخیر: {delay_days} روز\nلطفاً در اسرع وقت نسبت به واریز اقدام فرمایید.",
    val postOpTemplate: String = "{clinic}\nتوصیه های ترخیص و مراقبتی\nمراجع عزیز: {patient}\n{instructions}",
    val prescriptionTemplate: String = "{clinic}\nنسخه دارویی\nمراجع عزیز: {patient}\nدرمان: {treatment}\n{prescription}\n\nاین نسخه توسط پزشک تایید شده است.",
    val endoAnatomyOverrides: String = "",
    val workingDays: String = "0,1,2,3,4,5",
    val logoFileId: String? = null,
    val assistantPermissionsMask: Int = -1,
    val onboardingCompleted: Boolean = false,
    // ————————————————————————————————————————————————————————————
    // تنظیمات جدید (نسخه ۵ دیتابیس)
    // ————————————————————————————————————————————————————————————
    // پرسنل فعال: «یک پزشک فعال» و «یک دستیار فعال» — syncId از جدول staff_members
    // (منبع واحد نام‌ها؛ doctorName/staffNames قدیمی فقط برای سازگاریِ نمایش نگه داشته شده‌اند)
    val activeDoctorSyncId: String = "",
    val activeAssistantSyncId: String = "",
    // چیدمان کارت‌های داشبورد (کلیدهای جدا شده با کاما): HERO,VIEW_SWITCH,SUMMARY,CHARTS,QUICK_ACTIONS,CALENDAR
    val dashboardCardOrder: String = "",
    // نمایش کارت‌های تنظیمات: JSON به شکل {"cardKey":"NORMAL|HIDDEN"} — کارت‌های HIDDEN فقط
    // از منوی مخفی (لمس ۳ ثانیه‌ای چرخ‌دنده) قابل مشاهده‌اند
    val settingsCardVisibility: String = "",
    // لینک لوکیشن مطب (بلد/گوگل‌مپ) برای کارت هویت مطب
    val clinicMapLink: String = "",
    // آواتار حساب کاربری — مجزا از لوگوی مطب (لوگو برای بنر داشبورد و کارت هویت است)
    val avatarFileId: String? = null,
    // Synced copy of the local role-PIN hash/salt (see DentalViewModel.configureRolePin) so a
    // second device — or a fresh install signed in to the same clinic account — can unlock
    // without the doctor re-entering a PIN that used to live only in local SharedPreferences.
    val doctorPinHash: String? = null,
    val doctorPinSalt: String? = null,
    val assistantPinHash: String? = null,
    val assistantPinSalt: String? = null,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "app_calendar_events")
data class AppCalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // e.g. "ملی باستانی ایرانی", "ملی معاصر ایرانی", "مذهبی قمری (فقط تعطیلات)", "جهانی", "شخصی"
    val shamsiDay: Int? = null,
    val shamsiMonth: Int? = null,
    val shamsiYear: Int? = null, // for personal occasions
    val qamariDay: Int? = null,
    val qamariMonth: Int? = null,
    val miladiDay: Int? = null,
    val miladiMonth: Int? = null,
    val title: String, // مناسبت
    val isHolidayInIran: Boolean, // تعطیل رسمی در ایران (true/false)
    val description: String = "", // توضیحات
    val personalType: String = "", // مناسبت شخصی: "تولد", "فوت", "طلاق", "ازدواج", "سایر"
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "treatment_stages",
    indices = [
        Index(value = ["treatmentId"]),
        Index(value = ["stageIndex"])
    ]
)
data class TreatmentStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treatmentId: Long,
    val treatmentSyncId: String = "",
    val stageIndex: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val isIgnored: Boolean = false,
    val completedDate: Long? = null,
    val notes: String = "",
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "patient_documents",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["docType"])
    ]
)
data class PatientDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val patientSyncId: String = "",
    val uri: String? = null, // Local-disk cache path ONLY, never synced (see EntityMapper). Not the source of truth — a doc isn't final/shareable until fileId is set.
    val fileId: String? = null, // Supabase Storage object path — the ONLY source of truth once upload completes
    val localCachePath: String? = null,
    val docType: String = "RADIOGRAPH", // RADIOGRAPH, CONSENT, PHOTO, LAB, BEFORE_TREATMENT, AFTER_TREATMENT, OTHER
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "vendor_invoices",
    indices = [
        Index(value = ["vendorId"]),
        Index(value = ["date"])
    ]
)
data class VendorInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val vendorSyncId: String = "",
    val invoiceNumber: String = "",
    val amount: Long,
    val date: Long = System.currentTimeMillis(),
    val details: String = "",
    @androidx.room.ColumnInfo(name = "photoBase64") val documentFileId: String? = null,
    val isPaid: Boolean = true,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "financial_ledger",
    indices = [
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["bankAccountId"]),
        Index(value = ["status"])
    ]
)
data class FinancialLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: Long,
    val patientId: Long? = null,
    val patientSyncId: String = "",
    val bankAccountId: Long? = null,
    val bankAccountSyncId: String = "",
    val sourceSmsNativeId: String = "",
    val amountDelta: Long,
    val status: String = "POSTED", // POSTED, PENDING, BOUNCED, REVERSED
    val occurredAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "audit_events",
    indices = [Index(value = ["occurredAt"]), Index(value = ["action"])]
)
data class AuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorRole: String,
    val action: String,
    val entityType: String = "",
    val entityId: Long? = null,
    val result: String = "ALLOWED", // ALLOWED, DENIED, FAILED
    val details: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "drug_templates")
data class DrugTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String = "OTHER", // ANALGESIC, ANTIBIOTIC, MOUTHWASH, OTHER
    val title: String, // e.g. "Amoxicillin 500mg"
    val dosage: String,
    val timing: String,
    val instructions: String,
    val defaultDurationDays: Int = 5,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "staff_members")
data class StaffMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val role: String = "ASSISTANT", // DOCTOR, ASSISTANT
    val phone: String = "",
    val photoFileId: String? = null,
    val ownerId: String = "", // auth.uid سوپابیس مالکِ این رکورد — برای ایزوله‌سازی کامل داده‌های هر مطب/حساب از حساب‌های دیگر
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "pending_sync_ops")
data class PendingSyncOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionTable: String,
    val recordSyncId: String,
    val operation: String, // UPSERT, DELETE
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastError: String? = null
)
