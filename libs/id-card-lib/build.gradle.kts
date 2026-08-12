plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ee.ria.DigiDoc.idcard"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.bcprov.jdk18on)

    implementation(project(":libs:smart-card-reader-lib"))
    implementation(project(":libs:card-utils-lib"))
}