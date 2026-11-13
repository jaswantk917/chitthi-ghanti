import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.jaswantk917.chitthi"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "io.github.jaswantk917.chitthighanti"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 16
        versionName = "1.16"
        // Gemini Nano phones are all arm64
        ndk { abiFilters += "arm64-v8a" }
    }

    // the Play upload key; Google holds the app signing key. Both live outside the repo
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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Chitthi debug")
            resValue("string", "ghanti_name", "Ghanti debug")
            resValue("string", "app_label", "Tring debug")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Chitthi")
            resValue("string", "ghanti_name", "Ghanti")
            resValue("string", "app_label", "Tring")
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
                "androidx.compose.foundation.ExperimentalFoundationApi",
                "androidx.compose.animation.ExperimentalSharedTransitionApi",
            )
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.graphics.shapes)
    implementation(libs.coil.compose)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
