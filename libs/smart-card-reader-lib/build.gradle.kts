plugins {
    alias(libs.plugins.android.library)
    jacoco
}

android {
    namespace = "ee.ria.DigiDoc.smartcardreader"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
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

    implementation(project(":libs:card-utils-lib"))

    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
                    "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
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