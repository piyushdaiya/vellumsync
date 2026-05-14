package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.viewinterop.AndroidView
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.SupernoteInspectionReport
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector

@Composable
fun NoteViewerScreen(
    selection: ViewerNoteSelection,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reportResult = remember(selection.notePath) {
        runCatching {
            val bytes = ImportedNoteCache.readCachedNote(selection.notePath)
            SupernoteNoteInspector.inspect(
                fileName = selection.fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes,
                cachedCopyPath = selection.notePath
            )
        }
    }
    val report = reportResult.getOrNull()
    val error = reportResult.exceptionOrNull()?.message
    val currentPageIndex = remember(selection.sha256) { mutableStateOf(0) }
    val selectedTransformMode = remember(selection.sha256) {
        mutableStateOf(ViewerTransformPersistence.load(context, selection.sha256))
    }
    val exportError = remember { mutableStateOf<String?>(null) }
    val pendingExportJson = remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeTextToUri(
                    context = context,
                    uri = uri,
                    text = pendingExportJson.value.orEmpty()
                )
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export diagnostics JSON."
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "VellumSync Viewer Mode")
        Text(text = selection.fileName)
        Text(text = "Read-only viewer. The original Supernote .note file is not modified.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) {
                Text(text = "Back")
            }
            if (report != null) {
                Button(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    onClick = {
                        pendingExportJson.value = report.toJson()
                        exportLauncher.launch("vellumsync-note-diagnostics-${report.sha256.take(12)}.json")
                    }
                ) {
                    Text(text = "Export diagnostics")
                }
            }
        }

        error?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Unable to open cached note: $it"
                )
            }
        }

        exportError.value?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Export error: $it"
                )
            }
        }

        report?.let { inspection ->
            ViewerSummaryCard(report = inspection)

            val pages = inspection.strokeGeometryReport.pageReports
            if (pages.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "No decoded preview pages are available for this note."
                    )
                }
            } else {
                val safeIndex = currentPageIndex.value.coerceIn(0, pages.lastIndex)
                if (safeIndex != currentPageIndex.value) currentPageIndex.value = safeIndex
                val page = pages[safeIndex]

                PageNavigationCard(
                    currentPage = safeIndex + 1,
                    totalPages = pages.size,
                    onPrevious = {
                        currentPageIndex.value = (currentPageIndex.value - 1).coerceAtLeast(0)
                    },
                    onNext = {
                        currentPageIndex.value = (currentPageIndex.value + 1).coerceAtMost(pages.lastIndex)
                    }
                )

                TransformSelectorCard(
                    selected = selectedTransformMode.value,
                    onSelect = { mode ->
                        selectedTransformMode.value = mode
                        ViewerTransformPersistence.save(context, selection.sha256, mode)
                    }
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "Page ${page.pageNumber} preview")
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(760.dp),
                            factory = { androidContext ->
                                SupernoteVectorPreviewView(
                                    context = androidContext,
                                    pageReport = page,
                                    transformMode = selectedTransformMode.value
                                )
                            },
                            update = { view ->
                                view.updatePageReport(
                                    pageReport = page,
                                    transformMode = selectedTransformMode.value
                                )
                            }
                        )
                        Text(text = "Pan: drag inside preview. Zoom: pinch with two fingers. Use transform modes to align against Supernote PDF exports.")
                    }
                }

                RenderSummaryCard(report = inspection, pageIndex = safeIndex)
            }
        }
    }
}

@Composable
private fun ViewerSummaryCard(report: SupernoteInspectionReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Note summary")
            Text(text = "Version: ${report.versionMarker ?: "not detected"}")
            Text(text = "Equipment: ${report.detectedEquipment ?: "not detected"}")
            Text(text = "Pages: ${report.strokeGeometryReport.totalPages}")
            Text(text = "Decoded records: ${report.strokeGeometryReport.totalDecodedRecords}")
            Text(text = "Rendered records: ${report.strokeGeometryReport.totalRenderedRecords}")
            Text(text = "Skipped records: ${report.strokeGeometryReport.totalSkippedRecords}")
            Text(text = "Cached copy: ${report.cachedCopyPath ?: "not cached"}")
        }
    }
}

@Composable
private fun PageNavigationCard(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Page $currentPage of $totalPages")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPrevious, enabled = currentPage > 1) {
                    Text(text = "Previous")
                }
                Button(onClick = onNext, enabled = currentPage < totalPages) {
                    Text(text = "Next")
                }
            }
        }
    }
}

@Composable
private fun TransformSelectorCard(
    selected: SupernotePreviewTransformMode,
    onSelect: (SupernotePreviewTransformMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Transform mode: ${selected.label}")
            Text(text = "Persisted per note for viewer alignment.")
            SupernotePreviewTransformMode.values().forEach { mode ->
                Button(
                    onClick = { onSelect(mode) },
                    enabled = mode != selected
                ) {
                    Text(text = mode.label)
                }
            }
        }
    }
}

@Composable
private fun RenderSummaryCard(
    report: SupernoteInspectionReport,
    pageIndex: Int
) {
    val page = report.strokeGeometryReport.pageReports[pageIndex]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Render summary")
            Text(text = "Page: ${page.pageNumber}")
            Text(text = "Decoded: ${page.decodedRecords}")
            Text(text = "Rendered: ${page.renderedRecords}")
            Text(text = "Skipped: ${page.skippedRecords}")
            Text(text = "Unknown subtype: ${page.unknownSubtypeRecords}")
            Text(text = "Possible eraser/metadata: ${page.possibleEraserOrMetadataRecords}")
            Text(text = "Note rendered records: ${report.strokeGeometryReport.totalRenderedRecords}/${report.strokeGeometryReport.totalDecodedRecords}")
            if (page.warnings.isNotEmpty()) {
                Text(text = "Page warnings")
                page.warnings.forEach { warning -> Text(text = "• $warning") }
            }
        }
    }
}
