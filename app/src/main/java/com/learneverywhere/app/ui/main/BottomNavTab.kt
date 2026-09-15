package com.learneverywhere.app.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.learneverywhere.app.R

/**
 * Рівно дві вкладки нижньої навігації, без тексту — тільки іконки
 * (історія 23 / R12, R13, брифінг п.2.1).
 */
enum class BottomNavTab(@DrawableRes val iconRes: Int, @StringRes val contentDescriptionRes: Int) {
    HOME(R.drawable.ic_nav_home, R.string.nav_home_content_description),
    DICTIONARIES(R.drawable.ic_nav_dictionary, R.string.nav_dictionaries_content_description),
}
