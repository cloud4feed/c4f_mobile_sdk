plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false  // ← Make sure this is 1.9.0
}

allprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"  // ← Change to 17 here too
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}