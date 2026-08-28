package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DentalViewModel
import kotlinx.coroutines.launch

private data class OnboardingStaffItem(val name: String, val phone: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: DentalViewModel,
    onComplete: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) } // 0: Clinic Info, 1: Doctor & License, 2: Staff & Team
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Step 0: Clinic Info
    var clinicName by remember { mutableStateOf("") }
    var clinicPhone by remember { mutableStateOf("") }
    var clinicAddress by remember { mutableStateOf("") }

    // Step 1: Doctor & License
    var doctorName by remember { mutableStateOf("") }
    var doctorLicenseNo by remember { mutableStateOf("") }
    var doctorPin by remember { mutableStateOf("") }
    var doctorPinConfirm by remember { mutableStateOf("") }

    // Step 2: Staff & Team
    val staffList = remember { mutableStateListOf<OnboardingStaffItem>() }
    var newStaffName by remember { mutableStateOf("") }
    var newStaffPhone by remember { mutableStateOf("") }
    var assistantPin by remember { mutableStateOf("") }

    fun t(fa: String, en: String) = if (isPersian) fa else en

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = t("راه‌اندازی اولیه مطب", "Initial Clinic Setup"),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step Progress Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepBadge(stepNumber = 1, title = t("اطلاعات مطب", "Clinic Info"), isCurrent = step == 0, isDone = step > 0)
                    Divider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), color = if (step > 0) cs.primary else cs.outlineVariant)
                    StepBadge(stepNumber = 2, title = t("پزشک و نظام‌پزشکی", "Doctor Info"), isCurrent = step == 1, isDone = step > 1)
                    Divider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), color = if (step > 1) cs.primary else cs.outlineVariant)
                    StepBadge(stepNumber = 3, title = t("تیم درمان", "Staff Team"), isCurrent = step == 2, isDone = false)
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cs.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = cs.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(msg, color = cs.onErrorContainer, fontSize = 13.sp)
                            }
                        }
                    }
                }

                when (step) {
                    0 -> {
                        // Step 1: Clinic Information
                        StepSectionHeader(
                            icon = Icons.Default.Business,
                            title = t("مرحله ۱: مشخصات مطب / کلینیک", "Step 1: Clinic Details"),
                            description = t("اطلاعات عمومی مطب جهت درج در سربرگ، نسخه‌ها و پیامک‌های ارسالی", "General clinic info used in invoices, prescriptions, and reminders")
                        )

                        OutlinedTextField(
                            value = clinicName,
                            onValueChange = { clinicName = it; errorMessage = null },
                            label = { Text(t("نام مطب یا کلینیک *", "Clinic Name *")) },
                            leadingIcon = { Icon(Icons.Default.LocalHospital, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = clinicPhone,
                            onValueChange = { clinicPhone = it; errorMessage = null },
                            label = { Text(t("تلفن مطب", "Clinic Phone")) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = clinicAddress,
                            onValueChange = { clinicAddress = it },
                            label = { Text(t("آدرس مطب", "Clinic Address")) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    1 -> {
                        // Step 2: Doctor & License
                        StepSectionHeader(
                            icon = Icons.Default.MedicalServices,
                            title = t("مرحله ۲: اطلاعات پزشک و شماره نظام", "Step 2: Doctor & License"),
                            description = t("مشخصات پزشک مسئول و پین ورود برای دسترسی‌های مدیریتی", "Responsible doctor details and secure PIN for access control")
                        )

                        OutlinedTextField(
                            value = doctorName,
                            onValueChange = { doctorName = it; errorMessage = null },
                            label = { Text(t("نام و نام‌خانوادگی پزشک *", "Doctor's Full Name *")) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = doctorLicenseNo,
                            onValueChange = { doctorLicenseNo = it; errorMessage = null },
                            label = { Text(t("شماره نظام پزشکی (اختیاری)", "Medical License No (Optional)")) },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t("تعیین پین اختصاصی پزشک (۴ تا ۱۲ رقم):", "Set Doctor's Security PIN (4-12 digits):"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = doctorPin,
                            onValueChange = { doctorPin = it.filter(Char::isDigit).take(12); errorMessage = null },
                            label = { Text(t("پین ورود پزشک (اختیاری)", "Doctor PIN (Optional)")) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (doctorPin.isNotEmpty()) {
                            OutlinedTextField(
                                value = doctorPinConfirm,
                                onValueChange = { doctorPinConfirm = it.filter(Char::isDigit).take(12); errorMessage = null },
                                label = { Text(t("تکرار پین پزشک", "Confirm PIN")) },
                                leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    2 -> {
                        // Step 3: Staff & Treatment Team
                        StepSectionHeader(
                            icon = Icons.Default.Groups,
                            title = t("مرحله ۳: تیم درمان و دستیاران", "Step 3: Clinical Team & Staff"),
                            description = t("ثبت اسامی همکاران، دستیاران و منشی‌ها (می‌توانید بعداً نیز اضافه کنید)", "Add staff members and assistants (you can also edit this later in settings)")
                        )

                        if (staffList.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = t("لیست همکاران اضافه شده:", "Added Staff Members:"),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    staffList.forEach { staff ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PersonOutline, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("${staff.name} ${if (staff.phone.isNotBlank()) "(${staff.phone})" else ""}", fontSize = 14.sp)
                                            }
                                            IconButton(onClick = { staffList.remove(staff) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = cs.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = newStaffName,
                                onValueChange = { newStaffName = it },
                                label = { Text(t("نام دستیار / منشی", "Staff Name")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = newStaffPhone,
                                onValueChange = { newStaffPhone = it },
                                label = { Text(t("شماره تماس", "Phone")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (newStaffName.isNotBlank()) {
                                    staffList.add(OnboardingStaffItem(newStaffName.trim(), newStaffPhone.trim()))
                                    newStaffName = ""
                                    newStaffPhone = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(t("افزودن دستیار به لیست", "Add Staff Member"))
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t("پین عمومی دستیار/منشی (اختیاری):", "Shared Assistant PIN (Optional):"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedTextField(
                            value = assistantPin,
                            onValueChange = { assistantPin = it.filter(Char::isDigit).take(12) },
                            label = { Text(t("پین ورود منشی", "Assistant PIN")) },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Bottom Navigation Actions
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = {
                                step -= 1
                                errorMessage = null
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(t("مرحله قبل", "Back"))
                        }
                    } else {
                        Spacer(Modifier.width(16.dp))
                    }

                    Button(
                        onClick = {
                            when (step) {
                                0 -> {
                                    if (clinicName.trim().isBlank()) {
                                        errorMessage = t("لطفاً نام مطب یا کلینیک را وارد کنید", "Please enter clinic name")
                                    } else {
                                        errorMessage = null
                                        step = 1
                                    }
                                }
                                1 -> {
                                    if (doctorName.trim().isBlank()) {
                                        errorMessage = t("لطفاً نام و نام‌خانوادگی پزشک را وارد کنید", "Please enter doctor name")
                                    } else if (doctorPin.isNotEmpty() && doctorPin != doctorPinConfirm) {
                                        errorMessage = t("پین پزشک و تکرار آن یکسان نیستند", "PIN and confirmation do not match")
                                    } else if (doctorPin.isNotEmpty() && doctorPin.length < 4) {
                                        errorMessage = t("پین باید حداقل ۴ رقم باشد", "PIN must be at least 4 digits")
                                    } else {
                                        errorMessage = null
                                        step = 2
                                    }
                                }
                                2 -> {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            viewModel.completeOnboarding(
                                                clinicName = clinicName.trim(),
                                                clinicPhone = clinicPhone.trim(),
                                                clinicAddress = clinicAddress.trim(),
                                                doctorName = doctorName.trim(),
                                                doctorLicenseNo = doctorLicenseNo.trim(),
                                                doctorPin = doctorPin
                                            )
                                            staffList.forEach { staff ->
                                                viewModel.addStaffDuringOnboarding(
                                                    fullName = staff.name,
                                                    phone = staff.phone,
                                                    pin = assistantPin.takeIf { it.isNotBlank() }
                                                )
                                            }
                                            // Trigger sync
                                            try {
                                                viewModel.syncEngine.pushPending()
                                            } catch (e: Exception) {
                                                // Ignore offline
                                            }
                                            onComplete()
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: t("خطایی در ثبت اطلاعات رخ داد", "Failed to save settings")
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = cs.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(t("در حال ذخیره...", "Saving..."))
                        } else {
                            Text(
                                if (step == 2) t("تکمیل و ورود به برنامه", "Complete & Enter")
                                else t("مرحله بعد", "Next")
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (step == 2) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(stepNumber: Int, title: String, isCurrent: Boolean, isDone: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bgColor = when {
        isDone -> cs.primary
        isCurrent -> cs.primaryContainer
        else -> cs.surfaceVariant
    }
    val contentColor = when {
        isDone -> cs.onPrimary
        isCurrent -> cs.onPrimaryContainer
        else -> cs.onSurfaceVariant
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    text = "$stepNumber",
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) cs.primary else cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepSectionHeader(icon: ImageVector, title: String, description: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}
