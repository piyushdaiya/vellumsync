// marker=vellumsync-import-note-picker-mime-fallback-v0
package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationOverlayStore
import java.text.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

// marker=vellumsync-import-note-async-pipeline-import-logging-v0
@Composable
fun RecentNotesScreen(
    onBackToDeviceCheck: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenViewer: (ViewerNoteSelection) -> Unit
) {
    val context = LocalContext.current
    val libraryNotes = remember { mutableStateOf(LocalNoteLibrary.listLibraryNotes(context)) }
    val statusMessage = remember { mutableStateOf<String?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val linkedFolderUri = remember { mutableStateOf(LocalNoteLibrary.linkedFolderUri(context)) }
    val importInProgress = remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()

    fun refreshLibrary() {
        libraryNotes.value = LocalNoteLibrary.listLibraryNotes(context)
        linkedFolderUri.value = LocalNoteLibrary.linkedFolderUri(context)
    }

    fun openEntry(entry: LibraryNoteEntry) {
        LocalNoteLibrary.markOpened(
            context = context,
            selection = ViewerNoteSelection(
                fileName = entry.fileName,
                sha256 = entry.sha256,
                notePath = entry.notePath,
                fileSizeBytes = entry.fileSizeBytes
            ),
            pageNumber = entry.lastOpenedPage,
            totalPages = entry.pageCount ?: 0,
            transformModeId = entry.transformModeId
        )
        refreshLibrary()
        onOpenViewer(
            ViewerNoteSelection(
                fileName = entry.fileName,
                sha256 = entry.sha256,
                notePath = entry.notePath,
                fileSizeBytes = entry.fileSizeBytes
            )
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Log.i("VellumSyncOpen", "import picker result received uri=${uri}")
        importInProgress.value = true
        statusMessage.value = "Importing note…"
        errorMessage.value = null
        importScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = LocalNoteLibrary.queryDisplayName(context, uri)
                        ?: uri.lastPathSegment
                        ?: "selected.note"
                    Log.i("VellumSyncOpen", "import openInputStream start uri=${uri}")
                    val startedAt = System.currentTimeMillis()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                        ?: error("Unable to read selected file.")
                    val elapsed = System.currentTimeMillis() - startedAt
                    Log.i("VellumSyncOpen", "import openInputStream end bytes=${bytes.size} elapsedMs=${elapsed}")

                    Log.i("VellumSyncOpen", "import cache write start file=${fileName}")
                    val cacheResult = ImportedNoteCache.cacheReadOnlyCopy(
                        context = context,
                        fileName = fileName,
                        bytes = bytes
                    )
                    Log.i("VellumSyncOpen", "import cache write end path=${cacheResult.cacheFile.absolutePath}")

                    val sha = ImportedNoteCache.sha256(bytes)
                    LocalNoteLibrary.upsertImportedNote(
                        context = context,
                        fileName = fileName,
                        sha256 = sha,
                        notePath = cacheResult.cacheFile.absolutePath,
                        fileSizeBytes = bytes.size.toLong(),
                        pageCount = null,
                        sourceUri = uri.toString(),
                        sourceTreeUri = null,
                        sourceDisplayName = fileName,
                        sourceSizeBytes = bytes.size.toLong(),
                        sourceLastModifiedMillis = LocalNoteLibrary.queryLastModified(context, uri),
                        cacheStatus = "local import"
                    )
                    ViewerNoteSelection(
                        fileName = fileName,
                        sha256 = sha,
                        notePath = cacheResult.cacheFile.absolutePath,
                        fileSizeBytes = bytes.size.toLong()
                    )
                }
            }
            importInProgress.value = false
            result.onSuccess { selection ->
                errorMessage.value = null
                statusMessage.value = "Imported ${selection.fileName}."
                refreshLibrary()
                Log.i("VellumSyncOpen", "import navigation to viewer start file=${selection.fileName}")
                onOpenViewer(selection)
                Log.i("VellumSyncOpen", "import navigation to viewer end file=${selection.fileName}")
            }.onFailure { throwable ->
                errorMessage.value = throwable.message ?: "Unable to import note."
                statusMessage.value = null
                Log.e("VellumSyncOpen", "import failed", throwable)
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            LocalNoteLibrary.scanAndImportLinkedFolder(context, uri)
        }.onSuccess { summary ->
            errorMessage.value = null
            statusMessage.value = summary.summaryText()
            refreshLibrary()
        }.onFailure { throwable ->
            errorMessage.value = throwable.message ?: "Unable to scan selected folder."
        }
    }

    fun rescanLinkedFolder() {
        val saved = linkedFolderUri.value
        if (saved.isNullOrBlank()) {
            statusMessage.value = "No notes folder is linked yet. Choose a folder first."
            return
        }
        runCatching {
            LocalNoteLibrary.scanAndImportLinkedFolder(context, Uri.parse(saved))
        }.onSuccess { summary ->
            errorMessage.value = null
            statusMessage.value = summary.summaryText()
            refreshLibrary()
        }.onFailure { throwable ->
            throwable.printStackTrace()
            errorMessage.value = throwable.message ?: "Unable to rescan linked folder."
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "VellumSync", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = "Supernote-created notes · Boox stylus overlay · safe sync copies", fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    enabled = !importInProgress.value,
                    onClick = {
                        Log.i("VellumSyncOpen", "import button tapped")
                        importLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/x-note",
                                "*/*"
                            )
                        )
                    }
                ) { Text(text = if (importInProgress.value) "Importing…" else "Import .note") }
                Button(onClick = { folderLauncher.launch(null) }) { Text(text = "Choose folder") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { refreshLibrary(); statusMessage.value = "Library refreshed." }) { Text(text = "Refresh") }
            TextButton(onClick = { rescanLinkedFolder() }) { Text(text = "Rescan folder") }
        }

        linkedFolderUri.value?.let { folder ->
            CompactInfoCard(title = "Linked notes folder", detail = folder)
        }
        statusMessage.value?.let { message -> CompactInfoCard(title = "Status", detail = message) }
        errorMessage.value?.let { error -> CompactInfoCard(title = "Error", detail = error) }

        Divider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Notes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = "${libraryNotes.value.size} item(s)", fontSize = 13.sp)
        }

        if (libraryNotes.value.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    NoteIcon()
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "No Supernote notes yet", fontWeight = FontWeight.Bold)
                        Text(text = "Import a Supernote-created .note file or choose a folder containing .note files.")
                    }
                }
            }
        }

        libraryNotes.value.forEach { entry ->
            LibraryNoteRow(
                entry = entry,
                onOpenViewer = { openEntry(entry) }
            )
        }
    }
}

@Composable
private fun CompactInfoCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = detail, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryNoteRow(
    entry: LibraryNoteEntry,
    onOpenViewer: () -> Unit
) {
    val context = LocalContext.current
    val overlaySummary = remember(entry.sha256) { LocalAnnotationOverlayStore.noteSummary(context, entry.sha256) }
    val dateText = if (entry.lastOpenedMillis > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.lastOpenedMillis))
    } else {
        "Never opened"
    }
    val pageText = "${entry.pageCount?.toString() ?: "?"} page(s)"
    val overlayText = if (overlaySummary.hasOverlay) {
        "Overlay: ${overlaySummary.totalOverlayStrokes} stroke(s), ${overlaySummary.annotatedPageCount} page(s)"
    } else {
        "Overlay: none"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoteIcon()
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = entry.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "$pageText · ${entry.cacheStatus} · Last opened: $dateText", fontSize = 12.sp)
                Text(text = "$overlayText · Last page: ${entry.lastOpenedPage}", fontSize = 12.sp)
            }
            Button(onClick = onOpenViewer) { Text(text = "Open") }
        }
    }
}

@Composable
private fun NoteIcon() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(color = Color(0xFFE0E0E0), shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "▤", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
