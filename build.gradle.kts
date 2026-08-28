// ریشه‌ی پروژه — پلاگین‌ها فقط اینجا declare می‌شوند و در ماژول app اعمال می‌گردند.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
