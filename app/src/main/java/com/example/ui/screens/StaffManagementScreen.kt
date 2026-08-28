package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.data.StaffMember
import com.example.ui.DentalViewModel

// ==========================================
// STAFF MANAGEMENT — real per-person records (name, role, phone, photo) backed
// by StaffMember/Room, replacing the old free-text "Staff & Assistants" field
// in ClinicSettingsScreen which never actually persisted structured data.
// ==========================================
@Composable
fun StaffManagementScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    val staffList by viewModel.allStaffMembers.collectAsStateWithLifecycle()
    val isMediaUploading by viewModel.isMediaUploading.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var staffToEdit by remember { mutableStateOf<StaffMember?>(null) }
    var staffToDelete by remember { mutableStateOf<StaffMember?>(null) }

    if (staffToDelete != null) {
        val target = staffToDelete!!
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = {
                Text(
                    text = if (isPersian) "تایید حذف پرسنل" else "Confirm Staff Deletion",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    if (isPersian) "آیا از حذف «${target.fullName}» از فهرست پرسنل مطب اطمینان دارید؟"
                    else "Are you sure you want to remove ${target.fullName} from the staff list?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStaffMember(target)
                        staffToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isPersian) "حذف قطعی" else "Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) {
                    Text(if (isPersian) "انصراف" else "Cancel")
                }
            }
        )
    }

    if (showAddDialog || staffToEdit != null) {
        StaffEditDialog(
            viewModel = viewModel,
            isPersian = isPersian,
            existing = staffToEdit,
            onDismiss = {
                showAddDialog = false
                staffToEdit = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = if (isPersian) "پرسنل مطب" else "Staff Management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${staffList.size} ${if (isPersian) "نفر ثبت‌شده" else "staff members"}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = if (isPersian) "افزودن پرسنل" else "Add staff member", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (isMediaUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (staffList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (isPersian) "هنوز هیچ پرسنلی ثبت نشده است" else "No staff members registered yet",
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (isPersian) "پزشک و دستیاران مطب را اضافه کنید تا بتوانید برای هرکدام پین ورود جداگانه تعیین کنید."
                                else "Add the doctor and assistants so each one can have their own login PIN.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                items(staffList, key = { it.id }) { staff ->
                    StaffMemberRow(
                        staff = staff,
                        isPersian = isPersian,
                        isActiveDoctor = settings.activeDoctorSyncId.isNotBlank() && staff.syncId == settings.activeDoctorSyncId,
                        isActiveAssistant = settings.activeAssistantSyncId.isNotBlank() && staff.syncId == settings.activeAssistantSyncId,
                        onSetActiveDoctor = { viewModel.setActiveDoctor(staff) },
                        onSetActiveAssistant = { viewModel.setActiveAssistant(staff) },
                        onClearActiveDoctor = { viewModel.clearActiveDoctor() },
                        onClearActiveAssistant = { viewModel.clearActiveAssistant() },
                        onEdit = { staffToEdit = staff },
                        onDelete = { staffToDelete = staff }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffMemberRow(
    staff: StaffMember,
    isPersian: Boolean,
    isActiveDoctor: Boolean,
    isActiveAssistant: Boolean,
    onSetActiveDoctor: () -> Unit,
    onSetActiveAssistant: () -> Unit,
    onClearActiveDoctor: () -> Unit,
    onClearActiveAssistant: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDoctor = staff.role == "DOCTOR"
    val isActive = if (isDoctor) isActiveDoctor else isActiveAssistant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!staff.photoFileId.isNullOrBlank()) {
                        SmartImage(
                            fileId = staff.photoFileId,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            if (isDoctor) Icons.Default.MedicalServices else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = staff.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (isDoctor) (if (isPersian) "پزشک فعال" else "Active Doctor") else (if (isPersian) "دستیار فعال" else "Active Assistant"),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Text(
                        text = when {
                            isDoctor -> if (isPersian) "پزشک" else "Doctor"
                            else -> if (isPersian) "دستیار / منشی" else "Assistant / Reception"
                        },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    if (staff.phone.isNotBlank()) {
                        Text(text = staff.phone, fontSize = 11.sp, color = Color.Gray)
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = if (isPersian) "ویرایش" else "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = if (isPersian) "حذف" else "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            // تعیین پزشک فعال / دستیار فعال — یک پزشک و یک دستیار از لیست پرسنل
            Button(
                onClick = {
                    when {
                        isActive && isDoctor -> onClearActiveDoctor()
                        isActive && !isDoctor -> onClearActiveAssistant()
                        !isActive && isDoctor -> onSetActiveDoctor()
                        else -> onSetActiveAssistant()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(
                    if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isDoctor && isActive -> if (isPersian) "پزشک فعال — لمس برای حذف برچسب" else "Active doctor — tap to unmark"
                        isDoctor -> if (isPersian) "تعیین به‌عنوان پزشک فعال" else "Set as active doctor"
                        isActive -> if (isPersian) "دستیار فعال — لمس برای حذف برچسب" else "Active assistant — tap to unmark"
                        else -> if (isPersian) "تعیین به‌عنوان دستیار فعال" else "Set as active assistant"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StaffEditDialog(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    existing: StaffMember?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf(existing?.fullName ?: "") }
    var role by remember { mutableStateOf(existing?.role ?: "ASSISTANT") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var pendingPhotoLocalPath by remember { mutableStateOf<String?>(null) }

    val staffAvatarEditLauncher = rememberPhotoEditLauncher(PhotoAssetKind.STAFF_AVATAR) { localPath ->
        pendingPhotoLocalPath = localPath
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { staffAvatarEditLauncher(it) }
    }
    val launchCamera = rememberEditableCameraLauncher(context) { uri ->
        staffAvatarEditLauncher(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) {
                    if (isPersian) "افزودن پرسنل جدید" else "Add Staff Member"
                } else {
                    if (isPersian) "ویرایش پرسنل" else "Edit Staff Member"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val previewFileId = existing?.photoFileId
                    when {
                        pendingPhotoLocalPath != null -> {
                            // Shown directly from the freshly picked local file — not yet uploaded.
                            val bmp = remember(pendingPhotoLocalPath) {
                                android.graphics.BitmapFactory.decodeFile(pendingPhotoLocalPath)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        !previewFileId.isNullOrBlank() -> {
                            SmartImage(fileId = previewFileId, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        else -> {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text(if (isPersian) "گالری" else "Gallery", fontSize = 11.sp)
                    }
                    TextButton(onClick = { launchCamera() }) {
                        Text(if (isPersian) "دوربین" else "Camera", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(if (isPersian) "نام و نام خانوادگی" else "Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = role == "DOCTOR",
                        onClick = { role = "DOCTOR" },
                        label = { Text(if (isPersian) "پزشک" else "Doctor") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = role == "ASSISTANT",
                        onClick = { role = "ASSISTANT" },
                        label = { Text(if (isPersian) "دستیار" else "Assistant") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isPersian) "شماره تماس" else "Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isNotBlank()) {
                        if (existing == null) {
                            viewModel.insertStaffMember(
                                StaffMember(fullName = fullName.trim(), role = role, phone = phone.trim())
                            )
                            // Note: the photo for a brand-new staff member is uploaded on the
                            // next edit once the record (and its id) exists — same two-step
                            // pattern the app already uses elsewhere for freshly created records.
                        } else {
                            val updated = existing.copy(fullName = fullName.trim(), role = role, phone = phone.trim())
                            viewModel.updateStaffMember(updated)
                            val path = pendingPhotoLocalPath
                            if (path != null) {
                                viewModel.updateStaffPhoto(updated, path)
                            }
                        }
                        onDismiss()
                    }
                },
                enabled = fullName.isNotBlank()
            ) {
                Text(if (isPersian) "ذخیره" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}
