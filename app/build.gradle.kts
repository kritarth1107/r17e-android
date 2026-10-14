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
        versionName = "0.0.1-spike"
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
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
