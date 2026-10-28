// Top-level build file. Plugin versions are declared here once (apply false) and applied in the modules.
// AGP 9+ has Kotlin built in, so only the Compose compiler plugin is needed next to it.
buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // Pins the Kotlin Gradle plugin used by AGP's built-in Kotlin (newer than the one bundled with AGP).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
