package com.learneverywhere.app.playback

import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Юніт-тести на найважливіший шов тікета 08 — критерії приймання
 * `.autopilot/.../tickets/08-playback-engine.md`: повний цикл фаз з
 * повторами й паузами з `AppSettings`, shuffle без повторів слова в межах
 * проходу, loop, порожній словник без падіння.
 */
class PlaybackEngineTest {

    private fun word(id: Long, ukrainian: String = "слово$id") = WordEntry(
        id = id,
        dictionaryId = 1L,
        ukrainian = ukrainian,
        translation1 = "translation$id",
        translation2 = null,
        example = "example$id",
        isExampleGenerated = false,
    )

    @Test
    fun `full cycle repeats ukrainian and translation exactly as many times as settings say, then pauses before next word`() {
        val w = word(1)
        val settings = AppSettings(
            ukrainianRepeatCount = 2,
            ukrainianRepeatPause = 3,
            translationDelay = 4,
            translationRepeatCount = 2,
            translationRepeatPause = 5,
            includeExampleInPlayback = false,
            nextWordPause = 6,
        )
        val engine = PlaybackEngine(listOf(w), settings)

        assertEquals(PlaybackPhase.SpeakUkrainian(w, 1, 2), engine.phase)
        assertEquals(PlaybackPhase.PauseBetweenUkrainianRepeats(w, 3, 2, 2), engine.advance())
        assertEquals(PlaybackPhase.SpeakUkrainian(w, 2, 2), engine.advance())
        assertEquals(PlaybackPhase.WaitBeforeTranslation(w, 4), engine.advance())
        assertEquals(PlaybackPhase.SpeakTranslation(w, 1, 2), engine.advance())
        assertEquals(PlaybackPhase.PauseBetweenTranslationRepeats(w, 5, 2, 2), engine.advance())
        assertEquals(PlaybackPhase.SpeakTranslation(w, 2, 2), engine.advance())
        assertEquals(PlaybackPhase.PauseBeforeNext(w, 6), engine.advance())
        assertEquals(PlaybackPhase.Finished, engine.advance())
    }

    @Test
    fun `example phase is included with examplePause only when includeExampleInPlayback is enabled`() {
        val w = word(1)
        val settingsWithExample = AppSettings(
            ukrainianRepeatCount = 1,
            translationRepeatCount = 1,
            includeExampleInPlayback = true,
            examplePause = 2,
            nextWordPause = 6,
        )
        val engine = PlaybackEngine(listOf(w), settingsWithExample)

        engine.advance() // WaitBeforeTranslation
        engine.advance() // SpeakTranslation
        assertEquals(PlaybackPhase.WaitBeforeExample(w, 2), engine.advance())
        assertEquals(PlaybackPhase.SpeakExample(w), engine.advance())
        assertEquals(PlaybackPhase.PauseBeforeNext(w, 6), engine.advance())
    }

    @Test
    fun `example phase is skipped entirely when includeExampleInPlayback is disabled`() {
        val w = word(1)
        val settings = AppSettings(ukrainianRepeatCount = 1, translationRepeatCount = 1, includeExampleInPlayback = false, nextWordPause = 6)
        val engine = PlaybackEngine(listOf(w), settings)

        engine.advance() // WaitBeforeTranslation
        engine.advance() // SpeakTranslation(1, 1)

        assertEquals(PlaybackPhase.PauseBeforeNext(w, 6), engine.advance())
    }

    @Test
    fun `shuffle disabled keeps the dictionary order across the whole pass`() {
        val words = listOf(word(1), word(2), word(3))
        val settings = AppSettings(shuffle = false, ukrainianRepeatCount = 1, translationRepeatCount = 1, nextWordPause = 1)
        val engine = PlaybackEngine(words, settings)

        val visited = collectWordsOfOnePass(engine, expectedCount = 3)

        assertEquals(listOf(word(1), word(2), word(3)), visited)
    }

    @Test
    fun `shuffle enabled visits every word of the dictionary exactly once per pass, no repeats`() {
        val words = listOf(word(1), word(2), word(3), word(4))
        val settings = AppSettings(shuffle = true, ukrainianRepeatCount = 1, translationRepeatCount = 1, nextWordPause = 1)
        // Тестовий шафлер — не рандом, а детермінований розворот, щоб довести, що
        // engine дійсно застосовує injected shuffler, а не ігнорує його.
        val engine = PlaybackEngine(words, settings, shuffler = { it.reversed() })

        val visited = collectWordsOfOnePass(engine, expectedCount = 4)

        assertEquals(listOf(word(4), word(3), word(2), word(1)), visited)
        assertEquals(words.toSet(), visited.toSet())
    }

    @Test
    fun `loop disabled stops after the last word instead of restarting`() {
        val words = listOf(word(1), word(2))
        val settings = AppSettings(loop = false, ukrainianRepeatCount = 1, translationRepeatCount = 1, nextWordPause = 1)
        val engine = PlaybackEngine(words, settings)

        // Кожне слово — рівно 4 кроки (Ukrainian -> WaitBeforeTranslation -> Translation -> PauseBeforeNext).
        repeat(4) { engine.advance() }
        assertEquals(PlaybackPhase.SpeakUkrainian(word(2), 1, 1), engine.phase)
        repeat(4) { engine.advance() }

        assertEquals(PlaybackPhase.Finished, engine.phase)
        assertNull(engine.phase.word)
    }

    @Test
    fun `loop enabled restarts from the first word of a new pass instead of stopping`() {
        val words = listOf(word(1), word(2))
        val settings = AppSettings(loop = true, ukrainianRepeatCount = 1, translationRepeatCount = 1, nextWordPause = 1)
        val engine = PlaybackEngine(words, settings)

        repeat(4) { engine.advance() }
        assertEquals(PlaybackPhase.SpeakUkrainian(word(2), 1, 1), engine.phase)
        repeat(4) { engine.advance() }

        assertEquals(PlaybackPhase.SpeakUkrainian(word(1), 1, 1), engine.phase)
    }

    @Test
    fun `empty dictionary starts Empty and stays Empty without throwing on advance or skip`() {
        val engine = PlaybackEngine(emptyList(), AppSettings())

        assertEquals(PlaybackPhase.Empty, engine.phase)
        assertEquals(PlaybackPhase.Empty, engine.advance())
        assertEquals(PlaybackPhase.Empty, engine.skipToNextWord())
    }

    @Test
    fun `skipToNextWord abandons remaining repeats and pauses of the current word`() {
        val words = listOf(word(1), word(2))
        val settings = AppSettings(ukrainianRepeatCount = 3, translationRepeatCount = 3, nextWordPause = 1)
        val engine = PlaybackEngine(words, settings)

        val next = engine.skipToNextWord()

        assertEquals(PlaybackPhase.SpeakUkrainian(word(2), 1, 3), next)
    }

    /** Проганяє автомат від поточної фази до PauseBeforeNext/Finished [expectedCount] разів, збираючи слова. */
    private fun collectWordsOfOnePass(engine: PlaybackEngine, expectedCount: Int): List<WordEntry> {
        val visited = mutableListOf(requireNotNull(engine.phase.word))
        var guard = 0
        while (visited.size < expectedCount && guard < 1000) {
            val next = engine.advance()
            if (next is PlaybackPhase.SpeakUkrainian && next.repeat == 1) {
                visited.add(next.word)
            }
            guard++
        }
        return visited
    }
}
