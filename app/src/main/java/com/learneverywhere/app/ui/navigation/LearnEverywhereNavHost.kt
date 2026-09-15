package com.learneverywhere.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learneverywhere.app.ui.main.MainScreen
import com.learneverywhere.app.ui.splash.SplashScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_MAIN = "main"

/**
 * Верхній рівень навігації: заставка -> головний екран (з нижньою панеллю
 * всередині `MainScreen`). Заставку прибираємо зі стеку (`popUpTo inclusive`),
 * щоб системна кнопка "назад" не повертала на неї.
 */
@Composable
fun LearnEverywhereNavHost() {
    val navController = rememberNavController()

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
            MainScreen()
        }
    }
}
