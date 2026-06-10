plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.notifassist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notifassist"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf("**/*.so")
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE"
            )
        }
    }

    androidResources { noCompress += listOf("zip", "onnx", "bin") }
}

configurations.all {
    // Paksa semua dependency pakai JNA versi yang sama — hindari duplicate
    resolutionStrategy {
        force("net.java.dev.jna:jna:5.7.0")
        force("net.java.dev.jna:jna-platform:5.7.0")
    }
    // Exclude JNA dari vosk-android agar tidak double
    exclude(group = "net.java.dev.jna", module = "jna-platform")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.android)
    // Sherpa-ONNX AAR lokal — taruh sherpa-onnx.aar di app/libs/
    // Download: https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/1.12.14/sherpa-onnx-1.12.14.aar
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    // JNA — satu sumber saja, versi yang kompatibel dengan Vosk
    implementation("net.java.dev.jna:jna:5.7.0@aar")
    implementation(libs.vosk.android)
    kapt(libs.room.compiler)
}
