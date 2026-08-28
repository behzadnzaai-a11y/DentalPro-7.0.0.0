package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ExtractedChequeData
import com.example.ai.GeminiChequeExtractor
import com.example.ui.DentalViewModel
import kotlinx.coroutines.launch

/**
 * منطق مشترک ثبت چک از فرم (دستی یا پس از استخراج AI) — مبلغ/تاریخ/بانک+شعبه/یادداشت
 * و ثبت رکورد پرداخت از نوع CHEQUE. هر دو مسیر (هوشمند و دستی) دقیقاً همین تابع را
 * صدا می‌زنند تا محاسبات حسابداری یکسان بماند.
 */
fun commitChequePaymentFromForm(
    viewModel: DentalViewModel,
    patientId: Long,
    amountStr: String,
    bankName: String,
    branchName: String,
    branchCode: String,
    drawerName: String,
    drawerNationalId: String,
    chequeNumber: String,
    sayadiId: String,
    dueDateStr: String,
    amountTextStr: String,
    photoPath: String? = null
): Boolean {
    val amount = amountStr.trim().toLongOrNull() ?: return false
    if (amount <= 0) return false
    val dueMillis = try {
        val parts = dueDateStr.trim().split("-")
        if (parts.size == 3) {
            com.example.util.PersianCalendarHelper.jalaliToTimestamp(
                parts[0].toInt(), parts[1].toInt(), parts[2].toInt()
            )
        } else null
    } catch (e: Exception) {
        null
    }
    // نکته: bankName ترکیبی از بانک + شعبه/کدش می‌شود چون رکورد Payment فقط یک فیلد
    // chequeBank دارد؛ کد ملی صاحب حساب و مبلغ به حروف هم در یادداشت نگه داشته می‌شوند
    // تا هیچ اطلاعاتی که کاربر تایید کرده گم نشود.
    val bankCombined = buildString {
        append(bankName.trim())
        if (branchName.isNotBlank() || branchCode.isNotBlank()) {
            append(" - شعبه ")
            append(branchName.trim())
            if (branchCode.isNotBlank()) append(" (کد ${branchCode.trim()})")
        }
    }.trim().ifBlank { null }
    val notesCombined = buildString {
        if (amountTextStr.isNotBlank()) append("مبلغ به حروف: ${amountTextStr.trim()}")
        if (drawerNationalId.isNotBlank()) {
            if (isNotEmpty()) append(" | ")
            append("کد ملی صاحب حساب: ${drawerNationalId.trim()}")
        }
    }.trim().ifBlank { null }
    viewModel.addPayment(
        patientId = patientId,
        amount = amount,
        type = "CHEQUE",
        chequeNum = chequeNumber.trim().ifBlank { null },
        chequeDue = dueMillis,
        sayadiId = sayadiId.trim().ifBlank { null },
        chequeBank = bankCombined,
        chequeDrawer = drawerName.trim().ifBlank { null },
        chequePhotoFileId = photoPath,
        notes = notesCombined
    )
    return true
}

/**
 * آپلود دسته‌ای چک با استخراج AI.
 *
 * جریان دقیقاً طبق درخواست: کاربر همه‌ی عکس‌های چک را یک‌جا از گالری انتخاب می‌کند (چندتایی)،
 * اما پردازش خودِ چک‌ها — کراپ و ثبت — یک‌به‌یک و جداگانه انجام می‌شود، نه هم‌زمان: برای هر
 * عکس، ابتدا ادیتور کراپ (با مشخصات چک صیادی ۱۰۷۵×۵۴۴) باز می‌شود، سپس عکسِ کراپ‌شده به Gemini
 * فرستاده می‌شود تا مبلغ/تاریخ/بانک/صادرکننده/شماره چک/شناسه صیادی را استخراج کند، نتیجه برای
 * تایید یا اصلاح به کاربر نشان داده می‌شود، و فقط بعد از تایید صریح، یک رکورد پرداخت مجزا ثبت
 * می‌شود — سپس خودکار می‌رود سراغ عکس بعدی در صف.
 */
@Composable
fun BatchChequeUploadDialog(
    patientId: Long,
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(fa: String, en: String) = if (isPersian) fa else en

    var queue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var croppedLocalPath by remember { mutableStateOf<String?>(null) }
    var extraction by remember { mutableStateOf<ExtractedChequeData?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }
    var skippedCount by remember { mutableStateOf(0) }

    // فیلدهای قابل‌ویرایش فرم تایید — با نتیجه‌ی AI پیش‌پر می‌شوند اما همیشه توسط کاربر قابل اصلاح‌اند
    var amountStr by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var branchCode by remember { mutableStateOf("") }
    var drawerName by remember { mutableStateOf("") }
    var drawerNationalId by remember { mutableStateOf("") }
    var chequeNumber by remember { mutableStateOf("") }
    var sayadiId by remember { mutableStateOf("") }
    var dueDateStr by remember { mutableStateOf("") } // شمسی، YYYY-MM-DD — تنها فیلدِ خامِ تاریخ

    val currency = viewModel.label("currency")
    val isToman = currency.contains("تومان") || currency.contains("Toman")

    // نکته‌ی مهم: مبلغ‌به‌حروف و تاریخِ کامل (روز + نام ماه + سال) دیگر فیلدهای مستقلِ
    // قابل‌ویرایش نیستند — همیشه به‌صورت خودکار و قطعی از amountStr/dueDateStr محاسبه می‌شوند
    // (طبق درخواست: «مبلغ به حروف تابع نتیجه‌ی آن نوشته شود»)، حتی وقتی کاربر دستی این دو را
    // اصلاح می‌کند.
    val amountTextStr = remember(amountStr, currency) {
        val amount = amountStr.trim().toLongOrNull() ?: return@remember ""
        val text = com.example.util.PersianNumberFormatter.numberToPersianWords(amount)
        if (text.isNotBlank()) "$text $currency" else ""
    }
    val dueDateDisplayStr = remember(dueDateStr) {
        com.example.util.PersianNumberFormatter.jalaliDigitsToFullDate(dueDateStr)
    }

    val chequeEditLauncher = rememberPhotoEditLauncher(PhotoAssetKind.SAYAD_CHEQUE) { localPath ->
        croppedLocalPath = localPath
        isExtracting = true
        scope.launch {
            val bitmap = try {
                BitmapFactory.decodeFile(localPath)
            } catch (e: Exception) {
                null
            }
            val result = if (bitmap != null) {
                GeminiChequeExtractor.extract(context, bitmap)
            } else {
                ExtractedChequeData(null, null, null, null, null, null, null, null, null, null, null, null, error = "خطا در خواندن تصویرِ کراپ‌شده")
            }
            extraction = result
            var amt = result.amount
            if (amt != null && isToman) {
                amt /= 10
            }
            amountStr = amt?.toString() ?: ""
            bankName = result.bankName ?: ""
            branchName = result.branchName ?: ""
            branchCode = result.branchCode ?: ""
            drawerName = result.drawerName ?: ""
            drawerNationalId = result.drawerNationalId ?: ""
            chequeNumber = result.chequeNumber ?: ""
            sayadiId = result.sayadiId ?: ""
            dueDateStr = result.dueDateDigits ?: ""
            isExtracting = false
        }
    }

    val multiPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            queue = uris
            currentIndex = 0
            savedCount = 0
            skippedCount = 0
        }
    }

    // به محض این‌که نوبت به یک عکسِ جدید در صف می‌رسد، خودکار ادیتور کراپ برای همان عکس باز می‌شود.
    LaunchedEffect(currentIndex, queue) {
        if (queue.isNotEmpty() && currentIndex < queue.size) {
            croppedLocalPath = null
            extraction = null
            amountStr = ""; bankName = ""; branchName = ""; branchCode = ""
            drawerName = ""; drawerNationalId = ""; chequeNumber = ""; sayadiId = ""; dueDateStr = ""
            chequeEditLauncher(queue[currentIndex])
        }
    }

    fun goToNext() {
        currentIndex += 1
    }

    fun saveCurrentAndAdvance() {
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
                photoPath = croppedLocalPath
            )
        ) {
            savedCount += 1
            goToNext()
        }
    }

    val isDone = queue.isNotEmpty() && currentIndex >= queue.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (queue.isEmpty()) t("آپلود دسته‌ای چک", "Batch Cheque Upload")
                else if (isDone) t("تمام شد", "Done")
                else t("چک ${currentIndex + 1} از ${queue.size}", "Cheque ${currentIndex + 1} of ${queue.size}")
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (extraction != null && extraction?.error == null) it.heightIn(max = 420.dp).verticalScroll(rememberScrollState()) else it }
            ) {
                when {
                    queue.isEmpty() -> {
                        Text(
                            t(
                                "چند عکس چک را هم‌زمان انتخاب کن. هرکدام جداگانه کراپ می‌شود و هوش مصنوعی مشخصاتش را می‌خواند؛ تو فقط تایید می‌کنی.",
                                "Select several cheque photos at once. Each one is cropped separately and AI reads its details; you just confirm."
                            ),
                            fontSize = 12.sp
                        )
                    }
                    isDone -> {
                        Text(
                            t(
                                "$savedCount چک ثبت شد" + if (skippedCount > 0) " و $skippedCount مورد رد شد." else ".",
                                "$savedCount cheques saved" + if (skippedCount > 0) ", $skippedCount skipped." else "."
                            )
                        )
                    }
                    isExtracting -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text(t("در حال خواندن اطلاعات چک با هوش مصنوعی...", "Reading cheque details with AI..."), fontSize = 12.sp)
                        }
                    }
                    else -> {
                        if (extraction?.error != null) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    extraction?.error ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        val current = com.example.ai.AiProviderSettings.getActiveProvider(context)
                                        val next = if (current == com.example.ai.AiProviderSettings.Provider.GEMINI) com.example.ai.AiProviderSettings.Provider.GROQ else com.example.ai.AiProviderSettings.Provider.GEMINI
                                        com.example.ai.AiProviderSettings.setActiveProvider(context, next)
                                        android.widget.Toast.makeText(context, "تغییر یافت به ${next.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Text(t("تعویض هوش مصنوعی", "Switch AI Provider")) }
                                    
                                    Button(onClick = {
                                        croppedLocalPath?.let { path ->
                                            isExtracting = true
                                            scope.launch {
                                                val bmp = try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
                                                val res = if (bmp != null) GeminiChequeExtractor.extract(context, bmp)
                                                          else ExtractedChequeData(null,null,null,null,null,null,null,null,null,null,null,null, error = "خطا در خواندن تصویر")
                                                extraction = res
                                                var amt = res.amount
                                                if (amt != null && isToman) amt /= 10
                                                amountStr = amt?.toString() ?: ""
                                                bankName = res.bankName ?: ""
                                                branchName = res.branchName ?: ""
                                                branchCode = res.branchCode ?: ""
                                                drawerName = res.drawerName ?: ""
                                                drawerNationalId = res.drawerNationalId ?: ""
                                                chequeNumber = res.chequeNumber ?: ""
                                                sayadiId = res.sayadiId ?: ""
                                                dueDateStr = res.dueDateDigits ?: ""
                                                isExtracting = false
                                            }
                                        }
                                    }) { Text(t("تلاش دوباره", "Retry")) }
                                }
                            }
                        } else if (extraction != null) {
                            Text(
                                t("مشخصات استخراج‌شده را بررسی و در صورت نیاز اصلاح کن:", "Review the extracted details and correct if needed:"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // مبلغ — عددی، کیبورد Number
                        OutlinedTextField(
                            value = amountStr, onValueChange = { amountStr = it.filter(Char::isDigit) },
                            label = { Text(t("مبلغ رقمی ($currency)", "Amount ($currency)")) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        // مبلغ به حروف — همیشه به‌صورت خودکار از مبلغِ رقمیِ بالا محاسبه می‌شود، قابل‌ویرایش دستی نیست
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
                        // تاریخ سررسید — کیبورد عددی، فرمت شمسی راهنما
                        OutlinedTextField(
                            value = dueDateStr, onValueChange = { dueDateStr = it },
                            label = { Text(t("تاریخ سررسید (شمسی، مثال 1404-03-15)", "Due date (Jalali, e.g. 1404-03-15)")) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        // تاریخ کامل (روز + نام ماه + سال) — همیشه خودکار از تاریخِ رقمیِ بالا محاسبه می‌شود
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
                }
            }
        },
        confirmButton = {
            when {
                queue.isEmpty() -> {
                    Button(onClick = { multiPickLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("انتخاب عکس‌ها", "Select Photos"))
                    }
                }
                isDone -> {
                    Button(onClick = onDismiss) { Text(t("بستن", "Close")) }
                }
                isExtracting -> {}
                else -> {
                    Button(
                        onClick = { saveCurrentAndAdvance() },
                        enabled = amountStr.trim().toLongOrNull()?.let { it > 0 } == true
                    ) {
                        Text(t("ثبت و بعدی", "Save & Next"))
                    }
                }
            }
        },
        dismissButton = {
            when {
                queue.isEmpty() -> {
                    TextButton(onClick = onDismiss) { Text(t("انصراف", "Cancel")) }
                }
                isDone || isExtracting -> {}
                else -> {
                    TextButton(onClick = {
                        skippedCount += 1
                        goToNext()
                    }) { Text(t("رد کردن این چک", "Skip this cheque")) }
                }
            }
        }
    )
}
