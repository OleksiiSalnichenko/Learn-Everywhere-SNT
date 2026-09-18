package com.learneverywhere.app.ui.settings

import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.learneverywhere.app.MainActivity
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Тікет 11 — регресія, знайдена сліпим прийманням реальним запуском на
 * емуляторі: `InterfaceLocaleProvider` підміняв `LocalContext` на голий
 * `Context` (не `ContextWrapper` навколо `Activity`), тому будь-який
 * `rememberLauncherForActivityResult` нижче по дереву композиції
 * (мікрофонний permission-launcher у `HomeScreen.kt:111`, файловий пікер у
 * `DictionariesScreen.kt:115`) падав з `IllegalStateException: No
 * ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner`
 * одразу при першій композиції — саме той стек, що й у крашу на пристрої.
 *
 * ГЛУХИЙ КУТ (щоб наступник не повторював): перша версія цього тесту
 * реально рендерила продакшн `HomeScreen()`/`DictionariesScreen()` (з їхніми
 * `ViewModel`, `AppContainer`, Room/DataStore/TTS/SpeechRecognizer, у
 * `HomeScreen` ще й нескінченна `rememberInfiniteTransition` пульсація
 * мікрофону) під `Robolectric.buildActivity(MainActivity::class.java)`.
 * Це виявилось нестабільним і подекуди зависало на кілька хвилин під повним
 * прогоном сюїти (важка DI-обв'язка + нескінченна анімація + взаємодія
 * Recomposer/Choreographer між послідовними тестами) — ризик, що
 * `./gradlew testDebugUnitTest` стає непередбачувано повільним чи висне,
 * неприйнятний для критерію приймання "весь набір зелений". Замінено на
 * мінімальний probe нижче, що відтворює РІВНО той самий механізм поломки
 * (`rememberLauncherForActivityResult` під `InterfaceLocaleProvider` на
 * реальній `Activity`) без важкої обв'язки — швидко, детерміновано,
 * без анімацій.
 *
 * Перевірено: на старому коді (голий `base.createConfigurationContext(...)`
 * як `LocalContext`) цей тест ловить `IllegalStateException: No
 * ActivityResultRegistryOwner...`; на фіксі
 * (`ActivityAwareConfigurationContext`) — проходить.
 */
@RunWith(RobolectricTestRunner::class)
class InterfaceLocaleActivityResultRegressionTest {

    @Test
    fun `rememberLauncherForActivityResult under InterfaceLocaleProvider does not crash on ActivityResultRegistryOwner lookup`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        var composed = false
        var caught: Throwable? = null
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> caught = throwable }

        try {
            activity.setContent {
                InterfaceLocaleProvider {
                    SideEffect { composed = true }
                    ActivityResultLauncherProbe()
                }
            }

            // Recomposer-корутина Compose-у виконується на Choreographer-кадрах —
            // голого idle() недостатньо (кадр запланований трохи в майбутньому
            // відносно віртуального часу Robolectric), тому проганяємо кадри,
            // поки композиція реально не відбудеться (маркер `composed`) або не
            // впаде (маркер `caught`).
            var framesLeft = 50
            while (!composed && caught == null && framesLeft > 0) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                framesLeft--
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            // Без явного знищення Activity/композиції глобальний
            // `AndroidUiDispatcher` (прив'язаний до симульованого головного
            // потоку, спільного для всіх тестів у цьому прогоні Robolectric)
            // лишає своє внутрішнє "кадр заплановано" в неконсистентному стані
            // — наступний тест у сюїті інакше ніколи не отримує свій
            // Choreographer-кадр.
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }

        caught?.let { throw AssertionError("Composition under InterfaceLocaleProvider crashed", it) }
        assertTrue("composition never ran — test would pass vacuously", composed)
    }
}

/**
 * Той самий шов, що падав у продакшні (`HomeScreen.kt:111`,
 * `DictionariesScreen.kt:115`): `rememberLauncherForActivityResult`,
 * викликаний під час першої композиції, одразу читає
 * `LocalActivityResultRegistryOwner.current`.
 */
@Composable
private fun ActivityResultLauncherProbe() {
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
}
