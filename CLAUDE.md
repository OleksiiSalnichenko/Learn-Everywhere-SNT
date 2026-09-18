<!-- autopilot:start -->
# Learn Everywhere

Android-застосунок для вивчення слів на слух: диктуєш слово, система перекладає
і додає в словник (німецький або англійський), потім програє слова вголос.

## Команди

Оточення (задай перед першою gradle-командою в новій сесії/worktree):
```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/asaln/AppData/Local/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```
`local.properties` — у `.gitignore`, генеруй заново якщо відсутній:
`sdk.dir=C:\\Users\\asaln\\AppData\\Local\\Android\\Sdk`

Тести (JUnit4 + Robolectric, без емулятора):
```
./gradlew testDebugUnitTest
```
Один клас:
```
./gradlew testDebugUnitTest --tests "com.learneverywhere.app.playback.PlaybackEngineTest"
```
Збірка debug APK:
```
./gradlew assembleDebug
```
Обидві команди перевірено щойно з нуля (свіжий worktree, згенерований `local.properties`):
`testDebugUnitTest` — 72/72 зелено, `assembleDebug` — успішно. Встановлення на
пристрій/емулятор і інструментальні (androidTest) тести не перевірялись — такого
раннера в проєкті немає.

## Структура

```
app/src/main/java/com/learneverywhere/app/
├── data/             # Room (db/), моделі (model/), DictionaryRepository + JSON імпорт/експорт
├── translation/      # TranslationService — клієнт MyMemory (без ключа)
├── voice/            # SpeechInput / Speaker / LanguageDetector — розпізнавання й TTS
├── playback/         # PlaybackEngine (чиста FSM) + PlaybackController + PlaybackMediaService
├── settings/         # SettingsRepository поверх DataStore
├── ui/               # home/ dictionaries/ settings/ main/ navigation/ splash/ theme/
├── di/AppContainer.kt
├── LearnEverywhereApplication.kt, MainActivity.kt
app/src/test/java/...   # дзеркалить main/ за пакетами
app/src/main/res/values{,-en,-de}/strings.xml   # локалізація uk/en/de
```

## Ключові файли

- `di/AppContainer.kt` — сервіс-локатор, тримає всі репозиторії/сервіси
- `LearnEverywhereApplication.kt` — власник `AppContainer`, створює дефолтні словники на старті
- `ui/navigation/LearnEverywhereNavHost.kt` — маршрути, обгорнуті `InterfaceLocaleProvider`
- `ui/home/HomeViewModel.kt` — захоплення слова (диктовка → переклад → словник)
- `ui/dictionaries/DictionariesViewModel.kt` — список/деталі словників, імпорт/експорт, плей-хедер
- `playback/PlaybackEngine.kt` — чиста FSM черги програвання, найважливіший тестовий шов
- `playback/PlaybackController.kt` — рушій + TTS + міст до Android-сервісу
- `data/repository/DictionaryExportProvider.kt` — власний `ContentProvider` замість `FileProvider`

## Архітектура

- DI — `AppContainer(context)`, сервіс-локатор, без Hilt/Dagger. Кожен composable-екран
  дістає його як `(LocalContext.current.applicationContext as LearnEverywhereApplication).container`
  і сам конструює ViewModel через `remember { ... }`. `PlaybackController` — поза
  `AppContainer`, конструюється в `DictionariesScreen` через
  `remember { PlaybackController.active ?: PlaybackController(...) }`.
- Захоплення слова: `HomeScreen` → `HomeViewModel` → `voice.SpeechInput.listen()` /
  `LanguageDetector.detect` → `translation.TranslationService.translate` (два виклики:
  слово→укр. і укр.→мова словника, для example) → `data.DictionaryRepository.addWord`.
- Програвання: `DictionariesScreen` → `PlaybackController.start(dictId)` читає
  `DictionaryRepository.words()` + `SettingsRepository.settings`, будує `PlaybackEngine`
  (чиста FSM фаз) і виконує кожну фазу через `voice.Speaker.speak`.
  `PlaybackMediaService` — тонка Android-обгортка (foreground-сервіс, `MediaSession`),
  керує тим самим інстансом через `PlaybackController.active`.
- Локалізація інтерфейсу: `InterfaceLocaleProvider` (в `ui/settings/InterfaceLocale.kt`)
  обгортає весь `NavHost`, підміняючи `LocalContext` за `settings.interfaceLanguage` —
  усі екрани успадковують поточну мову автоматично. Підміна йде через
  `ActivityAwareConfigurationContext` (приватний `ContextWrapper`), що делегує
  `ActivityResultRegistryOwner`/`SavedStateRegistryOwner`/`LifecycleOwner`/
  `OnBackPressedDispatcherOwner`/`ViewModelStoreOwner` до реальної `Activity`
  (знайденої через `Context.findActivity()`) — без цього `rememberLauncherForActivityResult`
  нижче по дереву (мікрофон, файловий пікер) падає, див. Підводні камені.
- Межі модулів (`data`/`translation`/`voice`/`playback`/`settings`/`ui`) і їхні публічні
  контракти зафіксовані в `.autopilot/2026-09-14-learn-everywhere--wip/interfaces.md`.

## Угоди коду

- ViewModel-класи (`DictionariesViewModel`, `HomeViewModel`) — звичайні класи, НЕ
  `androidx.lifecycle.ViewModel` (свідомий компроміс: не тягнути `lifecycle-viewmodel-compose`;
  стан не переживає поворот екрана).
- DI лише через `AppContainer`; новий сервіс — нове поле там, споживачі дістають його з
  `Application`, не заводь Hilt/Dagger.
- Усі видимі користувачу рядки — у `res/values/strings.xml` + `values-en` + `values-de`,
  жодного хардкоду тексту в composable.
- `PlaybackController`/`PlaybackEngine` — найважливіший тестовий шов проєкту; зміни в
  чергу/паузи/shuffle покривай тестами в `PlaybackEngineTest` першими.
- `pause()` діє лише на межі фаз — `Speaker` не вміє перервати вислів, що вже почався
  (задокументований компроміс, не баг).

## Оточення

- `JAVA_HOME` — JBR з Android Studio (Gradle/AGP 9.4.0 і Robolectric-тести на JDK 17+
  потребують саме його, не системний JDK).
- `ANDROID_HOME` — Android SDK, потрібен для compileSdk/targetSdk=37 і `platform-tools` (adb).

## Підводні камені

- `InterfaceLocaleProvider` не можна підміняти на голий `createConfigurationContext(...)`
  без обгортки — `rememberLauncherForActivityResult` (мікрофон, файловий пікер) шукає
  `ActivityResultRegistryOwner` через ланцюжок `ContextWrapper`, і голий `Context` його не
  має (`IllegalStateException: No ActivityResultRegistryOwner`, впіймано лише реальним
  запуском на емуляторі — юніт-тести на in-memory ViewModel цього не ловлять). Регресія на
  цей конкретний шов покрита `InterfaceLocaleActivityResultRegressionTest.kt` — якщо
  чіпаєш `InterfaceLocale.kt`, прожени саме цей тест.
- `androidx.core:core-ktx` 1.19.0: `FileProvider.SimplePathStrategy` хардкодить `/` і падає
  під Robolectric на Windows (`IllegalArgumentException: Failed to find configured root`)
  навіть на валідному шляху — тому `DictionaryExportProvider` є власною реалізацією, не
  `androidx.core.content.FileProvider`; стандартний `FileProvider` лишився в маніфесті лише
  заради тесту, що документує баг.
- `local.properties` не комітиться (`.gitignore`) — генеруй заново в кожній новій
  сесії/worktree, інакше `gradlew` не бачить SDK.
- `compileSdk`/`targetSdk` = 37, не 36 — цього вимагають версії з `libs.versions.toml`
  (`core-ktx` 1.19, `compose-bom` 2026.08.00 та ін.).
- Плагін `kotlin-android` навмисно прибрано з `build.gradle.kts` — AGP 9.4.0 має вбудовану
  підтримку Kotlin, повторне додавання плагіна з нею конфліктує.
- `translation.TranslationService` ходить у MyMemory без API-ключа (публічний ліміт) —
  мережева помилка/таймаут мапляться в `TranslationUnavailableException`.

## Тести

- JUnit4 + Robolectric (`isIncludeAndroidResources = true`) — `app/src/test` дзеркалить
  `app/src/main` за пакетами, 73 тести, усі зелені. Більшість — на рівні ViewModel/логіки;
  `InterfaceLocaleActivityResultRegressionTest.kt` — єдиний, що реально рендерить
  Compose UI на справжній `Activity` (мінімальний пробник, не повний екран).
- Публічні межі-шви для фейків: `DictionaryRepository`, `TranslationService`,
  `PlaybackController`/`PlaybackActions`, `SettingsRepository` — див.
  `ui/dictionaries/FakeRepositories.kt`, `ui/home/FakeHomeDependencies.kt`.
- Один файл: `./gradlew testDebugUnitTest --tests "com.learneverywhere.app.ПакетКлас"`.

## Як тут працює Autopilot

Збірку веде навичка `/autopilot`. Вимоги, специфікація і таски — в `.autopilot/`.
Прогрес — `.autopilot/dashboard.html`. Правило: вимогу з `manifest.md`
може зняти тільки користувач.

Якщо робота триває — скажи «продовж автопілот»: стан підніметься
з `.autopilot/state.js`, перепитувати нічого не потрібно.
<!-- autopilot:end -->
