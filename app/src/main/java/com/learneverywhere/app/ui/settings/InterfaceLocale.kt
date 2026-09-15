package com.learneverywhere.app.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.learneverywhere.app.LearnEverywhereApplication
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Піднято на корінь застосунку за рев'ю R37 (рух дозапиту): "мова інтерфейсу
 * має діяти на рівні всього застосунку, а не лише всередині SettingsScreen".
 * Обгортає `NavHost` у `LearnEverywhereNavHost.kt` — тому наступні тікети
 * (05, 06, 07), заповнюючи заглушки реальним вмістом, успадковують поточну
 * `settings.interfaceLanguage` автоматично, без повторної реалізації
 * локалізації на кожному екрані. Рядки й далі беруться з ресурсів
 * strings.xml (values, values-en, values-de) — тут лише підміняється
 * контекст, яким `stringResource()` їх читає.
 */
@Composable
fun InterfaceLocaleProvider(
    settingsRepository: SettingsRepository =
        (LocalContext.current.applicationContext as LearnEverywhereApplication).container.settingsRepository,
    content: @Composable () -> Unit,
) {
    val interfaceLanguage by settingsRepository.settings
        .map { it.interfaceLanguage }
        .distinctUntilChanged()
        .collectAsState(initial = AppSettings().interfaceLanguage)

    val base = LocalContext.current
    val localizedContext = remember(base, interfaceLanguage) {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(interfaceLanguage.toLocale())
        base.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(LocalContext provides localizedContext, content = content)
}

/**
 * Тимчасове дублювання (рев'ю, вісь craft): за задумом interfaces.md §voice
 * (тікет 03) це мало бути `InterfaceLanguage.toSpokenLanguage().toLocale()`,
 * але `voice`-модуль ще не змержений у цю гілку — там ще немає ні
 * `SpokenLanguage`, ні цих функцій. Коли тікет 03 приземлиться в `develop` —
 * замінити тіло цієї функції на той ланцюжок і прибрати дублювання.
 */
internal fun InterfaceLanguage.toLocale(): Locale = when (this) {
    // Регіони — точно за spec.md §«Озвучення» / interfaces.md §voice (тікет 03):
    // uk-UA, en-US, de-DE. Тимчасова функція мусить повертати ті самі локалі,
    // що й майбутній `toSpokenLanguage().toLocale()`, а не лише мову без регіону.
    InterfaceLanguage.UKRAINIAN -> Locale("uk", "UA")
    InterfaceLanguage.ENGLISH -> Locale("en", "US")
    InterfaceLanguage.GERMAN -> Locale("de", "DE")
}
