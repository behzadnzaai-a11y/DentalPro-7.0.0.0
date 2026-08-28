import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
}

// ---------------------------------------------------------------------------
// تنظیمات انتشار (Release guard) — مطابق docs/RELEASE_CHECKLIST.md:
// بیلد release فقط با RELEASE_APPROVED=true + keystore.properties + VERSION_CODE/VERSION_NAME
// ساخته می‌شود و هرگز به debug signing برگشت نمی‌کند.
// ---------------------------------------------------------------------------
val isReleaseApproved = providers.gradleProperty("RELEASE_APPROVED").getOrElse("false") == "true"
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").getOrElse("70000").toInt()
        versionName = providers.gradleProperty("VERSION_NAME").getOrElse("7.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePropsFile.exists() && keystoreProps.getProperty("storeFile") != null) {
            create("releaseExternal") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists() && keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("releaseExternal")
            }
            // بدون keystore خارجی، خروجی unsigned می‌ماند — debug signing هرگز fallback نیست.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // --- AndroidX پایه ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- ناوبری ---
    implementation(libs.androidx.navigation.compose)

    // --- Room (پایگاه داده محلی رمزنگاری‌شده با SQLCipher) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher)

    // --- Biometric / WorkManager ---
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)

    // --- Coroutine ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // --- شبکه: OkHttp + Gson (کلاینت Supabase) + Coil ---
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.coil.compose)

    // --- دوربین (گرفتن عکس بیمار/چک) ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- ویرایش عکس (برش/چرخش) ---
    implementation(libs.image.cropper)

    // --- اسکن بارکد/QR چک (ML Kit) ---
    implementation(libs.mlkit.barcode.scanning)

    // --- تست‌ها ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.runner)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ---------------------------------------------------------------------------
// قفل انتشار: assembleRelease بدون RELEASE_APPROVED=true ساخته نمی‌شود.
// ---------------------------------------------------------------------------
tasks.matching { it.name.startsWith("assemble") && it.name.endsWith("Release") }.configureEach {
    doFirst {
        if (!isReleaseApproved) {
            throw GradleException(
                "Release build blocked: set RELEASE_APPROVED=true and provide VERSION_CODE, " +
                    "VERSION_NAME and keystore.properties per docs/RELEASE_CHECKLIST.md"
            )
        }
    }
}
