package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.piyushdaiya.vellumsync.note.CachedImportedNote
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector
import java.util.Date

@Composable
fun RecentNotesScreen(
    onBackToDeviceCheck: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenViewer: (ViewerNoteSelection) -> Unit
) {
    val context = LocalContext.current
    val recentNotes = remember { mutableStateOf(ImportedNoteCache.listCachedNotes(context)) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            val fileName = queryDisplayNameForRecent(context, uri) ?: uri.lastPathSegment ?: "selected.note"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("Unable to read selected file.")

            val preliminaryReport = SupernoteNoteInspector.inspect(
                fileName = fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes
            )
            val cacheResult = ImportedNoteCache.cacheReadOnlyCopy(
                context = context,
                fileName = fileName,
                bytes = bytes,
                preliminaryReport = preliminaryReport
            )
            val finalReport = SupernoteNoteInspector.inspect(
                fileName = fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes,
                cachedCopyPath = cacheResult.cacheFile.absolutePath
            )

            recentNotes.value = ImportedNoteCache.listCachedNotes(context)
            ViewerNoteSelection(
                fileName = finalReport.fileName,
                sha256 = finalReport.sha256,
                notePath = cacheResult.cacheFile.absolutePath,
                fileSizeBytes = finalReport.fileSizeBytes
            )
        }.onSuccess { selection ->
            errorMessage.value = null
            onOpenViewer(selection)
        }.onFailure { throwable ->
            errorMessage.value = throwable.message ?: "Unable to import note."
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "VellumSync")
        Text(text = "Read-only Supernote .note viewer for Android e-ink tablets.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-note",
                            "*/*"
                        )
                    )
                }
            ) {
                Text(text = "Import .note")
            }

            Button(onClick = {
                recentNotes.value = ImportedNoteCache.listCachedNotes(context)
            }) {
                Text(text = "Refresh")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenDiagnostics) {
                Text(text = "Diagnostics")
            }
            Button(onClick = onBackToDeviceCheck) {
                Text(text = "Device check")
            }
        }

        errorMessage.value?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Import error: $error"
                )
            }
        }

        Text(text = "Recent notes")

        if (recentNotes.value.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "No cached notes yet.")
                    Text(text = "Use Import .note to add a read-only local copy, then open it in Viewer Mode.")
                }
            }
        }

        recentNotes.value.forEach { note ->
            RecentNoteCard(
                note = note,
                onOpenViewer = {
                    onOpenViewer(
                        ViewerNoteSelection(
                            fileName = note.fileName,
                            sha256 = note.sha256,
                            notePath = note.notePath,
                            fileSizeBytes = note.fileSizeBytes
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun RecentNoteCard(
    note: CachedImportedNote,
    onOpenViewer: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = note.fileName)
            Text(text = "Size: ${note.fileSizeBytes} bytes")
            Text(text = "SHA-256: ${note.sha256.take(16)}…")
            Text(text = "Cached: ${Date(note.lastModifiedMillis)}")
            Button(onClick = onOpenViewer) {
                Text(text = "Open")
            }
        }
    }
}

private fun queryDisplayNameForRecent(
    context: Context,
    uri: Uri
): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }
}
