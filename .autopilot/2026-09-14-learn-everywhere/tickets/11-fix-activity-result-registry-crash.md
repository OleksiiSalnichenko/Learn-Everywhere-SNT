# 11 — Фікс: краш застосунку через InterfaceLocaleProvider (виявлено сліпим прийманням)

**Вимоги:** усі UI-вимоги (R12-R44 та інші), які фактично недоступні через цей краш
**Blocked by:** 10
**Зона:** `ui/settings/InterfaceLocale.kt`, тестова інфраструктура для Compose UI
**Хвиля:** 6 (репаір поза звичайним планом)
**Status:** ready

## Що зламано

Сліпе приймання (Phase 8) реально запустило застосунок на емуляторі (Pixel_10_Pro).
Застосунок падає одразу після заставки, щойно рендериться `HomeScreen`:

```
java.lang.IllegalStateException: No ActivityResultRegistryOwner was provided via
LocalActivityResultRegistryOwner
    at com.learneverywhere.app.ui.home.HomeScreenKt.HomeContent(HomeScreen.kt:111)
    at com.learneverywhere.app.ui.home.HomeScreenKt.HomeScreen(HomeScreen.kt:79)
    at com.learneverywhere.app.ui.main.MainScreenKt.MainScreen$lambda$5(MainScreen.kt:55)
```

Відтворено двічі поспіль (детермінований, не флейк).

## Причина

`InterfaceLocaleProvider` (`app/src/main/java/com/learneverywhere/app/ui/settings/InterfaceLocale.kt`)
обгортає весь `NavHost` (`ui/navigation/LearnEverywhereNavHost.kt`) і підміняє
`LocalContext` на `base.createConfigurationContext(configuration)` — звичайний `Context`,
**не** `ContextWrapper` навколо `Activity`. Тому будь-який
`rememberLauncherForActivityResult` нижче по дереву композиції (мікрофонний
permission-launcher у `HomeScreen.kt:111`, файловий пікер у `DictionariesScreen.kt`) не
може знайти `ActivityResultRegistryOwner`, `SavedStateRegistryOwner` тощо (усі вони
резолвляться через `Context`-делегати, яких немає в голому `ContextWrapper`) і кидає
виняток при першій композиції.

Наслідок: користувач не бачить жодного екрана, крім заставки — ні головний, ні словники,
ні налаштування.

## Що треба зробити

Полагодити `InterfaceLocaleProvider` так, щоб підміна `LocalContext` для локалізації **не
ламала** делегати, потрібні `Activity`-залежним `CompositionLocal`'ам
(`LocalActivityResultRegistryOwner`, `LocalSavedStateRegistryOwner`,
`LocalOnBackPressedDispatcherOwner` — усі активно використовуються нижче по дереву).

Два відомі підходи (обери сам, що надійніше для цього коду, або запропонуй третій —
головне, щоб реальний запуск на емуляторі підтвердив відсутність крашу):

1. Замість підміни `LocalContext.current` на голий `base.createConfigurationContext(...)`,
   обгорни його у власний `ContextWrapper`, що **делегує** усі
   `Activity`-специфічні інтерфейси (`ActivityResultRegistryOwner`,
   `SavedStateRegistryOwner`, `LifecycleOwner`, `OnBackPressedDispatcherOwner`,
   `ViewModelStoreOwner`) до оригінального `Activity`-контексту, а `getResources()`/
   `getAssets()` — до конфігураційного. Знайти оригінальний `Activity` можна
   розгортанням ланцюжка `ContextWrapper` (`generateSequence(context) { (it as?
   ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()`).
2. Не підміняти `LocalContext` взагалі — замість цього виставляти окремий
   `CompositionLocal` для мови інтерфейсу (напр. `LocalInterfaceLocale`) і застосовувати
   локалізовані рядки через власну обгортку над `stringResource`, яка бере `Resources`,
   побудовані для потрібної мови, але лишає `LocalContext` як є (справжній
   `Activity`-контекст).

**Не звужуй R37** (уся програма успадковує поточну мову інтерфейсу автоматично) — обраний
підхід має зберігати цю поведінку, лише не через звичайний `Context`.

## Критерії приймання

- [ ] Реальний запуск на емуляторі: застосунок стартує, показує заставку, переходить на
      `HomeScreen` **без краху**. Перевір командами нижче, вклади в TESTS результат.
- [ ] Клік на кнопку мікрофона на `HomeScreen` не кидає виняток (permission-launcher
      реально викликається, діалог дозволу з'являється або дозвіл уже наданий — головне,
      без `IllegalStateException`).
- [ ] Перехід на `DictionariesScreen`, клік на кнопку імпорту (файловий пікер) — так само
      без краху.
- [ ] Локалізація інтерфейсу (R37) і далі працює: зміна мови в налаштуваннях далі
      застосовується до всього дерева навігації (не звужено).
- [ ] Юніт-тест: додай Robolectric-тест, що реально рендерить `HomeScreen`/
      `DictionariesScreen` під `InterfaceLocaleProvider` (напр. через
      `createAndroidComposeRule<ComponentActivity>()` або еквівалент, сумісний з
      наявним тестовим стеком) і фейлиться, якби регресія повернулась — це закриває
      прогалину, яку сліпе приймання назвало прямою причиною, чому 72 юніт-тести
      пропустили краш.
- [ ] `./gradlew testDebugUnitTest` — весь набір зелений.
- [ ] Комміт у `develop`.

## Як перевірити реальним запуском (команди сліпого приймача)

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/asaln/AppData/Local/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew assembleDebug
emulator -avd Pixel_10_Pro -no-snapshot -no-boot-anim &
adb wait-for-device
# зачекай sys.boot_completed=1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.learneverywhere.app/.MainActivity
adb logcat -d | grep -i -E "FATAL|learneverywhere"
```
Якщо емулятора Pixel_10_Pro немає в цьому середовищі — перевір, які AVD є
(`emulator -list-avds`), візьми будь-який готовий; якщо жодного немає і створити
неможливо без мережі — це BLOCKED з точним описом, але спробуй Robolectric-тест з
реальним рендером Compose (пункт вище) як мінімальну заміну доказу.
