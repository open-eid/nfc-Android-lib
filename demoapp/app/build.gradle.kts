import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader.nfc.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "ee.ria.DigiDoc.smartcardreader.nfc.example"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.fragment.ktx)
    implementation(libs.guava)
    implementation(libs.material)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.recyclerview)
    implementation(project(":demoapp:libdigidocpp"))
    implementation(project(":libs:card-utils-lib"))
    implementation(project(":libs:id-card-lib"))
    implementation(project(":libs:smart-card-reader-lib"))
}
