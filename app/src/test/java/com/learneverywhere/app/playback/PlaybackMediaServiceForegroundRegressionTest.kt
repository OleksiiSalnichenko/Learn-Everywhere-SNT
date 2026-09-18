package com.learneverywhere.app.playback

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith

/**
 * Тікет 13 — регресія `ForegroundServiceDidNotStartInTimeException` (реальний
 * запуск на емуляторі: Play у хедері словника валив увесь застосунок ~15-20с
 * по тому). Причина: `PlaybackMediaService.onCreate()` не викликав
 * `Service.startForeground(...)` сам — покладався на автоматичне просування
 * `MediaSessionService` у foreground (через внутрішній `MediaNotificationManager`,
 * що реагує на зміни стану `Player`), а цей механізм не гарантує виклику в межах
 * системного таймауту (кілька секунд від `startForegroundService`).
 *
 * Цей тест ловить рівно той регрес без реального Android-сервісу/емулятора:
 * Robolectric підміняє `Service.startForeground` на [org.robolectric.shadows.ShadowService],
 * що фіксує факт і аргументи виклику. Навмисно НЕ встановлюємо
 * [PlaybackController.active] перед `onCreate()` — це найгірший випадок
 * (контролер ще не готовий, дані з бази не завантажені) і саме він раніше
 * призводив до крашу, бо `startForeground` чекав на дані. Фікс викликає
 * `startForeground` синхронно першою дією в `onCreate()`, до `MediaSession`,
 * тож він має відбутись і тут, незалежно від стану [PlaybackController.active].
 *
 * Перевірено: на дореформеному коді (без явного `startForeground` у `onCreate`)
 * цей тест падає — `getLastForegroundNotification()` лишається `null`, бо
 * автоматичне просування `MediaSessionService` нічого не викликає без
 * реального `MediaController`, що підключився. VERIFICATION поля репорту
 * покладається саме на цей факт як на непряме, але надійне свідчення регресії.
 */
@RunWith(RobolectricTestRunner::class)
@UnstableApi
class PlaybackMediaServiceForegroundRegressionTest {

    @Test
    fun `onCreate calls startForeground synchronously even without an active controller`() {
        val controller = Robolectric.buildService(PlaybackMediaService::class.java)
        val service = controller.create().get()

        val shadow = shadowOf(service)

        assertNotNull(
            "startForeground(...) мав бути викликаний синхронно в onCreate(), " +
                "незалежно від того, чи готовий PlaybackController.active",
            shadow.lastForegroundNotification,
        )
        assertTrue("id нотифікації має бути стабільним і > 0", shadow.lastForegroundNotificationId > 0)
        assertEquals(false, shadow.isForegroundStopped)

        controller.destroy()
    }
}
