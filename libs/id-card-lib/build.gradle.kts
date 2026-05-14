plugins {
    alias(libs.plugins.android.library)
    jacoco
}

android {
    namespace = "ee.ria.DigiDoc.idcard"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.annotation)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.guava)
    annotationProcessor(libs.auto.value)
    compileOnly(libs.auto.value.annotations)

    implementation(project(":libs:smart-card-reader-lib"))
    implementation(project(":libs:card-utils-lib"))

    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.truth)
    // PKIX module for building synthetic X.509 fixtures in tests
    // (LatviaIdemiaPersonalDataTest). Not used by production code; tracks
    // the same `bouncyCastle` version as bcprov to avoid divergence.
    testImplementation(libs.bcpkix.jdk18on)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
            exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*",
                    "**/Manifest*.*", "**/*Test*.*", "android/**/*.*",
                    "**/AutoValue_*.*")
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) { include("**/testDebugUnitTest.exec") }
    )
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}