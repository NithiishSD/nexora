package com.nexora.desktop.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TamilStringsTest {

    @AfterEach
    fun resetLocale() {
        Strings.setLocale("en")
    }

    @Test
    @DisplayName("NFR-USE-01 Tamil locale loads authentic Tamil strings with UTF-8 encoding")
    fun tamilLocale_loadsTamilStrings() {
        Strings.setLocale("ta")
        assertEquals("ta", Strings.currentLocale())
        assertEquals("சேமி", s("common.save"))
        assertEquals("ரத்து செய்", s("common.cancel"))
        assertEquals("மீண்டும் முயற்சி", s("common.retry"))
        assertEquals("ஸ்மார்ட் சில்க்", s("app.title"))
        assertEquals("கச்சா பட்டு", s("product.RAW_SILK"))
        assertEquals("வழங்கப்பட்டது", s("enum.OrderStatus.DELIVERED"))
    }

    @Test
    @DisplayName("NFR-USE-01 Tamil locale formats arguments correctly")
    fun tamilLocale_formatsArguments() {
        Strings.setLocale("ta")
        assertEquals("சேமிக்கப்பட்டது இன்று", s("common.saved", "இன்று"))
        assertEquals(
            "ஏதோ தவறு நடந்துவிட்டது (ERR-500). தயவுசெய்து மீண்டும் முயற்சிக்கவும்.",
            s("error.unknown", "ERR-500"),
        )
    }

    @Test
    @DisplayName("NFR-USE-01 Tamil locale falls back to English when key is not in Tamil bundle")
    fun tamilLocale_fallsBackToEnglish() {
        Strings.setLocale("ta")
        // setup.title is in English bundle but omitted from minimal Tamil bundle
        assertEquals("Connect to the SmartSilk server", s("setup.title"))
    }

    @Test
    @DisplayName("NFR-USE-01 switching locales toggles active translations cleanly")
    fun switchingLocales_togglesCleanly() {
        Strings.setLocale("en")
        assertEquals("Save", s("common.save"))

        Strings.setLocale("ta")
        assertEquals("சேமி", s("common.save"))

        Strings.setLocale("en")
        assertEquals("Save", s("common.save"))

        assertTrue(Strings.availableLocales().containsAll(listOf("en", "ta")))
    }
}
