package com.learneverywhere.app.ui.dictionaries

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
 * Заглушка. Вкладки нім./англ., список карток і плей — тікет 05
 * (`ui/dictionaries/`, зона з manifest.md).
 */
@Composable
fun DictionariesScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.dictionaries_screen_placeholder),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
