@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import kotlin.OptIn
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.*
import com.example.reports.*
import com.example.ui.DentalViewModel
import kotlinx.coroutines.launch
import com.example.ui.theme.*
import java.util.Locale
import coil.compose.AsyncImage
import java.io.File
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard

fun getPatientFormattedName(name: String, gender: String, isPersian: Boolean = true): String {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return ""
    
    val isFemale = gender == "زن" || gender == "خانم" || gender.lowercase() == "female" || cleanName.startsWith("خانم") || cleanName.startsWith("Mrs") || cleanName.startsWith("Ms")
    
    if (cleanName.startsWith("آقای") || cleanName.startsWith("خانم") || cleanName.startsWith("Mr.") || cleanName.startsWith("Mrs.") || cleanName.startsWith("Ms.")) {
        return cleanName
    }
    
    val prefix = if (isPersian) {
        if (isFemale) "خانم " else "آقای "
    } else {
        if (isFemale) "Mrs. " else "Mr. "
    }
    return "$prefix$cleanName"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDentalScreen(viewModel: DentalViewModel, initialBankSms: String? = null, initialOpenAppUpdate: Boolean = false) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentViewingImage by viewModel.currentViewingImage.collectAsStateWithLifecycle()
    val navController = androidx.navigation.compose.rememberNavController()
    
    var pendingBankSmsText by remember(initialBankSms) { mutableStateOf(initialBankSms) }

    if (!pendingBankSmsText.isNullOrBlank()) {
        BankSmsParserDialog(
            viewModel = viewModel,
            isPersian = isPersian,
            initialSmsText = pendingBankSmsText,
            onDismiss = { pendingBankSmsText = null }
        )
    }

    // بروزرسانی درون‌برنامه‌ای: اگر سرور نسخه جدیدی اعلام کرده باشد (یا کاربر روی اعلان
    // «بروزرسانی جدید» لمس کرده باشد)، دیالوگ دانلود و نصب نمایش داده می‌شود.
    val availableAppUpdate by viewModel.availableAppUpdate.collectAsStateWithLifecycle()
    var appUpdateDialogDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(initialOpenAppUpdate) {
        if (initialOpenAppUpdate) {
            appUpdateDialogDismissed = false
            viewModel.checkForAppUpdate(notify = false)
        }
    }
    if (availableAppUpdate != null && !appUpdateDialogDismissed) {
        AppUpdateDialog(
            info = availableAppUpdate!!,
            isPersian = isPersian,
            onDismiss = { appUpdateDialogDismissed = true }
        )
    }

    // بروزرسانی PATCH (رفع باگ): بنر متنی خود-حذف‌شونده در استارت اپ — روزی یک‌بار
    val patchUpdateNotice by viewModel.patchUpdateNotice.collectAsStateWithLifecycle()
    if (patchUpdateNotice != null && availableAppUpdate == null) {
        PatchUpdateAutoBanner(
            info = patchUpdateNotice!!,
            isPersian = isPersian,
            onTap = {
                // لمس بنر → دیالوگ کامل دانلود و نصب
                viewModel.availableAppUpdate.value = patchUpdateNotice
                appUpdateDialogDismissed = false
                viewModel.dismissPatchUpdateNotice()
            },
            onDismiss = { viewModel.dismissPatchUpdateNotice() }
        )
    }
    
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isGoogleDriveConnected by viewModel.isGoogleDriveConnected.collectAsStateWithLifecycle(initialValue = false)
    val needsProfileCompletion = isGoogleDriveConnected && settings.clinicName.isBlank()
    
    if (needsProfileCompletion) {
        ProfileCompletionDialog(viewModel = viewModel, onComplete = { })
    }

    if (currentViewingImage != null) {
        FullScreenImageViewer(imageStr = currentViewingImage!!) {
            viewModel.currentViewingImage.value = null
        }
    }

    var showConnectionStatusDialog by remember { mutableStateOf(false) }
    var showClinicAccountDialog by remember { mutableStateOf(false) }

    if (showConnectionStatusDialog) {
        ConnectionStatusDialog(
            viewModel = viewModel,
            onDismiss = { showConnectionStatusDialog = false }
        )
    }

    if (showClinicAccountDialog) {
        ClinicAccountDialog(
            viewModel = viewModel,
            onNavigateToAccount = { navController.navigate("account") },
            onNavigateToSettings = { viewModel.currentTab.value = "settings" },
            onNavigateToExclusiveSettings = { viewModel.currentTab.value = "exclusive_settings" },
            onDismiss = { showClinicAccountDialog = false }
        )
    }
    val neonPalette = LocalNeonPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val layoutDirection = if (isPersian) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        val currentSettings by viewModel.settings.collectAsStateWithLifecycle()
                        if (!currentSettings.logoFileId.isNullOrBlank()) {
                            SmartImage(
                                fileId = currentSettings.logoFileId,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable { showClinicAccountDialog = true }
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable { showClinicAccountDialog = true },
                                contentScale = ContentScale.Crop
                            )
                        }
                            Text(
                                text = viewModel.label("app_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        val isOnline by viewModel.syncEngine.isOnline.collectAsStateWithLifecycle()
                        IconButton(
                            onClick = { showConnectionStatusDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isOnline) androidx.compose.material.icons.Icons.Default.Wifi else androidx.compose.material.icons.Icons.Default.CloudOff,
                                contentDescription = if (isOnline) "Online" else "Offline",
                                tint = if (isOnline) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val activeRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
                        var roleMenuExpanded by remember { mutableStateOf(false) }
                        var showDoctorAuthDialog by remember { mutableStateOf(false) }
                        var pendingTargetRole by remember { mutableStateOf<com.example.feature.auth.UserRole?>(null) }

                        if (showDoctorAuthDialog && pendingTargetRole != null) {
                            com.example.ui.components.DoctorRoleAuthDialog(
                                viewModel = viewModel,
                                targetRole = pendingTargetRole!!,
                                onDismiss = {
                                    showDoctorAuthDialog = false
                                    pendingTargetRole = null
                                },
                                onSuccess = {
                                    showDoctorAuthDialog = false
                                    pendingTargetRole = null
                                }
                            )
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable { roleMenuExpanded = true }
                                    .testTag("topbar_role_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeRole == com.example.feature.auth.UserRole.DOCTOR) Icons.Default.Lock else Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = activeRole.titleFa,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = roleMenuExpanded,
                                onDismissRequest = { roleMenuExpanded = false }
                            ) {
                                com.example.feature.auth.UserRole.entries.forEach { role ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (role == com.example.feature.auth.UserRole.DOCTOR) {
                                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                } else {
                                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                                Text(
                                                    text = role.titleFa,
                                                    fontWeight = if (activeRole == role) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            roleMenuExpanded = false
                                            if (activeRole != role) {
                                                pendingTargetRole = role
                                                showDoctorAuthDialog = true
                                            }
                                        },
                                        trailingIcon = if (activeRole == role) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                        } else null
                                    )
                                }
                            }
                        }
                        // دکمه تنظیمات — کلیک معمولی برای «تنظیمات مطب»
                        // لمس طولانی (۲ ثانیه) برای «تنظیمات اختصاصی نرم‌افزار» (بازگردانی منوی مخفی/اختصاصی)
                        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = { viewModel.currentTab.value = "settings" },
                                    onLongClick = { viewModel.currentTab.value = "exclusive_settings" }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    val dashboardLabel = viewModel.label("dashboard")
                    val patientsLabel = viewModel.label("patients")
                    val appointmentsLabel = viewModel.label("appointments")
                    val moreLabel = if (isPersian) "بخش‌های دیگر" else "More Modules"

                    NavigationBarItem(
                        selected = currentTab == "dashboard",
                        onClick = { viewModel.currentTab.value = "dashboard" },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = dashboardLabel) },
                        label = { Text(dashboardLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    if (viewModel.hasModuleAccess("patients")) {
                        NavigationBarItem(
                            selected = currentTab == "patients",
                            onClick = {
                                viewModel.selectedPatientId.value = null
                                viewModel.currentTab.value = "patients"
                            },
                            icon = { Icon(Icons.Default.ContactPage, contentDescription = patientsLabel) },
                            label = { Text(patientsLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                    if (viewModel.hasModuleAccess("appointments")) {
                        NavigationBarItem(
                            selected = currentTab == "appointments",
                            onClick = { viewModel.currentTab.value = "appointments" },
                            icon = { Icon(Icons.Default.CalendarToday, contentDescription = appointmentsLabel) },
                            label = { Text(appointmentsLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                    NavigationBarItem(
                        selected = currentTab in listOf("vendors", "labs", "maintenance", "expenses", "personal", "more", "templates", "bank", "settings", "exclusive_settings", "financial", "cheques", "reports"),
                        onClick = { viewModel.currentTab.value = "more" },
                        icon = { Icon(Icons.Default.Menu, contentDescription = moreLabel) },
                        label = { Text(moreLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                com.example.ui.navigation.DentalNavGraph(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: DentalViewModel) {
    val activeRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val personalTx by viewModel.personalTransactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val expenses by viewModel.clinicExpenses.collectAsStateWithLifecycle()

    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val currency = viewModel.label("currency")

    // Calculations
    val clinicIncome = allPayments.sumOf { it.amount }
    val personalDraw = personalTx.filter { it.type == "DRAW" }.sumOf { it.amount }
    val personalOtherIncome = personalTx.filter { it.type == "INCOME" }.sumOf { it.amount }
    val personalExpenses = personalTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    
    // Personal balance: Dentist personal income/draws minus personal expenses
    val personalBalance = personalDraw + personalOtherIncome - personalExpenses

    val unclearedCheques = allPayments.filter { it.type == "CHEQUE" && !it.isCleared }.sumOf { it.amount }

    // Advanced Calculations for Visual Gradient Charts
    val totalCost = allTreatments.sumOf { it.cost - it.discount }
    val totalPaid = allPayments.sumOf { it.amount }
    val outstandingDebt = if (totalCost > totalPaid) totalCost - totalPaid else 0L
    val totalExpenses = expenses.sumOf { it.amount }

    val todayAppointmentsCount = remember(appointments) {
        val now = System.currentTimeMillis()
        appointments.count { com.example.util.PersianCalendarHelper.isSameDay(it.date, now) }
    }

    // Quick Patient Add Form State
    var showQuickPatientDialog by remember { mutableStateOf(false) }

    // Quick Expense Form State
    var showQuickExpenseDialog by remember { mutableStateOf(false) }

    // چیدمان کارت‌های داشبورد (درخواست ۱۰) — از settings.dashboardCardOrder
    val dashboardOrderKeys = remember(settings.dashboardCardOrder) {
        val custom = settings.dashboardCardOrder.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (custom.isNotEmpty()) custom else listOf("HERO", "VIEW_SWITCH", "SUMMARY", "CHARTS", "QUICK_ACTIONS", "CALENDAR")
    }
    fun dashboardSectionEnabled(key: String): Boolean = when (key) {
        "SUMMARY" -> settings.showSummaryCard
        "CHARTS" -> settings.showCharts
        "QUICK_ACTIONS" -> settings.showQuickActions
        else -> true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Image Illustration Card (with custom clinic/doctor branding + neon glow)
        item {
            val neonPalette = LocalNeonPalette.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .neonGlow(neonPalette.glow, RoundedCornerShape(16.dp), elevation = 22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(neonPalette.heroBrush())
                    .border(1.dp, neonPalette.glow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Soft radial glow accent behind the circular logo on the LEFT side
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .background(neonPalette.glowRadialBrush(), shape = CircleShape)
                    ) {
                        if (!settings.logoFileId.isNullOrBlank()) {
                            SmartImage(
                                fileId = settings.logoFileId,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon),
                                contentDescription = "Clinic Logo",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (settings.clinicName.isNotBlank()) settings.clinicName else (if (isPersian) "کلینیک دندانپزشکی دیجیتال" else "Digital Dental Clinic"),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPersian) "نرم‌افزار جامع مدیریت مطب دندانپزشکی" else "Complete Dental Practice Management",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // بخش‌های قابل مرتب‌سازی داشبورد — ترتیب از settings.dashboardCardOrder (درخواست ۱۰)
        // (بنر بالا «HERO» همیشه ثابت است)
        dashboardOrderKeys.forEach { key ->
            when (key) {
                "VIEW_SWITCH" -> if (dashboardSectionEnabled("VIEW_SWITCH")) {
                    item {
                        var dashboardViewMode by remember { mutableStateOf(if (activeRole == com.example.feature.auth.UserRole.DOCTOR) "DOCTOR" else "RECEPTIONIST") }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ElevatedFilterChip(
                                    selected = dashboardViewMode == "DOCTOR",
                                    onClick = { dashboardViewMode = "DOCTOR" },
                                    label = { Text(if (isPersian) "🩺 دیدگاه هوشمند پزشک (بالینی)" else "Doctor View", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                                ElevatedFilterChip(
                                    selected = dashboardViewMode == "RECEPTIONIST",
                                    onClick = { dashboardViewMode = "RECEPTIONIST" },
                                    label = { Text(if (isPersian) "📋 دیدگاه منشی و پذیرش" else "Receptionist View", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                "SUMMARY" -> if (dashboardSectionEnabled("SUMMARY")) {
                    item {
                        Text(
                            text = viewModel.label("summary"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = viewModel.label("total_patients"),
                                    value = "${patients.size}",
                                    icon = Icons.Default.People,
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                                StatCard(
                                    title = viewModel.label("today_appointments"),
                                    value = "$todayAppointmentsCount",
                                    icon = Icons.Default.Event,
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = viewModel.label("clinic_income"),
                                    value = "${formatAmount(clinicIncome)} $currency",
                                    icon = Icons.Default.AttachMoney,
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    valueColor = Color(0xFF388E3C)
                                )
                                StatCard(
                                    title = viewModel.label("personal_balance"),
                                    value = "${formatAmount(personalBalance)} $currency",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (unclearedCheques > 0) {
                                StatCard(
                                    title = viewModel.label("uncleared_cheques"),
                                    value = "${formatAmount(unclearedCheques)} $currency",
                                    icon = Icons.Default.Payments,
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    valueColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                "CHARTS" -> if (dashboardSectionEnabled("CHARTS")) {
                    item {
                        ClinicFinancialGradientChart(
                            revenue = clinicIncome,
                            outstanding = outstandingDebt,
                            expenses = totalExpenses,
                            isPersian = isPersian
                        )
                    }
                }

                "QUICK_ACTIONS" -> if (dashboardSectionEnabled("QUICK_ACTIONS")) {
                    item {
                        Text(
                            text = viewModel.label("quick_add"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickActionButton(
                                label = viewModel.label("quick_patient"),
                                icon = Icons.Default.PersonAdd,
                                onClick = { showQuickPatientDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionButton(
                                label = viewModel.label("quick_appointment"),
                                icon = Icons.Default.AddAlarm,
                                onClick = { viewModel.currentTab.value = "appointments" },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionButton(
                                label = viewModel.label("quick_expense"),
                                icon = Icons.Default.MoneyOff,
                                onClick = { showQuickExpenseDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                "CALENDAR" -> if (dashboardSectionEnabled("CALENDAR")) {
                    item {
                        InteractiveAppointmentsCalendar(viewModel = viewModel, isPersian = isPersian)
                    }
                }
            }
        }

        // Quick Paste banking SMS detector on Dashboard!
    }

    // Quick Add Dialogs
    if (showQuickPatientDialog) {
        AddPatientDialog(
            viewModel = viewModel,
            onDismiss = { showQuickPatientDialog = false }
        )
    }

    if (showQuickExpenseDialog) {
        AddExpenseDialog(
            viewModel = viewModel,
            onDismiss = { showQuickExpenseDialog = false }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// 2. PATIENTS FILE MANAGER (DENTAL RECORDS)
// ==========================================
@Composable
fun PatientsScreen(viewModel: DentalViewModel) {
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val selectedPatientId by viewModel.selectedPatientId.collectAsStateWithLifecycle()
    val selectedPatient by viewModel.selectedPatient.collectAsStateWithLifecycle()
    val treatments by viewModel.selectedPatientTreatments.collectAsStateWithLifecycle()
    val payments by viewModel.selectedPatientPayments.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())

    var filterCategory by remember { mutableStateOf("ALL") }

    val filteredPatients = remember(patients, allTreatments, searchQuery, filterCategory) {
        val q = searchQuery.trim()
        val baseList = if (q.isBlank()) {
            patients
        } else {
            patients.filter { p ->
                p.name.contains(q, ignoreCase = true) ||
                p.phone.contains(q) ||
                p.patientCode.contains(q, ignoreCase = true) ||
                p.id.toString() == q ||
                p.medicalHistory.contains(q, ignoreCase = true) ||
                p.referrer.contains(q, ignoreCase = true) ||
                allTreatments.any { t ->
                    t.patientId == p.id && (
                        t.title.contains(q, ignoreCase = true) ||
                        t.selectedTeeth.contains(q) ||
                        t.notes.contains(q, ignoreCase = true)
                    )
                }
            }
        }

        when (filterCategory) {
            "DEBTORS" -> {
                val debtorIds = viewModel.allPayments.value
                    .filter { it.type == "CHEQUE" && it.chequeStatus == "BOUNCED" }
                    .map { it.patientId }.toSet()
                baseList.filter { debtorIds.contains(it.id) }
            }
            "HIGH_RISK" -> {
                baseList.filter { it.medicalAlerts.isNotBlank() && it.medicalAlerts != "بدون مورد" }
            }
            "RECALL" -> {
                val recallSet = viewModel.getPatientRecallCandidates().map { it.id }.toSet()
                baseList.filter { recallSet.contains(it.id) }
            }
            else -> baseList
        }
    }

    if (selectedPatientId != null && selectedPatient != null) {
        // Patient Detailed Medical & Billing View (Interactive split layout)
        PatientDetailsView(
            patient = selectedPatient!!,
            treatments = treatments,
            payments = payments,
            viewModel = viewModel,
            onBack = { viewModel.selectedPatientId.value = null }
        )
    } else {
        // List of Patients Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = viewModel.label("patients"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(viewModel.label("add_new_patient"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(viewModel.label("search_patient")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Smart Directory Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to (if (isPersian) "همه بیماران" else "All"),
                    "DEBTORS" to (if (isPersian) "بدهکاران / معوقه" else "Debtors"),
                    "HIGH_RISK" to (if (isPersian) "هشدار پزشکی" else "High Risk"),
                    "RECALL" to (if (isPersian) "معاینه ۶ ماهه (Recall)" else "6M Recall")
                ).forEach { (cat, label) ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = cat },
                        label = { Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (filteredPatients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.EmptyStateView(
                        title = if (isPersian) "پرونده‌ای یافت نشد" else "No patient records found",
                        subtitle = if (isPersian) "برای ثبت اولین پرونده بیمار، دکمه «افزودن بیمار جدید» را لمس کنید." else "Click 'Add New Patient' to create a patient record.",
                        actionLabel = viewModel.label("add_new_patient"),
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPatients) { patient ->
                        PatientListItem(
                            patient = patient,
                            isPersian = isPersian,
                            onClick = {
                                viewModel.selectedPatientId.value = patient.id
                            },
                            onDelete = {
                                patientToDelete = patient
                            }
                        )
                    }
                }
            }
        }
    }

    if (patientToDelete != null) {
        val pName = getPatientFormattedName(patientToDelete!!.name, patientToDelete!!.gender, isPersian)
        AlertDialog(
            onDismissRequest = { patientToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف پرونده بیمار" else "Confirm Patient Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف کامل پرونده «$pName» و تمامی سوابق درمانی، نوبت‌ها و پرداخت‌های مرتبط اطمینان دارید؟ این عملیات غیرقابل بازگشت است."
                    else "Are you sure you want to completely delete patient file '$pName' and all associated treatments, appointments, and payments? This action cannot be undone.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePatient(patientToDelete!!)
                        patientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف کامل پرونده" else "Delete Patient File", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { patientToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddPatientDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
    }
}

@Composable
fun PatientListItem(
    patient: Patient,
    isPersian: Boolean = true,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isFemale = patient.gender == "زن" || patient.gender == "خانم" || patient.gender.lowercase() == "female"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (!patient.photoFileId.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer else if (isFemale) Color(0xFFFCE4EC) else Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!patient.photoFileId.isNullOrBlank()) {
                        SmartImage(
                            fileId = patient.photoFileId,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isFemale) Icons.Default.Woman else Icons.Default.Man,
                            contentDescription = null,
                            tint = if (isFemale) Color(0xFFC2185B) else Color(0xFF1565C0)
                        )
                    }
                }
                Column {
                    Text(text = getPatientFormattedName(patient.name, patient.gender, isPersian), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = patient.phone, fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
            }
        }
    }
}

@Composable
fun TreatmentStageChecklistCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val allStagesList = remember(treatment.stages) {
        parseStagesList(treatment.stages)
    }
    val completedStagesList = remember(treatment.completedStages) {
        treatment.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val ignoredStagesList = remember(treatment.ignoredStages) {
        treatment.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val resolvedStagesList = remember(completedStagesList, ignoredStagesList) {
        (completedStagesList + ignoredStagesList).distinct()
    }
    val allChecked = allStagesList.isNotEmpty() && resolvedStagesList.containsAll(allStagesList)

    // پاپ‌آپ نسخه دارویی پس از تکمیل هر جلسه جاری — قابل ویرایش و نیازمند تایید پزشک
    // پیش از نمایش، نسخه پیشنهادی در برابر سوابق پزشکی/هشدارها/داروهای مصرفی بیمار بررسی و
    // در صورت وجود تداخل یا منع مصرف اصلاح می‌شود؛ موارد لحاظ‌شده در پانوشت نکات نسخه درج می‌گردد.
    val patientsForRx by viewModel.patients.collectAsStateWithLifecycle()
    var pendingRxState by remember { mutableStateOf<PendingRxPopup?>(null) }

    fun onStageMarkedComplete(stageName: String) {
        viewModel.toggleTreatmentStage(treatment, stageName)
        val patient = patientsForRx.find { it.id == treatment.patientId }
        val baseSuggestion = com.example.data.PrescriptionKnowledgeBase.suggest(treatment.title, stageName)
            ?: com.example.data.DrugPrescription()
        val adjusted = com.example.data.PrescriptionKnowledgeBase.adjustForPatient(
            baseSuggestion,
            patient?.medicalHistory ?: "",
            patient?.medicalAlerts ?: ""
        )
        pendingRxState = PendingRxPopup(treatment = treatment, stageName = stageName, prescription = adjusted, patient = patient)
    }

    if (pendingRxState != null) {
        val state = pendingRxState!!
        PrescriptionApprovalDialog(
            viewModel = viewModel,
            treatment = state.treatment,
            patient = state.patient,
            stageName = state.stageName,
            suggested = state.prescription,
            onDismiss = { pendingRxState = null }
        )
    }

    if (allStagesList.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "مراحل تراپی و درمان:" else "Therapy Stages:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!readOnly) {
                        // Group/Batch Check Action
                        TextButton(
                            onClick = { viewModel.toggleAllTreatmentStages(treatment, !allChecked) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                if (allChecked) Icons.Default.SelectAll else Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (allChecked) (if (isPersian) "خارج کردن همه" else "Uncheck All") else (if (isPersian) "تیک دسته‌ای همه" else "Batch Check All"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${resolvedStagesList.size}/${allStagesList.size} ${if (isPersian) "مرحله (تکمیل/عدم نیاز)" else "resolved"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Stage Progress bar incorporating completed + ignored stages
                val progress = if (allStagesList.isEmpty()) 0f else resolvedStagesList.size.toFloat() / allStagesList.size.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )

                // Single stage items
                allStagesList.forEach { stage ->
                    val isCompleted = completedStagesList.contains(stage)
                    val isIgnored = ignoredStagesList.contains(stage)
                    val isRepeated = stage.contains("تکرار جلسه قبل") || stage.contains("[تکراری]")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!readOnly) Modifier.clickable {
                                    if (isCompleted) viewModel.toggleTreatmentStage(treatment, stage) else onStageMarkedComplete(stage)
                                }
                                else Modifier
                            )
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isIgnored) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Ignored",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$stage (عدم نیاز به این مرحله)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFD32F2F),
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            )
                        } else if (isRepeated) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Repeated",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            )
                        } else if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            )
                        } else {
                            if (!readOnly) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { onStageMarkedComplete(stage) },
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                    .size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/** وضعیت پاپ‌آپ نسخه دارویی در انتظار بازبینی/تایید پزشک برای یک جلسه تکمیل‌شده. */
private data class PendingRxPopup(
    val treatment: Treatment,
    val stageName: String,
    val prescription: com.example.data.DrugPrescription,
    val patient: Patient?
)

/**
 * پاپ‌آپ نسخه دارویی پیشنهادی پس از تکمیل جلسه — کاملاً قابل ویرایش توسط پزشک.
 * نسخه پیشنهادی پیشاپیش در برابر سوابق پزشکی/هشدارها/داروهای مصرفی بیمار بررسی شده و
 * پانوشت «موارد احتیاطی لحاظ‌شده» در بخش نکات درج شده است. پس از تایید پزشک، نسخه در
 * پرونده بیمار ثبت می‌شود و امکان ارسال با پیامک یا واتساپ فراهم است.
 */
class RxItemEditState(rawText: String, globalDur: Int) {
    var title by mutableStateOf("")
    var dosage by mutableStateOf("")
    var timing by mutableStateOf("")
    var instructions by mutableStateOf("")
    var durationDays by mutableStateOf(globalDur.toString())

    init {
        if (rawText.isNotBlank()) {
            val clean = rawText.removePrefix("💊").removePrefix("🧴").removePrefix("➕").trim()
            if (clean.contains(" - ") || clean.contains(";;;")) {
                val sep = if (clean.contains(" - ")) " - " else ";;;"
                val p = clean.split(sep)
                title = p.getOrNull(0)?.trim() ?: ""
                dosage = p.getOrNull(1)?.trim() ?: ""
                timing = p.getOrNull(2)?.trim() ?: ""
                instructions = p.getOrNull(3)?.trim() ?: ""
                durationDays = p.getOrNull(4)?.trim().takeIf { !it.isNullOrBlank() } ?: globalDur.toString()
            } else {
                val parsed = parseRxToColumns(clean, globalDur)
                title = parsed.title
                dosage = parsed.dosage
                timing = parsed.timing
                instructions = parsed.instructions
                durationDays = globalDur.toString()
            }
        }
    }

    fun toRawString(): String {
        if (title.isBlank() && dosage.isBlank() && timing.isBlank() && instructions.isBlank()) return ""
        val dDays = durationDays.ifBlank { "5" }
        return "${title.trim()} - ${dosage.trim()} - ${timing.trim()} - ${instructions.trim()} - ${dDays.trim()}"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RxItemEditorRow(
    label: String, 
    state: RxItemEditState, 
    onDelete: (() -> Unit)? = null,
    templates: List<com.example.data.DrugTemplate> = emptyList(),
    isPersian: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row {
                if (templates.isNotEmpty()) {
                    TextButton(onClick = { expanded = true }, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(if (isPersian) "انتخاب از فایل" else "Templates", fontSize = 10.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        templates.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.title} (${t.dosage})", fontSize = 12.sp) },
                                onClick = {
                                    state.title = t.title
                                    state.dosage = t.dosage
                                    state.timing = t.timing
                                    state.instructions = t.instructions
                                    if (t.defaultDurationDays > 0) state.durationDays = t.defaultDurationDays.toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.title, onValueChange = { state.title = it },
                label = { Text("عنوان (نام و دوز)") },
                placeholder = { Text("مثلاً: استامینوفن (AC) - 500mg") },
                modifier = Modifier.weight(2f), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
            OutlinedTextField(
                value = state.durationDays, onValueChange = { state.durationDays = it },
                label = { Text("طول درمان (روز)") },
                modifier = Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.dosage, onValueChange = { state.dosage = it },
                label = { Text("مقادیر مصرف") },
                placeholder = { Text("مثلاً: هر ۶ ساعت ۱/۲ قرص") },
                modifier = Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
            OutlinedTextField(
                value = state.timing, onValueChange = { state.timing = it },
                label = { Text("زمان مصرف") },
                placeholder = { Text("مثلاً: بعد از نهار / ناشتا") },
                modifier = Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
        }
        OutlinedTextField(
            value = state.instructions, onValueChange = { state.instructions = it },
            label = { Text("دستورات متفرقه") },
            placeholder = { Text("دستورات و نکات تکمیلی مورد نیاز بیمار") },
            modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
        )
    }
}

@Composable
private fun PrescriptionApprovalDialog(
    viewModel: DentalViewModel,
    treatment: Treatment,
    patient: Patient?,
    stageName: String,
    suggested: com.example.data.DrugPrescription,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())

    var durationDaysStr by remember { mutableStateOf(suggested.durationDays.takeIf { it > 0 }?.toString() ?: "5") }
    val globalDur = durationDaysStr.toIntOrNull() ?: 5
    
    val analgesicState = remember { RxItemEditState(suggested.analgesic, globalDur) }
    val antibioticState = remember { RxItemEditState(suggested.antibiotic, globalDur) }
    val mouthwashState = remember { RxItemEditState(suggested.mouthwash, globalDur) }
    
    // Parse otherMeds (might contain multiple separated by |||)
    val otherMedsList = remember { mutableStateListOf<RxItemEditState>() }
    LaunchedEffect(Unit) {
        val parts = suggested.otherMeds.split("|||")
        for (p in parts) {
            if (p.isNotBlank()) {
                otherMedsList.add(RxItemEditState(p, globalDur))
            }
        }
        if (otherMedsList.isEmpty()) {
            otherMedsList.add(RxItemEditState("", globalDur))
        }
    }
    
    var notes by remember { mutableStateOf(suggested.notes) }
    var publicNote by remember { mutableStateOf(suggested.publicNote.ifBlank { "لطفاً داروها را سر ساعت و طبق دستور با آب کافی میل کنید. در صورت بروز هرگونه حساسیت پوستی یا گوارشی، با مطب تماس بگیرید." }) } 
    
    var savedId by remember { mutableStateOf<Long?>(null) }
    var isApproved by remember { mutableStateOf(false) }

    val patientName = patient?.name ?: "بیمار"
    val patientIdOrPhone = if (patient != null && patient.patientCode.isNotBlank()) patient.patientCode else patient?.phone ?: ""
    val dateStr = viewModel.formatDate(System.currentTimeMillis())

    // Live parsing for preview
    val parsedRows = remember(
        analgesicState.title, analgesicState.dosage, analgesicState.timing, analgesicState.instructions, analgesicState.durationDays,
        antibioticState.title, antibioticState.dosage, antibioticState.timing, antibioticState.instructions, antibioticState.durationDays,
        mouthwashState.title, mouthwashState.dosage, mouthwashState.timing, mouthwashState.instructions, mouthwashState.durationDays,
        otherMedsList.size, otherMedsList.map { it.title }, otherMedsList.map { it.dosage }, otherMedsList.map { it.timing }, otherMedsList.map { it.instructions }, otherMedsList.map { it.durationDays },
        globalDur
    ) {
        val list = mutableListOf<com.example.reports.RxColumnRowData>()
        val aRaw = analgesicState.toRawString()
        val abRaw = antibioticState.toRawString()
        val mRaw = mouthwashState.toRawString()
        
        if (aRaw.isNotBlank()) list.add(parseRxToColumns(aRaw, globalDur))
        if (abRaw.isNotBlank()) list.add(parseRxToColumns(abRaw, globalDur))
        if (mRaw.isNotBlank()) list.add(parseRxToColumns(mRaw, globalDur))
        
        for (other in otherMedsList) {
            val oRaw = other.toRawString()
            if (oRaw.isNotBlank()) {
                list.add(parseRxToColumns(oRaw, globalDur))
            }
        }
        
        if (list.isEmpty()) {
            list.add(com.example.reports.RxColumnRowData("داروی عمومی", "۱", "طبق دستور", "روزانه", "-"))
        }
        list
    }

    fun buildShareText(): String {
        return buildStandardRxShareText(
            patientName = patientName,
            patientNationalIdOrPhone = patientIdOrPhone,
            dateStr = dateStr,
            durationDays = globalDur,
            rows = parsedRows,
            notes = publicNote,
            clinicName = settings.clinicName,
            doctorName = settings.doctorName,
            gender = patient?.gender ?: "مرد"
        )
    }

    fun persistAndThen(sms: Boolean = false, wa: Boolean = false, after: () -> Unit = {}) {
        val rx = com.example.data.Prescription(
            id = savedId ?: 0L,
            patientId = treatment.patientId,
            treatmentId = treatment.id,
            treatmentTitle = treatment.title,
            stageName = stageName,
            analgesic = analgesicState.toRawString(),
            antibiotic = antibioticState.toRawString(),
            mouthwash = mouthwashState.toRawString(),
            otherMeds = otherMedsList.map { it.toRawString() }.filter { it.isNotBlank() }.joinToString("|||"),
            notes = notes,
            publicNote = publicNote,
            durationDays = globalDur,
            doctorApproved = true,
            sentSms = sms,
            sentWhatsApp = wa
        )
        viewModel.savePrescription(rx) { id ->
            savedId = id
            isApproved = true
            after()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("نسخه دارویی و دستورات — $stageName", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("بیمار: $patientName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (patientIdOrPhone.isNotBlank()) {
                            Text("شماره پرونده: $patientIdOrPhone", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text("۱. ویرایش نسخه:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = durationDaysStr, onValueChange = { durationDaysStr = it },
                    label = { Text("طول درمان پیش‌فرض (روز)") },
                    modifier = Modifier.fillMaxWidth(0.5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                RxItemEditorRow("💊 مسکن", analgesicState)
                RxItemEditorRow("💊 آنتی‌بیوتیک", antibioticState)
                RxItemEditorRow("🧴 دهان‌شویه", mouthwashState)
                
                otherMedsList.forEachIndexed { index, state ->
                    RxItemEditorRow("➕ سایر داروها (${index + 1})", state, onDelete = {
                        if (otherMedsList.size > 1) {
                            otherMedsList.removeAt(index)
                        } else {
                            state.title = ""
                            state.dosage = ""
                            state.timing = ""
                            state.instructions = ""
                        }
                    })
                }
                
                TextButton(onClick = { otherMedsList.add(RxItemEditState("", globalDur)) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("افزودن ردیف داروی جدید")
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("ملاحظات پزشکی (منع مصرف / داخلی مطب - ارسال نمی‌شود)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
                
                OutlinedTextField(
                    value = publicNote, onValueChange = { publicNote = it },
                    label = { Text("یادداشت برای بیمار (در نسخه ارسالی درج می‌شود)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                PrescriptionLiveTablePreview(rows = parsedRows, notes = publicNote)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("۲. تایید نسخه توسط پزشک:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                if (!isApproved) {
                    Button(
                        onClick = { persistAndThen { } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("✓ تایید نهایی و ثبت نسخه توسط پزشک", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("✅ نسخه توسط پزشک تایید و در پرونده بیمار ثبت شد.", fontSize = 12.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("۳. ارسال به بیمار (پس از تایید پزشک):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isApproved) MaterialTheme.colorScheme.primary else Color.Gray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (patient?.phone.isNullOrBlank()) {
                                android.widget.Toast.makeText(context, "شماره تلفن بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                persistAndThen(sms = true) {
                                    sendSmsPrefilled(context, patient!!.phone, buildShareText())
                                }
                            }
                        },
                        enabled = isApproved,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ارسال پیامک", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (patient?.phone.isNullOrBlank()) {
                                android.widget.Toast.makeText(context, "شماره تلفن بیمار ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                persistAndThen(wa = true) {
                                    val cardFile = com.example.reports.generateStandardPrescriptionCardImage(
                                        context = context,
                                        patientName = honorificPatientName(patientName, patient?.gender ?: ""),
                                        patientNationalIdOrPhone = patientIdOrPhone,
                                        dateStr = dateStr,
                                        rows = parsedRows,
                                        notes = publicNote,
                                        clinicName = settings.clinicName,
                                        doctorName = settings.doctorName
                                    )
                                    shareImageToWhatsApp(context, cardFile, patient!!.phone)
                                }
                            }
                        },
                        enabled = isApproved,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ارسال واتساپ", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (isApproved) {
                Button(onClick = onDismiss) {
                    Text("اتمام و بستن")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isApproved) "انصراف/بستن" else "بستن بدون ثبت") }
        }
    )
}

@Composable
fun PatientDetailsView(
    patient: Patient,
    treatments: List<Treatment>,
    payments: List<Payment>,
    viewModel: DentalViewModel,
    onBack: () -> Unit
) {
    val currency = viewModel.label("currency")
    val totalCost = treatments.sumOf { it.cost - it.discount }
    val totalPaid = payments.sumOf { it.amount }
    val netBalance = totalCost - totalPaid
    val allStaffMembersForDetails by viewModel.allStaffMembers.collectAsStateWithLifecycle()

    var currentSubTab by remember { mutableStateOf("treatments") } // treatments, payments, installments, documents
    var showTreatmentForm by remember { mutableStateOf(false) }
    var showPaymentForm by remember { mutableStateOf(false) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showEditPatientDialog by remember { mutableStateOf(false) }
    var treatmentToDelete by remember { mutableStateOf<Treatment?>(null) }
    var treatmentToEdit by remember { mutableStateOf<Treatment?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var editingRx by remember { mutableStateOf<Prescription?>(null) }

    val allInstallments by viewModel.allInstallments.collectAsStateWithLifecycle()
    val patientInstallments = remember(allInstallments, patient.id) { allInstallments.filter { it.patientId == patient.id } }

    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val avatarEditLauncher = rememberPhotoEditLauncher(PhotoAssetKind.PATIENT_AVATAR) { localPath ->
        viewModel.updatePatientPhoto(patient, localPath)
    }
    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { avatarEditLauncher(it) }
    }
    val launchAvatarCamera = rememberEditableCameraLauncher(context) { uri ->
        avatarEditLauncher(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Back Header with Interactive Avatar & Patient Edit Card
        var showAvatarPicker by remember { mutableStateOf(false) }
        val isFemale = patient.gender == "زن" || patient.gender == "خانم" || patient.gender.lowercase() == "female"
        val avatarBg = if (!patient.photoFileId.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer
                       else if (isFemale) Color(0xFFFCE4EC) else Color(0xFFE3F2FD)
        val avatarBorder = if (!patient.photoFileId.isNullOrBlank()) MaterialTheme.colorScheme.primary
                         else if (isFemale) Color(0xFFE91E63) else Color(0xFF1976D2)
        val avatarTint = if (isFemale) Color(0xFFC2185B) else Color(0xFF1565C0)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                // Interactive Patient Photo Avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(avatarBg)
                        .border(2.dp, avatarBorder, CircleShape)
                        .clickable { showAvatarPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (!patient.photoFileId.isNullOrBlank()) {
                        SmartImage(
                            fileId = patient.photoFileId,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isFemale) Icons.Default.Woman else Icons.Default.Man,
                            contentDescription = null,
                            tint = avatarTint,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Main Patient Header Column taking full weight(1f)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = getPatientFormattedName(patient.name, patient.gender, isPersian),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (patient.patientCode.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = patient.patientCode,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (patient.age > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${patient.age} ${if (isPersian) "ساله" else "yrs"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (isPersian) "تلفن: " else "Phone: "}${patient.phone.ifBlank { "ثبت نشده" }}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (patient.birthDate.isNotBlank()) {
                            Text(
                                text = "${if (isPersian) "تولد: " else "Born: "}${patient.birthDate}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    com.example.ui.components.AssistantNamesRow(
                        assistantIds = patient.assistantIds,
                        allStaff = allStaffMembersForDetails,
                        isPersian = isPersian
                    )
                }
            }

            // ردیف مستقل آیکون‌های عملیاتی — زیر هدر برگ بیمار (تماس/پیامک/ادیت/رفرش)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (patient.phone.isNotBlank() && patient.phone != "ثبت نشده") {
                    IconButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = if (isPersian) "تماس" else "Call",
                            tint = ClearColor
                        )
                    }
                    IconButton(onClick = {
                        sendSmsPrefilled(context, patient.phone, "سلام ${patient.name} عزیز، از مطب تماس می‌گیریم.")
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = if (isPersian) "پیامک" else "SMS",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Refresh — force-reload this patient's data from the database
                IconButton(onClick = {
                    viewModel.refreshSelectedPatient()
                    android.widget.Toast.makeText(
                        context,
                        if (isPersian) "به‌روزرسانی شد" else "Refreshed",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isPersian) "به‌روزرسانی" else "Refresh",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Edit Patient Pencil Button
                IconButton(onClick = { showEditPatientDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Patient",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }

        if (showEditPatientDialog) {
            EditPatientDialog(
                patient = patient,
                viewModel = viewModel,
                onDismiss = { showEditPatientDialog = false }
            )
        }

            if (showAvatarPicker) {
                AlertDialog(
                    onDismissRequest = { showAvatarPicker = false },
                    title = { Text(if (isPersian) "بارگذاری تصویر بیمار" else "Upload Patient Photo", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (isPersian) "منبع عکس بیمار را انتخاب کنید:" else "Select patient photo source:")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { launchAvatarCamera(); showAvatarPicker = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPersian) "دوربین" else "Camera", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { avatarGalleryLauncher.launch("image/*"); showAvatarPicker = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAvatarPicker = false }) {
                            Text(if (isPersian) "انصراف" else "Cancel")
                        }
                    }
                )
            }

        // Medical Warnings Badges, Risk Analysis & Self-Declaration Viewer Card
        var showSelfDecDetailsDialog by remember { mutableStateOf(false) }
        var showRiskGuidelinesDialog by remember { mutableStateOf(false) }
        var selectedProtocolTitleForPatientDetail by remember { mutableStateOf<String?>(null) }

        val hasMedicalAlerts = patient.medicalAlerts.isNotBlank() && patient.medicalAlerts != "بدون مورد"
        val hasRiskySelfDeclaration = remember(patient.selfDeclarationJson) {
            try {
                if (patient.selfDeclarationJson.isNotBlank()) {
                    val obj = org.json.JSONObject(patient.selfDeclarationJson)
                    var hasRisk = false
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (obj.getString(key) == "بله") hasRisk = true
                    }
                    hasRisk
                } else false
            } catch(e: Exception) { false }
        }
        val hasSelfDeclaration = patient.selfDeclarationJson.isNotBlank()
        
        val isRedRisk = hasMedicalAlerts || hasRiskySelfDeclaration

        // Only show Prominent Clinical Risks Card if the patient actually has medical alerts or a self-declaration on file
        if (hasMedicalAlerts || hasSelfDeclaration) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRedRisk) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.5.dp, if (isRedRisk) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRedRisk) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (isPersian) "🔴 موارد خطر، احتیاط‌ها و ممنوعیت‌های درمانی" else "🔴 Risk Alerts, Precautions & Contraindications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { showRiskGuidelinesDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(if (isPersian) "دستورالعمل بالینی" else "Clinical Rules", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (patient.medicalAlerts.isNotBlank() && patient.medicalAlerts != "بدون مورد") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            patient.medicalAlerts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { alert ->
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = alert,
                                        color = MaterialTheme.colorScheme.onError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (patient.selfDeclarationJson.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (isPersian) "فرم خوداظهاری سلامت بیمار تکمیل شده" else "Health Declaration On File",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = { showSelfDecDetailsDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (isPersian) "مشاهده پاسخ‌ها" else "View Answers", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Detailed Clinical Risk & Contraindications Modal Dialog
        if (showRiskGuidelinesDialog) {
            val alerts = patient.medicalAlerts
            // سایز فونت این دیالوگ به‌صورت خودکار از همان مقدار ذخیره‌شده در صفحات
            // شرح بالینی (دستورالعمل بالینی) گرفته می‌شود — نسبت‌ها حفظ می‌شوند
            val protoBase = settings.clinicalFontSizePt
            val protocolTitleFontSize = (protoBase + 4).sp
            val protocolSectionFontSize = (protoBase + 1).sp
            val protocolBodyFontSize = protoBase.sp
            val protocolSmallFontSize = (protoBase - 1).coerceAtLeast(7).sp
            // این دیالوگ هم مانند صفحه دستورالعمل بالینی از لغزنده سراسری فونت مستثنی است
            val riskDialogDensity = androidx.compose.ui.unit.Density(density = LocalDensity.current.density, fontScale = 1f)

            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides riskDialogDensity) {
            AlertDialog(
                onDismissRequest = { showRiskGuidelinesDialog = false },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = if (isPersian) "دستورالعمل بالینی و ممنوعیت‌های درمانی" else "Clinical Guidelines & Contraindications",
                                fontWeight = FontWeight.Bold,
                                fontSize = protocolTitleFontSize,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = if (isPersian) "بر اساس آخرین گایدلاین‌های معتبر دانشگاهی (ADA / AHA)" else "Based on ADA / AHA Evidence-Based Clinical Protocols",
                            fontSize = protocolSmallFontSize,
                            color = Color.Gray
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
                        // Section 1: Bleeding / Anticoagulants / Bleeding History
                        if (alerts.contains("انعقاد خون") || alerts.contains("خونریزی شدید")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🩸 اختلالات انعقادی / مصرف رقیق‌کننده خون (وارفارین/آسپرین):" else "🩸 Bleeding / Anticoagulant Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.error)
                                    Text(if (isPersian) "• احتیاط: سنجش آزمایشگاهی INR قبل جراحی یا کشیدن دندان (محدوده مجاز INR ≤ ۲.۵ تا ۳). استفاده از پانسمان بندآورنده خونریزی (ژلفوم، گاز ترانگزامیک اسید، بخیه متابولیک محکم)." else "• Precaution: Check INR (target ≤ 2.5-3). Use hemostatic agents, Tranexamic acid & tight sutures.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: قطع خودسرانه وارفارین بدون دستور پزشک معالج ممنوع است. تجویز داروهای NSAID (ایبوپروفن، نپروکسن، مفنامیک اسید) به علت افزایش خطر خونریزی معده اکیداً ممنوع بوده و فقط استامینوفن مجاز است." else "❌ Contraindication: Do NOT stop Warfarin independently. NSAIDs (Ibuprofen) are strictly contra-indicated; prescribe Acetaminophen.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 2: Penicillin / Anesthetic Allergies
                        if (alerts.contains("حساسیت دارویی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "⚠️ حساسیت دارویی / پنی‌سیلین یا بی حسی:" else "⚠️ Drug & Anesthetic Allergy Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.error)
                                    Text(if (isPersian) "• احتیاط (به‌روزرسانی‌شده طبق بیانیه علمی ۲۰۲۱ انجمن قلب آمریکا AHA): کلیندامایسین دیگر به‌دلیل خطر عفونت کلستریدیوم دیفیسیل توصیه نمی‌شود. در حساسیت به پنی‌سیلین، از سفالکسین (در صورت نبود واکنش شدید نوع آنافیلاکسی) یا آزیترومایسین/کلاریترومایسین یا داکسی‌سایکلین استفاده شود. در صورت حساسیت به بی‌حسی‌های آمیدی یا مواد نگهدارنده سولفیت، از مپیواکائین ۳٪ بدون اپی‌نفرین استفاده شود." else "• Precaution (updated per AHA 2021 Scientific Statement): Clindamycin is no longer recommended due to C. difficile risk. For penicillin allergy use Cephalexin (if no history of anaphylaxis) or Azithromycin/Clarithromycin or Doxycycline. Use Mepivacaine 3% without Epinephrine for sulfite/amide allergies.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: تجویز تمام مشتقات پنی‌سیلین (آموکسی‌سیلین، آمی‌سیلین، کوآموکسی‌کلاو) و سفالوسپورین‌ها در بیماران با آنفیلاکسی سابقه اکیداً ممنوع است." else "❌ Contraindication: All Penicillins (Amoxicillin, Augmentin) and Cephalosporins are strictly forbidden.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3: Cardiovascular / Hypertension / Diabetes / Epilepsy (تطبیق دقیق - فقط فشار خون بالا، نه پایین)
                        if (alerts.contains("بیماری قلبی") || alerts.contains("فشار خون بالا") || alerts.contains("دیابت") || alerts.contains("صرع")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫀 بیماران قلبی-عروقی، فشارخون بالا، دیابت و صرع:" else "🫀 Cardiovascular, Hypertension & Diabetes Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: کنترل فشار خون قبل کار (فشار بالای ۱۶۰/۱۰۰ نیاز به استراحت و ارجاع). محدودیت اپی‌نفرین به حداکثر ۲ کارپول ۱/۱۰۰۰۰۰. تنظیم وقت صبحگاهی برای بیماران دیابتی پس از مصرف صبحانه و انسولین (پرهیز از افت قندخون حین درمان). در دریچه مصنوعی قلب یا سابقه اندوکاردیت، پروفیلاکسی آنتی‌بیوتیکی طبق AHA 2021 فقط برای پرخطرترین گروه لازم است." else "• Precaution: Check blood pressure (refer if >160/100). Limit Epinephrine to max 2 cartridges 1:100k. Schedule diabetic patients in morning after meal & insulin (avoid hypoglycemia mid-treatment). For prosthetic valves/endocarditis history, AHA 2021 prophylaxis applies only to highest-risk group.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: استفاده از نخ پس‌کشنده لثه (Retraction cord) آغشته به اپی‌نفرین غلیظ اکیداً ممنوع است." else "❌ Contraindication: Retraction cords impregnated with concentrated Epinephrine are forbidden.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3b: Hypotension / Vasovagal Syncope during Injection (NEW)
                        if (alerts.contains("فشار خون پایین")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "💧 فشار خون پایین / سنکوپ وازوواگال حین تزریق:" else "💧 Hypotension / Vasovagal Syncope Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: بیمار را در وضعیت نیمه‌خوابیده (Semi-supine) و نه کاملاً عمودی قرار دهید؛ پیش از تزریق حتماً آسپیره کنید و بی‌حسی را آهسته تزریق نمایید؛ از ناشتا بودن طولانی بیمار پیش از نوبت پرهیز شود. علائم هشدار: رنگ‌پریدگی، تعریق سرد، سرگیجه، تهوع." else "• Precaution: Position patient semi-supine (not fully upright) before injection; always aspirate before injecting and inject slowly; ensure patient hasn't fasted for long before the appointment. Warning signs: pallor, cold sweat, dizziness, nausea.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "🚑 در صورت بروز غش/سنکوپ حین کار: فوراً درمان را متوقف کنید، بیمار را در وضعیت خوابیده با پاهای بالاتر از سطح قلب (Trendelenburg) قرار دهید، لباس تنگ را شل کنید، علائم حیاتی را پایش و در صورت نیاز اکسیژن تجویز کنید؛ اغلب موارد وازوواگال طی ۱-۲ دقیقه برطرف می‌شود. از بلندشدن ناگهانی بیمار پس از درمان خودداری شود." else "🚑 If syncope occurs: stop treatment immediately, place patient supine with legs elevated (Trendelenburg), loosen tight clothing, monitor vitals, give oxygen if needed; most vasovagal episodes resolve within 1-2 minutes. Avoid sudden postural changes after treatment.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3c: Asthma / Chronic Respiratory Disease (NEW)
                        if (alerts.contains("آسم")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫁 آسم و بیماری تنفسی مزمن:" else "🫁 Asthma / Chronic Respiratory Disease:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: از بیمار بخواهید اسپری برونکودیلاتور اورژانسی خود را همراه بیاورد؛ از مواد بودار/اسپری‌های قوی که محرک برونکواسپاسم هستند پرهیز شود؛ در آسم شدید، استفاده از رابردم با احتیاط بیشتری انجام شود." else "• Precaution: Have patient bring rescue inhaler to appointment; avoid strong-odor materials/aerosols that trigger bronchospasm; use rubber dam cautiously in severe asthma.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در بیماران با آسم حساس به آسپرین (Aspirin-Exacerbated Respiratory Disease)، تجویز آسپرین و سایر NSAIDها ممنوع است." else "❌ Contraindication: Avoid Aspirin/NSAIDs in patients with Aspirin-Exacerbated Respiratory Disease.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3d: Kidney Disease (NEW)
                        if (alerts.contains("بیماری کلیوی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫘 بیماری کلیوی:" else "🫘 Kidney Disease:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: تنظیم دوز داروهای دفع‌شونده از کلیه؛ در بیماران دیالیزی، درمان روز بعد از دیالیز انجام شود و از بازوی دارای فیستول برای فشارسنجی استفاده نشود." else "• Precaution: Adjust doses of renally-cleared drugs; for dialysis patients, treat the day after dialysis and avoid the fistula arm for BP measurement.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: NSAIDها به‌دلیل سمیت کلیوی و تتراسایکلین‌ها در نارسایی کلیوی ممنوع هستند." else "❌ Contraindication: NSAIDs (nephrotoxic) and Tetracyclines are contraindicated in renal impairment.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3e: Liver Disease / Hepatitis (NEW)
                        if (alerts.contains("بیماری کبدی")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🫄 بیماری کبدی / هپاتیت:" else "🫄 Liver Disease / Hepatitis:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: کبد محل تولید فاکتورهای انعقادی است؛ احتمال خونریزی بیشتر در نظر گرفته شود. رعایت کامل پروتکل کنترل عفونت به‌دلیل احتمال انتقال از راه خون (هپاتیت B/C)." else "• Precaution: Liver produces clotting factors; higher bleeding risk should be considered. Follow full infection-control protocol given bloodborne transmission risk (Hep B/C).", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در آسیب کبدی قابل‌توجه، دوز استامینوفن باید محدود شود و از داروهایی با متابولیسم عمده کبدی تا حد امکان پرهیز شود." else "❌ Contraindication: Limit Acetaminophen dose in significant liver impairment; avoid drugs with major hepatic metabolism where possible.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3f: Thyroid Disorder (NEW)
                        if (alerts.contains("اختلال تیروئید")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🦋 اختلال تیروئید:" else "🦋 Thyroid Disorder:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: پیش از درمان الکتیو، کنترل بودن وضعیت تیروئید (یوتیروئید) تایید شود." else "• Precaution: Confirm thyroid status is well-controlled (euthyroid) before elective treatment.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: در پرکاری تیروئید کنترل‌نشده، مصرف اپی‌نفرین باید به حداقل برسد (خطر آریتمی/توفان تیروئیدی)." else "❌ Contraindication: Minimize Epinephrine in uncontrolled hyperthyroidism (risk of arrhythmia/thyroid storm).", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3g: Osteoporosis / Bisphosphonate (MRONJ Risk) (NEW)
                        if (alerts.contains("استئوپروز")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🦴 استئوپروز / مصرف بیس‌فسفونات (خطر MRONJ):" else "🦴 Osteoporosis / Bisphosphonate (MRONJ Risk):", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط (طبق راهنمای انجمن دندانپزشکی آمریکا ADA): طبق شواهد فعلی، توقف خودسرانه یا دوره‌ای دارو («تعطیلی دارویی») به‌طور روتین توصیه نمی‌شود و تصمیم باید با هماهنگی پزشک/تیم انکولوژی تجویزکننده گرفته شود. حتی‌الامکان از اقدامات تهاجمی استخوانی غیرضروری پرهیز و بهداشت دهان به‌شدت رعایت شود؛ خطر در بیس‌فسفونات‌های وریدی (مصارف سرطانی) بسیار بیشتر از خوراکی (مصرف استئوپروز) است." else "• Precaution (per ADA guidance): Current evidence does not routinely support a unilateral 'drug holiday'; decisions should be coordinated with the prescribing physician/oncology team. Minimize unnecessary invasive bone procedures and maintain strict oral hygiene; risk is markedly higher with IV bisphosphonates (cancer use) than oral (osteoporosis use).", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: انجام جراحی استخوانی الکتیو بدون هماهنگی پزشکی در بیماران پرخطر (بیس‌فسفونات وریدی/سرطانی) توصیه نمی‌شود." else "❌ Contraindication: Elective invasive bone surgery without medical clearance in high-risk (IV bisphosphonate/cancer) patients should be deferred.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 3h: Cancer / Chemotherapy / Radiotherapy (NEW)
                        if (alerts.contains("سرطان")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🎗️ سرطان / سابقه شیمی‌درمانی یا پرتودرمانی:" else "🎗️ Cancer / Chemotherapy / Radiotherapy:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: پیش از اقدامات تهاجمی حین شیمی‌درمانی فعال، بررسی شمارش خون (خطر نوتروپنی/ترومبوسیتوپنی) و هماهنگی با آنکولوژیست ضروری است. در سابقه پرتودرمانی سر و گردن، خطر استئورادیونکروز وجود دارد؛ از کشیدن دندان غیرضروری در استخوان تحت تابش پرهیز و مشاوره تخصصی گرفته شود." else "• Precaution: Check blood counts (neutropenia/thrombocytopenia risk) before invasive procedures during active chemo, coordinate with oncologist. History of head/neck radiotherapy carries osteoradionecrosis risk; avoid unnecessary extractions in irradiated bone and seek specialist consultation.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: اقدامات تهاجمی الکتیو در دوره نوتروپنی/ترومبوسیتوپنی فعال بدون هماهنگی پزشکی ممنوع است." else "❌ Contraindication: Elective invasive procedures during active neutropenia/thrombocytopenia without medical clearance.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 4: Pregnancy / Lactation
                        if (alerts.contains("بارداری")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🤰 بارداری و شیردهی:" else "🤰 Pregnancy & Lactation Protocol:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.tertiary)
                                    Text(if (isPersian) "• احتیاط: زمان طلایی درمان‌های روتین، سه ماهه دوم بارداری است. رعایت اصل ALARA و استفاده حتمی از پیش‌بند سربی و یقه تیرویید هنگام رادیوگرافی. بی‌حسی انتخابی لیدوکائین ۲٪ با اپی‌نفرین (رده B)." else "• Precaution: Safe window is 2nd trimester. Use lead apron & thyroid collar for X-rays. Lidocaine 2% with 1:100k epi is Class B safe.", fontSize = protocolBodyFontSize)
                                    Text(if (isPersian) "❌ ممنوعیت: تجویز داروهای رده C و D (تتراسایکلین، بنزودیازپین‌ها، NSAIDها در ۳ ماهه سوم) اکیداً ممنوع است." else "❌ Contraindication: Category C/D drugs (Tetracyclines, NSAIDs in 3rd trimester, Benzodiazepines) strictly prohibited.", fontSize = protocolBodyFontSize, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Section 5: Smoking (NEW)
                        if (alerts.contains("دخانیات")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🚬 مصرف دخانیات:" else "🚬 Smoking:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: خطر بیشتر تاخیر در ترمیم زخم، خشکی سوکت پس از کشیدن دندان، پیشرفت بیماری پریودنتال و شکست ایمپلنت. توصیه به ترک یا کاهش مصرف حداقل ۲۴-۴۸ ساعت پیش و پس از جراحی." else "• Precaution: Higher risk of delayed healing, dry socket after extraction, periodontal progression, and implant failure. Recommend cessation/reduction at least 24-48h before and after surgery.", fontSize = protocolBodyFontSize)
                                }
                            }
                        }

                        // Section 6: Recent Surgery / Hospitalization (NEW)
                        if (alerts.contains("جراحی یا بستری اخیر")) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isPersian) "🏥 سابقه جراحی یا بستری اخیر:" else "🏥 Recent Surgery / Hospitalization:", fontWeight = FontWeight.Bold, fontSize = protocolSectionFontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isPersian) "• احتیاط: تاییدیه پزشکی جهت انجام درمان‌های الکتیو دندانپزشکی از پزشک معالج اخذ شود؛ توجه به داروهای ضدانعقاد باقی‌مانده، ضعف ایمنی موقت یا خطر عفونت مرتبط با نوع جراحی اخیر." else "• Precaution: Obtain medical clearance from treating physician for elective dental treatment; consider residual anticoagulation, temporary immunosuppression, or infection risk related to the recent surgery.", fontSize = protocolBodyFontSize)
                                }
                            }
                        }

                        // General Clinical Protocol Notice
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "توصیه می‌شود قبل از شروع درمان‌های جراحی، پرینت فرم خوداظهاری توسط بیمار امضا گردد." else "Recommend obtaining patient signed printed declaration prior to surgical procedures.",
                                    fontSize = protocolSmallFontSize,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRiskGuidelinesDialog = false }) {
                        Text(if (isPersian) "متوجه شدم و بستن" else "Understood & Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
            }
        }

        if (showSelfDecDetailsDialog) {
            val selfDecAnswers = remember(patient.selfDeclarationJson) {
                try {
                    val jsonObj = org.json.JSONObject(patient.selfDeclarationJson)
                    val qLabels = listOf(
                        "bleeding" to "1. سابقه خونریزی شدید پس از کشیدن دندان یا جراحی؟",
                        "allergy" to "2. حساسیت به بی‌حسی دندانپزشکی یا پنی‌سیلین؟",
                        "meds" to "3. مصرف داروهای رقیق‌کننده خون (مانند وارفارین/آسپرین)؟",
                        "chronic" to "4. سابقه بیماری قلبی، فشارخون، دیابت یا صرع؟",
                        "pregnant" to "5. بارداری یا شیردهی (ویژه بانوان)؟",
                        "surgery" to "6. سابقه جراحی یا بستری اخیر در بیمارستان؟"
                    )
                    qLabels.map { (key, qText) ->
                        qText to jsonObj.optString(key, "خیر")
                    }
                } catch(e: Exception) {
                    emptyList()
                }
            }

            AlertDialog(
                onDismissRequest = { showSelfDecDetailsDialog = false },
                title = {
                    Text(
                        text = if (isPersian) "فرم خوداظهاری سلامت بیمار: ${patient.name}" else "Patient Health Declaration: ${patient.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selfDecAnswers.isNotEmpty()) {
                            selfDecAnswers.forEach { (qText, ans) ->
                                val ansColor = if (ans == "بله") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(text = qText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "پاسخ: $ans",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ansColor
                                    )
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        } else {
                            Text(text = patient.selfDeclarationJson, fontSize = 12.sp)
                        }

                        // کادر ثبت و نمایش امضای لمسی دیجیتال بیمار
                        if (!patient.signatureFileId.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "امضا و اثر انگشت الکترونیکی بیمار جهت تأیید صحت خوداظهاری پزشکی و درمان",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SmartImage(
                                            fileId = patient.signatureFileId,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelfDecDetailsDialog = false }) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            )
        }

        // Financial Health Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    netBalance > 0 -> MaterialTheme.colorScheme.errorContainer
                    netBalance < 0 -> Color(0xFFE8F5E9)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = viewModel.label("financial_balance"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatAmount(kotlin.math.abs(netBalance))} $currency",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = when {
                            netBalance > 0 -> DebtColor
                            netBalance < 0 -> ClearColor
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Text(
                        text = when {
                            netBalance > 0 -> viewModel.label("balance_debtor")
                            netBalance < 0 -> viewModel.label("balance_creditor")
                            else -> viewModel.label("balance_settled")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            netBalance > 0 -> DebtColor
                            netBalance < 0 -> ClearColor
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = viewModel.label("total_treatments"), fontSize = 11.sp, color = Color.Gray)
                        Text(text = "${formatAmount(totalCost)} $currency", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = viewModel.label("total_payments"), fontSize = 11.sp, color = Color.Gray)
                        Text(text = "${formatAmount(totalPaid)} $currency", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Send-to-Patient Actions: Statement & Treatment Plan (WhatsApp = image, SMS = short text)
        PatientSendActionsRow(
            patient = patient,
            treatments = treatments,
            payments = payments,
            viewModel = viewModel,
            isPersian = isPersian
        )

        // Segmented Tabs: Treatment / Payments / Installments / Documents
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { currentSubTab = "treatments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "treatments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "treatments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(viewModel.label("treatments_tab"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "payments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "payments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "payments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(viewModel.label("payments_tab"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "installments" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "installments") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "installments") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(if (isPersian) "اقساط و بدهی" else "Installments", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "documents" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "documents") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "documents") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(if (isPersian) "مدارک و امضا" else "Docs & Sign", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { currentSubTab = "prescriptions" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentSubTab == "prescriptions") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (currentSubTab == "prescriptions") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(if (isPersian) "نسخه پزشکی" else "Prescriptions", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Subtab section title with toggle action
        val titleText = when (currentSubTab) {
            "treatments" -> viewModel.label("treatments_tab")
            "installments" -> if (isPersian) "اقساط و بدهی بیمار" else "Installments & Debts"
            "documents" -> if (isPersian) "رادیوگرافی، مدارک و رضایت‌نامه" else "Docs & Signatures"
            "prescriptions" -> if (isPersian) "نسخه‌های دارویی" else "Prescriptions"
            else -> viewModel.label("payments_tab")
        }
        val isFormOpen = if (currentSubTab == "treatments") showTreatmentForm else showPaymentForm

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = titleText, fontWeight = FontWeight.Bold)
            if (currentSubTab == "treatments" || currentSubTab == "payments") {
                IconButton(onClick = {
                    if (currentSubTab == "treatments") {
                        showTreatmentForm = !showTreatmentForm
                    } else {
                        showPaymentForm = !showPaymentForm
                    }
                }) {
                    Icon(
                        imageVector = if (isFormOpen) Icons.Default.Close else Icons.Default.AddCircle,
                        contentDescription = "Toggle Form",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (currentSubTab == "installments") {
                Button(
                    onClick = { showAddInstallmentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "ثبت قسط جدید" else "Add Installment", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Render Active List or Forms
        if (currentSubTab == "treatments") {
            if (showTreatmentForm) {
                AddTreatmentForm(
                    patient = patient,
                    viewModel = viewModel,
                    onSaved = { showTreatmentForm = false }
                )
            } else {
                var treatmentStatusFilter by remember { mutableStateOf("ALL") }
                var treatmentCategoryFilter by remember { mutableStateOf<String?>(null) }
                var treatmentSearchQuery by remember { mutableStateOf("") }

                if (treatments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPersian) "درمانی ثبت نشده" else "No treatments recorded", color = Color.Gray)
                    }
                } else {
                    // Treatment Plan Filters & Search Row
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Search field
                        OutlinedTextField(
                            value = treatmentSearchQuery,
                            onValueChange = { treatmentSearchQuery = it },
                            placeholder = { Text(if (isPersian) "جستجو در عنوان طرح درمان یا دندان..." else "Search treatment or tooth...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (treatmentSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { treatmentSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Status Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val statusFilterOptions = listOf(
                                "ALL" to (if (isPersian) "همه (${treatments.size})" else "All (${treatments.size})"),
                                "PLANNED" to (if (isPersian) "برنامه‌ریزی شده (${treatments.count { it.status != "IN_PROGRESS" && it.status != "COMPLETED" && it.status != "تمام شده" && it.status != "تکمیل شده" }})" else "Planned"),
                                "IN_PROGRESS" to (if (isPersian) "در حال انجام (${treatments.count { it.status == "IN_PROGRESS" || it.status == "در حال انجام" }})" else "In Progress"),
                                "COMPLETED" to (if (isPersian) "تکمیل شده (${treatments.count { it.status == "COMPLETED" || it.status == "تمام شده" || it.status == "تکمیل شده" }})" else "Completed")
                            )
                            statusFilterOptions.forEach { (stKey, stLabel) ->
                                FilterChip(
                                    selected = treatmentStatusFilter == stKey,
                                    onClick = { treatmentStatusFilter = stKey },
                                    label = { Text(stLabel, fontSize = 11.sp, fontWeight = if (treatmentStatusFilter == stKey) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        // Category Filter Chips
                        val txCategories = listOf("عصب‌کشی", "روکش", "کشیدن", "ترمیمی", "جراحی", "ایمپلنت", "سایر")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = treatmentCategoryFilter == null,
                                onClick = { treatmentCategoryFilter = null },
                                label = { Text(if (isPersian) "همه دسته‌ها" else "All Categories", fontSize = 10.5.sp) }
                            )
                            txCategories.forEach { cat ->
                                FilterChip(
                                    selected = treatmentCategoryFilter == cat,
                                    onClick = { treatmentCategoryFilter = if (treatmentCategoryFilter == cat) null else cat },
                                    label = { Text(cat, fontSize = 10.5.sp) }
                                )
                            }
                        }
                    }

                    val displayedTreatments = treatments.filter { tx ->
                        val matchesStatus = when (treatmentStatusFilter) {
                            "PLANNED" -> tx.status != "IN_PROGRESS" && tx.status != "COMPLETED" && tx.status != "تمام شده" && tx.status != "تکمیل شده"
                            "IN_PROGRESS" -> tx.status == "IN_PROGRESS" || tx.status == "در حال انجام"
                            "COMPLETED" -> tx.status == "COMPLETED" || tx.status == "تمام شده" || tx.status == "تکمیل شده"
                            else -> true
                        }
                        val matchesSearch = treatmentSearchQuery.isBlank() ||
                                tx.title.contains(treatmentSearchQuery, ignoreCase = true) ||
                                tx.selectedTeeth.contains(treatmentSearchQuery, ignoreCase = true) ||
                                tx.notes.contains(treatmentSearchQuery, ignoreCase = true)
                        val matchesCategory = treatmentCategoryFilter == null || when (treatmentCategoryFilter) {
                            "عصب‌کشی" -> tx.title.contains("عصب")
                            "روکش" -> tx.title.contains("روکش")
                            "کشیدن" -> tx.title.contains("کشیدن")
                            "ترمیمی" -> tx.title.contains("پرکردن") || tx.title.contains("ترمیم") || tx.title.contains("کامپوزیت") || tx.title.contains("آمالگام")
                            "جراحی" -> tx.title.contains("جراحی") || tx.title.contains("سینوس") || tx.title.contains("پیوند")
                            "ایمپلنت" -> tx.title.contains("ایمپلنت") || tx.title.contains("کاشت")
                            "سایر" -> !(tx.title.contains("عصب") || tx.title.contains("روکش") || tx.title.contains("کشیدن") || tx.title.contains("پرکردن") || tx.title.contains("ترمیم") || tx.title.contains("کامپوزیت") || tx.title.contains("آمالگام") || tx.title.contains("جراحی") || tx.title.contains("ایمپلنت"))
                            else -> true
                        }
                        matchesStatus && matchesSearch && matchesCategory
                    }

                    if (displayedTreatments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = if (isPersian) "هیچ طرح درمانی با این فیلتر یافت نشد" else "No treatments match this filter", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            displayedTreatments.forEach { treatment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = treatment.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.threeSecondLongPress(
                                                onLongClick = {
                                                    if (TherapyKnowledgeBase.findProtocol(treatment.title) != null) {
                                                        selectedProtocolTitleForPatientDetail = treatment.title
                                                    }
                                                }
                                            )
                                        )
                                        Text(text = viewModel.formatDate(treatment.date), fontSize = 11.sp, color = Color.Gray)
                                        com.example.ui.components.AssistantNamesRow(
                                            assistantIds = treatment.assistantIds,
                                            allStaff = allStaffMembersForDetails,
                                            isPersian = isPersian,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        // Row containing Tooth Numbers Field NEXT TO the 3-state Status Selector/Chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Teeth Field
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (treatment.selectedTeeth.isNotBlank()) {
                                                        if (isPersian) "دندان‌ها: ${treatment.selectedTeeth}" else "Teeth: ${treatment.selectedTeeth}"
                                                    } else {
                                                        if (isPersian) "دندان‌ها: —" else "Teeth: —"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            // Status Chips ("برنامه‌ریزی شده", "در حال انجام", "تمام شده")
                                            val currentStatus = when (treatment.status) {
                                                "COMPLETED", "تمام شده", "تکمیل شده", "پایان یافته" -> "COMPLETED"
                                                "IN_PROGRESS", "در حال انجام", "در حال درمان" -> "IN_PROGRESS"
                                                else -> "PLANNED"
                                            }

                                            val statusOptions = listOf(
                                                "PLANNED" to (if (isPersian) "برنامه‌ریزی شده" else "Planned"),
                                                "IN_PROGRESS" to (if (isPersian) "در حال انجام" else "In Progress"),
                                                "COMPLETED" to (if (isPersian) "تمام شده" else "Completed")
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                statusOptions.forEach { (stKey, stLabel) ->
                                                    val isSel = currentStatus == stKey
                                                    val bg = if (isSel) {
                                                        when (stKey) {
                                                            "COMPLETED" -> Color(0xFFE8F5E9)
                                                            "IN_PROGRESS" -> Color(0xFFFFF3E0)
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        }
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                                    }
                                                    val textColor = if (isSel) {
                                                        when (stKey) {
                                                            "COMPLETED" -> Color(0xFF2E7D32)
                                                            "IN_PROGRESS" -> Color(0xFFE65100)
                                                            else -> Color.DarkGray
                                                        }
                                                    } else {
                                                        Color.Gray
                                                    }

                                                    Surface(
                                                        color = bg,
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = if (isSel) BorderStroke(1.dp, textColor.copy(alpha = 0.5f)) else null
                                                    ) {
                                                        Text(
                                                            text = stLabel,
                                                            fontSize = 9.sp,
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                            color = textColor,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (treatment.discount > 0.0) {
                                            Surface(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isPersian) "تخفیف: -${formatAmount(treatment.discount)} $currency" else "Discount: -${formatAmount(treatment.discount)} $currency",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val displayNotes = treatment.notes.cleanNotesForDisplay()
                                        if (displayNotes.isNotEmpty()) {
                                            Text(text = displayNotes, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                                        }

                                        // Therapy Stages Checklist (Read-Only in patient record view)
                                        TreatmentStageChecklistCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        // Root Canal Working Lengths
                                        EndoWorkingLengthCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        // Restoration Surface Model (Read-Only)
                                        ToothSurfaceRestorationCard(treatment = treatment, viewModel = viewModel, readOnly = true)

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${formatAmount(treatment.cost - treatment.discount)} $currency",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (treatment.discount > 0.0) {
                                                    Text(
                                                        text = if (isPersian) "تخفیف: -${formatAmount(treatment.discount)} $currency" else "Discount: -${formatAmount(treatment.discount)} $currency",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                OutlinedButton(
                                                    onClick = { treatmentToEdit = treatment },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isPersian) "ویرایش درمان" else "Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedIconButton(
                                                    onClick = { treatmentToDelete = treatment }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        } else if (currentSubTab == "installments") {
            if (patientInstallments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPersian) "هیچ قسط یا تعهد مالی برای این بیمار ثبت نشده است" else "No installments recorded", color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patientInstallments.forEach { inst ->
                        val isOverdue = !inst.isPaid && inst.dueDate < System.currentTimeMillis()
                        val statusColor = when {
                            inst.isPaid -> Color(0xFF2E7D32)
                            isOverdue -> Color(0xFFD32F2F)
                            else -> Color(0xFFE65100)
                        }
                        val statusText = when {
                            inst.isPaid -> if (isPersian) "پرداخت شده" else "Paid"
                            isOverdue -> if (isPersian) "معوقه" else "Overdue"
                            else -> if (isPersian) "در انتظار پرداخت" else "Pending"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = inst.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "سررسید: ${viewModel.formatDate(inst.dueDate)}", fontSize = 11.sp, color = Color.Gray)
                                        if (inst.note.isNotBlank()) {
                                            Text(text = inst.note, fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "${formatAmount(inst.amount)} $currency", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = statusColor)
                                        Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                            Text(statusText, fontSize = 9.5.sp, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!inst.isPaid) {
                                        Button(
                                            onClick = { viewModel.markInstallmentPaid(inst) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "ثبت پرداخت قسط" else "Pay Installment", fontSize = 10.5.sp)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteInstallment(inst) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentSubTab == "documents") {
            ClinicalDocumentsTab(
                patient = patient,
                viewModel = viewModel,
                isPersian = isPersian
            )
        } else if (currentSubTab == "prescriptions") {
            // "نسخه پزشکی" Tab
            val rxListFlow = remember(patient.id) { viewModel.getPrescriptionsByPatient(patient.id) }
            val rxList by rxListFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rxList.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isPersian) "هیچ نسخه پزشکی برای این بیمار ثبت نشده است." else "No prescriptions recorded.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    rxList.sortedByDescending { it.date }.forEach { rx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(rx.treatmentTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(viewModel.formatDate(rx.date), fontSize = 11.sp, color = Color.Gray)
                                    }
                                    if (rx.doctorApproved) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = "Approved", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                    }
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                Text(if (isPersian) "ملاحظات دارویی (Notes):" else "Medication Considerations:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (rx.notes.isNotBlank()) {
                                    Text(rx.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("-", fontSize = 11.sp, color = Color.Gray)
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                Text(if (isPersian) "نسخه مربوطه (Prescription):" else "Prescription Details:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (rx.analgesic.isNotBlank()) Text("💊 مسکن: ${rx.analgesic}", fontSize = 11.sp)
                                if (rx.antibiotic.isNotBlank()) Text("💊 آنتی‌بیوتیک: ${rx.antibiotic}", fontSize = 11.sp)
                                if (rx.mouthwash.isNotBlank()) Text("🧴 دهان‌شویه: ${rx.mouthwash}", fontSize = 11.sp)
                                if (rx.otherMeds.isNotBlank()) {
                                    val others = rx.otherMeds.split("|||").filter { it.isNotBlank() }
                                    others.forEachIndexed { idx, o -> 
                                        Text("➕ سایر (${idx+1}): ${parseRxToColumns(o, rx.durationDays).title}", fontSize = 11.sp)
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { editingRx = rx }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPersian) "ویرایش" else "Edit", fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val settingsState by viewModel.settings.collectAsStateWithLifecycle()
                                        val pRows = remember(rx) {
                                            val list = mutableListOf<com.example.reports.RxColumnRowData>()
                                            if (rx.analgesic.isNotBlank()) list.add(parseRxToColumns(rx.analgesic, rx.durationDays))
                                            if (rx.antibiotic.isNotBlank()) list.add(parseRxToColumns(rx.antibiotic, rx.durationDays))
                                            if (rx.mouthwash.isNotBlank()) list.add(parseRxToColumns(rx.mouthwash, rx.durationDays))
                                            if (rx.otherMeds.isNotBlank()) {
                                                rx.otherMeds.split("|||").filter { it.isNotBlank() }.forEach { 
                                                    list.add(parseRxToColumns(it, rx.durationDays))
                                                }
                                            }
                                            if (list.isEmpty()) list.add(com.example.reports.RxColumnRowData("نسخه دارویی", "۱", "طبق دستور", "روزانه", "-"))
                                            list
                                        }
                                        val pName = patient.name
                                        val pIdPhone = if (patient.patientCode.isNotBlank()) patient.patientCode else patient.phone
                                        val pDateStr = viewModel.formatDate(rx.date)

                                        OutlinedButton(
                                            onClick = {
                                                val txt = buildStandardRxShareText(
                                                    patientName = pName,
                                                    patientNationalIdOrPhone = pIdPhone,
                                                    dateStr = pDateStr,
                                                    durationDays = rx.durationDays,
                                                    rows = pRows,
                                                    notes = rx.publicNote,
                                                    clinicName = settingsState.clinicName,
                                                    doctorName = settingsState.doctorName,
                                                    gender = patient.gender
                                                )
                                                sendSmsPrefilled(context, patient.phone, txt)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "پیامک" else "SMS", fontSize = 10.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val cardFile = com.example.reports.generateStandardPrescriptionCardImage(
                                                    context = context,
                                                    patientName = honorificPatientName(pName, patient.gender),
                                                    patientNationalIdOrPhone = pIdPhone,
                                                    dateStr = pDateStr,
                                                    rows = pRows,
                                                    notes = rx.publicNote,
                                                    clinicName = settingsState.clinicName,
                                                    doctorName = settingsState.doctorName
                                                )
                                                shareImageToWhatsApp(context, cardFile, patient.phone)
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366)),
                                            border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "WhatsApp", modifier = Modifier.size(14.dp), tint = Color(0xFF25D366))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "واتساپ (کارت)" else "WhatsApp", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (editingRx != null) {
                val currentRx = editingRx!!
                var eNotes by remember(currentRx) { mutableStateOf(currentRx.notes) }
                var eDurationStr by remember(currentRx) { mutableStateOf(currentRx.durationDays.toString()) }
                val gDur = eDurationStr.toIntOrNull() ?: currentRx.durationDays

                val eAnalgesicState = remember(currentRx) { RxItemEditState(currentRx.analgesic, gDur) }
                val eAntibioticState = remember(currentRx) { RxItemEditState(currentRx.antibiotic, gDur) }
                val eMouthwashState = remember(currentRx) { RxItemEditState(currentRx.mouthwash, gDur) }

                val eOtherMedsList = remember(currentRx) { mutableStateListOf<RxItemEditState>() }
                LaunchedEffect(currentRx) {
                    eOtherMedsList.clear()
                    val parts = currentRx.otherMeds.split("|||")
                    for (p in parts) {
                        if (p.isNotBlank()) {
                            eOtherMedsList.add(RxItemEditState(p, gDur))
                        }
                    }
                    if (eOtherMedsList.isEmpty()) {
                        eOtherMedsList.add(RxItemEditState("", gDur))
                    }
                }

                AlertDialog(
                    onDismissRequest = { editingRx = null },
                    title = { Text(if (isPersian) "ویرایش نسخه و ملاحظات" else "Edit Prescription", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = eDurationStr,
                                onValueChange = { eDurationStr = it },
                                label = { Text(if (isPersian) "طول درمان (روز)" else "Duration (Days)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = eNotes,
                                onValueChange = { eNotes = it },
                                label = { Text(if (isPersian) "ملاحظات دارویی" else "Notes") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            RxItemEditorRow(
                                label = if (isPersian) "مسکن (Analgesic)" else "Analgesic",
                                state = eAnalgesicState
                            )
                            RxItemEditorRow(
                                label = if (isPersian) "آنتی‌بیوتیک (Antibiotic)" else "Antibiotic",
                                state = eAntibioticState
                            )
                            RxItemEditorRow(
                                label = if (isPersian) "دهان‌شویه (Mouthwash)" else "Mouthwash",
                                state = eMouthwashState
                            )

                            eOtherMedsList.forEachIndexed { idx, state ->
                                RxItemEditorRow(
                                    label = if (isPersian) "سایر داروها (${idx + 1})" else "Other Med (${idx + 1})",
                                    state = state,
                                    onDelete = if (eOtherMedsList.size > 1) { { eOtherMedsList.removeAt(idx) } } else null
                                )
                            }

                            TextButton(onClick = { eOtherMedsList.add(RxItemEditState("", gDur)) }) {
                                Text(if (isPersian) "➕ افزودن داروی جدید" else "➕ Add New Drug", fontSize = 11.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val updated = currentRx.copy(
                                analgesic = eAnalgesicState.toRawString(),
                                antibiotic = eAntibioticState.toRawString(),
                                mouthwash = eMouthwashState.toRawString(),
                                otherMeds = eOtherMedsList.map { it.toRawString() }.filter { it.isNotBlank() }.joinToString("|||"),
                                notes = eNotes,
                                publicNote = eNotes,
                                durationDays = eDurationStr.toIntOrNull() ?: 5,
                                doctorApproved = true
                            )
                            viewModel.savePrescription(updated) { editingRx = null }
                        }) {
                            Text(if (isPersian) "ذخیره" else "Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingRx = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
                    }
                )
            }
        } else {
            // Payments Sub-Tab
            if (showPaymentForm) {
                AddPaymentForm(
                    patientId = patient.id,
                    viewModel = viewModel,
                    onSaved = { showPaymentForm = false }
                )
            } else {
                if (payments.isNotEmpty()) {
                    val context = LocalContext.current
                    val currentPalette = com.example.ui.theme.LocalNeonPalette.current
                    OutlinedButton(
                        onClick = {
                            val items = payments.map { p ->
                                com.example.reports.SummaryLineItem(
                                    title = viewModel.label(p.type.lowercase()),
                                    subtitle = viewModel.formatDate(p.date),
                                    amountText = "${formatAmount(p.amount)} $currency"
                                )
                            }
                            val cardFile = com.example.reports.buildSummaryImage(
                                context = context,
                                cardTitle = if (isPersian) "صورتحساب پرداختی‌های مراجع" else "Patient Payment Statement",
                                clinicName = settings.clinicName.ifBlank { "کلینیک دندانپزشکی" },
                                doctorName = settings.doctorName.ifBlank { "دکتر" },
                                patientName = patient.name,
                                items = items,
                                footerLines = listOf(
                                    Pair(if (isPersian) "مجموع دریافتی" else "Total Paid", "${formatAmount(payments.sumOf { it.amount })} $currency")
                                ),
                                themeSpec = com.example.reports.ExportThemeSpec.fromPalette(currentPalette),
                                fileNamePrefix = "payments_statement_${patient.id}",
                                gender = patient.gender
                            )
                            com.example.reports.shareImageNativeChooser(context, cardFile)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPersian) "📤 ارسال / اشتراک‌گذاری صورت‌حساب پرداختی‌ها" else "📤 Share Payments Statement", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPersian) "دریافتی ثبت نشده" else "No payments recorded", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        payments.forEach { payment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${viewModel.label("payment_type")}: ${viewModel.label(payment.type.lowercase())}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(text = viewModel.formatDate(payment.date), fontSize = 11.sp, color = Color.Gray)
                                            com.example.ui.components.AssistantNamesRow(
                                                assistantIds = payment.assistantIds,
                                                allStaff = allStaffMembersForDetails,
                                                isPersian = isPersian,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            if (payment.type == "CHEQUE") {
                                                Text(
                                                    text = "${viewModel.label("cheque_number")}: ${payment.chequeNumber ?: ""}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Gray
                                                )
                                                if (!payment.sayadiId.isNullOrBlank()) {
                                                    Text(
                                                        text = "شناسه صیادی: ${payment.sayadiId}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                payment.chequeDueDate?.let {
                                                    Text(
                                                        text = "${viewModel.label("cheque_due_date")}: ${viewModel.formatDate(it)}",
                                                        fontSize = 11.sp,
                                                        color = DebtColor
                                                    )
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "${formatAmount(payment.amount)} $currency", fontWeight = FontWeight.Bold, color = ClearColor)
                                            if (editMode) {
                                                IconButton(onClick = { paymentToDelete = payment }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                                }
                                            }
                                        }

                                    // Render Cheque Photo preview if exists
                                    if (payment.type == "CHEQUE" && !payment.chequePhotoFileId.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        SmartImage(
                                            fileId = payment.chequePhotoFileId,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (treatmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { treatmentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف درمان" else "Confirm Treatment Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف درمان «${treatmentToDelete!!.title}» برای بیمار اطمینان دارید؟"
                    else "Are you sure you want to delete treatment '${treatmentToDelete!!.title}'?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = treatmentToDelete!!
                        viewModel.deleteTreatment(deleted)
                        treatmentToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "درمان «${deleted.title}» حذف شد" else "'${deleted.title}' deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeleteTreatment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف درمان" else "Delete Treatment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { treatmentToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = if (isPersian) "تایید حذف تراکنش پرداخت" else "Confirm Payment Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف تراکنش پرداخت به مبلغ «${formatAmount(paymentToDelete!!.amount)} $currency» اطمینان دارید؟"
                    else "Are you sure you want to delete this payment of ${formatAmount(paymentToDelete!!.amount)} $currency?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = paymentToDelete!!
                        viewModel.deletePayment(deleted)
                        paymentToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "پرداخت «${formatAmount(deleted.amount)} $currency» حذف شد" else "Payment of ${formatAmount(deleted.amount)} $currency deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeletePayment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف پرداخت" else "Delete Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (treatmentToEdit != null) {
        EditTreatmentDialog(
            treatment = treatmentToEdit!!,
            viewModel = viewModel,
            onDelete = {
                treatmentToDelete = treatmentToEdit
                treatmentToEdit = null
            },
            onDismiss = { treatmentToEdit = null }
        )
    }

    if (selectedProtocolTitleForPatientDetail != null) {
        ClinicalProtocolDialog(
            therapyTitle = selectedProtocolTitleForPatientDetail!!,
            patientMedicalHistory = patient.medicalHistory,
            patientMedicalAlerts = patient.medicalAlerts,
            isPersian = isPersian,
            initialFontSizePt = settings.clinicalFontSizePt,
            onDismiss = { selectedProtocolTitleForPatientDetail = null }
        )
    }

    if (showAddInstallmentDialog) {
        AddInstallmentDialog(
            patientId = patient.id,
            viewModel = viewModel,
            onDismiss = { showAddInstallmentDialog = false }
        )
    }
}
}
}

// ==========================================
// ADD INSTALLMENT DIALOG
// ==========================================
@Composable
fun AddInstallmentDialog(
    patientId: Long,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("قسط درمان دندانپزشکی") }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var daysOffset by remember { mutableStateOf("30") }
    var installmentCountStr by remember { mutableStateOf("1") }
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "➕ ثبت اقساط جدید برای بیمار" else "➕ Add Patient Installment(s)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isPersian) "عنوان قسط" else "Installment Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(if (isPersian) "مبلغ هر قسط (تومان)" else "Amount per Installment") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = installmentCountStr,
                        onValueChange = { installmentCountStr = it },
                        label = { Text(if (isPersian) "تعداد اقساط (N)" else "Count (N)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = daysOffset,
                        onValueChange = { daysOffset = it },
                        label = { Text(if (isPersian) "فاصله اقساط (روز)" else "Interval (Days)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (isPersian) "توضیحات یا شماره چک/ضمانت" else "Note / Cheque Info") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toEnglishLong()
                    val offset = daysOffset.toEnglishLong().coerceAtLeast(1)
                    val count = installmentCountStr.toEnglishLong().coerceAtLeast(1).toInt()
                    if (amount > 0) {
                        for (i in 1..count) {
                            val dueDate = System.currentTimeMillis() + (i * offset * 24 * 3600 * 1000L)
                            val itemTitle = if (count > 1) "$title (قسط $i از $count)" else title
                            viewModel.addInstallment(
                                patientId = patientId,
                                treatmentId = null,
                                title = itemTitle,
                                amount = amount,
                                dueDate = dueDate,
                                note = note
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = amountStr.toEnglishLong() > 0
            ) {
                Text(if (isPersian) "ثبت اقساط" else "Save Installments")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

@Composable
fun EditTreatmentDialog(
    treatment: Treatment,
    viewModel: DentalViewModel,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    var title by remember { mutableStateOf(treatment.title) }
    var costStr by remember { mutableStateOf(treatment.cost.toString()) }
    var discountStr by remember { mutableStateOf(treatment.discount.toString()) }
    var selectedTeeth by remember { mutableStateOf(treatment.selectedTeeth) }
    var notes by remember { mutableStateOf(treatment.notes) }
    var status by remember { mutableStateOf(treatment.status) }
    var stages by remember { mutableStateOf(treatment.stages) }
    val allStaffMembersForEdit by viewModel.allStaffMembers.collectAsStateWithLifecycle()
    var selectedAssistantSyncIds by remember { mutableStateOf(com.example.ui.components.parseAssistantSyncIds(treatment.assistantIds)) }
    // پیش‌فرض: نام دستیار فعال به‌صورت خودکار وارد می‌شود — فقط اگر هنوز دستیاری انتخاب نشده باشد (درخواست ۶)
    val defaultAssistantSyncIds by viewModel.activeAssistantSyncIdsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(defaultAssistantSyncIds) {
        if (selectedAssistantSyncIds.isEmpty() && defaultAssistantSyncIds.isNotEmpty()) {
            selectedAssistantSyncIds = defaultAssistantSyncIds
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "ویرایش طرح درمان" else "Edit Treatment Plan", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isPersian) "عنوان درمان" else "Treatment Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text(if (isPersian) "هزینه (تومان)" else "Cost") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text(if (isPersian) "تخفیف (تومان)" else "Discount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                OutlinedTextField(
                    value = selectedTeeth,
                    onValueChange = { selectedTeeth = it },
                    label = { Text(if (isPersian) "دندان‌های مرتبط (مثلاً 11, 12)" else "Teeth") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stages,
                    onValueChange = { stages = it },
                    label = { Text(if (isPersian) "مراحل درمان (با | جدا کنید)" else "Treatment Stages (separated by |)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isPersian) "وضعیت طرح درمان:" else "Treatment Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusOptions = listOf(
                            "PLANNED" to (if (isPersian) "برنامه‌ریزی شده" else "Planned"),
                            "IN_PROGRESS" to (if (isPersian) "در حال انجام" else "In Progress"),
                            "COMPLETED" to (if (isPersian) "تمام شده" else "Completed")
                        )
                        statusOptions.forEach { (stKey, stLabel) ->
                            FilterChip(
                                selected = status == stKey,
                                onClick = { status = stKey },
                                label = { Text(stLabel, fontSize = 11.sp, fontWeight = if (status == stKey) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                com.example.ui.components.AssistantMultiSelectField(
                    selectedSyncIds = selectedAssistantSyncIds,
                    onSelectionChange = { selectedAssistantSyncIds = it },
                    allStaff = allStaffMembersForEdit,
                    isPersian = isPersian
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isPersian) "توضیحات و ملاحظات بالینی" else "Clinical Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = costStr.toEnglishLong()
                    val discount = discountStr.toEnglishLong()
                    if (title.isNotBlank()) {
                        viewModel.updateTreatment(
                            treatment.copy(
                                title = title.trim(),
                                cost = cost,
                                discount = discount,
                                selectedTeeth = selectedTeeth.trim(),
                                notes = notes.trim(),
                                status = status,
                                stages = stages.trim(),
                                assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds)
                            )
                        )
                        onDismiss()
                    }
                }
            ) {
                Text(if (isPersian) "ذخیره تغییرات" else "Save Changes")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "حذف طرح درمان" else "Delete", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        }
    )
}

// ----------------------------------------------------
// ULTRA FAST INPUTS: PRE-FILLED TREATMENT TEMPLATES
// ----------------------------------------------------
@Composable
fun AddTreatmentForm(
    patient: com.example.data.Patient,
    viewModel: DentalViewModel,
    onSaved: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var discountStr by remember { mutableStateOf("") }
    var selectedTeeth by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val titleError = if (title.isBlank()) "شرح درمان الزامی است" else null
    val costError = if (!com.example.util.isValidAmount(costStr)) "هزینه باید یک عدد معتبر و غیرمنفی باشد" else null

    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val customTemplates by viewModel.templates.collectAsStateWithLifecycle()
    val chartSystem by viewModel.dentalChartSystem.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories = listOf("عصب‌کشی", "روکش", "کشیدن", "ترمیمی", "سایر موارد")

    val allStaffMembersForTreatment by viewModel.allStaffMembers.collectAsStateWithLifecycle()
    var selectedAssistantSyncIds by remember { mutableStateOf(listOf<String>()) }
    // پیش‌فرض: نام دستیار فعال به‌صورت خودکار در تمام فعالیت‌ها وارد می‌شود (درخواست ۶)
    val defaultAssistantSyncIds by viewModel.activeAssistantSyncIdsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(defaultAssistantSyncIds) {
        if (selectedAssistantSyncIds.isEmpty() && defaultAssistantSyncIds.isNotEmpty()) {
            selectedAssistantSyncIds = defaultAssistantSyncIds
        }
    }

    val filteredTemplates = remember(customTemplates, selectedCategory) {
        val filtered = if (selectedCategory == null) customTemplates else {
            customTemplates.filter { temp ->
                val faTitle = temp.titleFa.ifBlank { temp.title }
                when (selectedCategory) {
                    "عصب‌کشی" -> faTitle.contains("عصب")
                    "روکش" -> faTitle.contains("روکش")
                    "کشیدن" -> faTitle.contains("کشیدن")
                    "ترمیمی" -> faTitle.contains("پرکردن") || faTitle.contains("ترمیم") || faTitle.contains("کامپوزیت") || faTitle.contains("آمالگام")
                    "سایر موارد" -> !(faTitle.contains("عصب") || faTitle.contains("روکش") || faTitle.contains("کشیدن") || faTitle.contains("پرکردن") || faTitle.contains("ترمیم") || faTitle.contains("کامپوزیت") || faTitle.contains("آمالگام"))
                    else -> true
                }
            }
        }
        filtered
    }

    val starredTemplatesList = filteredTemplates.filter { it.isStarred }.sortedBy { it.sortOrder }
    val generalTemplatesList = filteredTemplates.filter { !it.isStarred }.sortedBy { it.sortOrder }
    val allCustomTemplates = (starredTemplatesList + generalTemplatesList)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (customTemplates.isNotEmpty()) {
                var templateDropdownExpanded by remember { mutableStateOf(false) }

                if (isPersian) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("همه الگوها", fontSize = 11.sp) }
                            )
                        }
                        items(categories.size) { idx ->
                            val cat = categories[idx]
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                
                OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = templateDropdownExpanded,
                    onExpandedChange = { templateDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentTemplate = allCustomTemplates.find { it.title == title }
                    OutlinedTextField(
                        value = if (isPersian) (currentTemplate?.titleFa?.ifBlank { currentTemplate.title } ?: "انتخاب الگوی درمان...") else (currentTemplate?.title ?: "Select template..."),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true),
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        label = { Text(if (isPersian) "الگوهای آماده برای ورود سریع:" else "Quick Entry Templates:") }
                    )
                    ExposedDropdownMenu(
                        expanded = templateDropdownExpanded,
                        onDismissRequest = { templateDropdownExpanded = false }
                    ) {
                        allCustomTemplates.forEach { temp ->
                            val isStarred = temp.isStarred
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isStarred) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            if (isPersian) temp.titleFa.ifBlank { temp.title } else temp.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isStarred) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    title = temp.title
                                    costStr = temp.defaultCost.toString()
                                    note = temp.defaultNotes
                                    templateDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(viewModel.label("treatment_title")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = attemptedSubmit && titleError != null,
                supportingText = {
                    if (attemptedSubmit && titleError != null) {
                        Text(titleError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text(if (viewModel.isPersian.collectAsStateWithLifecycle().value) "هزینه واحد (تومان)" else "Unit Cost (Toman)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = attemptedSubmit && costError != null,
                        supportingText = {
                            if (attemptedSubmit && costError != null) {
                                Text(costError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )
                    RealTimeAmountHelper(costStr)
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text(if (isPersian) "تخفیف (تومان)" else "Discount (Toman)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    RealTimeAmountHelper(discountStr)
                }
            }

            // Interactive 32-Tooth Chart
            InteractiveToothChart(
                selectedTeeth = selectedTeeth,
                onTeethChanged = { selectedTeeth = it },
                system = chartSystem,
                isDeciduous = patient.age in 1..12
            )

            com.example.ui.components.AssistantMultiSelectField(
                selectedSyncIds = selectedAssistantSyncIds,
                onSelectionChange = { selectedAssistantSyncIds = it },
                allStaff = allStaffMembersForTreatment,
                isPersian = isPersian
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(viewModel.label("notes")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    attemptedSubmit = true
                    val cost = costStr.toEnglishLong()
                    val discount = discountStr.toEnglishLong()
                    if (titleError == null && costError == null) {
                        var finalNote = note
                        val lowerTitle = title.lowercase()
                        if ((lowerTitle.contains("عصب") || lowerTitle.contains("rct") || lowerTitle.contains("endo") || lowerTitle.contains("ریشه") || lowerTitle.contains("کانال"))
                            && !finalNote.contains("[ENDO_WORKING_LENGTH:")) {
                            val defaultCanals = getDefaultCanalsForTeeth(selectedTeeth)
                            finalNote = encodeEndoCanalData(finalNote, defaultCanals)
                        }

                        viewModel.addTreatmentWithDetails(
                            patientId = patient.id,
                            title = title,
                            cost = cost,
                            discount = discount,
                            selectedTeeth = selectedTeeth,
                            notes = finalNote,
                            assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds)
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun AddPaymentForm(
    patientId: Long,
    viewModel: DentalViewModel,
    onSaved: () -> Unit,
    initialPaymentType: String = "CASH"
) {
    var amountStr by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val amountError = if (!com.example.util.isValidPositiveAmount(amountStr)) "مبلغ باید یک عدد معتبر و بزرگ‌تر از صفر باشد" else null
    var paymentType by remember { mutableStateOf(initialPaymentType) } // CASH, CARD, CHEQUE
    var chequeNum by remember { mutableStateOf("") }
    var chequeDueDays by remember { mutableStateOf("30") } // e.g. due in 30 days
    var sayadiId by remember { mutableStateOf("") }
    var chequeBank by remember { mutableStateOf("") }
    var chequeDrawer by remember { mutableStateOf("") }
    var chequePhotoFileId by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var paymentDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    var showScannerSim by remember { mutableStateOf(false) }
    val allStaffMembersForPayment by viewModel.allStaffMembers.collectAsStateWithLifecycle()
    var selectedAssistantSyncIds by remember { mutableStateOf(listOf<String>()) }
    // پیش‌فرض: نام دستیار فعال به‌صورت خودکار در تمام فعالیت‌ها وارد می‌شود (درخواست ۶)
    val defaultAssistantSyncIds by viewModel.activeAssistantSyncIdsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(defaultAssistantSyncIds) {
        if (selectedAssistantSyncIds.isEmpty() && defaultAssistantSyncIds.isNotEmpty()) {
            selectedAssistantSyncIds = defaultAssistantSyncIds
        }
    }

    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val context = androidx.compose.ui.platform.LocalContext.current
    val currency = viewModel.label("currency")


    val contentResolver = context.contentResolver

    // تقویم شمسی برای تاریخ تراکنش (جایگزین DatePickerDialog میلادی)
    var showPaymentJalaliPicker by remember { mutableStateOf(false) }
    if (showPaymentJalaliPicker) {
        JalaliDatePickerDialog(
            initialTimestamp = paymentDate,
            isPersian = isPersian,
            onDateSelected = { jy, jm, jd ->
                val keepCal = java.util.Calendar.getInstance().apply { timeInMillis = paymentDate }
                paymentDate = com.example.util.PersianCalendarHelper.jalaliToTimestamp(
                    jy, jm, jd,
                    keepCal.get(java.util.Calendar.HOUR_OF_DAY),
                    keepCal.get(java.util.Calendar.MINUTE)
                )
                showPaymentJalaliPicker = false
            },
            onDismiss = { showPaymentJalaliPicker = false }
        )
    }

    // Camera launcher
    val chequePhotoEditLauncher = rememberPhotoEditLauncher(PhotoAssetKind.SAYAD_CHEQUE) { localPath ->
        chequePhotoFileId = localPath
    }
    val launchCamera = rememberEditableCameraLauncher(context) { uri ->
        chequePhotoEditLauncher(uri)
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            chequePhotoEditLauncher(uri)
        }
    }

    var showSmsParserDialog by remember { mutableStateOf(false) }

    if (showSmsParserDialog) {
        BankSmsParserDialog(
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { showSmsParserDialog = false }
        )
    }

    if (showScannerSim) {
        RealSayadiBarcodeScannerDialog(
            onDismiss = { showScannerSim = false },
            onScanned = { scanned16DigitId ->
                sayadiId = scanned16DigitId
                showScannerSim = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bank SMS Parser Button
            OutlinedButton(
                onClick = { showSmsParserDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isPersian) "📥 استخراج و ثبت هوشمند تراکنش از پیامک بانکی" else "Smart Bank SMS Parser", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            // 1. Payment Method Selector Row AT THE TOP
            Text(text = if (isPersian) "روش دریافت / پرداخت:" else "Payment Method:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "CASH" to (if (isPersian) "نقد (صندوق)" else "Cash"),
                    "CARD_TRANSFER" to (if (isPersian) "انتقال وجه" else "Fund Transfer"),
                    "CHEQUE" to (if (isPersian) "چک صیادی" else "Cheque"),
                    "POS" to (if (isPersian) "کارتخوان" else "POS Card")
                ).forEach { (type, labelText) ->
                    ElevatedFilterChip(
                        selected = paymentType == type,
                        onClick = { paymentType = type },
                        label = { Text(labelText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Amount and Date Inputs directly below Payment Method
            // (برای چک صیادی این فیلدهای «تراکنش» حذف می‌شوند — مبلغ/تاریخ از فرم خودِ چک می‌آید؛ درخواست ۸)
            if (paymentType != "CHEQUE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Amount Input
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(viewModel.label("amount")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = attemptedSubmit && amountError != null,
                        supportingText = {
                            if (attemptedSubmit && amountError != null) {
                                Text(amountError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )
                    RealTimeAmountHelper(amountStr)
                }

                // Date Input
                OutlinedTextField(
                    value = viewModel.formatDate(paymentDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isPersian) "تاریخ تراکنش" else "Date") },
                    trailingIcon = {
                        IconButton(onClick = { showPaymentJalaliPicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Date", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.weight(1f).clickable { showPaymentJalaliPicker = true },
                    textStyle = TextStyle(fontSize = 12.sp)
                )
            }
            }

            // If Cheque, show comprehensive sayadi cheque fields

            if (paymentType == "CARD_TRANSFER" || paymentType == "POS") {
                // فقط حساب‌ها/کارت‌های واقعاً بانکی — صندوق نقدی و تنخواه گردان کارت نیستند
                val pureBankAccounts = bankAccounts.filter {
                    !(it.bankName.contains("صندوق") || it.bankName.contains("تنخواه") || it.accountNumber.startsWith("CASH") || it.accountNumber.startsWith("PETTY"))
                }
                if (pureBankAccounts.isEmpty()) {
                    Text(text = if (isPersian) "ابتدا در بخش مدیریت مالی، حساب/کارت بانکی تعریف کنید" else "Please define a bank account in financial management first", fontSize = 11.sp, color = Color.Gray)
                } else {
                    Text(text = if (isPersian) "انتخاب حساب/کارت مقصد:" else "Select Destination Account/Card:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pureBankAccounts.forEach { acc ->
                            InputChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
            if (paymentType == "CASH") {
                // دریافتی نقدی: ثبت اختیاری در «صندوق نقدی مطب» یا «تنخواه گردان»
                val cashDestinationAccounts = bankAccounts.filter {
                    it.bankName.contains("صندوق") || it.bankName.contains("تنخواه") || it.accountNumber.startsWith("CASH") || it.accountNumber.startsWith("PETTY")
                }
                if (cashDestinationAccounts.isNotEmpty()) {
                    Text(text = if (isPersian) "ثبت در صندوق (اختیاری):" else "Cash destination (optional):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cashDestinationAccounts.forEach { acc ->
                            InputChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = if (selectedAccountId == acc.id) null else acc.id },
                                label = { Text(acc.bankName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
            if (paymentType == "CHEQUE") {
                var showBatchChequeUpload by remember { mutableStateOf(false) }
                var showManualChequeRegister by remember { mutableStateOf(false) }
                var showRealScanner by remember { mutableStateOf(false) }

                if (showRealScanner) {
                    RealSayadiBarcodeScannerDialog(
                        onDismiss = { showRealScanner = false },
                        onScanned = { scannedId ->
                            sayadiId = scannedId
                            showRealScanner = false
                        }
                    )
                }

                if (showBatchChequeUpload) {
                    BatchChequeUploadDialog(
                        patientId = patientId,
                        viewModel = viewModel,
                        isPersian = true,
                        onDismiss = { showBatchChequeUpload = false; onSaved() }
                    )
                }

                if (showManualChequeRegister) {
                    ManualChequeRegisterDialog(
                        patientId = patientId,
                        viewModel = viewModel,
                        isPersian = true,
                        onDismiss = { showManualChequeRegister = false },
                        onSaved = onSaved
                    )
                }

                OutlinedButton(
                    onClick = { showBatchChequeUpload = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPersian) "ثبت هوشمند دسته‌ای چک‌ها" else "AI Smart Batch Cheque Registration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // ثبت دستی-بارکد چک‌ها — همان فرم تایید استخراج هوشمند (درخواست ۹)
                OutlinedButton(
                    onClick = { showManualChequeRegister = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPersian) "ثبت دستی-بارکد چک‌ها" else "Manual Cheque Registration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sayadiId,
                        onValueChange = { sayadiId = it },
                        label = { Text(if (isPersian) "شناسه ۱۶ رقمی صیادی" else "16-digit Sayadi ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = { showRealScanner = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = chequeNum,
                        onValueChange = { chequeNum = it },
                        label = { Text(viewModel.label("cheque_number")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    var selectedChequeDueDate by remember { mutableStateOf<Long?>(null) }
                    var formattedDueDateText by remember { mutableStateOf("") }

                    // تقویم شمسی برای تاریخ سررسید چک (جایگزین DatePickerDialog میلادی)
                    var showChequeDueJalaliPicker by remember { mutableStateOf(false) }
                    if (showChequeDueJalaliPicker) {
                        JalaliDatePickerDialog(
                            initialTimestamp = selectedChequeDueDate ?: System.currentTimeMillis(),
                            isPersian = isPersian,
                            onDateSelected = { jy, jm, jd ->
                                val picked = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
                                selectedChequeDueDate = picked
                                formattedDueDateText = viewModel.formatDate(picked)
                                showChequeDueJalaliPicker = false
                            },
                            onDismiss = { showChequeDueJalaliPicker = false }
                        )
                    }

                    OutlinedTextField(
                        value = if (formattedDueDateText.isNotEmpty()) formattedDueDateText else (if (isPersian) "انتخاب تاریخ سررسید" else "Select Due Date"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isPersian) "تاریخ سررسید چک" else "Due Date") },
                        trailingIcon = {
                            IconButton(onClick = { showChequeDueJalaliPicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Due Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showChequeDueJalaliPicker = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = chequeBank,
                        onValueChange = { chequeBank = it },
                        label = { Text(if (isPersian) "بانک صادرکننده" else "Issuing Bank") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chequeDrawer,
                        onValueChange = { chequeDrawer = it },
                        label = { Text(if (isPersian) "نام صادرکننده" else "Drawer Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Camera & Gallery Document Section for Cheque Photo
                Text(
                    text = if (isPersian) "تصویر چک صیادی" else "Sayadi Cheque Photo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { launchCamera() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "دوربین" else "Camera", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                    }
                }

                // Render Photo Preview inside Form
                chequePhotoFileId?.let { base64 ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        SmartImage(
                            fileId = base64,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { chequePhotoFileId = null },
                            modifier = Modifier
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            com.example.ui.components.AssistantMultiSelectField(
                selectedSyncIds = selectedAssistantSyncIds,
                onSelectionChange = { selectedAssistantSyncIds = it },
                allStaff = allStaffMembersForPayment,
                isPersian = isPersian
            )

            if (paymentType == "CHEQUE") {
                // برای چک صیادی، ثبت فقط از طریق «ثبت هوشمند دسته‌ای» یا «ثبت دستی-بارکد چک‌ها» انجام می‌شود (درخواست ۸)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = if (isPersian)
                            "ℹ️ مبلغ و تاریخ چک از فرم بالا ثبت می‌شود — دکمه ذخیره برای سایر روش‌های پرداخت است."
                        else
                            "ℹ️ Cheque amount/date are registered via the buttons above — the save button applies to other payment methods.",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
            Button(
                onClick = {
                    attemptedSubmit = true
                    val amount = amountStr.toEnglishLong()
                    if (amountError == null) {
                        val dueTimestamp = if (paymentType == "CHEQUE") {
                            val days = chequeDueDays.toEnglishLong()
                            System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                        } else null

                        viewModel.addPaymentWithAccount(
                            bankAccountId = selectedAccountId,
                            patientId = patientId,
                            amount = amount,
                            type = paymentType,
                            chequeNum = if (paymentType == "CHEQUE") chequeNum else null,
                            chequeDue = dueTimestamp,
                            sayadiId = if (paymentType == "CHEQUE") sayadiId else null,
                            chequeBank = if (paymentType == "CHEQUE") chequeBank else null,
                            chequeDrawer = if (paymentType == "CHEQUE") chequeDrawer else null,
                            chequePhotoFileId = if (paymentType == "CHEQUE") chequePhotoFileId else null,
                            date = paymentDate,
                            assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds)
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
            }
        }
    }
    
}

// ==========================================
// 3. APPOINTMENTS SCHEDULER
// ==========================================
// 3. APPOINTMENTS SCHEDULER
// ==========================================

/**
 * تقویم پاپ‌آپ شمسی (جلالی) — جایگزینِ DatePickerDialog میلادی اندروید.
 * هفته از شنبه شروع می‌شود، جمعه‌ها قرمز، «امروز» و روزِ انتخاب‌شده هایلایت می‌شوند.
 */
@Composable
fun JalaliDatePickerDialog(
    initialTimestamp: Long = System.currentTimeMillis(),
    isPersian: Boolean = true,
    onDateSelected: (jy: Int, jm: Int, jd: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val helper = com.example.util.PersianCalendarHelper
    val todayJ = remember { helper.timestampToJalali(System.currentTimeMillis()) }
    val initialJ = remember { helper.timestampToJalali(initialTimestamp) }

    var viewYear by remember { mutableStateOf(initialJ.year) }
    var viewMonth by remember { mutableStateOf(initialJ.month) }
    var selectedDay by remember { mutableStateOf(initialJ.day) }

    val daysInMonth = helper.getDaysInMonth(viewYear, viewMonth)
    val leadingBlanks = helper.getDayOfWeekIndex(viewYear, viewMonth, 1)
    val weekRows = remember(daysInMonth, leadingBlanks) {
        ((0 until leadingBlanks).map { null } + (1..daysInMonth).toList()).chunked(7)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPersian) "تقویم شمسی" else "Jalali Calendar",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // هدر ماه/سال + ناوبری ماه قبل/بعد (در چیدمان راست‌به‌چپ: راست = ماه قبل)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (viewMonth == 1) { viewMonth = 12; viewYear -= 1 } else viewMonth -= 1
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = if (isPersian) "ماه قبل" else "Previous month")
                    }
                    Text(
                        text = "${helper.MONTH_NAMES[viewMonth - 1]} $viewYear",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = {
                        if (viewMonth == 12) { viewMonth = 1; viewYear += 1 } else viewMonth += 1
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = if (isPersian) "ماه بعد" else "Next month")
                    }
                }

                // سرستون روزهای هفته (شنبه تا جمعه)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { wd ->
                        Text(
                            text = wd,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // شبکه روزهای ماه
                weekRows.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        week.forEach { day ->
                            if (day == null) {
                                Box(modifier = Modifier.weight(1f).height(32.dp))
                            } else {
                                val isToday = viewYear == todayJ.year && viewMonth == todayJ.month && day == todayJ.day
                                val isSelected = day == selectedDay
                                val isFriday = helper.getDayOfWeekIndex(viewYear, viewMonth, day) == 6
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { selectedDay = day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$day",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isFriday -> Color(0xFFC2410C)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // میان‌بر انتخاب امروز
                TextButton(
                    onClick = {
                        viewYear = todayJ.year
                        viewMonth = todayJ.month
                        selectedDay = todayJ.day
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPersian) "انتخاب امروز (${todayJ})" else "Select Today (${todayJ})", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDateSelected(viewYear, viewMonth, selectedDay) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isPersian) "تایید تاریخ" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
        }
    )
}

@Composable
fun AppointmentsScreen(viewModel: DentalViewModel) {
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }
    var apptToDelete by remember { mutableStateOf<Appointment?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var archiveFilter by remember { mutableStateOf("TODAY") } // TODAY, UPCOMING, INCOMPLETE_SESSIONS, CANCELED, ARCHIVED_COMPLETED
    var selectedUnitFilter by remember { mutableStateOf("ALL") } // ALL, UNIT_1, UNIT_2, HYGIENE
    var dischargeApprovalAppt by remember { mutableStateOf<Pair<Appointment, String>?>(null) }
    var pendingRxForAppt by remember { mutableStateOf<PendingRxPopup?>(null) }
    var showHolidayConfirmQuestionDialog by remember { mutableStateOf<Pair<String, Long>?>(null) }

    // Form States
    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var treatmentType by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueDaysStr by remember { mutableStateOf("1") }
    var selectedTreatmentIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedAssistantSyncIds by remember { mutableStateOf(listOf<String>()) }
    // پیش‌فرض: نام دستیار فعال به‌صورت خودکار در تمام فعالیت‌ها وارد می‌شود (درخواست ۶)
    val defaultAssistantSyncIds by viewModel.activeAssistantSyncIdsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(defaultAssistantSyncIds) {
        if (selectedAssistantSyncIds.isEmpty() && defaultAssistantSyncIds.isNotEmpty()) {
            selectedAssistantSyncIds = defaultAssistantSyncIds
        }
    }
    val allStaffMembersForAppointments by viewModel.allStaffMembers.collectAsStateWithLifecycle()

    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val filteredAppointments = appointments.filter { appt ->
        val matchesQuery = appt.patientName.contains(searchQuery, ignoreCase = true) ||
                appt.treatmentType.contains(searchQuery, ignoreCase = true) ||
                appt.note.contains(searchQuery, ignoreCase = true) ||
                viewModel.formatDate(appt.date).contains(searchQuery)

        val now = System.currentTimeMillis()
        val matchesFilter = when (archiveFilter) {
            "TODAY" -> {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1
                appt.date in todayStart..todayEnd && appt.status != "CANCELED"
            }
            "UPCOMING" -> appt.date > now && !appt.isArchived && appt.status != "CANCELED"
            "CANCELED" -> appt.status == "CANCELED"
            "ARCHIVED_COMPLETED" -> appt.isArchived && appt.isCompleted
            else -> !appt.isArchived && appt.status != "CANCELED"
        }

        // فیلتر یونیت/صندلی — نوبت‌های بدون یونیت (رکوردهای قدیمی) به‌عنوان یونیت ۱ در نظر گرفته می‌شوند
        val matchesUnit = selectedUnitFilter == "ALL" || (appt.unit ?: "UNIT_1") == selectedUnitFilter
        matchesQuery && matchesFilter && matchesUnit
    }.sortedBy { it.date } // نوبت‌ها به ترتیب ساعت و روز

    // گروه‌بندی نوبت‌ها بر اساس روز — هر روز در یک کادر مجزا نمایش داده می‌شود
    val appointmentsByDay = filteredAppointments.groupBy { appt ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = appt.date }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.label("appointments"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showForm = !showForm },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showForm) viewModel.label("cancel") else viewModel.label("quick_appointment"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Search & Filter Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isPersian) "جستجوی نوبت بر اساس نام، نوع درمان یا تاریخ..." else "Search appointments...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Archive & Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "TODAY" to if (isPersian) "نوبت‌های امروز" else "Today",
                "UPCOMING" to if (isPersian) "نوبت‌های آینده" else "Upcoming",
                "INCOMPLETE_SESSIONS" to if (isPersian) "جلسات ناتمام" else "Incomplete Sessions",
                "CANCELED" to if (isPersian) "کنسل‌شده" else "Canceled",
                "ARCHIVED_COMPLETED" to if (isPersian) "تکمیل و آرشیو شده" else "Completed & Archived"
            ).forEach { (filterKey, filterLabel) ->
                ElevatedFilterChip(
                    selected = archiveFilter == filterKey,
                    onClick = { archiveFilter = filterKey },
                    label = { Text(filterLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Multi-Chair / Dental Unit Timeline Filter Chips (یونیت‌های مطب)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "ALL" to (if (isPersian) "همه یونیت‌ها" else "All Chairs"),
                "UNIT_1" to (if (isPersian) "یونیت شماره ۱" else "Chair #1"),
                "UNIT_2" to (if (isPersian) "یونیت شماره ۲ (جراحی)" else "Chair #2 (Surg)"),
                "HYGIENE" to (if (isPersian) "یونیت بخش بهداشت" else "Hygiene Chair")
            ).forEach { (uKey, uLabel) ->
                InputChip(
                    selected = selectedUnitFilter == uKey,
                    onClick = { selectedUnitFilter = uKey },
                    label = { Text(uLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.EventSeat, contentDescription = null, modifier = Modifier.size(12.dp)) }
                )
            }
        }

        if (showForm) {
            // Interactive Fast Booking Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isPersian) "ثبت سریع نوبت پزشک" else "Fast Appointment Scheduler",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Select Patient
                    Text(text = viewModel.label("patient_name"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        patients.forEach { p ->
                            InputChip(
                                selected = selectedPatient?.id == p.id,
                                onClick = { selectedPatient = p },
                                label = { Text(p.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    val patientTreatments = allTreatments.filter { it.patientId == selectedPatient?.id && it.status != "COMPLETED" }
                    if (patientTreatments.isNotEmpty()) {
                        var txDropdownExpanded by remember { mutableStateOf(false) }
                        
                        OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        androidx.compose.material3.ExposedDropdownMenuBox(
                            expanded = txDropdownExpanded,
                            onExpandedChange = { txDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (selectedTreatmentIds.isEmpty()) (if (isPersian) "انتخاب تراپی ناتمام..." else "Select unfinished therapy...") else (if (isPersian) "${selectedTreatmentIds.size} تراپی انتخاب شده" else "${selectedTreatmentIds.size} selected"),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = txDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true),
                                colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                label = { Text(if (isPersian) "انتخاب از طرح درمان:" else "Select from Treatment Plan:") }
                            )
                            ExposedDropdownMenu(
                                expanded = txDropdownExpanded,
                                onDismissRequest = { txDropdownExpanded = false }
                            ) {
                                patientTreatments.forEach { tr ->
                                    val isSelected = selectedTreatmentIds.contains(tr.id)
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Checkbox(checked = isSelected, onCheckedChange = null)
                                                Text(tr.title, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedTreatmentIds = if (isSelected) {
                                                selectedTreatmentIds - tr.id
                                            } else {
                                                selectedTreatmentIds + tr.id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    val context = LocalContext.current
                    var selectedDateMills by remember { mutableStateOf<Long?>(null) }
                    var selectedHour by remember { mutableStateOf(16) }
                    var selectedMinute by remember { mutableStateOf(0) }

                    val timePickerDialog = remember {
                        android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                selectedHour = hourOfDay
                                selectedMinute = minute
                            },
                            selectedHour,
                            selectedMinute,
                            true
                        )
                    }
                    // تقویم پاپ‌آپ شمسی (جلالی) — به‌جای DatePickerDialog میلادی اندروید
                    var showJalaliDatePicker by remember { mutableStateOf(false) }
                    // فیلد نمایش رقمی تاریخ (روز/ماه/سال با جداساز «/»؛ سال به‌صورت خودکار 14xx)
                    var dateInputStr by remember { mutableStateOf("") }
                    fun applyJalaliDate(jy: Int, jm: Int, jd: Int) {
                        selectedDateMills = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
                        dateInputStr = "$jd".padStart(2, '0') + "/" + "$jm".padStart(2, '0') + "/" + "$jy"
                        val diffMs = (selectedDateMills ?: 0L) - System.currentTimeMillis()
                        val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs) + 1
                        dueDaysStr = if (diffDays > 0) diffDays.toString() else "0"
                    }
                    LaunchedEffect(Unit) {
                        // مقدار اولیه: تاریخ روانِ فردا (پیش‌فرض قبلی فرم: +۱ روز)
                        val initCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                        val j = com.example.util.PersianCalendarHelper.timestampToJalali(initCal.timeInMillis)
                        applyJalaliDate(j.year, j.month, j.day)
                    }
                    if (showJalaliDatePicker) {
                        JalaliDatePickerDialog(
                            initialTimestamp = selectedDateMills ?: System.currentTimeMillis(),
                            isPersian = isPersian,
                            onDateSelected = { jy, jm, jd ->
                                applyJalaliDate(jy, jm, jd)
                                showJalaliDatePicker = false
                                timePickerDialog.show()
                            },
                            onDismiss = { showJalaliDatePicker = false }
                        )
                    }

                    // Calculation of exact appointment date & time
                    val targetCalculatedMills = remember(dueDaysStr, selectedHour, selectedMinute, selectedDateMills) {
                        val days = dueDaysStr.toEnglishLong()
                        val cal = java.util.Calendar.getInstance()
                        if (selectedDateMills != null) {
                            cal.timeInMillis = selectedDateMills!!
                        } else {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, days.toInt())
                        }
                        cal.set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                        cal.set(java.util.Calendar.MINUTE, selectedMinute)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        cal.timeInMillis
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dueDaysStr,
                            onValueChange = { input -> 
                                dueDaysStr = input.filter { c -> c.isDigit() }.take(4)
                                selectedDateMills = null
                                // همگام‌سازی فیلد رقمی تاریخ با «چند روز بعد؟»
                                val d = dueDaysStr.toEnglishLong().toInt()
                                if (d in 0..3650) {
                                    val syncCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, d) }
                                    val j = com.example.util.PersianCalendarHelper.timestampToJalali(syncCal.timeInMillis)
                                    dateInputStr = j.day.toString().padStart(2, '0') + "/" + j.month.toString().padStart(2, '0') + "/" + j.year
                                }
                            },
                            label = { Text(if (isPersian) "چند روز بعد؟" else "In how many days?") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        val timeFormattedStr = String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                        OutlinedTextField(
                            value = timeFormattedStr,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "ساعت" else "Time") },
                            modifier = Modifier.weight(0.8f).clickable { timePickerDialog.show() },
                            trailingIcon = {
                                IconButton(onClick = { timePickerDialog.show() }) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Pick Time")
                                }
                            }
                        )
                    }

                    // فیلد نمایش رقمی تاریخ (روز/ماه/سال با «/») + دکمه تقویم شمسی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dateInputStr,
                            onValueChange = { input ->
                                // فقط ارقام؛ جداساز «/» خودکار درج و در بخش سال پیشوند «14» وارد می‌شود
                                dateInputStr = formatJalaliDayFirstInput(input)
                                val parsed = parseJalaliDayFirstInput(dateInputStr)
                                if (parsed != null) {
                                    val (d, m, y) = parsed
                                    applyJalaliDate(y, m, d)
                                }
                            },
                            label = { Text(if (isPersian) "تاریخ (روز/ماه/سال)" else "Date (DD/MM/YYYY)") },
                            placeholder = { Text("04/06/1404") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = { showJalaliDatePicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = if (isPersian) "تقویم شمسی" else "Jalali Calendar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }

                    // انتخاب یونیت/صندلی درمانی — پیش‌فرض یونیت ۱
                    var selectedUnit by remember { mutableStateOf("UNIT_1") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "UNIT_1" to (if (isPersian) "یونیت ۱" else "Chair 1"),
                            "UNIT_2" to (if (isPersian) "یونیت ۲ (جراحی)" else "Chair 2 (Surg)"),
                            "HYGIENE" to (if (isPersian) "یونیت بهداشت" else "Hygiene")
                        ).forEach { (uKey, uLabel) ->
                            FilterChip(
                                selected = selectedUnit == uKey,
                                onClick = { selectedUnit = uKey },
                                label = { Text(uLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.EventSeat, contentDescription = null, modifier = Modifier.size(13.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    com.example.ui.components.AssistantMultiSelectField(
                        selectedSyncIds = selectedAssistantSyncIds,
                        onSelectionChange = { selectedAssistantSyncIds = it },
                        allStaff = allStaffMembersForAppointments,
                        isPersian = isPersian
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(viewModel.label("notes")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Red calculated date/time preview text
                    val previewText = viewModel.formatDateFullVerbalWithTime(targetCalculatedMills)
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = previewText,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val pat = selectedPatient
                            if (pat != null) {
                                val reason = viewModel.getHolidayOrNonWorkingReason(targetCalculatedMills)
                                if (reason != null) {
                                    showHolidayConfirmQuestionDialog = reason to targetCalculatedMills
                                } else {
                                    val derivedTxType = if (selectedTreatmentIds.isNotEmpty()) (if (isPersian) "طرح های انتخابی" else "Selected Treatments") else (if (isPersian) "جلسه درمانی" else "Therapy Session")
                                    val conflictResult = com.example.util.AppointmentConflictDetector.checkConflict(
                                        targetDate = targetCalculatedMills,
                                        durationMinutes = 30,
                                        existingAppointments = appointments
                                    )
                                    if (conflictResult.hasConflict) {
                                        showHolidayConfirmQuestionDialog = conflictResult.messageFa to targetCalculatedMills
                                    } else {
                                    viewModel.addAppointment(
                                        Appointment(
                                            patientId = pat.id,
                                            patientName = pat.name,
                                            date = targetCalculatedMills,
                                            treatmentType = derivedTxType,
                                            note = note,
                                            unit = selectedUnit,
                                            plannedTreatmentIds = selectedTreatmentIds.joinToString(","),
                                            assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds)
                                        )
                                    )
                                    showForm = false
                                    selectedPatient = null
                                    note = ""
                                    selectedTreatmentIds = emptySet()
                                    selectedAssistantSyncIds = emptyList()
                                    selectedDateMills = null
                                }
                            }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "تایید و ذخیره اطلاعات" else "Confirm & Save Information", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showHolidayConfirmQuestionDialog != null) {
                val (reason, dateMills) = showHolidayConfirmQuestionDialog!!
                val dateVerbal = viewModel.formatDateFullVerbal(dateMills)
                AlertDialog(
                    onDismissRequest = { showHolidayConfirmQuestionDialog = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                            Text(
                                text = if (isPersian) "ثبت نوبت در روز تعطیل / غیرکاری" else "Book Appointment on Off Day",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isPersian) "تاریخ انتخاب شده ($dateVerbal) مصادف با «$reason» می‌باشد."
                                else "Selected date ($dateVerbal) is: '$reason'.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isPersian) "آیا از ثبت نوبت در این روز تعطیل یا غیرکاری مطب اطمینان دارید؟"
                                else "Are you sure you want to register an appointment on this off day?",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val pat = selectedPatient
                                if (pat != null) {
                                    val derivedTxType = if (selectedTreatmentIds.isNotEmpty()) (if (isPersian) "طرح های انتخابی" else "Selected Treatments") else (if (isPersian) "جلسه درمانی" else "Therapy Session")
                                    val conflictResult = com.example.util.AppointmentConflictDetector.checkConflict(
                                        targetDate = dateMills,
                                        durationMinutes = 30,
                                        existingAppointments = appointments
                                    )
                                    if (conflictResult.hasConflict) {
                                        showHolidayConfirmQuestionDialog = conflictResult.messageFa to dateMills
                                    } else {
                                    viewModel.addAppointment(
                                        Appointment(
                                            patientId = pat.id,
                                            patientName = pat.name,
                                            date = dateMills,
                                            treatmentType = derivedTxType,
                                            note = note,
                                            plannedTreatmentIds = selectedTreatmentIds.joinToString(","),
                                            assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds)
                                        )
                                    )
                                    showForm = false
                                    selectedPatient = null
                                    note = ""
                                    selectedTreatmentIds = emptySet()
                                    selectedAssistantSyncIds = emptyList()
                                }
                            }
                                showHolidayConfirmQuestionDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text(if (isPersian) "بله، ثبت نوبت در روز تعطیل" else "Yes, Book on Off Day", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showHolidayConfirmQuestionDialog = null }) {
                            Text(if (isPersian) "خیر / انصراف" else "Cancel")
                        }
                    }
                )
            }
        } else if (archiveFilter == "INCOMPLETE_SESSIONS") {
            // Specialized View for Incomplete Treatments and Planned Sessions
            val incompleteTreatments = allTreatments.filter { tr ->
                val patient = patients.find { it.id == tr.patientId }
                val patientName = patient?.name ?: ""
                val matchesQuery = patientName.contains(searchQuery, ignoreCase = true) ||
                        tr.title.contains(searchQuery, ignoreCase = true) ||
                        tr.selectedTeeth.contains(searchQuery)
                tr.status != "COMPLETED" && matchesQuery
            }

            val startedTreatments = incompleteTreatments.filter { it.status == "IN_PROGRESS" || it.completedStages.isNotBlank() }
            val plannedTreatments = incompleteTreatments.filter { it.status == "PLANNED" && it.completedStages.isBlank() }

            if (incompleteTreatments.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isPersian) "هیچ جلسه یا طرح درمان ناتمامی ثبت نشده است" else "No incomplete treatment sessions found",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (startedTreatments.isNotEmpty()) {
                        item {
                            Text(
                                text = if (isPersian) "۱. درمان‌های آغاز شده اما ناتمام (${startedTreatments.size} مورد)" else "1. In-Progress Treatments (${startedTreatments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                        items(startedTreatments.size) { idx ->
                            val tr = startedTreatments[idx]
                            val pat = patients.find { it.id == tr.patientId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pat?.name ?: (if (isPersian) "بیمار ناشناس" else "Unknown"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = tr.title + if (tr.selectedTeeth.isNotBlank()) " — دندان: ${tr.selectedTeeth}" else "",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (tr.stages.isNotBlank()) {
                                        val totalStages = tr.stages.split(",").filter { it.isNotBlank() }
                                        val completedList = tr.completedStages.split(",").filter { it.isNotBlank() }
                                        Text(
                                            text = if (isPersian) "پیشرفت مراحل: ${completedList.size} از ${totalStages.size} مرحله تکمیل شده" else "Progress: ${completedList.size}/${totalStages.size} completed",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(if (isPersian) "در حال انجام" else "In Progress", fontSize = 10.sp, color = Color(0xFFE65100)) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFF3E0))
                                        )
                                        Button(
                                            onClick = {
                                                selectedPatient = pat
                                                selectedTreatmentIds = setOf(tr.id)
                                                showForm = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "ثبت نوبت بعدی" else "Book Next Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (plannedTreatments.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian) "۲. درمان‌های ثبت شده اما شروع نشده (${plannedTreatments.size} مورد)" else "2. Planned Treatments (${plannedTreatments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(plannedTreatments.size) { idx ->
                            val tr = plannedTreatments[idx]
                            val pat = patients.find { it.id == tr.patientId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pat?.name ?: (if (isPersian) "بیمار ناشناس" else "Unknown"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = tr.title + if (tr.selectedTeeth.isNotBlank()) " — دندان: ${tr.selectedTeeth}" else "",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(if (isPersian) "شروع نشده" else "Planned", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                        Button(
                                            onClick = {
                                                selectedPatient = pat
                                                selectedTreatmentIds = setOf(tr.id)
                                                showForm = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isPersian) "ثبت نوبت اول" else "Book First Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Appointments List
            if (filteredAppointments.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    com.example.ui.components.EmptyStateView(
                        title = if (searchQuery.isNotBlank() || archiveFilter != "ALL") (if (isPersian) "هیچ نوبتی با این مشخصات یافت نشد" else "No appointments found") else viewModel.label("no_appointments"),
                        subtitle = if (isPersian) "برای ثبت سریع نوبت، دکمه «نوبت‌دهی سریع» را لمس کنید." else "Click Quick Appointment to book a new slot.",
                        actionLabel = viewModel.label("quick_appointment"),
                        onAction = { showForm = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    appointmentsByDay.forEach { (dayMillis, dayAppts) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // هدر روز — هر روز در کادر مجزای خودش
                                Text(
                                    text = viewModel.formatDateFullVerbal(dayMillis),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                dayAppts.forEach { appt ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(text = appt.patientName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(appt.date))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "🕒 $timeStr",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            // نمایش یونیت/صندلی اختصاص‌یافته به این نوبت
                                            val apptUnitLabel = when (appt.unit ?: "UNIT_1") {
                                                "UNIT_2" -> if (isPersian) "یونیت ۲" else "Chair 2"
                                                "HYGIENE" -> if (isPersian) "بهداشت" else "Hygiene"
                                                else -> if (isPersian) "یونیت ۱" else "Chair 1"
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "🦷 $apptUnitLabel",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(text = appt.treatmentType, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        Text(text = viewModel.formatDateFullVerbal(appt.date), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        com.example.ui.components.AssistantNamesRow(
                                            assistantIds = appt.assistantIds,
                                            allStaff = allStaffMembersForAppointments,
                                            isPersian = isPersian,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (appt.note.isNotEmpty()) {
                                            Text(text = appt.note, fontSize = 11.sp, color = Color.Gray)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        var statusDropdownExpanded by remember { mutableStateOf(false) }
                                        val statusMap = mapOf(
                                            "PENDING" to if (isPersian) "در انتظار" else "Pending",
                                            "IN_OFFICE" to if (isPersian) "داخل مطب" else "In Office",
                                            "COMPLETED" to if (isPersian) "تکمیل‌شده" else "Completed",
                                            "CANCELED" to if (isPersian) "کنسل‌شده" else "Canceled"
                                        )
                                        val statusColor = when (appt.status) {
                                            "PENDING" -> Color(0xFFFFA000)
                                            "IN_OFFICE" -> Color(0xFF0288D1)
                                            "COMPLETED" -> Color(0xFF388E3C)
                                            "CANCELED" -> Color(0xFFD32F2F)
                                            else -> Color.Gray
                                        }

                                        Box {
                                            Surface(
                                                color = statusColor.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.clickable { statusDropdownExpanded = true }
                                            ) {
                                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statusColor))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = statusMap[appt.status] ?: appt.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = statusDropdownExpanded,
                                                onDismissRequest = { statusDropdownExpanded = false }
                                            ) {
                                                statusMap.forEach { (key, value) ->
                                                    DropdownMenuItem(
                                                        text = { Text(value, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateAppointment(appt.copy(status = key, isCompleted = (key == "COMPLETED")))
                                                            statusDropdownExpanded = false
                                                            if (key == "COMPLETED") {
                                                                apptToComplete = appt // For navigating to treatment plan
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                val isArchivedOrCompleted = appt.isArchived || appt.isCompleted || archiveFilter == "PAST"

                                if (isArchivedOrCompleted) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = if (appt.dischargeSent) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    if (appt.dischargeSent) Icons.Default.CheckCircle else Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = if (appt.dischargeSent) Color(0xFF2E7D32) else Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (appt.dischargeSent) (if (isPersian) "توضیحات ترخیص ارسال شد (${appt.dischargeMethod})" else "Discharge Sent (${appt.dischargeMethod})")
                                                    else (if (isPersian) "توضیحات ترخیص ارسال نشده" else "Discharge Not Sent"),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (appt.dischargeSent) Color(0xFF2E7D32) else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }

                                // Action buttons in a single row under the appointment field
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { sessionAppointment = appt },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(if (isArchivedOrCompleted) Icons.Default.Visibility else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isArchivedOrCompleted) (if (isPersian) "مشاهده جلسه" else "View Session")
                                            else (if (isPersian) "شروع جلسه" else "Start Session"),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isArchivedOrCompleted) {
                                        OutlinedIconButton(
                                            onClick = { dischargeApprovalAppt = Pair(appt, "پیامک") },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Message,
                                                contentDescription = if (isPersian) "ارسال توضیحات ترخیص پیامکی" else "Discharge SMS",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = { dischargeApprovalAppt = Pair(appt, "واتساپ") },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Chat,
                                                contentDescription = if (isPersian) "ارسال توضیحات ترخیص واتساپ" else "Discharge WhatsApp",
                                                tint = Color(0xFF25D366),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedIconButton(
                                            onClick = {
                                                val pObj = patients.find { it.name == appt.patientName }
                                                val msg = buildAppointmentReminderText(isPersian, appt.patientName, appt.date, viewModel, pObj?.gender ?: "مرد")
                                                sendSmsPrefilled(context, pObj?.phone ?: "", msg)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Message,
                                                contentDescription = if (isPersian) "اطلاع‌رسانی پیامکی" else "Notify via SMS",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = {
                                                val pObj = patients.find { it.name == appt.patientName }
                                                val msg = buildAppointmentReminderText(isPersian, appt.patientName, appt.date, viewModel, pObj?.gender ?: "مرد")
                                                val file = generateSimpleInfoCardImage(
                                                    context,
                                                    title = if (isPersian) "یادآوری نوبت مطب" else "Appointment Reminder",
                                                    lines = msg.split("۔", "\n").filter { it.isNotBlank() }
                                                )
                                                val phone = pObj?.phone
                                                shareImageToWhatsApp(context, file, phone)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Chat,
                                                contentDescription = if (isPersian) "اطلاع‌رسانی از طریق واتساپ" else "Notify via WhatsApp",
                                                tint = Color(0xFF25D366),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = { apptToComplete = appt },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Archive, contentDescription = if (isPersian) "آرشیو نوبت" else "Archive", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        OutlinedIconButton(
                                            onClick = { apptToDelete = appt },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                                } // end dayAppts.forEach
                            }
                        }
                    } // end item
                    } // end appointmentsByDay.forEach
                }
            }
        }
    }

    // Deletion Confirmation Dialog
    if (apptToDelete != null) {
        AlertDialog(
            onDismissRequest = { apptToDelete = null },
            title = { Text(if (isPersian) "حذف نوبت دندانپزشکی" else "Delete Appointment", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف نوبت بیمار «${apptToDelete?.patientName}» اطمینان دارید؟" else "Are you sure you want to delete this appointment?") },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = apptToDelete!!
                        viewModel.deleteAppointment(deleted)
                        apptToDelete = null
                        showUndoSnackbar(
                            scope = coroutineScope,
                            hostState = snackbarHostState,
                            message = if (isPersian) "نوبت «${deleted.patientName}» حذف شد" else "Appointment for '${deleted.patientName}' deleted",
                            actionLabel = if (isPersian) "بازگردانی" else "Undo",
                            onUndo = { viewModel.undoDeleteAppointment(deleted) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف نهایی" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { apptToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    if (apptToComplete != null) {
        AlertDialog(
            onDismissRequest = { apptToComplete = null },
            title = { Text(if (isPersian) "تکمیل و آرشیو نوبت دندانپزشکی" else "Complete & Archive Appointment", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "نوبت بیمار «${apptToComplete?.patientName}» به وضعیت تکمیل‌شده تغییر یافت. مایلید به پرونده بیمار و بخش صدور فاکتور هدایت شوید؟ (طرح درمان‌های مرتبط نیز به وضعیت تکمیل شده تغییر خواهند کرد)" else "Appointment marked as completed. Go to Treatment Plan / Payment?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val currentAppt = apptToComplete!!
                            viewModel.archiveAppointment(currentAppt)
                            
                            // Navigation
                            viewModel.selectedPatientId.value = currentAppt.patientId
                            viewModel.currentTab.value = "patients"
                            
                            apptToComplete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text(if (isPersian) "انتقال به بخش مالی" else "Go to Financial") }
                    
                    OutlinedButton(
                        onClick = {
                            val currentAppt = apptToComplete!!
                            viewModel.archiveAppointment(currentAppt)
                            
                            // Generate combined prescription
                            val txIds = currentAppt.plannedTreatmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                            val sessionTreatments = allTreatments.filter { txIds.contains(it.id) }
                            val rxPatient = patients.find { it.id == currentAppt.patientId }
                            
                            var cAnalgesic = mutableSetOf<String>()
                            var cAntibiotic = mutableSetOf<String>()
                            var cMouthwash = mutableSetOf<String>()
                            var cOther = mutableSetOf<String>()
                            var cNotes = mutableSetOf<String>()
                            var combinedTitle = mutableSetOf<String>()
                            
                            val titlesToSearch = mutableListOf<String>()
                            sessionTreatments.forEach { tx -> titlesToSearch.add(tx.title) }
                            if (titlesToSearch.isEmpty() && currentAppt.treatmentType.isNotBlank()) {
                                titlesToSearch.add(currentAppt.treatmentType)
                            }
                            
                            titlesToSearch.forEach { title ->
                                combinedTitle.add(title)
                                val baseSuggestion = com.example.data.PrescriptionKnowledgeBase.suggest(title, "تکمیل جلسه")
                                if (baseSuggestion != null && !baseSuggestion.isBlank()) {
                                    if (baseSuggestion.analgesic.isNotBlank()) cAnalgesic.add(baseSuggestion.analgesic)
                                    if (baseSuggestion.antibiotic.isNotBlank()) cAntibiotic.add(baseSuggestion.antibiotic)
                                    if (baseSuggestion.mouthwash.isNotBlank()) cMouthwash.add(baseSuggestion.mouthwash)
                                    if (baseSuggestion.otherMeds.isNotBlank()) cOther.add(baseSuggestion.otherMeds)
                                    if (baseSuggestion.notes.isNotBlank()) cNotes.add(baseSuggestion.notes)
                                }
                            }
                            
                            val combinedPrescription = com.example.data.DrugPrescription(
                                analgesic = cAnalgesic.joinToString(" + "),
                                antibiotic = cAntibiotic.joinToString(" + "),
                                mouthwash = cMouthwash.joinToString(" + "),
                                otherMeds = cOther.joinToString(" + "),
                                notes = (cNotes + listOf(currentAppt.note)).filter { it.isNotBlank() }.joinToString("\n")
                            )
                            
                            val adjusted = com.example.data.PrescriptionKnowledgeBase.adjustForPatient(
                                combinedPrescription,
                                rxPatient?.medicalHistory ?: "",
                                rxPatient?.medicalAlerts ?: ""
                            )
                            val displayTitle = if (combinedTitle.isNotEmpty()) combinedTitle.joinToString(" / ") else currentAppt.treatmentType.ifBlank { "تکمیل جلسه درمان" }
                            val dummyTx = sessionTreatments.firstOrNull()?.copy(title = displayTitle) 
                                ?: com.example.data.Treatment(id = 0L, patientId = currentAppt.patientId, title = displayTitle, cost = 0)
                            
                            pendingRxForAppt = PendingRxPopup(treatment = dummyTx, stageName = "تکمیل و آرشیو جلسه", prescription = adjusted, patient = rxPatient)
                            
                            apptToComplete = null
                        }
                    ) { Text(if (isPersian) "فقط ثبت و نسخه" else "Only Archive & Rx") }
                }
            },
            dismissButton = {
                TextButton(onClick = { apptToComplete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    // Doctor Approval Dialog for Discharge & Post-Care Instructions
    if (pendingRxForAppt != null) {
        val rxState = pendingRxForAppt!!
        PrescriptionApprovalDialog(
            viewModel = viewModel,
            treatment = rxState.treatment,
            patient = rxState.patient,
            stageName = rxState.stageName,
            suggested = rxState.prescription,
            onDismiss = { pendingRxForAppt = null }
        )
    }

    if (dischargeApprovalAppt != null) {
        val (targetAppt, method) = dischargeApprovalAppt!!
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val defaultText = remember(targetAppt.id) {
            TherapyKnowledgeBase.generateDischargeInstructions(
                treatmentTitle = targetAppt.treatmentType,
                patientName = targetAppt.patientName,
                clinicName = settings.clinicName,
                isPersian = isPersian
            )
        }
        var approvedText by remember(targetAppt.id) { mutableStateOf(defaultText) }

        AlertDialog(
            onDismissRequest = { dischargeApprovalAppt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isPersian) "تایید پزشک جهت ارسال توضیحات ترخیص" else "Doctor Approval - Post-Care Instructions",
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
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isPersian) "این توضیحات به‌صورت خودکار مطابق تراپی‌های این جلسه تهیه شده است. قبل از ارسال، بررسی و تایید پزشک الزامی است."
                            else "Auto-generated per session therapies. Requires doctor approval before dispatch.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = approvedText,
                        onValueChange = { approvedText = it },
                        label = { Text(if (isPersian) "متن توضیحات ترخیص و مراقبتی (قابل ویرایش)" else "Post-Care Instructions (Editable)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val patPhone = patients.find { it.name == targetAppt.patientName }?.phone ?: ""
                        if (method == "پیامک" || method == "SMS") {
                            sendSmsPrefilled(context, patPhone, approvedText)
                            viewModel.updateAppointment(targetAppt.copy(dischargeSent = true, dischargeMethod = "پیامک"))
                        } else {
                            val patObj = patients.find { it.name == targetAppt.patientName }
                            val cardFile = com.example.reports.generatePostOpCareInstructionsCardImage(
                                context = context,
                                clinicName = viewModel.getSettings().clinicName,
                                doctorName = viewModel.getSettings().doctorName,
                                patientName = targetAppt.patientName,
                                patientFatherName = patObj?.fatherName ?: "",
                                patientNationalId = patObj?.nationalId ?: "",
                                treatmentTitle = targetAppt.treatmentType,
                                careInstructions = approvedText.split("\n").filter { it.isNotBlank() },
                                emergencyPhone = viewModel.getSettings().clinicPhone,
                                themeSpec = viewModel.currentExportTheme()
                            )
                            val phone = patObj?.phone
                            shareImageToWhatsApp(context, cardFile, phone)
                            viewModel.updateAppointment(targetAppt.copy(dischargeSent = true, dischargeMethod = "واتساپ"))
                        }
                        dischargeApprovalAppt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(if (method == "پیامک" || method == "SMS") Icons.AutoMirrored.Filled.Message else Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPersian) "تایید پزشک و ارسال ($method)" else "Approve & Send ($method)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dischargeApprovalAppt = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    // Interactive Session Details & Stage Management Dialog
    if (sessionAppointment != null) {
        val appt = sessionAppointment!!
        val patientTreatments = allTreatments.filter { it.patientId == appt.patientId }
        val allTemplatesList by viewModel.templates.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()

        val plannedIds = remember(appt.plannedTreatmentIds) { appt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet() }
        val sessionTreatments = remember(patientTreatments, plannedIds) { patientTreatments.filter { plannedIds.contains(it.id) } }
        val combinedSessionSummary = remember(sessionTreatments) {
            if (sessionTreatments.isEmpty()) {
                if (isPersian) "جلسه درمانی" else "Therapy Session"
            } else {
                sessionTreatments.joinToString(" + ") { tx ->
                    val cleanTitle = tx.title.lineSequence().firstOrNull()?.trim().orEmpty()
                    val teethPart = if (tx.selectedTeeth.isNotBlank()) " (دندان ${tx.selectedTeeth})" else ""
                    "$cleanTitle$teethPart"
                }
            }
        }

        var showManageTherapiesDialog by remember { mutableStateOf(false) }
        var editNotes by remember { mutableStateOf(appt.note) }
        var editTxType by remember(appt.id, appt.treatmentType, combinedSessionSummary) {
            mutableStateOf(
                if (appt.treatmentType.isBlank() || appt.treatmentType == "Selected Treatments" || appt.treatmentType == "طرح های انتخابی") combinedSessionSummary
                else appt.treatmentType
            )
        }
        var selectedTemplateForTx by remember { mutableStateOf<Pair<Long, TreatmentTemplate>?>(null) }
        var sessionFinishConfirmed by remember(appt.id) { mutableStateOf(appt.isCompleted) }

        if (showManageTherapiesDialog) {
            AppointmentSessionDialog(
                appointment = appt,
                viewModel = viewModel,
                onDismiss = { showManageTherapiesDialog = false }
            )
        }

        AlertDialog(
            onDismissRequest = { sessionAppointment = null },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isPersian) "مدیریت جلسات و تراپی درمان" else "Session & Therapy Stage Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${if (isPersian) "بیمار: " else "Patient: "}${appt.patientName} | ${viewModel.formatDate(appt.date)}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editTxType,
                        onValueChange = { editTxType = it },
                        label = { Text(if (isPersian) "عنوان درمان / شرح خلاصه تمام تراپی‌های جلسه" else "Session Treatment Title") },
                        trailingIcon = {
                            IconButton(onClick = { editTxType = combinedSessionSummary }) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = "Autofill session summary",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text(viewModel.label("notes")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Stages fetched directly from Database for this patient
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "تراپی‌های این جلسه:" else "Session Therapies:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { showManageTherapiesDialog = true },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (isPersian) "ویرایش تراپی‌های جلسه" else "Manage Session Therapies", fontSize = 10.sp)
                        }
                    }
                    val plannedIds = remember(appt.plannedTreatmentIds) { appt.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet() }
                    val sessionTreatments = patientTreatments.filter { plannedIds.contains(it.id) }

                    if (sessionTreatments.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isPersian) "هیچ طرح درمانی برای این بیمار در پرونده ثبت نشده است." else "No treatment plans recorded for this patient.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        sessionTreatments.forEach { tx ->
                            val patientForProtocol = remember(appt.patientId) { patients.find { it.id == appt.patientId } }
                            // Auto-fetch stages from ready templates if tx.stages is empty
                            var txStagesStr = tx.stages
                            if (txStagesStr.isBlank() && allTemplatesList.isNotEmpty()) {
                                val matchedTemp = allTemplatesList.find { temp ->
                                    tx.title.contains(temp.title) || temp.title.contains(tx.title)
                                }
                                if (matchedTemp != null && matchedTemp.stages.isNotBlank()) {
                                    txStagesStr = matchedTemp.stages
                                    viewModel.updateTreatment(tx.copy(stages = matchedTemp.stages))
                                }
                            }

                            val allStagesList = remember(txStagesStr) {
                                parseStagesList(txStagesStr)
                            }
                            val completedStagesList = remember(tx.completedStages) {
                                tx.completedStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }
                            val ignoredStagesList = remember(tx.ignoredStages) {
                                tx.ignoredStages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }
                            val remainingStagesList = remember(allStagesList, completedStagesList, ignoredStagesList) {
                                allStagesList.filter { !completedStagesList.contains(it) && !ignoredStagesList.contains(it) }
                            }
                            // دیالوگ ادیت مرحله جاری در همان نشست (بدون خروج از روند جلسه)
                            var showEditStageDialog by remember { mutableStateOf(false) }
                            var stageEditNoteDraft by remember(tx.id) { mutableStateOf("") }
                            // دیالوگ شرح مفصل پروتکل بالینی تراپی (با لمس طولانی روی عنوان تراپی باز می‌شود)
                            var showTherapyProtocolDialog by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (remainingStagesList.isEmpty() && allStagesList.isNotEmpty()) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Treatment Title & Status Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = run {
                                                        val sessionNumber = completedStagesList.size + 1
                                                        val shortTitle = tx.title.lineSequence().firstOrNull()?.trim().orEmpty()
                                                        "$shortTitle — ${if (isPersian) "جلسه $sessionNumber" else "Session $sessionNumber"}"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.threeSecondLongPress(
                                                        onLongClick = {
                                                            if (TherapyKnowledgeBase.findProtocol(tx.title) != null) {
                                                                showTherapyProtocolDialog = true
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            if (tx.selectedTeeth.isNotBlank()) {
                                                Text(text = "${if (isPersian) "دندان:" else "Tooth:"} ${tx.selectedTeeth}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        Surface(
                                            color = when (tx.status) {
                                                "COMPLETED" -> Color(0xFFE8F5E9)
                                                "IN_PROGRESS" -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = when (tx.status) {
                                                    "COMPLETED" -> if (isPersian) "✓ تکمیل‌شده" else "Completed"
                                                    "CANCELLED" -> if (isPersian) "✕ لغوشده" else "Cancelled"
                                                    else -> {
                                                        val allStages = parseStagesList(tx.stages)
                                                        val completedStages = tx.completedStages.split(",").map{it.trim()}.filter{it.isNotEmpty()}
                                                        val ignoredStages = tx.ignoredStages.split(",").map{it.trim()}.filter{it.isNotEmpty()}
                                                        val resolvedStages = (completedStages + ignoredStages).distinct()
                                                        val completedCount = resolvedStages.size
                                                        val totalSessions = allStages.size
                                                        val hasIncompleted = allStages.isNotEmpty() && !resolvedStages.containsAll(allStages)
                                                        
                                                        if (hasIncompleted) {
                                                            if (isPersian) "◐ در حال انجام (${completedCount}/${totalSessions} جلسه)" else "In Progress (${completedCount}/${totalSessions})"
                                                        } else {
                                                            if (isPersian) "○ برنامه‌ریزی‌شده" else "Planned"
                                                        }
                                                    }
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (tx.status) {
                                                    "COMPLETED" -> Color(0xFF2E7D32)
                                                    "IN_PROGRESS" -> MaterialTheme.colorScheme.primary
                                                    else -> Color.Gray
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Option to import/sync stages from ready templates if empty
                                    if (allStagesList.isEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    Text(
                                                        text = if (isPersian) "فراخوانی مراحل درمان از الگوهای آماده:" else "Import stages from ready templates:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    allTemplatesList.forEach { template ->
                                                        AssistChip(
                                                            onClick = {
                                                                viewModel.updateTreatment(tx.copy(stages = template.stages))
                                                            },
                                                            label = { Text(if (isPersian) template.titleFa.ifBlank { template.title } else template.title, fontSize = 10.sp) },
                                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Progress Bar
                                        val totalDone = completedStagesList.size + ignoredStagesList.size
                                        val progress = if (allStagesList.isEmpty()) 0f else totalDone.toFloat() / allStagesList.size.toFloat()
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(
                                                    text = "${if (isPersian) "پیشرفت مراحل تراپی:" else "Stage Progress:"} $totalDone از ${allStagesList.size}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            )
                                        }

                                        // Section 1: Completed stages in previous sessions
                                        if (completedStagesList.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = if (isPersian) "✓ مراحل انجام‌شده در جلسات قبلی:" else "✓ Completed in previous sessions:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                completedStagesList.forEach { doneStage ->
                                                    val isRepeated = doneStage.startsWith("[REPEAT] ")
                                                    val displayStage = if (isRepeated) doneStage.removePrefix("[REPEAT] ") else doneStage
                                                    val tickColor = Color(0xFF2E7D32)
                                                    val surfaceColor = Color(0xFFE8F5E9)
                                                    val textColor = Color(0xFF1B5E20)
                                                    
                                                    Surface(
                                                        color = surfaceColor,
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tickColor, modifier = Modifier.size(14.dp))
                                                                Text(
                                                                    text = displayStage,
                                                                    fontSize = 11.sp,
                                                                    color = textColor,
                                                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { viewModel.toggleTreatmentStage(tx, doneStage) },
                                                                modifier = Modifier.size(18.dp)
                                                            ) {
                                                                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                            }
                                        }

                                        // Section 2: Active stage for THIS session & Future queued stages
                                        val activeCurrentStage = remainingStagesList.firstOrNull()
                                        val futureQueuedStages = remainingStagesList.drop(1)

                                        if (activeCurrentStage != null) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = if (isPersian) "📍 مرحله اختصاصی جهت اجرا در این جلسه:" else "📍 Active Stage for THIS Session:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                // Primary Active Stage Card for Current Session
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                Text(
                                                                    text = activeCurrentStage,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }

                                                        Button(
                                                            onClick = {
                                                                // Confirm and execute stage in this session
                                                                viewModel.toggleTreatmentStage(tx, activeCurrentStage)
                                                                val updatedType = if (sessionTreatments.isNotEmpty()) {
                                                                    sessionTreatments.joinToString(" + ") { sessionTx ->
                                                                        val cleanT = sessionTx.title.lineSequence().firstOrNull()?.trim().orEmpty()
                                                                        if (sessionTx.id == tx.id) {
                                                                            "$cleanT ($activeCurrentStage)"
                                                                        } else {
                                                                            cleanT
                                                                        }
                                                                    }
                                                                } else {
                                                                    "${tx.title} ($activeCurrentStage)"
                                                                }
                                                                editTxType = updatedType
                                                                viewModel.updateAppointment(appt.copy(treatmentType = updatedType, note = editNotes))
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(vertical = 6.dp)
                                                        ) {
                                                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = if (isPersian) "✓ تایید و ثبت انجام این مرحله در این جلسه" else "✓ Confirm & Complete Stage in this Session",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        // ردیف اکشن‌های تکمیلی: ایگنور کردن / ادیت همین جلسه / تکرار جلسه قبلی
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    // بی‌نیازی از این مرحله: ایگنور کردن بدون علامت‌گذاری به‌عنوان انجام‌شده
                                                                    viewModel.ignoreTreatmentStage(tx, activeCurrentStage)
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
                                                            ) {
                                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (isPersian) "عدم نیاز به این مرحله" else "Skip Stage",
                                                                    fontSize = 9.sp
                                                                )
                                                            }
                                                            OutlinedButton(
                                                                onClick = {
                                                                    stageEditNoteDraft = activeCurrentStage ?: ""
                                                                    showEditStageDialog = true
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (isPersian) "ادیت همین جلسه" else "Edit Session",
                                                                    fontSize = 9.sp
                                                                )
                                                            }
                                                            
                                                            var showNewStageDialog by remember { mutableStateOf(false) }
                                                            var newStageNameDraft by remember { mutableStateOf("") }

                                                            OutlinedButton(
                                                                onClick = {
                                                                    newStageNameDraft = ""
                                                                    showNewStageDialog = true
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (isPersian) "مرحله جدید" else "New Stage",
                                                                    fontSize = 9.sp
                                                                )
                                                            }

                                                            if (showNewStageDialog) {
                                                                AlertDialog(
                                                                    onDismissRequest = { showNewStageDialog = false },
                                                                    title = {
                                                                        Text(
                                                                            if (isPersian) "افزودن مرحله جدید" else "Add New Stage",
                                                                            fontSize = 14.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    },
                                                                    text = {
                                                                        OutlinedTextField(
                                                                            value = newStageNameDraft,
                                                                            onValueChange = { newStageNameDraft = it },
                                                                            placeholder = { Text(if (isPersian) "نام مرحله جدید را وارد کنید..." else "Enter new stage name...") },
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            textStyle = TextStyle(fontSize = 12.sp)
                                                                        )
                                                                    },
                                                                    confirmButton = {
                                                                        Button(
                                                                            onClick = {
                                                                                if (newStageNameDraft.isNotBlank()) {
                                                                                    val newStageName = newStageNameDraft.trim()
                                                                                    val newStagesList = allStagesList.toMutableList()
                                                                                    val index = if (remainingStagesList.isNotEmpty()) {
                                                                                        newStagesList.indexOf(remainingStagesList.first())
                                                                                    } else {
                                                                                        newStagesList.size
                                                                                    }
                                                                                    if (index >= 0) {
                                                                                        newStagesList.add(index, newStageName)
                                                                                    } else {
                                                                                        newStagesList.add(newStageName)
                                                                                    }
                                                                                    val newStagesStr = newStagesList.joinToString(" - ")
                                                                                    viewModel.updateTreatment(tx.copy(stages = newStagesStr))
                                                                                }
                                                                                showNewStageDialog = false
                                                                            }
                                                                        ) {
                                                                            Text(if (isPersian) "تایید و ثبت" else "Confirm", fontSize = 12.sp)
                                                                        }
                                                                    },
                                                                    dismissButton = {
                                                                        TextButton(onClick = { showNewStageDialog = false }) {
                                                                            Text(if (isPersian) "انصراف" else "Cancel", fontSize = 12.sp)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (showEditStageDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showEditStageDialog = false },
                                                        title = {
                                                            Text(if (isPersian) "ادیت جلسه جاری" else "Edit Current Session")
                                                        },
                                                        text = {
                                                            OutlinedTextField(
                                                                value = stageEditNoteDraft,
                                                                onValueChange = { stageEditNoteDraft = it },
                                                                label = { Text(if (isPersian) "یادداشت این جلسه" else "Session Note") },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                minLines = 3
                                                            )
                                                        },
                                                        confirmButton = {
                                                            TextButton(onClick = {
                                                                if (activeCurrentStage != null && stageEditNoteDraft.isNotBlank()) {
                                                                    viewModel.replaceTreatmentStage(tx, activeCurrentStage, stageEditNoteDraft)
                                                                }
                                                                showEditStageDialog = false
                                                            }) {
                                                                Text(if (isPersian) "ذخیره" else "Save")
                                                            }
                                                        },
                                                        dismissButton = {
                                                            TextButton(onClick = { showEditStageDialog = false }) {
                                                                Text(if (isPersian) "انصراف" else "Cancel")
                                                            }
                                                        }
                                                    )
                                                }

                                                if (showTherapyProtocolDialog) {
                                                    ClinicalProtocolDialog(
                                                        therapyTitle = tx.title,
                                                        patientMedicalHistory = patientForProtocol?.medicalHistory ?: "",
                                                        patientMedicalAlerts = patientForProtocol?.medicalAlerts ?: "",
                                                        isPersian = isPersian,
                                                        initialFontSizePt = settings.clinicalFontSizePt,
                                                        onDismiss = { showTherapyProtocolDialog = false }
                                                    )
                                                }

                                                // Section 3: Future queued stages for upcoming sessions
                                                if (futureQueuedStages.isNotEmpty()) {
                                                    Text(
                                                        text = if (isPersian) "⏳ مراحل و جلسات بعدی تراپی (در نوبت درمان):" else "⏳ Upcoming Future Therapy Sessions:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Gray,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )

                                                    futureQueuedStages.forEachIndexed { fIdx, futureStage ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                                    Text(
                                                                        text = futureStage,
                                                                        fontSize = 11.sp,
                                                                        color = Color.DarkGray
                                                                    )
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = if (isPersian) "جلسه ${completedStagesList.size + fIdx + 2}" else "Session ${completedStagesList.size + fIdx + 2}",
                                                                            fontSize = 9.sp,
                                                                            color = Color.Gray,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // All stages finished
                                            Surface(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                                    Text(
                                                        text = if (isPersian) "تمامی مراحل تراپی این درمان با موفقیت به پایان رسید." else "All therapy stages for this treatment have been completed.",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Root Canal Working Length (Endo Working Length)
                                    EndoWorkingLengthCard(treatment = tx, viewModel = viewModel)

                                    // Restoration Surface Model (Editable)
                                    ToothSurfaceRestorationCard(treatment = tx, viewModel = viewModel)
                                }
                            }
                        }
                    }

                    // پایان جلسه و آرشیو نوبت — در انتهای صفحه مدیریت جلسات و تراپی درمان
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = sessionFinishConfirmed, onCheckedChange = { sessionFinishConfirmed = it })
                        Text(
                            text = if (isPersian) "این جلسه به پایان رسید (اتمام کردن)" else "This session has finished",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sessionFinishConfirmed && !appt.isArchived) {
                            viewModel.archiveAppointment(appt)
                            val rxPatient = patients.find { it.id == appt.patientId }
                            val baseSuggestion = com.example.data.PrescriptionKnowledgeBase.suggest(editTxType, "تکمیل جلسه") ?: com.example.data.DrugPrescription()
                            val baseWithNotes = baseSuggestion.copy(notes = if (editNotes.isNotBlank()) "${baseSuggestion.notes}\n$editNotes".trim() else baseSuggestion.notes)
                            val adjusted = com.example.data.PrescriptionKnowledgeBase.adjustForPatient(
                                baseWithNotes,
                                rxPatient?.medicalHistory ?: "",
                                rxPatient?.medicalAlerts ?: ""
                            )
                            val dummyTx = com.example.data.Treatment(id = 0L, patientId = appt.patientId, title = editTxType.ifBlank { "تکمیل جلسه درمان" }, cost = 0)
                            pendingRxForAppt = PendingRxPopup(treatment = dummyTx, stageName = "تکمیل و آرشیو جلسه", prescription = adjusted, patient = rxPatient)
                        } else {
                            viewModel.updateAppointment(appt.copy(treatmentType = editTxType, note = editNotes, isCompleted = sessionFinishConfirmed))
                        }
                        sessionAppointment = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(viewModel.label("save"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { sessionAppointment = null }) { Text(viewModel.label("cancel")) }
            }
        )
    }

}

// ==========================================
// MORE MODULES MENU SELECTION GRID
// ==========================================
@Composable
fun MoreModulesGridScreen(viewModel: DentalViewModel) {
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isPersian) "پنل‌های تخصصی و حسابداری مطب" else "Specialized Modules & General Ledgers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1: Vendors & Labs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("vendors"),
                        desc = if (isPersian) "تدارکات و خرید اقلام مصرفی" else "Suppliers & supplies orders",
                        icon = Icons.Default.ShoppingCart,
                        onClick = { viewModel.currentTab.value = "vendors" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = viewModel.label("labs"),
                        desc = if (isPersian) "ساخت روکش و پروتزهای دندانی" else "Prosthetics, crowns & dentures",
                        icon = Icons.Default.Build,
                        onClick = { viewModel.currentTab.value = "labs" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Equipment Maintenance & Clinic Expenses
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("maintenance"),
                        desc = if (isPersian) "تعمیر اتوکلاو، توربین و یونیت" else "Repairs & dental chair maintenance",
                        icon = Icons.Default.Handyman,
                        onClick = { viewModel.currentTab.value = "maintenance" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = viewModel.label("expenses"),
                        desc = if (isPersian) "قبوض، اجاره مطب، حقوق پرسنل" else "Clinic bills, salaries & utilities",
                        icon = Icons.Default.ReceiptLong,
                        onClick = { viewModel.currentTab.value = "expenses" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Dentist Personal Accounting
                // (توجه: «الگوهای آماده درمان» به منوی تنظیمات اختصاصی — لمس ۲ثانیه‌ای چرخ‌دنده بالای صفحه — منتقل شد)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = viewModel.label("personal"),
                        desc = if (isPersian) "حساب‌ها و برداشت‌های شخصی پزشک" else "Private bookkeeping & drawers ledger",
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = { viewModel.currentTab.value = "personal" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 4: Sayadi Cheques Management & Bank Accounts
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = if (isPersian) "مدیریت چک‌های صیادی" else "Sayad Cheques Ledger",
                        desc = if (isPersian) "اسکن صیاد، پاس، برگشتی، انتقال چک" else "Scan, clear, bounce & endorse cheques",
                        icon = Icons.Default.ConfirmationNumber,
                        onClick = { viewModel.currentTab.value = "cheques" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = if (isPersian) "حساب‌ها و کارت‌ها" else "Bank Accounts",
                        desc = if (isPersian) "مدیریت موجودی حساب‌های بانکی" else "Manage bank balances",
                        icon = Icons.Default.CreditCard,
                        onClick = { viewModel.currentTab.value = "bank" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 5: Clinic Settings & Reports Center
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = if (isPersian) "شخصی‌سازی مطب" else "Clinic Branding",
                        desc = if (isPersian) "نام کلینیک، پزشک و چیدمان داشبورد" else "Branding, staff list & dashboard layout",
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = { viewModel.currentTab.value = "settings" },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleMenuCard(
                        title = if (isPersian) "گزارش‌ها و اسناد" else "Reports & Documents",
                        desc = if (isPersian) "چاپ فاکتور، صورت‌حساب و گزارش‌های مالی PDF" else "Print invoices, statements & financial PDFs",
                        icon = Icons.Default.Description,
                        onClick = { viewModel.currentTab.value = "reports" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 6: Staff Management (personnel records, roles, dedicated PIN access)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModuleMenuCard(
                        title = if (isPersian) "پرسنل مطب" else "Staff Management",
                        desc = if (isPersian) "افزودن، ویرایش و حذف پزشک و دستیاران" else "Add, edit & remove doctors and assistants",
                        icon = Icons.Default.Badge,
                        onClick = { viewModel.currentTab.value = "staff_management" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleMenuCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
            }
        }
    }
    
}

// ==========================================
// 4. VENDORS MANAGEMENT
// ==========================================
@Composable
fun VendorsScreen(viewModel: DentalViewModel) {
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var vendorToDelete by remember { mutableStateOf<Vendor?>(null) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }

    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()

    var invoicePhotoFileId by remember { mutableStateOf<String?>(null) }
    var isScanningInvoice by remember { mutableStateOf(false) }

    // بازطراحی (درخواست ۱۵): ثبت پرداخت به فروشنده + مشاهده جزئیات/مانده
    var vendorToPay by remember { mutableStateOf<com.example.data.Vendor?>(null) }
    var vendorPayAmountStr by remember { mutableStateOf("") }
    var vendorPayNote by remember { mutableStateOf("") }
    var vendorToDetail by remember { mutableStateOf<com.example.data.Vendor?>(null) }

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // Camera launcher
    val launchCamera = rememberSafeCameraLauncher(context) { path ->
        invoicePhotoFileId = path
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                invoicePhotoFileId = saveUriToLocalFile(context, uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("vendors"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_vendor"))
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Document & Invoice Camera/Gallery picker
                    Text(
                        text = if (isPersian) "تصویر فاکتور خرید" else "Purchase Invoice Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "عکس دوربین" else "Camera Image", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                        }
                    }

                    // Photo preview and AI trigger
                    invoicePhotoFileId?.let { base64 ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                SmartImage(
                                    fileId = base64,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { invoicePhotoFileId = null },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(viewModel.label("vendor_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text(viewModel.label("company_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(viewModel.label("phone")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(viewModel.label("product_type")) }, modifier = Modifier.fillMaxWidth())
                    
                    Column {
                        OutlinedTextField(value = orders, onValueChange = { orders = it }, label = { Text(viewModel.label("total_purchases")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        RealTimeAmountHelper(orders)
                    }

                    Column {
                        OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text(viewModel.label("paid_amount")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        RealTimeAmountHelper(paid)
                    }

                    Button(
                        onClick = {
                            val ord = orders.toEnglishLong()
                            val pd = paid.toEnglishLong()
                            if (name.isNotEmpty()) {
                                viewModel.addVendor(name, company, phone, type, ord, pd, invoicePhotoFileId)
                                showForm = false
                                // reset
                                name = ""
                                company = ""
                                phone = ""
                                type = ""
                                orders = ""
                                paid = ""
                                invoicePhotoFileId = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vendors) { vendor ->
                    val balance = vendor.totalOrders - vendor.totalPaid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${vendor.name} (${vendor.company})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "${viewModel.label("product_type")}: ${vendor.itemType}", fontSize = 12.sp, color = Color.Gray)
                                Text(text = "${viewModel.label("phone")}: ${vendor.phone}", fontSize = 11.sp, color = Color.Gray)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text(text = "${viewModel.label("total_purchases")}: ${formatAmount(vendor.totalOrders)} $currency", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(text = "${viewModel.label("paid_amount")}: ${formatAmount(vendor.totalPaid)} $currency", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(100.dp)) {
                                Text(text = viewModel.label("vendor_debt"), fontSize = 10.sp, color = Color.Gray)
                                Text(text = "${formatAmount(balance)} $currency", fontWeight = FontWeight.Bold, color = if (balance > 0) DebtColor else ClearColor)
                                
                                // Render thumbnail if invoice photo exists
                                vendor.invoicePhotoFileId?.let { base64 ->
                                    var showZoom by remember { mutableStateOf(false) }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Card(
                                        onClick = { showZoom = true },
                                        modifier = Modifier.size(44.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color.LightGray)
                                    ) {
                                        SmartImage(
                                            fileId = base64,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    if (showZoom) {
                                        AlertDialog(
                                            onDismissRequest = { showZoom = false },
                                            title = { Text(if (isPersian) "تصویر فاکتور خرید" else "Invoice Photo Preview") },
                                            text = {
                                                SmartImage(
                                                    fileId = base64,
                                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showZoom = false }) {
                                                    Text(if (isPersian) "بستن" else "Close")
                                                }
                                            }
                                        )
                                    }
                                }

                                IconButton(onClick = { vendorToDelete = vendor }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                }
                            }
                        }

                        // بازطراحی (درخواست ۱۵): جزئیات + مانده + ثبت پرداخت
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPersian)
                                    "مانده حساب: ${formatAmount(balance)} $currency ${if (balance > 0) "(بدهی)" else "(تسویه)"}"
                                else
                                    "Balance: ${formatAmount(balance)} $currency",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance > 0) DebtColor else ClearColor
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vendorToDetail = vendor }) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(if (isPersian) "جزئیات" else "Details", fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    vendorToPay = vendor
                                    vendorPayAmountStr = ""
                                    vendorPayNote = ""
                                }) {
                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(if (isPersian) "ثبت پرداخت" else "Record Payment", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // دیالوگ ثبت پرداخت به فروشنده — بدهی فروشنده را کاهش می‌دهد و به‌صورت هزینه ثبت می‌شود (درخواست ۱۵)
    vendorToPay?.let { vendor ->
        AlertDialog(
            onDismissRequest = { vendorToPay = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (isPersian) "ثبت پرداخت به ${vendor.name}" else "Record payment to ${vendor.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val vendorBalance = vendor.totalOrders - vendor.totalPaid
                    Text(
                        text = if (isPersian) "مانده فعلی: ${formatAmount(vendorBalance)} $currency" else "Current balance: ${formatAmount(vendorBalance)} $currency",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vendorBalance > 0) DebtColor else ClearColor
                    )
                    OutlinedTextField(
                        value = vendorPayAmountStr,
                        onValueChange = { vendorPayAmountStr = it.filter(Char::isDigit) },
                        label = { Text(if (isPersian) "مبلغ پرداختی" else "Payment amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = vendorPayNote,
                        onValueChange = { vendorPayNote = it },
                        label = { Text(if (isPersian) "توضیح پرداخت (اختیاری)" else "Payment note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = vendorPayAmountStr.toEnglishLongOrNull() ?: 0L
                        if (amt > 0L) {
                            viewModel.updateVendor(vendor.copy(totalPaid = vendor.totalPaid + amt))
                            viewModel.addExpense(
                                category = "تسویه حساب فروشنده",
                                amount = amt,
                                desc = vendorPayNote.ifBlank { "پرداخت به فروشنده ${vendor.name}" },
                                paymentMethod = "CARD_TRANSFER",
                                subAccountName = vendor.name
                            )
                            vendorToPay = null
                        }
                    },
                    enabled = vendorPayAmountStr.toEnglishLongOrNull()?.let { it > 0L } == true
                ) {
                    Text(if (isPersian) "ثبت پرداخت" else "Save Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vendorToPay = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    // دیالوگ جزئیات فروشنده — مشخصات کامل + تصویر فاکتور (درخواست ۱۵)
    vendorToDetail?.let { vendor ->
        val vendorBalance = vendor.totalOrders - vendor.totalPaid
        AlertDialog(
            onDismissRequest = { vendorToDetail = null },
            title = { Text(if (isPersian) "جزئیات ${vendor.name}" else "Details — ${vendor.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vendor.company.isNotBlank()) Text(if (isPersian) "شرکت: ${vendor.company}" else "Company: ${vendor.company}", fontSize = 12.sp)
                    if (vendor.phone.isNotBlank()) Text(if (isPersian) "تلفن: ${vendor.phone}" else "Phone: ${vendor.phone}", fontSize = 12.sp)
                    if (vendor.itemType.isNotBlank()) Text(if (isPersian) "نوع کالا/خدمات: ${vendor.itemType}" else "Type: ${vendor.itemType}", fontSize = 12.sp)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text(if (isPersian) "خرید کل: ${formatAmount(vendor.totalOrders)} $currency" else "Total purchases: ${formatAmount(vendor.totalOrders)} $currency", fontSize = 12.sp)
                    Text(if (isPersian) "پرداختی: ${formatAmount(vendor.totalPaid)} $currency" else "Paid: ${formatAmount(vendor.totalPaid)} $currency", fontSize = 12.sp)
                    Text(
                        text = if (isPersian) "مانده: ${formatAmount(vendorBalance)} $currency" else "Balance: ${formatAmount(vendorBalance)} $currency",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vendorBalance > 0) DebtColor else ClearColor
                    )
                    vendor.invoicePhotoFileId?.let { photo ->
                        Text(if (isPersian) "تصویر فاکتور خرید:" else "Purchase invoice photo:", fontSize = 11.sp, color = Color.Gray)
                        SmartImage(
                            fileId = photo,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vendorToDetail = null }) { Text(if (isPersian) "بستن" else "Close") }
            }
        )
    }

    if (vendorToDelete != null) {
        AlertDialog(
            onDismissRequest = { vendorToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف تامین‌کننده/لابراتوار" else "Confirm Vendor Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف کامل اطلاعات «${vendorToDelete!!.name} (${vendorToDelete!!.company})» اطمینان دارید؟ این عمل غیرقابل بازگشت است."
                    else "Are you sure you want to delete vendor '${vendorToDelete!!.name}'?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVendor(vendorToDelete!!)
                        vendorToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف اطلاعات" else "Delete Vendor", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vendorToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

// ==========================================
// 5. DENTAL LABS
// ==========================================
@Composable
fun LabsScreen(viewModel: DentalViewModel) {
    val orders by viewModel.labOrders.collectAsStateWithLifecycle()
    val services by viewModel.labServices.collectAsStateWithLifecycle()
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    val currency = viewModel.label("currency")

    var selectedTab by remember { mutableStateOf(0) } // 0: Orders, 1: Services Catalog
    var showForm by remember { mutableStateOf(false) }
    var labOrderToDelete by remember { mutableStateOf<LabOrder?>(null) }
    var serviceToDelete by remember { mutableStateOf<LabService?>(null) }
    var serviceToEdit by remember { mutableStateOf<LabService?>(null) }

    // Lab Order Form inputs
    var selectedLabName by remember { mutableStateOf("") }
    var selectedPatientObj by remember { mutableStateOf<Patient?>(null) }
    var toothNum by remember { mutableStateOf("") }
    var prosthesis by remember { mutableStateOf("") }
    var shadeBrand by remember { mutableStateOf("") }
    var shadeColor by remember { mutableStateOf("") }
    var unitPriceStr by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }
    var formattedDueDateText by remember { mutableStateOf("") }
    var deliveryType by remember { mutableStateOf("FINAL") } // FRAME or FINAL
    // روزهای قابل ویرایش تحویل (درخواست ۱۲) — پیش‌فرض از تعرفه خدمات
    var frameDaysOverride by remember { mutableStateOf("3") }
    var finalDaysOverride by remember { mutableStateOf("7") }
    var selectedTxIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // قیمت واحد = آخرین قیمت واردشده (درخواست ۱۳) — در حافظهٔ جلسه نگه داشته می‌شود تا
    // برای سفارش بعدی همان قیمت پیش‌فرض شود (قابل ویرایش)
    var lastUnitPrice by remember { mutableStateOf<String?>(null) }
    var itemPriceOverrides by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var patientQuery by remember { mutableStateOf("") }

    // Service Form inputs
    var sLabName by remember { mutableStateOf("") }
    var sServiceName by remember { mutableStateOf("") }
    var sPriceStr by remember { mutableStateOf("") }
    var sNotes by remember { mutableStateOf("") }
    var sFrameDays by remember { mutableStateOf("3") }
    var sFinalDays by remember { mutableStateOf("7") }

    val context = LocalContext.current

    // تقویم شمسی برای تاریخ تحویل سفارش لابراتوار (جایگزین DatePickerDialog میلادی)
    var showLabDueJalaliPicker by remember { mutableStateOf(false) }
    if (showLabDueJalaliPicker) {
        JalaliDatePickerDialog(
            initialTimestamp = selectedDueDate ?: System.currentTimeMillis(),
            isPersian = isPersian,
            onDateSelected = { jy, jm, jd ->
                val picked = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
                selectedDueDate = picked
                formattedDueDateText = viewModel.formatDateFullVerbal(picked)
                showLabDueJalaliPicker = false
            },
            onDismiss = { showLabDueJalaliPicker = false }
        )
    }

    val availableLabNames = (services.map { it.labName } + vendors.map { it.name }).distinct().filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("labs"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (showForm) viewModel.label("cancel")
                    else if (selectedTab == 0) viewModel.label("add_lab_order")
                    else if (isPersian) "افزودن خدمت جدید" else "Add Lab Service"
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; showForm = false },
                text = { Text(if (isPersian) "ثبت و لیست سفارشات کاری" else "Lab Orders", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; showForm = false },
                text = { Text(if (isPersian) "تعرفه و لیست قیمت خدمات" else "Lab Prices Catalog", fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedTab == 0) {
            // Orders Tab
            if (showForm) {
                // قیمت واحد = آخرین قیمت واردشده (درخواست ۱۳) — پیش‌فرض سفارش جدید
                LaunchedEffect(showForm) {
                    if (unitPriceStr.isBlank() && !lastUnitPrice.isNullOrBlank()) {
                        unitPriceStr = lastUnitPrice!!
                        val count = parseTeethCount(toothNum)
                        if (count > 0) costStr = (lastUnitPrice!!.toEnglishLongOrNull() ?: 0L) * count
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (isPersian) "ثبت سفارش پروتز به لابراتوار" else "New Prosthesis Order", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        // 1. Lab Selector (Dropdown)
                        Text(if (isPersian) "انتخاب لابراتوار:" else "Select Lab:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var labDropdownExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = labDropdownExpanded,
                            onExpandedChange = { labDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedLabName,
                                onValueChange = { selectedLabName = it; labDropdownExpanded = true },
                                label = { Text(if (isPersian) "نام لابراتوار (انتخاب یا تایپ جدید)" else "Lab Name (select or type new)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            val filteredLabNames = availableLabNames.filter { selectedLabName.isBlank() || it.contains(selectedLabName, ignoreCase = true) }
                            if (filteredLabNames.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = labDropdownExpanded,
                                    onDismissRequest = { labDropdownExpanded = false }
                                ) {
                                    filteredLabNames.forEach { lName ->
                                        DropdownMenuItem(
                                            text = { Text(lName) },
                                            onClick = {
                                                selectedLabName = lName
                                                labDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Patient Selector (Dropdown) & Auto-Fill from Record
                        Text(if (isPersian) "انتخاب بیمار از پرونده:" else "Select Patient:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var patientDropdownExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = patientDropdownExpanded,
                            onExpandedChange = { patientDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPatientObj?.name ?: patientQuery,
                                onValueChange = {
                                    patientQuery = it
                                    selectedPatientObj = null
                                    selectedTxIds = emptySet()
                                    itemPriceOverrides = emptyMap()
                                    patientDropdownExpanded = true
                                },
                                label = { Text(viewModel.label("patient_name")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            val filteredPatients = patients.filter { patientQuery.isBlank() || it.name.contains(patientQuery, ignoreCase = true) }
                            if (filteredPatients.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = patientDropdownExpanded,
                                    onDismissRequest = { patientDropdownExpanded = false }
                                ) {
                                    filteredPatients.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                selectedPatientObj = p
                                                patientQuery = ""
                                                patientDropdownExpanded = false
                                                // (درخواست ۱۱) رفتار قبلی که درست کار می‌کرد بازسازی شد:
                                                // انتخاب بیمار => همه درمان‌های بازِ مرتبط با لابراتوار همان بیمار به‌صورت
                                                // پیش‌فرض تیک می‌خورند و شماره دندان/نوع پروتز/قیمت واحد خودکار پر می‌شود؛
                                                // کاربر می‌تواند قبل از ثبت، موارد را تغییر دهد.
                                                val pTreatments = allTreatments.filter { it.patientId == p.id && it.status != "COMPLETED" && isLabRelatedTreatment(it, services) }
                                                selectedTxIds = pTreatments.map { it.id }.toSet()
                                                itemPriceOverrides = pTreatments.associate { tx ->
                                                    val matchedService = services.find { s ->
                                                        (selectedLabName.isBlank() || s.labName.equals(selectedLabName, ignoreCase = true)) &&
                                                        (s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true))
                                                    } ?: services.find { s -> s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true) }
                                                    tx.id to (matchedService?.price ?: 0L).toString()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Itemized treatment checklist — matches each treatment to the lab's price list,
                        // groups by type, and combines everything into a single invoice.
                        if (selectedPatientObj != null) {
                            val patientTreatments = allTreatments.filter { it.patientId == selectedPatientObj!!.id && it.status != "COMPLETED" && isLabRelatedTreatment(it, services) }
                            if (patientTreatments.isNotEmpty()) {
                                Text(
                                    if (isPersian) "خدمات مربوط به این بیمار (تیک بزنید تا در فاکتور واحد لحاظ شود):" else "Patient's services (check to include in the combined invoice):",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    patientTreatments.forEach { tx ->
                                        val isChecked = selectedTxIds.contains(tx.id)
                                        val priceStr = itemPriceOverrides[tx.id] ?: tx.cost.toString()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedTxIds = if (checked) selectedTxIds + tx.id else selectedTxIds - tx.id
                                                    if (checked && !itemPriceOverrides.containsKey(tx.id)) {
                                                        val matchedService = services.find { s ->
                                                            (selectedLabName.isBlank() || s.labName.equals(selectedLabName, ignoreCase = true)) &&
                                                            (s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true))
                                                        }
                                                        itemPriceOverrides = itemPriceOverrides + (tx.id to (matchedService?.price ?: tx.cost).toString())
                                                    }
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${tx.title}${if (tx.selectedTeeth.isNotBlank()) " (${if (isPersian) "دندان" else "Tooth"} ${tx.selectedTeeth})" else ""}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedTextField(
                                                value = priceStr,
                                                onValueChange = { itemPriceOverrides = itemPriceOverrides + (tx.id to it) },
                                                modifier = Modifier.width(110.dp),
                                                singleLine = true,
                                                enabled = isChecked,
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }

                                // Recompute the aggregated fields whenever the selection changes
                                LaunchedEffect(selectedTxIds, itemPriceOverrides, selectedPatientObj) {
                                    val chosen = patientTreatments.filter { selectedTxIds.contains(it.id) }
                                    if (chosen.isNotEmpty()) {
                                        toothNum = chosen.map { it.selectedTeeth }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                                        val grouped = chosen.groupBy { it.title }
                                        prosthesis = grouped.entries.joinToString(" + ") { (title, list) -> if (list.size > 1) "$title x${list.size}" else title }
                                        val totalCost = chosen.sumOf { (itemPriceOverrides[it.id] ?: it.cost.toString()).toEnglishLongOrNull() ?: it.cost }
                                        costStr = totalCost.toString()
                                        val count = parseTeethCount(toothNum)
                                        if (count > 0) {
                                            unitPriceStr = (totalCost / count).toString()
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isPersian) "تعداد خدمات انتخاب‌شده: ${selectedTxIds.size}" else "Selected services: ${selectedTxIds.size}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${formatAmount(costStr.toEnglishLongOrNull() ?: 0L)} $currency",
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Quick Service selector from Catalog for selected lab
                        val availableServices = services.filter { s -> selectedLabName.isBlank() || s.labName.contains(selectedLabName, ignoreCase = true) }
                        if (availableServices.isNotEmpty()) {
                            Text(if (isPersian) "انتخاب خدمت از تعرفه و محاسبه هوشمند زمان تحویل:" else "Select Service from Price Catalog:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableServices.forEach { s ->
                                    FilterChip(
                                        selected = prosthesis == s.serviceName,
                                        onClick = {
                                            if (selectedLabName.isBlank()) selectedLabName = s.labName
                                            // این یک سفارش تک‌خدمتی سریع است؛ برای جلوگیری از ناهماهنگی قیمت،
                                            // انتخاب چک‌لیست درمان‌های تعدادی (که مبلغ را خودکار بازنویسی می‌کند) پاک می‌شود
                                            // تا قیمت دقیقاً همان تعرفه ثبت‌شده لابراتوار باقی بماند.
                                            selectedTxIds = emptySet()
                                            itemPriceOverrides = emptyMap()
                                            prosthesis = s.serviceName
                                            unitPriceStr = s.price.toString()
                                            val count = parseTeethCount(toothNum)
                                            costStr = (s.price * count).toString()
                                            val days = if (deliveryType == "FRAME") s.frameDeliveryDays else s.finalDeliveryDays
                                            val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                            selectedDueDate = targetTime
                                            formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                        },
                                        label = { Text("${s.serviceName} (${formatAmount(s.price)})", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = toothNum,
                            onValueChange = { input ->
                                toothNum = input
                                val count = parseTeethCount(input)
                                val uPrice = unitPriceStr.toEnglishLongOrNull()
                                if (uPrice != null && uPrice > 0L) {
                                    costStr = (uPrice * count).toString()
                                } else {
                                    val total = costStr.toEnglishLongOrNull()
                                    if (total != null && count > 0) {
                                        unitPriceStr = (total / count).toString()
                                    }
                                }
                            },
                            label = { Text(if (isPersian) "شماره دندان‌ها (مثال: 13/14/15/16/17)" else "Tooth Numbers (e.g. 13/14/15/16/17)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(value = prosthesis, onValueChange = { prosthesis = it }, label = { Text(viewModel.label("prosthesis_type")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                        // فیلد انتخابی و سرچ شونده رنگ و شید رنگ
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isPersian) "🎨 انتخاب شرکت سازنده / برند و رنگ شید دندان" else "Shade Brand & Color",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Brand selector & input
                                var brandSearch by remember { mutableStateOf("") }
                                val shadeBrands = listOf("نوریتاکه (Noritake)", "ویتا (Vita)", "ایوکلار (Ivoclar)", "زرکونزاهن (Zirkonzahn)", "شوفو (Shofu)", "جی‌سی (GC)", "دنتسپلی (Dentsply)")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    shadeBrands.forEach { b ->
                                        FilterChip(
                                            selected = shadeBrand == b,
                                            onClick = { shadeBrand = if (shadeBrand == b) "" else b },
                                            label = { Text(b, fontSize = 10.sp) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = shadeBrand,
                                    onValueChange = { shadeBrand = it },
                                    label = { Text(if (isPersian) "نام شرکت / برند (مثال: نوریتاکه)" else "Shade Brand (e.g. Noritake)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                // Shade color chips & input
                                val commonShades = listOf("A1", "A2", "A3", "A3.5", "A4", "B1", "B2", "B3", "B4", "C1", "C2", "C3", "C4", "D2", "D3", "D4", "BL1", "BL2", "BL3", "BL4")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    commonShades.forEach { sh ->
                                        FilterChip(
                                            selected = shadeColor.equals(sh, ignoreCase = true),
                                            onClick = { shadeColor = if (shadeColor == sh) "" else sh },
                                            label = { Text(sh, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = shadeColor,
                                    onValueChange = { shadeColor = it },
                                    label = { Text(if (isPersian) "رنگ / شید (مثال: A3 یا BL2)" else "Shade / Color (e.g. A3 or BL2)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        // خلاصه محاسبات ضرب تعداد واحد دندان در قیمت واحد
                        val teethCount = parseTeethCount(toothNum)
                        val currentUnitPrice = unitPriceStr.toEnglishLongOrNull() ?: (if (teethCount > 0) (costStr.toEnglishLongOrNull() ?: 0L) / teethCount else 0L)
                        val currentTotalCost = costStr.toEnglishLongOrNull() ?: (currentUnitPrice * teethCount)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تعداد سفارش بر حسب واحد دندان:" else "Teeth Unit Count:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text("$teethCount واحد دندان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "قیمت هر واحد خدمات در لابراتوار:" else "Lab Unit Price:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text("${formatAmount(currentUnitPrice)} $currency", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isPersian) "جمع کل فاکتور ($teethCount واحد × ${formatAmount(currentUnitPrice)} تومان):" else "Calculated Invoice Total:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${formatAmount(currentTotalCost)} $currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Text(if (isPersian) "نوع تحویل سفارش:" else "Delivery Type:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = deliveryType == "FRAME",
                                onClick = {
                                    deliveryType = "FRAME"
                                    val matchedServ = services.find { it.serviceName.contains(prosthesis, ignoreCase = true) }
                                    val days = matchedServ?.frameDeliveryDays ?: frameDaysOverride
                                    val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                    selectedDueDate = targetTime
                                    formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                },
                                label = { Text(if (isPersian) "امتحان فریم (${frameDaysOverride} روزه)" else "Frame Test (${frameDaysOverride}d)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = deliveryType == "FINAL",
                                onClick = {
                                    deliveryType = "FINAL"
                                    val matchedServ = services.find { it.serviceName.contains(prosthesis, ignoreCase = true) }
                                    val days = matchedServ?.finalDeliveryDays ?: finalDaysOverride
                                    val targetTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                    selectedDueDate = targetTime
                                    formattedDueDateText = viewModel.formatDateFullVerbal(targetTime)
                                },
                                label = { Text(if (isPersian) "تحویل نهایی (${finalDaysOverride} روزه)" else "Final Delivery (${finalDaysOverride}d)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // روزهای قابل ویرایش امتحان فریم / تحویل نهایی (درخواست ۱۲)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = frameDaysOverride,
                                onValueChange = { input -> frameDaysOverride = input.filter(Char::isDigit) },
                                label = { Text(if (isPersian) "روزهای امتحان فریم" else "Frame Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = finalDaysOverride,
                                onValueChange = { input -> finalDaysOverride = input.filter(Char::isDigit) },
                                label = { Text(if (isPersian) "روزهای تحویل نهایی" else "Final Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = unitPriceStr,
                                onValueChange = { input ->
                                    unitPriceStr = input
                                    if (input.toEnglishLongOrNull() ?: 0L > 0L) lastUnitPrice = input
                                    val uPrice = input.toEnglishLongOrNull() ?: 0L
                                    val count = parseTeethCount(toothNum)
                                    costStr = (uPrice * count).toString()
                                },
                                label = { Text(if (isPersian) "قیمت واحد (تومان)" else "Unit Price (Toman)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = costStr,
                                onValueChange = { input ->
                                    costStr = input
                                    val total = input.toEnglishLongOrNull() ?: 0L
                                    val count = parseTeethCount(toothNum)
                                    if (count > 0 && total > 0L) {
                                        unitPriceStr = (total / count).toString()
                                    }
                                },
                                label = { Text(if (isPersian) "جمع کل فاکتور (تومان)" else "Total Cost (Toman)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        OutlinedTextField(
                            value = if (formattedDueDateText.isNotEmpty()) formattedDueDateText else (if (isPersian) "انتخاب تاریخ تحویل" else "Select Delivery Date"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "تاریخ و روز تحویل سررسید" else "Due Delivery Date") },
                            trailingIcon = {
                                IconButton(onClick = { showLabDueJalaliPicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Delivery Date", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { showLabDueJalaliPicker = true }
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isPersian) "ℹ️ با ثبت سفارش، مبلغ فاکتور بلافاصله به صورت بدهی به لابراتوار در هزینه های جاری ثبت می گردد."
                                else "ℹ️ Total invoice cost will be automatically posted to Clinic Expenses as lab debt.",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val cost = costStr.toEnglishLong()
                                val fallbackDays = if (deliveryType == "FRAME") frameDaysOverride.toEnglishLongOrNull() ?: 3L else finalDaysOverride.toEnglishLongOrNull() ?: 7L
                                val dueTime = selectedDueDate ?: (System.currentTimeMillis() + fallbackDays * 24 * 60 * 60 * 1000L)
                                val pName = selectedPatientObj?.name ?: ""
                                if (selectedLabName.isNotEmpty() && pName.isNotEmpty()) {
                                    viewModel.addProsthesisOrderAndExpense(
                                        labName = selectedLabName,
                                        patientName = pName,
                                        toothNum = toothNum,
                                        prosthesis = prosthesis,
                                        cost = cost,
                                        dueDate = dueTime,
                                        shadeBrand = shadeBrand,
                                        shadeColor = shadeColor
                                    )
                                    showForm = false
                                    selectedLabName = ""
                                    selectedPatientObj = null
                                    selectedTxIds = emptySet()
                                    itemPriceOverrides = emptyMap()
                                    patientQuery = ""
                                    toothNum = ""
                                    prosthesis = ""
                                    shadeBrand = ""
                                    shadeColor = ""
                                    unitPriceStr = ""
                                    costStr = ""
                                    formattedDueDateText = ""
                                    selectedDueDate = null
                                    frameDaysOverride = "3"
                                    finalDaysOverride = "7"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPersian) "ثبت سفارش و صدور فاکتور بدهی" else "Save Order & Issue Invoice")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val count = parseTeethCount(order.toothNumber)
                                        val unitPrice = if (count > 0) order.cost / count else order.cost
                                        Text(text = "لابراتوار ${order.labName} ⬅️ بیمار: ${order.patientName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "${viewModel.label("prosthesis_type")}: ${order.prosthesisType} (${viewModel.label("tooth_num")}: ${if (order.toothNumber.isNotBlank()) order.toothNumber else "-"})", fontSize = 12.sp, color = Color.Gray)
                                        if (!order.shadeBrand.isNullOrBlank() || !order.shadeColor.isNullOrBlank()) {
                                            val sInfo = listOfNotNull(order.shadeBrand?.takeIf { it.isNotBlank() }, order.shadeColor?.takeIf { it.isNotBlank() }).joinToString(" | ")
                                            Text(text = "🎨 رنگ / شید: $sInfo", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(text = if (isPersian) "تعداد: $count واحد دندان | قیمت: ${formatAmount(unitPrice)} $currency" else "Units: $count | Unit Price: ${formatAmount(unitPrice)} $currency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                        Text(text = "سررسید تحویل: ${viewModel.formatDateFullVerbal(order.dueDate)}", fontSize = 11.sp, color = DebtColor, fontWeight = FontWeight.Bold)
                                        Text(text = if (isPersian) "جمع کل فاکتور: ${formatAmount(order.cost)} $currency" else "Total Invoice Cost: ${formatAmount(order.cost)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { labOrderToDelete = order }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("ORDERED", "RECEIVED", "FITTED").forEach { status ->
                                        val isSelected = order.status == status
                                        ElevatedButton(
                                            onClick = { viewModel.updateLabOrderStatus(order, status) },
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = viewModel.label("status_${status.lowercase()}"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    var showOfficialInvoiceDialog by remember { mutableStateOf(false) }
                                    OutlinedButton(
                                        onClick = { showOfficialInvoiceDialog = true },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(if (isPersian) "فاکتور رسمی" else "Invoice", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (showOfficialInvoiceDialog) {
                                        OfficialLabInvoiceDialog(
                                            order = order,
                                            viewModel = viewModel,
                                            isPersian = isPersian,
                                            onDismiss = { showOfficialInvoiceDialog = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Services Catalog Tab
            if (showForm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (isPersian) "تعریف خدمت و تعرفه قیمت جدید لابراتوار" else "Define New Lab Service", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = sLabName,
                            onValueChange = { sLabName = it },
                            label = { Text(if (isPersian) "نام لابراتوار" else "Lab Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sServiceName,
                            onValueChange = { sServiceName = it },
                            label = { Text(if (isPersian) "نام خدمت / نوع پروتز (مثلاً روکش زيركونيا)" else "Service / Prosthesis Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sPriceStr,
                            onValueChange = { sPriceStr = it },
                            label = { Text(if (isPersian) "قیمت خدمت (تومان)" else "Price (Toman)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sFrameDays,
                                onValueChange = { sFrameDays = it },
                                label = { Text(if (isPersian) "روزهای تحویل امتحان فریم" else "Frame Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sFinalDays,
                                onValueChange = { sFinalDays = it },
                                label = { Text(if (isPersian) "روزهای تحویل نهایی" else "Final Days") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = sNotes,
                            onValueChange = { sNotes = it },
                            label = { Text(if (isPersian) "توضیحات و مشخصات" else "Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                        Button(
                            onClick = {
                                val p = sPriceStr.toEnglishLong()
                                val fDays = sFrameDays.toEnglishLong().toInt().coerceAtLeast(1)
                                val fnDays = sFinalDays.toEnglishLong().toInt().coerceAtLeast(1)
                                if (sLabName.isNotBlank() && sServiceName.isNotBlank()) {
                                    viewModel.addLabService(sLabName, sServiceName, p, sNotes, fDays, fnDays)
                                    showForm = false
                                    sLabName = ""
                                    sServiceName = ""
                                    sPriceStr = ""
                                    sNotes = ""
                                    sFrameDays = "3"
                                    sFinalDays = "7"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPersian) "ثبت در تعرفه خدمات" else "Save Service")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(services) { serv ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = serv.labName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = serv.serviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "⏱️ تحویل امتحان فریم: ${serv.frameDeliveryDays} روز | تحویل نهایی: ${serv.finalDeliveryDays} روز",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (serv.notes.isNotBlank()) {
                                        Text(text = serv.notes, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text(text = "${formatAmount(serv.price)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { serviceToEdit = serv }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Service", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { serviceToDelete = serv }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Service", tint = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (serviceToEdit != null) {
        var eLab by remember { mutableStateOf(serviceToEdit!!.labName) }
        var eName by remember { mutableStateOf(serviceToEdit!!.serviceName) }
        var ePrice by remember { mutableStateOf(serviceToEdit!!.price.toString()) }
        var eNotes by remember { mutableStateOf(serviceToEdit!!.notes) }
        var eFrame by remember { mutableStateOf(serviceToEdit!!.frameDeliveryDays.toString()) }
        var eFinal by remember { mutableStateOf(serviceToEdit!!.finalDeliveryDays.toString()) }

        AlertDialog(
            onDismissRequest = { serviceToEdit = null },
            title = { Text(if (isPersian) "ویرایش تعرفه خدمت لابراتوار" else "Edit Lab Service", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = eLab, onValueChange = { eLab = it }, label = { Text(if (isPersian) "نام لابراتوار" else "Lab Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(if (isPersian) "نام خدمت" else "Service Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ePrice, onValueChange = { ePrice = it }, label = { Text(if (isPersian) "قیمت (تومان)" else "Price") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = eFrame, onValueChange = { eFrame = it }, label = { Text(if (isPersian) "روزهای فریم" else "Frame Days") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = eFinal, onValueChange = { eFinal = it }, label = { Text(if (isPersian) "روزهای نهایی" else "Final Days") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = eNotes, onValueChange = { eNotes = it }, label = { Text(if (isPersian) "توضیحات" else "Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = ePrice.toEnglishLong()
                        val fDays = eFrame.toEnglishLong().toInt().coerceAtLeast(1)
                        val fnDays = eFinal.toEnglishLong().toInt().coerceAtLeast(1)
                        if (eLab.isNotBlank() && eName.isNotBlank()) {
                            viewModel.updateLabService(
                                serviceToEdit!!.copy(
                                    labName = eLab,
                                    serviceName = eName,
                                    price = price,
                                    notes = eNotes,
                                    frameDeliveryDays = fDays,
                                    finalDeliveryDays = fnDays
                                )
                            )
                            serviceToEdit = null
                        }
                    }
                ) {
                    Text(if (isPersian) "ذخیره" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { serviceToEdit = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }

    if (serviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { serviceToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف خدمت" else "Confirm Service Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف خدمت «${serviceToDelete!!.serviceName}» متعلق به لابراتوار «${serviceToDelete!!.labName}» اطمینان دارید؟"
                    else "Are you sure you want to delete service ${serviceToDelete!!.serviceName}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLabService(serviceToDelete!!)
                        serviceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف خدمت" else "Delete Service", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { serviceToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (labOrderToDelete != null) {
        AlertDialog(
            onDismissRequest = { labOrderToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = if (isPersian) "تایید حذف سفارش" else "Confirm Order Deletion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = if (isPersian) "آیا از حذف سفارش «${labOrderToDelete!!.prosthesisType}» برای بیمار «${labOrderToDelete!!.patientName}» اطمینان دارید؟"
                    else "Are you sure you want to delete lab order for ${labOrderToDelete!!.patientName}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLabOrder(labOrderToDelete!!)
                        labOrderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف سفارش" else "Delete Order", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labOrderToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }
}

// ==========================================
// SAYADI BARCODE SCANNER DIALOG (CAMERA & ML KIT)
// ==========================================
@OptIn(ExperimentalGetImage::class)
@Composable
fun RealSayadiBarcodeScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnScanned by rememberUpdatedState(onScanned)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_CODE_128
                )
                .build()
        )
    }
    val isDisposed = remember { AtomicBoolean(false) }
    val hasDeliveredResult = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            cameraProvider?.unbindAll()
            analysisExecutor.shutdownNow()
            barcodeScanner.close()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var manualInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "اسکنر بارکد صیادی (دوربین)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!hasCameraPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "جهت اسکن مستقیم بارکد چک صیادی، مجوز استفاده از دوربین لازم است.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            ) {
                                Text("اعطای دسترسی به دوربین")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val provider = cameraProviderFuture.get()
                                    if (isDisposed.get()) {
                                        provider.unbindAll()
                                        return@addListener
                                    }
                                    cameraProvider = provider
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage == null || isDisposed.get() || hasDeliveredResult.get()) {
                                            imageProxy.close()
                                        } else {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    val match = barcodes.asSequence()
                                                        .map { it.rawValue ?: it.displayValue ?: "" }
                                                        .map { raw -> raw.filter(Char::isDigit) }
                                                        .mapNotNull { digits ->
                                                            Regex("\\d{16}").find(digits)?.value
                                                                ?: digits.takeIf { it.length == 16 }
                                                        }
                                                        .firstOrNull()
                                                    if (match != null && hasDeliveredResult.compareAndSet(false, true)) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        currentOnScanned(match)
                                                        currentOnDismiss()
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        }
                                    }

                                    try {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (_: Exception) {
                                        provider.unbindAll()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Green reticle overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(120.dp)
                                .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                        )

                        Text(
                            text = "بارکد مربعی یا میله‌ای چک صیادی را داخل کادر قرار دهید",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) manualInput = it },
                        label = { Text("ورود دستی شناسه ۱۶ رقمی", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            if (manualInput.length == 16) {
                                onScanned(manualInput)
                                onDismiss()
                            } else {
                                errorMessage = "شناسه صیاد باید دقیقاً ۱۶ رقم باشد"
                            }
                        },
                        enabled = manualInput.length == 16
                    ) {
                        Text("ثبت", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// SAYADI CHEQUES MANAGER SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChequesManagerScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    onBack: () -> Unit
) {
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val allPatients by viewModel.patients.collectAsStateWithLifecycle()
    val currency = viewModel.label("currency")

    val cheques = remember(allPayments) {
        allPayments.filter { it.type == "CHEQUE" }
    }

    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    var chequeToChangeStatus by remember { mutableStateOf<Payment?>(null) }
    var chequeToTransfer by remember { mutableStateOf<Payment?>(null) }
    var chequeToEdit by remember { mutableStateOf<Payment?>(null) }
    var chequeToDelete by remember { mutableStateOf<Payment?>(null) }
    var showAddChequeDialog by remember { mutableStateOf(false) }
    var fullPhotoUri by remember { mutableStateOf<String?>(null) }

    val filteredCheques = remember(cheques, selectedStatusFilter, searchQuery, allPatients) {
        cheques.filter { cheque ->
            val statusMatches = when (selectedStatusFilter) {
                "PENDING" -> cheque.chequeStatus == "PENDING" || (!cheque.isCleared && cheque.chequeStatus != "BOUNCED" && cheque.chequeStatus != "TRANSFERRED")
                "CLEARED" -> cheque.chequeStatus == "CLEARED" || (cheque.isCleared && cheque.chequeStatus != "TRANSFERRED")
                "BOUNCED" -> cheque.chequeStatus == "BOUNCED"
                "TRANSFERRED" -> cheque.chequeStatus == "TRANSFERRED"
                else -> true
            }

            val patientName = allPatients.find { it.id == cheque.patientId }?.name ?: ""
            val searchMatches = searchQuery.isBlank() ||
                (cheque.sayadiId?.contains(searchQuery, ignoreCase = true) == true) ||
                (cheque.chequeBank?.contains(searchQuery, ignoreCase = true) == true) ||
                (cheque.chequeDrawer?.contains(searchQuery, ignoreCase = true) == true) ||
                (patientName.contains(searchQuery, ignoreCase = true))

            statusMatches && searchMatches
        }
    }

    // Stats calculations
    val totalCount = cheques.size
    val totalSum = cheques.sumOf { it.amount }
    val pendingSum = cheques.filter { it.chequeStatus == "PENDING" || (!it.isCleared && it.chequeStatus != "BOUNCED" && it.chequeStatus != "TRANSFERRED") }.sumOf { it.amount }
    val clearedSum = cheques.filter { it.chequeStatus == "CLEARED" || (it.isCleared && it.chequeStatus != "TRANSFERRED") }.sumOf { it.amount }
    val bouncedSum = cheques.filter { it.chequeStatus == "BOUNCED" }.sumOf { it.amount }
    val transferredSum = cheques.filter { it.chequeStatus == "TRANSFERRED" }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isPersian) "مدیریت و بایگانی چک‌های صیادی" else "Sayad Cheques Ledger",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddChequeDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "ثبت چک جدید" else "New Cheque", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // KPI Stats Horizontal Summary Cards
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📊 خلاصه وضعیت کلی چک‌های دریافتی ($totalCount فقره)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "مجموع: ${formatAmount(totalSum)} $currency",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                title = "در جریان وصول",
                                amount = pendingSum,
                                currency = currency,
                                color = Color(0xFF0288D1),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "پاس شده",
                                amount = clearedSum,
                                currency = currency,
                                color = Color(0xFF22C55E),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "برگشتی",
                                amount = bouncedSum,
                                currency = currency,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "واگذار شده",
                                amount = transferredSum,
                                currency = currency,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("جستجو براساس شناسه صیاد ۱۶ رقمی، بانک، صادرکننده یا بیمار...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "همه چک‌ها ($totalCount)",
                            "PENDING" to "در جریان وصول",
                            "CLEARED" to "پاس شده",
                            "BOUNCED" to "برگشتی",
                            "TRANSFERRED" to "واگذار شده"
                        ).forEach { (code, label) ->
                            val isSel = selectedStatusFilter == code
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusFilter = code },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // List of Cheques
            if (filteredCheques.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Text("هیچ چکی با مشخصات یا فیلتر انتخابی یافت نشد.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                items(filteredCheques) { cheque ->
                    val patient = allPatients.find { it.id == cheque.patientId }
                    ChequeCardItem(
                        cheque = cheque,
                        patientName = patient?.name ?: "بیمار نامشخص",
                        currency = currency,
                        viewModel = viewModel,
                        onChangeStatus = { chequeToChangeStatus = cheque },
                        onTransfer = { chequeToTransfer = cheque },
                        onEdit = { chequeToEdit = cheque },
                        onDelete = { chequeToDelete = cheque },
                        onViewPhoto = { photo -> fullPhotoUri = photo }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // 1. Change Status Dialog
    chequeToChangeStatus?.let { cheque ->
        ChequeStatusChangeDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToChangeStatus = null }
        )
    }

    // 2. Transfer / Endorse Dialog
    chequeToTransfer?.let { cheque ->
        ChequeTransferDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToTransfer = null }
        )
    }

    // 3. Edit Cheque Dialog
    chequeToEdit?.let { cheque ->
        ChequeEditDialog(
            cheque = cheque,
            viewModel = viewModel,
            onDismiss = { chequeToEdit = null }
        )
    }

    // 4. Delete Cheque Dialog
    chequeToDelete?.let { cheque ->
        AlertDialog(
            onDismissRequest = { chequeToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("حذف چک صیادی", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("آیا از حذف چک صیادی به مبلغ ${formatAmount(cheque.amount)} $currency با شناسه ${cheque.sayadiId ?: "نامشخص"} اطمینان دارید؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(cheque)
                        chequeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف چک")
                }
            },
            dismissButton = {
                TextButton(onClick = { chequeToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // 5. Add Cheque Dialog
    if (showAddChequeDialog) {
        if (allPatients.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showAddChequeDialog = false },
                title = { Text("ثبت دریافتی و چک جدید") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                        AddPaymentForm(
                            patientId = allPatients.first().id,
                            viewModel = viewModel,
                            onSaved = { showAddChequeDialog = false },
                            initialPaymentType = "CHEQUE"
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddChequeDialog = false }) {
                        Text("بستن")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddChequeDialog = false },
                title = { Text("بیماری یافت نشد") },
                text = { Text("لطفا ابتدا یک بیمار در سیستم ثبت کنید تا بتوانید برای وی چک صیادی جدید ثبت نمایید.") },
                confirmButton = {
                    Button(onClick = { showAddChequeDialog = false }) { Text("متوجه شدم") }
                }
            )
        }
    }

    // Photo Fullscreen Dialog
    fullPhotoUri?.let { uri ->
        Dialog(onDismissRequest = { fullPhotoUri = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تصویر کامل چک صیادی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { fullPhotoUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    SmartImage(
                        fileId = uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    amount: Long,
    currency: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text("${formatAmount(amount)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(currency, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ChequeCardItem(
    cheque: Payment,
    patientName: String,
    currency: String,
    viewModel: DentalViewModel,
    onChangeStatus: () -> Unit,
    onTransfer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val status = cheque.chequeStatus
    val (statusLabel, badgeBg, badgeText) = when (status) {
        "CLEARED" -> Triple("پاس شده / وصول شده", Color(0xFF22C55E), Color.White)
        "BOUNCED" -> Triple("برگشتی / نقد نشده", Color(0xFFEF4444), Color.White)
        "TRANSFERRED" -> Triple("واگذار شده به: ${cheque.transferredTo ?: "ثالث"}", Color(0xFFF59E0B), Color.Black)
        else -> Triple("در جریان وصول", Color(0xFF0288D1), Color.White)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(patientName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                color = Color(0xFF6366F1).copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                        Text("شناسه ۱۶ رقمی صیادی:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                    }
                    Text(
                        text = cheque.sayadiId ?: "ثبت نشده",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4338CA),
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("مبلغ چک:", fontSize = 10.sp, color = Color.Gray)
                    Text("${formatAmount(cheque.amount)} $currency", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("تاریخ سررسید:", fontSize = 10.sp, color = Color.Gray)
                    cheque.chequeDueDate?.let { due ->
                        Text(viewModel.formatDate(due), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (due < System.currentTimeMillis() && cheque.chequeStatus == "PENDING") Color.Red else MaterialTheme.colorScheme.onSurface)
                    } ?: Text("-", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!cheque.chequeBank.isNullOrBlank()) {
                    Text("بانک: ${cheque.chequeBank}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!cheque.chequeDrawer.isNullOrBlank()) {
                    Text("صادرکننده: ${cheque.chequeDrawer}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!cheque.chequePhotoFileId.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewPhoto(cheque.chequePhotoFileId) }
                ) {
                    SmartImage(
                        fileId = cheque.chequePhotoFileId,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("🔍 مشاهده تصویر چک", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeStatus,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PublishedWithChanges, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("تغییر وضعیت", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onTransfer,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("انتقال چک", fontSize = 10.sp)
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChequeStatusChangeDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(cheque.chequeStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعیین وضعیت نهایی چک صیادی", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("شناسه صیاد: ${cheque.sayadiId ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("مبلغ: ${formatAmount(cheque.amount)} ${viewModel.label("currency")}", fontSize = 12.sp)

                Text("لطفاً وضعیت نهایی چک را انتخاب کنید:", fontSize = 11.sp, color = Color.Gray)

                listOf(
                    "PENDING" to "در جریان وصول (ایام سررسید)",
                    "CLEARED" to "پاس شده / وصول و واریز شد",
                    "BOUNCED" to "برگشتی / نقد نشده"
                ).forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedStatus = code }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = selectedStatus == code, onClick = { selectedStatus = code })
                        Text(label, fontSize = 12.sp, fontWeight = if (selectedStatus == code) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = cheque.copy(
                        chequeStatus = selectedStatus,
                        isCleared = selectedStatus == "CLEARED" || selectedStatus == "TRANSFERRED"
                    )
                    viewModel.updatePayment(updated)
                    onDismiss()
                }
            ) {
                Text("ذخیره تغییر وضعیت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ChequeTransferDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    val labOrders by viewModel.labOrders.collectAsStateWithLifecycle()
    val personalSubAccounts by viewModel.personalSubAccounts.collectAsStateWithLifecycle()
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value

    val distinctLabs = remember(labOrders) { labOrders.map { it.labName }.distinct().filter { it.isNotBlank() } }
    val expenseCategories = listOf("اجاره مطب", "تجهیزات و مواد دندانپزشکی", "قبوض و مراقبت", "حقوق کادر مطب", "سایر هزینه‌های جاری")

    var destType by remember { mutableStateOf("VENDOR") } // VENDOR, LAB, EXPENSE, PERSONAL, BANK, OTHER
    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var selectedLabName by remember { mutableStateOf("") }
    var selectedExpenseCat by remember { mutableStateOf(expenseCategories.first()) }
    var selectedPersonalAccount by remember { mutableStateOf("") }
    var selectedBankAccount by remember { mutableStateOf<com.example.data.BankAccount?>(null) }
    var recipientName by remember { mutableStateOf(cheque.transferredTo ?: "") }
    var transferNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتقال / واگذاری چک صیادی و اعمال بدهکار-بستانکار", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("شناسه صیاد: ${cheque.sayadiId ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("مبلغ چک: ${formatAmount(cheque.amount)} ${viewModel.label("currency")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Text("نوع حساب مقصد جهت فراخوانی و اعمال حسابداری:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "VENDOR" to "فروشندگان و تامین‌کنندگان",
                        "LAB" to "حساب لابراتوار",
                        "EXPENSE" to "هزینه جاری مطب",
                        "PERSONAL" to "سرفصل حساب شخصی",
                        "BANK" to "حساب بانکی",
                        "OTHER" to "سایر / شخص ثالث"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = destType == code,
                            onClick = { destType = code },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                when (destType) {
                    "VENDOR" -> {
                        if (vendors.isEmpty()) {
                            Text("هیچ تامین‌کننده‌ای ثبت نشده است.", fontSize = 11.sp, color = Color.Gray)
                        } else {
                            Text("انتخاب فروشنده/تامین‌کننده مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                vendors.forEach { v ->
                                    FilterChip(
                                        selected = selectedVendor?.id == v.id,
                                        onClick = {
                                            selectedVendor = v
                                            recipientName = "${v.name} (${v.company})"
                                        },
                                        label = { Text("${v.name} - ${v.company}", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    "LAB" -> {
                        Text("انتخاب یا درج نام لابراتوار مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (distinctLabs.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                distinctLabs.forEach { lab ->
                                    FilterChip(
                                        selected = selectedLabName == lab,
                                        onClick = {
                                            selectedLabName = lab
                                            recipientName = "لابراتوار $lab"
                                        },
                                        label = { Text(lab, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            label = { Text("نام دقیق لابراتوار") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "EXPENSE" -> {
                        Text("سرفصل هزینه جاری مطب:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            expenseCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedExpenseCat == cat,
                                    onClick = {
                                        selectedExpenseCat = cat
                                        recipientName = "هزینه مطب: $cat"
                                    },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    "PERSONAL" -> {
                        Text("سرفصل هزینه شخصی مقصد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (personalSubAccounts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                personalSubAccounts.forEach { sub ->
                                    FilterChip(
                                        selected = selectedPersonalAccount == sub.name,
                                        onClick = {
                                            selectedPersonalAccount = sub.name
                                            recipientName = "حساب شخصی: ${sub.name}"
                                        },
                                        label = { Text(sub.name, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    "BANK" -> {
                        Text("حساب بانکی مقصد (موجودی آن به‌اندازه مبلغ چک افزایش می‌یابد):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (bankAccounts.isEmpty()) {
                            Text("هیچ حساب بانکی ثبت نشده است.", fontSize = 11.sp, color = Color.Gray)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                bankAccounts.forEach { acc ->
                                    FilterChip(
                                        selected = selectedBankAccount?.id == acc.id,
                                        onClick = {
                                            selectedBankAccount = acc
                                            recipientName = "حساب بانکی: ${acc.bankName} (${acc.cardHolder})"
                                        },
                                        label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            label = { Text("نام دریافت‌کننده چک", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                OutlinedTextField(
                    value = transferNote,
                    onValueChange = { transferNote = it },
                    label = { Text("توضیحات بابت انتقال (اختیاری)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalRecipient = recipientName.ifBlank { "واگذاری چک" }
                    val updated = cheque.copy(
                        chequeStatus = "TRANSFERRED",
                        transferredTo = finalRecipient.trim(),
                        transferDate = System.currentTimeMillis(),
                        isCleared = true
                    )
                    viewModel.updatePayment(updated)

                    // Execute accounting debit/credit for destination — مبلغ چک در محاسبات
                    // مرتبط (هزینه‌های مطب، لابراتوار، فروشنده، حساب بانکی) لحاظ می‌شود.
                    when (destType) {
                        "VENDOR" -> {
                            selectedVendor?.let { v ->
                                viewModel.updateVendor(v.copy(totalPaid = v.totalPaid + cheque.amount))
                            }
                        }
                        "LAB" -> {
                            val labName = selectedLabName.ifBlank { recipientName.ifBlank { "لابراتوار" } }
                            viewModel.addExpense(
                                category = "هزینه لابراتوار",
                                amount = cheque.amount,
                                desc = "انتقال چک صیاد ${cheque.sayadiId ?: cheque.chequeNumber ?: ""} به لابراتوار $labName - $transferNote",
                                paymentMethod = "CHEQUE",
                                subAccountName = labName,
                                chequeNumber = cheque.chequeNumber
                            )
                        }
                        "EXPENSE" -> {
                            viewModel.addExpense(
                                category = selectedExpenseCat,
                                amount = cheque.amount,
                                desc = "انتقال چک صیاد ${cheque.sayadiId ?: cheque.chequeNumber ?: ""} - $transferNote",
                                paymentMethod = "CHEQUE",
                                subAccountName = selectedExpenseCat,
                                chequeNumber = cheque.chequeNumber
                            )
                        }
                        "PERSONAL" -> {
                            val targetAccount = selectedPersonalAccount.ifBlank { "هزینه‌های شخصی" }
                            viewModel.addPersonalTx(
                                type = "EXPENSE",
                                amount = cheque.amount,
                                desc = "انتقال چک صیاد ${cheque.sayadiId ?: cheque.chequeNumber ?: ""} به حساب $targetAccount - $transferNote",
                                paymentMethod = "CHEQUE_TRANSFER",
                                subAccountName = targetAccount,
                                chequeNumber = cheque.chequeNumber
                            )
                        }
                        "BANK" -> {
                            selectedBankAccount?.let { acc ->
                                viewModel.updateBankAccount(acc.copy(balance = acc.balance + cheque.amount))
                            }
                        }
                    }
                    onDismiss()
                },
                enabled = recipientName.isNotBlank() || selectedVendor != null || selectedLabName.isNotBlank() || selectedBankAccount != null || destType == "EXPENSE" || destType == "PERSONAL"
            ) {
                Text("ثبت انتقال و اعمال بدهکار/بستانکار")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ChequeEditDialog(
    cheque: Payment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf(cheque.amount.toString()) }
    var sayadiId by remember { mutableStateOf(cheque.sayadiId ?: "") }
    var bankName by remember { mutableStateOf(cheque.chequeBank ?: "") }
    var branchName by remember { mutableStateOf("") }
    var branchCode by remember { mutableStateOf("") }
    var drawerName by remember { mutableStateOf(cheque.chequeDrawer ?: "") }
    var drawerNationalId by remember { mutableStateOf("") }
    var chequeNum by remember { mutableStateOf(cheque.chequeNumber ?: "") }
    var dueDateStr by remember {
        mutableStateOf(
            cheque.chequeDueDate?.let {
                val j = com.example.util.PersianCalendarHelper.timestampToJalali(it)
                "${j.year}-${j.month.toString().padStart(2, '0')}-${j.day.toString().padStart(2, '0')}"
            } ?: ""
        )
    }
    var photoUri by remember { mutableStateOf(cheque.chequePhotoFileId) }
    var showScanner by remember { mutableStateOf(false) }

    // نکته‌ی هماهنگ‌سازی با فرم آپلود دسته‌ای هوش‌مصنوعی: بانکِ ترکیبی (بانک+شعبه+کد) و کدملی که
    // قبلاً در notes یا chequeBank ذخیره شده بودند را — اگر پیدا شوند — برای ویرایش راحت‌تر از هم
    // جدا می‌کنیم؛ در غیر این صورت خالی می‌مانند و کاربر خودش پر می‌کند.
    LaunchedEffect(cheque.id) {
        val notesText = cheque.notes ?: ""
        Regex("کد ملی صاحب حساب:\\s*(\\d+)").find(notesText)?.let { drawerNationalId = it.groupValues[1] }
    }

    // مبلغ به حروف — همیشه به‌صورت خودکار از مبلغِ رقمیِ بالا محاسبه می‌شود، دیگر فیلد دستیِ
    // جداگانه نیست (طبق درخواست: «مبلغ به حروف تابع نتیجه‌ی آن نوشته شود»)
    val amountTextStr = remember(amountStr) {
        amountStr.trim().toLongOrNull()?.let { com.example.util.PersianNumberFormatter.numberToPersianWords(it) } ?: ""
    }
    val dueDateDisplayStr = remember(dueDateStr) {
        com.example.util.PersianNumberFormatter.jalaliDigitsToFullDate(dueDateStr)
    }

    if (showScanner) {
        RealSayadiBarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { scannedId ->
                sayadiId = scannedId
                showScanner = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش مشخصات چک صیادی", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountStr = it },
                    label = { Text("مبلغ چک (${viewModel.label("currency")})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (amountTextStr.isNotBlank()) {
                    OutlinedTextField(
                        value = amountTextStr, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text("مبلغ به حروف (خودکار)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                OutlinedTextField(
                    value = dueDateStr,
                    onValueChange = { dueDateStr = it },
                    label = { Text("تاریخ سررسید (شمسی، مثال 1404-03-15)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (dueDateDisplayStr.isNotBlank()) {
                    OutlinedTextField(
                        value = dueDateDisplayStr, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text("تاریخ سررسید (خودکار)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sayadiId,
                        onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) sayadiId = it },
                        label = { Text("شناسه ۱۶ رقمی صیادی", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = chequeNum,
                    onValueChange = { chequeNum = it.filter(Char::isDigit) },
                    label = { Text("شماره سریال چک") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("نام بانک صادرکننده") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("شعبه", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = branchCode,
                        onValueChange = { branchCode = it.filter(Char::isDigit) },
                        label = { Text("کد شعبه", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = drawerName,
                    onValueChange = { drawerName = it },
                    label = { Text("نام صاحب حساب / صادرکننده") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = drawerNationalId,
                    onValueChange = { drawerNationalId = it.filter(Char::isDigit) },
                    label = { Text("کد ملی صاحب حساب", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAmount = amountStr.toLongOrNull() ?: cheque.amount
                    val dueMillis = try {
                        val parts = dueDateStr.trim().split("-")
                        if (parts.size == 3) {
                            com.example.util.PersianCalendarHelper.jalaliToTimestamp(
                                parts[0].toInt(), parts[1].toInt(), parts[2].toInt()
                            )
                        } else cheque.chequeDueDate
                    } catch (e: Exception) {
                        cheque.chequeDueDate
                    }
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
                    val updated = cheque.copy(
                        amount = newAmount,
                        chequeDueDate = dueMillis,
                        sayadiId = sayadiId.ifBlank { null },
                        chequeBank = bankCombined,
                        chequeDrawer = drawerName.ifBlank { null },
                        chequeNumber = chequeNum.ifBlank { null },
                        chequePhotoFileId = photoUri,
                        notes = notesCombined ?: cheque.notes
                    )
                    viewModel.updatePayment(updated)
                    onDismiss()
                }
            ) {
                Text("ذخیره تغییرات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

// ==========================================
// 6. EQUIPMENT MAINTENANCE
// ==========================================
@Composable
fun MaintenanceScreen(viewModel: DentalViewModel) {
    val items by viewModel.maintenanceList.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var apptToComplete by remember { mutableStateOf<Appointment?>(null) }
    var sessionAppointment by remember { mutableStateOf<Appointment?>(null) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var nextCheckDaysStr by remember { mutableStateOf("180") }
    // بازطراحی تعمیرات (درخواست ۱۵): تکنسین + شماره فاکتور
    var technicianName by remember { mutableStateOf("") }
    var invoiceNum by remember { mutableStateOf("") }

    val currency = viewModel.label("currency")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("maintenance"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_maintenance"))
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(viewModel.label("equipment_name")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("maintenance_desc")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = costStr, onValueChange = { costStr = it }, label = { Text(if (viewModel.isPersian.collectAsStateWithLifecycle().value) "هزینه واحد (تومان)" else "Unit Cost (Toman)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = nextCheckDaysStr, onValueChange = { nextCheckDaysStr = it }, label = { Text(if (viewModel.isPersian.collectAsStateWithLifecycle().value) "یادآور سرویس بعدی (چند روز دیگر)؟" else "Next service due in how many days?") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    // بازطراحی تعمیرات (درخواست ۱۵): تکنسین + شماره فاکتور
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = technicianName,
                            onValueChange = { technicianName = it },
                            label = { Text(if (viewModel.isPersian.collectAsStateWithLifecycle().value) "نام تکنسین / تعمیرکار" else "Technician Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = invoiceNum,
                            onValueChange = { invoiceNum = it },
                            label = { Text(if (viewModel.isPersian.collectAsStateWithLifecycle().value) "شماره فاکتور" else "Invoice No.") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            val cost = costStr.toEnglishLong()
                            val days = nextCheckDaysStr.toEnglishLongOrNull()
                            val nextDue = if (days != null) System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L) else null
                            if (name.isNotEmpty()) {
                                viewModel.addMaintenanceWithInvoice(
                                    equipment = name,
                                    desc = desc,
                                    cost = cost,
                                    date = System.currentTimeMillis(),
                                    nextCheck = nextDue,
                                    technician = technicianName.trim().ifBlank { null },
                                    invoiceNum = invoiceNum.trim().ifBlank { null }
                                )
                                showForm = false
                                name = ""
                                desc = ""
                                costStr = ""
                                technicianName = ""
                                invoiceNum = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = record.equipmentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = record.description, fontSize = 12.sp, color = Color.Gray)
                                if (!record.technicianName.isNullOrBlank()) {
                                    Text(text = "🔧 ${viewModel.label("technician")}: ${record.technicianName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                Text(text = "${viewModel.label("cost")}: ${formatAmount(record.cost)} $currency", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(text = viewModel.formatDate(record.date), fontSize = 11.sp, color = Color.Gray)
                                record.invoiceNumber?.takeIf { it.isNotBlank() }?.let {
                                    Text(text = "🧾 ${viewModel.label("invoice")}: $it", fontSize = 11.sp, color = Color.Gray)
                                }
                                record.nextServiceDate?.let {
                                    Text(text = "${viewModel.label("next_service")}: ${viewModel.formatDate(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteMaintenance(record) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesScreen(viewModel: DentalViewModel) {
    val expenses by viewModel.clinicExpenses.collectAsStateWithLifecycle()
    val allExpensePayments by viewModel.allExpensePayments.collectAsStateWithLifecycle()
    val labOrdersForInvoice by viewModel.labOrders.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var invoiceOrderToShow by remember { mutableStateOf<com.example.data.LabOrder?>(null) }
    var expenseToPay by remember { mutableStateOf<com.example.data.ClinicExpense?>(null) }
    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value

    // Group expenses by party (subAccountName or category)
    val groupedExpenses = remember(expenses) {
        expenses.groupBy { it.subAccountName.ifBlank { it.category } }
    }

    // فیلتر بازه زمانی صورتحساب (درخواست ۱۴) — همه / ۳۰ روز اخیر / ۹۰ روز اخیر / امسال
    var statementRangeFilter by remember { mutableStateOf("ALL") }
    // پاپ‌آپ انتخاب تعداد فاکتور + بازه برای صورتحساب
    var statementParty by remember { mutableStateOf<Pair<String, List<com.example.data.ClinicExpense>>?>(null) }
    var statementMaxInvoices by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("expenses"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(viewModel.label("add_expense"))
            }
        }

        // فیلتر بازه زمانی صورتحساب طرف حساب (درخواست ۱۴)
        if (expenses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rangeOptions = listOf(
                    "ALL" to (if (isPersian) "همه" else "All"),
                    "30D" to (if (isPersian) "۳۰ روز اخیر" else "Last 30 days"),
                    "90D" to (if (isPersian) "۹۰ روز اخیر" else "Last 90 days"),
                    "YEAR" to (if (isPersian) "امسال" else "This year")
                )
                rangeOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = statementRangeFilter == key,
                        onClick = { statementRangeFilter = key },
                        label = { Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        if (expenses.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (isPersian) "هیچ فاکتور/هزینه‌ای ثبت نشده است." else "No expense or invoice recorded.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedExpenses.forEach { (partyName, partyInvoices) ->
                    // اعمال بازه زمانی انتخاب‌شده (درخواست ۱۴)
                    val rangeStart = when (statementRangeFilter) {
                        "30D" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        "90D" -> System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                        "YEAR" -> {
                            val j = com.example.util.PersianCalendarHelper.timestampToJalali(System.currentTimeMillis())
                            com.example.util.PersianCalendarHelper.jalaliToTimestamp(j.year, 1, 1)
                        }
                        else -> 0L
                    }
                    val filteredPartyInvoices = if (statementRangeFilter == "ALL") partyInvoices else partyInvoices.filter { it.date >= rangeStart }
                    val partyInvoiceIds = filteredPartyInvoices.map { it.id }.toSet()
                    val partyPayments = allExpensePayments.filter { partyInvoiceIds.contains(it.clinicExpenseId) }
                    val totalPartyInvoices = filteredPartyInvoices.sumOf { it.amount }
                    val totalPartyPaid = partyPayments.sumOf { it.amount }
                    val partyRemaining = totalPartyInvoices - totalPartyPaid

                    item(key = "party_$partyName") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Party Header Summary
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = partyName,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${filteredPartyInvoices.size} ${if (isPersian) "فاکتور / هزینه" else "invoices"}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isPersian) "مانده حساب:" else "Balance:"} ${formatAmount(kotlin.math.abs(partyRemaining))} $currency",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (partyRemaining > 0) DebtColor else ClearColor
                                        )
                                        Text(
                                            text = "${if (isPersian) "جمع فاکتورها:" else "Total:"} ${formatAmount(totalPartyInvoices)} | ${if (isPersian) "پرداختی:" else "Paid:"} ${formatAmount(totalPartyPaid)}",
                                            fontSize = 10.5.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                // Share Button for Vendor Statement — با انتخاب تعداد فاکتور و بازه (درخواست ۱۴)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            statementParty = partyName to filteredPartyInvoices
                                            statementMaxInvoices = null
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPersian) "اشتراک‌گذاری صورتحساب" else "Share Statement", fontSize = 11.sp)
                                    }
                                }

                                HorizontalDivider()

                                // Invoices breakdown for this party (filtered by range)
                                filteredPartyInvoices.forEach { exp ->
                                    val paymentsForThisExp = allExpensePayments.filter { it.clinicExpenseId == exp.id }
                                    val expPaidSum = paymentsForThisExp.sumOf { it.amount }
                                    val expRemaining = exp.amount - expPaidSum

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (exp.category.isNotBlank()) "${exp.category} - ${exp.description.ifBlank { "بدون شرح" }}" else exp.description.ifBlank { "فاکتور" },
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(text = viewModel.formatDate(exp.date), fontSize = 10.5.sp, color = Color.Gray)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "${formatAmount(exp.amount)} $currency",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    IconButton(onClick = { viewModel.deleteExpense(exp) }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }

                                            // Payments list for this invoice
                                            if (paymentsForThisExp.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                        .padding(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isPersian) "پرداخت‌های ثبت‌شده این فاکتور:" else "Payments registered:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    paymentsForThisExp.forEach { p ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "• ${formatAmount(p.amount)} $currency (${p.method}) - ${viewModel.formatDate(p.date)} ${if(p.note.isNotBlank()) "(${p.note})" else ""}",
                                                                fontSize = 10.sp,
                                                                color = ClearColor
                                                            )
                                                            IconButton(onClick = { viewModel.deleteExpensePayment(p) }, modifier = Modifier.size(16.dp)) {
                                                                Icon(Icons.Default.Close, contentDescription = "Delete payment", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Action buttons
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val linkedOrder = exp.labOrderId?.let { oid -> labOrdersForInvoice.find { it.id == oid } }
                                                if (linkedOrder != null) {
                                                    TextButton(
                                                        onClick = { invoiceOrderToShow = linkedOrder },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(if (isPersian) "مشاهده فاکتور رسمی" else "View Official Invoice", fontSize = 10.sp)
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.width(1.dp))
                                                }

                                                TextButton(
                                                    onClick = { expenseToPay = exp },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (expRemaining <= 0) (if (isPersian) "پرداخت تکمیلی / جدید" else "Add Payment") else (if (isPersian) "پرداخت فاکتور (${formatAmount(expRemaining)})" else "Pay Invoice"),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddExpenseDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }

    invoiceOrderToShow?.let { order ->
        OfficialLabInvoiceDialog(
            order = order,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { invoiceOrderToShow = null }
        )
    }

    if (expenseToPay != null) {
        PayLabInvoiceDialog(
            expense = expenseToPay!!,
            viewModel = viewModel,
            isPersian = isPersian,
            onDismiss = { expenseToPay = null }
        )
    }

    // دیالوگ صورتحساب طرف حساب — انتخاب تعداد فاکتور + اشتراک (درخواست ۱۴)
    statementParty?.let { (partyName, filteredPartyInvoices) ->
        val context = LocalContext.current
        val settings = viewModel.settings.collectAsStateWithLifecycle().value
        val neonPalette = LocalNeonPalette.current
        val partyInvoiceIds = filteredPartyInvoices.map { it.id }.toSet()
        val partyPayments = allExpensePayments.filter { partyInvoiceIds.contains(it.clinicExpenseId) }
        val totalDebt = filteredPartyInvoices.sumOf { it.amount }
        val totalPaid = partyPayments.sumOf { it.amount }
        val remaining = totalDebt - totalPaid
        val limitedInvoices = statementMaxInvoices?.toIntOrNull()?.let { n ->
            filteredPartyInvoices.sortedByDescending { it.date }.take(n.coerceAtLeast(1)).reversed()
        } ?: filteredPartyInvoices

        AlertDialog(
            onDismissRequest = { statementParty = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (isPersian) "صورتحساب ($partyName)" else "Statement ($partyName)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "${filteredPartyInvoices.size} فاکتور در بازه انتخابی | جمع: ${formatAmount(totalDebt)} $currency | مانده: ${formatAmount(kotlin.math.abs(remaining))} $currency"
                        else
                            "${filteredPartyInvoices.size} invoices in range | Total: ${formatAmount(totalDebt)} $currency | Balance: ${formatAmount(kotlin.math.abs(remaining))} $currency",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(if (isPersian) "تعداد فاکتورهای آخر (اختیاری):" else "Last N invoices (optional):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = statementMaxInvoices ?: "",
                        onValueChange = { statementMaxInvoices = it.filter(Char::isDigit) },
                        label = { Text(if (isPersian) "مثلاً ۵ = فقط ۵ فاکتور آخر (خالی = همه)" else "e.g. 5 = last 5 invoices (blank = all)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        text = if (isPersian) "در تصویر نهایی: ${limitedInvoices.size} فاکتور + تمام پرداخت‌های آن‌ها" else "Image will include: ${limitedInvoices.size} invoices + all their payments",
                        fontSize = 10.5.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limitedIds = limitedInvoices.map { it.id }.toSet()
                    val limitedPayments = partyPayments.filter { limitedIds.contains(it.clinicExpenseId) }
                    val imgFile = com.example.reports.buildVendorStatementImage(
                        context = context,
                        clinicName = settings.clinicName,
                        partyName = partyName,
                        expenses = limitedInvoices,
                        payments = limitedPayments,
                        totalDebt = limitedInvoices.sumOf { it.amount },
                        totalPaid = limitedPayments.sumOf { it.amount },
                        remaining = limitedInvoices.sumOf { it.amount } - limitedPayments.sumOf { it.amount },
                        themeSpec = com.example.reports.ExportThemeSpec.fromPalette(neonPalette)
                    )
                    com.example.reports.shareImageToWhatsApp(context, imgFile)
                    statementParty = null
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "ساخت و اشتراک‌گذاری" else "Generate & Share", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { statementParty = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }
}

@Composable
fun PersonalScreen(viewModel: DentalViewModel) {
    val txList by viewModel.personalTransactions.collectAsStateWithLifecycle()
    val subAccounts by viewModel.personalSubAccounts.collectAsStateWithLifecycle()
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }

    // Form inputs
    var type by remember { mutableStateOf("EXPENSE") } // EXPENSE, DRAW, INCOME
    var selectedMethod by remember { mutableStateOf("CARD_TRANSFER") } // CARD_TRANSFER, CASH, CHEQUE_TRANSFER, POS
    var selectedSubAccount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var amountStr by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var chequeNum by remember { mutableStateOf("") }
    var selectedChequeForPersonal by remember { mutableStateOf<Payment?>(null) }

    val currency = viewModel.label("currency")
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value

    val totalExpenses = txList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalDraw = txList.filter { it.type == "DRAW" }.sumOf { it.amount }
    val totalIncome = txList.filter { it.type == "INCOME" }.sumOf { it.amount }

    // Aggregated Sub-Accounts Summary
    val aggregatedSubAccounts = remember(txList) {
        txList.filter { it.type == "EXPENSE" }
            .groupBy { it.subAccountName.ifBlank { if (isPersian) "سایر هزینه‌های شخصی" else "Other Personal" } }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.label("personal"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showForm = !showForm }) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showForm) viewModel.label("cancel") else viewModel.label("add_personal_tx"))
            }
        }

        var showManageSubAccounts by remember { mutableStateOf(false) }
        // (توجه: «سرفصل‌های شخصی» و «درآمد ثابت ماهانه» به منوی تنظیمات اختصاصی —
        // لمس ۲ثانیه‌ای چرخ‌دنده بالای صفحه — منتقل شدند)
        if (showManageSubAccounts) {
            ManageSubAccountsDialog(viewModel = viewModel, isPersian = isPersian, onDismiss = { showManageSubAccounts = false })
        }

        // Summary Cards Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = if (isPersian) "مجموع هزینه‌های شخصی" else "Total Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(text = "${formatAmount(totalExpenses)} $currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = if (isPersian) "مجموع برداشت از مطب" else "Total Clinic Draw", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = "${formatAmount(totalDraw)} $currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (showForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = viewModel.label("transaction_type"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "EXPENSE" to (if (isPersian) "هزینه شخصی" else "Personal Expense"),
                            "DRAW" to (if (isPersian) "برداشت از مطب" else "Clinic Draw"),
                            "INCOME" to (if (isPersian) "درآمد متفرقه" else "Other Income")
                        ).forEach { (txType, labelText) ->
                            ElevatedFilterChip(
                                selected = type == txType,
                                onClick = { type = txType },
                                label = { Text(labelText, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (type == "EXPENSE") {
                        Text(text = if (isPersian) "سرفصل هزینه شخصی:" else "Personal Sub-Account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickSelectNames = subAccounts.filter { it.isQuickSelect }.map { it.name }
                            val otherNames = (subAccounts.map { it.name } + listOf(
                                "اجاره مسکن و شارژ", "خریدهای روزانه و سوپرمارکت", "هزینه‌های درمانی و دارو",
                                "قبوض آب، برق، گاز و تلفن", "تفریح، سفر و رستوران", "خودرو و حمل و نقل",
                                "آموزش و شهریه فرزندان", "اقساط وام و تسهیلات"
                            )).distinct().filterNot { it in quickSelectNames }
                            val allSubAccountNames = quickSelectNames + otherNames

                            allSubAccountNames.forEach { subName ->
                                val isQuick = subName in quickSelectNames
                                FilterChip(
                                    selected = selectedSubAccount == subName,
                                    onClick = { selectedSubAccount = subName },
                                    label = { Text(subName, fontSize = 11.sp) },
                                    leadingIcon = if (isQuick) {
                                        { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    Text(text = if (isPersian) "روش پرداخت:" else "Payment Method:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "CARD_TRANSFER" to (if (isPersian) "کارت به کارت" else "Card Transfer"),
                            "CASH" to (if (isPersian) "نقدی" else "Cash"),
                            "CHEQUE_TRANSFER" to (if (isPersian) "انتقال چک" else "Cheque Transfer"),
                            "POS" to (if (isPersian) "پوز" else "POS")
                        ).forEach { (mCode, mLabel) ->
                            FilterChip(
                                selected = selectedMethod == mCode,
                                onClick = { selectedMethod = mCode },
                                label = { Text(mLabel, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedMethod == "CARD_TRANSFER" || selectedMethod == "POS") {
                        if (bankAccounts.isNotEmpty()) {
                            Text(text = if (isPersian) "حساب/کارت بانکی مبدا:" else "Source Bank Card:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bankAccounts.forEach { acc ->
                                    FilterChip(
                                        selected = selectedAccountId == acc.id,
                                        onClick = { selectedAccountId = acc.id },
                                        label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    } else if (selectedMethod == "CHEQUE_TRANSFER") {
                        val untransferredChequesForPersonal = remember(allPayments) {
                            allPayments.filter { it.type == "CHEQUE" && it.chequeStatus != "TRANSFERRED" }
                        }
                        if (untransferredChequesForPersonal.isNotEmpty()) {
                            Text(if (isPersian) "انتخاب از چک‌های دریافتی ثبت‌شده:" else "Select from received cheques:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            var chequeDropdownExpandedPersonal by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = chequeDropdownExpandedPersonal,
                                onExpandedChange = { chequeDropdownExpandedPersonal = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedChequeForPersonal?.let { c -> "${c.chequeBank ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}" } ?: (if (isPersian) "— بدون انتخاب —" else "— None —"),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chequeDropdownExpandedPersonal) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = chequeDropdownExpandedPersonal,
                                    onDismissRequest = { chequeDropdownExpandedPersonal = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isPersian) "— بدون چک (وارد کردن دستی) —" else "— None (manual entry) —") },
                                        onClick = {
                                            selectedChequeForPersonal = null
                                            chequeDropdownExpandedPersonal = false
                                        }
                                    )
                                    untransferredChequesForPersonal.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text("${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}", fontSize = 12.sp) },
                                            onClick = {
                                                selectedChequeForPersonal = c
                                                chequeNum = c.chequeNumber ?: ""
                                                amountStr = c.amount.toString()
                                                desc = if (isPersian) "انتقال چک شماره ${c.chequeNumber ?: ""} (بانک ${c.chequeBank ?: ""}، صادرکننده ${c.chequeDrawer ?: ""})" else "Cheque transfer #${c.chequeNumber ?: ""} (${c.chequeBank ?: ""})"
                                                chequeDropdownExpandedPersonal = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = chequeNum,
                            onValueChange = { chequeNum = it; selectedChequeForPersonal = null },
                            label = { Text(if (isPersian) "شماره/شناسه چک صیادی" else "Cheque Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = selectedChequeForPersonal != null
                        )
                    }

                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text(viewModel.label("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("personal_desc")) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)

                    Button(
                        onClick = {
                            val amt = amountStr.toEnglishLong()
                            if (amt >= 0) {
                                val subName = selectedSubAccount.ifBlank { if (isPersian) "هزینه‌های شخصی" else "General Personal" }
                                val cheque = selectedChequeForPersonal
                                if (cheque != null) {
                                    viewModel.transferChequeToPersonal(cheque, recipientName = desc.ifBlank { subName }, subAccountName = subName)
                                } else {
                                    viewModel.addPersonalTx(
                                        type = type,
                                        amount = amt,
                                        desc = desc.trim(),
                                        paymentMethod = selectedMethod,
                                        subAccountName = subName,
                                        chequeNumber = chequeNum.ifBlank { null }
                                    )
                                }
                                // Debit/credit source bank card if applicable
                                if ((selectedMethod == "CARD_TRANSFER" || selectedMethod == "POS") && selectedAccountId != null) {
                                    val acc = bankAccounts.find { it.id == selectedAccountId }
                                    if (acc != null) {
                                        viewModel.updateBankAccount(acc.copy(balance = acc.balance - amt))
                                    }
                                }
                                showForm = false
                                amountStr = ""
                                desc = ""
                                chequeNum = ""
                                selectedChequeForPersonal = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.label("save"))
                    }
                }
            }
        } else {
            // Display Aggregated Sub-Accounts Summary Header
            if (aggregatedSubAccounts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = if (isPersian) "📊 تجمیع سرفصل‌های هزینه شخصی:" else "📊 Aggregated Personal Expense Headings:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        aggregatedSubAccounts.forEach { (subName, totalAmt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = subName, fontSize = 12.sp)
                                Text(text = "${formatAmount(totalAmt)} $currency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(txList) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val titleStr = when (tx.type) {
                                    "DRAW" -> viewModel.label("dentist_draw")
                                    "INCOME" -> viewModel.label("personal_income")
                                    else -> tx.subAccountName.ifBlank { viewModel.label("personal_expense") }
                                }
                                Text(text = titleStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (tx.paymentMethod.isNotBlank()) {
                                    val mLabel = when (tx.paymentMethod) {
                                        "CARD_TRANSFER" -> if (isPersian) "کارت به کارت" else "Card Transfer"
                                        "CASH" -> if (isPersian) "نقدی" else "Cash"
                                        "CHEQUE_TRANSFER" -> if (isPersian) "انتقال چک" else "Cheque Transfer"
                                        "POS" -> if (isPersian) "پوز" else "POS"
                                        else -> tx.paymentMethod
                                    }
                                    Text(text = "روش: $mLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                if (tx.description.isNotEmpty()) {
                                    Text(text = tx.description, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(text = viewModel.formatDate(tx.date), fontSize = 11.sp, color = Color.Gray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val valColor = when (tx.type) {
                                    "DRAW" -> ClearColor
                                    "INCOME" -> ClearColor
                                    else -> DebtColor
                                }
                                val sign = if (tx.type == "EXPENSE") "-" else "+"
                                Text(text = "$sign${formatAmount(tx.amount)} $currency", fontWeight = FontWeight.Bold, color = valColor)
                                IconButton(onClick = { viewModel.deletePersonalTx(tx) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
@Composable
fun UnregisteredBankSmsManagerDialog(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    onDismiss: () -> Unit
) {
    val pendingSmsList by viewModel.unregisteredBankSmsList.collectAsStateWithLifecycle()
    val registeredSmsList by viewModel.registeredBankSmsList.collectAsStateWithLifecycle()
    val ignoredSmsList by viewModel.ignoredBankSmsList.collectAsStateWithLifecycle()
    // لمس هر آیتم صف، عیناً همان پاپ‌آپ لمس نوتیفیکیشن پیامک بانکی (ثبت واریز/برداشت) را باز می‌کند
    var smsToProcess by remember { mutableStateOf<com.example.data.UnregisteredBankSms?>(null) }
    // تب فعال: PENDING = ثبت‌نشده‌ها، REGISTERED = ثبت‌شده‌ها (تک‌سطری)، IGNORED = نادیده‌گرفته‌ها
    var activeQueueTab by remember { mutableStateOf("PENDING") }
    // فیلتر واریزی / برداشتی برای لیست صف
    var queueTypeFilter by remember { mutableStateOf<String?>(null) } // null=همه، DEPOSIT، WITHDRAWAL
    val baseList = when (activeQueueTab) {
        "REGISTERED" -> registeredSmsList
        "IGNORED" -> ignoredSmsList
        else -> pendingSmsList
    }
    val filteredSmsList = remember(baseList, queueTypeFilter) {
        if (queueTypeFilter == null) baseList else baseList.filter { it.type == queueTypeFilter }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SmsFailed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (isPersian) "صف پیامک‌های بانکی ثبت‌نشده (لمس هر مورد = پاپ‌آپ ثبت تراکنش)" else "Unregistered Bank SMS Queue (tap = register)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // تب‌ها: ثبت‌نشده‌ها / ثبت‌شده‌ها (تک‌سطری) / نادیده‌گرفته‌ها (لیست مجزا)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = activeQueueTab == "PENDING",
                        onClick = { activeQueueTab = "PENDING" },
                        label = { Text(if (isPersian) "ثبت‌نشده (${pendingSmsList.size})" else "Pending (${pendingSmsList.size})", fontSize = 9.5.sp, fontWeight = if (activeQueueTab == "PENDING") FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = activeQueueTab == "REGISTERED",
                        onClick = { activeQueueTab = "REGISTERED" },
                        label = { Text(if (isPersian) "ثبت‌شده (${registeredSmsList.size})" else "Registered (${registeredSmsList.size})", fontSize = 9.5.sp, fontWeight = if (activeQueueTab == "REGISTERED") FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = activeQueueTab == "IGNORED",
                        onClick = { activeQueueTab = "IGNORED" },
                        label = { Text(if (isPersian) "نادیده‌گرفته (${ignoredSmsList.size})" else "Ignored (${ignoredSmsList.size})", fontSize = 9.5.sp, fontWeight = if (activeQueueTab == "IGNORED") FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // فیلتر واریزی / برداشتی (روی تب فعال اعمال می‌شود)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = queueTypeFilter == null,
                        onClick = { queueTypeFilter = null },
                        label = { Text(if (isPersian) "همه" else "All", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = queueTypeFilter == "DEPOSIT",
                        onClick = { queueTypeFilter = "DEPOSIT" },
                        label = { Text(if (isPersian) "واریزی‌ها" else "Deposits", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = queueTypeFilter == "WITHDRAWAL",
                        onClick = { queueTypeFilter = "WITHDRAWAL" },
                        label = { Text(if (isPersian) "برداشتی‌ها" else "Withdrawals", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeQueueTab == "PENDING" && pendingSmsList.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPersian) "تعداد ${pendingSmsList.size} تراکنش پیامکی بانکی هنوز در حسابداری ثبت نشده‌اند. روی هر مورد لمس کنید تا پاپ‌آپ ثبت واریز/برداشت (عیناً مانند لمس نوتیفیکیشن پیامک بانکی) باز شود:"
                                   else "${pendingSmsList.size} unregistered SMS transactions. Tap any item to open the deposit/withdrawal registration popup:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (baseList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp))
                        Text(
                            text = when (activeQueueTab) {
                                "REGISTERED" -> if (isPersian) "هنوز تراکنش پیامکی ثبت‌شده‌ای وجود ندارد" else "No registered SMS transactions yet"
                                "IGNORED" -> if (isPersian) "هیچ پیامک نادیده‌گرفته‌شده‌ای وجود ندارد" else "No ignored SMS messages"
                                else -> if (isPersian) "هیچ پیامک بانکی بلاتکلیفی وجود ندارد" else "All bank SMS messages are registered"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredSmsList.isEmpty()) {
                    Text(
                        text = if (isPersian) "موردی مطابق این فیلتر در این لیست نیست." else "No items match this filter.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (activeQueueTab == "REGISTERED") {
                    // ثبت‌شده‌ها: نمایش تک‌سطری «مبلغ | بابت (خلاصه) | تاریخ» + امکان تغییر وضعیت
                    // (برگرداندن به ثبت‌نشده یا نادیده‌گرفتن — درخواست: تغییر وضعیت پیامک‌های ثبت‌شده)
                    filteredSmsList.forEach { sms ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (if (sms.type == "DEPOSIT") "📥 " else "📤 ") + formatAmount(sms.amountTomans),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sms.type == "DEPOSIT") Color(0xFF2E7D32) else Color(0xFFC2410C)
                                    )
                                    Text(
                                        text = sms.registeredForSummary.ifBlank { if (isPersian) "بابت: —" else "—" },
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = sms.dateStr,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.revertRegisteredBankSms(sms.id, "IGNORED") },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(if (isPersian) "نادیده گرفتن" else "Ignore", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                    TextButton(
                                        onClick = { viewModel.revertRegisteredBankSms(sms.id, "PENDING") },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(if (isPersian) "برگرداندن به ثبت‌نشده" else "Revert to Pending", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else if (activeQueueTab == "IGNORED") {
                    // نادیده‌گرفته‌شده‌ها: لیست مجزا با امکان بازگردانی یا ثبت
                    filteredSmsList.forEach { sms ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { smsToProcess = sms },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🏦 ${sms.bankNameGuess}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color.Gray)
                                    Text(
                                        text = (if (sms.type == "DEPOSIT") "واریز " else "برداشت ") + formatAmount(sms.amountTomans),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                                Text("تاریخ: ${sms.dateStr} - ${sms.timeStr}", fontSize = 9.5.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.restoreIgnoredBankSms(sms.id) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(if (isPersian) "بازگردانی به ثبت‌نشده‌ها" else "Restore", fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = { smsToProcess = sms },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(if (isPersian) "ثبت شود" else "Register", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ثبت‌نشده‌ها: اطلاعات کامل + اکشن‌های «ثبت شود» و «فعلاً نادیده گرفته شود»
                    filteredSmsList.forEach { sms ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { smsToProcess = sms },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🏦 ${sms.bankNameGuess}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (sms.type == "DEPOSIT") Color(0xFF2E7D32) else Color(0xFFC2410C)
                                    ) {
                                        Text(
                                            text = if (sms.type == "DEPOSIT") "واریز" else "برداشت",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "مبلغ: ${formatAmount(sms.amountTomans)} تومان  |  حساب/کارت: ${sms.accountNumberRaw.ifBlank { "نامشخص" }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "تاریخ: ${sms.dateStr} - ${sms.timeStr}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )

                                if (sms.content.isNotBlank()) {
                                    Text(
                                        text = sms.content,
                                        fontSize = 9.5.sp,
                                        color = Color.Gray,
                                        maxLines = 3
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.ignorePendingSms(sms.id) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(if (isPersian) "فعلاً نادیده گرفته شود" else "Ignore for now", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error)
                                    }

                                    Button(
                                        onClick = { smsToProcess = sms },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isPersian) "ثبت شود" else "Register", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "بستن" else "Close") }
        }
    )

    // لمس آیتم صف => عیناً همان پاپ‌آپ ثبت تراکنش نوتیفیکیشن پیامک بانکی (واریز/برداشت)
    if (smsToProcess != null) {
        val sms = smsToProcess!!
        BankSmsTransactionDialog(
            viewModel = viewModel,
            isPersian = isPersian,
            info = BankSmsTxnInfo(
                bankName = sms.bankNameGuess,
                type = sms.type,
                amountTomans = sms.amountTomans,
                dateStr = sms.dateStr,
                timeStr = sms.timeStr,
                accountNumberRaw = sms.accountNumberRaw,
                rawText = sms.content,
                score = sms.score,
                queueId = sms.id,
                smsNativeId = sms.smsNativeId
            ),
            onDismiss = { smsToProcess = null }
        )
    }
}

@Composable
fun ProminentSmsDisclosureDialog(
    isPersian: Boolean = true,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text(if (isPersian) "افشای شفاف و مجوز دسترسی به پیامک‌ها" else "Prominent Disclosure: SMS Permission", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isPersian) "برنامه دنتال‌پرو جهت استخراج و ثبت خودکار تراکنش‌های مالی (واریز/برداشت) نیازمند دسترسی به پیامک‌های بانکی است."
                           else "DentalPro requires SMS access to detect and automatically record bank transaction amounts.",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPersian) "🔒 حریم خصوصی: تمام تحلیل و استخراج پیامک‌ها ۱۰۰٪ به‌صورت آفلاین و درون گوشی انجام می‌شود. هیچ متن پیامکی هرگز به خارج از گوشی یا سرور ارسال نمی‌گردد."
                               else "🔒 Privacy: All SMS processing runs 100% on-device. SMS text is never uploaded to any remote server.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(if (isPersian) "متوجه شدم و اعطای مجوز" else "I Understand & Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

@Composable
fun BankSmsParserDialog(
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    initialSmsText: String? = null,
    onDismiss: () -> Unit
) {
    // اگر متن پیامک از قبل موجود است (لمس نوتیفیکیشن پیامک بانکی)، عیناً همان پاپ‌آپ
    // ثبت تراکنش واریز/برداشت باز می‌شود — همان پاپ‌آپی که از صف «پیامک‌های ثبت‌نشده» هم باز می‌شود.
    val parsed = remember(initialSmsText) {
        initialSmsText?.takeIf { it.isNotBlank() }?.let { com.example.util.BankSmsEngine.evaluateSms(it, "") }
    }
    if (parsed != null) {
        BankSmsTransactionDialog(
            viewModel = viewModel,
            isPersian = isPersian,
            info = BankSmsTxnInfo(
                bankName = parsed.bankName,
                type = parsed.transactionType,
                amountTomans = parsed.amountTomans,
                dateStr = parsed.dateStr,
                timeStr = parsed.timeStr,
                accountNumberRaw = parsed.accountNumberRaw,
                rawText = parsed.rawSms,
                score = parsed.score
            ),
            onDismiss = onDismiss
        )
    } else {
        com.example.ui.screens.BankSmsParserDialogImpl(viewModel, isPersian, initialSmsText, onDismiss)
    }
}

// PERSONAL SUB-ACCOUNTS MANAGEMENT
// ==========================================
@Composable
fun ManageSubAccountsDialog(viewModel: DentalViewModel, isPersian: Boolean, onDismiss: () -> Unit) {
    val subAccounts by viewModel.personalSubAccounts.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("EXPENSE") }
    var newLedgerSide by remember { mutableStateOf("NONE") }
    var subToDelete by remember { mutableStateOf<PersonalSubAccount?>(null) }
    var subToEdit by remember { mutableStateOf<PersonalSubAccount?>(null) }
    var editName by remember { mutableStateOf("") }
    var editLedgerSide by remember { mutableStateOf("NONE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "مدیریت سرفصل‌های هزینه شخصی و درآمد متفرقه" else "Manage Personal Sub-Accounts", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isPersian) "افزودن سرفصل جدید:" else "Add new sub-account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "EXPENSE" to (if (isPersian) "هزینه" else "Expense"),
                        "INCOME" to (if (isPersian) "درآمد متفرقه" else "Other Income"),
                        "DRAW" to (if (isPersian) "برداشت" else "Draw")
                    ).forEach { (code, lbl) ->
                        FilterChip(
                            selected = newCategory == code,
                            onClick = { newCategory = code },
                            label = { Text(lbl, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ماهیت حساب (بدهکار / بستانکار)
                Text(if (isPersian) "ماهیت حساب در دفتر کل:" else "Ledger Side:", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "NONE" to (if (isPersian) "عادی" else "Default"),
                        "DEBTOR" to (if (isPersian) "بدهکار" else "Debtor"),
                        "CREDITOR" to (if (isPersian) "بستانکار" else "Creditor")
                    ).forEach { (sideCode, sideLbl) ->
                        FilterChip(
                            selected = newLedgerSide == sideCode,
                            onClick = { newLedgerSide = sideCode },
                            label = { Text(sideLbl, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(if (isPersian) "نام سرفصل" else "Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addPersonalSubAccount(newName.trim(), newCategory, newLedgerSide)
                            newName = ""
                            newLedgerSide = "NONE"
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = null) }
                }

                HorizontalDivider()
                Text(if (isPersian) "سرفصل‌های ثبت‌شده (تیک ستاره = نمایش در انتخاب سریع):" else "Existing sub-accounts (star = show in quick-select):", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                if (subAccounts.isEmpty()) {
                    Text(if (isPersian) "هیچ سرفصلی ثبت نشده است." else "No sub-accounts yet.", fontSize = 12.sp, color = Color.Gray)
                }
                subAccounts.forEach { sub ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (subToEdit?.id == sub.id) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(onClick = {
                                    if (editName.isNotBlank()) {
                                        viewModel.updatePersonalSubAccount(sub.copy(name = editName.trim(), ledgerSide = editLedgerSide))
                                    }
                                    subToEdit = null
                                }) { Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF2E7D32)) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "NONE" to (if (isPersian) "عادی" else "Default"),
                                    "DEBTOR" to (if (isPersian) "بدهکار" else "Debtor"),
                                    "CREDITOR" to (if (isPersian) "بستانکار" else "Creditor")
                                ).forEach { (sideCode, sideLbl) ->
                                    FilterChip(
                                        selected = editLedgerSide == sideCode,
                                        onClick = { editLedgerSide = sideCode },
                                        label = { Text(sideLbl, fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = sub.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val catText = when (sub.category) {
                                            "EXPENSE" -> if (isPersian) "هزینه" else "Expense"
                                            "INCOME" -> if (isPersian) "درآمد" else "Income"
                                            "DRAW" -> if (isPersian) "برداشت" else "Draw"
                                            else -> sub.category
                                        }
                                        Text(text = catText, fontSize = 9.5.sp, color = Color.Gray)
                                        if (sub.ledgerSide != "NONE") {
                                            val sideText = if (sub.ledgerSide == "DEBTOR") (if (isPersian) "بدهکار" else "Debtor") else (if (isPersian) "بستانکار" else "Creditor")
                                            val sideColor = if (sub.ledgerSide == "DEBTOR") Color(0xFFC62828) else Color(0xFF2E7D32)
                                            Text(
                                                text = "• $sideText",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = sideColor
                                            )
                                        }
                                    }
                                }
                                IconToggleButton(
                                    checked = sub.isQuickSelect,
                                    onCheckedChange = { checked -> viewModel.updatePersonalSubAccount(sub.copy(isQuickSelect = checked)) }
                                ) {
                                    Icon(
                                        if (sub.isQuickSelect) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Quick Select",
                                        tint = if (sub.isQuickSelect) Color(0xFFFFA000) else Color.Gray
                                    )
                                }
                                IconButton(onClick = { subToEdit = sub; editName = sub.name; editLedgerSide = sub.ledgerSide }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { subToDelete = sub }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "بستن" else "Close") }
        }
    )

    if (subToDelete != null) {
        AlertDialog(
            onDismissRequest = { subToDelete = null },
            title = { Text(if (isPersian) "تایید حذف سرفصل" else "Confirm Delete", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف سرفصل «${subToDelete!!.name}» اطمینان دارید؟" else "Delete sub-account '${subToDelete!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePersonalSubAccount(subToDelete!!)
                        subToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { subToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }
}

// ==========================================
// FIXED MONTHLY INCOME MANAGEMENT
// ==========================================
@Composable
fun ManageFixedIncomesDialog(viewModel: DentalViewModel, isPersian: Boolean, onDismiss: () -> Unit) {
    val fixedIncomes by viewModel.fixedIncomes.collectAsStateWithLifecycle()
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val currency = viewModel.label("currency")

    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dayStr by remember { mutableStateOf("1") }
    var selectedBankId by remember { mutableStateOf<Long?>(null) }
    var subAccountName by remember { mutableStateOf("درآمد ثابت") }
    var incomeToDelete by remember { mutableStateOf<FixedIncome?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "درآمدهای ثابت ماهانه (واریز خودکار)" else "Fixed Monthly Incomes (Auto-Deposit)", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isPersian) "ℹ️ در تاریخ سررسید هر ماه، مبلغ به‌صورت خودکار به‌عنوان درآمد به حساب بانکی انتخابی واریز و ثبت می‌شود."
                        else "ℹ️ On the due date each month, the amount is automatically deposited and recorded as income to the selected bank account.",
                        fontSize = 11.sp, modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(if (isPersian) "عنوان درآمد" else "Income Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(if (isPersian) "مبلغ (تومان)" else "Amount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = { v -> if (v.isEmpty() || (v.toIntOrNull() != null && v.toInt() in 1..31)) dayStr = v },
                        label = { Text(if (isPersian) "روز سررسید (۱ تا ۳۱)" else "Due Day (1-31)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (bankAccounts.isNotEmpty()) {
                    Text(if (isPersian) "حساب بانکی مقصد:" else "Destination Bank Account:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        bankAccounts.forEach { acc ->
                            FilterChip(
                                selected = selectedBankId == acc.id,
                                onClick = { selectedBankId = acc.id },
                                label = { Text("${acc.bankName} - ${acc.cardHolder}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = subAccountName, onValueChange = { subAccountName = it }, label = { Text(if (isPersian) "سرفصل درآمد" else "Income Sub-Account") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Button(
                    onClick = {
                        val amt = amountStr.toEnglishLong()
                        val day = dayStr.toIntOrNull() ?: 1
                        if (title.isNotBlank() && amt > 0) {
                            val bankName = bankAccounts.find { it.id == selectedBankId }?.bankName ?: ""
                            viewModel.addFixedIncome(
                                title = title.trim(),
                                amount = amt,
                                dayOfMonth = day,
                                bankAccountId = selectedBankId,
                                bankAccountName = bankName,
                                subAccountName = subAccountName.ifBlank { "درآمد ثابت" }
                            )
                            title = ""; amountStr = ""; dayStr = "1"; selectedBankId = null; subAccountName = "درآمد ثابت"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "افزودن درآمد ثابت" else "Add Fixed Income")
                }

                HorizontalDivider()
                if (fixedIncomes.isEmpty()) {
                    Text(if (isPersian) "هیچ درآمد ثابتی ثبت نشده است." else "No fixed incomes yet.", fontSize = 12.sp, color = Color.Gray)
                }
                fixedIncomes.forEach { income ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = income.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = (if (isPersian) "روز ${income.dayOfMonth} هر ماه" else "Day ${income.dayOfMonth} of each month") +
                                        (if (income.bankAccountName.isNotBlank()) " • ${income.bankAccountName}" else ""),
                                    fontSize = 10.sp, color = Color.Gray
                                )
                            }
                            Text(text = "${formatAmount(income.amount)} $currency", fontWeight = FontWeight.Bold, color = ClearColor, fontSize = 12.sp)
                            IconButton(onClick = { incomeToDelete = income }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "بستن" else "Close") }
        }
    )

    if (incomeToDelete != null) {
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            title = { Text(if (isPersian) "تایید حذف" else "Confirm Delete", fontWeight = FontWeight.Bold) },
            text = { Text(if (isPersian) "آیا از حذف درآمد ثابت «${incomeToDelete!!.title}» اطمینان دارید؟" else "Delete fixed income '${incomeToDelete!!.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFixedIncome(incomeToDelete!!)
                        incomeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isPersian) "حذف" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) { Text(if (isPersian) "انصراف" else "Cancel") }
            }
        )
    }
}

// ==========================================
// DIALOGS & SHARED COMPONENT UTILS
// ==========================================

@Composable
fun BeforeAfterSliderDialog(
    beforeFileId: String,
    afterFileId: String,
    isPersian: Boolean = true,
    onDismiss: () -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerWidthPx by remember { mutableIntStateOf(1000) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "اسلایدر مقایسه‌ای عکس‌های قبل و بعد از درمان" else "Before / After Photo Comparison Slider",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .onSizeChanged { containerWidthPx = if (it.width > 0) it.width else 1000 }
                ) {
                    // After image (Base layer)
                    SmartImage(
                        fileId = afterFileId,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Before image (Clipped top layer)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = sliderPosition.coerceIn(0.01f, 0.99f))
                            .clipToBounds()
                    ) {
                        SmartImage(
                            fileId = beforeFileId,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Badges
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text("قبل از درمان", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text("بعد از درمان", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // Drag Slider Control
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            }
        }
    }
}

@Composable
fun RadiographLightboxDialog(
    fileId: String,
    docTitle: String = "عکس رادیوگرافی",
    isPersian: Boolean = true,
    onDismiss: () -> Unit
) {
    var isInverted by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Filter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(docTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Lightbox Image Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    SmartImage(
                        fileId = fileId,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPersian) "معکوس‌سازی رنگ‌ها (Invert B&W):" else "Invert Colors:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isInverted,
                        onCheckedChange = { isInverted = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                        Text(if (isPersian) "بستن لایت‌باکس" else "Close Lightbox")
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusDialog(
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val isOnline by viewModel.syncEngine.isOnline.collectAsStateWithLifecycle()
    val pendingOpsCount by viewModel.pendingSyncOpsCount.collectAsStateWithLifecycle()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var isPingTesting by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isOnline) Color(0xFF4CAF50) else Color(0xFFFF5252)
                )
                Text(
                    text = if (isPersian) "منوی وضعیت اتصال و همگام‌سازی ابری" else "Connection & Sync Status",
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
                Surface(
                    color = if (isOnline) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOnline) "🟢 متصل به سرور ابری (آنلاین)" else "🔴 اتصال ابری قطع است (حالت آفلاین)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC2410C)
                        )
                    }
                }

                Text(text = "سرور ابری: ابری (Supabase)", fontSize = 10.5.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("عملیات در صف ارسال:", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (pendingOpsCount > 0) "$pendingOpsCount مورد معلق" else "تمام تغییرات ارسال شده",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingOpsCount > 0) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("آخرین همگام‌سازی موفق:", fontSize = 12.sp, color = Color.Gray)
                    val lastSyncStr = if (lastSyncedAt != null && lastSyncedAt!! > 0) {
                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(lastSyncedAt!!))
                    } else "هنوز انجام نشده"
                    Text(lastSyncStr, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }

                if (lastSyncError != null) {
                    Text("خطای اخیر: $lastSyncError", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.syncEngine.pushPending()
                                viewModel.syncEngine.pullAll()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("همگام‌سازی ابری", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isPingTesting = true
                                pingResult = viewModel.pingServer()
                                isPingTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isPingTesting) "در حال تست..." else "تست اینترنت", fontSize = 10.5.sp)
                    }
                }

                if (pingResult != null) {
                    Text("نتیجه تست اتصال: $pingResult", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
fun ClinicAccountDialog(
    viewModel: DentalViewModel,
    onNavigateToAccount: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExclusiveSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val allStaffMembers by viewModel.allStaffMembers.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!settings.logoFileId.isNullOrBlank()) {
                    SmartImage(fileId = settings.logoFileId, modifier = Modifier.size(36.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        text = settings.clinicName.ifBlank { "کلینیک دندان‌پزشکی" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "دکتر ${settings.doctorName.ifBlank { "معالج" }}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نقش فعال فعلی:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text(activeRole.titleFa, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNavigateToAccount()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مدیریت حساب کاربری و وضعیت ابری", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNavigateToSettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تنظیمات مطب", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNavigateToExclusiveSettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تنظیمات اختصاصی نرم‌افزار مطب", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                if (allStaffMembers.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("پرسنل و دستیاران پذیرش مطب:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    allStaffMembers.forEach { staff ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${staff.fullName}", fontSize = 11.sp)
                            if (staff.phone.isNotBlank()) {
                                Text(staff.phone, fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
fun BallpointSignaturePad(
    modifier: Modifier = Modifier,
    isPersian: Boolean = true,
    onSignatureCaptured: (localPath: String) -> Unit,
    onClear: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // هر خطِ امضا به‌صورت فهرستی از نقاط نگهداری می‌شود تا هنگام رسم با «منحنی‌های درجه‌دو»
    // (quadratic Bézier از میان نقاط میانی) خطی روان و پیوسته — مانند جوهرِ روانِ خودکاری —
    // ترسیم شود؛ خروجی نهایی نیز با همان منحنی‌ها به‌صورت PNG شفاف ذخیره می‌گردد.
    val strokes = remember { androidx.compose.runtime.mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    val currentPoints = remember { androidx.compose.runtime.mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
    var drawTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var padSizePx by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = if (isPersian) "✍️ کادر امضای لمسی بیمار (افکت خودکار روان):" else "✍️ Ballpoint Touch Signature:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = if (isPersian) "امضای الکترونیکی بیمار" else "Digital Signature", fontSize = 9.5.sp, color = Color.Gray)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .onSizeChanged { padSizePx = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset)
                            drawTrigger++
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints.add(change.position)
                            drawTrigger++
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                strokes.add(currentPoints.toList())
                            }
                            currentPoints.clear()
                            drawTrigger++
                        }
                    )
                }
        ) {
            val dummy = drawTrigger
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (dummy >= 0) {
                    // مرکب روانِ آبیِ خودکاری
                    val inkColor = Color(0xFF1D4ED8)
                    val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 5.5f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                    // ترسیم روان با منحنی درجه‌دو: هر نقطه به‌عنوان نقطه کنترل و میان‌نقطه‌ها به‌عنوان مقصد
                    fun drawSmoothStroke(points: List<androidx.compose.ui.geometry.Offset>) {
                        if (points.isEmpty()) return
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(points.first().x, points.first().y)
                        if (points.size > 2) {
                            for (i in 1 until points.size - 1) {
                                val midX = (points[i].x + points[i + 1].x) / 2f
                                val midY = (points[i].y + points[i + 1].y) / 2f
                                path.quadraticBezierTo(points[i].x, points[i].y, midX, midY)
                            }
                            path.lineTo(points.last().x, points.last().y)
                        } else {
                            points.drop(1).forEach { path.lineTo(it.x, it.y) }
                        }
                        drawPath(path = path, color = inkColor, style = strokeStyle)
                    }
                    strokes.forEach { pts -> drawSmoothStroke(pts) }
                    drawSmoothStroke(currentPoints.toList())
                }
            }
            if (strokes.isEmpty() && currentPoints.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Gesture, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                    Text(
                        text = if (isPersian) "امضای بیمار را با انگشت یا قلم در این کادر رسم کنید" else "Sign here with finger or stylus",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            strokes.removeAt(strokes.size - 1)
                            drawTrigger++
                        }
                    },
                    enabled = strokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (isPersian) "بازگشت" else "Undo", fontSize = 10.5.sp)
                }
                TextButton(
                    onClick = {
                        strokes.clear()
                        currentPoints.clear()
                        drawTrigger++
                        onClear()
                    },
                    enabled = strokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (isPersian) "پاک‌کردن" else "Clear", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            if (strokes.isNotEmpty()) {
                Button(
                    onClick = {
                        val width = if (padSizePx.width > 0) padSizePx.width else 800
                        val height = if (padSizePx.height > 0) padSizePx.height else 400
                        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        // پس‌زمینه شفاف — فقط خطوطِ آبیِ خودکاری روی PNG شفاف ذخیره می‌شوند
                        canvas.drawColor(android.graphics.Color.TRANSPARENT)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#1D4ED8")
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 6.5f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                            isDither = true
                        }
                        strokes.forEach { pts ->
                            val p = android.graphics.Path()
                            p.moveTo(pts.first().x, pts.first().y)
                            if (pts.size > 2) {
                                for (i in 1 until pts.size - 1) {
                                    val midX = (pts[i].x + pts[i + 1].x) / 2f
                                    val midY = (pts[i].y + pts[i + 1].y) / 2f
                                    p.quadTo(pts[i].x, pts[i].y, midX, midY)
                                }
                                p.lineTo(pts.last().x, pts.last().y)
                            } else {
                                pts.drop(1).forEach { p.lineTo(it.x, it.y) }
                            }
                            canvas.drawPath(p, paint)
                        }
                        val localPath = saveBitmapToLocalFile(
                            context = context,
                            bitmap = bmp,
                            maxDim = 1000,
                            format = android.graphics.Bitmap.CompressFormat.PNG,
                            quality = 100
                        )
                        onSignatureCaptured(localPath)
                        android.widget.Toast.makeText(
                            context,
                            if (isPersian) "امضا با موفقیت ذخیره و آماده سینک شد" else "Ballpoint signature recorded",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPersian) "تأیید امضا" else "Confirm", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddPatientDialog(viewModel: DentalViewModel, onDismiss: () -> Unit) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())

    val clinicSettings by viewModel.settings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var capturedSignaturePath by remember { mutableStateOf<String?>(null) }
    var province by remember { mutableStateOf("آذربایجان شرقی") }
    var city by remember { mutableStateOf("تبریز") }
    var addressStreet by remember { mutableStateOf("") }
    var addressPelak by remember { mutableStateOf("") }
    var addressDetails by remember { mutableStateOf("") }
    var referrer by remember { mutableStateOf("") }
    var expandedProvince by remember { mutableStateOf(false) }
    var expandedCity by remember { mutableStateOf(false) }
    var preferredMessenger by remember { mutableStateOf("واتساپ") }
    var gender by remember { mutableStateOf(if (isPersian) "آقای" else "Mr.") }
    var ageStr by remember { mutableStateOf("") }
    var birthDateStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val nameError = if (name.isBlank()) "نام بیمار الزامی است" else null
    val phoneError = if (phoneDigits.length != 9) "شماره همراه باید دقیقاً ۹ رقم باشد" else null
    val validationError = nameError ?: phoneError

    // موارد هشدار پزشکی و خوداظهاری سلامت اکنون یک فهرست واحد و یکپارچه است (به‌جای دو بخش جدا و ناهماهنگ قبلی)
    var selectedAlerts by remember { mutableStateOf(setOf<String>()) }

    // دستیار(های) مسئول پذیرش این بیمار
    val allStaffMembers by viewModel.allStaffMembers.collectAsStateWithLifecycle()
    var selectedAssistantSyncIds by remember { mutableStateOf(listOf<String>()) }
    // پیش‌فرض: نام دستیار فعال به‌صورت خودکار در تمام فعالیت‌ها وارد می‌شود (درخواست ۶)
    val defaultAssistantSyncIds by viewModel.activeAssistantSyncIdsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(defaultAssistantSyncIds) {
        if (selectedAssistantSyncIds.isEmpty() && defaultAssistantSyncIds.isNotEmpty()) {
            selectedAssistantSyncIds = defaultAssistantSyncIds
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isPersian) "پذیرش بیمار جدید و تشکیل پرونده" else "Patient Admission & Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isPersian) "ثبت مشخصات، جنسیت، پیام‌رسان پیش‌فرض و فرم خوداظهاری" else "Register info, gender, messenger & self-declaration",
                    fontSize = 11.sp,
                    color = Color.Gray
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
                // Patient Name (Required with Live Validation)
                var nameEdited by remember { mutableStateOf(false) }
                val showNameError = (attemptedSubmit || nameEdited) && nameError != null
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it 
                        nameEdited = true
                    },
                    label = { Text(if (isPersian) "نام و نام خانوادگی بیمار *" else "Patient Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showNameError,
                    supportingText = {
                        if (showNameError) {
                            Text(nameError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                )

                // Phone Number (+989 fixed, exactly 9 digits, LTR layout & Live Validation)
                var phoneEdited by remember { mutableStateOf(false) }
                val showPhoneError = (attemptedSubmit || phoneEdited) && phoneError != null
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = phoneDigits,
                        onValueChange = { input -> 
                            val digits = input.filter { it.isDigit() }.take(9)
                            phoneDigits = digits 
                            phoneEdited = true
                        },
                        label = { Text(if (isPersian) "شماره موبایل (۹ رقم) *" else "Mobile Number *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        leadingIcon = { Text("+98 9", modifier = Modifier.padding(start=16.dp), fontWeight = FontWeight.Bold) },
                        isError = showPhoneError,
                        supportingText = {
                            if (showPhoneError) {
                                Text(phoneError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Text("${phoneDigits.length} / 9 رقم", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    )
                }

                // National ID & Father's Name Section (کد ملی و نام پدر — غیرالزامی)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nationalId,
                        onValueChange = { nationalId = it },
                        label = { Text(if (isPersian) "کد ملی (اختیاری)" else "National ID (Opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text(if (isPersian) "نام پدر (اختیاری)" else "Father's Name (Opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Province & City Selectors (استان و شهر)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Province Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedProvince,
                        onExpandedChange = { expandedProvince = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = province,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "استان" else "Province") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvince) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProvince,
                            onDismissRequest = { expandedProvince = false }
                        ) {
                            com.example.util.IranianProvinces.getProvinces().forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, fontSize = 12.sp) },
                                    onClick = {
                                        province = p
                                        val cities = com.example.util.IranianProvinces.getCities(p)
                                        if (!cities.contains(city)) {
                                            city = cities.firstOrNull() ?: ""
                                        }
                                        expandedProvince = false
                                    }
                                )
                            }
                        }
                    }

                    // City Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedCity,
                        onExpandedChange = { expandedCity = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "شهر" else "City") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCity) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCity,
                            onDismissRequest = { expandedCity = false }
                        ) {
                            com.example.util.IranianProvinces.getCities(province).forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c, fontSize = 12.sp) },
                                    onClick = {
                                        city = c
                                        expandedCity = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3-Split Address Fields & Referrer
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = addressStreet,
                            onValueChange = { addressStreet = it },
                            label = { Text(if (isPersian) "خیابان / محله" else "Street / District") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = addressPelak,
                            onValueChange = { addressPelak = it },
                            label = { Text(if (isPersian) "پلاک / واحد" else "Pelak / Unit") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = addressDetails,
                        onValueChange = { addressDetails = it },
                        label = { Text(if (isPersian) "سایر جزییات آدرس (کوچه، پلاک،..." else "Additional Address Details") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = referrer,
                        onValueChange = { referrer = it },
                        label = { Text(if (isPersian) "معرف بیمار / نحوه آشنایی" else "Referrer / Channel") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }

                    // Preferred Messenger Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "پیام‌رسان پیش‌فرض بیمار جهت ارسال پیام و مدارک:" else "Preferred Messenger:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                            messengers.forEach { m ->
                                FilterChip(
                                    selected = preferredMessenger == m,
                                    onClick = { preferredMessenger = m },
                                    label = { Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Age & Birth Date with automatic bidirectional calculation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { input ->
                                // کیبورد عددی: فقط ارقام (حداکثر ۳ رقم) پذیرفته می‌شود
                                ageStr = input.filter { it.isDigit() }.take(3)
                                val parsedAge = ageStr.toEnglishIntOrNull()
                                if (parsedAge != null && parsedAge > 0 && parsedAge < 120) {
                                    birthDateStr = "${1405 - parsedAge}"
                                }
                            },
                            label = { Text(if (isPersian) "سن بیمار" else "Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("مثلاً ۲۸") }
                        )

                        com.example.ui.components.BirthDateInput(
                            birthDateStr = birthDateStr,
                            onBirthDateChange = { input -> 
                                birthDateStr = input
                                val yearOnly = birthDateStr.takeWhile { it.isDigit() }.toEnglishIntOrNull()
                                if (yearOnly != null && yearOnly in 1300..1405) {
                                    ageStr = "${1405 - yearOnly}"
                                }
                            },
                            isPersian = isPersian,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Gender Selector (جنسیت)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "جنسیت بیمار:" else "Patient Gender:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val genderOptions = if (isPersian) listOf("آقای", "خانم") else listOf("Mr.", "Ms.")
                            genderOptions.forEach { g ->
                                val isMale = g == "آقای" || g == "Mr."
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            if (isMale) Icons.Default.Man else Icons.Default.Woman,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // موارد هشدار پزشکی و خوداظهاری سلامت (ادغام‌شده - قبلاً این بخش و «فرم خوداظهاری» دو بخش جدا و ناهماهنگ بودند)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "📋 موارد هشدار، بیماری‌های خاص و خوداظهاری سلامت بیمار:" else "📋 Medical Warnings & Health Self-Declaration:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = if (isPersian) "هر مورد مرتبط با وضعیت سلامت بیمار را روشن کنید؛ این اطلاعات مستقیماً برای هشدارهای بالینی حین درمان استفاده می‌شود." else "Toggle any items relevant to the patient's health; used directly for clinical risk alerts during treatment.",
                                fontSize = 9.5.sp,
                                color = Color.Gray
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            val groupedAddAlerts = DENTAL_MEDICAL_ALERT_ITEMS.groupBy { it.category }
                            groupedAddAlerts.forEach { (catName, items) ->
                                Text(
                                    text = catName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                                items.forEach { item ->
                                    val isSel = selectedAlerts.contains(item.labelFa)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAlerts = if (isSel) selectedAlerts - item.labelFa else selectedAlerts + item.labelFa }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = item.labelFa, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = isSel,
                                            onCheckedChange = { checked -> selectedAlerts = if (checked) selectedAlerts + item.labelFa else selectedAlerts - item.labelFa },
                                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.error)
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }

                    com.example.ui.components.AssistantMultiSelectField(
                        selectedSyncIds = selectedAssistantSyncIds,
                        onSelectionChange = { selectedAssistantSyncIds = it },
                        allStaff = allStaffMembers,
                        isPersian = isPersian
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(if (isPersian) "توضیحات و یادداشت اولیه" else "Initial Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // دریافت نمونه امضای اولیه بیمار با افکت روان خودکار
                    BallpointSignaturePad(
                        isPersian = isPersian,
                        onSignatureCaptured = { capturedSignaturePath = it },
                        onClear = { capturedSignaturePath = null }
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSubmit = true
                    if (validationError == null) {
                        val alertsStr = selectedAlerts.joinToString(", ")
                        val selfDecMap = DENTAL_MEDICAL_ALERT_ITEMS.associate { item ->
                            item.slug to (if (selectedAlerts.contains(item.labelFa)) "بله" else "خیر")
                        }
                        val jsonStr = org.json.JSONObject(selfDecMap as Map<*, *>).toString()

                        val fullAddress = listOf(province, city, addressStreet, addressPelak, addressDetails).filter { it.isNotBlank() }.joinToString("، ")
                        viewModel.addPatientFull(
                            name = name,
                            phone = "+989$phoneDigits",
                            address = fullAddress,
                            note = note,
                            gender = gender,
                            medicalAlerts = alertsStr,
                            selfDeclarationJson = jsonStr,
                            age = ageStr.toEnglishIntOrNull() ?: 0,
                            birthDate = birthDateStr,
                            preferredMessenger = preferredMessenger,
                            referrer = referrer,
                            assistantIds = com.example.ui.components.joinAssistantSyncIds(selectedAssistantSyncIds),
                            fatherName = fatherName,
                            nationalId = nationalId,
                            signatureLocalFilePath = capturedSignaturePath
                        )
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(viewModel.label("save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(viewModel.label("cancel"))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(viewModel: DentalViewModel, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf("Materials") }
    var amountStr by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val untransferredCheques = remember(allPayments) {
        allPayments.filter { it.type == "CHEQUE" && it.chequeStatus != "TRANSFERRED" }
    }
    var selectedCheque by remember { mutableStateOf<Payment?>(null) }
    var chequeDropdownExpanded by remember { mutableStateOf(false) }

    var expenseDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }

    if (showCalculator) {
        val curVal = amountStr.toEnglishLong()
        com.example.ui.components.SimpleCalculatorDialog(
            initialValue = curVal,
            isPersian = isPersian,
            onConfirm = { calculatedAmount ->
                amountStr = calculatedAmount.toString()
            },
            onDismiss = { showCalculator = false }
        )
    }

    // تقویم شمسی برای تاریخ هزینه (جایگزین DatePicker میلادی Material3)
    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialTimestamp = expenseDateMillis,
            isPersian = isPersian,
            onDateSelected = { jy, jm, jd ->
                expenseDateMillis = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.label("add_expense"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (untransferredCheques.isNotEmpty()) {
                    Text(
                        if (isPersian) "انتقال از یک چک دریافتی (اختیاری):" else "Transfer from a received cheque (optional):",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    ExposedDropdownMenuBox(
                        expanded = chequeDropdownExpanded,
                        onExpandedChange = { chequeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCheque?.let { c -> "${c.chequeBank ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}" } ?: (if (isPersian) "بدون انتخاب چک (هزینه نقدی/عادی)" else "No cheque selected"),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chequeDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = chequeDropdownExpanded,
                            onDismissRequest = { chequeDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPersian) "— بدون چک —" else "— None —") },
                                onClick = {
                                    selectedCheque = null
                                    amountStr = ""
                                    desc = ""
                                    chequeDropdownExpanded = false
                                }
                            )
                            untransferredCheques.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)} - ${viewModel.formatDate(c.date)}", fontSize = 12.sp) },
                                    onClick = {
                                        selectedCheque = c
                                        amountStr = c.amount.toString()
                                        desc = (if (isPersian) "انتقال چک شماره ${c.chequeNumber ?: ""} (بانک ${c.chequeBank ?: ""}، صادرکننده ${c.chequeDrawer ?: ""})" else "Cheque transfer #${c.chequeNumber ?: ""} (${c.chequeBank ?: ""})")
                                        chequeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Rent", "Utilities", "Salaries", "Materials", "Other").forEach { cat ->
                        ElevatedFilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(viewModel.label(cat.lowercase())) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(viewModel.label("amount")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = selectedCheque != null,
                    trailingIcon = {
                        if (selectedCheque == null) {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(Icons.Default.Calculate, contentDescription = "Calculator", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                Text(if (isPersian) "تاریخ ثبت هزینه:" else "Expense Date:", fontSize = 12.sp)
                OutlinedTextField(
                    value = viewModel.formatDate(expenseDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )

                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(viewModel.label("notes")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.toEnglishLong()
                val selDate = expenseDateMillis
                if (amt >= 0) {
                    val cheque = selectedCheque
                    if (cheque != null) {
                        viewModel.transferChequeToExpense(cheque, recipientName = desc.ifBlank { viewModel.label(category.lowercase()) }, category = category)
                    } else {
                        viewModel.addExpense(category, amt, desc, date = selDate)
                    }
                    onDismiss()
                }
            }) {
                Text(viewModel.label("save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(viewModel.label("cancel"))
            }
        }
    )
}

// Format currency helper
fun formatAmount(value: Long): String {
    val absValue = kotlin.math.abs(value)
    return if (absValue >= 1_000_000_000) {
        val billions = absValue.toDouble() / 1_000_000_000
        val formatted = String.format(Locale.US, "%.1f", billions)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr میلیارد"
    } else if (absValue >= 1_000_000) {
        val millions = absValue.toDouble() / 1_000_000
        val formatted = String.format(Locale.US, "%.1f", millions)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr میلیون"
    } else if (absValue >= 1_000) {
        val thousands = absValue.toDouble() / 1_000
        val formatted = String.format(Locale.US, "%.1f", thousands)
        val finalStr = if (formatted.endsWith(".0")) formatted.substringBefore(".0") else formatted
        val sign = if (value < 0) "-" else ""
        "$sign$finalStr هزار"
    } else {
        value.toString()
    }
}

// Downscale and compress image for SQLite storage as base64

// Compress camera captures to Base64

// Convert Base64 string to Android Bitmap for drawing in Composables

// Convert any numeric Long amount to full Persian alphabetical text (e.g. 250000 -> دویست و پنجاه هزار)
fun numberToPersianWords(number: Long): String {
    if (number == 0L) return "صفر"
    
    val units = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    val teens = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    val tens = listOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    val hundreds = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    val groups = listOf("", "هزار", "میلیون", "میلیارد", "تریلیون")

    var temp = number
    val parts = mutableListOf<String>()
    var groupIndex = 0

    while (temp > 0) {
        val part = (temp % 1000).toInt()
        if (part > 0) {
            val partWords = mutableListOf<String>()
            val h = part / 100
            val t = (part % 100) / 10
            val u = part % 10

            if (h > 0) partWords.add(hundreds[h])
            
            if (t == 1) {
                partWords.add(teens[u])
            } else {
                if (t > 1) partWords.add(tens[t])
                if (u > 0) partWords.add(units[u])
            }

            val groupWord = groups[groupIndex]
            var combined = partWords.joinToString(" و ")
            if (groupWord.isNotEmpty()) {
                combined += " $groupWord"
            }
            parts.add(0, combined)
        }
        temp /= 1000
        groupIndex++
    }

    return parts.joinToString(" و ")
}

// Format any input string of numbers to Persian words with "تومان" currency suffix
fun formatToPersianWordsWithUnit(amountStr: String): String {
    val value = amountStr.toEnglishLong()
    if (value == 0L) return ""
    return "${numberToPersianWords(value)} تومان"
}

// Beautiful real-time assistant that renders below OutlinedTextField amount fields
@Composable
fun RealTimeAmountHelper(amountStr: String) {
    val words = remember(amountStr) { formatToPersianWordsWithUnit(amountStr) }
    if (words.isNotEmpty()) {
        Text(
            text = words,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}



@Composable
fun AppointmentSessionDialog(
    appointment: com.example.data.Appointment,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val allTreatments by viewModel.allTreatments.collectAsStateWithLifecycle()
    val patientTreatments = allTreatments.filter { it.patientId == appointment.patientId }
    val plannedIds = appointment.plannedTreatmentIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    
    var selectedIds by remember { mutableStateOf(plannedIds) }
    val isPersian = viewModel.isPersian.collectAsStateWithLifecycle().value
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "مدیریت جلسه درمانی" else "Manage Treatment Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isPersian) "طرح‌های درمان را برای این جلسه انتخاب کنید:" else "Select treatments for this session:",
                    fontSize = 13.sp, color = Color.Gray
                )
                
                val selectable = patientTreatments.filter { it.status != "COMPLETED" || selectedIds.contains(it.id) }
                if (selectable.isEmpty()) {
                    Text(text = if (isPersian) "طرح درمانی یافت نشد." else "No active treatments found.")
                } else {
                    selectable.forEach { tr ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (selectedIds.contains(tr.id)) {
                                    selectedIds - tr.id
                                } else {
                                    selectedIds + tr.id
                                }
                            }
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(tr.id),
                                onCheckedChange = { chk -> 
                                    selectedIds = if (chk) selectedIds + tr.id else selectedIds - tr.id 
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = tr.title, fontWeight = FontWeight.Bold)
                                Text(text = "${formatAmount(tr.cost - tr.discount)} ${viewModel.label("currency")}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateAppointment(appointment.copy(
                    plannedTreatmentIds = selectedIds.joinToString(","),
                    treatmentType = if (selectedIds.isNotEmpty()) if (isPersian) "طرح های انتخابی" else "Selected Treatments" else ""
                ))
                onDismiss()
            }) {
                Text(if (isPersian) "ذخیره جلسه" else "Save Session")
            }
        },
        dismissButton = {
            if (selectedIds.isNotEmpty()) {
                Button(
                    onClick = {
                        val toComplete = patientTreatments.filter { selectedIds.contains(it.id) }
                        toComplete.forEach { tr ->
                            viewModel.updateTreatmentStatus(tr, "COMPLETED")
                        }
                        viewModel.updateAppointment(appointment.copy(
                            plannedTreatmentIds = selectedIds.joinToString(","),
                            treatmentType = if (isPersian) "انجام شده" else "Completed"
                        ))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(if (isPersian) "پایان و تایید انجام" else "Complete & Confirm", color = Color.White)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(viewModel.label("cancel"))
                }
            }
        }
    )
}

fun String.toEnglishIntOrNull(): Int? {
    if (this.isBlank()) return null
    val englishStr = this.map { ch ->
        when (ch) {
            '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
            '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> ch
        }
    }.joinToString("")
    return englishStr.toIntOrNull()
}

fun parseStagesList(stagesStr: String): List<String> {
    if (stagesStr.isBlank()) return emptyList()
    return stagesStr.split(Regex("""(?:\s*-\s*|\s*\|\s*|\s*,\s*|\s*\n\s*|\s*؛\s*)"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPatientDialog(
    patient: Patient,
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())

    val clinicSettings by viewModel.settings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(patient.name) }
    var gender by remember { mutableStateOf(if (patient.gender.isNotBlank()) (if (patient.gender == "زن" || patient.gender == "خانم" || patient.gender == "Ms.") (if (isPersian) "خانم" else "Ms.") else (if (isPersian) "آقای" else "Mr.")) else (if (isPersian) "آقای" else "Mr.")) }
    var ageStr by remember { mutableStateOf(if (patient.age > 0) patient.age.toString() else "") }
    var birthDateStr by remember { mutableStateOf(patient.birthDate) }
    var phoneDigits by remember { mutableStateOf(patient.phone.takeLast(9).filter { it.isDigit() }) }
    val parsedAddressParts = remember(patient.address) {
        patient.address.split(Regex("""\s*[،,-]\s*""")).map { it.trim() }.filter { it.isNotBlank() }
    }
    var province by remember { mutableStateOf(parsedAddressParts.getOrNull(0) ?: "آذربایجان شرقی") }
    var city by remember { mutableStateOf(parsedAddressParts.getOrNull(1) ?: "تبریز") }
    var addressStreet by remember { mutableStateOf(parsedAddressParts.getOrNull(2) ?: "") }
    var addressPelak by remember { mutableStateOf(parsedAddressParts.getOrNull(3) ?: "") }
    var addressDetails by remember { mutableStateOf(parsedAddressParts.drop(4).joinToString("، ")) }
    var expandedProvince by remember { mutableStateOf(false) }
    var expandedCity by remember { mutableStateOf(false) }
    var referrer by remember { mutableStateOf(patient.referrer) }
    var fatherName by remember { mutableStateOf(patient.fatherName) }
    var nationalId by remember { mutableStateOf(patient.nationalId) }
    var capturedSignaturePath by remember { mutableStateOf<String?>(null) }
    var preferredMessenger by remember { mutableStateOf(if (patient.preferredMessenger.isNotBlank()) patient.preferredMessenger else "واتساپ") }
    var note by remember { mutableStateOf(patient.note) }
    var medicalHistory by remember { mutableStateOf(patient.medicalHistory) }

    // موارد هشدار پزشکی و خوداظهاری سلامت اکنون یک فهرست واحد و یکپارچه است (لیست مشترک DENTAL_MEDICAL_ALERT_ITEMS)
    // برای بیمارانی که پیش از این ادغام ثبت شده‌اند، تلاش می‌شود داده‌های قبلی (چه از فهرست هشدارهای قدیمی و چه از
    // پرسشنامه جداگانه قبلی) به‌صورت best-effort به آیتم‌های جدید نگاشت شوند تا داده‌ای از دست نرود.
    val legacyAlertLabels = remember(patient.medicalAlerts) {
        patient.medicalAlerts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val legacySelfDec = remember(patient.selfDeclarationJson) {
        try {
            if (patient.selfDeclarationJson.isNotBlank()) org.json.JSONObject(patient.selfDeclarationJson) else null
        } catch (e: Exception) { null }
    }
    var selectedAlerts by remember {
        mutableStateOf(
            DENTAL_MEDICAL_ALERT_ITEMS.filter { item ->
                // ۱) تطبیق مستقیم با برچسب جدید (برای بیمارانی که قبلاً با همین فهرست یکپارچه ثبت شده‌اند)
                legacyAlertLabels.any { it == item.labelFa } ||
                // ۲) تطبیق کلیدواژه‌ای با فهرست‌های قدیمی و متفاوت دو دیالوگ قبلی
                legacyAlertLabels.any { old ->
                    when (item.slug) {
                        "diabetes" -> old.contains("دیابت")
                        "htn" -> old.contains("فشار") && !old.contains("پایین") && !old.contains("افت")
                        "cardiac" -> old.contains("قلب")
                        "asthma" -> old.contains("آسم")
                        "epilepsy" -> old.contains("صرع")
                        "kidney" -> old.contains("کلی")
                        "liver" -> old.contains("کبد") || old.contains("هپاتیت") || old.contains("ایدز") || old.contains("HIV")
                        "thyroid" -> old.contains("تیروئید")
                        "bleeding_disorder" -> old.contains("انعقاد") || old.contains("رقیق")
                        "allergy" -> old.contains("حساسیت") || old.contains("پنسلین") || old.contains("پنی")
                        "pregnancy" -> old.contains("بارداری") || old.contains("شیردهی")
                        else -> false
                    }
                } ||
                // ۳) تطبیق با پاسخ‌های پرسشنامه جداگانه قدیمی (bleeding/meds/pregnant/surgery)
                (item.slug == "bleeding_history" && legacySelfDec?.optString("bleeding") == "بله") ||
                (item.slug == "bleeding_disorder" && legacySelfDec?.optString("meds") == "بله") ||
                (item.slug == "allergy" && legacySelfDec?.optString("allergy") == "بله") ||
                (item.slug == "pregnancy" && legacySelfDec?.optString("pregnant") == "بله") ||
                (item.slug == "recent_surgery" && legacySelfDec?.optString("surgery") == "بله")
            }.map { it.labelFa }.toSet()
        )
    }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isPersian) "ویرایش پرونده بیمار و فرم خوداظهاری" else "Edit Patient Record & Self-Declaration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isPersian) "اصلاح مشخصات، هشدارها و پاسخ‌های فرم سلامت" else "Modify info, alerts & health declaration answers",
                    fontSize = 11.sp,
                    color = Color.Gray
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
                // Patient Name (Required with Live Validation)
                val editNameError = if (name.isBlank()) "نام بیمار نمی‌تواند خالی باشد" else null
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isPersian) "نام و نام خانوادگی بیمار *" else "Patient Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = editNameError != null,
                    supportingText = {
                        if (editNameError != null) {
                            Text(editNameError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                )

                // Phone Number (+989 fixed, LTR layout & Live Validation)
                val editPhoneError = if (phoneDigits.length != 9) "شماره همراه باید دقیقاً ۹ رقم باشد" else null
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = phoneDigits,
                        onValueChange = { input -> 
                            val digits = input.filter { it.isDigit() }.take(9)
                            phoneDigits = digits 
                        },
                        label = { Text(if (isPersian) "شماره موبایل (۹ رقم) *" else "Mobile Number *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        leadingIcon = { Text("+98 9", modifier = Modifier.padding(start=16.dp), fontWeight = FontWeight.Bold) },
                        isError = editPhoneError != null,
                        supportingText = {
                            if (editPhoneError != null) {
                                Text(editPhoneError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Text("${phoneDigits.length} / 9 رقم", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    )
                }

                // National ID & Father's Name Section (کد ملی و نام پدر — غیرالزامی)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nationalId,
                        onValueChange = { nationalId = it },
                        label = { Text(if (isPersian) "کد ملی (اختیاری)" else "National ID (Opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text(if (isPersian) "نام پدر (اختیاری)" else "Father's Name (Opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Province & City Selectors (استان و شهر)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Province Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedProvince,
                        onExpandedChange = { expandedProvince = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = province,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "استان" else "Province") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvince) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProvince,
                            onDismissRequest = { expandedProvince = false }
                        ) {
                            com.example.util.IranianProvinces.getProvinces().forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, fontSize = 12.sp) },
                                    onClick = {
                                        province = p
                                        val cities = com.example.util.IranianProvinces.getCities(p)
                                        if (!cities.contains(city)) {
                                            city = cities.firstOrNull() ?: ""
                                        }
                                        expandedProvince = false
                                    }
                                )
                            }
                        }
                    }

                    // City Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedCity,
                        onExpandedChange = { expandedCity = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "شهر" else "City") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCity) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCity,
                            onDismissRequest = { expandedCity = false }
                        ) {
                            com.example.util.IranianProvinces.getCities(province).forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c, fontSize = 12.sp) },
                                    onClick = {
                                        city = c
                                        expandedCity = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3-Split Address Fields & Referrer
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = addressStreet,
                            onValueChange = { addressStreet = it },
                            label = { Text(if (isPersian) "خیابان / محله" else "Street / District") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = addressPelak,
                            onValueChange = { addressPelak = it },
                            label = { Text(if (isPersian) "پلاک / واحد" else "Pelak / Unit") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = addressDetails,
                        onValueChange = { addressDetails = it },
                        label = { Text(if (isPersian) "سایر جزییات آدرس (کوچه، پلاک،..." else "Additional Address Details") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = referrer,
                        onValueChange = { referrer = it },
                        label = { Text(if (isPersian) "معرف بیمار / نحوه آشنایی" else "Referrer / Channel") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }

                    // Preferred Messenger Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "پیام‌رسان پیش‌فرض بیمار جهت ارسال پیام و مدارک:" else "Preferred Messenger:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val messengers = listOf("واتساپ", "تلگرام", "روبیکا", "بله", "پیامک")
                            messengers.forEach { m ->
                                FilterChip(
                                    selected = preferredMessenger == m,
                                    onClick = { preferredMessenger = m },
                                    label = { Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Age & Birth Date with automatic calculation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { input ->
                                // کیبورد عددی: فقط ارقام (حداکثر ۳ رقم) پذیرفته می‌شود
                                ageStr = input.filter { it.isDigit() }.take(3)
                                val parsedAge = ageStr.toEnglishIntOrNull()
                                if (parsedAge != null && parsedAge > 0 && parsedAge < 120) {
                                    birthDateStr = "${1405 - parsedAge}"
                                }
                            },
                            label = { Text(if (isPersian) "سن بیمار" else "Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        com.example.ui.components.BirthDateInput(
                            birthDateStr = birthDateStr,
                            onBirthDateChange = { input -> 
                                birthDateStr = input
                                val yearOnly = birthDateStr.takeWhile { it.isDigit() }.toEnglishIntOrNull()
                                if (yearOnly != null && yearOnly in 1300..1405) {
                                    ageStr = "${1405 - yearOnly}"
                                }
                            },
                            isPersian = isPersian,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Gender Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isPersian) "جنسیت بیمار:" else "Patient Gender:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val genderOptions = if (isPersian) listOf("آقای", "خانم") else listOf("Mr.", "Ms.")
                            genderOptions.forEach { g ->
                                val isMale = g == "آقای" || g == "Mr."
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            if (isMale) Icons.Default.Man else Icons.Default.Woman,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // موارد هشدار پزشکی و خوداظهاری سلامت (ادغام‌شده با فرم خوداظهاری قبلی)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isPersian) "📋 موارد هشدار، بیماری‌های خاص و خوداظهاری سلامت بیمار:" else "📋 Medical Warnings & Health Self-Declaration:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            val groupedEditAlerts = DENTAL_MEDICAL_ALERT_ITEMS.groupBy { it.category }
                            groupedEditAlerts.forEach { (catName, items) ->
                                Text(
                                    text = catName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                                items.forEach { item ->
                                    val isSel = selectedAlerts.contains(item.labelFa)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAlerts = if (isSel) selectedAlerts - item.labelFa else selectedAlerts + item.labelFa }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = item.labelFa, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = isSel,
                                            onCheckedChange = { checked -> selectedAlerts = if (checked) selectedAlerts + item.labelFa else selectedAlerts - item.labelFa },
                                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.error)
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(if (isPersian) "یادداشت اولیه" else "Initial Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = medicalHistory,
                        onValueChange = { medicalHistory = it },
                        label = { Text(if (isPersian) "سوابق پزشکی و ملاحظات" else "Medical History & Considerations") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // امضای دیجیتال بیمار در پرونده
                    if (!patient.signatureFileId.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (isPersian) "امضای لمسی موجود در پرونده بیمار:" else "Current Signature on File:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Card(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    SmartImage(fileId = patient.signatureFileId, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                }
                            }
                            Text(
                                text = "امضا و اثر انگشت الکترونیکی بیمار جهت تأیید صحت خوداظهاری پزشکی و درمان",
                                fontSize = 9.5.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    BallpointSignaturePad(
                        isPersian = isPersian,
                        onSignatureCaptured = { capturedSignaturePath = it },
                        onClear = { capturedSignaturePath = null }
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val alertsStr = selectedAlerts.joinToString(", ")
                        val selfDecMap = DENTAL_MEDICAL_ALERT_ITEMS.associate { item ->
                            item.slug to (if (selectedAlerts.contains(item.labelFa)) "بله" else "خیر")
                        }
                        val jsonStr = org.json.JSONObject(selfDecMap as Map<*, *>).toString()
                        val parsedAge = ageStr.toEnglishIntOrNull() ?: patient.age

                        val fullAddress = listOf(province, city, addressStreet, addressPelak, addressDetails).filter { it.isNotBlank() }.joinToString("، ")
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val uploadedSigFileId = if (capturedSignaturePath != null) {
                                viewModel.uploadLocalFileOrNull(capturedSignaturePath) ?: capturedSignaturePath
                            } else patient.signatureFileId

                            val updated = patient.copy(
                                name = name,
                                phone = "+989$phoneDigits",
                                address = fullAddress,
                                referrer = referrer,
                                gender = gender,
                                age = parsedAge,
                                birthDate = birthDateStr,
                                medicalAlerts = alertsStr,
                                selfDeclarationJson = jsonStr,
                                note = note,
                                medicalHistory = medicalHistory,
                                preferredMessenger = preferredMessenger,
                                fatherName = fatherName,
                                nationalId = nationalId,
                                signatureFileId = uploadedSigFileId
                            )
                            viewModel.updatePatient(updated)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onDismiss()
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isPersian) "ذخیره تغییرات" else "Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}

data class EndoCanalData(
    val name: String,
    val lengthMm: String = "21.0",
    val refPoint: String = "Cusp Tip",
    val mafFile: String = "#30",
    // بازه کنترلی طول کار (mm) مخصوص همین کانال/دندان - بر اساس میانگین‌های استاندارد اندودانتیکس (جداول Ingle / Weine)
    val minLengthMm: Double = 0.5,
    val maxLengthMm: Double = 30.0
)

/**
 * پروفایل تشریحی هر دندان: تعداد و نام علمی کانال‌ها، طول نرمال/میانگین (پیش‌فرض)
 * و بازه کنترلی مخصوص همان دندان (کمینه سراسری ۰.۵ میلی‌متر طبق الزام پروژه).
 * مقادیر میانگین بر اساس جداول مرجع آموزشی اندودانتیکس (Ingle's Endodontics / Weine's average tooth length table).
 */
fun buildCanal(spec: CanalAnatomySpec): EndoCanalData {
    return EndoCanalData(
        name = spec.name,
        lengthMm = spec.avgLength.toString(),
        refPoint = spec.refPoint,
        mafFile = spec.mafFile,
        minLengthMm = 0.5,
        maxLengthMm = spec.maxLength
    )
}

fun getDefaultCanalsForTeeth(selectedTeethStr: String, treatmentTitle: String = ""): List<EndoCanalData> {
    val toothDigits = Regex("""\d+""").findAll(selectedTeethStr).map { it.value }.toList()
    val teeth = if (toothDigits.isNotEmpty()) toothDigits else selectedTeethStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val firstToothNum = teeth.firstOrNull()?.toIntOrNull() ?: 14
    return EndoAnatomyManager.canalSpecsForTooth(firstToothNum, treatmentTitle).filter { it.isDefault }.map { buildCanal(it) }
}

fun parseEndoCanalData(notes: String, selectedTeeth: String = "", treatmentTitle: String = ""): List<EndoCanalData> {
    val defaults = getDefaultCanalsForTeeth(selectedTeeth, treatmentTitle).toMutableList()
    if (!notes.contains("[ENDO_WORKING_LENGTH:")) {
        return defaults
    }
    return try {
        val regex = Regex("""\[ENDO_WORKING_LENGTH:\s*(\[[\s\S]*?\])\s*\]""")
        val match = regex.find(notes)
        if (match != null) {
            val jsonStr = match.groupValues[1]
            val array = org.json.JSONArray(jsonStr)
            
            val savedData = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                savedData[obj.optString("name", "MB")] = obj
            }
            
            val finalList = mutableListOf<EndoCanalData>()
            defaults.forEach { defaultCanal ->
                val savedObj = savedData[defaultCanal.name]
                if (savedObj != null) {
                    finalList.add(defaultCanal.copy(
                        lengthMm = savedObj.optString("lengthMm", defaultCanal.lengthMm),
                        refPoint = savedObj.optString("refPoint", defaultCanal.refPoint),
                        mafFile = savedObj.optString("mafFile", defaultCanal.mafFile)
                    ))
                    savedData.remove(defaultCanal.name)
                } else {
                    finalList.add(defaultCanal)
                }
            }
            
            savedData.values.forEach { obj ->
                val canalName = obj.optString("name", "Extra")
                val fallback = defaults.firstOrNull()
                finalList.add(
                    EndoCanalData(
                        name = canalName,
                        lengthMm = obj.optString("lengthMm", "21.0"),
                        refPoint = obj.optString("refPoint", "Cusp Tip"),
                        mafFile = obj.optString("mafFile", "#30"),
                        minLengthMm = obj.optDouble("minLengthMm", fallback?.minLengthMm ?: 0.5),
                        maxLengthMm = obj.optDouble("maxLengthMm", fallback?.maxLengthMm ?: 30.0)
                    )
                )
            }
            finalList
        } else {
            defaults
        }
    } catch (e: Exception) {
        defaults
    }
}

fun encodeEndoCanalData(existingNotes: String, canals: List<EndoCanalData>): String {
    val cleanNotes = existingNotes.cleanNotesForDisplay()
    val jsonArray = org.json.JSONArray()
    canals.forEach { c ->
        val obj = org.json.JSONObject()
        obj.put("name", c.name)
        obj.put("lengthMm", c.lengthMm)
        obj.put("refPoint", c.refPoint)
        obj.put("mafFile", c.mafFile)
        obj.put("minLengthMm", c.minLengthMm)
        obj.put("maxLengthMm", c.maxLengthMm)
        jsonArray.put(obj)
    }
    val tag = "[ENDO_WORKING_LENGTH: ${jsonArray.toString()}]"
    return if (cleanNotes.isBlank()) tag else "$cleanNotes\n$tag"
}

fun String.cleanNotesForDisplay(): String {
    return this.replace(Regex("""\[ENDO_WORKING_LENGTH:\s*\[[\s\S]*?\]\s*\]"""), "")
               .replace(Regex("""\[RESTORATION_SURFACES:\s*[^\]]*\]"""), "")
               .replace(Regex("""\[CAVITY_CLASS:\s*[^\]]*\]"""), "")
               .trim()
}

@Composable
fun ToothSchematicDiagram(
    selectedTeeth: String,
    canals: List<EndoCanalData>,
    isPersian: Boolean
) {
    val toothNum = selectedTeeth.trim().ifBlank { "14" }
    val isUpperArch = remember(toothNum) {
        val digits = Regex("""\d+""").find(toothNum)?.value
        val num = digits?.toIntOrNull() ?: 14
        (num in 11..28) || (num in 51..68)
    }

    // Color mapper according to prompt requirement:
    // Lingual / Tongue (زبان/زبانی): RED (قرمز)
    // Facial / Buccal (صورت/دهانی): ORANGE (نارنجی)
    // Posterior / Mesial / Distal / Central (پشت/مزیال/دیستال/مرکزی): BLUE (آبی)
    fun getCanalDirectionColor(canalName: String): Color {
        // رنگ‌بندی مستقل و متمایز برای هر کانال بر اساس نام علمی آن (نه فقط جهت کلی)،
        // تا در دندان‌های چندکاناله مثل مولارها هر کانال به‌وضوح از بقیه قابل تفکیک باشد.
        return when (canalName.uppercase()) {
            "MB" -> Color(0xFFFF9800)   // نارنجی - Mesio-Buccal
            "MB2" -> Color(0xFFFFC107)  // کهربایی - Mesio-Buccal 2
            "DB" -> Color(0xFFE64A19)   // نارنجی سوخته - Disto-Buccal
            "P", "PALATAL" -> Color(0xFF8E24AA) // بنفش - Palatal
            "ML" -> Color(0xFF26A69A)   // فیروزه‌ای - Mesio-Lingual
            "DL" -> Color(0xFF00897B)   // سبزآبی - Disto-Lingual
            "D", "DISTAL" -> Color(0xFF42A5F5) // آبی - Distal
            "M", "MESIAL" -> Color(0xFF66BB6A) // سبز - Mesial
            "B", "BUCCAL", "F", "FACIAL" -> Color(0xFFFF9800) // نارنجی - Buccal (تک‌کانال)
            "L", "LINGUAL" -> Color(0xFFEF5350) // قرمز - Lingual (تک‌کانال)
            "C" -> Color(0xFF42A5F5)    // آبی - کانال منفرد قدامی (Central/Single)
            else -> Color(0xFF9E9E9E)   // خاکستری - نام ناشناخته
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersian) "🦷 نمایه ۳‌بعدی کانال‌ها (دندان $toothNum)" else "🦷 3D Root Canal View (Tooth $toothNum)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = if (isUpperArch) Color(0xFF1E88E5).copy(alpha = 0.15f) else Color(0xFF43A047).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isUpperArch) Color(0xFF1E88E5) else Color(0xFF43A047))
                ) {
                    Text(
                        text = if (isPersian) (if (isUpperArch) "فک بالا (Maxilla) ▲" else "فک پایین (Mandible) ▼") else (if (isUpperArch) "Maxillary ▲" else "Mandibular ▼"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpperArch) Color(0xFF1565C0) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Directional Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF5350), CircleShape))
                    Text(if (isPersian) "زبان (زبانی/پالاتال)" else "Tongue (Lingual)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                    Text(if (isPersian) "صورت (دهانی/بوکال)" else "Face (Buccal)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF42A5F5), CircleShape))
                    Text(if (isPersian) "پشت (مزیال/دیستال)" else "Back (Mesial/Distal)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42A5F5))
                }
            }

            // Canvas for 3D Schematic
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B)
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw background grid lines
                val gridStep = 20.dp.toPx()
                var gx = gridStep
                while (gx < canvasWidth) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.04f),
                        start = androidx.compose.ui.geometry.Offset(gx, 0f),
                        end = androidx.compose.ui.geometry.Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridStep
                }

                val rulerX = 28.dp.toPx()
                val crownWidth = canvasWidth * 0.62f
                val crownLeft = (canvasWidth - crownWidth) / 2f
                val crownRight = crownLeft + crownWidth

                // بازه خط‌کش به‌صورت پویا بر اساس بازه کنترلی واقعی همین دندان محاسبه می‌شود
                // (مثلاً برای کانین که ریشه بلندتری دارد، خط‌کش هم بازه وسیع‌تری نشان می‌دهد)
                val canalMinBound = (canals.minOfOrNull { it.minLengthMm } ?: 10.0).toFloat()
                val canalMaxBound = (canals.maxOfOrNull { it.maxLengthMm } ?: 30.0).toFloat()
                val rulerLo = (kotlin.math.floor(canalMinBound / 5f) * 5f).coerceAtLeast(0f)
                val rulerHiRaw = kotlin.math.ceil(canalMaxBound / 5f) * 5f
                val rulerHi = if (rulerHiRaw <= rulerLo) rulerLo + 5f else rulerHiRaw
                val rulerTicks = generateSequence(rulerLo) { it + 5f }.takeWhile { it <= rulerHi + 0.01f }.toList()

                // Determine Crown & Root direction based on Upper/Lower Arch
                // Upper Arch (Maxilla): Crown at bottom, Root Apex extending UPWARDS
                // Lower Arch (Mandible): Crown at top, Root Apex extending DOWNWARDS
                val crownHeight = 36.dp.toPx()
                val crownTop: Float
                val crownBottom: Float
                val rulerStartY: Float
                val rulerEndY: Float

                if (isUpperArch) {
                    crownBottom = canvasHeight - 12.dp.toPx()
                    crownTop = crownBottom - crownHeight
                    rulerStartY = crownTop
                    rulerEndY = 16.dp.toPx()
                } else {
                    crownTop = 12.dp.toPx()
                    crownBottom = crownTop + crownHeight
                    rulerStartY = crownBottom
                    rulerEndY = canvasHeight - 16.dp.toPx()
                }

                // Ruler line
                drawLine(
                    color = Color(0xFF64748B),
                    start = androidx.compose.ui.geometry.Offset(rulerX, rulerStartY),
                    end = androidx.compose.ui.geometry.Offset(rulerX, rulerEndY),
                    strokeWidth = 2.dp.toPx()
                )

                val mmStep = (rulerEndY - rulerStartY) / (rulerTicks.size - 1).coerceAtLeast(1)
                rulerTicks.forEachIndexed { i, mmLabel ->
                    val y = rulerStartY + i * mmStep
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = androidx.compose.ui.geometry.Offset(rulerX - 4.dp.toPx(), y),
                        end = androidx.compose.ui.geometry.Offset(rulerX + 4.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        mmLabel.toInt().toString(),
                        rulerX - 20.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#94A3B8")
                            textSize = 9.dp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                    )
                }

                // Draw Tooth Crown (3D Shaded)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFE2E8F0),
                            Color(0xFFCBD5E1)
                        )
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(crownLeft, crownTop),
                    size = androidx.compose.ui.geometry.Size(crownWidth, crownHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )

                // Pulp Chamber Shading
                val pulpMarginX = crownWidth * 0.2f
                val pulpMarginY = crownHeight * 0.25f
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFF880E4F)),
                        center = androidx.compose.ui.geometry.Offset(crownLeft + crownWidth * 0.5f, crownTop + crownHeight * 0.5f),
                        radius = 22.dp.toPx()
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(crownLeft + pulpMarginX, crownTop + pulpMarginY),
                    size = androidx.compose.ui.geometry.Size(crownWidth - pulpMarginX * 2, crownHeight - pulpMarginY * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Render each Root Canal
                val numCanals = canals.size.coerceAtLeast(1)
                val canalSpacing = crownWidth / (numCanals + 1)
                val canalXPositions = (1..numCanals).map { crownLeft + it * canalSpacing }
                val rootDirSign = if (isUpperArch) -1f else 1f // ریشه‌ها از تاج به سمت اپکس (بالا برای فک بالا، پایین برای فک پایین) واگرا می‌شوند
                val rootBaseY = if (isUpperArch) crownTop else crownBottom
                // نکته مهم: طول/موقعیت شماتیک ریشه فقط به بازه استاندارد آناتومیک دندان (rulerLo/rulerHi) وابسته است،
                // نه به مقدار فعلی canal.lengthMm که کاربر در حال ویرایش آن است. به همین دلیل با تغییر عدد طول کار
                // توسط کاربر (دکمه‌های +/-)، شکل و اندازه ریشه ثابت می‌ماند و فقط خط رنگی کانال داخل آن جابه‌جا می‌شود.
                val rootExtent = kotlin.math.abs(rulerEndY - rootBaseY) * 0.58f

                // شماتیک بدنه ریشه (سایه‌روشن) متناسب با تعداد ریشه استاندارد همین دندان و با تیپرینگ واقعی‌تر
                // (باریک و نوک‌تیز به سمت اپکس، مطابق شکل واقعی ریشه دندان در تصویر مرجع)
                canalXPositions.forEachIndexed { idx, rx ->
                    val spread = if (numCanals == 1) 0f else (rx - (crownLeft + crownWidth / 2f)) * 0.95f
                    val rootTipX = rx + spread
                    val rootTipY = rootBaseY + rootDirSign * rootExtent
                    val rootWidthAtBase = (crownWidth / (numCanals + 1)) * 0.68f
                    val midY = rootBaseY + rootDirSign * rootExtent * 0.55f

                    val rootPath = Path().apply {
                        moveTo(rx - rootWidthAtBase / 2f, rootBaseY)
                        // ضلع چپ ریشه: از عریض در قاعده به‌تدریج باریک می‌شود
                        quadraticTo(rx - rootWidthAtBase * 0.42f, midY, rx - rootWidthAtBase * 0.08f, rootTipY - rootDirSign * (rootExtent * 0.08f))
                        // نوک تیز و مخروطی اپکس (مطابق ریشه واقعی دندان)
                        lineTo(rootTipX, rootTipY)
                        lineTo(rx + rootWidthAtBase * 0.08f, rootTipY - rootDirSign * (rootExtent * 0.08f))
                        // ضلع راست ریشه
                        quadraticTo(rx + rootWidthAtBase * 0.42f, midY, rx + rootWidthAtBase / 2f, rootBaseY)
                        close()
                    }
                    drawPath(
                        path = rootPath,
                        brush = Brush.verticalGradient(
                            colors = if (isUpperArch) listOf(Color(0xFFEDE4CE), Color(0xFFCBBE9C)) else listOf(Color(0xFFCBBE9C), Color(0xFFEDE4CE))
                        ),
                        alpha = 0.55f
                    )
                    drawPath(
                        path = rootPath,
                        color = Color(0xFF8D7B68).copy(alpha = 0.65f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }

                canals.forEachIndexed { idx, canal ->
                    val canalX = canalXPositions[idx]
                    val lengthVal = canal.lengthMm.toFloatOrNull() ?: 21.0f
                    val normalized = ((lengthVal - rulerLo) / (rulerHi - rulerLo)).coerceIn(0.03f, 1.0f)

                    val apexY = rulerStartY + normalized * (rulerEndY - rulerStartY)
                    val canalBaseColor = getCanalDirectionColor(canal.name)

                    val curveOffsetX = if (idx == 0) -10.dp.toPx() else if (idx == numCanals - 1) 10.dp.toPx() else 0f
                    val apexX = canalX + curveOffsetX

                    val canalStartY = if (isUpperArch) crownTop else crownBottom

                    val path = Path().apply {
                        moveTo(canalX, canalStartY)
                        cubicTo(
                            canalX, canalStartY + (apexY - canalStartY) * 0.4f,
                            apexX, canalStartY + (apexY - canalStartY) * 0.7f,
                            apexX, apexY
                        )
                    }

                    // Outer glow
                    drawPath(
                        path = path,
                        color = canalBaseColor.copy(alpha = 0.35f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 10.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Canal Core
                    drawPath(
                        path = path,
                        color = canalBaseColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Canal Inner Highlight
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.75f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Length dashed line to ruler
                    drawLine(
                        color = canalBaseColor.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(rulerX, apexY),
                        end = androidx.compose.ui.geometry.Offset(apexX, apexY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Apex Point Circles
                    drawCircle(
                        color = canalBaseColor.copy(alpha = 0.4f),
                        radius = 8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                    drawCircle(
                        color = canalBaseColor,
                        radius = 4.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(apexX, apexY)
                    )
                }
            }

            // Canal Badges Row below diagram
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                canals.forEach { canal ->
                    val canalColor = getCanalDirectionColor(canal.name)

                    Surface(
                        color = canalColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, canalColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "کانال ${canal.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = canalColor
                            )
                            Text(
                                text = "${canal.lengthMm} mm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = canalColor
                            )
                            Text(
                                text = "MAF ${canal.mafFile}",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun parseRestorationSurfaces(notes: String): Set<String> {
    val regex = Regex("""\[RESTORATION_SURFACES:\s*([^\]]*)\]""")
    val match = regex.find(notes)
    if (match != null) {
        val str = match.groupValues[1]
        return str.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }
    return emptySet()
}

fun parseCavityClass(notes: String): String {
    val regex = Regex("""\[CAVITY_CLASS:\s*([^\]]*)\]""")
    val match = regex.find(notes)
    return match?.groupValues?.get(1)?.trim() ?: ""
}

fun encodeRestorationData(existingNotes: String, surfaces: Set<String>, cavityClass: String): String {
    val cleanNotes = existingNotes.cleanNotesForDisplay()
    val surfStr = surfaces.joinToString(", ")
    var tag = ""
    if (surfaces.isNotEmpty()) {
        tag += "[RESTORATION_SURFACES: $surfStr]"
    }
    if (cavityClass.isNotBlank()) {
        if (tag.isNotEmpty()) tag += "\n"
        tag += "[CAVITY_CLASS: $cavityClass]"
    }
    return if (cleanNotes.isBlank()) tag else "$cleanNotes\n$tag"
}

@Composable
fun ToothSurfaceRestorationCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val isRestorationTreatment = remember(treatment.title, treatment.notes) {
        val t = treatment.title.lowercase()
        t.contains("ترمیم") || t.contains("پر") || t.contains("کامپوزیت") || t.contains("آمالگام") ||
                t.contains("restor") || t.contains("composite") || t.contains("amalgam") || t.contains("filling") ||
                treatment.notes.contains("[RESTORATION_SURFACES:")
    }

    if (!isRestorationTreatment) return

    var expanded by remember { mutableStateOf(!readOnly) }
    var selectedSurfaces by remember(treatment.notes) {
        mutableStateOf(parseRestorationSurfaces(treatment.notes))
    }
    var cavityClass by remember(treatment.notes) {
        mutableStateOf(parseCavityClass(treatment.notes))
    }

    val toothNum = treatment.selectedTeeth.trim().ifBlank { "16" }
    val toothNumDigits = remember(toothNum) { Regex("""\d+""").find(toothNum)?.value ?: "16" }
    val toothInt = remember(toothNumDigits) { toothNumDigits.toIntOrNull() ?: 16 }
    val isAnterior = remember(toothInt) {
        (toothInt in 11..13) || (toothInt in 21..23) || (toothInt in 31..33) || (toothInt in 41..43) ||
        (toothInt in 51..53) || (toothInt in 61..63) || (toothInt in 71..73) || (toothInt in 81..83)
    }

    // Filter applicable G.V. Black Cavity Classes strictly by Tooth Type (Anterior vs Posterior)
    val applicableCavityClasses = remember(isAnterior) {
        if (isAnterior) {
            listOf("Class I", "Class III", "Class IV", "Class V")
        } else {
            listOf("Class I", "Class II (MOD)", "Class II (MO)", "Class II (DO)", "Class V")
        }
    }

    fun toggleSurface(code: String) {
        if (readOnly) return
        val newSet = selectedSurfaces.toMutableSet()
        if (newSet.contains(code)) newSet.remove(code) else newSet.add(code)
        selectedSurfaces = newSet
        val updatedNotes = encodeRestorationData(treatment.notes, newSet, cavityClass)
        viewModel.updateTreatment(treatment.copy(notes = updatedNotes))
    }

    fun selectCavityClass(cClass: String) {
        if (readOnly) return
        val newClass = if (cavityClass == cClass) "" else cClass
        cavityClass = newClass
        val newSurfaces = when (newClass) {
            "Class I" -> if (isAnterior) setOf("I") else setOf("O")
            "Class II (MO)" -> setOf("M", "O")
            "Class II (DO)" -> setOf("D", "O")
            "Class II (MOD)" -> setOf("M", "O", "D")
            "Class III" -> setOf("M", "D")
            "Class IV" -> setOf("I", "M", "D")
            "Class V" -> setOf("V")
            else -> selectedSurfaces
        }
        selectedSurfaces = newSurfaces
        val updatedNotes = encodeRestorationData(treatment.notes, newSurfaces, newClass)
        viewModel.updateTreatment(treatment.copy(notes = updatedNotes))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isPersian) {
                            if (isAnterior) "تصویر شماتیک و سطوح قدامی (دندان $toothNum)" else "تصویر شماتیک و سطوح خلفی (دندان $toothNum)"
                        } else {
                            if (isAnterior) "Anterior Caries Schematic & Surfaces (Tooth $toothNum)" else "Posterior Caries Schematic & Surfaces (Tooth $toothNum)"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (selectedSurfaces.isEmpty()) "انتخاب سطح" else selectedSurfaces.joinToString("-"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (expanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Display Interactive Caries Class Schematic Anatomy Diagram!
                        com.example.ui.components.CariesClassSchematicDiagram(
                            modifier = Modifier.fillMaxWidth(),
                            isAnterior = isAnterior,
                            toothNumber = toothInt,
                            selectedClass = cavityClass,
                            onClassSelected = { cClass ->
                                selectCavityClass(cClass)
                            }
                        )

                        // Quick Surface Chips Row
                        if (!readOnly) {
                            Text(
                                text = if (isPersian) "کلیدهای سریع انتخاب سطوح دندان:" else "Quick Surface Chips:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val allSurfaces = if (isAnterior) {
                                    listOf(
                                        "I" to (if (isPersian) "اینسیزال (I)" else "Incisal (I)"),
                                        "M" to (if (isPersian) "مزیال (M)" else "Mesial (M)"),
                                        "D" to (if (isPersian) "دیستال (D)" else "Distal (D)"),
                                        "B" to (if (isPersian) "لب/صورت (B)" else "Labial (B)"),
                                        "L" to (if (isPersian) "زبانی (L)" else "Lingual (L)"),
                                        "V" to (if (isPersian) "طوق (V - جینجیوال)" else "Gingival (V)")
                                    )
                                } else {
                                    listOf(
                                        "O" to (if (isPersian) "اکلوزال (O)" else "Occlusal (O)"),
                                        "M" to (if (isPersian) "مزیال (M)" else "Mesial (M)"),
                                        "D" to (if (isPersian) "دیستال (D)" else "Distal (D)"),
                                        "B" to (if (isPersian) "دهانی (B)" else "Buccal (B)"),
                                        "L" to (if (isPersian) "زبانی (L)" else "Lingual (L)"),
                                        "V" to (if (isPersian) "طوق (V - جینجیوال)" else "Gingival (V)")
                                    )
                                }

                                allSurfaces.forEach { (code, label) ->
                                    val isSel = selectedSurfaces.contains(code) || (code == "I" && selectedSurfaces.contains("O")) || (code == "O" && selectedSurfaces.contains("I"))
                                    val chipColor = when (code) {
                                        "L" -> Color(0xFFEF5350) // RED
                                        "B" -> Color(0xFFFF9800) // ORANGE
                                        "V" -> Color(0xFFAB47BC) // PURPLE (GINGIVAL)
                                        "I" -> Color(0xFF00ACC1) // CYAN
                                        else -> Color(0xFF1E88E5) // BLUE
                                    }

                                    FilterChip(
                                        selected = isSel,
                                        onClick = { toggleSurface(code) },
                                        label = { Text(label, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                            selectedLabelColor = chipColor
                                        ),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }

                            // Cavity Class Selection Filtered strictly by Anterior / Posterior
                            Text(
                                text = if (isPersian) "کلاس علمی پوسیدگی (Black's Classification) - ${if (isAnterior) "قدامی" else "خلفی"}:" else "Cavity Class (G.V. Black) - ${if (isAnterior) "Anterior" else "Posterior"}:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                applicableCavityClasses.forEach { cClass ->
                                    val isSel = cavityClass == cClass
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectCavityClass(cClass) },
                                        label = { Text(cClass, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EndoWorkingLengthCard(
    treatment: Treatment,
    viewModel: DentalViewModel,
    readOnly: Boolean = false
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val drugTemplates by viewModel.drugTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val isEndoTreatment = remember(treatment.title, treatment.notes) {
        val t = treatment.title.lowercase()
        val n = treatment.notes.lowercase()
        val hasEndoKeyword = t.contains("عصب") || t.contains("ریشه") || t.contains("rct") || t.contains("endo") || t.contains("پالپ") || n.contains("[endo_working_length:")
        val isPureRestoration = (t.contains("ترمیم") || t.contains("پرکردن") || t.contains("پر کردن") || t.contains("کامپوزیت") || t.contains("آمالگام") || t.contains("filling") || t.contains("restor")) && !t.contains("عصب") && !t.contains("ریشه") && !t.contains("endo") && !t.contains("rct")
        hasEndoKeyword && !isPureRestoration
    }

    if (!isEndoTreatment) return

    // Default accordion state: CLOSED BY DEFAULT for Patient Record view (readOnly = true), OPEN for Treatment session editing
    var expanded by remember { mutableStateOf(!readOnly) }
    var canalList by remember(treatment.notes, treatment.selectedTeeth, treatment.title) {
        mutableStateOf(parseEndoCanalData(treatment.notes, treatment.selectedTeeth, treatment.title))
    }

    val toothDigits = Regex("""\d+""").findAll(treatment.selectedTeeth).map { it.value }.toList()
    val teeth = if (toothDigits.isNotEmpty()) toothDigits else treatment.selectedTeeth.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val firstToothNum = teeth.firstOrNull()?.toIntOrNull() ?: 14
    val canalSpecs = EndoAnatomyManager.canalSpecsForTooth(firstToothNum, treatment.title)
    
    val stdMafOptions = listOf("#15", "#20", "#25", "#30", "#35", "#40")
    val largeMafOptions = listOf("#45", "#50", "#55", "#60", "#70", "#80")
    val rotaryMafOptions = listOf("F1", "F2", "F3", "F4", "F5", "20/.04", "25/.04", "25/.06", "30/.06")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Straighten,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isPersian) "🦷 طول کارکرد کانال‌های ریشه (Endo Length)" else "🦷 Root Canal Working Lengths",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${canalList.size} ${if (isPersian) "کانال" else "canals"}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (expanded) {
                // Tooth Graphic Schematic Diagram (Visible in both read-only and edit mode)
                ToothSchematicDiagram(
                    selectedTeeth = treatment.selectedTeeth,
                    canals = canalList,
                    isPersian = isPersian
                )

                // Interactive controls ONLY shown during treatment sessions (when readOnly = false)
                if (!readOnly) {
                    // Reset / Default Canals quick action button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "تنظیمات پیش‌فرض بر اساس دندان انتخاب‌شده:" else "Default Canals based on tooth:",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = {
                                val defaults = getDefaultCanalsForTeeth(treatment.selectedTeeth, treatment.title)
                                canalList = defaults
                                val newNotes = encodeEndoCanalData(treatment.notes, defaults)
                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(if (isPersian) "ریست پیش‌فرض" else "Reset Default", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Interactive List of canals for length adjustments during treatment sessions
                    canalList.forEachIndexed { index, canal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = when (canal.name) {
                                            "MB", "MB2" -> Color(0xFFE3F2FD)
                                            "DB" -> Color(0xFFE8F5E9)
                                            "P" -> Color(0xFFFFF3E0)
                                            "B" -> Color(0xFFE0F7FA)
                                            "L" -> Color(0xFFF3E5F5)
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "کانال ${canal.name}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (canal.name) {
                                                "MB", "MB2" -> Color(0xFF1565C0)
                                                "DB" -> Color(0xFF2E7D32)
                                                "P" -> Color(0xFFE65100)
                                                "B" -> Color(0xFF00838F)
                                                "L" -> Color(0xFF6A1B9A)
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Interactive length increment/decrement controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val currentVal = canal.lengthMm.toDoubleOrNull() ?: 21.0
                                                val newVal = (currentVal - 0.5).coerceAtLeast(canal.minLengthMm)
                                                val updated = canalList.toMutableList()
                                                updated[index] = canal.copy(lengthMm = String.format(java.util.Locale.US, "%.1f", newVal))
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("-0.5", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text(
                                                    text = "${canal.lengthMm} mm",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.1f", canal.minLengthMm)}–${String.format(java.util.Locale.US, "%.1f", canal.maxLengthMm)}",
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val currentVal = canal.lengthMm.toDoubleOrNull() ?: 21.0
                                                val newVal = (currentVal + 0.5).coerceAtMost(canal.maxLengthMm)
                                                val updated = canalList.toMutableList()
                                                updated[index] = canal.copy(lengthMm = String.format(java.util.Locale.US, "%.1f", newVal))
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("+0.5", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = canalList.toMutableList()
                                                updated.removeAt(index)
                                                canalList = updated
                                                val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isPersian) "سایز فایل نهائی (MAF):" else "MAF File:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Row 1: Standard Hand Files (#15 - #40)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۱. دستی (۱۵-۴۰):" else "1. Std:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            stdMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Large Hand Files (#45 - #80)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۲. ضخیم (۴۵-۸۰):" else "2. Large:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            largeMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 3: Rotary Standard Files
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "۳. روتاری:" else "3. Rotary:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rotaryMafOptions.forEach { maf ->
                                                val isSel = canal.mafFile == maf
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        val updated = canalList.toMutableList()
                                                        updated[index] = canal.copy(mafFile = maf)
                                                        canalList = updated
                                                        val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                                        viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                                    },
                                                    label = { Text(maf, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add Canal Options Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isPersian) "افزودن کانال جدید:" else "Add Canal:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            canalSpecs.filter { spec -> canalList.none { it.name == spec.name } }.forEach { spec ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val updated = canalList.toMutableList()
                                            updated.add(buildCanal(spec))
                                            canalList = updated
                                            val newNotes = encodeEndoCanalData(treatment.notes, updated)
                                            viewModel.updateTreatment(treatment.copy(notes = newNotes))
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("+ ${spec.name}", fontSize = 10.sp)
                                    }
                                    if (spec.probabilityNote != null) {
                                        Text(text = spec.probabilityNote, fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * لیست یکپارچه و جامع «موارد هشدار پزشکی و خوداظهاری سلامت بیمار».
 * پیش از این، این فهرست به‌صورت دو بخش جداگانه و ناهماهنگ (چک‌لیست هشدارها در یک تب،
 * و پرسشنامه بله/خیر در تب دیگر با فهرست متفاوت در دیالوگ افزودن در مقابل ویرایش بیمار) وجود داشت.
 * اکنون هر دو در یک فهرست واحد ادغام شده‌اند تا هم در ثبت بیمار جدید و هم ویرایش پرونده کاملاً یکسان باشند.
 * هر آیتم یک اسلاگ پایدار (برای ذخیره‌سازی JSON) و یک برچسب فارسی (برای نمایش/تطبیق با medicalAlerts) دارد.
 */
data class MedicalAlertItem(val slug: String, val labelFa: String, val category: String = "۱. تاریخچه پزشکی و سیستمیک")

val DENTAL_MEDICAL_ALERT_ITEMS = listOf(
    // ۱. تاریخچه پزشکی و سیستمیک (Medical History Checklist - 26 items)
    MedicalAlertItem("htn", "فشار خون بالا (Hypertension)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("hypotension", "فشار خون پایین / افت فشار و سرگیجه حین تزریق", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("mi", "سابقه سکته قلبی / آنژین صدری / استنت", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("hf", "نارسایی قلبی (Heart Failure)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("valve", "تعویض دریچه / دریچه مصنوعی قلب", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("pacemaker", "پیس‌میکر / ICD قلبی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("diabetes", "دیابت (نوع ۱ یا نوع ۲)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("thyroid", "اختلالات تیروئید (کم‌کاری / پرکاری)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("osteoporosis", "پوکی استخوان (Osteoporosis)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("allergy", "آلرژی دارویی (پنی‌سیلین، مسکن و...)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("allergy_latex", "آلرژی به لاتکس / بی‌حسی موضعی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("bleeding_disorder", "اختلالات خونریزی‌دهنده / هموفیلی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("bleeding_history", "سابقه خونریزی شدید پس از کشیدن دندان یا جراحی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("hepatitis", "هپاتیت (B / C) یا بیماری کبدی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("hiv", "عفونت HIV / نقص ایمنی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("tb", "سل (Tuberculosis)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("asthma", "آسم (Asthma) یا بیماری تنفسی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("copd", "بیماری انسدادی ریه (COPD)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("gerd", "ریفلاکس معده / زخم گوارشی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("kidney", "نارسایی کلیه / دیالیز", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("epilepsy", "صرع / اختلالات تشنج", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("psychiatric", "افسردگی / دوقطبی / اضطراب شدید", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("pregnancy", "بارداری (سن بارداری به ماه)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("lactation", "شیردهی", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("radiotherapy", "سابقه رادیوتراپی سر و گردن", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("chemotherapy", "شیمی‌درمانی فعال / اخیر", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("smoking", "مصرف دخانیات (سیگار / قلیان / ویپ)", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("alcohol", "مصرف الکل / مواد مخدر", "۱. تاریخچه پزشکی و سیستمیک"),
    MedicalAlertItem("recent_surgery", "سابقه جراحی یا بستری اخیر در بیمارستان", "۱. تاریخچه پزشکی و سیستمیک"),

    // ۲. چک‌لیست داروهای مصرفی پرخطر (Problematic Medications - 13 items)
    MedicalAlertItem("med_warfarin", "دارو: وارفارین (Warfarin)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_plavix", "دارو: کلوپیدوگرل (Plavix)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_asa", "دارو: آسپرین (دوز بالا / روزانه)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_noac", "دارو: ریواروکسابان / آپیکسابان (NOACs)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_bis_oral", "دارو: بیس‌فسفونات خوراکی (آلندرونات و...)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_bis_iv", "دارو: بیس‌فسفونات تزریقی / دنوزوماب", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_steroid", "دارو: کورتیکوستروئید طولانی‌مدت", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_metformin", "دارو: متفورمین / انسولین", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_maoi", "دارو: داروهای MAOI (ضد افسردگی)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_ssri", "دارو: داروهای SSRI / SNRI", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_lithium", "دارو: لیتیوم (Lithium)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_anti_epileptic", "دارو: داروهای ضد صرع (کاربامازپین/والپروات)", "۲. داروهای مصرفی پرخطر"),
    MedicalAlertItem("med_herbal", "دارو: مکمل گیاهی (سیر، جینکو، جنسینگ، ویتامین E)", "۲. داروهای مصرفی پرخطر"),

    // ۳. تاریخچه دندانپزشکی و شکایات اصلی (Dental History - 8 items)
    MedicalAlertItem("dh_pain", "شکایت: درد خودبه‌خود و شبانه", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_cold", "شکایت: حساسیت شدید به سرما / گرما", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_chew", "شکایت: درد هنگام جویدن / فشار", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_fear", "سوابق: ترس و اضطراب شدید از دندانپزشکی", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_anesthesia", "سوابق: عدم بی‌پاسخی به بی‌حسی / سنکوپ حین تزریق", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_bruxism", "سوابق: دندان‌قروچه (بروکسیسم) / ساییدگی شدید", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_esthet", "سوابق: بلیچینگ / ونیر / لمینت قبلی", "۳. تاریخچه دندانپزشکی و شکایات اصلی"),
    MedicalAlertItem("dh_symptom_only", "سوابق: مراجعه فقط هنگام بروز درد شدید", "۳. تاریخچه دندانپزشکی و شکایات اصلی")
)

fun isLabRelatedTreatment(tx: Treatment, services: List<LabService>): Boolean {
    val t = tx.title.lowercase()
    val n = tx.notes.lowercase()
    val labKeywords = listOf(
        "روکش", "crown", "لمینت", "laminate", "ونیر", "veneer", "بریج", "bridge", 
        "اینله", "آنله", "inlay", "onlay", "پروتز", "دست دندان", "denture", "ایمپلنت", 
        "implant", "الاینر", "aligner", "پلاک", "نایت گارد", "pfm", "zirconia", 
        "ssc", "لابراتوار", "lab", "سرامیک", "کست"
    )
    if (labKeywords.any { kw -> t.contains(kw) || n.contains(kw) }) return true
    return services.any { s -> s.serviceName.contains(tx.title, ignoreCase = true) || tx.title.contains(s.serviceName, ignoreCase = true) }
}

fun Modifier.threeSecondLongPress(
    onLongClick: () -> Unit,
    onClick: (() -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var isLongPress = false
            val timerJob = this@coroutineScope.launch {
                delay(2000L) // Exactly 2 seconds of continuous holding
                isLongPress = true
                onLongClick()
            }
            val up = waitForUpOrCancellation()
            timerJob.cancel()
            if (!isLongPress && up != null) {
                onClick?.invoke()
            }
        }
    }
}

@Composable
fun ClinicalProtocolDialog(
    therapyTitle: String,
    patientMedicalHistory: String = "",
    patientMedicalAlerts: String = "",
    isPersian: Boolean = true,
    initialFontSizePt: Int = 11,
    onDismiss: () -> Unit
) {
    val initialProtocol = remember(therapyTitle) { TherapyKnowledgeBase.findProtocol(therapyTitle) }
    if (initialProtocol == null) return

    var currentProtocol by remember(therapyTitle) { mutableStateOf(initialProtocol) }
    val fontSizePt = initialFontSizePt
    var isEditMode by remember { mutableStateOf(false) }

    // Draft state for edit mode
    var editNameFa by remember(currentProtocol) { mutableStateOf(currentProtocol.nameFa) }
    var editSessionsText by remember(currentProtocol) { 
        mutableStateOf(currentProtocol.sessions.joinToString("\n---\n") { "${it.titleFa}\n${it.descFa}" }) 
    }
    var editPrecautionsText by remember(currentProtocol) { mutableStateOf(currentProtocol.precautions.joinToString("\n")) }
    var editPrescriptionsText by remember(currentProtocol) { mutableStateOf(currentProtocol.prescriptions.joinToString("\n")) }
    var editMaterialsText by remember(currentProtocol) { 
        mutableStateOf(
            if (currentProtocol.modernMaterials.isNotEmpty()) currentProtocol.modernMaterials.joinToString("\n")
            else "• بوندینگ نسل ۸ Universal\n• فایل‌های روتاری تخصصی heat-treated\n• رزین و بیوسرامیک‌های جدید\n• ماتریس و وج‌های سیلیکونی"
        ) 
    }
    var editComplicationsText by remember(currentProtocol) { 
        mutableStateOf(currentProtocol.complications.joinToString("\n") { "${it.first} : ${it.second}" }) 
    }
    var editPostOpText by remember(currentProtocol) { mutableStateOf(currentProtocol.postSessionInstructions.joinToString("\n")) }

    val historyFlags = remember(currentProtocol, patientMedicalHistory, patientMedicalAlerts) {
        TherapyKnowledgeBase.matchingHistoryFlags(
            currentProtocol,
            patientMedicalHistory,
            patientMedicalAlerts
        )
    }

    val bodySize = fontSizePt.sp
    val titleSize = (fontSizePt + 2).sp
    // این صفحه سایز فونت اختصاصی خودش را دارد و از لغزنده‌ی سراسری فونت (تنظیمات اختصاصی) مستثنی است
    val unscaledDensity = LocalDensity.current
    val protocolDensity = androidx.compose.ui.unit.Density(density = unscaledDensity.density, fontScale = 1f)

    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides protocolDensity) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) currentProtocol.nameFa else currentProtocol.nameEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizePt + 3).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edit Protocol",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (isPersian) "💡 لمس طولانی ۳ ثانیه‌ای برای فراخوانی شرح بالینی" else "💡 Hold for 3s to open clinical protocol",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
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
                if (isEditMode) {
                    // EDIT MODE FORM
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPersian) "✏️ حالت ویرایش شرح بالینی و متریال‌ها (تغییرات را پس از ویرایش ذخیره کنید):"
                            else "✏️ Edit Mode: Modify clinical guidelines & materials:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = editNameFa,
                        onValueChange = { editNameFa = it },
                        label = { Text("عنوان پروتکل درمانی") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editSessionsText,
                        onValueChange = { editSessionsText = it },
                        label = { Text("مراحل و شرح جلسات (جداسازی با ---)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = editMaterialsText,
                        onValueChange = { editMaterialsText = it },
                        label = { Text("🧪 متریال‌ها، تجهیزات و مواد مصرفی بروز") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = editPrecautionsText,
                        onValueChange = { editPrecautionsText = it },
                        label = { Text("احتیاط‌ها و هشدارهای بالینی") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editPrescriptionsText,
                        onValueChange = { editPrescriptionsText = it },
                        label = { Text("داروهای تجویزی (ADA/AHA)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editComplicationsText,
                        onValueChange = { editComplicationsText = it },
                        label = { Text("عوارض بالینی و راهکارها (فرمت: عارضه : راهکار)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = editPostOpText,
                        onValueChange = { editPostOpText = it },
                        label = { Text("سفارشات ترخیص و مراقبت‌های بعدی") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Button(
                        onClick = {
                            val newSessions = editSessionsText.split("---").mapNotNull { block ->
                                val lines = block.trim().lines().filter { it.isNotBlank() }
                                if (lines.isNotEmpty()) {
                                    TherapySessionInfo(
                                        titleFa = lines.first(),
                                        descFa = lines.drop(1).joinToString("\n")
                                    )
                                } else null
                            }
                            val newPrecautions = editPrecautionsText.lines().filter { it.isNotBlank() }
                            val newPrescriptions = editPrescriptionsText.lines().filter { it.isNotBlank() }
                            val newMaterials = editMaterialsText.lines().filter { it.isNotBlank() }
                            val newComplications = editComplicationsText.lines().mapNotNull { line ->
                                if (line.contains(":")) {
                                    val parts = line.split(":", limit = 2)
                                    parts[0].trim() to parts[1].trim()
                                } else if (line.isNotBlank()) line to "" else null
                            }
                            val newPostOp = editPostOpText.lines().filter { it.isNotBlank() }

                            val updated = currentProtocol.copy(
                                nameFa = editNameFa,
                                sessions = if (newSessions.isNotEmpty()) newSessions else currentProtocol.sessions,
                                precautions = if (newPrecautions.isNotEmpty()) newPrecautions else currentProtocol.precautions,
                                prescriptions = if (newPrescriptions.isNotEmpty()) newPrescriptions else currentProtocol.prescriptions,
                                modernMaterials = newMaterials,
                                complications = if (newComplications.isNotEmpty()) newComplications else currentProtocol.complications,
                                postSessionInstructions = if (newPostOp.isNotEmpty()) newPostOp else currentProtocol.postSessionInstructions
                            )
                            TherapyKnowledgeBase.updateProtocol(updated)
                            currentProtocol = updated
                            isEditMode = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPersian) "ذخیره تغییرات شرح بالینی" else "Save Protocol Changes")
                    }
                } else {
                    // VIEW MODE WITH CUSTOMIZABLE FONT SIZE
                    if (historyFlags.isNotEmpty()) {
                        Text(
                            text = if (isPersian) "⚠️ ملاحظات اختصاصی سابقه این بیمار:" else "⚠️ Patient-Specific History Considerations:",
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSize,
                            color = MaterialTheme.colorScheme.error
                        )
                        historyFlags.forEach { flag ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (flag.severity == FlagSeverity.CONTRAINDICATION)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = flag.warningFa,
                                    fontSize = bodySize,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    Text(
                        text = if (isPersian) "📋 شرح جلسات و مراحل درمان:" else "📋 Sessions & Stage Breakdown:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.sessions.forEach { s ->
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(text = s.titleFa, fontWeight = FontWeight.Bold, fontSize = (fontSizePt + 0.5).sp)
                            Text(text = s.descFa, fontSize = bodySize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()

                    // SECTION: MODERN MATERIALS & EQUIPMENT
                    Text(
                        text = if (isPersian) "🧪 تجهیزات و متریال‌های بروز مصرفی:" else "🧪 Modern Materials & Equipment:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val displayMaterials = if (currentProtocol.modernMaterials.isNotEmpty()) currentProtocol.modernMaterials else listOf(
                        "• بوندینگ نسل جدید Universal / Self-Etch",
                        "• فایل‌های حرارتی و روتاری تخصصی Gold / Blue",
                        "• بیوسرامیک، MTA و سیلرهای برپایه کلسیم سیلیکات",
                        "• نایت گارد و تجهیزات ایزولاسیون رابردام"
                    )
                    displayMaterials.forEach { mat ->
                        Text(text = if (mat.startsWith("•") || mat.startsWith("-")) mat else "• $mat", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "🛡️ احتیاط‌ها و الزامات:" else "🛡️ Precautions & Requirements:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.precautions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "💊 داروهای تجویزی (ADA 2024 / AHA 2021):" else "💊 Prescribed Medications (ADA 2024 / AHA 2021):",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.prescriptions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "⚕️ عوارض بالینی و فوریت‌ها:" else "⚕️ Clinical Complications & Emergencies:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.complications.forEach { (comp, sol) ->
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(text = "▸ $comp", fontWeight = FontWeight.Bold, fontSize = bodySize, color = Color(0xFFB71C1C))
                            if (sol.isNotBlank()) {
                                Text(text = sol, fontSize = bodySize)
                            }
                        }
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "📝 سفارشات ترخیص و مراقبت:" else "📝 Post-Session Instructions:",
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = MaterialTheme.colorScheme.primary
                    )
                    currentProtocol.postSessionInstructions.forEach { p ->
                        Text(text = "• $p", fontSize = bodySize)
                    }
                    HorizontalDivider()

                    Text(
                        text = if (isPersian) "منابع: ${currentProtocol.sources.joinToString("، ")}" else "Sources: ${currentProtocol.sources.joinToString(", ")}",
                        fontSize = (fontSizePt - 2).coerceAtLeast(8).sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "بستن" else "Close")
            }
        }
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayLabInvoiceDialog(
    expense: com.example.data.ClinicExpense,
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val paymentMethods = if (isPersian) listOf("نقدی", "کارتخوان", "کارت به کارت", "انتخاب چک دریافتی بیمار") else listOf("Cash", "POS Terminal", "Card Transfer", "Patient Received Cheque")
    var selectedMethod by remember { mutableStateOf(paymentMethods[1]) } // Default to کارتخوان
    val currency = viewModel.label("currency")
    
    var amountText by remember { mutableStateOf(expense.amount.toString()) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) } // Default to today
    var showDatePicker by remember { mutableStateOf(false) }

    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val untransferredCheques = remember(allPayments) {
        allPayments.filter { it.type == "CHEQUE" && it.chequeStatus != "TRANSFERRED" }
    }
    var selectedCheque by remember { mutableStateOf<com.example.data.Payment?>(null) }
    var chequeDropdownExpanded by remember { mutableStateOf(false) }

    val useCheque = selectedMethod == "انتخاب چک دریافتی بیمار" || selectedMethod == "Patient Received Cheque"

    // تقویم شمسی برای تاریخ پرداخت فاکتور (جایگزین DatePicker میلادی Material3)
    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialTimestamp = selectedDateMillis,
            isPersian = isPersian,
            onDateSelected = { jy, jm, jd ->
                selectedDateMillis = com.example.util.PersianCalendarHelper.jalaliToTimestamp(jy, jm, jd)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPersian) "پرداخت فاکتور" else "Pay Invoice", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                    label = { Text(if (isPersian) "مبلغ پرداخت ($currency)" else "Payment Amount ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Text(if (isPersian) "روش پرداخت:" else "Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    paymentMethods.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method, fontSize = 11.sp) }
                        )
                    }
                }
                
                if (useCheque) {
                    Text(if (isPersian) "انتخاب چک دریافتی بیمار:" else "Select Patient Cheque:", fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = chequeDropdownExpanded,
                        onExpandedChange = { chequeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCheque?.let { c -> "${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)}" } ?: (if (isPersian) "انتخاب چک..." else "Select cheque..."),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chequeDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = chequeDropdownExpanded,
                            onDismissRequest = { chequeDropdownExpanded = false }
                        ) {
                            untransferredCheques.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.chequeBank ?: ""} ${c.chequeNumber ?: ""} - ${formatAmount(c.amount)} $currency", fontSize = 12.sp) },
                                    onClick = {
                                        selectedCheque = c
                                        chequeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(if (isPersian) "تاریخ پرداخت:" else "Payment Date:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = viewModel.formatDate(selectedDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.toLongOrNull() ?: 0L
                    if (useCheque && selectedCheque != null) {
                        val chq = selectedCheque!!
                        viewModel.transferChequeToExpense(chq, recipientName = expense.subAccountName, category = expense.category)
                        viewModel.insertExpensePayment(
                            com.example.data.ExpensePayment(
                                clinicExpenseId = expense.id,
                                amount = chq.amount,
                                method = "CHEQUE",
                                chequeNumber = chq.chequeNumber,
                                chequeBank = chq.chequeBank,
                                date = selectedDateMillis,
                                note = "چک واگذار شده بیمار (${chq.chequeBank ?: ""})"
                            )
                        )
                    } else if (parsedAmount > 0) {
                        val mappedMethod = when (selectedMethod) {
                            "نقدی", "Cash" -> "CASH"
                            "کارتخوان", "POS Terminal", "پوز" -> "POS"
                            "کارت به کارت", "Card Transfer" -> "CARD_TRANSFER"
                            else -> "CASH"
                        }
                        viewModel.insertExpensePayment(
                            com.example.data.ExpensePayment(
                                clinicExpenseId = expense.id,
                                amount = parsedAmount,
                                method = mappedMethod,
                                date = selectedDateMillis,
                                note = "پرداخت فاکتور"
                            )
                        )
                    }
                    onDismiss()
                },
                enabled = (!useCheque && (amountText.toLongOrNull() ?: 0L) > 0) || (useCheque && selectedCheque != null)
            ) {
                Text(if (isPersian) "ثبت پرداخت" else "Save Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isPersian) "انصراف" else "Cancel") }
        }
    )
}

@Composable
fun OfficialLabInvoiceDialog(
    order: com.example.data.LabOrder,
    viewModel: DentalViewModel,
    isPersian: Boolean = true,
    onDismiss: () -> Unit
) {
    val currency = viewModel.label("currency")
    var printStatusText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val neonPalette = LocalNeonPalette.current
    val accentColorInt = neonPalette.primary.toArgb()

    val count = parseTeethCount(order.toothNumber)
    val unitPrice = if (count > 0) order.cost / count else order.cost

    val services by viewModel.labServices.collectAsStateWithLifecycle()
    val matchedService = services.find {
        it.serviceName.equals(order.prosthesisType, ignoreCase = true) &&
        it.labName.equals(order.labName, ignoreCase = true)
    }
    val frameDays = matchedService?.frameDeliveryDays ?: 3
    val finalDays = matchedService?.finalDeliveryDays ?: 7

    val orderDate = order.orderDate
    val frameTime = orderDate + (frameDays * 24L * 60L * 60L * 1000L)
    val finalTime = orderDate + (finalDays * 24L * 60L * 60L * 1000L)

    val orderDateStr = viewModel.formatDateFullVerbal(orderDate)
    val frameDateStr = viewModel.formatDateFullVerbal(frameTime)
    val finalDateStr = viewModel.formatDateFullVerbal(finalTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (isPersian) "صورت‌حساب و فاکتور سفارش لابراتوار" else "Lab Order Invoice",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Printable Card Layout
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isPersian) "مطب / کلینیک تخصصی دندانپزشکی" else "Dental Clinic Invoice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "شماره فاکتور: LAB-${order.id + 1000}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isPersian) "فاکتور سفارش" else "INVOICE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        // Parties info
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "لابراتوار طرف قرارداد:" else "Laboratory:",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Text(order.labName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "نام بیمار / پرونده:" else "Patient:",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Text(order.patientName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "نوع پروتز / خدمت:" else "Prosthesis:", fontSize = 11.sp, color = Color.Gray)
                                    Text(order.prosthesisType, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "شماره دندان(ها):" else "Tooth No:", fontSize = 11.sp, color = Color.Gray)
                                    Text(if (order.toothNumber.isNotBlank()) order.toothNumber else "-", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                if (!order.shadeBrand.isNullOrBlank() || !order.shadeColor.isNullOrBlank()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (isPersian) "رنگ / شید دندان:" else "Color / Shade:", fontSize = 11.sp, color = Color.Gray)
                                        val sInfo = listOfNotNull(order.shadeBrand?.takeIf { it.isNotBlank() }, order.shadeColor?.takeIf { it.isNotBlank() }).joinToString(" - ")
                                        Text(sInfo, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تعداد واحد سفارش:" else "Units Count:", fontSize = 11.sp, color = Color.Gray)
                                    Text("$count واحد", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "قیمت (هر واحد):" else "Unit Price:", fontSize = 11.sp, color = Color.Gray)
                                    Text("${formatAmount(unitPrice)} $currency", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تاریخ ثبت سفارش:" else "Order Date:", fontSize = 11.sp, color = Color.Gray)
                                    Text(orderDateStr, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تاریخ تحویل فریم/تایید:" else "Frame Date:", fontSize = 11.sp, color = Color.Gray)
                                    Text(frameDateStr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "تاریخ تحویل نهایی:" else "Final Date:", fontSize = 11.sp, color = Color.Gray)
                                    Text(finalDateStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DebtColor)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isPersian) "وضعیت ساخت:" else "Status:", fontSize = 11.sp, color = Color.Gray)
                                    Text(viewModel.label("status_${order.status.lowercase()}"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        HorizontalDivider()

                        // Total Price Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isPersian) "جمع کل فاکتور سفارش:" else "Total Invoice Cost:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${formatAmount(order.cost)} $currency",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "($count واحد × ${formatAmount(unitPrice)} $currency)",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Seal & Signature Line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isPersian) "محل مهر و امضای لابراتوار" else "Lab Signature",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Text(
                                if (isPersian) "محل امضای دندانپزشک / مسئول کلینیک" else "Dentist Signature",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                if (printStatusText != null) {
                    Text(
                        text = printStatusText!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val file = ReportGenerators.labOrderInvoice(
                                context = context,
                                clinicName = settings.clinicName,
                                doctorName = settings.doctorName,
                                accent = accentColorInt,
                                currency = currency,
                                order = order,
                                dateFmt = { viewModel.formatDate(it) },
                                amountFmt = { formatAmount(it) }
                            )
                            printPdfFile(context, file, "فاکتور لابراتوار")
                            printStatusText = if (isPersian) "📄 فاکتور برای چاپ ارسال شد" else "Invoice sent to print"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "چاپ فاکتور" else "Print")
                    }

                    OutlinedButton(
                        onClick = {
                            val imageFile = com.example.reports.buildOfficialLabInvoiceImage(
                                context = context,
                                clinicName = settings.clinicName,
                                doctorName = settings.doctorName,
                                labName = order.labName,
                                patientName = order.patientName,
                                invoiceNo = "LAB-${order.id + 1000}",
                                prosthesisType = order.prosthesisType,
                                toothNumber = order.toothNumber.ifBlank { "-" },
                                teethCount = count,
                                unitPriceText = "${formatAmount(unitPrice)} $currency",
                                totalCostText = "${formatAmount(order.cost)} $currency",
                                orderDateText = orderDateStr,
                                frameDateText = frameDateStr,
                                finalDateText = finalDateStr,
                                paymentMethodText = if (isPersian) "ثبت در حساب/بدهی لابراتوار" else "Recorded in Lab Account",
                                themeSpec = com.example.reports.ExportThemeSpec.fromPalette(neonPalette)
                            )
                            com.example.reports.shareImageToWhatsApp(context, imageFile)
                            printStatusText = if (isPersian) "📸 تصویر فاکتور رسمی آماده اشتراک‌گذاری شد" else "Official invoice image ready to share"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "اشتراک/واتساپ" else "Share")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            }
        },
        confirmButton = {}
    )
}




fun calculateRxQuantity(title: String, dosage: String, timing: String, dur: Int): String {
    val cleanTitle = title.trim()
    val cleanDosage = dosage.trim()
    val cleanTiming = timing.trim()
    
    val unit = when {
        cleanTitle.contains("کپسول") -> "Cap"
        cleanTitle.contains("قرص") -> "Tab"
        cleanTitle.contains("دهان‌شویه") || cleanTitle.contains("سوسپانسیون") || cleanTitle.contains("شربت") || cleanDosage.contains("سی‌سی") || cleanDosage.contains("سی سی") -> "Bottle"
        cleanTitle.contains("قطره") || cleanDosage.contains("قطره") -> "Bottle"
        cleanTitle.contains("آمپول") -> "Amp"
        cleanTitle.contains("پماد") || cleanTitle.contains("ژل") -> "Tube"
        else -> "عدد"
    }

    if (unit == "Bottle" || unit == "Tube") {
        return "1 $unit"
    }

    var timesPerDay = 0
    val textToSearch = "$cleanTiming $cleanDosage"
    
    if (textToSearch.contains("هر ۶ ساعت") || textToSearch.contains("هر 6 ساعت")) timesPerDay = 4
    else if (textToSearch.contains("هر ۸ ساعت") || textToSearch.contains("هر 8 ساعت")) timesPerDay = 3
    else if (textToSearch.contains("هر ۱۲ ساعت") || textToSearch.contains("هر 12 ساعت")) timesPerDay = 2
    else if (textToSearch.contains("هر ۲۴ ساعت") || textToSearch.contains("هر 24 ساعت") || textToSearch.contains("روزانه") || textToSearch.contains("روزی ۱") || textToSearch.contains("روزی 1")) timesPerDay = 1
    else {
        val m = java.util.regex.Pattern.compile("(\\d+)").matcher(cleanTiming)
        if (m.find()) {
            val num = m.group(1).toIntOrNull() ?: 0
            if (num > 0 && cleanTiming.contains("ساعت")) timesPerDay = 24 / num
            else if (num > 0) timesPerDay = num
        }
    }

    if (timesPerDay == 0) timesPerDay = 2

    var doseMultiplier = 1.0
    if (cleanDosage.contains("۱/۲") || cleanDosage.contains("1/2") || cleanDosage.contains("نصف")) {
        doseMultiplier = 0.5
    } else if (cleanDosage.contains("۲") || cleanDosage.contains("2")) {
        doseMultiplier = 2.0
    }

    val days = if (dur > 0) dur else 5
    val totalUnits = kotlin.math.ceil(timesPerDay * doseMultiplier * days).toInt()
    return "$totalUnits $unit"
}

fun parseRxToColumns(rawText: String, defaultDurationDays: Int): com.example.reports.RxColumnRowData {
    if (rawText.isBlank()) return com.example.reports.RxColumnRowData("", "", "", "", "")
    val clean = rawText.removePrefix("💊").removePrefix("🧴").removePrefix("➕").trim()
    
    if (clean.contains(" - ") || clean.contains(";;;")) {
        val sep = if (clean.contains(" - ")) " - " else ";;;"
        val p = clean.split(sep)
        val title = p.getOrNull(0)?.trim() ?: ""
        val dosage = p.getOrNull(1)?.trim() ?: ""
        val timing = p.getOrNull(2)?.trim() ?: ""
        val instructions = p.getOrNull(3)?.trim() ?: ""
        val durStr = p.getOrNull(4)?.trim() ?: ""
        val dur = durStr.toIntOrNull() ?: defaultDurationDays
        
        val quantity = calculateRxQuantity(title, dosage, timing, dur)
        
        return com.example.reports.RxColumnRowData(
            title = title,
            quantity = quantity,
            dosage = dosage,
            timing = timing,
            instructions = instructions.ifBlank { "-" }
        )
    }

    val parts = clean.split("-", "—", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
    
    val title = parts.getOrNull(0) ?: clean
    var timing = ""
    var dosage = ""
    var quantity = ""
    val notesList = mutableListOf<String>()
    
    for (i in 1 until parts.size) {
        val p = parts[i]
        when {
            p.contains("هر ") || p.contains("روزی") || p.contains("ساعت") || p.contains("صبح") || p.contains("شب") || p.contains("ظهر") -> {
                if (timing.isEmpty()) timing = p else notesList.add(p)
            }
            p.contains("تعداد") || p.contains("مقدار:") -> {
                quantity = p.replace("تعداد:", "").replace("مقدار:", "").trim()
            }
            p.contains("عدد") || p.contains("بار") || p.contains("قطره") || p.contains("کپسول") || p.contains("قاشق") -> {
                if (dosage.isEmpty()) dosage = p else notesList.add(p)
            }
            else -> {
                notesList.add(p)
            }
        }
    }
    
    if (quantity.isEmpty()) {
        quantity = calculateRxQuantity(title, dosage, timing, defaultDurationDays)
    }
    
    return com.example.reports.RxColumnRowData(
        title = title,
        quantity = quantity,
        dosage = dosage.ifBlank { "۱ عدد" },
        timing = timing.ifBlank { "طبق دستور" },
        instructions = notesList.joinToString("، ").ifBlank { "-" }
    )
}

fun buildStandardRxShareText(
    patientName: String,
    patientNationalIdOrPhone: String,
    dateStr: String,
    durationDays: Int,
    rows: List<com.example.reports.RxColumnRowData>,
    notes: String,
    clinicName: String,
    doctorName: String,
    gender: String = "مرد"
): String {
    val formattedPatient = honorificPatientName(patientName, gender)
    return buildString {
        append("📋 نسخه دارویی الکترونیک\n")
        append("----------------------------------------\n")
        append("👤 نام بیمار: $formattedPatient\n")
        if (patientNationalIdOrPhone.isNotBlank()) append("🆔 شماره پرونده: $patientNationalIdOrPhone\n")
        append("📅 تاریخ: $dateStr | طول درمان: $durationDays روز\n")
        append("----------------------------------------\n")
        append("Rp / لیست داروها:\n\n")
        
        rows.forEachIndexed { idx, r ->
            append("${idx + 1}. عنوان: ${r.title}\n")
            append("   ▫ تعداد: ${r.quantity} | مقادیر مصرف: ${r.dosage}\n")
            append("   ▫ زمان مصرف: ${r.timing}\n")
            if (r.instructions.isNotBlank() && r.instructions != "-") append("   ▫ دستورات متفرقه: ${r.instructions}\n")
            append("\n")
        }
        
        if (notes.isNotBlank()) {
            append("----------------------------------------\n")
            append("⚠️ دستورات متفرقه و نکات مهم:\n$notes\n")
        }
        append("----------------------------------------\n")
        append("🏥 $clinicName ${if(doctorName.isNotBlank()) "— دکتر $doctorName" else ""}\n")
    }
}

@Composable
fun PrescriptionLiveTablePreview(
    rows: List<com.example.reports.RxColumnRowData>,
    notes: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("📋 پیش‌نمایش نسخه قابل ارسال (قالب ۵ ستونه)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("عنوان (نام و دوز)", modifier = Modifier.weight(2.0f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("تعداد", modifier = Modifier.weight(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("مقادیر مصرف", modifier = Modifier.weight(1.0f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("زمان مصرف", modifier = Modifier.weight(1.1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("دستورات متفرقه", modifier = Modifier.weight(1.1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            
            // Data Rows
            rows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(r.title, modifier = Modifier.weight(2.0f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(r.quantity, modifier = Modifier.weight(0.7f), fontSize = 10.sp)
                    Text(r.dosage, modifier = Modifier.weight(1.0f), fontSize = 10.sp)
                    Text(r.timing, modifier = Modifier.weight(1.1f), fontSize = 10.sp)
                    Text(r.instructions, modifier = Modifier.weight(1.1f), fontSize = 10.sp, color = Color.Gray)
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
            }
            
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("دستورات متفرقه و نکات مهم:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notes,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

fun formatRxItemWithQuantity(text: String, durationDays: Int): String {
    if (text.contains(";;;")) {
        val cols = parseRxToColumns(text, durationDays)
        return "${cols.title} - ${cols.dosage} - ${cols.timing}" + (if(cols.quantity != "۱") " - مقدار: ${cols.quantity}" else "") + (if(cols.instructions != "-") " - ${cols.instructions}" else "")
    }
    if (text.isBlank()) return ""
    var modifiedText = text
    val persianToEnglish = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )
    val normalizedText = text.map { persianToEnglish[it] ?: it }.joinToString("")
    
    val patternHours = java.util.regex.Pattern.compile("هر\\s+(\\d+)(?:\\s*-\\s*\\d+)?\\s+ساعت")
    val matcherHours = patternHours.matcher(normalizedText)
    
    var dailyCount = 0
    if (matcherHours.find()) {
        val hours = matcherHours.group(1).toIntOrNull() ?: 0
        if (hours > 0) dailyCount = 24 / hours
    } else {
        val patternDays = java.util.regex.Pattern.compile("روزی\\s+(\\d+)\\s+بار")
        val matcherDays = patternDays.matcher(normalizedText)
        if (matcherDays.find()) {
            dailyCount = matcherDays.group(1).toIntOrNull() ?: 0
        }
    }
    
    if (dailyCount > 0 && durationDays > 0) {
        val totalQuantity = dailyCount * durationDays
        modifiedText = "$text - مقدار: $totalQuantity عدد"
    } else if (durationDays > 0) {
        modifiedText = "$text - برای $durationDays روز"
    }
    return modifiedText
}



fun openWhatsAppChatWithMessageRx(context: android.content.Context, rawPhone: String, message: String) {
    val normalized = formatIranianPhoneNumber(rawPhone)
    if (normalized == "98" || normalized.isBlank()) {
        android.widget.Toast.makeText(context, "شماره تلفن معتبر نیست", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val encodedMsg = android.net.Uri.encode(message)
    val uri = android.net.Uri.parse("https://wa.me/$normalized?text=$encodedMsg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        setPackage("com.whatsapp")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}

@Composable
fun ProfileCompletionDialog(
    viewModel: DentalViewModel,
    onComplete: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var clinicName by remember { mutableStateOf(if (settings.clinicName.isBlank()) "" else settings.clinicName) }
    var doctorName by remember { mutableStateOf(if (settings.doctorName == "دکتر") "" else settings.doctorName) }
    var staffNames by remember { mutableStateOf(settings.staffNames) }
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { /* Require completion */ },
        title = {
            Text(
                text = if (isPersian) "تکمیل مشخصات کلینیک" else "Complete Clinic Profile",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isPersian) "برای همگام‌سازی صحیح ابری و صدور گزارش‌ها، لطفاً نام کلینیک و نام پزشک را وارد فرمایید:" else "To ensure accurate cloud sync and reports, please enter your clinic name and doctor name:",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = clinicName,
                    onValueChange = { clinicName = it },
                    label = { Text(if (isPersian) "نام کلینیک / مطب" else "Clinic Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = { Text(if (isPersian) "نام پزشک معالج" else "Doctor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = staffNames,
                    onValueChange = { staffNames = it },
                    label = { Text(if (isPersian) "نام پرسنل / منشی" else "Staff Names") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalClinic = if (clinicName.isBlank()) "کلینیک دندانپزشکی" else clinicName.trim()
                    val finalDoctor = if (doctorName.isBlank()) "دکتر معالج" else doctorName.trim()
                    viewModel.updateSettings(
                        clinicName = finalClinic,
                        doctorName = finalDoctor,
                        staffNames = staffNames.trim(),
                        showSummary = settings.showSummaryCard,
                        showQuick = settings.showQuickActions,
                        showCharts = settings.showCharts
                    )
                    onComplete()
                }
            ) {
                Text(if (isPersian) "ثبت و ادامه" else "Save & Continue")
            }
        }
    )
}

