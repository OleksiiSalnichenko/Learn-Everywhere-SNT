# Межі та контракти — Learn Everywhere

## Правила проєкту (для кожного субагента)

- **Стек:** Kotlin, Jetpack Compose, Material 3 (лише як бібліотека компонентів — власний
  візуальний стиль, див. `spec.md` §«Стек»). Мінімальна версія — API 26.
- **Збірка:** Gradle (Kotlin DSL), стандартна структура Android-проєкту `app/`.
- **Гілка:** усі готові тікети коммітяться в `develop` (вимога R63). Один тікет — один комміт
  (або кілька логічних коммітів одним тікетом), з повідомленням, що коротко каже, що зроблено.
- **Тести:** `./gradlew testDebugUnitTest` — юніт-тести на публічних межах нижче. Кожен тікет
  додає тести на свій шов; не потрібен окремий інструментальний (UI) тест-раннер на цьому етапі.
- **Java/Android SDK/Gradle — Є в цьому оточенні, просто не на PATH.** Перш ніж повертати
  `BLOCKED` через "немає Java/Gradle/SDK" — встанови змінні середовища нижче, це майже
  напевно вирішує проблему:
  ```
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  export ANDROID_HOME="/c/Users/asaln/AppData/Local/Android/Sdk"
  export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
  ```
  Якщо `gradlew`/`gradlew.bat` ще немає (перший тікет) — згенеруй його наявним Gradle
  9.7.1 з локального кешу дистрибутивів, не завантажуючи нічого з мережі:
  `"/c/Users/asaln/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle" wrapper --gradle-version 9.7.1`
  (запусти з коренем `JAVA_HOME`, як вище). Після цього користуйся звичайним `./gradlew`.
  `local.properties` з `sdk.dir=C:\\Users\\asaln\\AppData\\Local\\Android\\Sdk` — файл у
  `.gitignore`, створюй його заново в кожному новому тікеті, якщо відсутній.
  **Якщо після цього все одно не працює** — тоді дійсно `BLOCKED`, з точним описом помилки.
- **Не займай:** файли поза `app/` (окрім `settings.gradle.kts`, `build.gradle.kts` кореня) і
  файли `.autopilot/`.
- **DI:** через `AppContainer` (простий сервіс-локатор, тікет 01) — жодних Hilt/Dagger анотацій.
- **Локалізація:** усі видимі користувачу рядки — в `res/values/strings.xml`,
  `res/values-en/strings.xml`, `res/values-de/strings.xml`. Жодного хардкоду тексту в composable.

## Межі, вирішені в специфікації

| Модуль | Володіє | Виставляє | Ховає |
|---|---|---|---|
| `data` | Room-база, сутності словників і слів, JSON імпорт/експорт | `DictionaryRepository`: `dictionaries(lang): Flow<List<Dictionary>>`, `defaultDictionary(lang): Flow<Dictionary?>`, `createDictionary(lang, name): Dictionary`, `setDefault(id)`, `rename(id, name)`, `deleteDictionary(id)`, `words(dictId): Flow<List<WordEntry>>`, `addWord(...)`, `updateWord(...)`, `deleteWord(id)`, `exportToJson(dictId): Uri`, `importFromJson(uri, lang): Dictionary` | схему таблиць, формат JSON-файлу |
| `translation` | звернення до MyMemory | `TranslationService.translate(word, sourceLang, targetLang): TranslationResult(candidates: List<String>, example: String, isExampleGenerated: Boolean)` | HTTP-деталі, парсинг відповіді, шаблони фраз-заглушок |
| `voice` | розпізнавання мовлення, озвучення, евристика визначення мови | `SpeechInput.listen(): Flow<SpeechResult>`, `LanguageDetector.detect(text): SpokenLanguage`, `Speaker.speak(text, locale, onDone)` | доступ до `SpeechRecognizer`/`TextToSpeech`, список службових слів |
| `playback` | стан і чергу програвання | `PlaybackController.start(dictId)`, `.pause()`, `.resume()`, `.stop()`, `.skipNext()`, `currentWord: StateFlow<WordEntry?>`, `isPlaying: StateFlow<Boolean>` | конечний автомат фаз, медіа-сесію, foreground-сервіс |
| `settings` | налаштування застосунку | `SettingsRepository.settings: Flow<AppSettings>`, `.update(transform)`, `.resetToDefaults()` | ключі DataStore |
| `ui` | екрани Compose, навігація | — (кінцевий споживач інших модулів) | внутрішню структуру композиблів |

Шви для тестів — ті самі публічні межі: `DictionaryRepository`, `TranslationService`,
`PlaybackController`, `SettingsRepository`. Найважливіший шов — `PlaybackController`
(найбільше логіки: черга, повтори, shuffle, пауза).

## Дані (з `spec.md` §«Модель даних»)

```
DictionaryEntity(id, language: GERMAN|ENGLISH, name, isDefault)
WordEntryEntity(id, dictionaryId, ukrainian, translation1, translation2?, example, isExampleGenerated)
AppSettings(mainLanguage, interfaceLanguage, loop, shuffle,
            ukrainianRepeatCount, ukrainianRepeatPause,
            translationDelay, translationRepeatCount, translationRepeatPause,
            examplePause, includeExampleInPlayback,
            nextWordPause, showCardDuringPlayback)
```

JSON (імпорт/експорт):
```
{ "dictionaryName", "language", "words": [ { "ukrainian", "translation1", "translation2", "example" } ] }
```

## З таска 01 — фундамент (готово, комміти `cbb37db`, `d56a9ec`)

- `AppContainer(context: Context)` — сервіс-локатор, поля `dictionaryRepository`, `settingsRepository`
- `DictionaryRepository` реалізовано за планом + додано `suspend fun ensureDefaultDictionaries()`
  (викликається з `LearnEverywhereApplication.onCreate`) — створює дефолтний словник на
  GERMAN/ENGLISH, якщо його ще нема
- `DictionaryRepository.exportToJson`/`.importFromJson` — сигнатура стабільна, тіло `TODO()`
  (реалізація — тікет 09, не займай ці методи раніше)
- `SettingsRepository.settings: Flow<AppSettings>`, `.update(transform)`, `.resetToDefaults()` — готово, DataStore
- Room: `DictionaryEntity`, `WordEntryEntity`, `DictionaryDao`, `WordEntryDao`, `AppDatabase`, `Converters`
- Навігація: `LearnEverywhereNavHost` (Compose Navigation), `MainScreen` з нижньою панеллю
  (`BottomNavTab`), `SplashScreen`, заглушки `HomeScreen`/`DictionariesScreen` — наступні
  тікети замінюють вміст заглушок, не структуру навігації
- Тема: `ui/theme/{Color,Theme,Type}.kt` — Space Grotesk/Plus Jakarta Sans, коралова акцентна
  палітра, день/ніч через `isSystemInDarkTheme()`
- **Збірка:** `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`,
  `ANDROID_HOME="/c/Users/asaln/AppData/Local/Android/Sdk"`, `local.properties` створювати
  заново щотікета (у `.gitignore`). `./gradlew testDebugUnitTest` — зелений (3/3, `DictionaryRepositoryTest`)
- `DictionaryRepositoryImpl` приймає `Context` (applicationContext) третім параметром —
  читає `R.string.default_dictionary_name`. `DictionaryDao.setDefault(id)` — `@Transaction`,
  атомарна зміна дефолтного словника мови.
- compileSdk/targetSdk = 37 (не 36 — версії з `gradle/libs.versions.toml` цього вимагають)
- Плагін `kotlin-android` прибрано — AGP 9.4.0 має вбудовану підтримку Kotlin, не додавай його знову

## З таска 02 — переклад (MyMemory)

- `TranslationService.translate(word: String, sourceLang: Language, targetLang: Language): TranslationResult`
- `TranslationResult(candidates: List<String>, example: String, isExampleGenerated: Boolean)`
- `Language(isoCode)` — `UKRAINIAN`/`GERMAN`/`ENGLISH`
- `TranslationUnavailableException` — доменний виняток мережевої помилки/таймауту
- `fun interface TranslationHttpClient { fun get(url): String }` — шов мокання в тестах;
  `OkHttpTranslationHttpClient` — продакшн-реалізація (додано `okhttp`, `org.json:json` в `libs.versions.toml`)
- **Не підключено в `AppContainer`** (поза зоною `translation/`) — наступний тікет, що
  споживає сервіс (06 — захоплення слова), сам додає `translationService = TranslationServiceImpl(OkHttpTranslationHttpClient())`

## З таска 03 — голос (розпізнавання, озвучення, визначення мови)

- `LanguageDetector(interfaceLanguage: () -> InterfaceLanguage).detect(text): SpokenLanguage`
- `SpokenLanguage.toLocale(): Locale`; `InterfaceLanguage.toSpokenLanguage(): SpokenLanguage`
- `SpeechInput.listen(): Flow<SpeechResult>` — `SpeechResult` sealed: `Recognized(text)` / `Failed(reason: SpeechFailureReason)`
- `AndroidSpeechInput(context, interfaceLanguage: () -> InterfaceLanguage)` — реалізація
- `isMicrophonePermissionGranted(context): Boolean`; `mapSpeechRecognizerError(Int): SpeechFailureReason`
- `Speaker.speak(text, locale: SpokenLanguage, onDone: () -> Unit = {})`, `.shutdown()`; `AndroidSpeaker(context)` — реалізація
- **Рантайм-запит `RECORD_AUDIO` і діалог «не вдалося розпізнати» — НЕ тут.** `voice/` лише
  виставляє типізовану перевірку дозволу і `Failed(PERMISSION_DENIED)`/`Failed(NOT_UNDERSTOOD)`;
  показ — відповідальність тікета 06 (ui/home), за межею зони
- Дозволи `RECORD_AUDIO`/`INTERNET` вже в `AndroidManifest.xml`

## З таска 04 — екран налаштувань

- `SettingsScreen(modifier, onBack, settingsRepository = AppContainer default)` — composable
- `SettingsOptions.SECONDS_OPTIONS: List<Int>` (1..6) — живить усі дропдауни пауз/повторів
- NavHost-маршрут `"settings"` доданий; `MainScreen(onSettingsClick: () -> Unit)` — іконка
  налаштувань у верхньому куті **Home**-вкладки (за коментарем у коді)
- **Вийшло за межі зони `ui/settings/`:** `MainScreen.kt` (іконка переходу) і
  `LearnEverywhereNavHost.kt` (маршрут) — тікет 01 не лишив ні заглушки, ні навігації для
  переходу на налаштування, тож виконавець 04 додав їх сам (потрібно для R22)
- Виконавець не бачив Stitch-мокап (потрібен вхід) — верстка йде за токенами теми
  (`Color/Theme/Type.kt`), без прямого звірення з макетом
- **За рев'ю дороблено (R37):** `InterfaceLocaleProvider` перенесено з `SettingsScreen`-піддерева
  на корінь застосунку — обгортає весь `NavHost` у `LearnEverywhereNavHost.kt`, а не лише
  маршрут `settings`; усі екрани (в т.ч. майбутні 05/06/07) успадковують поточну мову
  автоматично. `InterfaceLanguage.toLocale()` — тимчасовий дублікат мапінгу з `interfaces.md`
  §voice (`SpokenLanguage.toLocale()`), значення синхронізовані зі спекою (uk-UA/de-DE/en-US);
  замінити на делегування до voice-модуля при зручній нагоді (не блокуюче)

## З таска 05 — екран словників (вкладки, список)

- `DictionariesViewModel(dictionaryRepository, settingsRepository, scope: CoroutineScope)` —
  **звичайний клас, НЕ `androidx.lifecycle.ViewModel`** (щоб не тягнути нову Gradle-залежність
  `lifecycle-viewmodel-compose`) — стан не переживає поворот екрана; свідомий компроміс,
  наступний тікет, що додасть реальний `ViewModelProvider`, може захотіти це переглянути
- `selectedLanguage`, `uiState: StateFlow<DictionariesUiState>`; `.selectTab(lang)`,
  `.setDefault(id)`, `.createDictionary(name: String)`
- `DictionariesUiState(selectedLanguage, items: List<DictionaryListItem>)`,
  `DictionaryListItem(dictionary, wordCount)`
- Кнопка плей у хедері — **лише UI-заглушка**, клікабельна, без логіки програвання (тікет 08/10)

## З таска 07 — деталі словника (перегляд, редагування)

Розширено `DictionariesViewModel`/`DictionariesScreen` з тікета 05, НЕ окремий екран/маршрут —
тап на картку перемикає стан того ж екрана.

- `DictionariesViewModel.detailUiState: StateFlow<DictionaryDetailUiState?>`
- `.openDictionary(id)`, `.closeDictionary()`, `.selectWord(id)`, `.setWordSearchQuery(q)`,
  `.renameDictionary(name)`, `.deleteDictionary()`, `.updateWord(word: WordEntry)`, `.deleteWord(id)`
- `DictionaryDetailUiState(dictionary, words, selectedWordId, searchQuery)` — обчислювані
  `isSearchVisible` (words.size > 20), `filteredWords`, `selectedWord`
- Іконки топбару (перейменувати/видалити/редагувати/закрити) — текстові гліфи (✎ 🗑 🖊 ✕),
  у стилі вже наявних ▶/+ на екрані, без `material-icons-extended`; не звірено з Stitch-мокапом
  візуально (не було доступу до браузера)

## З таска 06 — головний екран (захоплення слова)

- `HomeViewModel(dictionaryRepository, translationService, speechInput, languageDetector, settingsRepository, scope, defaultDictionaryName: () -> String)`
- `uiState: StateFlow<HomeUiState>`; дії: `onInputTextChange/onMicClick/onSubmitText/onChooseDictionary/onConfirm/onCancelConfirmation/onDismissRecognitionFailed/onDismissTranslationFailed/onRetryTranslation`
- `HomeUiState(inputText, isListening, dialog)`; `HomeDialog` sealed
  (`ChooseDictionary`/`Confirm`/`RecognitionFailed`/`TranslationFailed`); `WordCapture`
- `AppContainer` тепер має `translationService`/`speechInput`/`languageDetector`/`speaker`
  (підключено з тікетів 02/03, раніше не було)
- `ChooseDictionary`-діалог без Cancel (брифінг не пропонує скасування вибору словника)
- **За рев'ю дороблено (R08/R17):** для нім./англ. слова `translateForeignWord` робить ДВА
  виклики — `translate(word, DE/EN, UKRAINIAN)` лише за українським значенням, і
  `translate(ukrainianValue, UKRAINIAN, dictionaryLanguage)` саме за `example` мовою словника.
  `translation1` береться лише з першого виклику. `runTranslation(retry, buildCapture)` —
  спільний хелпер, замінив дублювання retry/runCatching в обох гілках (укр.→нім/англ і
  нім/англ→укр).

## З таска 08 — рушій програвання

- `PlaybackEngine(words, settings, shuffler)` — чистий FSM без Android-залежностей: `.phase:
  PlaybackPhase`, `.advance()`, `.skipToNextWord()` — головний тестований шов
- `PlaybackController(context, dictionaryRepository, settingsRepository, speaker, scope)` —
  `.start(dictId)/.pause()/.resume()/.stop()/.skipNext()`, `currentWord`/`isPlaying: StateFlow`
  — сигнатура як в спеці, але **звичайний клас, НЕ сам `MediaSessionService`** (Android не
  дає конструювати `MediaSessionService` напряму) — `PlaybackMediaService` (Android-сервіс)
  тримає і делегує йому
- `PlaybackController.active: PlaybackController?` (companion) — міст між сервісом і
  `MiniPlayer(controller)` composable
- **`MiniPlayer` готовий, але НЕ вживлений у жоден екран** — це зона тікета 10, за тим самим
  прецедентом, що й плей-кнопка в хедері словників (тікет 05) — заглушка до 10
- `pause()` діє на межі фаз, не перериває TTS-вислів чи паузу в польоті (`Speaker` не має
  "зупинити поточний вислів" без shutdown усього) — задокументований компроміс
- AC5 (звук ≥2хв з вимкненим екраном) і AC6 (пауза на вхідний дзвінок) реалізовано за спекою,
  але НЕ верифіковано на реальному пристрої/емуляторі — інструментального раннера нема

## З таска 09 — імпорт/експорт словників (JSON)

- `DictionaryRepository.exportToJson(dictId): Uri` / `.importFromJson(uri, lang): Dictionary` —
  реалізовано за фіксованою сигнатурою з тікета 01
- `DictionaryRepository.detectImportLanguage(uri): DictionaryLanguage?` — новий метод (не було
  в плані): визначає мову словника з файлу, якщо вона там є; `null` — просить користувача
  вибрати мову при імпорті
- `InvalidDictionaryFileException` — доменний виняток кривого JSON (відсутнє поле, не JSON);
  імпорт на ньому не чіпає базу
- `DictionaryExportProvider` — власний `ContentProvider` (не `androidx.core.content.FileProvider`)
  для віддачі експортованого файлу через `content://`, зареєстрований в `AndroidManifest.xml`.
  Причина: `FileProvider` кидає виняток на канонізації шляху під Robolectric на Windows —
  не Reinvention заради смаку, обхід конкретного багу тестового оточення (перевірено рев'ю)
- `DictionariesViewModel.exportDictionary(id, onReady)`, `.onImportFileSelected(uri)`,
  `.onImportLanguageChosen(uri, lang)`, `.onDismissImportDialog()`, `importDialog: StateFlow<ImportDialog?>`
- `ImportDialog` sealed (`ChooseLanguage`/`InvalidFile`) в `DictionariesUiState.kt`
- JSON-схема — як у спеці: `{ dictionaryName, language, words: [{ ukrainian, translation1, translation2, example }] }`;
  імпортовані слова завжди `isExampleGenerated = false` (поза схемою)

## Growing this file

Після кожного зданого тікета — дописати сюди фактичні сигнатури, якщо вони відрізняються
від запланованих вище (з поясненням чому), і нові публічні межі, яких не було в спеці.
