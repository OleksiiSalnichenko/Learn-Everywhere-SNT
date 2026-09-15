package com.learneverywhere.app.voice

/**
 * Озвучення тексту (spec.md §«Озвучення», interfaces.md §voice). `onDone`
 * викликається, коли фраза договорена — playback (тікет 08/10) чекає на
 * нього, щоб перейти до наступного кроку черги.
 */
interface Speaker {
    fun speak(text: String, locale: SpokenLanguage, onDone: () -> Unit = {})
    fun shutdown()
}
