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

    // Model Vosk (.fst, grafik, dll) tidak boleh dikompres agar bisa di-unpack &
    // dibaca langsung oleh engine native tanpa korup.
    androidResources {
        noCompress += listOf("mdl", "fst", "conf", "int", "ie", "carpa", "txt", "dubm")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE"
            )
        }
    }
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
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation(libs.vosk.android)
    // JNA harus varian @aar (berisi .so Android); jar biasa hanya untuk desktop.
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    kapt(libs.room.compiler)
}
