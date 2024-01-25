// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.library") version "8.1.2" apply false
}

val androidxVersion by extra("1.6.0")

val androidxAppCompatVersion by extra("1.6.1")

val timberVersion by extra ("5.0.1")

val bouncycastleVersion by extra ("1.70")

val autoValueVersion by extra ("1.10.1")

val guavaVersion by extra ("31.1-android")
val rxJavaVersion by extra ("3.1.6")

val junitVersion by extra ("5.10.0")
val truthVersion by extra ("1.1.3")
val mockitoVersion by extra ("5.4.0")
