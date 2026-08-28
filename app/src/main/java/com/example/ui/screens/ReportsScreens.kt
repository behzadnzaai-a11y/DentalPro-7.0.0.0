package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.reports.*
import com.example.ui.DentalViewModel
import com.example.ui.theme.LocalNeonPalette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReportEntry(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val generate: (Context2) -> File
)

/** Bundles everything a report generator might need, gathered once per screen composition. */
private class Context2(
    val androidContext: android.content.Context,
    val clinicName: String,
    val doctorName: String,
    val accent: Int,
    val currency: String,
    val dateFmt: (Long) -> String,
    val amountFmt: (Long) -> String,
    val vm: DentalViewModel
)

@Composable
fun ReportsCenterScreen(viewModel: DentalViewModel, isPersian: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val neonPalette = LocalNeonPalette.current
    val accent = neonPalette.primary.toArgb()
    val currency = viewModel.label("currency")

    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    val labOrders by viewModel.labOrders.collectAsStateWithLifecycle()
    val maintenanceList by viewModel.maintenanceList.collectAsStateWithLifecycle()
    val clinicExpenses by viewModel.clinicExpenses.collectAsStateWithLifecycle()
    val personalTransactions by viewModel.personalTransactions.collectAsStateWithLifecycle()
    val childrenExpenses by viewModel.childrenExpenses.collectAsStateWithLifecycle()
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val allInstallments by viewModel.allInstallments.collectAsStateWithLifecycle()

    var showInstallmentsDialog by remember { mutableStateOf(false) }

    fun patientNameOf(id: Long) = patients.firstOrNull { it.id == id }?.name ?: "—"
    fun patientPhoneOf(id: Long) = patients.firstOrNull { it.id == id }?.phone
    fun amountFmt(v: Long) = formatAmount(v)
    fun dateFmt(v: Long) = viewModel.formatDate(v)

    var generatedFile by remember { mutableStateOf<File?>(null) }
    var generatedTitle by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val ctx2 = Context2(context, settings.clinicName, settings.doctorName, accent, currency, ::dateFmt, ::amountFmt, viewModel)

    val reports = remember(patients, allTreatments, allPayments, vendors, labOrders, maintenanceList, clinicExpenses, personalTransactions, childrenExpenses, bankAccounts, appointments, allInstallments) {
        listOf(
            ReportEntry("installments", if (isPersian) "سررسید و تاخیر اقساط" else "Installments & Due/Delay Notices", if (isPersian) "لیست تمام اقساط سیو شده + ارسال سررسید و تاخیر به بیمار" else "Saved installments list with SMS/WhatsApp reminders", Icons.Default.Schedule) { c ->
                File(c.androidContext.cacheDir, "dummy")
            },
            ReportEntry("income", if (isPersian) "گزارش درآمد دوره‌ای" else "Period Income Report", if (isPersian) "همه پرداخت‌های ثبت‌شده" else "All recorded payments", Icons.Default.TrendingUp) { c ->
                ReportGenerators.periodIncomeReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", allPayments, c.dateFmt, c.amountFmt, ::patientNameOf)
            },
            ReportEntry("debtors", if (isPersian) "گزارش مطالبات معوق" else "Outstanding Debtors", if (isPersian) "بیماران بدهکار به ترتیب مبلغ" else "Debtor patients by amount", Icons.Default.MoneyOff) { c ->
                val debtors = patients.map { p ->
                    val cost = allTreatments.filter { it.patientId == p.id }.sumOf { it.cost - it.discount }
                    val paid = allPayments.filter { it.patientId == p.id }.sumOf { it.amount }
                    Triple(p.name, p.phone, cost - paid)
                }.filter { it.third > 0 }
                ReportGenerators.outstandingDebtorsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, debtors, c.amountFmt)
            },
            ReportEntry("cheques", if (isPersian) "گزارش چک‌ها" else "Cheques Report", if (isPersian) "در جریان، پاس‌شده و برگشتی" else "Pending, cleared & bounced", Icons.Default.ConfirmationNumber) { c ->
                ReportGenerators.chequesReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, allPayments.filter { it.type == "CHEQUE" }, c.dateFmt, c.amountFmt, ::patientNameOf)
            },
            ReportEntry("profit_loss", if (isPersian) "گزارش سود و زیان" else "Profit & Loss", if (isPersian) "درآمد در برابر هزینه‌های مطب" else "Clinic income vs expenses", Icons.Default.Assessment) { c ->
                val income = allPayments.sumOf { it.amount }
                ReportGenerators.profitLossReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, clinicExpenses, maintenanceList.sumOf { it.cost }, c.amountFmt)
            },
            ReportEntry("tax", if (isPersian) "خلاصه مالیاتی سالانه" else "Annual Tax Summary", if (isPersian) "جهت ارائه به حسابدار" else "For your accountant", Icons.Default.Gavel) { c ->
                val byMonth = allPayments.groupBy { c.dateFmt(it.date).take(7) }.mapValues { it.value.sumOf { p -> p.amount } }
                val totalExpense = clinicExpenses.sumOf { it.amount } + maintenanceList.sumOf { it.cost }
                ReportGenerators.taxSummaryReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "جاری" else "Current", byMonth.toList(), totalExpense, c.amountFmt)
            },
            ReportEntry("bank", if (isPersian) "گزارش حساب‌های بانکی" else "Bank Accounts", if (isPersian) "موجودی هر حساب" else "Balance per account", Icons.Default.AccountBalance) { c ->
                ReportGenerators.bankAccountsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, bankAccounts, c.amountFmt)
            },
            ReportEntry("draw", if (isPersian) "گزارش برداشت شخصی" else "Personal Draw Report", if (isPersian) "برداشت دکتر از درآمد مطب" else "Owner draws vs clinic income", Icons.Default.Savings) { c ->
                val income = allPayments.sumOf { it.amount }
                ReportGenerators.personalDrawReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, personalTransactions.filter { it.type == "DRAW" }, c.dateFmt, c.amountFmt)
            },
            ReportEntry("children", if (isPersian) "هزینه‌های فرزندان" else "Children Expenses", if (isPersian) "به تفکیک هر فرزند" else "Broken down per child", Icons.Default.ChildCare) { c ->
                ReportGenerators.childrenExpenseReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", childrenExpenses, c.dateFmt, c.amountFmt)
            },
            ReportEntry("performance", if (isPersian) "عملکرد درمانی" else "Treatment Performance", if (isPersian) "پرتکرارترین خدمات" else "Most frequent services", Icons.Default.BarChart) { c ->
                ReportGenerators.treatmentPerformanceReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", allTreatments, c.amountFmt)
            },
            ReportEntry("lab", if (isPersian) "سفارش‌های آزمایشگاهی" else "Lab Orders Report", if (isPersian) "وضعیت و سررسید سفارش‌ها" else "Status & due dates", Icons.Default.Science) { c ->
                ReportGenerators.labOrdersReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, labOrders, c.dateFmt, c.amountFmt)
            },
            ReportEntry("maintenance", if (isPersian) "تعمیر و نگهداری تجهیزات" else "Equipment Maintenance", if (isPersian) "تاریخچه هزینه هر دستگاه" else "Cost history per device", Icons.Default.Handyman) { c ->
                ReportGenerators.maintenanceReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, maintenanceList, c.dateFmt, c.amountFmt)
            },
            ReportEntry("vendors", if (isPersian) "گزارش تامین‌کنندگان" else "Vendors Report", if (isPersian) "بدهی به هر فروشنده" else "Outstanding balance per vendor", Icons.Default.LocalShipping) { c ->
                ReportGenerators.vendorsReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, vendors, c.amountFmt)
            },
            ReportEntry("dashboard", if (isPersian) "گزارش جامع ماهانه مطب" else "Monthly Dashboard Report", if (isPersian) "خلاصه یک‌صفحه‌ای کل مطب" else "One-page clinic summary", Icons.Default.Dashboard) { c ->
                val income = allPayments.sumOf { it.amount }
                val expense = clinicExpenses.sumOf { it.amount } + maintenanceList.sumOf { it.cost }
                val debt = patients.sumOf { p ->
                    val cost = allTreatments.filter { it.patientId == p.id }.sumOf { it.cost - it.discount }
                    val paid = allPayments.filter { it.patientId == p.id }.sumOf { it.amount }
                    maxOf(0L, cost - paid)
                }
                ReportGenerators.monthlyDashboardReport(c.androidContext, c.clinicName, c.doctorName, c.accent, c.currency, if (isPersian) "کل دوره" else "All time", income, expense, debt, appointments.size, patients.size, c.amountFmt)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = if (isPersian) "گزارش‌ها و اسناد" else "Reports & Documents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPersian) "تولید، چاپ و اشتراک‌گذاری اسناد PDF" else "Generate, print & share PDF documents",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(reports) { report ->
                ReportRow(
                    report = report,
                    isPersian = isPersian,
                    onGenerate = {
                        if (report.id == "installments") {
                            showInstallmentsDialog = true
                        } else if (!isGenerating) {
                            scope.launch {
                                isGenerating = true
                                try {
                                    val file = withContext(Dispatchers.IO) { report.generate(ctx2) }
                                    generatedFile = file
                                    generatedTitle = report.title
                                } catch (error: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        error.localizedMessage ?: if (isPersian) "خطا در تولید گزارش" else "Report generation failed",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    if (isGenerating) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(if (isPersian) "در حال پردازش" else "Processing") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(if (isPersian) "لطفاً منتظر بمانید…" else "Please wait…")
                }
            }
        )
    }

    val fileToActOn = generatedFile
    if (fileToActOn != null && fileToActOn.name != "dummy" && !isGenerating) {
        AlertDialog(
            onDismissRequest = { generatedFile = null },
            title = { Text(if (isPersian) "سند آماده شد" else "Document Ready") },
            text = {
                Text(
                    if (isPersian) "«$generatedTitle» ساخته شد. می‌خواهید چاپ کنید یا اشتراک بگذارید؟"
                    else "\"$generatedTitle\" is ready. Print it or share it?"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        printPdfFile(context, fileToActOn, generatedTitle)
                        generatedFile = null
                    }) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "چاپ" else "Print")
                    }
                    TextButton(onClick = {
                        if (!isGenerating) {
                            scope.launch {
                                isGenerating = true
                                try {
                                    val exportTheme = ExportThemeSpec.fromPalette(neonPalette)
                                    val imageFiles = withContext(Dispatchers.IO) {
                                        PdfRendererHelper.renderPdfToImageCards(
                                            context = context,
                                            pdfFile = fileToActOn,
                                            themeSpec = exportTheme
                                        )
                                    }
                                    shareImagesNativeChooser(
                                        context,
                                        imageFiles,
                                        if (isPersian) "اشتراک‌گذاری همه صفحات" else "Share all pages"
                                    )
                                    generatedFile = null
                                } catch (error: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        error.localizedMessage ?: if (isPersian) "خطا در تبدیل گزارش" else "Report conversion failed",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "واتساپ (تصویر کامل)" else "WhatsApp (Full Card)")
                    }
                    TextButton(onClick = {
                        sharePdfToWhatsApp(context, fileToActOn)
                        generatedFile = null
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "واتساپ (PDF)" else "WhatsApp (PDF)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { generatedFile = null }) {
                    Text(if (isPersian) "بستن" else "Close")
                }
            }
        )
    }

    if (showInstallmentsDialog) {
        val nowMs = System.currentTimeMillis()
        var searchQ by remember { mutableStateOf("") }
        val filteredInsts = remember(allInstallments, searchQ, patients) {
            allInstallments.filter { inst ->
                val pName = patients.find { it.id == inst.patientId }?.name ?: ""
                searchQ.isBlank() || pName.contains(searchQ, ignoreCase = true) || inst.title.contains(searchQ, ignoreCase = true)
            }.sortedBy { it.dueDate }
        }

        AlertDialog(
            onDismissRequest = { showInstallmentsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isPersian) "تمام اقساط سیو شده و اطلاع‌رسانی سررسید/تاخیر" else "Saved Installments & Due/Delay Notices",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQ,
                        onValueChange = { searchQ = it },
                        placeholder = { Text(if (isPersian) "جستجوی بیمار..." else "Search patient...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )

                    if (filteredInsts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (isPersian) "هیچ قسطی یافت نشد." else "No installments found.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredInsts) { inst ->
                                val pat = patients.find { it.id == inst.patientId }
                                val rawPatName = pat?.name ?: (if (isPersian) "بیمار ناشناس" else "Unknown Patient")
                                val patName = honorificPatientName(rawPatName, pat?.gender ?: "")
                                val patPhone = pat?.phone ?: ""
                                val amtStr = "${formatAmount(inst.amount)} $currency"
                                val dueDateStr = dateFmt(inst.dueDate)

                                val diffMs = nowMs - inst.dueDate
                                val delayDays = if (diffMs > 0 && !inst.isPaid) diffMs / (1000 * 60 * 60 * 24) else 0L
                                val isOverdue = delayDays > 0 && !inst.isPaid

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (inst.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(patName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(inst.title.ifBlank { if (isPersian) "قسط درمان" else "Installment" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                            }
                                            Surface(
                                                color = if (inst.isPaid) Color(0xFFE8F5E9)
                                                else if (isOverdue) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (inst.isPaid) (if (isPersian) "پرداخت شده" else "Paid")
                                                    else if (isOverdue) (if (isPersian) "تاخیر اقساط ($delayDays روز تاخیر)" else "Overdue ($delayDays d)")
                                                    else (if (isPersian) "سررسید قسط" else "Due Soon"),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (inst.isPaid) Color(0xFF2E7D32)
                                                    else if (isOverdue) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${if (isPersian) "مبلغ:" else "Amount:"} $amtStr | ${if (isPersian) "سررسید:" else "Due:"} $dueDateStr",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // Pattern SMS
                                                IconButton(
                                                    onClick = {
                                                        val templateToUse = if (isOverdue) settings.overdueInstallmentTemplate else settings.installmentTemplate
                                                        val msg = templateToUse
                                                            .replace("{clinic}", settings.clinicName)
                                                            .replace("{patient}", patName)
                                                            .replace("{amount}", amtStr)
                                                            .replace("{date}", dueDateStr)
                                                            .replace("{delay_days}", delayDays.toString())
                                                        sendSmsPrefilled(context, patPhone, msg)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }

                                                // Pattern WhatsApp
                                                IconButton(
                                                    onClick = {
                                                        val patObj = patients.firstOrNull { it.id == inst.patientId }
                                                        val patFatherName = patObj?.fatherName ?: ""
                                                        val patNatId = patObj?.nationalId ?: ""

                                                        val cardFile = if (isOverdue) {
                                                            com.example.reports.generateOverdueInstallmentNoticeCardImage(
                                                                context = context,
                                                                clinicName = settings.clinicName,
                                                                patientName = patName,
                                                                patientFatherName = patFatherName,
                                                                patientNationalId = patNatId,
                                                                installmentTitle = inst.title,
                                                                dueDateStr = dueDateStr,
                                                                delayDays = delayDays,
                                                                amountStr = amtStr,
                                                                themeSpec = com.example.reports.ExportThemeSpec.fromPalette(neonPalette)
                                                            )
                                                        } else {
                                                            com.example.reports.generateInstallmentDueReminderCardImage(
                                                                context = context,
                                                                clinicName = settings.clinicName,
                                                                patientName = patName,
                                                                patientFatherName = patFatherName,
                                                                patientNationalId = patNatId,
                                                                installmentTitle = inst.title,
                                                                dueDateStr = dueDateStr,
                                                                amountStr = amtStr,
                                                                themeSpec = com.example.reports.ExportThemeSpec.fromPalette(neonPalette)
                                                            )
                                                        }
                                                        shareImageToWhatsApp(context, cardFile, patPhone)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstallmentsDialog = false }) {
                    Text(if (isPersian) "بستن" else "Close")
                }
            }
        )
    }
}

@Composable
fun PatientSendActionsRow(
    patient: Patient,
    treatments: List<Treatment>,
    payments: List<Payment>,
    viewModel: DentalViewModel,
    isPersian: Boolean
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val neonPalette = LocalNeonPalette.current
    val accent = neonPalette.primary.toArgb()
    val bg = neonPalette.background.toArgb()
    val currency = viewModel.label("currency")

    var pendingAction by remember { mutableStateOf<String?>(null) } // "statement" or "plan"

    fun hasUsablePhone() = patient.phone.isNotBlank() && patient.phone != "ثبت نشده"

    fun sendStatementViaMessenger(messenger: String) {
        val totalCost = treatments.sumOf { it.cost - it.discount }
        val totalPaid = payments.sumOf { it.amount }
        val balance = totalCost - totalPaid
        
        if (messenger == "واتساپ") {
            val items = treatments.sortedByDescending { it.date }.take(12).map {
                SummaryLineItem(it.title, viewModel.formatDate(it.date), "${formatAmount(it.cost - it.discount)} $currency")
            }
            val footer = listOf(
                (if (isPersian) "جمع هزینه درمان‌ها" else "Total treatment cost") to "${formatAmount(totalCost)} $currency",
                (if (isPersian) "جمع پرداختی‌ها" else "Total paid") to "${formatAmount(totalPaid)} $currency",
                (if (balance > 0) (if (isPersian) "مانده بدهی" else "Balance due") else (if (isPersian) "مانده بستانکاری" else "Credit balance")) to "${formatAmount(kotlin.math.abs(balance))} $currency"
            )
            val file = buildSummaryImage(
                context = context,
                cardTitle = if (isPersian) "صورت‌حساب بیمار" else "Patient Statement",
                clinicName = settings.clinicName,
                doctorName = settings.doctorName,
                patientName = patient.name,
                items = items,
                footerLines = footer,
                themeSpec = ExportThemeSpec.fromPalette(neonPalette),
                fileNamePrefix = "statement",
                gender = patient.gender
            )
            shareImageToWhatsApp(context, file, patient.phone)
        } else {
            val text = buildStatementSmsText(
                settings.clinicName, patient.name, currency,
                formatAmount(totalCost), formatAmount(totalPaid), formatAmount(kotlin.math.abs(balance)), balance > 0,
                gender = patient.gender
            )
            com.example.ui.screens.openMessengerChat(context, patient.phone, text, messenger)
        }
    }

    fun sendTreatmentPlanViaMessenger(messenger: String) {
        val planned = treatments.filter { it.status == "PLANNED" }
        val totalPrice = planned.sumOf { it.cost - it.discount }
        if (messenger == "واتساپ") {
            val items = planned.map {
                SummaryLineItem(it.title, if (it.selectedTeeth.isNotBlank()) (if (isPersian) "دندان: ${it.selectedTeeth}" else "Tooth: ${it.selectedTeeth}") else "", "${formatAmount(it.cost - it.discount)} $currency")
            }
            val footer = listOf((if (isPersian) "هزینه کل تخمینی" else "Total estimated cost") to "${formatAmount(totalPrice)} $currency")
            val file = buildSummaryImage(
                context = context,
                cardTitle = if (isPersian) "طرح درمان پیشنهادی" else "Proposed Treatment Plan",
                clinicName = settings.clinicName,
                doctorName = settings.doctorName,
                patientName = patient.name,
                items = items,
                footerLines = footer,
                themeSpec = ExportThemeSpec.fromPalette(neonPalette),
                fileNamePrefix = "treatment_plan",
                gender = patient.gender
            )
            shareImageToWhatsApp(context, file, patient.phone)
        } else {
            val text = buildTreatmentPlanSmsText(settings.clinicName, patient.name, currency, planned.map { it.title }, formatAmount(totalPrice), gender = patient.gender)
            com.example.ui.screens.openMessengerChat(context, patient.phone, text, messenger)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { pendingAction = "statement" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isPersian) "ارسال صورت‌حساب" else "Send Statement", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = { pendingAction = "plan" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isPersian) "ارسال طرح درمان" else "Send Treatment Plan", fontSize = 12.sp)
        }
    }

    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (isPersian) "انتخاب کانال ارسال" else "Choose messenger channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isPersian) "پیام‌رسان پیش‌فرض بیمار: ${patient.preferredMessenger}"
                        else "Patient's preferred messenger: ${patient.preferredMessenger}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        if (isPersian) "کانال مورد نظر خود را جهت ارسال متنی یا تصویری انتخاب کنید:"
                        else "Select channel to send:",
                        fontSize = 11.sp
                    )

                    val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                    messengers.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { m ->
                                val isPref = m == patient.preferredMessenger
                                Button(
                                    onClick = {
                                        if (action == "statement") sendStatementViaMessenger(m) else sendTreatmentPlanViaMessenger(m)
                                        pendingAction = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (isPref) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPref) "⭐ $m (پیش‌فرض)" else m,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReportRow(report: ReportEntry, isPersian: Boolean, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onGenerate() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(report.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(report.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(report.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}
