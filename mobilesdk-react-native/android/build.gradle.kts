plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.c4f.mobileSDK.reactnative"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // ← Change to 17
        targetCompatibility = JavaVersion.VERSION_17 // ← Change to 17
    }

    kotlinOptions {
        jvmTarget = "17" // ← Change to 17 to match Java
    }

    lint {
        checkReleaseBuilds = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

// ADD THIS - Modern Kotlin toolchain approach
kotlin {
    jvmToolchain(17) // ← Add this for proper toolchain setup
}

dependencies {
    api("com.facebook.react:react-android:0.72.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    // IF YOU MADE ANY CHANGES WTIH CORE MODULES, USE PROJECT IMPLEMENTATION FOR JITPACK BUILD AND COMMEND OUT LATER FOR NPM RELEASE

    //implementation(project(":mobileSDK"))
    // USE TAG NUMBER FOR NPM RELEASE
    implementation("com.github.cloud4feed:c4f_mobile_sdk:v2.0.16")
    // WHILE YOU DONT HAVE TAG NUMBER
    // implementation("com.github.cloud4feed:c4f_mobile_sdk:main-SNAPSHOT")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.cloud4feed"
                artifactId = "mobilesdk-react-native"
                version = "1.2.17"

                pom {
                    name.set("Survey SDK React Native")
                    description.set("React Native bridge for Survey SDK")
                    url.set("https://github.com/cloud4feed/c4f_mobile_sdk")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("cloud4feed")
                            name.set("Cloud4Feed")
                        }
                    }
                }
            }
        }
    }
}
