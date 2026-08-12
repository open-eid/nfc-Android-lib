plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.bcprov.jdk18on)

    implementation(project(":libs:card-utils-lib"))

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.truth)
}