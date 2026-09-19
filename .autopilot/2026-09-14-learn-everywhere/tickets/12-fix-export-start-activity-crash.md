# 12 — Фікс: краш Export через startActivity() поза Activity-контекстом

**Вимоги:** R10 (не звузити)
**Blocked by:** 11
**Зона:** `ui/dictionaries/` (шлях експорту), можливо `ui/settings/InterfaceLocale.kt`
**Хвиля:** 7 (репаір, паралельно з таском 13 — незалежні зони)
**Status:** ready

## Що зламано

Незалежний смок-тест на емуляторі (Phase 8, після фіксу тікета 11) знайшов новий
детермінований краш: кнопка "Export" у деталях словника падає одразу.

```
AndroidRuntimeException: Calling startActivity() from outside of an Activity context
requires FLAG_ACTIVITY_NEW_TASK
    at DictionariesScreen.kt:142 -> DictionariesViewModel.exportDictionary
```

## Причина (гіпотеза з попередньої перевірки, перевір сам)

Тікет 11 додав `ActivityAwareConfigurationContext` — `ContextWrapper`, що делегує
`ActivityResultRegistryOwner`/`SavedStateRegistryOwner`/`LifecycleOwner`/
`OnBackPressedDispatcherOwner`/`ViewModelStoreOwner` до реальної `Activity`, але **не
перевизначає `startActivity()`**. Десь у шляху експорту (`DictionaryExportProvider` +
системний діалог "поділитися", `Intent.ACTION_SEND`/`createChooser`) викликається
`context.startActivity(...)` там, де `context` — обгорнутий (не-Activity) контекст з
`InterfaceLocaleProvider`, тому виклик іде через `ContextWrapper.startActivity()` до
голого `configurationContext`, який кидає виняток.

## Що треба зробити

Полагодь так, щоб Export реально відкривав системний діалог "поділитися" без крашу, і
щоб фікс був системний (той самий клас багу може ховатись в інших місцях, що викликають
`startActivity` крізь `InterfaceLocaleProvider`-обгортку), а не точковий костиль лише для
Export:

- Найімовірніше правильне місце — `ActivityAwareConfigurationContext` (тікет 11): додай
  перевизначення `startActivity(Intent)`/`startActivity(Intent, Bundle?)`, що делегує до
  реальної `Activity`, знайденої тим самим `findActivity()`, замість `super.startActivity(...)`.
  Це узгоджено з рештою фіксу тікета 11 (делегування до реальної Activity) і закриває
  цілий клас багів одним фіксом, а не лише Export.
- Альтернатива, якщо перше з якоїсь причини не підходить: у самому виклику `startActivity`
  для "поділитися" (де б він не був) додати `FLAG_ACTIVITY_NEW_TASK` до `Intent` — але це
  локальний костиль, застосуй лише якщо перший підхід дійсно не працює, і поясни чому.

**Не звужуй R10** (експорт віддає JSON через системний діалог "поділитися").

## Критерії приймання

- [ ] Реальний запуск на емуляторі: відкрити деталі словника з хоч одним словом, тапнути
      Export — системний діалог "поділитися" відкривається, без краху. Перевір командами
      нижче.
- [ ] Юніт/Robolectric-тест або регресійний тест на цей конкретний виклик (той самий
      підхід, що й `InterfaceLocaleActivityResultRegressionTest.kt` з тікета 11, якщо
      доречно — перевір, чи можна розширити той самий тест новим кейсом на
      `startActivity`, замість дублювання інфраструктури).
- [ ] Заодно перевір (грепом по коду), чи є ще виклики `startActivity`/
      `startActivityForResult` крізь composable-дерево під `InterfaceLocaleProvider` —
      якщо так, вкажи їх у CONCERNS навіть якщо не крашаться зараз (можуть за інших умов).
- [ ] `./gradlew testDebugUnitTest` — весь набір зелений.
- [ ] Комміт у `develop`.

## Як перевірити реальним запуском

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/asaln/AppData/Local/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew assembleDebug
adb devices -l   # має бути хоч один emulator-XXXX; якщо ні — emulator -list-avds, підняти
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.learneverywhere.app/.MainActivity
# UI: перейти на Словники -> тапнути словник -> тапнути Export
adb logcat -d | grep -i -E "FATAL|learneverywhere"
```
