package com.learneverywhere.app.playback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.learneverywhere.app.R

/**
 * Міні-плеєр (A02, критерій приймання тікета 08: "показує поточне слово і
 * кнопки паузи/пропуску поза екраном словників"). Читає [PlaybackController]
 * лише через його публічні `StateFlow`-и — не знає нічого про чергу/фази.
 * Свідомо окремий composable у `playback/`, не в `ui/`: тікет 01 не лишив
 * жодного місця для нього в навігації, а `interfaces.md` дозволяє покласти
 * тут або в `ui/playback/` — обрано `playback/`, щоб не займати чужу зону
 * заради самого лише розміщення composable-функції. Ticket 10 (playback-wiring)
 * вирішує, у якому composable-контейнері його показати (напр. поверх
 * `MainScreen`, як зараз зроблено з іконкою налаштувань).
 *
 * Кнопки — текстові гліфи (▶/⏸/⏭), як і плей-заглушка тікета 05
 * (`DictionariesScreen`) — свідомо без `material-icons-extended`, щоб не
 * тягнути ще одну залежність заради пари іконок (той самий принцип, що й
 * там: "не ускладнювати").
 *
 * Нічого не малює (`Unit`), якщо [PlaybackController.currentWord] — `null`,
 * тобто сервіс не грає — саме так "доступний, поки сервіс живий" читається
 * через публічний `StateFlow`, без окремого прапорця видимості.
 */
@Composable
fun MiniPlayer(controller: PlaybackController, modifier: Modifier = Modifier) {
    val word by controller.currentWord.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val current = word ?: return

    val pauseResumeDescription = stringResource(
        if (isPlaying) R.string.playback_pause_content_description else R.string.playback_resume_content_description,
    )
    val skipDescription = stringResource(R.string.playback_skip_next_content_description)

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current.ukrainian,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (isPlaying) controller.pause() else controller.resume() },
                modifier = Modifier.semantics { contentDescription = pauseResumeDescription },
            ) {
                Text(text = if (isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(
                onClick = { controller.skipNext() },
                modifier = Modifier.semantics { contentDescription = skipDescription },
            ) {
                Text(text = "⏭", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
