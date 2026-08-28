package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DentalViewModel
import com.example.feature.auth.UserRole
import com.example.util.Supabase
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject

// ---------------------------------------------------------------------
// Simple client-side validation helpers
// ---------------------------------------------------------------------
private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private fun normalizeIranPhone(raw: String): String {
    var p = raw.trim().replace(" ", "")
    return when {
        p.startsWith("+") -> p
        p.startsWith("0") -> "+98" + p.substring(1)
        p.startsWith("98") -> "+$p"
        else -> "+98$p"
    }
}
private fun isValidIranPhoneLoose(raw: String): Boolean {
    val normalized = normalizeIranPhone(raw)
    return Regex("^\\+98\\d{10}$").matches(normalized)
}

private fun isValidEmail(v: String) = emailRegex.matches(v.trim())
private fun isValidPassword(v: String) = v.length >= 8
private fun isValidPatientStylePhoneDigits(digits: String) = digits.length == 9 && digits.all(Char::isDigit)
private fun toFullIranPhone(digits: String) = "+989$digits"

@Composable
fun IranMobileNumberField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String = "شماره موبایل (۹ رقم) *",
    isError: Boolean = false,
    errorText: String? = null
) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        androidx.compose.material3.OutlinedTextField(
            value = digits,
            onValueChange = { input -> onDigitsChange(input.filter { it.isDigit() }.take(9)) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Text("+98 9", modifier = Modifier.padding(start = 16.dp), fontWeight = FontWeight.Bold) },
            isError = isError,
            supportingText = {
                if (isError) Text(errorText ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                else Text("${digits.length} / 9 رقم", fontSize = 10.sp, color = Color.Gray)
            }
        )
    }
}

private enum class AuthMode { LOGIN_EMAIL, LOGIN_PHONE_REQUEST, LOGIN_PHONE_VERIFY, REGISTER }
private enum class PostAuthStage { NONE, EMAIL_VERIFICATION_PENDING, GOOGLE_PROFILE_SETUP, LOCK }

@Composable
fun AuthScreen(viewModel: DentalViewModel, onUnlocked: () -> Unit) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN_EMAIL) }

    // Shared fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }
    var acceptedTerms by remember { mutableStateOf(false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var otpCode by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var loadingLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var checkingSession by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    // رفع باگ حلقه‌ی لاگین گوگل: این پرچم تضمین می‌کند که منطق post-auth حداکثر یک‌بار
    // در طول چرخه‌ی عمر این کامپوزیشن اجرا شود. بعد از بازگشت از OAuth Custom Tab
    // ممکن است activity بازسازی شود و LaunchedEffect(Unit) دوباره اجرا گردد — بدون
    // این guard، چند بار هم‌زمان proceedAfterAuth / bootstrap صدا زده می‌شد و منجر به
    // رفت و برگشت بین صفحه‌ی لاگین/داشبورد/پروفایل‌ستاپ گوگل می‌گردید.
    var authFlowFinished by remember { mutableStateOf(false) }

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    // Which post-login screen (if any) should render instead of the auth
    // form. Onboarding is per-clinic (synced via clinic_settings), the lock
    // screen is per-device (local PIN in SharedPreferences).
    var postAuthStage by remember { mutableStateOf(PostAuthStage.NONE) }

    /**
     * نوشتن/به‌روزرسانی ردیف پروفایل کاربر در جدول users — id همان auth.uid است،
     * upsert با on_conflict=id انجام می‌شود و RLS فقط اجازه‌ی نوشتن ردیف خودِ
     * کاربر را می‌دهد.
     */
    suspend fun upsertUserDocumentSafe(
        userId: String,
        name: String,
        doctorName: String,
        email: String,
        phone: String,
        role: String
    ): Boolean {
        val payload = mapOf<String, Any>(
            "id" to userId,
            "name" to name.trim(),
            "doctorName" to doctorName.trim(),
            "email" to email.trim(),
            "phone" to phone.trim(),
            "role" to role.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        return try {
            Supabase.upsertRow(Supabase.TABLE_USERS, userId, payload, conflictColumn = "id")
            true
        } catch (e: Exception) {
            android.util.Log.w("AuthScreen", "Could not save user row in users table: ${e.message}")
            false
        }
    }

    suspend fun proceedAfterAuth(alreadyBootstrapped: Boolean = false) {
        if (authFlowFinished) return  // guard: جلوگیری از اجرای چندباره ناشی از بازسازی اکتیویتی

        // ایزوله‌سازی حساب: قبل از هرکاری، شناسه‌ی حساب سوپابیس را کش می‌کنیم تا bootstrap
        // (pullAll) بداند دقیقاً داده‌ی کدام حساب را باید بکشد — RLS هم همین را تضمین می‌کند.
        try {
            Supabase.resetAccountIdCache()
            Supabase.ensureAccountId()
        } catch (e: Exception) {
            // آفلاین یا خطای شبکه؛ bootstrapAfterLogin خودش این حالت را مدیریت می‌کند
        }

        val acc = try {
            Supabase.currentUser()
        } catch (e: Exception) {
            null
        }

        if (acc != null) {
            // حساب‌های ایمیلیِ تاییدنشده هنوز وارد نمی‌شوند (گوگل/پیامکی همیشه تاییدشده‌اند)
            if (!acc.emailVerified && acc.email.isNotBlank() && !acc.email.endsWith("@${Supabase.PHONE_EMAIL_DOMAIN}")) {
                isLoading = false
                email = acc.email
                postAuthStage = PostAuthStage.EMAIL_VERIFICATION_PENDING
                return
            }

            // Check if user row exists in users table or is already configured locally
            var profileLoaded = false
            try {
                val userDoc = Supabase.fetchRowByColumn(Supabase.TABLE_USERS, "id", acc.id)
                    ?: throw Exception("user row not found")
                fun rowField(name: String): String? =
                    userDoc.get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
                val roleCode = rowField("role") ?: "DOCTOR"
                val docName = rowField("name") ?: acc.name
                val docPhone = rowField("phone") ?: acc.phone
                val docDoctorName = rowField("doctorName") ?: docName
                viewModel.setCachedUserProfile(name = docName, phone = docPhone, email = acc.email, doctorName = docDoctorName, accountId = acc.id)
                viewModel.setUserRole(UserRole.fromCode(roleCode))
                viewModel.markProfileSetupComplete()
                profileLoaded = true
            } catch (e: Exception) {
                // If remote doc not found/network error, only trust local cache when the
                // profile-setup flag is set AND the cached account id matches exactly.
                // This prevents the LaunchedEffect fired after OAuth Custom Tab returns
                // (where we cached acc.name/acc.email in onGoogleLogin) from mistaking
                // "just-started Google login, profile not yet filled" for "profile complete"
                // and skipping GOOGLE_PROFILE_SETUP, which was the root of the Google-login
                // infinite loop.
                val cachedAccountId = viewModel.getCachedAccountId()
                val accountMatches = cachedAccountId == acc.id && cachedAccountId.isNotBlank()
                if (accountMatches && viewModel.isProfileSetupComplete()) {
                    profileLoaded = true
                }
            }

            if (!profileLoaded) {
                // Profile not created yet — new Google signup or incomplete signup.
                email = acc.email
                name = acc.name
                isLoading = false
                postAuthStage = PostAuthStage.GOOGLE_PROFILE_SETUP
                return
            }
        } else {
            // اگر حساب کاربری معتبر از سوپابیس دریافت نشد، تنها در صورت ادامه می‌دهیم
            // که حالت مهمان فعال باشد یا پروفایل آفلاین قبلاً تایید شده باشد.
            if (!viewModel.isGuestMode.value) {
                val cachedAccountId = viewModel.getCachedAccountId()
                val cachedEmail = viewModel.getCurrentUserEmail()
                if (cachedAccountId.isBlank() && cachedEmail.isBlank()) {
                    isLoading = false
                    return
                }
            }
        }

        // رفع باگ اصلی: bootstrap (pullAll) را اینجا به‌صورت هم‌زمان (suspend) صدا می‌زنیم
        // — نه در یک لانچ fire-and-forget. کاربر را فقط AFTER از پایان موفق دانلود به
        // داشبورد می‌فرستیم. این تضمین می‌کند که لیست‌های Flow قبل از باز شدن MainScreen
        // داده‌های سرور را دارند و دیگر «خالی آمدن» نداشته باشیم.
        if (!alreadyBootstrapped && acc != null && !viewModel.isGuestMode.value) {
            try {
                viewModel.bootstrapAfterLogin { prog ->
                    loadingLabel = "در حال همگام‌سازی (${(prog * 100).toInt()}%)"
                }
            } catch (e: Exception) {
                Log.w("AuthScreen", "Bootstrap pullAll had errors but continuing: ${e.message}")
            }
        }

        // Auto-complete onboarding in clinic settings so wizard is never displayed on login
        try {
            viewModel.ensureOnboardingCompleted()
        } catch (e: Exception) {}

        authFlowFinished = true
        isLoading = false
        postAuthStage = PostAuthStage.NONE
        viewModel.isAppUnlocked.value = true
        onUnlocked()
    }

    // Auto-login check on mount.
    // نکته مهم: بعد از بازگشت از OAuth گوگل ممکن است اکتیویتی دوباره ساخته شود و این
    // LaunchedEffect مجدداً اجرا گردد. با guard «authFlowFinished» و «isLoading» مانع
    // اجرای دوباره proceedAfterAuth در میانه‌ی یک لاگین در حال انجام می‌شویم.
    LaunchedEffect(Unit) {
        if (authFlowFinished || isLoading) {
            checkingSession = false
            return@LaunchedEffect
        }
        try {
            Supabase.ensureAccountId() // در صورت وجود refresh token، نشست هم تمدید می‌شود
            isLoading = true
            loadingLabel = "در حال همگام‌سازی..."
            proceedAfterAuth()
        } catch (e: Exception) {
            // Not logged in — stay on auth screen.
        } finally {
            checkingSession = false
            if (!authFlowFinished) isLoading = false
        }
    }

    // تکمیل ورود گوگل (PKCE): مرورگر با dental://app?code=... برمی‌گردد و اینجا
    // کد با نشست تعویض می‌شود.
    LaunchedEffect(Unit) {
        viewModel.pendingOAuthCode.collect { code ->
            if (code.isNullOrBlank() || authFlowFinished) return@collect
            viewModel.pendingOAuthCode.value = null
            isLoading = true
            loadingLabel = "در حال تکمیل ورود گوگل..."
            try {
                Supabase.exchangeOAuthCode(code)
                proceedAfterAuth()
            } catch (e: Exception) {
                errorMessage = "تکمیل ورود گوگل ناموفق بود: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background
    ) {
        if (postAuthStage == PostAuthStage.EMAIL_VERIFICATION_PENDING) {
            EmailVerificationPendingScreen(
                email = email,
                onResend = {
                    scope.launch {
                        try {
                            Supabase.resendVerificationEmail(email)
                            infoMessage = "لینک تایید مجدداً ارسال شد"
                        } catch (e: Exception) {
                            errorMessage = "خطا در ارسال مجدد لینک تایید: ${e.message}"
                        }
                    }
                },
                onCheckAgain = {
                    scope.launch {
                        clearMessages()
                        isLoading = true
                        loadingLabel = "در حال بررسی..."
                        try {
                            // اگر کاربر روی لینک ایمیل کلیک کرده باشد، ورود با همان
                            // ایمیل/رمزِ واردشده موفق می‌شود و نشست ساخته می‌شود
                            Supabase.signInWithPassword(email.trim(), password)
                            proceedAfterAuth()
                        } catch (e: Exception) {
                            infoMessage = "هنوز تایید نشده است — پس از کلیک روی لینک ایمیل، دوباره بررسی کنید"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        try { Supabase.signOut() } catch (e: Exception) {}
                        Supabase.resetAccountIdCache()
                        postAuthStage = PostAuthStage.NONE
                    }
                },
                isLoading = isLoading
            )
            return@Surface
        }

        if (postAuthStage == PostAuthStage.GOOGLE_PROFILE_SETUP) {
            GoogleProfileSetupScreen(
                prefilledEmail = email,
                prefilledName = name,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onClearError = { errorMessage = null },
                onCancel = {
                    scope.launch {
                        try { Supabase.signOut() } catch (e: Exception) {}
                        Supabase.resetAccountIdCache()
                        // کش‌شده‌های OAuth را هم پاک می‌کنیم تا LaunchedEffect بعدی
                        // نتواند با تطبیق ایمیل/اکانت کش‌شده، کاربر را به‌صورت
                        // ناقص/زودهنگام وارد داشبورد کند.
                        viewModel.setCachedUserProfile(name = "", phone = "", email = "", accountId = "")
                        authFlowFinished = false
                        isLoading = false
                        errorMessage = null
                        postAuthStage = PostAuthStage.NONE
                        mode = AuthMode.LOGIN_EMAIL
                    }
                },
                onSubmit = { uName, dName, pDigits, role ->
                    clearMessages()
                    scope.launch {
                        isLoading = true
                        loadingLabel = "در حال ثبت اطلاعات حساب..."
                        try {
                            val acc = Supabase.currentUser()
                            // Update display name if changed
                            if (uName.trim().isNotBlank() && uName.trim() != acc.name) {
                                try { Supabase.updateUser(mapOf("name" to uName.trim())) } catch (e: Exception) {}
                            }
                            val fullPhone = toFullIranPhone(pDigits)

                            // Cache profile locally and persist user role
                            viewModel.setUserRole(role)
                            viewModel.setCachedUserProfile(
                                name = uName.trim(),
                                phone = fullPhone,
                                email = acc.email,
                                doctorName = dName.trim(),
                                accountId = acc.id
                            )

                            // Also update clinic settings with doctor name if provided
                            try {
                                val currentSettings = viewModel.settings.value
                                val docNameToApply = if (dName.trim().isNotBlank()) dName.trim() else currentSettings.doctorName
                                val phoneToApply = if (fullPhone.isNotBlank()) fullPhone else currentSettings.clinicPhone
                                viewModel.updateClinicSettings(
                                    currentSettings.copy(
                                        doctorName = docNameToApply,
                                        clinicPhone = phoneToApply
                                    )
                                )
                            } catch (e: Exception) {
                                android.util.Log.w("AuthScreen", "Could not update clinic settings during profile setup: ${e.message}")
                            }

                            // Save to Supabase users table (RLS: فقط ردیف خودِ کاربر)
                            try {
                                upsertUserDocumentSafe(
                                    userId = acc.id,
                                    name = uName.trim(),
                                    doctorName = dName.trim(),
                                    email = acc.email,
                                    phone = fullPhone,
                                    role = role.code
                                )
                            } catch (e: Exception) {
                                android.util.Log.w("AuthScreen", "Remote user document upsert warning: ${e.message}")
                            }

                            // پرچمِ تکمیل پروفایل ست شود تا LaunchedEffect (در صورت بازسازی
                            // اکتیویتی در همین لحظه) دوباره این فرم گوگل را بالا نیاورد.
                            viewModel.markProfileSetupComplete()

                            postAuthStage = PostAuthStage.NONE
                            loadingLabel = "در حال همگام‌سازی اطلاعات..."
                            viewModel.bootstrapAfterLogin { prog ->
                                loadingLabel = "در حال همگام‌سازی (${(prog * 100).toInt()}%)"
                            }
                            proceedAfterAuth(alreadyBootstrapped = true)
                        } catch (e: Exception) {
                            errorMessage = friendlyAuthError(e)
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
            return@Surface
        }

        if (postAuthStage == PostAuthStage.LOCK) {
            LockScreen(viewModel = viewModel, onUnlocked = onUnlocked)
            return@Surface
        }

        if (checkingSession) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = cs.primary)
            }
            return@Surface
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle brand gradient behind the header for visual polish
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(cs.primaryContainer.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- Brand header ----------------
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "دنتال‌پرو",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )
                Text(
                    text = "نرم‌افزار مدیریت مطب دندان‌پزشکی",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ---------------- Card container ----------------
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(220)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        Text(
                            text = when (mode) {
                                AuthMode.REGISTER -> "ایجاد حساب کاربری جدید"
                                AuthMode.LOGIN_PHONE_REQUEST, AuthMode.LOGIN_PHONE_VERIFY -> "ورود با شماره موبایل"
                                else -> "ورود به حساب کاربری"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (mode) {
                                AuthMode.REGISTER -> "اطلاعات زیر را برای ثبت‌نام کامل کنید"
                                AuthMode.LOGIN_PHONE_REQUEST -> "کد تایید برای شماره شما پیامک می‌شود"
                                AuthMode.LOGIN_PHONE_VERIFY -> "کد ۶ رقمی ارسال‌شده را وارد کنید"
                                else -> "برای ادامه، ایمیل و رمز عبور خود را وارد کنید"
                            },
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // ---------------- Inline message banner ----------------
                        AnimatedVisibility(visible = errorMessage != null || infoMessage != null) {
                            val isError = errorMessage != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isError) cs.errorContainer else cs.primaryContainer
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = if (isError) cs.onErrorContainer else cs.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: infoMessage ?: "",
                                    fontSize = 13.sp,
                                    color = if (isError) cs.onErrorContainer else cs.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        when (mode) {
                            AuthMode.LOGIN_EMAIL -> LoginEmailForm(
                                email = email, onEmailChange = { email = it; clearMessages() },
                                password = password, onPasswordChange = { password = it; clearMessages() },
                                passwordVisible = passwordVisible, onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                isLoading = isLoading, loadingLabel = loadingLabel,
                                onForgotPassword = forgotPassword@{
                                    clearMessages()
                                    if (!isValidEmail(email)) {
                                        errorMessage = "برای بازیابی رمز، ابتدا یک ایمیل معتبر وارد کنید"
                                        return@forgotPassword
                                    }
                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال ارسال لینک بازیابی..."
                                        try {
                                            Supabase.requestPasswordReset(email.trim())
                                            infoMessage = "لینک بازیابی رمز عبور به ایمیل شما ارسال شد"
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "ارسال لینک بازیابی با خطا مواجه شد"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onSubmit = loginSubmit@{
                                    clearMessages()
                                    val input = email.trim()
                                    if (input.isBlank()) {
                                        errorMessage = "ایمیل یا شماره موبایل را وارد کنید"
                                        return@loginSubmit
                                    }
                                    if (password.isBlank()) {
                                        errorMessage = "رمز عبور را وارد کنید"
                                        return@loginSubmit
                                    }
                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال ورود..."
                                        try {
                                            if (isValidEmail(input)) {
                                                Supabase.signInWithPassword(input, password)
                                            } else if (isValidPatientStylePhoneDigits(input.filter(Char::isDigit).takeLast(9))) {
                                                // ورود با موبایل+رمز: حساب‌های پیامکی «ایمیل قطعی» دارند؛ برای
                                                // حساب‌های ثبت‌شده با ایمیل واقعی، تابع سرور phone-to-email ایمیل
                                                // واقعی را برمی‌گرداند و دوباره تلاش می‌کنیم.
                                                val digits9 = input.filter(Char::isDigit).takeLast(9)
                                                val fullPhone = toFullIranPhone(digits9)
                                                val deterministic = Supabase.phoneToDeterministicEmail(fullPhone)
                                                try {
                                                    Supabase.signInWithPassword(deterministic, password)
                                                } catch (firstAttempt: Exception) {
                                                    val resolvedEmail = try {
                                                        val fnBody = JsonObject().apply { addProperty("phone", fullPhone) }
                                                        val fnRes = org.json.JSONObject(Supabase.invokeEdgeFunction("phone-to-email", fnBody))
                                                        fnRes.optString("email", "")
                                                    } catch (e: Exception) { "" }
                                                    if (resolvedEmail.isNotBlank() && !resolvedEmail.equals(deterministic, ignoreCase = true)) {
                                                        Supabase.signInWithPassword(resolvedEmail, password)
                                                    } else {
                                                        throw firstAttempt
                                                    }
                                                }
                                            } else {
                                                errorMessage = "فرمت ایمیل یا شماره موبایل صحیح نیست"
                                                isLoading = false
                                                return@launch
                                            }
                                            try {
                                                val acc = Supabase.currentUser()
                                                viewModel.setCachedUserProfile(name = acc.name, phone = acc.phone, email = acc.email, accountId = acc.id)
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                            loadingLabel = "در حال همگام‌سازی اطلاعات..."
                                            viewModel.bootstrapAfterLogin { prog ->
                                                loadingLabel = "در حال همگام‌سازی (${(prog * 100).toInt()}%)"
                                            }
                                            proceedAfterAuth(alreadyBootstrapped = true)
                                        } catch (e: Exception) {
                                            errorMessage = friendlyAuthError(e)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onSwitchToPhone = { clearMessages(); mode = AuthMode.LOGIN_PHONE_REQUEST },
                                onSwitchToRegister = { clearMessages(); mode = AuthMode.REGISTER },

                                onGuestLogin = {
                                    clearMessages()
                                    viewModel.setCachedUserProfile(
                                        name = "کاربر مهمان (نسخه دمو)",
                                        phone = "09120000000",
                                        email = "guest@dentalpro.app",
                                        doctorName = "دکتر مهمان"
                                    )
                                    viewModel.setUserRole(UserRole.DOCTOR)
                                    viewModel.isGuestMode.value = true
                                    viewModel.isAppUnlocked.value = true
                                    onUnlocked()
                                },

                                onGoogleLogin = googleLogin@{
                                    if (isLoading || authFlowFinished) return@googleLogin
                                    clearMessages()
                                    try {
                                        // OAuth گوگل با PKCE — بدون نیاز به Google Play Services (مناسب ایران)؛
                                        // مرورگر/Chrome Custom Tab باز می‌شود و با dental://app?code=... برمی‌گردد.
                                        val verifier = Supabase.generatePkceVerifier()
                                        val url = Supabase.buildGoogleAuthorizeUrl(verifier)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        infoMessage = "صفحه ورود گوگل باز شد؛ پس از تکمیل ورود، به برنامه برگردید"
                                    } catch (e: Exception) {
                                        errorMessage = "باز کردن صفحه گوگل ممکن نشد: ${e.message}"
                                    }
                                }
                            )

                            AuthMode.LOGIN_PHONE_REQUEST -> PhoneRequestForm(
                                phone = phone, onPhoneChange = { phone = it; clearMessages() },
                                isLoading = isLoading, loadingLabel = loadingLabel,
                                onSubmit = phoneRequestSubmit@{
                                    clearMessages()
                                    if (!isValidIranPhoneLoose(phone)) {
                                        errorMessage = "شماره موبایل معتبر نیست. مثال: 0912xxxxxxx"
                                        return@phoneRequestSubmit
                                    }
                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال ارسال کد..."
                                        try {
                                            val formatted = normalizeIranPhone(phone)
                                            val fnBody = JsonObject().apply { addProperty("phone", formatted) }
                                            Supabase.invokeEdgeFunction("send-otp", fnBody)
                                            mode = AuthMode.LOGIN_PHONE_VERIFY
                                        } catch (e: Exception) {
                                            errorMessage = friendlyAuthError(e)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onBack = { clearMessages(); mode = AuthMode.LOGIN_EMAIL }
                            )

                            AuthMode.LOGIN_PHONE_VERIFY -> PhoneVerifyForm(
                                phone = normalizeIranPhone(phone),
                                otpCode = otpCode, onOtpChange = { otpCode = it; clearMessages() },
                                isLoading = isLoading, loadingLabel = loadingLabel,
                                onSubmit = phoneVerifySubmit@{
                                    clearMessages()
                                    if (otpCode.length < 4) {
                                        errorMessage = "کد تایید را کامل وارد کنید"
                                        return@phoneVerifySubmit
                                    }
                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال تایید..."
                                        try {
                                            val formatted = normalizeIranPhone(phone)
                                            val fnBody = JsonObject().apply {
                                                addProperty("phone", formatted)
                                                addProperty("code", otpCode.trim())
                                            }
                                            val fnRes = org.json.JSONObject(Supabase.invokeEdgeFunction("verify-otp", fnBody))
                                            val magicToken = fnRes.optString("token", "")
                                            if (magicToken.isBlank()) throw Exception("توکن ورود از سرور دریافت نشد")
                                            Supabase.verifyMagicToken(magicToken)
                                            loadingLabel = "در حال همگام‌سازی اطلاعات..."
                                            proceedAfterAuth()
                                        } catch (e: Exception) {
                                            errorMessage = friendlyAuthError(e)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onResend = {
                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال ارسال مجدد..."
                                        clearMessages()
                                        try {
                                            val formatted = normalizeIranPhone(phone)
                                            val fnBody = JsonObject().apply { addProperty("phone", formatted) }
                                            Supabase.invokeEdgeFunction("send-otp", fnBody)
                                            infoMessage = "کد تایید مجدد ارسال شد"
                                        } catch (e: Exception) {
                                            errorMessage = friendlyAuthError(e)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onBack = { clearMessages(); otpCode = ""; mode = AuthMode.LOGIN_PHONE_REQUEST }
                            )

                            AuthMode.REGISTER -> RegisterForm(
                                name = name, onNameChange = { name = it; clearMessages() },
                                email = email, onEmailChange = { email = it; clearMessages() },
                                phone = phone, onPhoneChange = { phone = it; clearMessages() },
                                password = password, onPasswordChange = { password = it; clearMessages() },
                                confirmPassword = confirmPassword, onConfirmPasswordChange = { confirmPassword = it; clearMessages() },
                                passwordVisible = passwordVisible, onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                confirmPasswordVisible = confirmPasswordVisible, onToggleConfirmPasswordVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                                selectedRole = selectedRole, onRoleChange = { selectedRole = it },
                                acceptedTerms = acceptedTerms, onAcceptedTermsChange = { acceptedTerms = it },
                                isLoading = isLoading, loadingLabel = loadingLabel,
                                onSubmit = registerSubmit@{
                                    clearMessages()
                                    val validationError = when {
                                        name.trim().length < 2 -> "نام کامل خود را وارد کنید"
                                        !isValidEmail(email) -> "ایمیل وارد شده معتبر نیست"
                                        !isValidPatientStylePhoneDigits(phone) -> "شماره موبایل ۹ رقمی را صحیح وارد کنید"
                                        !isValidPassword(password) -> "رمز عبور باید حداقل ۸ کاراکتر باشد"
                                        password != confirmPassword -> "رمز عبور و تکرار آن یکسان نیستند"
                                        selectedRole == null -> "لطفا سمت خود را انتخاب کنید"
                                        !acceptedTerms -> "برای ادامه باید قوانین استفاده را بپذیرید"
                                        else -> null
                                    }
                                    if (validationError != null) {
                                        errorMessage = validationError
                                        return@registerSubmit
                                    }

                                    scope.launch {
                                        isLoading = true; loadingLabel = "در حال ایجاد حساب..."
                                        try {
                                            val fullPhone = toFullIranPhone(phone.trim())
                                            val session = Supabase.signUpWithEmail(
                                                email.trim(),
                                                password,
                                                mapOf(
                                                    "name" to name.trim(),
                                                    "phone" to fullPhone,
                                                    "role" to (selectedRole?.code ?: "DOCTOR")
                                                )
                                            )
                                            viewModel.setCachedUserProfile(name = name.trim(), phone = fullPhone, email = email.trim(), doctorName = name.trim())
                                            viewModel.setUserRole(selectedRole!!)
                                            viewModel.markProfileSetupComplete()

                                            if (session != null) {
                                                // تایید ایمیل روی پروژه غیرفعال است — ورود مستقیم
                                                upsertUserDocumentSafe(
                                                    userId = Supabase.ensureAccountId(),
                                                    name = name.trim(),
                                                    doctorName = name.trim(),
                                                    email = email.trim(),
                                                    phone = fullPhone,
                                                    role = selectedRole!!.code
                                                )
                                                loadingLabel = "در حال همگام‌سازی اطلاعات..."
                                                viewModel.bootstrapAfterLogin { prog ->
                                                    loadingLabel = "در حال همگام‌سازی (${(prog * 100).toInt()}%)"
                                                }
                                                proceedAfterAuth(alreadyBootstrapped = true)
                                            } else {
                                                // تایید ایمیل فعال است — نشست فقط بعد از کلیک روی لینک می‌آید
                                                postAuthStage = PostAuthStage.EMAIL_VERIFICATION_PENDING
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = friendlyAuthError(e)
                                            val msg = e.message ?: ""
                                            if (msg.contains("already registered", ignoreCase = true) || msg.contains("already exists", ignoreCase = true)) {
                                                errorMessage = "این ایمیل قبلاً ثبت شده است. لطفاً وارد شوید."
                                                kotlinx.coroutines.delay(1500)
                                                mode = AuthMode.LOGIN_EMAIL
                                            }
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onBackToLogin = { clearMessages(); mode = AuthMode.LOGIN_EMAIL }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ورود شما به معنای پذیرش قوانین استفاده از نرم‌افزار دنتال‌پرو است",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun friendlyAuthError(e: Exception): String {
    val msg = e.message ?: return "خطایی رخ داد. دوباره تلاش کنید"
    return when {
        msg.contains("Invalid login credentials", ignoreCase = true) -> "ایمیل/شماره یا رمز عبور نادرست است"
        msg.contains("Email not confirmed", ignoreCase = true) -> "ایمیل شما هنوز تایید نشده است"
        msg.contains("already registered", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) -> "این ایمیل قبلاً ثبت شده است. وارد شوید یا از گزینه فراموشی رمز استفاده کنید"
        msg.contains("Signups not allowed", ignoreCase = true) -> "ثبت‌نام روی این سرور غیرفعال است (پنل مدیریت سرور ابری را بررسی کنید)"
        msg.contains("سرویس پیامک هنوز پیکربندی نشده", ignoreCase = true) -> "سرویس پیامک هنوز فعال نشده است — کلید SMS.ir را در Secretهای توابع سرور تنظیم کنید"
        msg.contains("Function not found", ignoreCase = true) || msg.contains("Edge Function", ignoreCase = true) -> "توابع سرور (Edge Functions) هنوز Deploy نشده‌اند"
        msg.contains("Rate limit", ignoreCase = true) || msg.contains("Too Many Requests", ignoreCase = true) -> "تعداد تلاش‌ها بیش از حد مجاز است. کمی بعد دوباره تلاش کنید"
        msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true) || msg.contains("network", ignoreCase = true) -> "خطا در اتصال به اینترنت"
        else -> msg
    }
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it, fontSize = 11.sp) } },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun LoadingButton(
    text: String,
    isLoading: Boolean,
    loadingLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(loadingLabel.ifBlank { "لطفاً صبر کنید..." }, fontSize = 14.sp)
            }
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoginEmailForm(
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    isLoading: Boolean, loadingLabel: String,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
    onSwitchToPhone: () -> Unit,
    onSwitchToRegister: () -> Unit,
    onGuestLogin: () -> Unit,
    onGoogleLogin: () -> Unit
) {
    Column {
        StyledTextField(
            value = email, onValueChange = onEmailChange,
            label = "ایمیل یا شماره موبایل", leadingIcon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )
        Spacer(modifier = Modifier.height(14.dp))
        StyledTextField(
            value = password, onValueChange = onPasswordChange,
            label = "رمز عبور", leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                }
            },
            keyboardType = KeyboardType.Password
        )
        Spacer(modifier = Modifier.height(14.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgotPassword) {
                Text("فراموشی رمز عبور؟", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        LoadingButton(text = "ورود", isLoading = isLoading, loadingLabel = loadingLabel, onClick = onSubmit)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Guest Login Button
        OutlinedButton(
            onClick = onGuestLogin,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ورود مهمان", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(" یا سایر روش‌ها ", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp), fontSize = 12.sp)
            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onSwitchToPhone, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تلفن همراه")
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = onGoogleLogin, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("گوگل")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("حساب کاربری ندارید؟", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSwitchToRegister) {
                Text("ثبت‌نام کنید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PhoneRequestForm(
    phone: String, onPhoneChange: (String) -> Unit,
    isLoading: Boolean, loadingLabel: String,
    onSubmit: () -> Unit, onBack: () -> Unit
) {
    Column {
        IranMobileNumberField(
            digits = phone,
            onDigitsChange = onPhoneChange,
            label = "شماره موبایل (۹ رقم) *"
        )
        Spacer(modifier = Modifier.height(24.dp))
        LoadingButton(text = "دریافت کد تایید", isLoading = isLoading, loadingLabel = loadingLabel, onClick = onSubmit)
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("بازگشت به ورود با ایمیل")
        }
    }
}

@Composable
private fun PhoneVerifyForm(
    phone: String,
    otpCode: String, onOtpChange: (String) -> Unit,
    isLoading: Boolean, loadingLabel: String,
    onSubmit: () -> Unit, onResend: () -> Unit, onBack: () -> Unit
) {
    Column {
        Text("کد تایید به شماره $phone پیامک شد", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        StyledTextField(
            value = otpCode, onValueChange = onOtpChange,
            label = "کد ۶ رقمی تایید", leadingIcon = Icons.Default.Lock,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(24.dp))
        LoadingButton(text = "تایید و ورود", isLoading = isLoading, loadingLabel = loadingLabel, onClick = onSubmit)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Text("ویرایش شماره")
            }
            TextButton(onClick = onResend) {
                Text("ارسال مجدد کد")
            }
        }
    }
}

@Composable
private fun RegisterForm(
    name: String, onNameChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    confirmPassword: String, onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    confirmPasswordVisible: Boolean, onToggleConfirmPasswordVisible: () -> Unit,
    selectedRole: UserRole?, onRoleChange: (UserRole) -> Unit,
    acceptedTerms: Boolean, onAcceptedTermsChange: (Boolean) -> Unit,
    isLoading: Boolean, loadingLabel: String,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var showTermsDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column {
        StyledTextField(
            value = name, onValueChange = onNameChange,
            label = "نام و نام خانوادگی (ثبت‌نام‌کننده)", leadingIcon = Icons.Default.Person
        )
        Spacer(modifier = Modifier.height(14.dp))
        StyledTextField(
            value = email, onValueChange = onEmailChange,
            label = "ایمیل معتبر", leadingIcon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )
        Spacer(modifier = Modifier.height(14.dp))
        IranMobileNumberField(
            digits = phone,
            onDigitsChange = onPhoneChange,
            label = "شماره موبایل (۹ رقم) *"
        )
        Spacer(modifier = Modifier.height(14.dp))
        StyledTextField(
            value = password, onValueChange = onPasswordChange,
            label = "رمز عبور", leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                }
            },
            keyboardType = KeyboardType.Password,
            supportingText = "حداقل ۸ کاراکتر"
        )
        Spacer(modifier = Modifier.height(14.dp))
        StyledTextField(
            value = confirmPassword, onValueChange = onConfirmPasswordChange,
            label = "تکرار رمز عبور", leadingIcon = Icons.Default.Lock,
            visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleConfirmPasswordVisible) {
                    Icon(if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                }
            },
            keyboardType = KeyboardType.Password
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("تعیین سمت:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickableSimple { onRoleChange(UserRole.DOCTOR) }) {
                RadioButton(selected = selectedRole == UserRole.DOCTOR, onClick = { onRoleChange(UserRole.DOCTOR) })
                Text("پزشک (مدیر)")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickableSimple { onRoleChange(UserRole.ASSISTANT) }) {
                RadioButton(selected = selectedRole == UserRole.ASSISTANT, onClick = { onRoleChange(UserRole.ASSISTANT) })
                Text("منشی (پذیرش)")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acceptedTerms, onCheckedChange = onAcceptedTermsChange)
            Text("قوانین و مقررات دنتال‌پرو را ", fontSize = 13.sp, modifier = Modifier.clickableSimple { onAcceptedTermsChange(!acceptedTerms) })
            TextButton(onClick = { showTermsDialog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("مطالعه و ", textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, fontSize = 13.sp)
            }
            Text("می‌پذیرم", fontSize = 13.sp, modifier = Modifier.clickableSimple { onAcceptedTermsChange(!acceptedTerms) })
        }

        if (showTermsDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = { Text("قوانین و مقررات استفاده از دنتال‌پرو") },
                text = {
                    Column(modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 400.dp)) {
                        Text(TERMS_AND_CONDITIONS_TEXT_FA)
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        onAcceptedTermsChange(true)
                        showTermsDialog = false
                    }) { Text("قبول دارم") }
                },
                dismissButton = {
                    TextButton(onClick = { showTermsDialog = false }) { Text("بستن") }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        LoadingButton(text = "ایجاد حساب کاربری", isLoading = isLoading, loadingLabel = loadingLabel, onClick = onSubmit)
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("حساب کاربری دارید؟", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBackToLogin) {
                Text("وارد شوید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// Small helper so plain Text/Row elements act as tap targets, without a
// ripple/interaction-source boilerplate at every call site.
private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
fun EmailVerificationPendingScreen(
    email: String,
    onResend: () -> Unit,
    onCheckAgain: () -> Unit,
    onLogout: () -> Unit,
    isLoading: Boolean
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(72.dp), tint = cs.primary)
        Spacer(Modifier.height(24.dp))
        Text("تایید ایمیل", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        Text("لینک فعال‌سازی به ایمیل زیر ارسال شده است:", textAlign = TextAlign.Center)
        Text(email, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
        Text("لطفا صندوق ورودی (و پوشه Spam) خود را بررسی کنید.", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        
        LoadingButton(text = "بررسی مجدد وضعیت", isLoading = isLoading, loadingLabel = "در حال بررسی...", onClick = onCheckAgain)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
            Text("ارسال مجدد لینک تایید")
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onLogout) {
            Text("خروج از حساب")
        }
    }
}

@Composable
fun GoogleProfileSetupScreen(
    prefilledEmail: String,
    prefilledName: String,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: (fullName: String, doctorName: String, phoneDigits: String, role: UserRole) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var fullName by remember { mutableStateOf(prefilledName) }
    var doctorName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = cs.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "تکمیل اطلاعات حساب کاربری",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = cs.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "اطلاعات حساب و مطب خود را برای شروع استفاده تکمیل کنید",
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        StyledTextField(
            value = fullName,
            onValueChange = { fullName = it; localError = null; onClearError() },
            label = "نام و نام خانوادگی (ثبت‌نام‌کننده) *",
            leadingIcon = Icons.Default.Person
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = prefilledEmail,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("ایمیل (تایید شده با حساب گوگل)") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            trailingIcon = { Icon(Icons.Default.CheckCircle, tint = Color(0xFF2E7D32), contentDescription = "تایید شده") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = cs.onSurface,
                disabledBorderColor = cs.outlineVariant,
                disabledLabelColor = cs.onSurfaceVariant,
                disabledLeadingIconColor = cs.primary,
                disabledTrailingIconColor = Color(0xFF2E7D32)
            )
        )
        Spacer(Modifier.height(14.dp))

        IranMobileNumberField(
            digits = phone,
            onDigitsChange = { phone = it; localError = null; onClearError() },
            label = "شماره موبایل (۹ رقم) *"
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = "تعیین سمت در مطب:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableSimple { selectedRole = UserRole.DOCTOR; localError = null; onClearError() }
            ) {
                RadioButton(
                    selected = selectedRole == UserRole.DOCTOR,
                    onClick = { selectedRole = UserRole.DOCTOR; localError = null; onClearError() }
                )
                Text("پزشک (مدیر)")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableSimple { selectedRole = UserRole.ASSISTANT; localError = null; onClearError() }
            ) {
                RadioButton(
                    selected = selectedRole == UserRole.ASSISTANT,
                    onClick = { selectedRole = UserRole.ASSISTANT; localError = null; onClearError() }
                )
                Text("منشی (پذیرش)")
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it; localError = null; onClearError() }
            )
            Text(
                text = "قوانین و مقررات دنتال‌پرو را ",
                fontSize = 13.sp,
                modifier = Modifier.clickableSimple { acceptedTerms = !acceptedTerms; localError = null; onClearError() }
            )
            TextButton(
                onClick = { showTermsDialog = true },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "مطالعه و ",
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    fontSize = 13.sp
                )
            }
            Text(
                text = "می‌پذیرم",
                fontSize = 13.sp,
                modifier = Modifier.clickableSimple { acceptedTerms = !acceptedTerms; localError = null; onClearError() }
            )
        }

        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = { Text("قوانین و مقررات استفاده از دنتال‌پرو") },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 400.dp)
                    ) {
                        Text(TERMS_AND_CONDITIONS_TEXT_FA)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        acceptedTerms = true
                        localError = null
                        onClearError()
                        showTermsDialog = false
                    }) { Text("قبول دارم") }
                },
                dismissButton = {
                    TextButton(onClick = { showTermsDialog = false }) { Text("بستن") }
                }
            )
        }

        val displayError = localError ?: errorMessage
        if (displayError != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = cs.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = displayError,
                        color = cs.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        LoadingButton(
            text = "ثبت اطلاعات و ورود",
            isLoading = isLoading,
            loadingLabel = "در حال ثبت اطلاعات...",
            onClick = {
                val vError = when {
                    fullName.trim().isBlank() -> "لطفاً نام و نام خانوادگی خود را وارد کنید"
                    !isValidPatientStylePhoneDigits(phone.trim()) -> "شماره موبایل باید ۹ رقم باشد (بدون صفر اول)"
                    selectedRole == null -> "لطفاً سمت خود را در مطب انتخاب کنید"
                    !acceptedTerms -> "برای ادامه باید قوانین استفاده را بپذیرید"
                    else -> null
                }
                if (vError != null) {
                    localError = vError
                } else {
                    localError = null
                    onClearError()
                    onSubmit(fullName.trim(), fullName.trim(), phone.trim(), selectedRole!!)
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) {
            Text(
                text = "انصراف و خروج از حساب گوگل",
                color = cs.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
