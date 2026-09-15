package com.learneverywhere.app.voice

import kotlinx.coroutines.flow.Flow

/**
 * Голосовий ввід (R14, interfaces.md §voice). Один виклик [listen] починає
 * слухати і повертає Flow, що випромінює рівно один [SpeechResult]
 * (розпізнаний текст або помилку) і завершується.
 */
interface SpeechInput {
    fun listen(): Flow<SpeechResult>
}
