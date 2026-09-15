package com.learneverywhere.app.ui.theme

import androidx.compose.ui.graphics.Color

// Коралова акцентна енергія — узгоджено з користувачем через Stitch-макети
// (spec.md §«Макети Stitch», reference.md). Один акцент на обидві теми:
// день і ніч відрізняються лише фоном/поверхнями, не акцентом.
val Coral = Color(0xFFFF6A45)
val CoralDark = Color(0xFFE85A37)
val OnCoral = Color(0xFFFFFFFF)

// День — теплий нейтральний фон (той самий, що window_background у colors.xml).
val WarmBackgroundLight = Color(0xFFFDF6F3)
val WarmSurfaceLight = Color(0xFFFFFFFF)
val WarmOnBackgroundLight = Color(0xFF231A17)

// Ніч — темний теплий (не чорно-синій), той самий відтінок сім'ї, що й день.
val WarmBackgroundDark = Color(0xFF0E0B12)
val WarmSurfaceDark = Color(0xFF1C1620)
val WarmOnBackgroundDark = Color(0xFFF3EAE6)
