package `in`.daasavaani.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreferencesTest {
    @Test fun supportedOrderedDeviceLanguagesAndFallback() {
        assertEquals("kn", uiLanguage(listOf("fr-FR", "kn-IN", "en-GB")))
        assertEquals("en", uiLanguage(listOf("en-IN", "kn-IN")))
        assertEquals("en", uiLanguage(listOf("fr", "de")))
        assertEquals("en", uiLanguage(emptyList()))
        assertEquals("kn", uiLanguage(listOf("KN-in")))
    }
    @Test fun explicitOverrideIsIndependentOfDevice() {
        assertEquals("en", uiLanguage(listOf("kn"), "en"))
        assertEquals("kn", uiLanguage(listOf("en"), "kn"))
        assertFailsWith<IllegalArgumentException> { uiLanguage(listOf("kn"), "unsupported") }
    }
}
