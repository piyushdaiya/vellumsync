package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import java.io.File

data class ImportedNoteCacheResult(
    val cacheFile: File,
    val metadataFile: File
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
}
