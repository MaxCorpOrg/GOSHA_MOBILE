import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.inputStream().use(::load)
    }
}

fun configValue(name: String): String =
    (
        keystoreProperties.getProperty(name)
            ?: providers.gradleProperty(name).orNull
            ?: System.getenv(name)
            ?: ""
        ).trim()

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun isHttpUrl(value: String): Boolean {
    return try {
        val uri = URI(value.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

val rustoreKeystoreFile = configValue("RUSTORE_KEYSTORE_FILE")
val rustoreKeystorePassword = configValue("RUSTORE_KEYSTORE_PASSWORD")
val rustoreKeyAlias = configValue("RUSTORE_KEY_ALIAS")
val rustoreKeyPassword = configValue("RUSTORE_KEY_PASSWORD")
val runtimePanelBaseUrl = configValue("GOSHA_PANEL_BASE_URL")
val rustorePrivacyPolicyUrl = configValue("RUSTORE_PRIVACY_POLICY_URL")
val rustoreTermsOfUseUrl = configValue("RUSTORE_TERMS_OF_USE_URL")

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true)
    }
    if (releaseRequested) {
        require(isHttpUrl(rustorePrivacyPolicyUrl)) {
            "Release build requires RUSTORE_PRIVACY_POLICY_URL as an http(s) URL from keystore.properties, Gradle property, or env."
        }
        require(isHttpUrl(rustoreTermsOfUseUrl)) {
            "Release build requires RUSTORE_TERMS_OF_USE_URL as an http(s) URL from keystore.properties, Gradle property, or env."
        }
    }
}

android {
    namespace = "com.maxcorp.gosha.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maxcorp.gosha.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "DEFAULT_PANEL_BASE_URL",
            "\"${escapeBuildConfigString(runtimePanelBaseUrl)}\""
        )
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"${escapeBuildConfigString(rustorePrivacyPolicyUrl)}\""
        )
        buildConfigField(
            "String",
            "TERMS_OF_USE_URL",
            "\"${escapeBuildConfigString(rustoreTermsOfUseUrl)}\""
        )
        buildConfigField("boolean", "IS_ADMIN_APP", "false")
        resValue("string", "app_name", "Гоша")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("client") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        create("rustoreRelease") {
            if (rustoreKeystoreFile.isNotBlank()) {
                storeFile = file(rustoreKeystoreFile)
            }
            storePassword = rustoreKeystorePassword
            keyAlias = rustoreKeyAlias
            keyPassword = rustoreKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (
                rustoreKeystoreFile.isNotBlank() &&
                rustoreKeystorePassword.isNotBlank() &&
                rustoreKeyAlias.isNotBlank() &&
                rustoreKeyPassword.isNotBlank()
            ) {
                signingConfig = signingConfigs.getByName("rustoreRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        disable += "CoarseFineLocation"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
