package com.learneverywhere.app.data.model

/**
 * Доменна модель одного слова в словнику: українське слово + до двох
 * перекладів (translation2 == null, якщо варіант перекладу лише один,
 * R06/R07) + приклад речення мовою словника.
 */
data class WordEntry(
    val id: Long,
    val dictionaryId: Long,
    val ukrainian: String,
    val translation1: String,
    val translation2: String?,
    val example: String,
    val isExampleGenerated: Boolean,
)
