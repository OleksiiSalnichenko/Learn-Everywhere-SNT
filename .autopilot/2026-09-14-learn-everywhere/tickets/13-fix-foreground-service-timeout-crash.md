# 13 — Фікс: краш усього застосунку при Play (ForegroundServiceDidNotStartInTimeException)

**Вимоги:** R43, R44, R45 (не звузити — програвання ключовий сценарій)
**Blocked by:** 10
**Зона:** `playback/`
**Хвиля:** 7 (репаір, паралельно з таском 12 — незалежні зони)
**Status:** ready

## Що зламано

Незалежний смок-тест на емуляторі (Phase 8) натиснув Play у хедері словника —
програвання стартувало ("Зараз програється: ..."), але через ~15-20 секунд **увесь
застосунок вилетів** (процес вбито сигналом 9):

```
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException
```

Це найкритичніший з трьох крашів, знайдених у Phase 8 — програвання є основною метою
застосунку ("Learn Everywhere" — вивчення слів на слух), і воно валить весь процес, а не
просто падає з помилкою.

## Причина (гіпотеза, перевір сам)

`PlaybackController.kt` (десь біля рядка 78, за звітом попередньої перевірки) викликає
`context.startForegroundService(...)`, який запускає `PlaybackMediaService`. Android
вимагає, щоб сервіс, запущений через `startForegroundService`, викликав
`Service.startForeground(id, notification)` **протягом кількох секунд** (з Android 8,
жорсткіше з новішими версіями) — інакше система вбиває процес з
`ForegroundServiceDidNotStartInTimeException`. Судячи з таймінгу (~15-20с, не миттєво),
`PlaybackMediaService.startForeground(...)` або викликається із запізненням (після
якоїсь асинхронної підготовки — читання слів з бази, побудова черги), або не
викликається зовсім на цьому шляху запуску.

## Що треба зробити

Знайди фактичний шлях `PlaybackController.start(dictId)` → `PlaybackMediaService` і
переконайся, що `startForeground(id, notification)` викликається **синхронно, одразу**
при старті сервісу (у `onCreate()`/`onStartCommand()`, до будь-якого `suspend`/`launch`
очікування на дані з бази чи мережі) — не після того, як черга слів уже завантажена.
Якщо потрібно показати актуальну інформацію в нотифікації (поточне слово) — онови
нотифікацію пізніше через `NotificationManager.notify(...)`, але саму `startForeground`
виклич негайно з тимчасовим/базовим контентом нотифікації.

Перевір також:
- Чи задекларований правильний `foregroundServiceType` в `AndroidManifest.xml`
  (`mediaPlayback`, за нотатками тікета 08) і чи він відповідає тому, що дозволяє ОС на
  цільовій версії SDK (targetSdk=37).
- Чи є в проєкті permission `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_MEDIA_PLAYBACK` —
  без них `startForeground` може падати іншим способом.

**Не звужуй R43/R44/R45** (плей, картка, фонове програвання) — фікс має зберігати
задум "запустити і продовжувати грати з вимкненим екраном", лише прибрати краш.

## Критерії приймання

- [ ] Реальний запуск на емуляторі: відкрити словник з хоч одним словом, тапнути Play у
      хедері — програвання триває ХОЧА Б 30 секунд без краху застосунку (довше за
      попередній інтервал до краху). Перевір командами нижче, тримай застосунок відкритим
      і зачекай, дивись logcat на FATAL/RemoteServiceException.
- [ ] Пауза/стоп з екрана і з міні-плеєра (тікет 08/10) далі працюють, не крашать.
- [ ] Юніт-тест на новій поведінці, якщо є що тестувати без реального Android-сервісу
      (напр. що `PlaybackMediaService`/`PlaybackController` викликають `startForeground`
      негайно, а не після завантаження даних) — якщо це неможливо перевірити юніт-тестом
      через залежність від реального Android `Service`, поясни чому в CONCERNS і
      покладись на реальний запуск як доказ.
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
adb logcat -c
# UI: Словники -> тапнути Play у хедері словника з хоч одним словом
sleep 30
adb logcat -d | grep -i -E "FATAL|RemoteServiceException|learneverywhere"
```
