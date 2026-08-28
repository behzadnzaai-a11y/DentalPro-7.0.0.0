package com.example.reports

import android.content.Context
import android.graphics.Color
import com.example.data.*
import java.io.File

/**
 * Every function here returns a ready-to-share/print PDF [File]. They are
 * intentionally plain functions (no ViewModel/Compose dependency) so they
 * can be called from a background coroutine and unit-tested independently.
 * Screens are responsible for gathering the data lists and formatters
 * (date/amount) and passing them in.
 */
object ReportGenerators {

    // #20 — Single-treatment invoice / receipt
    fun treatmentInvoice(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        patientName: String, treatment: Treatment, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val net = treatment.cost - treatment.discount
        return PdfReportBuilder(clinicName, doctorName, "فاکتور / رسید درمان", "بیمار: $patientName", accent)
            .withTable(
                listOf(ReportColumn("شرح خدمت", 3f), ReportColumn("تاریخ", 1.3f), ReportColumn("مبلغ", 1.3f)),
                listOf(listOf(treatment.title, dateFmt(treatment.date), "${amountFmt(treatment.cost)} $currency"))
            )
            .apply { if (treatment.discount > 0) withSummaryLine("تخفیف", "${amountFmt(treatment.discount)} $currency") }
            .withSummaryLine("مبلغ قابل پرداخت", "${amountFmt(net)} $currency", emphasize = true)
            .withSignatureLines()
            .build(context, "invoice")
    }

    // #21 — Full patient statement
    fun patientStatement(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        patientName: String, treatments: List<Treatment>, payments: List<Payment>,
        dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val totalCost = treatments.sumOf { it.cost - it.discount }
        val totalPaid = payments.sumOf { it.amount }
        val balance = totalCost - totalPaid
        val rows = mutableListOf<List<String>>()
        treatments.sortedBy { it.date }.forEach {
            rows.add(listOf("درمان: ${it.title}", dateFmt(it.date), "${amountFmt(it.cost - it.discount)} $currency"))
        }
        payments.sortedBy { it.date }.forEach {
            rows.add(listOf("پرداخت (${paymentTypeLabel(it.type)})", dateFmt(it.date), "-${amountFmt(it.amount)} $currency"))
        }
        return PdfReportBuilder(clinicName, doctorName, "صورت‌حساب کامل بیمار", "بیمار: $patientName", accent)
            .withTable(listOf(ReportColumn("شرح", 3f), ReportColumn("تاریخ", 1.3f), ReportColumn("مبلغ", 1.3f)), rows)
            .withSummaryLine("جمع هزینه درمان‌ها", "${amountFmt(totalCost)} $currency")
            .withSummaryLine("جمع پرداختی‌ها", "${amountFmt(totalPaid)} $currency")
            .withSummaryLine(if (balance > 0) "مانده بدهی" else if (balance < 0) "مانده بستانکاری" else "تسویه", "${amountFmt(kotlin.math.abs(balance))} $currency", emphasize = true)
            .withSignatureLines()
            .build(context, "statement")
    }

    // #22 — Treatment plan proposal with pricing
    fun treatmentPlan(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        patientName: String, plannedTreatments: List<Treatment>, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val total = plannedTreatments.sumOf { it.cost - it.discount }
        return PdfReportBuilder(
            clinicName, doctorName, "برنامه و طرح درمان پیشنهادی", "بیمار: $patientName", accent
        )
            .withParagraph("موارد زیر بر اساس معاینه بالینی پیشنهاد می‌گردد. قیمت‌ها تخمینی بوده و ممکن است بر اساس روند درمان تغییر کند.")
            .withTable(
                listOf(ReportColumn("شرح درمان پیشنهادی", 3f), ReportColumn("دندان(ها)", 1.3f), ReportColumn("هزینه تخمینی", 1.3f)),
                plannedTreatments.map { listOf(it.title, it.selectedTeeth.ifBlank { "-" }, "${amountFmt(it.cost - it.discount)} $currency") }
            )
            .withSummaryLine("جمع کل هزینه تخمینی طرح درمان", "${amountFmt(total)} $currency", emphasize = true)
            .withSignatureLines()
            .build(context, "treatment_plan")
    }

    // #23 — Printable appointment card/slip
    fun appointmentCard(
        context: Context, clinicName: String, doctorName: String, accent: Int,
        patientName: String, appointment: Appointment, dateFmt: (Long) -> String
    ): File {
        return PdfReportBuilder(clinicName, doctorName, "کارت نوبت ملاقات", null, accent)
            .withSummaryLine("بیمار", patientName, emphasize = true)
            .withSummaryLine("تاریخ و ساعت نوبت", dateFmt(appointment.date), emphasize = true)
            .withSummaryLine("نوع درمان", appointment.treatmentType.ifBlank { "-" })
            .apply { if (appointment.note.isNotBlank()) withSummaryLine("توضیحات", appointment.note) }
            .build(context, "appointment_card")
    }

    // #24 — Simplified printable tooth chart (tabular; not the graphical mouth diagram)
    fun toothChartReport(
        context: Context, clinicName: String, doctorName: String, accent: Int,
        patientName: String, treatments: List<Treatment>, dateFmt: (Long) -> String
    ): File {
        val rows = treatments
            .filter { it.selectedTeeth.isNotBlank() }
            .sortedBy { it.date }
            .map { listOf(it.selectedTeeth, it.title, statusLabel(it.status), dateFmt(it.date)) }
        return PdfReportBuilder(clinicName, doctorName, "نمودار وضعیت دندان‌ها", "بیمار: $patientName", accent)
            .withParagraph("فهرست زیر خلاصه‌ی متنی درمان‌های ثبت‌شده به تفکیک شماره دندان است.")
            .withTable(
                listOf(ReportColumn("شماره دندان", 1.2f), ReportColumn("درمان", 2.4f), ReportColumn("وضعیت", 1f), ReportColumn("تاریخ", 1.3f)),
                rows
            )
            .build(context, "tooth_chart")
    }

    // #25 — Period income report
    fun periodIncomeReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, payments: List<Payment>, dateFmt: (Long) -> String, amountFmt: (Long) -> String,
        patientNameOf: (Long) -> String
    ): File {
        val total = payments.sumOf { it.amount }
        return PdfReportBuilder(clinicName, doctorName, "گزارش درآمد دوره‌ای", periodLabel, accent)
            .withTable(
                listOf(ReportColumn("بیمار", 2f), ReportColumn("نوع پرداخت", 1.2f), ReportColumn("تاریخ", 1.3f), ReportColumn("مبلغ", 1.3f)),
                payments.sortedBy { it.date }.map {
                    listOf(patientNameOf(it.patientId), paymentTypeLabel(it.type), dateFmt(it.date), "${amountFmt(it.amount)} $currency")
                }
            )
            .withSummaryLine("جمع کل درآمد دوره", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "income_report")
    }

    // #26 — Outstanding debtors report
    fun outstandingDebtorsReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        debtors: List<Triple<String, String, Long>>, // name, phone, balance
        amountFmt: (Long) -> String
    ): File {
        val total = debtors.sumOf { it.third }
        return PdfReportBuilder(clinicName, doctorName, "گزارش مطالبات معوق بیماران", "مرتب‌شده بر اساس بیشترین بدهی", accent)
            .withTable(
                listOf(ReportColumn("نام بیمار", 2.2f), ReportColumn("تلفن", 1.3f), ReportColumn("مانده بدهی", 1.3f)),
                debtors.sortedByDescending { it.third }.map { listOf(it.first, it.second.ifBlank { "-" }, "${amountFmt(it.third)} $currency") }
            )
            .withSummaryLine("جمع کل مطالبات معوق", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "debtors_report")
    }

    // #27 — Cheques report (payments where type == CHEQUE)
    fun chequesReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        cheques: List<Payment>, dateFmt: (Long) -> String, amountFmt: (Long) -> String, patientNameOf: (Long) -> String
    ): File {
        val total = cheques.sumOf { it.amount }
        return PdfReportBuilder(clinicName, doctorName, "گزارش چک‌ها", "وضعیت: در جریان / پاس‌شده / برگشتی", accent)
            .withTable(
                listOf(ReportColumn("بیمار", 1.8f), ReportColumn("شماره چک", 1.2f), ReportColumn("سررسید", 1.2f), ReportColumn("وضعیت", 1f), ReportColumn("مبلغ", 1.3f)),
                cheques.sortedBy { it.chequeDueDate ?: it.date }.map {
                    listOf(
                        patientNameOf(it.patientId), it.chequeNumber ?: "-",
                        it.chequeDueDate?.let { d -> dateFmt(d) } ?: "-",
                        chequeStatusLabel(it.chequeStatus), "${amountFmt(it.amount)} $currency"
                    )
                }
            )
            .withSummaryLine("جمع کل مبلغ چک‌ها", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "cheques_report")
    }

    // #28 — Profit & loss report
    fun profitLossReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, totalIncome: Long, clinicExpenses: List<ClinicExpense>, maintenanceCosts: Long,
        amountFmt: (Long) -> String
    ): File {
        val expenseByCategory = clinicExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        val totalExpense = expenseByCategory.values.sum() + maintenanceCosts
        val net = totalIncome - totalExpense
        return PdfReportBuilder(clinicName, doctorName, "گزارش سود و زیان مطب", periodLabel, accent)
            .withTable(
                listOf(ReportColumn("دسته هزینه", 2.5f), ReportColumn("مبلغ", 1.5f)),
                expenseByCategory.map { (cat, amount) -> listOf(cat, "${amountFmt(amount)} $currency") } +
                    listOf(listOf("تعمیر و نگهداری تجهیزات", "${amountFmt(maintenanceCosts)} $currency"))
            )
            .withSummaryLine("جمع کل درآمد", "${amountFmt(totalIncome)} $currency")
            .withSummaryLine("جمع کل هزینه‌ها", "${amountFmt(totalExpense)} $currency")
            .withSummaryLine(if (net >= 0) "سود خالص دوره" else "زیان خالص دوره", "${amountFmt(kotlin.math.abs(net))} $currency", emphasize = true)
            .build(context, "profit_loss")
    }

    // #29 — Annual tax summary
    fun taxSummaryReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        year: String, monthlyIncome: List<Pair<String, Long>>, totalExpense: Long, amountFmt: (Long) -> String
    ): File {
        val totalIncome = monthlyIncome.sumOf { it.second }
        return PdfReportBuilder(clinicName, doctorName, "خلاصه مالیاتی سالانه", "سال $year — جهت ارائه به حسابدار", accent)
            .withTable(
                listOf(ReportColumn("ماه", 2f), ReportColumn("درآمد ثبت‌شده", 2f)),
                monthlyIncome.map { (month, amount) -> listOf(month, "${amountFmt(amount)} $currency") }
            )
            .withSummaryLine("جمع کل درآمد سالانه", "${amountFmt(totalIncome)} $currency")
            .withSummaryLine("جمع کل هزینه‌های قابل‌کسر", "${amountFmt(totalExpense)} $currency")
            .withSummaryLine("درآمد مشمول تخمینی", "${amountFmt(totalIncome - totalExpense)} $currency", emphasize = true)
            .build(context, "tax_summary")
    }

    // #30 — Bank accounts report
    fun bankAccountsReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        accounts: List<BankAccount>, amountFmt: (Long) -> String
    ): File {
        val total = accounts.sumOf { it.balance }
        return PdfReportBuilder(clinicName, doctorName, "گزارش حساب‌های بانکی", null, accent)
            .withTable(
                listOf(ReportColumn("بانک", 1.6f), ReportColumn("صاحب کارت", 1.6f), ReportColumn("شماره کارت", 1.6f), ReportColumn("موجودی", 1.3f)),
                accounts.map { listOf(it.bankName, it.cardHolder.ifBlank { "-" }, maskCard(it.cardNumber), "${amountFmt(it.balance)} $currency") }
            )
            .withSummaryLine("جمع کل موجودی حساب‌ها", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "bank_accounts")
    }

    // #31 — Personal draw vs clinic income
    fun personalDrawReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, clinicIncome: Long, draws: List<PersonalTransaction>, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val totalDraw = draws.sumOf { it.amount }
        return PdfReportBuilder(clinicName, doctorName, "گزارش برداشت شخصی از مطب", periodLabel, accent)
            .withTable(
                listOf(ReportColumn("شرح برداشت", 2.2f), ReportColumn("تاریخ", 1.3f), ReportColumn("مبلغ", 1.3f)),
                draws.sortedBy { it.date }.map { listOf(it.description.ifBlank { it.subAccountName }, dateFmt(it.date), "${amountFmt(it.amount)} $currency") }
            )
            .withSummaryLine("جمع کل درآمد کلینیک در دوره", "${amountFmt(clinicIncome)} $currency")
            .withSummaryLine("جمع کل برداشت شخصی", "${amountFmt(totalDraw)} $currency", emphasize = true)
            .build(context, "personal_draw")
    }

    // #32 — Children / personal expenses report
    fun childrenExpenseReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, expenses: List<ChildrenExpense>, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val byChild = expenses.groupBy { it.childName }.mapValues { it.value.sumOf { e -> e.amount } }
        val total = expenses.sumOf { it.amount }
        return PdfReportBuilder(clinicName, doctorName, "گزارش هزینه‌های فرزندان", periodLabel, accent)
            .withTable(
                listOf(ReportColumn("فرزند", 1.5f), ReportColumn("شرح هزینه", 1.8f), ReportColumn("تاریخ", 1.2f), ReportColumn("مبلغ", 1.3f)),
                expenses.sortedBy { it.date }.map { listOf(it.childName, it.title, dateFmt(it.date), "${amountFmt(it.amount)} $currency") }
            )
            .apply { byChild.forEach { (child, amount) -> withSummaryLine("جمع هزینه $child", "${amountFmt(amount)} $currency") } }
            .withSummaryLine("جمع کل هزینه‌های فرزندان", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "children_expenses")
    }

    // #33 — Treatment performance report (most frequent services)
    fun treatmentPerformanceReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, treatments: List<Treatment>, amountFmt: (Long) -> String
    ): File {
        val grouped = treatments.groupBy { it.title }
        val rows = grouped.map { (title, list) ->
            val avg = if (list.isNotEmpty()) list.sumOf { it.cost - it.discount } / list.size else 0L
            listOf(title, list.size.toString(), "${amountFmt(avg)} $currency", "${amountFmt(list.sumOf { it.cost - it.discount })} $currency")
        }.sortedByDescending { it[1].toIntOrNull() ?: 0 }
        return PdfReportBuilder(clinicName, doctorName, "گزارش عملکرد درمانی", periodLabel, accent)
            .withTable(
                listOf(ReportColumn("نوع درمان", 2f), ReportColumn("تعداد", 1f), ReportColumn("میانگین هزینه", 1.3f), ReportColumn("جمع درآمد", 1.3f)),
                rows
            )
            .build(context, "treatment_performance")
    }

    // #34b — Single lab-order official invoice (used by OfficialLabInvoiceDialog's print/share button)
    fun labOrderInvoice(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        order: LabOrder, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val count = com.example.ui.screens.parseTeethCount(order.toothNumber)
        val unitPrice = if (count > 0) order.cost / count else order.cost
        return PdfReportBuilder(clinicName, doctorName, "فاکتور سفارش آزمایشگاه", "شماره فاکتور: LAB-${order.id + 1000}", accent)
            .withTable(
                listOf(
                    ReportColumn("بیمار", 1.4f),
                    ReportColumn("نوع خدمت", 1.4f),
                    ReportColumn("دندان", 1f),
                    ReportColumn("تعداد واحد", 0.9f),
                    ReportColumn("قیمت واحد", 1.1f),
                    ReportColumn("جمع کل فاکتور", 1.2f)
                ),
                listOf(listOf(
                    order.patientName,
                    order.prosthesisType,
                    order.toothNumber.ifBlank { "-" },
                    "$count واحد",
                    "${amountFmt(unitPrice)} $currency",
                    "${amountFmt(order.cost)} $currency"
                ))
            )
            .withSummaryLine("آزمایشگاه طرف قرارداد", order.labName)
            .withSummaryLine("مبلغ کل قابل پرداخت", "${amountFmt(order.cost)} $currency ($count واحد × ${amountFmt(unitPrice)} $currency)", emphasize = true)
            .withSignatureLines()
            .build(context, "lab_invoice")
    }

    // #34 — Lab orders report
    fun labOrdersReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        orders: List<LabOrder>, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val total = orders.sumOf { it.cost }
        return PdfReportBuilder(clinicName, doctorName, "گزارش سفارش‌های آزمایشگاهی", null, accent)
            .withTable(
                listOf(ReportColumn("آزمایشگاه", 1.6f), ReportColumn("بیمار", 1.6f), ReportColumn("نوع کار", 1.4f), ReportColumn("سررسید", 1.2f), ReportColumn("وضعیت", 1f), ReportColumn("هزینه", 1.3f)),
                orders.sortedBy { it.dueDate }.map {
                    listOf(it.labName, it.patientName, it.prosthesisType, dateFmt(it.dueDate), labOrderStatusLabel(it.status), "${amountFmt(it.cost)} $currency")
                }
            )
            .withSummaryLine("جمع کل هزینه سفارش‌ها", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "lab_orders")
    }

    // #35 — Equipment maintenance report
    fun maintenanceReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        records: List<EquipmentMaintenance>, dateFmt: (Long) -> String, amountFmt: (Long) -> String
    ): File {
        val total = records.sumOf { it.cost }
        return PdfReportBuilder(clinicName, doctorName, "گزارش تعمیر و نگهداری تجهیزات", null, accent)
            .withTable(
                listOf(ReportColumn("تجهیز", 1.6f), ReportColumn("شرح", 2f), ReportColumn("تاریخ", 1.2f), ReportColumn("سررسید بعدی", 1.2f), ReportColumn("هزینه", 1.3f)),
                records.sortedByDescending { it.date }.map {
                    listOf(it.equipmentName, it.description, dateFmt(it.date), it.nextServiceDate?.let { d -> dateFmt(d) } ?: "-", "${amountFmt(it.cost)} $currency")
                }
            )
            .withSummaryLine("جمع کل هزینه تعمیرات", "${amountFmt(total)} $currency", emphasize = true)
            .build(context, "maintenance_report")
    }

    // #36 — Vendors report
    fun vendorsReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        vendors: List<Vendor>, amountFmt: (Long) -> String
    ): File {
        val totalDebt = vendors.sumOf { it.totalOrders - it.totalPaid }
        return PdfReportBuilder(clinicName, doctorName, "گزارش تامین‌کنندگان", null, accent)
            .withTable(
                listOf(ReportColumn("نام", 1.6f), ReportColumn("شرکت", 1.6f), ReportColumn("نوع کالا", 1.4f), ReportColumn("بدهی باقی‌مانده", 1.4f)),
                vendors.map { listOf(it.name, it.company, it.itemType, "${amountFmt(it.totalOrders - it.totalPaid)} $currency") }
            )
            .withSummaryLine("جمع کل بدهی به تامین‌کنندگان", "${amountFmt(totalDebt)} $currency", emphasize = true)
            .build(context, "vendors_report")
    }

    // #37 — Comprehensive monthly clinic dashboard
    fun monthlyDashboardReport(
        context: Context, clinicName: String, doctorName: String, accent: Int, currency: String,
        periodLabel: String, totalIncome: Long, totalExpense: Long, outstandingDebt: Long,
        appointmentsCount: Int, newPatientsCount: Int, amountFmt: (Long) -> String
    ): File {
        val net = totalIncome - totalExpense
        return PdfReportBuilder(clinicName, doctorName, "گزارش جامع ماهانه مطب", periodLabel, accent)
            .withSummaryLine("جمع کل درآمد", "${amountFmt(totalIncome)} $currency")
            .withSummaryLine("جمع کل هزینه‌ها", "${amountFmt(totalExpense)} $currency")
            .withSummaryLine(if (net >= 0) "سود خالص" else "زیان خالص", "${amountFmt(kotlin.math.abs(net))} $currency", emphasize = true)
            .withSummaryLine("مطالبات معوق کل", "${amountFmt(outstandingDebt)} $currency")
            .withSummaryLine("تعداد نوبت‌های ثبت‌شده", appointmentsCount.toString())
            .withSummaryLine("بیماران جدید این دوره", newPatientsCount.toString())
            .build(context, "monthly_dashboard")
    }

    private fun paymentTypeLabel(type: String) = when (type) {
        "CASH" -> "نقدی"; "CHEQUE" -> "چک"; "CARD" -> "کارت به کارت"; else -> type
    }

    private fun chequeStatusLabel(status: String) = when (status) {
        "PENDING" -> "در جریان وصول"; "CLEARED" -> "پاس‌شده"; "BOUNCED" -> "برگشتی"; "TRANSFERRED" -> "خرج‌شده"; else -> status
    }

    private fun labOrderStatusLabel(status: String) = when (status) {
        "ORDERED" -> "سفارش داده‌شده"; "RECEIVED" -> "دریافت‌شده"; "FITTED" -> "نصب‌شده"; else -> status
    }

    private fun statusLabel(status: String) = when (status) {
        "PLANNED" -> "برنامه‌ریزی‌شده"; "IN_PROGRESS" -> "در حال انجام"; "COMPLETED" -> "تکمیل‌شده"; else -> status
    }

    private fun maskCard(cardNumber: String): String {
        val digits = cardNumber.filter { it.isDigit() }
        return if (digits.length >= 4) "****-${digits.takeLast(4)}" else cardNumber
    }
}
