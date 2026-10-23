package com.nexora.desktop.util

import com.nexora.desktop.api.ApiJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/** Position and size of the main window, restored at the next start (06 section 2). */
@Serializable
data class WindowSettings(val x: Int? = null, val y: Int? = null, val width: Int = 1366, val height: Int = 768, val maximized: Boolean = false)

/**
 * Everything the app remembers between starts for this OS user. Deliberately no tokens and no passwords:
 * the refresh token lives only in the OS credential store (09-SECURITY, NFR-SEC-06).
 */
@Serializable
data class AppSettings(
    val serverUrl: String? = null,
    val lastUsername: String? = null,
    val window: WindowSettings = WindowSettings(),
    /** 1.0 = 100 %, up to 2.0 = 200 % (NFR-USE-06). */
    val textScale: Float = 1.0f,
    val sidebarCollapsed: Boolean = false,
    val darkMode: Boolean = false,
    /** Last choices on this computer, e.g. "payment.method" -> "UPI", "payment.account.UPI" -> account id. */
    val lastUsed: Map<String, String> = emptyMap(),
)

/** Reads and writes `settings.json` in the OS application-data folder. */
class SettingsStore(val file: Path = defaultFile()) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        _settings.value = next
        save(next)
    }

    fun toggleDarkMode() = update { it.copy(darkMode = !it.darkMode) }

    fun lastUsed(key: String): String? = current.lastUsed[key]

    fun remember(key: String, value: String) = update { it.copy(lastUsed = it.lastUsed + (key to value)) }

    private fun load(): AppSettings = try {
        if (Files.exists(file)) ApiJson.decodeFromString(AppSettings.serializer(), Files.readString(file)) else AppSettings()
    } catch (_: SerializationException) {
        // A damaged file must not stop the app from starting; the user just sets the server again.
        AppSettings()
    } catch (_: IllegalArgumentException) {
        AppSettings()
    }

    private fun save(settings: AppSettings) {
        Files.createDirectories(file.parent)
        // Write to a temporary file and move it over the old one, so a crash mid-write never leaves half a file.
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, ApiJson.encodeToString(AppSettings.serializer(), settings))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        /** `%APPDATA%\SmartSilk\settings.json` on Windows, `~/.config/smartsilk/settings.json` on Linux. */
        fun defaultFile(): Path {
            val appData = System.getenv("APPDATA")
            return if (System.getProperty("os.name").startsWith("Windows") && appData != null) {
                Paths.get(appData, "SmartSilk", "settings.json")
            } else {
                Paths.get(System.getProperty("user.home"), ".config", "smartsilk", "settings.json")
            }
        }
    }
}
