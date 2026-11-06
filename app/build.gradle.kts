import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Presage key stays in local.properties, which git ignores.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val presageKey: String = localProperties.getProperty("PRESAGE_API_KEY")
    ?: System.getenv("PRESAGE_API_KEY")
    ?: ""

// Developer-only switch for testing screens on an emulator. It replaces the camera with
// made-up readings and must never be used for a real check or a demo.
val simulate = (findProperty("nidra.simulate") as String?) == "true"

android {
    namespace = "com.rahulrao.nidra"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.rahulrao.nidra"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PRESAGE_API_KEY", "\"$presageKey\"")
        buildConfigField("boolean", "SIMULATE", simulate.toString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-view:1.6.0")
    implementation("com.presagetech:smartspectra:3.4.0")

    testImplementation("junit:junit:4.13.2")
}
