package com.learneverywhere.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.learneverywhere.app.LearnEverywhereApplication
import com.learneverywhere.app.R
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.voice.isMicrophonePermissionGranted

/**
 * Головний екран (тікет 06) — велика кнопка мікрофона по центру з
 * пульсацією під час запису і поле вводу знизу; обидва шляхи ведуть до
 * одного діалогу підтвердження (`HomeViewModel.beginCapture`). Затверджений
 * макет — Stitch, проєкт `17266546567257325539`, екран "Головний екран"
 * (spec.md §«Макети Stitch»): коралова кнопка, кільця пульсу навколо,
 * поле вводу з кнопкою "Ок" знизу над нижньою навігацією.
 *
 * `HomeViewModel` дістає сервіси з `AppContainer` напряму з `Application`
 * (сервіс-локатор, не Hilt) — так структура навігації лишається незмінною.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as LearnEverywhereApplication
    val scope = rememberCoroutineScope()
    val viewModel = remember {
        HomeViewModel(
            dictionaryRepository = application.container.dictionaryRepository,
            translationService = application.container.translationService,
            speechInput = application.container.speechInput,
            languageDetector = application.container.languageDetector,
            settingsRepository = application.container.settingsRepository,
            scope = scope,
            defaultDictionaryName = { application.getString(R.string.default_dictionary_name) },
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onMicClick = viewModel::onMicClick,
        onInputTextChange = viewModel::onInputTextChange,
        onSubmitText = viewModel::onSubmitText,
        onChooseDictionary = viewModel::onChooseDictionary,
        onConfirm = viewModel::onConfirm,
        onCancelConfirmation = viewModel::onCancelConfirmation,
        onDismissRecognitionFailed = viewModel::onDismissRecognitionFailed,
        onRetryTranslation = viewModel::onRetryTranslation,
        onDismissTranslationFailed = viewModel::onDismissTranslationFailed,
    )
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    uiState: HomeUiState,
    onMicClick: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onChooseDictionary: (DictionaryLanguage) -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onDismissRecognitionFailed: () -> Unit,
    onRetryTranslation: () -> Unit,
    onDismissTranslationFailed: () -> Unit,
) {
    val context = LocalContext.current
    // Рантайм-запит RECORD_AUDIO — відповідальність цього тікета (interfaces.md
    // §voice: "показ... НЕ тут [voice-модуль]"), не `AndroidSpeechInput`.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Відмова — `SpeechInput.listen()` сама віддасть `Failed(PERMISSION_DENIED)`
        // наступного разу; окремого діалогу тут не показуємо, щоб не дублювати
        // "не вдалося розпізнати" одразу після системного діалогу дозволу.
        if (granted) onMicClick()
    }
    val handleMicClick = {
        if (isMicrophonePermissionGranted(context)) {
            onMicClick()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PulsingMicButton(isListening = uiState.isListening, onClick = handleMicClick)
        }
        InputRow(
            text = uiState.inputText,
            onTextChange = onInputTextChange,
            onSubmit = onSubmitText,
        )
    }

    when (val dialog = uiState.dialog) {
        null -> Unit
        is HomeDialog.ChooseDictionary -> ChooseDictionaryDialog(onChoose = onChooseDictionary)
        is HomeDialog.Confirm -> ConfirmDialog(capture = dialog.capture, onConfirm = onConfirm, onCancel = onCancelConfirmation)
        HomeDialog.RecognitionFailed -> RecognitionFailedDialog(onDismiss = onDismissRecognitionFailed)
        HomeDialog.TranslationFailed -> TranslationFailedDialog(onRetry = onRetryTranslation, onCancel = onDismissTranslationFailed)
    }
}

/** Коралова кнопка-мікрофон з кільцями пульсу під час запису (історія 1/A04, R14). */
@Composable
private fun PulsingMicButton(isListening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mic-pulse-progress",
    )
    val micDescription = if (isListening) {
        stringResource(R.string.home_mic_listening_content_description)
    } else {
        stringResource(R.string.home_mic_content_description)
    }

    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            PulseRing(scaleFraction = pulse, alphaFraction = 1f - pulse)
            PulseRing(scaleFraction = (pulse + 0.5f) % 1f, alphaFraction = 1f - (pulse + 0.5f) % 1f)
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = micDescription }) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PulseRing(scaleFraction: Float, alphaFraction: Float) {
    val minSize = 96.dp
    val maxSize = 220.dp
    val size = minSize + (maxSize - minSize) * scaleFraction
    Box(
        modifier = Modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = (alphaFraction * 0.35f).coerceIn(0f, 0.35f)),
                CircleShape,
            ),
    )
}

/** Поле вводу з кнопкою "Ок" — альтернатива мікрофону (історія 12, R19/R20). */
@Composable
private fun InputRow(text: String, onTextChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.home_input_placeholder)) },
            singleLine = true,
        )
        Button(onClick = onSubmit, enabled = text.isNotBlank()) {
            Text(stringResource(R.string.home_input_submit))
        }
    }
}

/** Історія 5, R16: українське слово без головної мови — вибір словника.
 *
 * `confirmButton`/`dismissButton` тут НЕ "ок"/"скасувати" — це два рівноправні
 * вибори (німецький/англійський словник), просто позичені в `AlertDialog`
 * заради готового layout-у "дві кнопки поруч". Скасувати нема куди: слово вже
 * визначене як українське, словник обрати необхідно (звідси й порожній
 * `onDismissRequest` — тап поза діалогом/back не закриває його). */
@Composable
private fun ChooseDictionaryDialog(onChoose: (DictionaryLanguage) -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Вибір обов'язковий — без варіанту "жоден" зберігати нема куди. */ },
        title = { Text(stringResource(R.string.home_choose_dictionary_title)) },
        text = {},
        confirmButton = {
            TextButton(onClick = { onChoose(DictionaryLanguage.GERMAN) }) {
                Text(stringResource(R.string.home_choose_dictionary_german))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(DictionaryLanguage.ENGLISH) }) {
                Text(stringResource(R.string.home_choose_dictionary_english))
            }
        },
    )
}

/** Історія 11, R18: підсумок перед записом — Ок/Cancel. */
@Composable
private fun ConfirmDialog(capture: WordCapture, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.home_confirm_title)) },
        text = {
            Column {
                Text(text = capture.ukrainian, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.home_confirm_translation_label), style = MaterialTheme.typography.labelLarge)
                Text(text = listOfNotNull(capture.translation1, capture.translation2).joinToString(", "))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.home_confirm_example_label), style = MaterialTheme.typography.labelLarge)
                Text(text = capture.example)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_confirm_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.home_confirm_cancel)) }
        },
    )
}

/** Історія 2, R14.1: розпізнавання не вдалося — діалог, а не тиша. */
@Composable
private fun RecognitionFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_recognition_failed_title)) },
        text = { Text(stringResource(R.string.home_recognition_failed_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_recognition_failed_ok)) }
        },
    )
}

/** Історія 14, R14.2: переклад недоступний — кнопка "повторити", слово лишається в полі. */
@Composable
private fun TranslationFailedDialog(onRetry: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.home_translation_failed_title)) },
        text = { Text(stringResource(R.string.home_translation_failed_message)) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.home_translation_failed_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.home_translation_failed_cancel)) }
        },
    )
}
