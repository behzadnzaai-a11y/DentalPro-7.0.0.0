package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.feature.auth.UserRole
import com.example.ui.DentalViewModel

@Composable
fun LockScreen(
    viewModel: DentalViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Which PIN to check against — the doctor's own PIN, or the shared
    // assistant/secretary PIN the doctor configured. This was previously
    // hardcoded to UserRole.DOCTOR everywhere below, meaning an assistant
    // could never unlock with their own PIN through this screen at all.
    val assistantPinConfigured = viewModel.hasPinConfigured(UserRole.ASSISTANT)
    var selectedRole by remember { mutableStateOf(UserRole.DOCTOR) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Locked",
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isPersian) "ورود امن به نرم‌افزار مطب" else "Secure Clinic Lock Screen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPersian) "پین خود را وارد کرده یا از احراز هویت بیومتریک استفاده کنید" else "Enter your PIN or use biometric authentication",
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Role selector — only shown when an assistant PIN actually exists;
            // otherwise everyone unlocking this device is the doctor and the
            // extra tap would be pure friction.
            if (assistantPinConfigured) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RoleChip(
                        selected = selectedRole == UserRole.DOCTOR,
                        icon = Icons.Default.MedicalServices,
                        label = if (isPersian) "پزشک" else "Doctor",
                        onClick = { selectedRole = UserRole.DOCTOR; pinError = false }
                    )
                    RoleChip(
                        selected = selectedRole == UserRole.ASSISTANT,
                        icon = Icons.Default.SupportAgent,
                        label = if (isPersian) "دستیار / منشی" else "Assistant",
                        onClick = { selectedRole = UserRole.ASSISTANT; pinError = false }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // PIN Input box
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it.filter(Char::isDigit).take(12)
                    pinError = false
                },
                label = { Text(if (isPersian) "پین ورود" else "PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = pinError,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(220.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (viewModel.unlockAppWithPin(pinInput, selectedRole)) {
                        pinInput = ""
                        onUnlocked()
                    } else {
                        pinError = true
                    }
                },
                enabled = pinInput.length >= 4,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isPersian) "ورود با پین" else "Unlock with PIN")
            }

            if (pinError) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isPersian) "رمز اشتباه است. دوباره تلاش کنید" else "Incorrect PIN code. Try again",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            // Biometric unlock is device-level, not per-person, so it only
            // makes sense for the doctor's own device/identity.
            if (selectedRole == UserRole.DOCTOR) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle(if (isPersian) "ورود به نرم‌افزار مطب" else "Clinic App Login")
                                .setSubtitle(if (isPersian) "احراز هویت بیومتریک" else "Biometric Scan")
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()

                            val biometricPrompt = BiometricPrompt(activity, executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        viewModel.currentUserRole.value = UserRole.DOCTOR
                                        viewModel.isAppUnlocked.value = true
                                        onUnlocked()
                                    }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                        pinError = true
                                    }
                                })

                            biometricPrompt.authenticate(promptInfo)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPersian) "ورود با اثر انگشت / تشخیص چهره" else "Biometric Unlock", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RoleChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) cs.primary else Color.Transparent)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (selected) cs.onPrimary else cs.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant
        )
    }
}
