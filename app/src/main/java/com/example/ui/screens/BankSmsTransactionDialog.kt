package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PersonalSubAccount
import com.example.ui.BankSmsAllocation
import com.example.ui.DentalViewModel
import com.example.ui.components.SimpleCalculatorDialog
import com.example.util.PersianNumberFormatter

/**
 * دادهٔ ورودی پاپ‌آپ ثبت تراکنش پیامک بانکی.
 * هم از لمس نوتیفیکیشن پیامک بانکی و هم از لمس هر آیتم صف «پیامک‌های ثبت‌نشده»
 * دقیقاً همین پاپ‌آپ باز می‌شود (queueId فقط برای آیتم‌های صف مقدار دارد).
 */
data class BankSmsTxnInfo(
    val bankName: String,
    val type: String, // "DEPOSIT" یا "WITHDRAWAL"
    val amountTomans: Long,
    val dateStr: String,
    val timeStr: String,
    val accountNumberRaw: String,
    val rawText: String,
    val score: Int = 5,
    val queueId: Long? = null,
    val smsNativeId: String? = null
)

/** یک سطر تخصیص در کادر پایین پاپ‌آپ (واریز بابت / برداشت بابت). */
private data class AllocRowUi(
    val id: Int,
    val kind: String = "", // PATIENT / MISC_INCOME / CLINIC_EXPENSE / PERSONAL_SUB / PERSON
    val targetId: Long? = null,
    val targetName: String = "",
    val amount: Long = 0L,
    val description: String = ""
)

@Composable
fun BankSmsTransactionDialog(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    info: BankSmsTxnInfo,
    onDismiss: () -> Unit
) {
    val isDeposit = info.type == "DEPOSIT"
    val total = info.amountTomans
    val currencyUnit by viewModel.currencyUnit.collectAsStateWithLifecycle()

    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val clinicExpenses by viewModel.clinicExpenses.collectAsStateWithLifecycle()
    val allExpensePayments by viewModel.allExpensePayments.collectAsStateWithLifecycle()
    val personalSubAccounts by viewModel.personalSubAccounts.collectAsStateWithLifecycle()
    val childrenExpenses by viewModel.childrenExpenses.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // حساب‌های واقعاً بانکی (صندوق/تنخواه نقدی در انتهای لیست انتخاب)
    val accountPool = remember(bankAccounts) {
        bankAccounts.filter { !it.isDeleted }.sortedBy {
            if (it.bankName.contains("صندوق") || it.bankName.contains("تنخواه")) 1 else 0
        }
    }

    // پیش‌فرض: حساب بانکی «همنام بانک» پیامک در حساب‌ها و کارت‌ها؛ سپس تطبیق شماره حساب/کارت
    val defaultAccount = remember(accountPool, info) {
        accountPool.firstOrNull { acc ->
            info.bankName.isNotBlank() && info.bankName != "بانک" &&
                (acc.bankName.contains(info.bankName) || info.bankName.contains(acc.bankName))
        } ?: accountPool.firstOrNull { acc ->
            val raw = info.accountNumberRaw
            val accNum = acc.accountNumber.filter { it.isDigit() }
            val cardNum = acc.cardNumber.filter { it.isDigit() }
            raw.isNotBlank() && (
                (accNum.isNotBlank() && (accNum.endsWith(raw) || raw.endsWith(accNum))) ||
                    (cardNum.isNotBlank() && cardNum.endsWith(raw))
                )
        } ?: accountPool.firstOrNull()
    }
    var selectedAccountId by remember(defaultAccount) { mutableStateOf(defaultAccount?.id) }

    // بیماران تسویه‌نشده: جمع هزینه درمان‌ها (منهای تخفیف) بیشتر از جمع پرداختی‌ها
    val unsettledPatients = remember(patients, allTreatments, allPayments) {
        patients.filter { !it.isDeleted }.mapNotNull { p ->
            val totalCost = allTreatments.filter { it.patientId == p.id && !it.isDeleted }
                .sumOf { (it.cost - it.discount).coerceAtLeast(0L) }
            val totalPaid = allPayments.filter { it.patientId == p.id && !it.isDeleted }
                .sumOf { it.amount }
            val remaining = totalCost - totalPaid
            if (remaining > 0) Triple(p.id, p.name, remaining) else null
        }
    }

    // طرف‌حساب‌های تسویه‌نشده در بخش هزینه‌ها: هزینه‌هایی که هنوز کامل پرداخت نشده‌اند
    val unsettledExpenses = remember(clinicExpenses, allExpensePayments) {
        clinicExpenses.filter { !it.isDeleted }.mapNotNull { exp ->
            val paid = allExpensePayments.filter { it.clinicExpenseId == exp.id && !it.isDeleted }
                .sumOf { it.amount }
            val remaining = exp.amount - paid
            if (remaining > 0) {
                val label = exp.description.ifBlank { exp.category }
                Triple(exp.id, label, remaining)
            } else null
        }
    }

    // لیست اشخاص در بخش فرزندان (از تنظیمات + سوابق هزینه‌های فرزندان)
    val personNames = remember(settings, childrenExpenses) {
        (settings.childrenNames.split(",", "،").map { it.trim() }.filter { it.isNotBlank() } +
            childrenExpenses.filter { !it.isDeleted }.map { it.childName.trim() }.filter { it.isNotBlank() })
            .distinct()
    }

    var nextRowId by remember { mutableStateOf(2) }
    var rows by remember(info) { mutableStateOf(listOf(AllocRowUi(id = 1, amount = total))) }
    var warningRowId by remember { mutableStateOf<Int?>(null) }
    var activeCalcRowId by remember { mutableStateOf<Int?>(null) }
    var personalPickerRowId by remember { mutableStateOf<Int?>(null) }
    var incomePickerRowId by remember { mutableStateOf<Int?>(null) }

    val rowsSum = rows.sumOf { it.amount }
    val remaining = total - rowsSum

    // تنظیم مبلغ یک سطر با کنترل لحظه‌ای تراز:
    //  - اگر مبلغ واردشده باعث شود جمع کادر پایین از کادر بالا بیشتر شود، مقدار رد و اخطار سطری نمایش داده می‌شود
    //  - اگر جمع کمتر از کل باشد، خودکار یک کادر جدید با پیش‌فرض مانده اضافه می‌شود
    fun applyRowAmount(rowId: Int, newAmount: Long) {
        val idx = rows.indexOfFirst { it.id == rowId }
        if (idx < 0) return
        val othersSum = rows.filter { it.id != rowId }.sumOf { it.amount }
        if (othersSum + newAmount > total) {
            warningRowId = rowId
            return
        }
        warningRowId = null
        val mut = rows.toMutableList()
        mut[idx] = mut[idx].copy(amount = newAmount)
        val sum = othersSum + newAmount
        if (sum < total) {
            // کادر جدید با همان مشخصات، پیش‌فرض = ماندهٔ مبلغ
            mut.add(AllocRowUi(id = nextRowId, amount = total - sum))
            nextRowId += 1
        } else {
            // تراز شد؛ سطرهای خالی باقی‌مانده حذف شوند
            val balanced = mut.filter { it.amount > 0 }
            mut.clear()
            mut.addAll(balanced)
        }
        rows = mut
    }

    fun removeRow(rowId: Int) {
        if (rows.size <= 1) return
        warningRowId = null
        var mut = rows.filter { it.id != rowId }.toMutableList()
        val sum = mut.sumOf { it.amount }
        if (sum < total) {
            mut.add(AllocRowUi(id = nextRowId, amount = total - sum))
            nextRowId += 1
        }
        rows = mut
    }

    // ماشین‌حساب سادهٔ مبلغ (لود با یک لمس روی فیلد مبلغ)
    if (activeCalcRowId != null) {
        val row = rows.firstOrNull { it.id == activeCalcRowId }
        SimpleCalculatorDialog(
            initialValue = row?.amount ?: 0L,
            isPersian = isPersian,
            title = if (isPersian) "ماشین‌حساب مبلغ سهم این ردیف" else "Row Amount Calculator",
            onConfirm = { calculated ->
                val id = activeCalcRowId
                if (id != null) applyRowAmount(id, calculated)
            },
            onDismiss = { activeCalcRowId = null }
        )
    }

    // پاپ‌آپ جدید انتخاب هزینه شخصی / اشخاص (با حفظ پاپ‌آپ اصلی)
    if (personalPickerRowId != null) {
        PersonalWithdrawTargetPickerDialog(
            isPersian = isPersian,
            subAccounts = personalSubAccounts.filter { !it.isDeleted && (it.category == "EXPENSE" || it.category == "DRAW") },
            persons = personNames,
            onSelect = { kind, name ->
                val rid = personalPickerRowId
                if (rid != null) {
                    rows = rows.map { r ->
                        if (r.id == rid) r.copy(kind = kind, targetId = null, targetName = name) else r
                    }
                }
                personalPickerRowId = null
            },
            onDismiss = { personalPickerRowId = null }
        )
    }

    // پاپ‌آپ انتخاب سرفصل درآمد شخصی (ساختار درختی پرمیس) — با حفظ پاپ‌آپ اصلی
    if (incomePickerRowId != null) {
        PersonalIncomePickerDialog(
            isPersian = isPersian,
            subAccounts = personalSubAccounts.filter { !it.isDeleted && it.category == "INCOME" },
            onSelect = { name ->
                val rid = incomePickerRowId
                if (rid != null) {
                    rows = rows.map { r ->
                        if (r.id == rid) r.copy(kind = "MISC_INCOME", targetId = null, targetName = name) else r
                    }
                }
                incomePickerRowId = null
            },
            onDismiss = { incomePickerRowId = null }
        )
    }

    val amountWords = remember(total) { PersianNumberFormatter.numberToPersianWords(total) }
    val typeWord = if (isDeposit) "واریز" else "برداشت"
    val accentColor = if (isDeposit) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val containerColor = if (isDeposit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = accentColor
                )
                Text(
                    text = if (isPersian) "ثبت $typeWord پیامک بانکی" else "Register Bank ${if (isDeposit) "Deposit" else "Withdrawal"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ================= کادر بالا =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🏦 ${info.bankName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor)
                        Text(
                            text = "مبلغ $typeWord: ${PersianNumberFormatter.formatAmount(total)} $currencyUnit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (amountWords.isNotBlank()) {
                            Text(
                                text = "به حروف: $amountWords $currencyUnit",
                                fontSize = 11.5.sp,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "📅 تاریخ $typeWord: ${info.dateStr}    🕒 ساعت $typeWord: ${info.timeStr}",
                            fontSize = 11.sp
                        )

                        HorizontalDivider(color = accentColor.copy(alpha = 0.2f))

                        Text(
                            text = if (isDeposit) "واریز به حساب:" else "برداشت از حساب:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        var accExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                val acc = accountPool.find { it.id == selectedAccountId }
                                Text(
                                    text = acc?.let {
                                        val numPart = it.accountNumber.ifBlank { it.cardNumber }
                                        if (numPart.isNotBlank()) "💳 ${it.bankName} — $numPart" else "💳 ${it.bankName}"
                                    } ?: (if (isPersian) "انتخاب حساب بانکی..." else "Select bank account..."),
                                    fontSize = 11.sp
                                )
                            }
                            DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                                accountPool.forEach { acc ->
                                    val numPart = acc.accountNumber.ifBlank { acc.cardNumber }
                                    DropdownMenuItem(
                                        text = { Text("💳 ${acc.bankName}${if (numPart.isNotBlank()) " — $numPart" else ""}", fontSize = 11.sp) },
                                        onClick = { selectedAccountId = acc.id; accExpanded = false }
                                    )
                                }
                            }
                        }
                        if (info.accountNumberRaw.isNotBlank()) {
                            Text(
                                text = "شماره حساب/کارت در پیامک: ${info.accountNumberRaw}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // ================= کادر(های) پایین =================
                Text(
                    text = if (isDeposit) "واریز بابت:" else "برداشت بابت:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )

                rows.forEach { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isDeposit) "واریز بابت" else "برداشت بابت",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (rows.size > 1) {
                                    IconButton(onClick = { removeRow(row.id) }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            // انتخاب مقصد سطر
                            var targetExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { targetExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = when {
                                            row.kind == "PATIENT" -> "👤 ${row.targetName}"
                                            row.kind == "MISC_INCOME" -> "💼 درآمد متفرقه (حساب شخصی)"
                                            row.kind == "CLINIC_EXPENSE" -> "🏭 ${row.targetName}"
                                            row.kind == "PERSONAL_SUB" -> "🏠 هزینه شخصی: ${row.targetName}"
                                            row.kind == "PERSON" -> "🧒 حساب اشخاص: ${row.targetName}"
                                            else -> if (isDeposit) "انتخاب بابت واریز..." else "انتخاب بابت برداشت..."
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                                DropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                                    if (isDeposit) {
                                        // لیست بیماران تسویه‌نشده (کشویی)
                                        if (unsettledPatients.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("بیمار تسویه‌نشده‌ای وجود ندارد", fontSize = 11.sp, color = Color.Gray) },
                                                onClick = { targetExpanded = false }
                                            )
                                        }
                                        unsettledPatients.forEach { (pid, pname, rem) ->
                                            DropdownMenuItem(
                                                text = { Text("👤 $pname — مانده بدهی: ${PersianNumberFormatter.formatAmount(rem)} $currencyUnit", fontSize = 11.sp) },
                                                onClick = {
                                                    rows = rows.map { r -> if (r.id == row.id) r.copy(kind = "PATIENT", targetId = pid, targetName = pname) else r }
                                                    targetExpanded = false
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        // در حساب‌های شخصی: درآمد متفرقه
                                        DropdownMenuItem(
                                            text = { Text("💼 درآمد متفرقه (حساب شخصی)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                rows = rows.map { r -> if (r.id == row.id) r.copy(kind = "MISC_INCOME", targetId = null, targetName = "درآمد متفرقه") else r }
                                                targetExpanded = false
                                            }
                                        )
                                        // انتخاب سرفصل درآمد شخصی از ساختار درختی (حقوق/فروش/سود سرمایه/...)
                                        DropdownMenuItem(
                                            text = { Text("📂 سرفصل درآمد شخصی (حقوق/فروش/...)...", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                targetExpanded = false
                                                incomePickerRowId = row.id
                                            }
                                        )
                                    } else {
                                        // لیست طرف‌حساب‌های تسویه‌نشده بخش هزینه‌ها (کشویی)
                                        if (unsettledExpenses.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("طرف‌حساب تسویه‌نشده‌ای در هزینه‌ها نیست", fontSize = 11.sp, color = Color.Gray) },
                                                onClick = { targetExpanded = false }
                                            )
                                        }
                                        unsettledExpenses.forEach { (eid, elabel, rem) ->
                                            DropdownMenuItem(
                                                text = { Text("🏭 $elabel — مانده: ${PersianNumberFormatter.formatAmount(rem)} $currencyUnit", fontSize = 11.sp) },
                                                onClick = {
                                                    rows = rows.map { r -> if (r.id == row.id) r.copy(kind = "CLINIC_EXPENSE", targetId = eid, targetName = elabel) else r }
                                                    targetExpanded = false
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        // در حساب‌های شخصی: انتخاب در پاپ‌آپ جدید (با حفظ پاپ‌آپ فعلی)
                                        DropdownMenuItem(
                                            text = { Text("🏠 هزینه شخصی / حساب اشخاص (فرزندان)...", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                targetExpanded = false
                                                personalPickerRowId = row.id
                                            }
                                        )
                                    }
                                }
                            }

                            // فیلد مبلغ — با یک لمس، ماشین‌حساب ساده لود می‌شود
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeCalcRowId = row.id }
                            ) {
                                OutlinedTextField(
                                    value = if (row.amount > 0) "${PersianNumberFormatter.formatAmount(row.amount)} $currencyUnit" else "",
                                    onValueChange = { },
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text(if (isPersian) "مبلغ (لمس = ماشین‌حساب)" else "Amount (tap = calculator)", fontSize = 10.5.sp) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            // اخطار سطری: جلوگیری از ثبت مبلغی که جمع را از کادر بالا بیشتر می‌کند
                            if (warningRowId == row.id) {
                                Text(
                                    text = "⛔ این مبلغ جمع کادرهای پایین را از مبلغ $typeWord بیشتر می‌کند و قابل ثبت نیست.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // فیلد توضیحات
                            OutlinedTextField(
                                value = row.description,
                                onValueChange = { newDesc ->
                                    rows = rows.map { r -> if (r.id == row.id) r.copy(description = newDesc) else r }
                                },
                                label = { Text(if (isPersian) "توضیحات" else "Description", fontSize = 10.5.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                // تراز لحظه‌ای جمع کادر پایین با کادر بالا
                Text(
                    text = "جمع کادرهای پایین: ${PersianNumberFormatter.formatAmount(rowsSum)} از ${PersianNumberFormatter.formatAmount(total)} $currencyUnit" +
                        if (remaining > 0) "  (مانده: ${PersianNumberFormatter.formatAmount(remaining)})" else "",
                    color = if (rowsSum == total) Color(0xFF2E7D32) else Color(0xFFC2410C),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )

                HorizontalDivider()

                // ================= دکمه‌های پایانی =================
                val allRowsValid = rows.all { it.kind.isNotBlank() && it.amount > 0 } &&
                    rows.filter { it.kind == "PATIENT" || it.kind == "CLINIC_EXPENSE" }.all { it.targetId != null }
                val canRegister = selectedAccountId != null && rowsSum == total && allRowsValid

                Button(
                    onClick = {
                        val accId = selectedAccountId ?: return@Button
                        val hash = (info.rawText + "|" + info.amountTomans + "|" + info.dateStr + "|" + info.timeStr).hashCode()
                        val nativeId = info.smsNativeId ?: "live_$hash"
                        val allocations = rows.map { r ->
                            BankSmsAllocation(
                                kind = r.kind,
                                targetId = r.targetId,
                                targetName = r.targetName,
                                amount = r.amount,
                                description = r.description.trim()
                            )
                        }
                        viewModel.registerBankSmsTransaction(
                            isDeposit = isDeposit,
                            bankAccountId = accId,
                            allocations = allocations,
                            smsNativeId = nativeId,
                            smsDedupHash = hash,
                            queueItemId = info.queueId,
                            smsRecordSeed = com.example.data.UnregisteredBankSms(
                                smsNativeId = nativeId,
                                content = info.rawText,
                                bankNameGuess = info.bankName,
                                type = info.type,
                                amountTomans = info.amountTomans,
                                accountNumberRaw = info.accountNumberRaw,
                                dateStr = info.dateStr,
                                timeStr = info.timeStr,
                                score = info.score
                            )
                        ) {
                            android.widget.Toast.makeText(
                                viewModel.appContext,
                                if (isPersian) "تراکنش $typeWord با موفقیت ثبت شد" else "Transaction registered",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        onDismiss()
                    },
                    enabled = canRegister,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPersian) "ثبت تراکنش" else "Register Transaction", fontWeight = FontWeight.Bold)
                }

                if (info.queueId == null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.enqueueBankSmsToUnregisteredQueue(
                                bankName = info.bankName,
                                type = info.type,
                                amountTomans = info.amountTomans,
                                accountNumberRaw = info.accountNumberRaw,
                                dateStr = info.dateStr,
                                timeStr = info.timeStr,
                                rawText = info.rawText,
                                score = info.score
                            )
                            android.widget.Toast.makeText(
                                viewModel.appContext,
                                if (isPersian) "به لیست پیامک‌های ثبت‌نشده منتقل شد" else "Moved to unregistered list",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPersian) "انتقال به لیست ثبت‌نشده‌ها" else "Move to Unregistered List", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPersian) "ماندن در لیست ثبت‌نشده‌ها" else "Keep in Unregistered List", fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = {
                        val qid = info.queueId
                        if (qid != null) viewModel.ignorePendingSms(qid)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isPersian) "نادیده گرفتن" else "Ignore", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { },
        dismissButton = { }
    )
}

/**
 * پاپ‌آپ جدید انتخاب مقصد برداشتِ شخصی (با حفظ پاپ‌آپ قبلی):
 *  - لیست هزینه‌های شخصی (سرفصل‌های حساب شخصی)
 *  - تیک برای لیست اشخاص در بخش فرزندان (حساب اشخاص، قابل گزارش‌گیری با نام شخص و ریز حساب)
 */
@Composable
fun PersonalWithdrawTargetPickerDialog(
    isPersian: Boolean = true,
    subAccounts: List<PersonalSubAccount>,
    persons: List<String>,
    onSelect: (kind: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKind by remember { mutableStateOf("") } // PERSONAL_SUB یا PERSON
    var selectedName by remember { mutableStateOf("") }
    var newPersonName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPersian) "انتخاب هزینه شخصی / حساب اشخاص" else "Personal Expense / Person Account",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isPersian) "هزینه‌های شخصی (سرفصل‌های پرمیس):" else "Personal Expenses:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subAccounts.isEmpty()) {
                    Text("سرفصل هزینه شخصی ثبت نشده است.", fontSize = 11.sp, color = Color.Gray)
                }
                // نمایش درختی: سرفصل والد (گروه) + زیرشاخه‌های قابل انتخاب
                val groupedSubs = subAccounts.groupBy { it.parentName }
                groupedSubs.forEach { (parent, subs) ->
                    if (parent.isNotBlank()) {
                        Text(
                            text = "📁 $parent",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    subs.forEach { sub ->
                        val displayName = if (sub.parentName.isBlank()) sub.name else "${sub.parentName} - ${sub.name}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedKind = "PERSONAL_SUB"
                                    selectedName = displayName
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedKind == "PERSONAL_SUB" && selectedName == displayName,
                                onClick = {
                                    selectedKind = "PERSONAL_SUB"
                                    selectedName = displayName
                                }
                            )
                            Text(sub.name, fontSize = 11.5.sp)
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = if (isPersian) "اشخاص (بخش فرزندان) — با تیک انتخاب کنید:" else "Persons (Children section):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (persons.isEmpty()) {
                    Text(
                        text = if (isPersian) "شخصی ثبت نشده؛ نام شخص جدید را در فیلد زیر وارد کنید." else "No persons yet; add one below.",
                        fontSize = 10.5.sp,
                        color = Color.Gray
                    )
                }
                persons.forEach { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedKind = "PERSON"
                                selectedName = person
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedKind == "PERSON" && selectedName == person,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedKind = "PERSON"
                                    selectedName = person
                                } else if (selectedKind == "PERSON" && selectedName == person) {
                                    selectedKind = ""
                                    selectedName = ""
                                }
                            }
                        )
                        Text("🧒 $person", fontSize = 11.5.sp)
                    }
                }

                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    label = { Text(if (isPersian) "شخص جدید (نام)" else "New person (name)", fontSize = 10.5.sp) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val name = newPersonName.trim()
                                if (name.isNotBlank()) {
                                    selectedKind = "PERSON"
                                    selectedName = name
                                }
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (selectedKind == "PERSON" && selectedName.isNotBlank() && persons.none { it == selectedName }) {
                    Text(
                        text = "✅ شخص جدید انتخاب‌شده: $selectedName",
                        fontSize = 10.5.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selectedKind, selectedName) },
                enabled = selectedKind.isNotBlank() && selectedName.isNotBlank()
            ) {
                Text(if (isPersian) "انتخاب" else "Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
        }
    )
}

/**
 * پاپ‌آپ انتخاب سرفصل درآمد شخصی (ساختار درختی پرمیس: حقوق/فروش/سود سرمایه/
 * یارانه و هدایا/درآمد اجاره) — با حفظ پاپ‌آپ قبلی ثبت تراکنش.
 */
@Composable
fun PersonalIncomePickerDialog(
    isPersian: Boolean = true,
    subAccounts: List<PersonalSubAccount>,
    onSelect: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPersian) "انتخاب سرفصل درآمد شخصی" else "Select Income Category",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (subAccounts.isEmpty()) {
                    Text("سرفصل درآمدی ثبت نشده است.", fontSize = 11.sp, color = Color.Gray)
                }
                val grouped = subAccounts.groupBy { it.parentName }
                grouped.forEach { (parent, subs) ->
                    if (parent.isNotBlank()) {
                        Text(
                            text = "📁 $parent",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    subs.forEach { sub ->
                        val displayName = if (sub.parentName.isBlank()) sub.name else "${sub.parentName} - ${sub.name}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedName = displayName },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedName == displayName,
                                onClick = { selectedName = displayName }
                            )
                            Text(sub.name, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selectedName) },
                enabled = selectedName.isNotBlank()
            ) {
                Text(if (isPersian) "انتخاب" else "Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
        }
    )
}
