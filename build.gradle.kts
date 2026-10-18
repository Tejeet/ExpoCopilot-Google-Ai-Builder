// Top-level build file. Per-module config lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.13.2" apply false
    // Kotlin 2.4: required by LiteRT-LM (on-device Gemma), which is compiled with Kotlin 2.4.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
