package com.learneverywhere.app.ui.dictionaries

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learneverywhere.app.LearnEverywhereApplication
import com.learneverywhere.app.R
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.playback.PlaybackController

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
    val context = LocalContext.current
    val application = context.applicationContext as LearnEverywhereApplication
    val scope = rememberCoroutineScope()
    // Тікет 08 не поклав жодного `PlaybackController` в `AppContainer` (di/ —
    // поза зоною цього тікета) — конструюємо тут, тим самим прийомом, що й
    // `DictionariesViewModel` вище. `PlaybackController.active` підхоплюється
    // першим: якщо програвання вже триває (стартоване до перестворення цього
    // composable — напр. користувач перемкнув нижню вкладку і повернувся),
    // такий самий інстанс, а не порожній новий, лишається джерелом стану.
    val playbackController = remember {
        PlaybackController.active ?: PlaybackController(
            context = application,
            dictionaryRepository = application.container.dictionaryRepository,
            settingsRepository = application.container.settingsRepository,
            speaker = application.container.speaker,
            scope = scope,
        )
    }
    val viewModel = remember {
        DictionariesViewModel(
            dictionaryRepository = application.container.dictionaryRepository,
            settingsRepository = application.container.settingsRepository,
            scope = scope,
            playback = playbackController,
        )
    }

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val detailUiState by viewModel.detailUiState.collectAsState()
    val importDialog by viewModel.importDialog.collectAsState()
    val playbackUiState by viewModel.playbackUiState.collectAsState()

    // Тікет 09 (історія 21/R10.1) — системний вибір файлу для імпорту; сам
    // парсинг і валідація JSON лишаються в `DictionaryRepository`, сюди
    // приходить лише готовий Uri обраного файлу.
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::onImportFileSelected)
    }

    // Тап на картку словника заміняє список його вмістом на тому ж екрані
    // (історія 38/R47) — окремого Compose-маршруту немає навмисно, це один екран.
    val detail = detailUiState
    if (detail != null) {
        DictionaryDetailContent(
            modifier = modifier,
            state = detail,
            onBack = viewModel::closeDictionary,
            onRename = viewModel::renameDictionary,
            onDeleteDictionary = viewModel::deleteDictionary,
            onSelectWord = viewModel::selectWord,
            onUpdateWord = viewModel::updateWord,
            onDeleteWord = viewModel::deleteWord,
            onSearchQueryChange = viewModel::setWordSearchQuery,
            onExport = {
                // Тікет 09 (історія 20/R10) — репозиторій пише JSON у кеш і
                // повертає content://-Uri, тут лише системний діалог "поділитися".
                viewModel.exportDictionary(detail.dictionary.id) { uri ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            },
        )
    } else {
        DictionariesContent(
            modifier = modifier,
            selectedLanguage = selectedLanguage,
            uiState = uiState,
            playbackUiState = playbackUiState,
            onTabSelected = viewModel::selectTab,
            onSetDefault = viewModel::setDefault,
            onCreateDictionary = viewModel::createDictionary,
            onOpenDictionary = viewModel::openDictionary,
            onImportClick = { importFileLauncher.launch("application/json") },
            onPlayHeaderClick = viewModel::onPlayHeaderClick,
            onStopPlaybackClick = viewModel::onStopPlaybackClick,
        )
    }

    when (val dialog = importDialog) {
        is ImportDialog.ChooseLanguage -> ChooseImportLanguageDialog(
            onConfirm = { language -> viewModel.onImportLanguageChosen(dialog.uri, language) },
            onDismiss = viewModel::onDismissImportDialog,
        )
        ImportDialog.InvalidFile -> InvalidImportFileDialog(onDismiss = viewModel::onDismissImportDialog)
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionariesContent(
    modifier: Modifier,
    selectedLanguage: DictionaryLanguage,
    uiState: DictionariesUiState,
    playbackUiState: PlaybackUiState,
    onTabSelected: (DictionaryLanguage) -> Unit,
    onSetDefault: (Long) -> Unit,
    onCreateDictionary: (String) -> Unit,
    onOpenDictionary: (Long) -> Unit,
    onImportClick: () -> Unit,
    onPlayHeaderClick: () -> Unit,
    onStopPlaybackClick: () -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    val importDescription = stringResource(R.string.dictionaries_import_content_description)
                    IconButton(
                        onClick = onImportClick,
                        modifier = Modifier.semantics { contentDescription = importDescription },
                    ) {
                        Text(text = "📥", style = MaterialTheme.typography.headlineSmall)
                    }
                },
            )
        },
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
                DefaultDictionaryHeader(
                    item = uiState.defaultItem,
                    playbackUiState = playbackUiState,
                    onPlayClick = onPlayHeaderClick,
                    onStopClick = onStopPlaybackClick,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.items, key = { it.dictionary.id }) { item ->
                        DictionaryCard(
                            item = item,
                            onSetDefault = { onSetDefault(item.dictionary.id) },
                            onOpen = { onOpenDictionary(item.dictionary.id) },
                        )
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

/**
 * Хедер з дефолтним словником і кнопкою плей (історія 25/27/28, R42/R43.1/R44).
 * Кнопка плей: нічого не грає — стартує; грає — пауза/відтворити (та сама дія,
 * що в мініплеєрі, 08). Поки [PlaybackUiState.isActive] — поруч з'являється
 * кнопка стоп (критерій приймання тікета 10 — пауза/стоп доступні з екрана).
 * Картка поточного слова — лише коли [PlaybackUiState.showCard] (налаштування
 * 04 + справді щось грає).
 */
@Composable
private fun DefaultDictionaryHeader(
    item: DictionaryListItem?,
    playbackUiState: PlaybackUiState,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playbackUiState.isActive) {
                    val stopDescription = stringResource(R.string.playback_stop_content_description)
                    IconButton(
                        onClick = onStopClick,
                        modifier = Modifier.semantics { contentDescription = stopDescription },
                    ) {
                        Text(text = "⏹", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                val playDescription = if (playbackUiState.isActive) {
                    stringResource(
                        if (playbackUiState.isPlaying) {
                            R.string.playback_pause_content_description
                        } else {
                            R.string.playback_resume_content_description
                        },
                    )
                } else {
                    stringResource(R.string.dictionaries_play_content_description)
                }
                IconButton(
                    onClick = onPlayClick,
                    enabled = playEnabled,
                    modifier = Modifier.semantics { contentDescription = playDescription },
                ) {
                    val glyph = if (playbackUiState.isActive && playbackUiState.isPlaying) "⏸" else "▶"
                    Text(text = glyph, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        if (!playEnabled) {
            Text(
                text = stringResource(R.string.dictionaries_header_no_words),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (playbackUiState.showCard) {
            val word = playbackUiState.currentWord
            if (word != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PlaybackWordCard(word = word)
            }
        }
    }
}

/**
 * Картка поточного слова під час програвання (історія 28, R44) — з'являється
 * лише коли в налаштуваннях (04) увімкнено «показати картку» і щось грає;
 * оновлюється синхронно, бо читає той самий `PlaybackController.currentWord`,
 * що керує озвученням (`DictionariesViewModel.playbackUiState`).
 */
@Composable
private fun PlaybackWordCard(word: WordEntry, modifier: Modifier = Modifier) {
    val translations = listOfNotNull(word.translation1, word.translation2).joinToString(", ")
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.dictionaries_now_playing_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = word.ukrainian, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = translations, style = MaterialTheme.typography.titleMedium)
            Text(text = word.example, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
    }
}

/** Картка словника з кільцем-індикатором кількості слів (історія 43/54, R52, A03). */
@Composable
private fun DictionaryCard(
    item: DictionaryListItem,
    onSetDefault: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
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

/**
 * Деталі словника (тікет 07, історія 38-41/R47-R50): заміняє список на тому ж
 * екрані. Панель зверху — назад, перейменувати, видалити словник, редагувати/
 * видалити виділене слово. Поле пошуку — лише коли `state.isSearchVisible`
 * (A05, R48.1). `LazyColumn` тримає прокрутку плавною і при 50+ записах,
 * бо рендерить тільки видимі рядки.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryDetailContent(
    modifier: Modifier,
    state: DictionaryDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDeleteDictionary: () -> Unit,
    onSelectWord: (Long) -> Unit,
    onUpdateWord: (WordEntry) -> Unit,
    onDeleteWord: (Long) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExport: () -> Unit,
) {
    var showRenameDialog by rememberSaveable(state.dictionary.id) { mutableStateOf(false) }
    var showDeleteDictionaryConfirm by rememberSaveable(state.dictionary.id) { mutableStateOf(false) }
    var showEditWordDialog by rememberSaveable(state.selectedWordId) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = state.dictionary.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.dictionary_detail_back_content_description),
                        )
                    }
                },
                actions = {
                    val exportDescription = stringResource(R.string.dictionary_detail_export_content_description)
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.semantics { contentDescription = exportDescription },
                    ) {
                        Text(text = "📤")
                    }
                    val renameDescription = stringResource(R.string.dictionary_detail_rename_content_description)
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.semantics { contentDescription = renameDescription },
                    ) {
                        Text(text = "✎")
                    }
                    val deleteDictionaryDescription = stringResource(R.string.dictionary_detail_delete_dictionary_content_description)
                    IconButton(
                        onClick = { showDeleteDictionaryConfirm = true },
                        modifier = Modifier.semantics { contentDescription = deleteDictionaryDescription },
                    ) {
                        Text(text = "🗑")
                    }
                    val editWordDescription = stringResource(R.string.dictionary_detail_edit_word_content_description)
                    IconButton(
                        onClick = { showEditWordDialog = true },
                        enabled = state.selectedWord != null,
                        modifier = Modifier.semantics { contentDescription = editWordDescription },
                    ) {
                        Text(text = "🖊")
                    }
                    val deleteWordDescription = stringResource(R.string.dictionary_detail_delete_word_content_description)
                    IconButton(
                        onClick = { state.selectedWord?.let { onDeleteWord(it.id) } },
                        enabled = state.selectedWord != null,
                        modifier = Modifier.semantics { contentDescription = deleteWordDescription },
                    ) {
                        Text(text = "✕")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (state.isSearchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 8.dp),
                    label = { Text(text = stringResource(R.string.dictionary_detail_search_label)) },
                    singleLine = true,
                )
            }

            if (state.filteredWords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.dictionary_detail_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp, 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filteredWords, key = { it.id }) { word ->
                        WordRow(
                            word = word,
                            selected = word.id == state.selectedWordId,
                            onClick = { onSelectWord(word.id) },
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDictionaryDialog(
            initialName = state.dictionary.name,
            onConfirm = { name ->
                onRename(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDictionaryConfirm) {
        DeleteDictionaryConfirmDialog(
            dictionaryName = state.dictionary.name,
            onConfirm = {
                showDeleteDictionaryConfirm = false
                onDeleteDictionary()
            },
            onDismiss = { showDeleteDictionaryConfirm = false },
        )
    }

    val selectedWord = state.selectedWord
    if (showEditWordDialog && selectedWord != null) {
        EditWordDialog(
            word = selectedWord,
            onConfirm = { edited ->
                onUpdateWord(edited)
                showEditWordDialog = false
            },
            onDismiss = { showEditWordDialog = false },
        )
    }
}

/** Один компактний рядок слова: укр. жирним, переклади через кому, приклад курсивом (історія 39/R48). */
@Composable
private fun WordRow(word: WordEntry, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val translations = listOfNotNull(word.translation1, word.translation2).joinToString(", ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 8.dp),
    ) {
        Row {
            Text(text = word.ukrainian, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = " — $translations", style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = word.example,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
    }
}

/** Перейменування словника (R49) — поле, передзаповнене поточною назвою. */
@Composable
private fun RenameDictionaryDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionary_detail_rename_dialog_title)) },
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
                Text(text = stringResource(R.string.dictionary_detail_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionary_detail_cancel))
            }
        },
    )
}

/** Видалення словника — з підтвердженням (критерій приймання тікета 07). */
@Composable
private fun DeleteDictionaryConfirmDialog(dictionaryName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionary_detail_delete_dialog_title)) },
        text = { Text(text = stringResource(R.string.dictionary_detail_delete_dialog_message, dictionaryName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.dictionary_detail_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionary_detail_cancel))
            }
        },
    )
}

/** Редагування виділеного слова — усі поля разом, Ок/Cancel (брифінг, R49/R50). */
@Composable
private fun EditWordDialog(word: WordEntry, onConfirm: (WordEntry) -> Unit, onDismiss: () -> Unit) {
    var ukrainian by rememberSaveable { mutableStateOf(word.ukrainian) }
    var translation1 by rememberSaveable { mutableStateOf(word.translation1) }
    var translation2 by rememberSaveable { mutableStateOf(word.translation2.orEmpty()) }
    var example by rememberSaveable { mutableStateOf(word.example) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionary_detail_edit_word_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ukrainian,
                    onValueChange = { ukrainian = it },
                    label = { Text(text = stringResource(R.string.dictionary_detail_field_ukrainian)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = translation1,
                    onValueChange = { translation1 = it },
                    label = { Text(text = stringResource(R.string.dictionary_detail_field_translation1)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = translation2,
                    onValueChange = { translation2 = it },
                    label = { Text(text = stringResource(R.string.dictionary_detail_field_translation2)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text(text = stringResource(R.string.dictionary_detail_field_example)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    word.copy(
                        ukrainian = ukrainian.trim(),
                        translation1 = translation1.trim(),
                        translation2 = translation2.trim().ifBlank { null },
                        example = example.trim(),
                    ),
                )
            }, enabled = ukrainian.isNotBlank() && translation1.isNotBlank()) {
                Text(text = stringResource(R.string.dictionary_detail_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionary_detail_cancel))
            }
        },
    )
}

/**
 * Мова словника не визначена з JSON-файлу (тікет 09, історія 21/R10.1) —
 * просимо користувача обрати одну з двох перед фактичним імпортом.
 */
@Composable
private fun ChooseImportLanguageDialog(onConfirm: (DictionaryLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionaries_import_choose_language_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DictionaryLanguage.entries.forEach { language ->
                    val (flag, description) = when (language) {
                        DictionaryLanguage.GERMAN ->
                            stringResource(R.string.dictionaries_tab_german_flag) to
                                stringResource(R.string.dictionaries_tab_german_description)
                        DictionaryLanguage.ENGLISH ->
                            stringResource(R.string.dictionaries_tab_english_flag) to
                                stringResource(R.string.dictionaries_tab_english_description)
                    }
                    TextButton(onClick = { onConfirm(language) }) {
                        Text(text = "$flag $description")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionaries_add_dialog_cancel))
            }
        },
    )
}

/** Файл для імпорту не відповідає JSON-схемі (тікет 09, історія 22/R10.2) — нічого не імпортовано. */
@Composable
private fun InvalidImportFileDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dictionaries_import_invalid_file_title)) },
        text = { Text(text = stringResource(R.string.dictionaries_import_invalid_file_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dictionary_detail_confirm))
            }
        },
    )
}
