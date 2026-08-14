// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Note: AGP 9.0+ has built-in Kotlin support, so there is no separate
// "org.jetbrains.kotlin.android" plugin declared here — AGP compiles Kotlin
// sources itself. The Compose Compiler plugin is still declared separately,
// since it performs the Compose-specific compiler transformation.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
