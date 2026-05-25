package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Single
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.io.InputStreamReader
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LayoutFileOperations @Inject constructor(
        @ApplicationContext private val context: Context,
        private val gson: Gson,
        private val settingsRepository: SettingsRepository,
        private val uriHandler: UriHandler,
) {
    companion object {
        const val LAYOUT_FILE_PREFIX = "melonds-layout-"
        const val LAYOUT_FILE_SUFFIX = ".json"

        private val layoutListType: Type = object : TypeToken<List<LayoutConfiguration>>() {}.type
    }

    data class LayoutFileEntry(val displayName: String, val uri: Uri)

    class NoStorageDirectoryException : Exception()
    class LayoutFileReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class LayoutFileWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun getStorageDirectory(): Uri? {
        settingsRepository.getSaveFileDirectory()?.let { return it }
        return settingsRepository.getRomSearchDirectories().firstOrNull()
    }

    fun exportLayout(layout: LayoutConfiguration): Single<String> {
        return Single.fromCallable {
            val directoryUri = getStorageDirectory() ?: throw NoStorageDirectoryException()
            val directory = uriHandler.getUriTreeDocument(directoryUri)
                    ?: throw LayoutFileWriteException("Could not access save directory")

            val fileName = buildFileName(layout)
            directory.findFile(fileName)?.delete()

            val fileDocument = directory.createFile("application/json", fileName)
                    ?: throw LayoutFileWriteException("Could not create layout file")

            val json = gson.toJson(layout)
            context.contentResolver.openOutputStream(fileDocument.uri)?.use { outputStream ->
                outputStream.writer().use { it.write(json) }
            } ?: throw LayoutFileWriteException("Could not write layout file")

            fileName
        }
    }

    fun listExportedLayoutFiles(): Single<List<LayoutFileEntry>> {
        return Single.fromCallable {
            val directoryUri = getStorageDirectory() ?: throw NoStorageDirectoryException()
            val directory = uriHandler.getUriTreeDocument(directoryUri)
                    ?: throw LayoutFileReadException("Could not access save directory")

            directory.listFiles()
                    .filter { file ->
                        file.isFile && file.name?.let { isLayoutExportFileName(it) } == true
                    }
                    .mapNotNull { file ->
                        val name = file.name ?: return@mapNotNull null
                        LayoutFileEntry(name, file.uri)
                    }
                    .sortedBy { it.displayName.lowercase() }
        }
    }

    fun importLayout(fileUri: Uri): Single<LayoutConfiguration> {
        return Single.fromCallable {
            val json = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                InputStreamReader(inputStream).readText()
            } ?: throw LayoutFileReadException("Could not read layout file")

            val layout = parseLayoutJson(json)
            prepareImportedLayout(layout)
        }
    }

    fun buildFileName(layout: LayoutConfiguration): String {
        val baseName = layout.name?.takeIf { it.isNotBlank() }
                ?.let { sanitizeFileName(it) }
                ?: layout.id?.toString()
                ?: "layout"
        return "$LAYOUT_FILE_PREFIX$baseName$LAYOUT_FILE_SUFFIX"
    }

    private fun isLayoutExportFileName(fileName: String): Boolean {
        return fileName.startsWith(LAYOUT_FILE_PREFIX, ignoreCase = true)
                && fileName.endsWith(LAYOUT_FILE_SUFFIX, ignoreCase = true)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
    }

    private fun parseLayoutJson(json: String): LayoutConfiguration {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            val layouts = gson.fromJson<List<LayoutConfiguration>>(trimmed, layoutListType)
            layouts?.firstOrNull() ?: throw LayoutFileReadException("Layout file is empty")
        } else {
            gson.fromJson(trimmed, LayoutConfiguration::class.java)
                    ?: throw LayoutFileReadException("Layout file is invalid")
        }
    }

    private fun prepareImportedLayout(layout: LayoutConfiguration): LayoutConfiguration {
        return layout.copy(
                id = null,
                type = LayoutConfiguration.LayoutType.CUSTOM,
                name = layout.name?.takeIf { it.isNotBlank() } ?: "Imported layout"
        )
    }
}
