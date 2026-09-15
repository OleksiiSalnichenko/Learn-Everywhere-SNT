package com.learneverywhere.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.data.model.MainLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Юніт-тест на шов `SettingsRepository` (interfaces.md) — критерії приймання
 * тікета 04, які саме цей екран мусить гарантувати поверх сховища з тікета 01:
 * дефолти з брифа, збереження між "перезапусками" і повне скидання.
 *
 * Файловий DataStore (не in-memory delegate) — щоб другий інстанс репозиторію
 * поверх того самого файлу справді імітував перезапуск застосунку, а не просто
 * читав той самий об'єкт у пам'яті.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun dataStoreAt(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun newStoreFile(): File = File(tempFolder.newFolder(), "settings.preferences_pb")

    @Test
    fun `settings equal brief defaults before any change`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val repository: SettingsRepository = SettingsRepositoryImpl(dataStoreAt(newStoreFile(), scope))

        val defaults = repository.settings.first()

        assertEquals(AppSettings(), defaults)
        assertEquals(MainLanguage.GERMAN, defaults.mainLanguage)
        assertEquals(InterfaceLanguage.ENGLISH, defaults.interfaceLanguage)
        assertFalse(defaults.includeExampleInPlayback)
        assertTrue(defaults.showCardDuringPlayback)

        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test
    fun `update persists and survives a new repository instance over the same store`() = runBlocking {
        val file = newStoreFile()
        val scopeBeforeRestart = CoroutineScope(SupervisorJob())
        val beforeRestart = SettingsRepositoryImpl(dataStoreAt(file, scopeBeforeRestart))

        beforeRestart.update { current ->
            current.copy(
                mainLanguage = MainLanguage.ENGLISH,
                loop = true,
                shuffle = true,
                ukrainianRepeatPause = 5,
            )
        }
        // Закриваємо перший DataStore (скасовуємо його scope), інакше AndroidX DataStore
        // забороняє два одночасно активні інстанси на одному файлі — саме так і
        // імітуємо перезапуск застосунку, а не паралельний доступ.
        scopeBeforeRestart.coroutineContext[Job]!!.cancelAndJoin()

        val scopeAfterRestart = CoroutineScope(SupervisorJob())
        val afterRestart = SettingsRepositoryImpl(dataStoreAt(file, scopeAfterRestart))
        val restored = afterRestart.settings.first()

        assertEquals(MainLanguage.ENGLISH, restored.mainLanguage)
        assertTrue(restored.loop)
        assertTrue(restored.shuffle)
        assertEquals(5, restored.ukrainianRepeatPause)

        scopeAfterRestart.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test
    fun `resetToDefaults reverts every changed field back to brief defaults`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val repository: SettingsRepository = SettingsRepositoryImpl(dataStoreAt(newStoreFile(), scope))

        repository.update { current ->
            current.copy(
                mainLanguage = MainLanguage.NONE,
                interfaceLanguage = InterfaceLanguage.GERMAN,
                loop = true,
                shuffle = true,
                ukrainianRepeatCount = 6,
                translationDelay = 6,
                includeExampleInPlayback = true,
                showCardDuringPlayback = false,
            )
        }

        repository.resetToDefaults()

        assertEquals(AppSettings(), repository.settings.first())

        scope.coroutineContext[Job]!!.cancelAndJoin()
    }
}
