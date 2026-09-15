package com.learneverywhere.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.UUID

/**
 * Один екземпляр `TextToSpeech` на застосунок (spec.md §«Озвучення») —
 * локаль виставляється перед кожною фразою залежно від мови сказаного
 * ([SpokenLanguage.toLocale]). Виклики [speak] до готовності двигуна TTS
 * ставляться в чергу і виконуються одразу після ініціалізації.
 */
class AndroidSpeaker(context: Context) : Speaker {

    private var isReady = false
    private val pendingWhileInitializing = mutableListOf<() -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
        val pending = pendingWhileInitializing.toList()
        pendingWhileInitializing.clear()
        if (isReady) pending.forEach { it() }
    }

    override fun speak(text: String, locale: SpokenLanguage, onDone: () -> Unit) {
        val speakNow: () -> Unit = {
            tts.language = locale.toLocale()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    onDone()
                }

                @Deprecated("Deprecated in Java", ReplaceWith(""))
                override fun onError(utteranceId: String?) {
                    onDone()
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
        if (isReady) speakNow() else pendingWhileInitializing.add(speakNow)
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
