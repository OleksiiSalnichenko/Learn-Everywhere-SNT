package com.learneverywhere.app.ui.dictionaries

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learneverywhere.app.LearnEverywhereApplication
import com.learneverywhere.app.R
import com.learneverywhere.app.data.model.DictionaryLanguage

/**
 * Екран "Словники" (тікет 05): вкладки-прапорці, хедер з дефолтним
 * словником і плеєм-заглушкою, список карток з кільцем-індикатором і
 * кнопка додавання нового словника. Затверджений макет — Stitch, проєкт
 * `17266546567257325539` (spec.md §«Макети Stitch»).
 *
 * `DictionariesViewModel` дістає `AppContainer` напряму з `Application`
 * (сервіс-локатор, не Hilt) — так структура навігації (`MainScreen`,
 * `LearnEverywhereNavHost`) лишається незмінною, як вимагає тікет.
 */
@Composable
fun DictionariesScreen(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as LearnEverywhereApplication
    val scope = rememberCoroutineScope()
    val viewModel = remember {
        DictionariesViewModel(
            dictionaryRepository = application.container.dictionaryRepository,
            settingsRepository = application.container.settingsRepository,
            scope = scope,
        )
    }

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    DictionariesContent(
        modifier = modifier,
        selectedLanguage = selectedLanguage,
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onSetDefault = viewModel::setDefault,
        onCreateDictionary = viewModel::createDictionary,
    )
}

@Composable
private fun DictionariesContent(
    modifier: Modifier,
    selectedLanguage: DictionaryLanguage,
    uiState: DictionariesUiState,
    onTabSelected: (DictionaryLanguage) -> Unit,
    onSetDefault: (Long) -> Unit,
    onCreateDictionary: (String) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            val addDescription = stringResource(R.string.dictionaries_add_content_description)
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.semantics { contentDescription = addDescription },
            ) {
                Text(text = "+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LanguageTabs(selected = selectedLanguage, onSelected = onTabSelected)

            if (uiState.items.isEmpty()) {
                EmptyDictionariesInvite(modifier = Modifier.weight(1f))
            } else {
                DefaultDictionaryHeader(item = uiState.defaultItem)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.items, key = { it.dictionary.id }) { item ->
                        DictionaryCard(item = item, onSetDefault = { onSetDefault(item.dictionary.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDictionaryDialog(
            onConfirm = { name ->
                onCreateDictionary(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** Дві вкладки-прапорці (історія 24/R38) — однакова розмітка, різні дані з репозиторію. */
@Composable
private fun LanguageTabs(
    selected: DictionaryLanguage,
    onSelected: (DictionaryLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = DictionaryLanguage.entries
    TabRow(selectedTabIndex = tabs.indexOf(selected), modifier = modifier) {
        tabs.forEach { language ->
            val (flag, description) = when (language) {
                DictionaryLanguage.GERMAN ->
                    stringResource(R.string.dictionaries_tab_german_flag) to
                        stringResource(R.string.dictionaries_tab_german_description)
                DictionaryLanguage.ENGLISH ->
                    stringResource(R.string.dictionaries_tab_english_flag) to
                        stringResource(R.string.dictionaries_tab_english_description)
            }
            Tab(
                selected = language == selected,
                onClick = { onSelected(language) },
                modifier = Modifier.semantics { contentDescription = description },
                text = { Text(text = flag, style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
}

/** Хедер з дефолтним словником і плеєм-заглушкою (історія 25/27, R42/R43.1). */
@Composable
private fun DefaultDictionaryHeader(item: DictionaryListItem?, modifier: Modifier = Modifier) {
    if (item == null) return
    val playEnabled = item.wordCount > 0

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(text = stringResource(R.string.dictionaries_header_label), style = MaterialTheme.typography.bodyMedium)
                Text(text = item.dictionary.name, style = MaterialTheme.typography.titleLarge)
            }
            val playDescription = stringResource(R.string.dictionaries_play_content_description)
            IconButton(
                // Лише UI-заглушка (клікабельна) — реальне відтворення йде в тікет 08/10,
                // тут НЕ винаходимо PlaybackController заново.
                onClick = { /* тікет 08/10 */ },
                enabled = playEnabled,
                modifier = Modifier.semantics { contentDescription = playDescription },
            ) {
                Text(text = "▶", style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (!playEnabled) {
            Text(
                text = stringResource(R.string.dictionaries_header_no_words),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Картка словника з кільцем-індикатором кількості слів (історія 43/54, R52, A03). */
@Composable
private fun DictionaryCard(
    item: DictionaryListItem,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WordCountRing(count = item.wordCount)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.dictionary.name, style = MaterialTheme.typography.titleMedium)
                if (item.dictionary.isDefault) {
                    Text(
                        text = stringResource(R.string.dictionaries_default_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val setDefaultDescription = stringResource(R.string.dictionaries_set_default_content_description)
            RadioButton(
                selected = item.dictionary.isDefault,
                onClick = onSetDefault,
                modifier = Modifier.semantics { contentDescription = setDefaultDescription },
            )
        }
    }
}

private const val RING_FULL_AT_WORD_COUNT = 50f

/** Кільце-індикатор — не сухе число в рядку (критерій приймання тікета 05). */
@Composable
private fun WordCountRing(count: Int, modifier: Modifier = Modifier) {
    val progress = (count / RING_FULL_AT_WORD_COUNT).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val progressColor = MaterialTheme.colorScheme.primary
    val description = pluralStringResource(R.plurals.dictionaries_word_count, count, count)

    Box(
        modifier = modifier
            .size(56.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Text(text = count.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

/** Кнопка "+" відкриває цей діалог з полем назви (історія 42, R51). */
@Composable
private fun AddDictionaryDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionaries_add_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.dictionaries_add_dialog_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(text = stringResource(R.string.dictionaries_add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionaries_add_dialog_cancel))
            }
        },
    )
}

/** Порожній список (видалили останній словник) — запрошення додати, без падіння (критерій приймання тікета 05). */
@Composable
private fun EmptyDictionariesInvite(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.dictionaries_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dictionaries_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
