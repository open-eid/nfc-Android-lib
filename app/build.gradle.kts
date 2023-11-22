plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader.nfc.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "ee.ria.DigiDoc.smartcardreader.nfc.example"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.0.2"

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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Collections
    implementation("com.google.guava:guava:31.1-android")

    // Fragment
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Digidoc container
    implementation(project(":libdigidocpp"))

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    //ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // NFC-ID lib
    implementation(project(":id-card-lib"))
    implementation(project(":smart-card-reader-lib"))
}
