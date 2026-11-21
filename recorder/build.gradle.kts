import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 1
        versionName = "1.0"
    }

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
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    kotlin {
        compilerOptions { jvmTarget = JvmTarget.fromTarget(libs.versions.java.get()) }
    }
}
