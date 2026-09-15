package com.learneverywhere.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.learneverywhere.app.data.model.InterfaceLanguage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Обгортка над Android `SpeechRecognizer` (spec.md §«Голосовий ввід»).
 * Локаль розпізнавання — поточна мова інтерфейсу як базова підказка
 * (остаточну мову слова після розпізнавання вирішує [LanguageDetector] —
 * розпізнавач лише полегшує саме розпізнавання звуку в текст).
 *
 * `RECORD_AUDIO` перевіряється тут перед кожним стартом ([isMicrophonePermissionGranted])
 * — відмова повертається як [SpeechResult.Failed] через Flow, а не кидається
 * винятком (критерій приймання тікета 03). Сам рантайм-запит дозволу —
 * платформний діалог, для якого потрібна `Activity` — ініціює UI-шар
 * (ui/home, тікет 06) до виклику [listen]; тут лише детерміноване
 * "дозволено чи ні" на момент виклику.
 */
class AndroidSpeechInput(
    private val context: Context,
    private val interfaceLanguage: () -> InterfaceLanguage,
) : SpeechInput {

    override fun listen(): Flow<SpeechResult> = callbackFlow {
        if (!isMicrophonePermissionGranted(context)) {
            trySend(SpeechResult.Failed(SpeechFailureReason.PERMISSION_DENIED))
            close()
            return@callbackFlow
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechResult.Failed(SpeechFailureReason.OTHER))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    trySend(SpeechResult.Failed(SpeechFailureReason.NOT_UNDERSTOOD))
                } else {
                    trySend(SpeechResult.Recognized(text))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechResult.Failed(mapSpeechRecognizerError(error)))
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, interfaceLanguage().toSpokenLanguage().toLocale().toLanguageTag())
        }
        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }
}
