package com.learneverywhere.app.voice

import com.learneverywhere.app.data.model.InterfaceLanguage
import java.util.Locale

/**
 * Евристика визначення мови сказаного/введеного слова (R01, R01.1):
 * кирилиця -> українська; латиниця -> шукаємо службові слова, характерні
 * для німецької чи англійської (der/die/das/und/ist… vs the/and/is/a…);
 * якщо жодного маркера немає -> поточна мова інтерфейсу як здогад.
 * Обмеження задокументоване в spec.md як відомий ризик, не блокер: список
 * службових слів навмисно малий (без зовнішніх ML-бібліотек, п.8 брифа),
 * тому короткі побутові слова поза списком (типовий випадок — одне слово,
 * не речення) не завжди впізнаються правильно. Привітання (hallo/hello)
 * додані поряд зі службовими словами навмисно — без них евристика на
 * "службових словах" не спрацьовує взагалі на односкладові репліки, а саме
 * вони — головний сценарій застосунку (юзер каже одне слово).
 */
class LanguageDetector(
    private val interfaceLanguage: () -> InterfaceLanguage,
) {

    fun detect(text: String): SpokenLanguage {
        val trimmed = text.trim()
        if (trimmed.any { it in CYRILLIC_RANGE }) return SpokenLanguage.UKRAINIAN

        val words = trimmed.lowercase(Locale.ROOT).split(NON_LETTER_REGEX).filter { it.isNotBlank() }
        val germanHits = words.count { it in GERMAN_MARKERS }
        val englishHits = words.count { it in ENGLISH_MARKERS }

        return when {
            germanHits > englishHits -> SpokenLanguage.GERMAN
            englishHits > germanHits -> SpokenLanguage.ENGLISH
            else -> interfaceLanguage().toSpokenLanguage()
        }
    }

    private companion object {
        val CYRILLIC_RANGE = 'Ѐ'..'ӿ'
        val NON_LETTER_REGEX = Regex("[^\\p{L}]+")

        val GERMAN_MARKERS = setOf(
            "der", "die", "das", "und", "ist", "ich", "du", "nicht", "ein", "eine",
            "hallo", "guten", "tag", "danke", "bitte", "ja", "nein",
        )
        val ENGLISH_MARKERS = setOf(
            "the", "and", "is", "a", "an", "hello", "hi", "yes", "no", "please", "thanks",
        )
    }
}
