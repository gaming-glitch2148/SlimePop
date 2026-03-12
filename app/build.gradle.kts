@file:Suppress("DEPRECATION")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signingProps = Properties()
val signingPropsFile = rootProject.file("key.properties")
if (signingPropsFile.exists()) {
    signingPropsFile.inputStream().use { signingProps.load(it) }
}

fun signingProp(name: String): String? {
    return signingProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
}

val defaultStoreFile = if (rootProject.file("android.keystore").exists()) "android.keystore" else null
val releaseStoreFile = signingProp("STORE_FILE") ?: defaultStoreFile
val releaseStorePassword = signingProp("STORE_PASSWORD")
val releaseKeyAlias = signingProp("KEY_ALIAS") ?: "android"
val releaseKeyPassword = signingProp("KEY_PASSWORD") ?: releaseStorePassword
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (isReleaseTaskRequested) {
    check(!releaseStoreFile.isNullOrBlank()) { "Missing STORE_FILE for release signing. Use key.properties or env vars." }
    check(!releaseStorePassword.isNullOrBlank()) { "Missing STORE_PASSWORD for release signing. Use key.properties or env vars." }
    check(!releaseKeyAlias.isNullOrBlank()) { "Missing KEY_ALIAS for release signing. Use key.properties or env vars." }
    check(!releaseKeyPassword.isNullOrBlank()) { "Missing KEY_PASSWORD for release signing. Use key.properties or env vars." }
}

android {
    namespace = "com.slimepop.asmr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.slimepop.asmr"
        minSdk = 23
        targetSdk = 35
        // Increment for each Play Console upload.
        versionCode = 5
        versionName = "2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
    }

    signingConfigs {
        create("release") {
            if (!releaseStoreFile.isNullOrBlank()
                && !releaseStorePassword.isNullOrBlank()
                && !releaseKeyAlias.isNullOrBlank()
                && !releaseKeyPassword.isNullOrBlank()
            ) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.play.services.ads)
    implementation(libs.play.games.v2)
    implementation(libs.billing.ktx)
    implementation(libs.play.review.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
