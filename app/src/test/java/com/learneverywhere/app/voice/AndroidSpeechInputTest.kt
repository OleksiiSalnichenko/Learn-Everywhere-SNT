package com.learneverywhere.app.voice

import androidx.test.core.app.ApplicationProvider
import com.learneverywhere.app.data.model.InterfaceLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Юніт-тест на шов `SpeechInput.listen()` (interfaces.md §voice), критерій
 * приймання тікета 03: "RECORD_AUDIO запитується в рантаймі перед першим
 * використанням мікрофона; відмова показує зрозуміле пояснення, а не тиху
 * відмову". Robolectric-застосунок за замовчуванням не має жодних дозволів
 * (як свіжовстановлений застосунок, що ще не питав дозвіл) — тому цей тест
 * фіксує, що `listen()` у такому стані віддає структурований
 * `Failed(PERMISSION_DENIED)` через Flow, а не кидає `SecurityException`.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidSpeechInputTest {

    @Test
    fun `listen reports permission denied instead of throwing when RECORD_AUDIO is not granted`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val speechInput = AndroidSpeechInput(context, interfaceLanguage = { InterfaceLanguage.UKRAINIAN })

        val result = speechInput.listen().first()

        assertEquals(SpeechResult.Failed(SpeechFailureReason.PERMISSION_DENIED), result)
    }
}
