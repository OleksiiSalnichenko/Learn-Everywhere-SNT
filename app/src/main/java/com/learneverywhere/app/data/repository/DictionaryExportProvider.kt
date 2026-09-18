package com.learneverywhere.app.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Мінімальний `ContentProvider`, що віддає JSON-файли, записані
 * [DictionaryRepositoryImpl.exportToJson] у `context.cacheDir/exports`, як
 * `content://`-Uri для системного діалогу "поділитися" (тікет 09, історія 20).
 *
 * Свідомо не `androidx.core.content.FileProvider`: та реалізація порівнює
 * канонічні шляхи кореня й файлу рядково (`SimplePathStrategy.belongsToRoot`)
 * і під Robolectric на Windows дає хибний
 * `IllegalArgumentException: Failed to find configured root` навіть коли
 * файл справді лежить у дозволеній теці — канонізація кореня й файлу
 * розходиться (відомий крайовий випадок платформи, не логіки застосунку).
 * Тут порівняння шляхів цілком у наших руках, тому воно детерміноване
 * в тестах і на пристрої однаково.
 */
class DictionaryExportProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/json"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileForUri(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    /** Лише файли з `exports/` за точним іменем — жодного виходу за межі теки (обхід шляху "../"). */
    private fun fileForUri(uri: Uri): File {
        val exportsDir = File(requireNotNull(context).cacheDir, EXPORTS_DIR_NAME)
        val fileName = uri.lastPathSegment
        require(!fileName.isNullOrBlank() && File(fileName).name == fileName) { "Invalid export uri: $uri" }
        return File(exportsDir, fileName)
    }

    companion object {
        const val EXPORTS_DIR_NAME = "exports"

        fun uriFor(authority: String, fileName: String): Uri =
            Uri.parse("content://$authority/$EXPORTS_DIR_NAME/$fileName")
    }
}
