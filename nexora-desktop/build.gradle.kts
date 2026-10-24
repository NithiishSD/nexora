import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

group = "com.nexora"
// The installer version and the "About" screen both read this value.
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Our "lint": any compiler warning fails the build, so warnings cannot pile up.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Skiko native libraries for the operating system that runs the build (Windows -> MSI, Linux -> DEB).
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // Provides Dispatchers.Main on the desktop (the Swing event thread).
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.java.keyring)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.compose.ui.test)
}

// Test-only APIs (virtual time, Compose test rules) are marked experimental by their libraries.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    compilerOptions.optIn.addAll("kotlinx.coroutines.ExperimentalCoroutinesApi", "androidx.compose.ui.test.ExperimentalTestApi")
}

// The About screen reads the version the installer is built with.
tasks.processResources {
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    filesMatching("app.properties") { expand("version" to appVersion) }
}

tasks.test {
    useJUnitPlatform()
    // Compose UI tests render off-screen; no window or display is opened.
    systemProperty("java.awt.headless", "true")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

compose.desktop {
    application {
        mainClass = "com.nexora.desktop.MainKt"
        nativeDistributions {
            // MSI is built on Windows, DEB on Linux (each OS can only build its own installer).
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SmartSilk"
            packageVersion = project.version.toString()
            description = "SmartSilk Business Ledger - Silk yarn trading, inventory, orders, and accounting"
            vendor = "SmartSilk"
            copyright = "Copyright 2026 SmartSilk"
            // jpackage bundles a trimmed Java 21 runtime: only these JDK modules besides the defaults
            // (from ./gradlew suggestRuntimeModules, plus jdk.crypto.ec for HTTPS with elliptic-curve keys).
            modules("java.instrument", "java.management", "jdk.security.auth", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menu = true            // Start-menu entry
                shortcut = true        // desktop shortcut
                dirChooser = true
                perUserInstall = false
                menuGroup = "SmartSilk"
                // Fixed upgrade code: a newer MSI replaces the installed version instead of installing a second copy.
                upgradeUuid = "6f1d2b8e-3c4a-4e5f-9a7b-1c2d3e4f5a6b"
            }
            linux {
                iconFile.set(project.file("packaging/icon.png"))
                shortcut = true
                menuGroup = "Office"
                debMaintainer = "SmartSilk Support <support@smartsilk.com>"
                appCategory = "Office;Finance;Accounting;"
            }
        }
    }
}
