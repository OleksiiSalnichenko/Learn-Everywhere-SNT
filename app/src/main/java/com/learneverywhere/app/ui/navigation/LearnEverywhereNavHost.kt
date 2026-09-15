package com.learneverywhere.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learneverywhere.app.ui.main.MainScreen
import com.learneverywhere.app.ui.settings.InterfaceLocaleProvider
import com.learneverywhere.app.ui.settings.SettingsScreen
import com.learneverywhere.app.ui.splash.SplashScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"

/**
 * Верхній рівень навігації: заставка -> головний екран (з нижньою панеллю
 * всередині `MainScreen`) -> налаштування (тікет 04, R22 — "перехід по
 * відповідній кнопці"). Заставку прибираємо зі стеку (`popUpTo inclusive`),
 * щоб системна кнопка "назад" не повертала на неї.
 *
 * `InterfaceLocaleProvider` обгортає весь граф навігації (рев'ю R37, не
 * лише `SettingsScreen`) — так наступні тікети (05, 06, 07), заповнюючи
 * заглушки `HomeScreen`/`DictionariesScreen` реальним вмістом, автоматично
 * читають рядки поточною `settings.interfaceLanguage`, без повторної
 * реалізації локалізації на кожному екрані.
 */
@Composable
fun LearnEverywhereNavHost() {
    val navController = rememberNavController()

    InterfaceLocaleProvider {
        NavHost(navController = navController, startDestination = ROUTE_SPLASH) {
            composable(ROUTE_SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(ROUTE_MAIN) {
                            popUpTo(ROUTE_SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(ROUTE_MAIN) {
                MainScreen(onSettingsClick = { navController.navigate(ROUTE_SETTINGS) })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
