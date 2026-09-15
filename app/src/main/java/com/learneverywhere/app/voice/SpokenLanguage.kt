package com.learneverywhere.app.voice

import com.learneverywhere.app.data.model.InterfaceLanguage
import java.util.Locale

/**
 * Мова, якою сказане або введене слово (R01). На відміну від
 * [com.learneverywhere.app.data.model.DictionaryLanguage] (лише нім./англ. —
 * мова словника), сюди входить і українська: саме слово могло прозвучати
 * українською, а вибір словника (нім./англ.) — окреме рішення вище по стеку
 * (історії 5, 6).
 */
enum class SpokenLanguage {
    UKRAINIAN,
    GERMAN,
    ENGLISH,
}

/**
 * Локаль для [Speaker] і базова підказка для [SpeechInput] — рівно ті три
 * локалі, що зафіксовані в spec.md §«Озвучення»: uk-UA / de-DE / en-US.
 */
fun SpokenLanguage.toLocale(): Locale = when (this) {
    SpokenLanguage.UKRAINIAN -> Locale("uk", "UA")
    SpokenLanguage.GERMAN -> Locale.GERMANY
    SpokenLanguage.ENGLISH -> Locale.US
}

/**
 * Мова інтерфейсу як здогад мови сказаного (R01.1, останній крок евристики
 * [LanguageDetector] — коли в латиниці не знайшлося жодного службового
 * слова). Значення обох енумів збігаються один в один навмисно.
 */
fun InterfaceLanguage.toSpokenLanguage(): SpokenLanguage = when (this) {
    InterfaceLanguage.UKRAINIAN -> SpokenLanguage.UKRAINIAN
    InterfaceLanguage.GERMAN -> SpokenLanguage.GERMAN
    InterfaceLanguage.ENGLISH -> SpokenLanguage.ENGLISH
}
