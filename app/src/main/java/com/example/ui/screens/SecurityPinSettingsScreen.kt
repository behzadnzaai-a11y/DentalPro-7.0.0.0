package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.auth.UserRole
import com.example.ui.DentalViewModel

/**
 * تنظیمات امنیتی و ورود برنامه + پین ورود دستیار/منشی.
 *
 * طبق درخواست، این بخش از کارت «اطلاعات مطب و چیدمان داشبورد» بیرون کشیده شده و به یک زیرصفحه‌ی
 * جدا در منوی مخفی منتقل شده — قابلیت خودش (تنها راه ورود دستیار/منشی با پین اختصاصی) حذف
 * نشده، فقط دیگر در کارت عمومی تنظیمات مطب نمایش داده نمی‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPinSettingsScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isPinSecurityEnabled by viewModel.isPinSecurityEnabled.collectAsStateWithLifecycle()
    var newPinInput by remember { mutableStateOf("") }
    var newAssistantPinInput by remember { mutableStateOf("") }
    val hasAssistantPin = remember(newAssistantPinInput) { viewModel.hasPinConfigured(UserRole.ASSISTANT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "تنظیمات امنیتی و ورود" else "Security & Login Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ----------------------------------------------------
            // SECTION 2: SECURITY & LOGIN PROTECTION
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "تنظیمات امنیتی و ورود به برنامه" else "Security & App Lock Protection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = if (isPersian) "فعال‌سازی قفل ورود (پین / بیومتریک)" else "Enable Security Lock (PIN/Biometric)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(text = if (isPersian) "در هنگام ورود به برنامه رمز یا اثرانگشت خواسته می‌شود" else "Require authentication on launch", fontSize = 10.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isPinSecurityEnabled,
                                onCheckedChange = { isChecked ->
                                    runCatching { viewModel.setPinSecurityEnabled(isChecked, newPinInput) }
                                        .onSuccess { if (isChecked) newPinInput = "" }
                                        .onFailure { error ->
                                            android.widget.Toast.makeText(
                                                context,
                                                error.message ?: "PIN settings are invalid",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = { inputStr ->
                                    newPinInput = inputStr.filter(Char::isDigit).take(12)
                                },
                                placeholder = { Text(if (isPersian) "۶ تا ۱۲ رقم" else "6 to 12 digits") },
                                label = { Text(if (isPersian) "پین امن جدید" else "New secure PIN") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                            if (isPinSecurityEnabled) {
                                Button(
                                    onClick = {
                                        runCatching { viewModel.setPinSecurityEnabled(true, newPinInput) }
                                            .onSuccess {
                                                newPinInput = ""
                                                android.widget.Toast.makeText(context, if (isPersian) "پین بروزرسانی شد" else "PIN updated", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure { error ->
                                                android.widget.Toast.makeText(context, error.message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                    },
                                    enabled = newPinInput.length >= 6,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isPersian) "ذخیره" else "Save", fontSize = 11.sp)
                                }
                                IconButton(onClick = { viewModel.lockApp() }) {
                                    Icon(Icons.Default.Lock, contentDescription = if (isPersian) "قفل اکنون" else "Lock now")
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // SECTION 3.5: ASSISTANT PIN (منشی/دستیار) — دسترسی جداگانه با پین اختصاصی
            // ----------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isPersian) "پین ورود دستیار/منشی" else "Assistant/Reception PIN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (hasAssistantPin) {
                                if (isPersian) "دستیار با پین اختصاصی خودش وارد می‌شود (بدون دسترسی مالی)" else "Assistant signs in with their own dedicated PIN (no financial access)"
                            } else {
                                if (isPersian) "هنوز برای نقش دستیار پینی تعیین نشده است" else "No PIN has been set for the Assistant role yet"
                            },
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newAssistantPinInput,
                                onValueChange = { inputStr ->
                                    newAssistantPinInput = inputStr.filter(Char::isDigit).take(12)
                                },
                                placeholder = { Text(if (isPersian) "۴ تا ۱۲ رقم" else "4 to 12 digits") },
                                label = { Text(if (isPersian) "پین جدید دستیار" else "New assistant PIN") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                            Button(
                                onClick = {
                                    runCatching { viewModel.setRolePin(UserRole.ASSISTANT, newAssistantPinInput) }
                                        .onSuccess {
                                            newAssistantPinInput = ""
                                            android.widget.Toast.makeText(context, if (isPersian) "پین دستیار ذخیره شد" else "Assistant PIN saved", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            android.widget.Toast.makeText(context, error.message ?: (if (isPersian) "پین نامعتبر است" else "Invalid PIN"), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                },
                                enabled = newAssistantPinInput.length >= 4,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isPersian) "ذخیره" else "Save", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
