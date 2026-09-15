package com.learneverywhere.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.learneverywhere.app.LearnEverywhereApplication
import com.learneverywhere.app.R
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Повний екран налаштувань (тікет 04, зона `ui/settings/`) — читає й пише
 * `SettingsRepository` з тікета 01. Мова інтерфейсу тут — уже успадкована
 * локаль: override `LocalContext` за `settings.interfaceLanguage` стоїть
 * на корені застосунку (`InterfaceLocaleProvider` в `InterfaceLocale.kt`,
 * обгортає `LearnEverywhereNavHost`), не тут — так само, як буде для
 * будь-якого іншого екрана.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    settingsRepository: SettingsRepository =
        (LocalContext.current.applicationContext as LearnEverywhereApplication).container.settingsRepository,
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { settingsRepository.update(transform) }
    }

    SettingsScreenContent(
        modifier = modifier,
        settings = settings,
        onBack = onBack,
        onMainLanguageChange = { update { s -> s.copy(mainLanguage = it) } },
        onInterfaceLanguageChange = { update { s -> s.copy(interfaceLanguage = it) } },
        onLoopChange = { update { s -> s.copy(loop = it) } },
        onShuffleChange = { update { s -> s.copy(shuffle = it) } },
        onUkrainianRepeatCountChange = { update { s -> s.copy(ukrainianRepeatCount = it) } },
        onUkrainianRepeatPauseChange = { update { s -> s.copy(ukrainianRepeatPause = it) } },
        onTranslationDelayChange = { update { s -> s.copy(translationDelay = it) } },
        onTranslationRepeatCountChange = { update { s -> s.copy(translationRepeatCount = it) } },
        onTranslationRepeatPauseChange = { update { s -> s.copy(translationRepeatPause = it) } },
        onExamplePauseChange = { update { s -> s.copy(examplePause = it) } },
        onNextWordPauseChange = { update { s -> s.copy(nextWordPause = it) } },
        onIncludeExampleChange = { update { s -> s.copy(includeExampleInPlayback = it) } },
        onShowCardChange = { update { s -> s.copy(showCardDuringPlayback = it) } },
        onResetToDefaults = { scope.launch { settingsRepository.resetToDefaults() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    modifier: Modifier,
    settings: AppSettings,
    onBack: () -> Unit,
    onMainLanguageChange: (MainLanguage) -> Unit,
    onInterfaceLanguageChange: (InterfaceLanguage) -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onUkrainianRepeatCountChange: (Int) -> Unit,
    onUkrainianRepeatPauseChange: (Int) -> Unit,
    onTranslationDelayChange: (Int) -> Unit,
    onTranslationRepeatCountChange: (Int) -> Unit,
    onTranslationRepeatPauseChange: (Int) -> Unit,
    onExamplePauseChange: (Int) -> Unit,
    onNextWordPauseChange: (Int) -> Unit,
    onIncludeExampleChange: (Boolean) -> Unit,
    onShowCardChange: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_main_language)) {
                LanguageSegmentedRow(
                    options = listOf(
                        MainLanguage.NONE to stringResource(R.string.settings_main_language_none),
                        MainLanguage.GERMAN to stringResource(R.string.settings_main_language_german),
                        MainLanguage.ENGLISH to stringResource(R.string.settings_main_language_english),
                    ),
                    selected = settings.mainLanguage,
                    onSelectedChange = onMainLanguageChange,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_interface_language)) {
                LanguageSegmentedRow(
                    options = listOf(
                        InterfaceLanguage.UKRAINIAN to stringResource(R.string.settings_interface_language_ukrainian),
                        InterfaceLanguage.ENGLISH to stringResource(R.string.settings_interface_language_english),
                        InterfaceLanguage.GERMAN to stringResource(R.string.settings_interface_language_german),
                    ),
                    selected = settings.interfaceLanguage,
                    onSelectedChange = onInterfaceLanguageChange,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_playback)) {
                val secondsUnit = stringResource(R.string.settings_seconds_unit)

                CheckboxRow(
                    label = stringResource(R.string.settings_loop_label),
                    checked = settings.loop,
                    onCheckedChange = onLoopChange,
                )
                CheckboxRow(
                    label = stringResource(R.string.settings_shuffle_label),
                    checked = settings.shuffle,
                    onCheckedChange = onShuffleChange,
                )
                HorizontalDivider()
                NumberOptionRow(
                    label = stringResource(R.string.settings_ukrainian_repeat_count_label),
                    value = settings.ukrainianRepeatCount,
                    onValueChange = onUkrainianRepeatCountChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_ukrainian_repeat_pause_label),
                    value = settings.ukrainianRepeatPause,
                    unitSuffix = secondsUnit,
                    onValueChange = onUkrainianRepeatPauseChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_translation_delay_label),
                    value = settings.translationDelay,
                    unitSuffix = secondsUnit,
                    onValueChange = onTranslationDelayChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_translation_repeat_count_label),
                    value = settings.translationRepeatCount,
                    onValueChange = onTranslationRepeatCountChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_translation_repeat_pause_label),
                    value = settings.translationRepeatPause,
                    unitSuffix = secondsUnit,
                    onValueChange = onTranslationRepeatPauseChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_example_pause_label),
                    value = settings.examplePause,
                    unitSuffix = secondsUnit,
                    onValueChange = onExamplePauseChange,
                )
                NumberOptionRow(
                    label = stringResource(R.string.settings_next_word_pause_label),
                    value = settings.nextWordPause,
                    unitSuffix = secondsUnit,
                    onValueChange = onNextWordPauseChange,
                )
                HorizontalDivider()
                CheckboxRow(
                    label = stringResource(R.string.settings_include_example_label),
                    checked = settings.includeExampleInPlayback,
                    onCheckedChange = onIncludeExampleChange,
                )
                CheckboxRow(
                    label = stringResource(R.string.settings_show_card_label),
                    checked = settings.showCardDuringPlayback,
                    onCheckedChange = onShowCardChange,
                )
            }

            OutlinedButton(onClick = onResetToDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_reset_button))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LanguageSegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelectedChange: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelectedChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumberOptionRow(
    label: String,
    value: Int,
    unitSuffix: String? = null,
    onValueChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    fun display(option: Int) = if (unitSuffix != null) "$option $unitSuffix" else option.toString()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(display(value))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SettingsOptions.SECONDS_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
