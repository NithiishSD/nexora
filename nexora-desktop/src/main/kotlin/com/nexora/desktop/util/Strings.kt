package com.nexora.desktop.util

import java.util.Properties

/**
 * Every text the user sees comes from `strings.properties` (06 rule P7), with Tamil support
 * in `strings_ta.properties`. `s(key, arg0, arg1)` replaces `{0}`, `{1}` in the text with the arguments.
 */
object Strings {
    private const val DEFAULT_LOCALE = "en"
    private var currentLang: String = DEFAULT_LOCALE

    private val baseTable: Properties = Properties().apply {
        // Read as UTF-8 so ₹ and, later, Tamil letters survive (Properties.load(InputStream) assumes ISO-8859-1).
        Strings::class.java.getResourceAsStream("/strings.properties")?.reader(Charsets.UTF_8)?.use { load(it) }
    }

    private var activeTable: Properties? = null

    fun setLocale(lang: String) {
        currentLang = lang.lowercase().trim()
        if (currentLang == DEFAULT_LOCALE || currentLang.isEmpty()) {
            activeTable = null
            return
        }
        val stream = Strings::class.java.getResourceAsStream("/strings_$currentLang.properties")
        activeTable = if (stream != null) {
            Properties().apply {
                stream.reader(Charsets.UTF_8).use { load(it) }
            }
        } else {
            null
        }
    }

    fun currentLocale(): String = currentLang

    fun availableLocales(): List<String> = listOf("en", "ta")

    fun has(key: String): Boolean = (activeTable?.containsKey(key) == true) || baseTable.containsKey(key)

    fun getOrNull(key: String, vararg args: Any?): String? {
        val raw = activeTable?.getProperty(key) ?: baseTable.getProperty(key)
        return raw?.let { format(it, args) }
    }

    /** A missing key shows the key itself, which is easy to spot; StringsTest fails the build on it. */
    fun get(key: String, vararg args: Any?): String = getOrNull(key, *args) ?: key

    private fun format(text: String, args: Array<out Any?>): String =
        args.foldIndexed(text) { index, acc, arg -> acc.replace("{$index}", arg.toString()) }
}

/** Short form used by the screens: s(key) with a key from strings.properties. */
fun s(key: String, vararg args: Any?): String = Strings.get(key, *args)
