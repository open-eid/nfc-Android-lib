import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader.nfc.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "ee.ria.DigiDoc.smartcardreader.nfc.example"
        minSdk = 34
        targetSdk = 37
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.fragment.ktx)
    implementation(libs.material)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.recyclerview)
    implementation(project(":demoapp:libdigidocpp"))
    implementation(project(":libs:card-utils-lib"))
    implementation(project(":libs:id-card-lib"))
    implementation(project(":libs:smart-card-reader-lib"))
}
