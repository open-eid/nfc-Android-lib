pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NFC-Android library and demo app"

include(":demoapp")
include(":demoapp:app")
include(":demoapp:libdigidocpp")
include(":libs")
include(":libs:card-utils-lib")
include(":libs:id-card-lib")
include(":libs:smart-card-reader-lib")