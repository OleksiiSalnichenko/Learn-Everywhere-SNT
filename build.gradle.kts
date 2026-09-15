// Кореневий build-скрипт. Плагіни оголошені тут з `apply false` і застосовуються
// в app/build.gradle.kts — стандартна практика багатомодульного (наразі одномодульного,
// на виріст) Gradle-проєкту, щоб версії плагінів були в одному місці (version catalog).
// AGP 9.0+ включає підтримку Kotlin вбудовано — окремий плагін
// org.jetbrains.kotlin.android більше не застосовується (конфліктує з вбудованою
// підтримкою), лишається тільки kotlin.plugin.compose для Compose-компілятора.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
