plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.r17e.guide"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.r17e.guide"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Local backend: emulator reaches the Mac via 10.0.2.2; a USB phone can use `adb reverse tcp:8080 tcp:8080`
        // and -Pr17e.backendUrl=http://localhost:8080
        // Play Integrity: GCP project number (not id). Set r17e.gcpProjectNumber in ~/.gradle/gradle.properties or -P.
        // Missing/0 => integrity token is skipped (no-op) and the build logs a warning.
        val gcpProjectNumber = providers.gradleProperty("r17e.gcpProjectNumber").orNull?.trim()?.toLongOrNull() ?: 0L
        if (gcpProjectNumber == 0L) logger.warn("r17e: r17e.gcpProjectNumber not set; Play Integrity disabled in this build")
        buildConfigField("long", "GCP_PROJECT_NUMBER", "${gcpProjectNumber}L")
        buildConfigField("String", "BACKEND_URL", "\"" + (providers.gradleProperty("r17e.backendUrl").orNull ?: "http://10.0.2.2:8080") + "\"")
    }
    // Shared debug key: CI decodes DEBUG_KEYSTORE_BASE64 to a file and sets R17E_DEBUG_KEYSTORE;
    // locally set r17e.debugKeystore in ~/.gradle/gradle.properties. Falls back to the default debug key.
    val sharedDebugKeystore = (System.getenv("R17E_DEBUG_KEYSTORE")
        ?: providers.gradleProperty("r17e.debugKeystore").orNull)
        ?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        getByName("debug") {
            if (sharedDebugKeystore != null) {
                storeFile = sharedDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
