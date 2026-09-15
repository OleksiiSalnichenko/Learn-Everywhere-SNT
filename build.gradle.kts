// Кореневий build-скрипт. Плагіни оголошені тут з `apply false` і застосовуються
// в app/build.gradle.kts — стандартна практика багатомодульного (наразі одномодульного,
// на виріст) Gradle-проєкту, щоб версії плагінів були в одному місці (version catalog).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
