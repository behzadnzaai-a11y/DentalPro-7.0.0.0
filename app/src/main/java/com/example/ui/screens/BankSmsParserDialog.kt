package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DentalViewModel
import com.example.util.BankSmsEngine
import com.example.util.PersianNumberFormatter
import kotlinx.coroutines.launch
import com.example.data.Payment
import com.example.data.ExpensePayment
import com.example.data.PersonalTransaction
import com.example.data.Installment
import com.example.ui.components.SimpleCalculatorDialog

@Composable
fun BankSmsParserDialogImpl(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    initialSmsText: String? = null,
    onDismiss: () -> Unit
) {
    var rawSmsText by remember { mutableStateOf(initialSmsText ?: "") }
    val parsedResult = remember(rawSmsText) {
        if (rawSmsText.isNotBlank()) BankSmsEngine.evaluateSms(rawSmsText, "") else null
    }

    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    // فقط حساب‌های کارتی و بانکی معتبر (حذف صندوق/تنخواه نقدی از انتخاب پیش‌فرض پیامک)
    val cardBankAccounts = remember(bankAccounts) {
        bankAccounts.filter { !it.bankName.contains("صندوق") }
    }
    val targetAccountPool = if (cardBankAccounts.isNotEmpty()) cardBankAccounts else bankAccounts

    // تطبیق هوشمند با حساب کارتی بر اساس نام بانک، ۴ رقم کارت یا کلیدواژه‌ها
    val detectedAccount = remember(parsedResult, targetAccountPool) {
        if (parsedResult == null) null
        else targetAccountPool.firstOrNull { acc ->
            val accNum = acc.accountNumber.filter { it.isDigit() }
            val cardNum = acc.cardNumber.filter { it.isDigit() }
            val raw = parsedResult.accountNumberRaw
            (raw.isNotBlank() && accNum.isNotBlank() && (accNum.endsWith(raw) || raw.endsWith(accNum))) ||
            (raw.isNotBlank() && cardNum.isNotBlank() && cardNum.endsWith(raw)) ||
            (parsedResult.bankName.isNotBlank() && (acc.bankName.contains(parsedResult.bankName, ignoreCase = true) || parsedResult.bankName.contains(acc.bankName, ignoreCase = true))) ||
            acc.smsKeywords.split(",").any { it.isNotBlank() && parsedResult.rawSms.contains(it.trim(), ignoreCase = true) }
        } ?: targetAccountPool.firstOrNull()
    }
    
    var selectedAccountId by remember(detectedAccount) { mutableStateOf<Long?>(detectedAccount?.id ?: targetAccountPool.firstOrNull()?.id) }

    val coroutineScope = rememberCoroutineScope()
    
    // Deposit state
    var depositSourceType by remember { mutableStateOf("PATIENT") } // PATIENT, INSTALLMENT, INCOME
    var selectedPatientId by remember { mutableStateOf<Long?>(null) }
    var selectedInstallmentId by remember { mutableStateOf<Long?>(null) }
    var selectedSubAccountId by remember { mutableStateOf<Long?>(null) }
    var editableDepositAmountStr by remember(parsedResult) {
        mutableStateOf(if (parsedResult != null && parsedResult.amountTomans > 0) parsedResult.amountTomans.toString() else "")
    }

    // Withdrawal state
    data class WithdrawalRow(
        val id: Int = kotlin.random.Random.nextInt(),
        var type: String = "CLINIC", // CLINIC, PERSONAL
        var categoryId: Long? = null,
        var amount: Long = 0L
    )
    var withdrawalRows by remember(parsedResult) {
        mutableStateOf(listOf(WithdrawalRow(amount = parsedResult?.amountTomans ?: 0L)))
    }

    // Calculator states
    var activeCalculatorRowIndex by remember { mutableStateOf<Int?>(null) }
    var showDepositCalculator by remember { mutableStateOf(false) }

    if (showDepositCalculator) {
        val curVal = editableDepositAmountStr.filter { it.isDigit() }.toLongOrNull() ?: parsedResult?.amountTomans ?: 0L
        SimpleCalculatorDialog(
            initialValue = curVal,
            isPersian = isPersian,
            onConfirm = { calculatedAmount ->
                editableDepositAmountStr = calculatedAmount.toString()
            },
            onDismiss = { showDepositCalculator = false }
        )
    }

    if (activeCalculatorRowIndex != null) {
        val rowIdx = activeCalculatorRowIndex!!
        val curVal = withdrawalRows.getOrNull(rowIdx)?.amount ?: 0L
        SimpleCalculatorDialog(
            initialValue = curVal,
            isPersian = isPersian,
            onConfirm = { calculatedAmount ->
                val mut = withdrawalRows.toMutableList()
                if (rowIdx in mut.indices) {
                    mut[rowIdx] = mut[rowIdx].copy(amount = calculatedAmount)
                    withdrawalRows = mut
                }
            },
            onDismiss = { activeCalculatorRowIndex = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (isPersian) "ثبت تراکنش پیامک بانکی" else "Register Bank Transaction",
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
                OutlinedTextField(
                    value = rawSmsText,
                    onValueChange = { rawSmsText = it },
                    label = { Text(if (isPersian) "متن پیامک" else "SMS Text") },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                )

                if (parsedResult != null) {
                    val isDeposit = parsedResult.transactionType == "DEPOSIT"
                    
                    // Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if(isDeposit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("🏦 بانک: ${parsedResult.bankName}", fontWeight = FontWeight.Bold, color = if(isDeposit) Color(0xFF1B5E20) else Color(0xFFB71C1C))
                            Text("مبلغ استخراج‌شده: ${PersianNumberFormatter.formatAmount(parsedResult.amountTomans)} تومان", fontWeight = FontWeight.Bold)
                            Text("نوع تراکنش: ${if(isDeposit) "واریز" else "برداشت / خرید"}")
                            Text("حساب کارتی متناظر: ${detectedAccount?.bankName?.let { "$it (کارتی)" } ?: "نامشخص (نیاز به انتخاب)"}", fontSize = 11.5.sp)
                        }
                    }

                    // FROM / TO logic
                    if (isDeposit) {
                        Text(if (isPersian) "مبلغ واریز و منبع تراکنش:" else "Deposit Amount & Source:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        
                        // فیلد مبلغ اتوماتیک پرشده و کاملاً قابل ویرایش همراه با دکمه ماشین‌حساب
                        OutlinedTextField(
                            value = editableDepositAmountStr,
                            onValueChange = { editableDepositAmountStr = it.filter { c -> c.isDigit() } },
                            label = { Text(if (isPersian) "مبلغ واریز (تومان) - قابل ویرایش" else "Deposit Amount (Toman)") },
                            trailingIcon = {
                                IconButton(onClick = { showDepositCalculator = true }) {
                                    Icon(Icons.Default.Calculate, contentDescription = "Calculator", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = depositSourceType == "PATIENT", onClick = { depositSourceType = "PATIENT" }, label = { Text("بیمار", fontSize=10.sp) })
                            FilterChip(selected = depositSourceType == "INSTALLMENT", onClick = { depositSourceType = "INSTALLMENT" }, label = { Text("اقساط", fontSize=10.sp) })
                            FilterChip(selected = depositSourceType == "INCOME", onClick = { depositSourceType = "INCOME" }, label = { Text("درآمد متفرقه/شخصی", fontSize=10.sp) })
                        }
                        
                        val patients by viewModel.patients.collectAsStateWithLifecycle()
                        if (depositSourceType == "PATIENT") {
                            // Patient selection
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(patients.find { it.id == selectedPatientId }?.name ?: "انتخاب بیمار...")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    patients.forEach { p ->
                                        DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedPatientId = p.id; expanded = false })
                                    }
                                }
                            }
                        } else if (depositSourceType == "INSTALLMENT") {
                            Text("انتخاب قسط (باید از پرونده بیمار ثبت شود)", fontSize=11.sp, color=Color.Gray)
                        } else if (depositSourceType == "INCOME") {
                            val subs by viewModel.personalSubAccounts.collectAsStateWithLifecycle(emptyList())
                            val incomeSubs = subs.filter { it.category == "INCOME" || it.ledgerSide == "CREDITOR" }
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(subs.find { it.id == selectedSubAccountId }?.name ?: "انتخاب سرفصل...")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    incomeSubs.forEach { s ->
                                        DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedSubAccountId = s.id; expanded = false })
                                    }
                                }
                            }
                        }
                    } else {
                        // Withdrawal logic (To)
                        Text(if (isPersian) "به (مقصد/هزینه‌های دسته‌ای):" else "To (Batch Expenses):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        
                        withdrawalRows.forEachIndexed { index, row ->
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(selected = row.type == "CLINIC", onClick = { val mut = withdrawalRows.toMutableList(); mut[index] = row.copy(type = "CLINIC"); withdrawalRows = mut }, label = { Text("هزینه مطب", fontSize=9.sp) })
                                        FilterChip(selected = row.type == "PERSONAL", onClick = { val mut = withdrawalRows.toMutableList(); mut[index] = row.copy(type = "PERSONAL"); withdrawalRows = mut }, label = { Text("هزینه شخصی", fontSize=9.sp) })
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (withdrawalRows.size > 1) {
                                            IconButton(onClick = { withdrawalRows = withdrawalRows.filter { it.id != row.id } }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    // Category Select
                                    if (row.type == "CLINIC") {
                                        val clinicExps by viewModel.clinicExpenses.collectAsStateWithLifecycle(emptyList())
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                Text(clinicExps.find { it.id == row.categoryId }?.description ?: "انتخاب هزینه مطب...", fontSize = 10.sp)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                clinicExps.forEach { ce ->
                                                    DropdownMenuItem(text = { Text(ce.description, fontSize=11.sp) }, onClick = { 
                                                        val mut = withdrawalRows.toMutableList()
                                                        mut[index] = row.copy(categoryId = ce.id)
                                                        withdrawalRows = mut
                                                        expanded = false
                                                    })
                                                }
                                            }
                                        }
                                    } else {
                                        val subs by viewModel.personalSubAccounts.collectAsStateWithLifecycle(emptyList())
                                        val expSubs = subs.filter { it.category == "EXPENSE" || it.category == "DRAW" }
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                Text(subs.find { it.id == row.categoryId }?.name ?: "انتخاب هزینه شخصی...", fontSize = 10.sp)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                expSubs.forEach { sub ->
                                                    DropdownMenuItem(text = { Text(sub.name, fontSize=11.sp) }, onClick = { 
                                                        val mut = withdrawalRows.toMutableList()
                                                        mut[index] = row.copy(categoryId = sub.id)
                                                        withdrawalRows = mut
                                                        expanded = false
                                                    })
                                                }
                                            }
                                        }
                                    }
                                    // فیلد مبلغ هزینه با ماشین‌حساب خودکار
                                    OutlinedTextField(
                                        value = if (row.amount > 0) row.amount.toString() else "",
                                        onValueChange = { 
                                            val num = it.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L
                                            val mut = withdrawalRows.toMutableList()
                                            mut[index] = row.copy(amount = num)
                                            withdrawalRows = mut
                                        },
                                        label = { Text(if (isPersian) "مبلغ سهم این هزینه (تومان)" else "Amount") },
                                        placeholder = { Text("مبلغ سهم این هزینه", fontSize = 10.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = { activeCalculatorRowIndex = index }) {
                                                Icon(Icons.Default.Calculate, contentDescription = "Calculator", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { withdrawalRows = withdrawalRows + WithdrawalRow() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPersian) "افزودن سهم هزینه ترکیبی جدید" else "Add Batch Expense Row", fontSize = 11.sp)
                        }
                        
                        val sum = withdrawalRows.sumOf { it.amount }
                        val effectiveTargetAmount = parsedResult.amountTomans
                        val diff = effectiveTargetAmount - sum
                        Text(
                            text = "مجموع هزینه‌ها: ${PersianNumberFormatter.formatAmount(sum)} از ${PersianNumberFormatter.formatAmount(effectiveTargetAmount)} تومان",
                            color = if (sum == effectiveTargetAmount) Color(0xFF2E7D32) else Color(0xFFC2410C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        if (diff > 0 && withdrawalRows.isNotEmpty()) {
                            TextButton(onClick = { 
                                val mut = withdrawalRows.toMutableList()
                                mut[mut.lastIndex] = mut.last().copy(amount = mut.last().amount + diff)
                                withdrawalRows = mut
                            }) {
                                Text("جبران ${PersianNumberFormatter.formatAmount(diff)} تومان باقی‌مانده روی سطر آخر", fontSize = 10.sp)
                            }
                        }
                    }

                    // Bank Account Selector — فقط حساب‌های کارتی/بانکی
                    Text(if (isPersian) "حساب کارتی / بانکی متصل:" else "Linked Card / Bank Account:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    var accExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            val acc = targetAccountPool.find { it.id == selectedAccountId }
                            Text(acc?.let { "💳 ${it.bankName} - ${it.cardHolder} (${it.cardNumber.takeLast(4)})" } ?: "انتخاب حساب کارتی...", fontSize=11.sp)
                        }
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            targetAccountPool.forEach { b ->
                                val cardSuffix = if (b.cardNumber.length >= 4) " (کارت: *${b.cardNumber.takeLast(4)})" else ""
                                DropdownMenuItem(
                                    text = { Text("💳 ${b.bankName} - ${b.cardHolder}$cardSuffix", fontSize=11.sp) },
                                    onClick = { selectedAccountId = b.id; accExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (parsedResult != null) {
                val isDeposit = parsedResult.transactionType == "DEPOSIT"
                val depositFinalAmt = editableDepositAmountStr.filter { it.isDigit() }.toLongOrNull() ?: parsedResult.amountTomans
                val sum = withdrawalRows.sumOf { it.amount }
                val isWithdrawalValid = !isDeposit && sum > 0 && withdrawalRows.all { it.categoryId != null && it.amount > 0 }
                val isDepositValid = isDeposit && depositFinalAmt > 0 && ((depositSourceType == "PATIENT" && selectedPatientId != null) || (depositSourceType == "INCOME" && selectedSubAccountId != null))
                
                Button(
                    enabled = selectedAccountId != null && (isDepositValid || isWithdrawalValid),
                    onClick = {
                        val smsId = "sms_${parsedResult.dateStr}_${parsedResult.timeStr}_${parsedResult.amountTomans}".hashCode().toString()
                        coroutineScope.launch {
                            if (isDeposit) {
                                if (depositSourceType == "PATIENT") {
                                    val payment = Payment(
                                        patientId = selectedPatientId!!,
                                        amount = depositFinalAmt,
                                        type = "CARD",
                                        bankAccountId = selectedAccountId,
                                        sourceSmsNativeId = smsId
                                    )
                                    viewModel.database.paymentDao().insertPayment(payment)
                                } else if (depositSourceType == "INCOME") {
                                    val subAcc = viewModel.database.personalSubAccountDao().getAllList().find { it.id == selectedSubAccountId }
                                    val pt = PersonalTransaction(
                                        type = "INCOME",
                                        subAccountName = subAcc?.name ?: "سایر",
                                        amount = depositFinalAmt,
                                        bankAccountId = selectedAccountId,
                                        sourceSmsNativeId = smsId
                                    )
                                    viewModel.database.personalTransactionDao().insertTransaction(pt)
                                }
                            } else {
                                withdrawalRows.forEach { row ->
                                    if (row.type == "CLINIC") {
                                        val ep = ExpensePayment(
                                            clinicExpenseId = row.categoryId!!,
                                            amount = row.amount,
                                            method = "CARD_TRANSFER",
                                            bankAccountId = selectedAccountId,
                                            sourceSmsNativeId = smsId
                                        )
                                        viewModel.database.expensePaymentDao().insertExpensePayment(ep)
                                    } else {
                                        val subAcc = viewModel.database.personalSubAccountDao().getAllList().find { it.id == row.categoryId }
                                        val pt = PersonalTransaction(
                                            type = "EXPENSE",
                                            subAccountName = subAcc?.name ?: "سایر",
                                            amount = row.amount,
                                            bankAccountId = selectedAccountId,
                                            sourceSmsNativeId = smsId
                                        )
                                        viewModel.database.personalTransactionDao().insertTransaction(pt)
                                    }
                                }
                            }
                            onDismiss()
                        }
                    }
                ) {
                    Text(if (isPersian) "ثبت در حسابداری" else "Register")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
        }
    )
}
