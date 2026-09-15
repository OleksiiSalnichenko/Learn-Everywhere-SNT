package com.learneverywhere.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Критерій приймання тікета 04: "числові налаштування (повтори, паузи)
 * обираються зі списку варіантів 1-6 секунд, не довільним вводом" —
 * фіксує сам список, яким живиться дропдаун у `SettingsScreen`.
 */
class SettingsOptionsTest {

    @Test
    fun `seconds options are exactly 1 through 6`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), SettingsOptions.SECONDS_OPTIONS)
    }
}
