package com.learneverywhere.app

import android.app.Application
import com.learneverywhere.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LearnEverywhereApplication : Application() {

    /** Публічний, бо `MainActivity` дістає з нього репозиторії для ViewModel-ів. */
    lateinit var container: AppContainer
        private set

    // SupervisorJob — падіння однієї фонової корутини (напр. запис у DataStore)
    // не має вбити весь застосунок-scope.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Перший запуск: гарантуємо дефолтні словники "Основний" на обидві
        // мови до того, як користувач відкриє екран словників (історія 17).
        applicationScope.launch {
            container.dictionaryRepository.ensureDefaultDictionaries()
        }
    }
}
