package com.learneverywhere.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
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
 *
 * Фікс тікета 11: `base.createConfigurationContext(...)` сам собою повертає
 * голий `Context`, не `ContextWrapper` навколо `Activity` — підмінивши ним
 * `LocalContext` напряму, попередня версія ламала будь-який
 * `rememberLauncherForActivityResult`/`LocalOnBackPressedDispatcherOwner`
 * нижче по дереву (їх делегати шукають `ActivityResultRegistryOwner` тощо
 * через `Context`, яких у голого `ContextWrapper` немає — `IllegalStateException
 * No ActivityResultRegistryOwner...`). Тепер конфігураційний контекст
 * обгортається в `ActivityAwareConfigurationContext`, що делегує
 * Activity-специфічні інтерфейси до реальної `Activity`, знайденої
 * розгортанням ланцюжка `ContextWrapper` від оригінального (не
 * локалізованого) контексту — так `LocalContext` і далі віддає локалізовані
 * рядки/ресурси, а `rememberLauncherForActivityResult` та інші делегати
 * резолвляться до справжньої `Activity`.
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
        val configurationContext = base.createConfigurationContext(configuration)
        val activity = base.findActivity()
        if (activity == null) {
            // Немає Activity в ланцюжку (напр. preview) — поведінка як і
            // раніше, без делегатів; жоден Activity-залежний CompositionLocal
            // тут не потрібен був би.
            configurationContext
        } else {
            ActivityAwareConfigurationContext(configurationContext, activity)
        }
    }

    CompositionLocalProvider(LocalContext provides localizedContext, content = content)
}

/**
 * Розгортає ланцюжок `ContextWrapper`, шукаючи оригінальну `Activity` —
 * саме так резолвляться `LocalActivityResultRegistryOwner` тощо у продакшн-коді.
 */
private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

/**
 * `ContextWrapper` навколо конфігураційного (локалізованого) контексту, що
 * делегує `getResources()`/`getAssets()`/`getTheme()` йому (щоб рядки й далі
 * читались локалізованими), а Activity-специфічні `CompositionLocal`-делегати
 * (`ActivityResultRegistryOwner`, `SavedStateRegistryOwner`, `LifecycleOwner`,
 * `OnBackPressedDispatcherOwner`, `ViewModelStoreOwner`) — до реальної
 * `Activity`, знайденої `findActivity()`. Без цього делегування
 * `rememberLauncherForActivityResult` і подібні кидають `IllegalStateException`,
 * бо голий сконфігурований `Context` цих інтерфейсів не реалізує.
 */
private class ActivityAwareConfigurationContext(
    configurationContext: Context,
    private val activity: Activity,
) : ContextWrapper(configurationContext),
    ActivityResultRegistryOwner,
    SavedStateRegistryOwner,
    LifecycleOwner,
    OnBackPressedDispatcherOwner,
    ViewModelStoreOwner {

    override val activityResultRegistry: ActivityResultRegistry
        get() = (activity as ActivityResultRegistryOwner).activityResultRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = (activity as SavedStateRegistryOwner).savedStateRegistry

    override val lifecycle: Lifecycle
        get() = (activity as LifecycleOwner).lifecycle

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = (activity as OnBackPressedDispatcherOwner).onBackPressedDispatcher

    override val viewModelStore: ViewModelStore
        get() = (activity as ViewModelStoreOwner).viewModelStore
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
