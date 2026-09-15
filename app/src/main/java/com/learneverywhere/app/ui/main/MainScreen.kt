package com.learneverywhere.app.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.learneverywhere.app.ui.dictionaries.DictionariesScreen
import com.learneverywhere.app.ui.home.HomeScreen

/**
 * Головний і словниковий екрани — по суті два незалежні "таби" (не окремий
 * граф навігації: перемикання без стеку назад, R12/R13 — "мінімум кнопок").
 * Кнопка "налаштування" (історія 23.1) додається в тікеті 04 поверх HomeScreen.
 */
@Composable
fun MainScreen() {
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
        when (selectedTab) {
            BottomNavTab.HOME -> HomeScreen(modifier = Modifier.padding(innerPadding))
            BottomNavTab.DICTIONARIES -> DictionariesScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
