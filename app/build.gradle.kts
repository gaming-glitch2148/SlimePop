@file:Suppress("DEPRECATION")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.slimepop.asmr"
    // Changed from 36 to 35 for better stability with current libraries
    compileSdk = 35

    defaultConfig {
        applicationId = "com.slimepop.asmr"
        minSdk = 23
        targetSdk = 35
        // version 2 to allow Play Store upload
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            res.setSrcDirs(listOf("src/main/res"))
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

    // FIXED: Replaced deprecated kotlinOptions with modern compilerOptions
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

/**
 * 3D PROCEDURAL SKIN CATALOG (NO IMAGES - USE IN DRAWING LOGIC)
 * -----------------------------------------------------------
 * Map these IDs to hex codes in your SlimeView to create 3D Radial Gradients.
 *
 * 1.  Tropical Ocean:  Base #0077BE, Highlight #82EEFD
 * 2.  Golden Shimmer:  Base #D4AF37, Highlight #FFFACD
 * 3.  Neon Toxic:      Base #39FF14, Highlight #CCFF00 (Enable Glow)
 * 4.  Deep Galaxy:     Base #2E0854, Highlight #FF00FF
 * 5.  Molten Magma:    Base #FF4500, Highlight #FFFF00
 * 6.  Midnight Onyx:   Base #0F0F0F, Highlight #434343
 * 7.  Silver Mercury:  Base #BEC2CB, Highlight #FFFFFF
 * 8.  Magic Mint:      Base #00FA9A, Highlight #F0FFF0
 * 9.  Royal Velvet:    Base #800080, Highlight #E0B0FF
 * 10. Cyber Punk:     Base #FF007F, Highlight #00FFFF
 * 11. Pearl Dust:     Base #F0EAD6, Highlight #FFFFFF
 * 12. Emerald Fire:   Base #50C878, Highlight #99FFCC
 * 13. Electric Blue:  Base #0000FF, Highlight #00FFFF (Enable Glow)
 * 14. Bubblegum:      Base #FF69B4, Highlight #FFC0CB
 * 15. Solar Flare:    Base #FF8C00, Highlight #FFD700
 */