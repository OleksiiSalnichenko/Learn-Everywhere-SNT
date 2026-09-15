package com.learneverywhere.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.learneverywhere.app.R

/**
 * Заглушка. Кнопка мікрофона, поле вводу і діалог підтвердження — тікет 06
 * (`ui/home/`, зона з manifest.md). Тут лише порожній екран, щоб нижня
 * навігація мала куди перемикатись (критерій приймання тікета 01).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.home_screen_placeholder),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
