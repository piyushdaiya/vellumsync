package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import java.io.File

data class ImportedNoteCacheResult(
    val cacheFile: File,
    val metadataFile: File
)

data class CachedImportedNote(
    val fileName: String,
    val sha256: String,
    val fileSizeBytes: Long,
    val lastModifiedMillis: Long,
    val notePath: String,
    val diagnosticsPath: String?
)

object ImportedNoteCache {
    fun cacheReadOnlyCopy(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        preliminaryReport: SupernoteInspectionReport
    ): ImportedNoteCacheResult {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val importDir = File(context.filesDir, "imported-notes/${preliminaryReport.sha256}")
        importDir.mkdirs()

        val noteFile = File(importDir, safeName)
        noteFile.writeBytes(bytes)

        val metadataFile = File(importDir, "diagnostics.json")
        metadataFile.writeText(preliminaryReport.copy(cachedCopyPath = noteFile.absolutePath).toJson())

        return ImportedNoteCacheResult(
            cacheFile = noteFile,
            metadataFile = metadataFile
        )
    }

    fun listCachedNotes(context: Context): List<CachedImportedNote> {
        val root = File(context.filesDir, "imported-notes")
        if (!root.exists() || !root.isDirectory) return emptyList()

        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { importDir ->
                val sha = importDir.name
                importDir.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file -> file.isFile && file.extension.equals("note", ignoreCase = true) }
                    .map { noteFile ->
                        CachedImportedNote(
                            fileName = noteFile.name,
                            sha256 = sha,
                            fileSizeBytes = noteFile.length(),
                            lastModifiedMillis = noteFile.lastModified(),
                            notePath = noteFile.absolutePath,
                            diagnosticsPath = File(importDir, "diagnostics.json")
                                .takeIf { it.exists() }
                                ?.absolutePath
                        )
                    }
            }
            .sortedByDescending { it.lastModifiedMillis }
            .toList()
    }

    fun readCachedNote(selectionPath: String): ByteArray {
        return File(selectionPath).readBytes()
    }
}
