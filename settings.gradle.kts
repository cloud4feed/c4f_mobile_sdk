pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "c4f_mobile_sdk"

// CORRECT PATHS for your structure
include(":mobileSDK")
project(":mobileSDK").projectDir = file("mobileSDK")

include(":mobilesdk-react-native")
project(":mobilesdk-react-native").projectDir = file("mobilesdk-react-native/android")
