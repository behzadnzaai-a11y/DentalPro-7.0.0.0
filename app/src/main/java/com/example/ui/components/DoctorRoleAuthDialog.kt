package com.example.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.feature.auth.UserRole
import com.example.security.SecurityUtils
import com.example.ui.DentalViewModel
import com.example.util.AppLocalization

@Composable
fun DoctorRoleAuthDialog(
    viewModel: DentalViewModel,
    targetRole: UserRole = UserRole.DOCTOR,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }

    // Check if PIN is already configured for this role
    val isPinConfigured = viewModel.hasPinConfigured(targetRole)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .testTag("doctor_auth_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPinConfigured) Icons.Default.Lock else Icons.Default.Security,
                        contentDescription = "Role Auth",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Title
                Text(
                    text = if (isPinConfigured) {
                        if (isPersian) "ورود به عنوان ${targetRole.titleFa}" else "Enter PIN for ${targetRole.titleEn}"
                    } else {
                        if (isPersian) "تعریف رمز عبور برای ${targetRole.titleFa}" else "Set PIN for ${targetRole.titleEn}"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Subtitle / Description
                Text(
                    text = if (isPinConfigured) {
                        if (isPersian) "جهت احراز هویت، رمز عبور ۴ رقمی این نقش را وارد کنید." else "Enter the 4-digit PIN for this role."
                    } else {
                        if (isPersian) "یک رمز عبور ۴ تا ۱۲ رقمی برای این نقش تنظیم نمایید." else "Set a 4 to 12 digit PIN for this role."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // Pin Input Field
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        enteredPin = it.filter(Char::isDigit).take(12)
                        errorMessage = null
                    },
                    label = {
                        Text(if (isPinConfigured) "PIN" else if (isPersian) "رمز عبور جدید" else "New PIN")
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (isPinConfigured) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isPinConfigured) {
                                validateAndSubmit(
                                    enteredPin = enteredPin,
                                    viewModel = viewModel,
                                    targetRole = targetRole,
                                    selectedLanguage = selectedLanguage,
                                    onSuccess = onSuccess,
                                    onError = { msg, attempts ->
                                        errorMessage = msg
                                        failedAttempts = attempts
                                    }
                                )
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN Visibility"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doctor_pin_input")
                )

                // If configuring new PIN: Confirm field
                if (!isPinConfigured) {
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter(Char::isDigit).take(12)
                            errorMessage = null
                        },
                        label = {
                            Text(if (isPersian) "تکرار رمز عبور" else "Confirm PIN")
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doctor_confirm_pin_input")
                    )
                }

                // Error Message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_doctor_auth_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(AppLocalization.get("cancel", selectedLanguage))
                    }

                    Button(
                        onClick = {
                            if (isPinConfigured) {
                                validateAndSubmit(
                                    enteredPin = enteredPin,
                                    viewModel = viewModel,
                                    targetRole = targetRole,
                                    selectedLanguage = selectedLanguage,
                                    onSuccess = onSuccess,
                                    onError = { msg, attempts ->
                                        errorMessage = msg
                                        failedAttempts = attempts
                                    }
                                )
                            } else {
                                // Set new PIN
                                if (enteredPin.length < 4) {
                                    errorMessage = if (isPersian) "رمز عبور باید حداقل ۴ رقم باشد." else "PIN must be at least 4 digits."
                                    return@Button
                                }
                                if (enteredPin != confirmPin) {
                                    errorMessage = if (isPersian) "رمز عبور و تکرار آن یکسان نیستند." else "PINs do not match."
                                    return@Button
                                }
                                viewModel.configureRolePin(targetRole, enteredPin)
                                viewModel.requestRoleSwitch(targetRole, enteredPin)
                                onSuccess()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_doctor_auth_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isPinConfigured) AppLocalization.get("confirm", selectedLanguage) else AppLocalization.get("save", selectedLanguage),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun validateAndSubmit(
    enteredPin: String,
    viewModel: DentalViewModel,
    targetRole: UserRole,
    selectedLanguage: String,
    onSuccess: () -> Unit,
    onError: (String, Int) -> Unit
) {
    if (enteredPin.isBlank()) {
        onError(if (selectedLanguage == "fa") "لطفاً رمز عبور را وارد کنید." else "Please enter PIN.", 1)
        return
    }

    val isValid = viewModel.verifyRolePin(targetRole, enteredPin)
    if (isValid) {
        viewModel.requestRoleSwitch(targetRole, enteredPin)
        onSuccess()
    } else {
        onError(AppLocalization.get("pin_incorrect", selectedLanguage), 1)
    }
}
