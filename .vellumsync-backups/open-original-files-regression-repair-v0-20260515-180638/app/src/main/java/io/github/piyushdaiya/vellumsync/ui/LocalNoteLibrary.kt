package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.piyushdaiya.vellumsync.note.CachedImportedNote
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationOverlayStore
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Local library index for VellumSync.
 *
 * This is intentionally small and file-backed so it does not add a database
 * dependency while the app is still stabilizing the Supernote note surface.
 * The index records imported/cached .note files, linked folder metadata,
 * last-opened page, transform mode, and source URI information when available.
 */
data class LibraryNoteEntry(
    val fileName: String,
    val sha256: String,
    val notePath: String,
    val fileSizeBytes: Long,
    val cacheModifiedMillis: Long,
    val importedAtMillis: Long,
    val lastOpenedMillis: Long,
    val sourceUri: String?,
    val sourceTreeUri: String?,
    val sourceDisplayName: String?,
    val sourceSizeBytes: Long?,
    val sourceLastModifiedMillis: Long?,
    val pageCount: Int?,
    val lastOpenedPage: Int,
    val transformModeId: String?,
    val cacheStatus: String
)

data class LibraryFolderScanSummary(
    val scannedFiles: Int,
    val importedFiles: Int,
    val changedFiles: Int,
    val skippedFiles: Int,
    val errors: List<String>
) {
    fun summaryText(): String {
        val base = "Scanned $scannedFiles .note file(s); imported $importedFiles; changed $changedFiles; skipped $skippedFiles."
        return if (errors.isEmpty()) base else "$base Errors: ${errors.take(3).joinToString(" | ")}"
    }
}

object LocalNoteLibrary {
    private const val INDEX_FILE_NAME = "library-index.tsv"
    private const val PREFS_NAME = "vellumsync-local-library"
    private const val KEY_LINKED_FOLDER_URI = "linked-folder-uri"

    fun linkedFolderUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LINKED_FOLDER_URI, null)
    }

    fun saveLinkedFolderUri(context: Context, treeUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LINKED_FOLDER_URI, treeUri.toString())
            .apply()
    }

    fun listLibraryNotes(context: Context): List<LibraryNoteEntry> {
        val indexedBySha = readIndex(context).associateBy { it.sha256 }
        val cached = ImportedNoteCache.listCachedNotes(context)
        val cachedEntries = cached.map { cachedNote ->
            indexedBySha[cachedNote.sha256]?.mergeCached(cachedNote) ?: cachedNote.toLibraryEntry()
        }

        val cachedHashes = cachedEntries.map { it.sha256 }.toSet()
        val indexedOnly = indexedBySha.values
            .filter { it.sha256 !in cachedHashes }
            .map { it.copy(cacheStatus = "missing cached copy") }

        return (cachedEntries + indexedOnly)
            .sortedWith(compareByDescending<LibraryNoteEntry> { it.lastOpenedMillis }.thenByDescending { it.cacheModifiedMillis })
    }

    fun loadLastOpenedPage(context: Context, sha256: String): Int {
        return readIndex(context).firstOrNull { it.sha256 == sha256 }?.lastOpenedPage?.coerceAtLeast(1) ?: 1
    }

    fun markOpened(
        context: Context,
        selection: ViewerNoteSelection,
        pageNumber: Int,
        totalPages: Int,
        transformModeId: String?
    ) {
        val existing = readIndex(context).toMutableList()
        val now = System.currentTimeMillis()
        val index = existing.indexOfFirst { it.sha256 == selection.sha256 }
        val old = existing.getOrNull(index)
        val updated = (old ?: LibraryNoteEntry(
            fileName = selection.fileName,
            sha256 = selection.sha256,
            notePath = selection.notePath,
            fileSizeBytes = selection.fileSizeBytes,
            cacheModifiedMillis = File(selection.notePath).lastModified(),
            importedAtMillis = now,
            lastOpenedMillis = now,
            sourceUri = null,
            sourceTreeUri = null,
            sourceDisplayName = null,
            sourceSizeBytes = null,
            sourceLastModifiedMillis = null,
            pageCount = totalPages,
            lastOpenedPage = pageNumber,
            transformModeId = transformModeId,
            cacheStatus = "cached"
        )).copy(
            fileName = selection.fileName,
            notePath = selection.notePath,
            fileSizeBytes = selection.fileSizeBytes,
            cacheModifiedMillis = File(selection.notePath).lastModified(),
            lastOpenedMillis = now,
            pageCount = totalPages.takeIf { it > 0 } ?: old?.pageCount,
            lastOpenedPage = pageNumber.coerceAtLeast(1),
            transformModeId = transformModeId,
            cacheStatus = if (old?.cacheStatus == "missing cached copy") "cached" else old?.cacheStatus ?: "cached"
        )
        if (index >= 0) existing[index] = updated else existing.add(updated)
        writeIndex(context, existing)
    }

    fun upsertImportedNote(
        context: Context,
        fileName: String,
        sha256: String,
        notePath: String,
        fileSizeBytes: Long,
        pageCount: Int?,
        sourceUri: String?,
        sourceTreeUri: String?,
        sourceDisplayName: String?,
        sourceSizeBytes: Long?,
        sourceLastModifiedMillis: Long?,
        cacheStatus: String
    ) {
        val existing = readIndex(context).toMutableList()
        val now = System.currentTimeMillis()
        val index = existing.indexOfFirst { it.sha256 == sha256 }
        val old = existing.getOrNull(index)
        val updated = LibraryNoteEntry(
            fileName = fileName,
            sha256 = sha256,
            notePath = notePath,
            fileSizeBytes = fileSizeBytes,
            cacheModifiedMillis = File(notePath).lastModified(),
            importedAtMillis = old?.importedAtMillis ?: now,
            lastOpenedMillis = old?.lastOpenedMillis ?: 0L,
            sourceUri = sourceUri ?: old?.sourceUri,
            sourceTreeUri = sourceTreeUri ?: old?.sourceTreeUri,
            sourceDisplayName = sourceDisplayName ?: old?.sourceDisplayName,
            sourceSizeBytes = sourceSizeBytes ?: old?.sourceSizeBytes,
            sourceLastModifiedMillis = sourceLastModifiedMillis ?: old?.sourceLastModifiedMillis,
            pageCount = pageCount ?: old?.pageCount,
            lastOpenedPage = old?.lastOpenedPage ?: 1,
            transformModeId = old?.transformModeId,
            cacheStatus = cacheStatus
        )
        if (index >= 0) existing[index] = updated else existing.add(updated)
        writeIndex(context, existing)
    }

    fun scanAndImportLinkedFolder(context: Context, treeUri: Uri): LibraryFolderScanSummary {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        saveLinkedFolderUri(context, treeUri)

        val found = mutableListOf<FolderSourceDocument>()
        val errors = mutableListOf<String>()
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrElse { throwable ->
            return LibraryFolderScanSummary(
                scannedFiles = 0,
                importedFiles = 0,
                changedFiles = 0,
                skippedFiles = 0,
                errors = listOf(throwable.message ?: "Unable to read selected folder.")
            )
        }

        walkTree(context, treeUri, rootDocumentId, found, errors, depth = 0)

        var imported = 0
        var changed = 0
        var skipped = 0
        val existing = readIndex(context)
        val notes = found.filter { it.kind == FolderSourceKind.NOTE }
        val pdfReferences = found.filter { it.kind == FolderSourceKind.PDF }
        notes.forEach { doc ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
                    ?: error("Unable to read ${doc.displayName}")
                val preliminaryReport = SupernoteNoteInspector.inspect(
                    fileName = doc.displayName,
                    fileSizeBytes = bytes.size.toLong(),
                    bytes = bytes
                )
                val previousBySource = existing.firstOrNull { it.sourceUri == doc.uri.toString() }
                val changedSource = previousBySource != null && previousBySource.sha256 != preliminaryReport.sha256
                val matchingPdf = findMatchingPdfReference(doc, pdfReferences)
                val matchingPdfBytes = matchingPdf?.let { pdf ->
                    context.contentResolver.openInputStream(pdf.uri)?.use { it.readBytes() }
                }
                val cacheResult = ImportedNoteCache.cacheReadOnlyCopy(
                    context = context,
                    fileName = doc.displayName,
                    bytes = bytes,
                    preliminaryReport = preliminaryReport,
                    referencePdfFileName = matchingPdf?.displayName,
                    referencePdfBytes = matchingPdfBytes
                )
                val finalReport = SupernoteNoteInspector.inspect(
                    fileName = doc.displayName,
                    fileSizeBytes = bytes.size.toLong(),
                    bytes = bytes,
                    cachedCopyPath = cacheResult.cacheFile.absolutePath
                )
                upsertImportedNote(
                    context = context,
                    fileName = finalReport.fileName,
                    sha256 = finalReport.sha256,
                    notePath = cacheResult.cacheFile.absolutePath,
                    fileSizeBytes = finalReport.fileSizeBytes,
                    pageCount = finalReport.strokeGeometryReport.totalPages,
                    sourceUri = doc.uri.toString(),
                    sourceTreeUri = treeUri.toString(),
                    sourceDisplayName = doc.displayName,
                    sourceSizeBytes = doc.sizeBytes ?: finalReport.fileSizeBytes,
                    sourceLastModifiedMillis = doc.lastModifiedMillis,
                    cacheStatus = cacheStatusForLinkedImport(changedSource, matchingPdfBytes != null)
                )
                imported += 1
                if (changedSource) changed += 1
            }.onFailure { throwable ->
                skipped += 1
                errors.add("${doc.displayName}: ${throwable.message ?: "scan failed"}")
            }
        }

        return LibraryFolderScanSummary(
            scannedFiles = notes.size,
            importedFiles = imported,
            changedFiles = changed,
            skippedFiles = skipped,
            errors = errors
        )
    }

    private fun walkTree(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        notes: MutableList<FolderSourceDocument>,
        errors: MutableList<String>,
        depth: Int
    ) {
        if (depth > 8) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val childDocumentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: childDocumentId
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkTree(context, treeUri, childDocumentId, notes, errors, depth + 1)
                    } else if (name.endsWith(".note", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)) {
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                        val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else null
                        notes.add(
                            FolderSourceDocument(
                                displayName = name,
                                uri = childUri,
                                sizeBytes = size,
                                lastModifiedMillis = modified,
                                parentDocumentId = parentDocumentId,
                                kind = if (name.endsWith(".pdf", ignoreCase = true)) FolderSourceKind.PDF else FolderSourceKind.NOTE
                            )
                        )
                    }
                }
            }
        }.onFailure { throwable ->
            errors.add(throwable.message ?: "Unable to scan a folder level.")
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    fun queryLastModified(context: Context, uri: Uri): Long? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_FILE_NAME)

    private fun readIndex(context: Context): List<LibraryNoteEntry> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { lineToEntry(line) }.getOrNull() }
    }

    private fun writeIndex(context: Context, entries: List<LibraryNoteEntry>) {
        indexFile(context).writeText(
            entries.distinctBy { it.sha256 }
                .joinToString("\n") { entryToLine(it) }
        )
    }

    private fun CachedImportedNote.toLibraryEntry(): LibraryNoteEntry {
        return LibraryNoteEntry(
            fileName = fileName,
            sha256 = sha256,
            notePath = notePath,
            fileSizeBytes = fileSizeBytes,
            cacheModifiedMillis = lastModifiedMillis,
            importedAtMillis = lastModifiedMillis,
            lastOpenedMillis = 0L,
            sourceUri = null,
            sourceTreeUri = null,
            sourceDisplayName = null,
            sourceSizeBytes = null,
            sourceLastModifiedMillis = null,
            pageCount = null,
            lastOpenedPage = 1,
            transformModeId = null,
            cacheStatus = "cached"
        )
    }

    private fun LibraryNoteEntry.mergeCached(cached: CachedImportedNote): LibraryNoteEntry {
        return copy(
            fileName = cached.fileName,
            notePath = cached.notePath,
            fileSizeBytes = cached.fileSizeBytes,
            cacheModifiedMillis = cached.lastModifiedMillis,
            cacheStatus = if (cacheStatus == "missing cached copy") "cached" else cacheStatus
        )
    }

    private fun entryToLine(entry: LibraryNoteEntry): String {
        return listOf(
            entry.fileName,
            entry.sha256,
            entry.notePath,
            entry.fileSizeBytes.toString(),
            entry.cacheModifiedMillis.toString(),
            entry.importedAtMillis.toString(),
            entry.lastOpenedMillis.toString(),
            entry.sourceUri.orEmpty(),
            entry.sourceTreeUri.orEmpty(),
            entry.sourceDisplayName.orEmpty(),
            entry.sourceSizeBytes?.toString().orEmpty(),
            entry.sourceLastModifiedMillis?.toString().orEmpty(),
            entry.pageCount?.toString().orEmpty(),
            entry.lastOpenedPage.toString(),
            entry.transformModeId.orEmpty(),
            entry.cacheStatus
        ).joinToString("\t") { encode(it) }
    }

    private fun lineToEntry(line: String): LibraryNoteEntry {
        val parts = line.split("\t").map { decode(it) }
        return LibraryNoteEntry(
            fileName = parts.getOrElse(0) { "note.note" },
            sha256 = parts.getOrElse(1) { "" },
            notePath = parts.getOrElse(2) { "" },
            fileSizeBytes = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            cacheModifiedMillis = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            importedAtMillis = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            lastOpenedMillis = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
            sourceUri = parts.getOrNull(7)?.ifBlank { null },
            sourceTreeUri = parts.getOrNull(8)?.ifBlank { null },
            sourceDisplayName = parts.getOrNull(9)?.ifBlank { null },
            sourceSizeBytes = parts.getOrNull(10)?.toLongOrNull(),
            sourceLastModifiedMillis = parts.getOrNull(11)?.toLongOrNull(),
            pageCount = parts.getOrNull(12)?.toIntOrNull(),
            lastOpenedPage = parts.getOrNull(13)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            transformModeId = parts.getOrNull(14)?.ifBlank { null },
            cacheStatus = parts.getOrElse(15) { "cached" }
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

private fun findMatchingPdfReference(
    note: FolderSourceDocument,
    pdfReferences: List<FolderSourceDocument>
): FolderSourceDocument? {
    val noteBase = normalizedBaseName(note.displayName)
    return pdfReferences.firstOrNull { pdf ->
        pdf.parentDocumentId == note.parentDocumentId && normalizedBaseName(pdf.displayName) == noteBase
    } ?: pdfReferences.firstOrNull { pdf ->
        normalizedBaseName(pdf.displayName) == noteBase
    }
}

private fun cacheStatusForLinkedImport(changedSource: Boolean, pdfReferenceCached: Boolean): String {
    val base = if (changedSource) "source hash changed" else "linked folder"
    return if (pdfReferenceCached) "$base + PDF reference" else base
}

private fun normalizedBaseName(fileName: String): String {
    return fileName.substringBeforeLast('.', fileName).lowercase()
}

private enum class FolderSourceKind {
    NOTE,
    PDF
}

private data class FolderSourceDocument(
    val displayName: String,
    val uri: Uri,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?,
    val parentDocumentId: String,
    val kind: FolderSourceKind
)
