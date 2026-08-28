package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DentalViewModel

/**
 * ثبت دستی چک صیادی — دقیقاً همان فرم تایید اطلاعات استخراج‌شده در BatchChequeUploadDialog
 * (درخواست: دکمه «ثبت دستی-بارکد چک‌ها» زیر دکمه ثبت هوشمند دسته‌ای؛ همان فرم آماده با همه
 * فیلدهای استخراج‌شده + دکمه‌های «ثبت چک» و «برگشت»). ثبت نهایی از طریق تابع مشترک
 * commitChequePaymentFromForm انجام می‌شود تا حسابداری با مسیر هوشمند یکسان باشد.
 */
@Composable
fun ManualChequeRegisterDialog(
    patientId: Long,
    viewModel: DentalViewModel,
    isPersian: Boolean,
    initialPhotoPath: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    fun t(fa: String, en: String) = if (isPersian) fa else en

    // فیلدها — دقیقاً همان مجموعه فیلدهای فرم استخراج هوشمند
    var amountStr by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var branchCode by remember { mutableStateOf("") }
    var drawerName by remember { mutableStateOf("") }
    var drawerNationalId by remember { mutableStateOf("") }
    var chequeNumber by remember { mutableStateOf("") }
    var sayadiId by remember { mutableStateOf("") }
    var dueDateStr by remember { mutableStateOf("") } // شمسی YYYY-MM-DD
    var attemptSaved by remember { mutableStateOf(false) }

    val currency = viewModel.label("currency")

    // مبلغ به حروف و تاریخ کامل — خودکار از ورودی‌های بالا
    val amountTextStr = remember(amountStr, currency) {
        val amount = amountStr.trim().toLongOrNull() ?: return@remember ""
        val text = com.example.util.PersianNumberFormatter.numberToPersianWords(amount)
        if (text.isNotBlank()) "$text $currency" else ""
    }
    val dueDateDisplayStr = remember(dueDateStr) {
        com.example.util.PersianNumberFormatter.jalaliDigitsToFullDate(dueDateStr)
    }

    val amountError = if (attemptSaved && amountStr.trim().toLongOrNull()?.let { it > 0 } != true)
        t("مبلغ چک را وارد کنید", "Enter cheque amount") else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(t("ثبت دستی چک صیادی", "Manual Sayadi Cheque Entry"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = t(
                        "همه مشخصات چک را وارد کنید (بارکد صیادی را می‌توانید از همان شناسه ۱۶ رقمی کپی کنید):",
                        "Enter all cheque details (paste the 16-digit Sayad ID from the barcode):"
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountStr, onValueChange = { amountStr = it.filter(Char::isDigit) },
                    label = { Text(t("مبلغ رقمی ($currency)", "Amount ($currency)")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = amountError != null,
                    supportingText = { if (amountError != null) Text(amountError, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = amountTextStr.ifBlank { t("— با نوشتن مبلغ رقمی، خودکار محاسبه می‌شود —", "— Calculated automatically from the digits above —") },
                    onValueChange = {}, readOnly = true, enabled = false,
                    label = { Text(t("مبلغ به حروف ($currency)", "Amount in words ($currency)")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = dueDateStr, onValueChange = { dueDateStr = it },
                    label = { Text(t("تاریخ سررسید (شمسی، مثال 1404-03-15)", "Due date (Jalali, e.g. 1404-03-15)")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (dueDateDisplayStr.isNotBlank()) {
                    OutlinedTextField(
                        value = dueDateDisplayStr, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text(t("تاریخ سررسید (خودکار)", "Due date (auto)")) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                OutlinedTextField(
                    value = bankName, onValueChange = { bankName = it },
                    label = { Text(t("بانک", "Bank")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = branchName, onValueChange = { branchName = it },
                        label = { Text(t("شعبه", "Branch")) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = branchCode, onValueChange = { branchCode = it.filter(Char::isDigit) },
                        label = { Text(t("کد شعبه", "Branch code")) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                OutlinedTextField(
                    value = drawerName, onValueChange = { drawerName = it },
                    label = { Text(t("صاحب حساب / صادرکننده", "Account holder / Drawer")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = drawerNationalId, onValueChange = { drawerNationalId = it.filter(Char::isDigit) },
                    label = { Text(t("کد ملی صاحب حساب", "Account holder National ID")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = chequeNumber, onValueChange = { chequeNumber = it.filter(Char::isDigit) },
                    label = { Text(t("شماره سریال چک", "Cheque Serial Number")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = sayadiId, onValueChange = { sayadiId = it.filter(Char::isDigit) },
                    label = { Text(t("شناسه صیادی (۱۶ رقم)", "Sayad ID (16 digits)")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                attemptSaved = true
                if (commitChequePaymentFromForm(
                        viewModel = viewModel,
                        patientId = patientId,
                        amountStr = amountStr,
                        bankName = bankName,
                        branchName = branchName,
                        branchCode = branchCode,
                        drawerName = drawerName,
                        drawerNationalId = drawerNationalId,
                        chequeNumber = chequeNumber,
                        sayadiId = sayadiId,
                        dueDateStr = dueDateStr,
                        amountTextStr = amountTextStr,
                        photoPath = initialPhotoPath
                    )
                ) {
                    onSaved()
                    onDismiss()
                }
            }) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("ثبت چک", "Register Cheque"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("برگشت", "Back"))
            }
        }
    )
}
