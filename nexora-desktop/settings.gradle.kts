// The desktop client is its own Gradle build, separate from Nexora-backend (ADR-035).
rootProject.name = "nexora-desktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform pulls some androidx artifacts from Google's repository.
        google()
    }
}
