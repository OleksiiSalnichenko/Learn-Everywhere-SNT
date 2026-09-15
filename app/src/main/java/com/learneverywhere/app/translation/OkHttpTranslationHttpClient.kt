package com.learneverywhere.app.translation

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Реальна реалізація [TranslationHttpClient] через OkHttp — виконує
 * блокуючий GET-запит (викликається з `Dispatchers.IO` в
 * `TranslationServiceImpl`).
 */
class OkHttpTranslationHttpClient(
    private val client: OkHttpClient = OkHttpClient(),
) : TranslationHttpClient {

    override fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("MyMemory HTTP ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Порожня відповідь сервісу перекладу")
        }
    }
}
