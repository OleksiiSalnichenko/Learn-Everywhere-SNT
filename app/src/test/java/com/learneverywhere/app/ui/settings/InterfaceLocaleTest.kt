package com.learneverywhere.app.ui.settings

import com.learneverywhere.app.data.model.InterfaceLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Критерій приймання тікета 04 (R37): кожна опція мови інтерфейсу мусить
 * мати свій, і рівно свій, локаль — інакше `InterfaceLocaleProvider`
 * (кореневий override `LocalContext`, `InterfaceLocale.kt`) підставить не ту
 * мову екранам, що успадковують її з кореня застосунку.
 */
class InterfaceLocaleTest {

    @Test
    fun `each interface language maps to its own distinct locale`() {
        // Регіони зафіксовані spec.md §«Озвучення» / interfaces.md §voice: uk-UA, en-US, de-DE —
        // ті самі, що й майбутній `InterfaceLanguage.toSpokenLanguage().toLocale()` (тікет 03).
        assertEquals(Locale("uk", "UA"), InterfaceLanguage.UKRAINIAN.toLocale())
        assertEquals(Locale("en", "US"), InterfaceLanguage.ENGLISH.toLocale())
        assertEquals(Locale("de", "DE"), InterfaceLanguage.GERMAN.toLocale())
    }
}
