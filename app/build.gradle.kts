plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.agcodespace"
    compileSdk = 36
    val ndkEnv = System.getenv("ANDROID_NDK_VERSION")
    val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val localNdks = if (sdkRoot != null) file("$sdkRoot/ndk").listFiles()?.map { it.name } else null
    val localNdk = localNdks?.firstOrNull { it.startsWith("30.0.") }
    ndkVersion = ndkEnv ?: localNdk ?: "30.0.16138531"

    defaultConfig {
        applicationId = "app.agcodespace"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    val releaseKeystorePath = System.getenv("AGCODESPACE_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("AGCODESPACE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("AGCODESPACE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("AGCODESPACE_KEY_PASSWORD")

    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
            create("playRelease") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    val playReleaseSigning = signingConfigs.findByName("playRelease")
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            if (playReleaseSigning != null) {
                signingConfig = playReleaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
}
