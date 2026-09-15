package com.learneverywhere.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Чи вже надано `RECORD_AUDIO` (R66i). Перевіряється [AndroidSpeechInput]
 * перед кожним [SpeechInput.listen] і може використовуватись UI-шаром
 * (ui/home, тікет 06), щоб вирішити, чи запитувати дозвіл у рантаймі перед
 * першим використанням мікрофона, перш ніж узагалі намагатись слухати.
 */
fun isMicrophonePermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
