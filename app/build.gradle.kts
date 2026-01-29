import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
}

configurations.all {
    resolutionStrategy {
        // Fix Room being a broken piece of garbage
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    }
}

android {
    namespace = "se.koditoriet.snout"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "se.koditoriet.snout"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-pre6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            ndk {
                abiFilters.add("arm64-v8a")
                debugSymbolLevel = "none"
                packaging {
                    jniLibs {
                        useLegacyPackaging = false
                    }
                }
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    buildFeatures {
        compose = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    // Core Android stuff
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.fragment.ktx)

    // CBOR
    implementation(libs.com.upokecenter.cbor)

    // kotlinx-datetime
    implementation(libs.org.jetbrains.kotlinx.datetime)

    // Serialization
    implementation(libs.org.jetbrains.kotlinx.serialization.json)

    // SQLCipher
    implementation(libs.net.zetetic.sqlcipher.android)

    // ZXing for QR scanning
    implementation(libs.com.google.zxing.core)

    // Printing
    implementation(libs.androidx.print)

    // Room
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)

    // Test deps
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}