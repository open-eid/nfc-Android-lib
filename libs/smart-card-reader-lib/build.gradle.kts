plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("../lint.xml")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.guava)
    implementation(libs.rxjava)

    implementation(
        files(
            "libs/acssmc-1.1.6.jar", // ACS
            "libs/androidSCardV1.2.jar" // Identiv
        )
    )

    implementation(project(":libs:card-utils-lib"))

    testImplementation(libs.hamcrest)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.truth)
}