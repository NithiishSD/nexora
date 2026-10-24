package com.nexora.desktop.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

object LocalEngineManager {

    private const val LOCAL_HEALTH_URL = "http://localhost:8088/actuator/health"
    private var engineProcess: Process? = null

    init {
        // Ensure the engine is cleanly shut down when the desktop app is closed
        Runtime.getRuntime().addShutdownHook(Thread {
            stopEngine()
        })
    }

    /**
     * Checks if the local engine is already up and healthy on port 8088.
     */
    suspend fun isEngineRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = URI(LOCAL_HEALTH_URL)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds the backend jar file. Checks production installed locations as well as
     * development directories.
     */
    fun findBackendJar(): File? {
        // 1. Compose application resources directory (set in production native distributions)
        val composeResourcesDir = System.getProperty("compose.application.resources.dir")
        if (!composeResourcesDir.isNullOrBlank()) {
            val file = File(composeResourcesDir, "backend-engine.jar")
            if (file.exists()) return file
        }

        // 2. Relative to application installation root (Windows MSI / Linux DEB)
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val parent = File(javaHome).parentFile
            if (parent != null) {
                listOf(
                    File(parent, "app/resources/backend-engine.jar"),
                    File(parent, "lib/app/resources/backend-engine.jar"),
                    File(parent, "resources/backend-engine.jar"),
                    File(parent, "app/backend-engine.jar")
                ).firstOrNull { it.exists() }?.let { return it }
            }
        }

        // 3. Working directory resources
        listOf(
            File("resources/backend-engine.jar"),
            File("extra-resources/backend-engine.jar"),
            File("packaging/backend-engine.jar"),
            File("../Nexora-backend/build/libs/app.jar"),
            File("Nexora-backend/build/libs/app.jar")
        ).firstOrNull { it.exists() }?.let { return it }

        return null
    }

    /**
     * Locates the Java executable (uses bundled JRE in production).
     */
    private fun findJavaBinary(): String {
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val isWin = System.getProperty("os.name", "").lowercase().contains("win")
            val binName = if (isWin) "java.exe" else "java"
            val bin = File(javaHome, "bin/$binName")
            if (bin.exists() && bin.canExecute()) {
                return bin.absolutePath
            }
        }
        return "java"
    }

    /**
     * Starts the embedded local engine if it is not already running.
     * Polls until healthy or until timeout.
     */
    suspend fun ensureEngineStarted(onProgress: (String) -> Unit): Boolean {
        if (isEngineRunning()) {
            return true
        }

        val jar = findBackendJar()
        if (jar == null) {
            onProgress("Backend engine archive not found. Make sure backend is packaged or running.")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                onProgress("Starting local SmartSilk business engine...")
                val javaBin = findJavaBinary()
                val pb = ProcessBuilder(
                    javaBin,
                    "-jar",
                    jar.absolutePath,
                    "--spring.profiles.active=standalone",
                    "--server.port=8088"
                )
                pb.redirectErrorStream(true)
                engineProcess = pb.start()

                // Wait for up to 35 seconds for the engine to initialize and report healthy
                val startTime = System.currentTimeMillis()
                val timeoutMs = 35_000L

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (isEngineRunning()) {
                        onProgress("Local engine ready.")
                        return@withContext true
                    }
                    if (engineProcess?.isAlive == false) {
                        onProgress("Local engine process exited unexpectedly.")
                        return@withContext false
                    }
                    delay(800)
                }

                onProgress("Timed out waiting for local engine to start.")
                false
            } catch (e: Exception) {
                onProgress("Failed to start local engine: ${e.message}")
                false
            }
        }
    }

    /**
     * Stops the running local engine process cleanly.
     */
    fun stopEngine() {
        try {
            engineProcess?.let {
                if (it.isAlive) {
                    it.destroy()
                    it.waitFor(java.util.concurrent.TimeUnit.SECONDS.toMillis(5), java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (it.isAlive) {
                        it.destroyForcibly()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            engineProcess = null
        }
    }
}
