plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ee.ria.DigiDoc.idcard"
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
    annotationProcessor(libs.auto.value)
    compileOnly(libs.auto.value.annotations)

    implementation(project(":libs:smart-card-reader-lib"))
    implementation(project(":libs:card-utils-lib"))
}