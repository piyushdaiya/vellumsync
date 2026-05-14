package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import io.github.piyushdaiya.vellumsync.note.SupernoteInspectionReport
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector

@Composable
fun NoteInspectorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val report = remember { mutableStateOf<SupernoteInspectionReport?>(null) }
    val error = remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "selected.note"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("Unable to read selected file.")

            SupernoteNoteInspector.inspect(
                fileName = fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes
            )
        }.onSuccess {
            report.value = it
            error.value = null
        }.onFailure {
            error.value = it.message ?: "Unknown error while reading file."
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Supernote .note inspector")

        Text(
            text = "This screen performs read-only marker inspection. It does not modify the selected file."
        )

        Button(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = {
                picker.launch(
                    arrayOf(
                        "application/octet-stream",
                        "application/x-note",
                        "*/*"
                    )
                )
            }
        ) {
            Text(text = "Select .note file")
        }

        Button(onClick = onBack) {
            Text(text = "Back to device check")
        }

        error.value?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Error: $it"
                )
            }
        }

        report.value?.let { inspection ->
            InspectionReportCard(report = inspection)
        }
    }
}

@Composable
private fun InspectionReportCard(report: SupernoteInspectionReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "File: ${report.fileName}")
            Text(text = "Size: ${report.fileSizeBytes} bytes")
            Text(text = "Version: ${report.versionMarker ?: "not detected"}")
            Text(text = "Equipment: ${report.detectedEquipment ?: "not detected"}")
            Text(text = "Estimated pages: ${report.estimatedPageCount}")
            Text(text = "NOTE marker: ${report.hasNoteMarker}")
            Text(text = "MAINLAYER: ${report.hasMainLayer}")
            Text(text = "BGLAYER: ${report.hasBackgroundLayer}")
            Text(text = "LAYERINFO: ${report.hasLayerInfo}")
            Text(text = "LAYERSEQ: ${report.hasLayerSequence}")
            Text(text = "TOTALPATH: ${report.hasTotalPath}")
            Text(text = "PAGESTYLE: ${report.hasPageStyle}")
            Text(text = "TITLE metadata: ${report.hasTitleMetadata}")
            Text(text = "KEYWORD metadata: ${report.hasKeywordMetadata}")
            Text(text = "LINK metadata: ${report.hasLinkMetadata}")
            Text(text = "STAR metadata: ${report.hasStarMetadata}")
            Text(text = "Status: ${report.compatibilityStatus}")

            if (report.warnings.isNotEmpty()) {
                Text(text = "Warnings")
                report.warnings.forEach { warning ->
                    Text(text = "• $warning")
                }
            }
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri
): String? {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }
}

