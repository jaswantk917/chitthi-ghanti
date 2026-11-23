import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Sideloaded, never on Play: Play doesn't allow an Accessibility service whose job is recording calls
android {
    namespace = "io.github.jaswantk917.tring.recorder"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "io.github.jaswantk917.tring.recorder"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 12
        versionName = "1.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Shruti's engine comes prebuilt for arm64 only; its C++ brings no runtime of its own
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }

    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // mapped straight from the APK at run time; the 4-bit weights don't compress anyway
    androidResources { noCompress += "cact" }

    // ONNX Runtime's library is 34 MB stored as is and about a third of that packed: a smaller
    // download, for a copy unpacked on install
    packaging { jniLibs { useLegacyPackaging = true } }

    val uploadKeystore = providers.gradleProperty("CG_UPLOAD_KEYSTORE").orNull
    signingConfigs {
        if (uploadKeystore != null) {
            create("release") {
                storeFile = file(uploadKeystore)
                storePassword = providers.gradleProperty("CG_UPLOAD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CG_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CG_UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        // its own app beside the installed recorder, so device tests never replace it (and its recordings)
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
            optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.appfunctions)
    implementation(libs.work.runtime)
    ksp(libs.appfunctions.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    // IndicConformer, for clearer transcripts
    implementation(libs.onnxruntime.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
