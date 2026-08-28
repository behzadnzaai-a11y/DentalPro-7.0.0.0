package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProviderSettings
import com.example.security.SecureSecretStore

/**
 * تنظیمات کلیدهای API — دو ارائه‌دهنده‌ی مجزا (Gemini و Groq)، هرکدام با کلید خودش؛ یک تیکِ
 * «فعال» تعیین می‌کند کدام‌یک واقعاً برای استخراج مشخصات چک استفاده شود (فقط یکی هم‌زمان).
 * هر دو کلید به‌صورت رمزنگاری‌شده (AndroidKeyStore، از طریق SecureSecretStore) روی خودِ گوشی
 * ذخیره می‌شوند — نه از یک سرویس واسطِ سرور.
 *
 * فقط از داخل منوی مخفی (پشت لمس طولانی روی چرخ‌دنده‌ی تنظیمات) قابل‌دسترس است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysSettingsScreen(isPersian: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    fun t(fa: String, en: String) = if (isPersian) fa else en

    var activeProvider by remember { mutableStateOf(AiProviderSettings.getActiveProvider(context)) }

    var geminiKey by remember { mutableStateOf(AiProviderSettings.getGeminiApiKey(context) ?: "") }
    var geminiKeyVisible by remember { mutableStateOf(false) }
    var geminiModel by remember { mutableStateOf(AiProviderSettings.getGeminiModel(context)) }
    var geminiModelMenuExpanded by remember { mutableStateOf(false) }
    var geminiSavedMessage by remember { mutableStateOf<String?>(null) }

    var groqKey by remember { mutableStateOf(AiProviderSettings.getGroqApiKey(context) ?: "") }
    var groqKeyVisible by remember { mutableStateOf(false) }
    var groqSavedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("تنظیمات API های هوش مصنوعی", "AI API Settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                t(
                    "کلید API هرکدام از این دو سرویس را وارد کن. با تیکِ «فعال»، مشخص می‌کنی کدام‌یک واقعاً برای استخراج مشخصات چک استفاده شود — همیشه فقط یکی هم‌زمان فعال است.",
                    "Enter the API key for either service. The «active» checkbox decides which one is actually used for cheque extraction — only one can be active at a time."
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---------------- Gemini ----------------
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Gemini API", "Gemini API"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("فعال", "Active"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Checkbox(
                                checked = activeProvider == AiProviderSettings.Provider.GEMINI,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        activeProvider = AiProviderSettings.Provider.GEMINI
                                        AiProviderSettings.setActiveProvider(context, AiProviderSettings.Provider.GEMINI)
                                    }
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it; geminiSavedMessage = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (geminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { geminiKeyVisible = !geminiKeyVisible }) {
                                Icon(if (geminiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        placeholder = { Text("AIza...") },
                        label = { Text(t("کلیدهای Gemini API (چند کلید با کاما یا خط جدید)", "Gemini API Keys (comma or newline separated)")) }
                    )
                    // فیلد نمایشی: نام سرویس هوش مصنوعی که از روی الگوی خودِ کلید تشخیص داده می‌شود
                    val detectedProvider = remember(geminiKey) { detectAiProviderFromKey(geminiKey) }
                    OutlinedTextField(
                        value = detectedProvider ?: t("— هنوز کلیدی وارد نشده —", "— No key entered yet —"),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(t("سرویس هوش مصنوعیِ تشخیص‌داده‌شده", "Detected AI Provider")) },
                        leadingIcon = {
                            Icon(
                                if (detectedProvider != null) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = if (detectedProvider != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // انتخاب مدل — طبق درخواست، بین ۴ مدل، پیش‌فرض اولی
                    ExposedDropdownMenuBox(
                        expanded = geminiModelMenuExpanded,
                        onExpandedChange = { geminiModelMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(t("مدل Gemini", "Gemini Model")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = geminiModelMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = geminiModelMenuExpanded,
                            onDismissRequest = { geminiModelMenuExpanded = false }
                        ) {
                            AiProviderSettings.GEMINI_MODELS.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        geminiModel = m
                                        AiProviderSettings.setGeminiModel(context, m)
                                        geminiModelMenuExpanded = false
                                        geminiSavedMessage = null
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        t(
                            "این کلید را از aistudio.google.com بگیر.",
                            "Get this key from aistudio.google.com."
                        ),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            AiProviderSettings.setGeminiModel(context, geminiModel)
                            SecureSecretStore.put(context, AiProviderSettings.KEY_GEMINI_API_KEY, geminiKey.trim())
                            geminiSavedMessage = t("ذخیره شد", "Saved")
                        }) {
                            Text(t("ذخیره", "Save"))
                        }
                        if (geminiKey.isNotBlank()) {
                            OutlinedButton(onClick = {
                                geminiKey = ""
                                SecureSecretStore.remove(context, AiProviderSettings.KEY_GEMINI_API_KEY)
                                geminiSavedMessage = t("کلید حذف شد", "Key removed")
                            }) {
                                Text(t("حذف کلید", "Remove Key"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (geminiSavedMessage != null) {
                        Text(geminiSavedMessage ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---------------- Groq ----------------
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Groq API", "Groq API"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("فعال", "Active"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Checkbox(
                                checked = activeProvider == AiProviderSettings.Provider.GROQ,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        activeProvider = AiProviderSettings.Provider.GROQ
                                        AiProviderSettings.setActiveProvider(context, AiProviderSettings.Provider.GROQ)
                                    }
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = { groqKey = it; groqSavedMessage = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (groqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { groqKeyVisible = !groqKeyVisible }) {
                                Icon(if (groqKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        placeholder = { Text("gsk_...") },
                        label = { Text(t("کلیدهای Groq API (چند کلید با کاما یا خط جدید)", "Groq API Keys (comma or newline separated)")) }
                    )
                    // مدل ثابت — طبق درخواست فقط همین یک مدل، غیرقابل‌تغییر
                    OutlinedTextField(
                        value = AiProviderSettings.GROQ_MODEL,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(t("مدل (ثابت)", "Model (fixed)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        t(
                            "این کلید را از console.groq.com بگیر.",
                            "Get this key from console.groq.com."
                        ),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            SecureSecretStore.put(context, AiProviderSettings.KEY_GROQ_API_KEY, groqKey.trim())
                            groqSavedMessage = t("ذخیره شد", "Saved")
                        }) {
                            Text(t("ذخیره", "Save"))
                        }
                        if (groqKey.isNotBlank()) {
                            OutlinedButton(onClick = {
                                groqKey = ""
                                SecureSecretStore.remove(context, AiProviderSettings.KEY_GROQ_API_KEY)
                                groqSavedMessage = t("کلید حذف شد", "Key removed")
                            }) {
                                Text(t("حذف کلید", "Remove Key"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (groqSavedMessage != null) {
                        Text(groqSavedMessage ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // جای باز برای کلیدهای API دیگر در آینده (مثلاً پیامک، و ...)
            Text(
                t("سایر API ها به‌زودی اینجا اضافه می‌شوند.", "Other APIs will be added here soon."),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * تشخیص سرویس هوش مصنوعی از روی خودِ الگوی رشته‌ی کلید — بدون هیچ تماس شبکه‌ای (فقط برای
 * راهنمایی بصری کاربر که کلید درستی وارد کرده). اگر الگویی شناخته نشد، null برمی‌گردد.
 */
private fun detectAiProviderFromKey(key: String): String? {
    val k = key.trim()
    if (k.isBlank()) return null
    return when {
        k.startsWith("AIza") -> "Google Gemini"
        k.startsWith("sk-ant-") -> "Anthropic Claude"
        k.startsWith("sk-proj-") || k.startsWith("sk-") -> "OpenAI"
        k.startsWith("gsk_") -> "Groq"
        k.startsWith("hf_") -> "Hugging Face"
        k.startsWith("r8_") -> "Replicate"
        k.startsWith("xai-") -> "xAI Grok"
        else -> null
    }
}
