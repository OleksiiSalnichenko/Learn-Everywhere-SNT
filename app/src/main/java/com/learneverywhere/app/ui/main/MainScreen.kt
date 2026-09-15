package com.learneverywhere.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.learneverywhere.app.R
import com.learneverywhere.app.ui.dictionaries.DictionariesScreen
import com.learneverywhere.app.ui.home.HomeScreen

/**
 * Головний і словниковий екрани — по суті два незалежні "таби" (не окремий
 * граф навігації: перемикання без стеку назад, R12/R13 — "мінімум кнопок").
 * Кнопка "налаштування" (історія 23.1 / R22, тікет 04) — іконка у верхньому
 * куті поверх HomeScreen, як і було заплановано тікетом 01.
 */
@Composable
fun MainScreen(onSettingsClick: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.contentDescriptionRes),
                            )
                        },
                        label = null,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                BottomNavTab.HOME -> HomeScreen(modifier = Modifier.fillMaxSize())
                BottomNavTab.DICTIONARIES -> DictionariesScreen(modifier = Modifier.fillMaxSize())
            }
            if (selectedTab == BottomNavTab.HOME) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings_open_content_description),
                    )
                }
            }
        }
    }
}
