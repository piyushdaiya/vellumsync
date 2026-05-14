package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

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
    val showDetails = remember { mutableStateOf(false) }
    val fullScreenPreview = remember { mutableStateOf(false) }
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

    val pages = report?.strokeGeometryReport?.pageReports.orEmpty()
    val safeIndex = if (pages.isEmpty()) 0 else currentPageIndex.value.coerceIn(0, pages.lastIndex)
    if (pages.isNotEmpty() && safeIndex != currentPageIndex.value) {
        currentPageIndex.value = safeIndex
    }
    val selectedPage = pages.getOrNull(safeIndex)

    if (fullScreenPreview.value && report != null && selectedPage != null) {
        FullScreenPreview(
            fileName = selection.fileName,
            page = selectedPage,
            pageIndex = safeIndex,
            totalPages = pages.size,
            transformMode = selectedTransformMode.value,
            onTransformSelect = { mode ->
                selectedTransformMode.value = mode
                ViewerTransformPersistence.save(context, selection.sha256, mode)
            },
            onExit = { fullScreenPreview.value = false },
            onPrevious = {
                currentPageIndex.value = (currentPageIndex.value - 1).coerceAtLeast(0)
            },
            onNext = {
                currentPageIndex.value = (currentPageIndex.value + 1).coerceAtMost(pages.lastIndex)
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactViewerHeader(
            fileName = selection.fileName,
            report = report,
            onBack = onBack,
            onExportDiagnostics = {
                if (report != null) {
                    pendingExportJson.value = report.toJson()
                    exportLauncher.launch("vellumsync-note-diagnostics-${report.sha256.take(12)}.json")
                }
            }
        )

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
            if (pages.isEmpty() || selectedPage == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "No decoded preview pages are available for this note."
                    )
                }
            } else {
                TransformSelectorCompact(
                    selected = selectedTransformMode.value,
                    onSelect = { mode ->
                        selectedTransformMode.value = mode
                        ViewerTransformPersistence.save(context, selection.sha256, mode)
                    }
                )

                PagePreviewCard(
                    page = selectedPage,
                    pageIndex = safeIndex,
                    totalPages = pages.size,
                    transformMode = selectedTransformMode.value,
                    onPrevious = {
                        currentPageIndex.value = (currentPageIndex.value - 1).coerceAtLeast(0)
                    },
                    onNext = {
                        currentPageIndex.value = (currentPageIndex.value + 1).coerceAtMost(pages.lastIndex)
                    },
                    onFullScreen = { fullScreenPreview.value = true }
                )

                ViewerDetailsCard(
                    report = inspection,
                    pageIndex = safeIndex,
                    expanded = showDetails.value,
                    onToggle = { showDetails.value = !showDetails.value }
                )
            }
        }
    }
}

@Composable
private fun CompactViewerHeader(
    fileName: String,
    report: SupernoteInspectionReport?,
    onBack: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "VellumSync Viewer")
            Text(text = fileName)
            if (report != null) {
                Text(
                    text = "Read-only • ${report.versionMarker ?: "unknown version"} • " +
                        "${report.strokeGeometryReport.totalRenderedRecords}/${report.strokeGeometryReport.totalDecodedRecords} rendered"
                )
            } else {
                Text(text = "Read-only viewer. The original Supernote .note file is not modified.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBack) {
                    Text(text = "Back")
                }
                Button(onClick = onExportDiagnostics, enabled = report != null) {
                    Text(text = "Export diagnostics")
                }
            }
        }
    }
}

@Composable
private fun PagePreviewCard(
    page: SupernoteStrokeGeometryPageReport,
    pageIndex: Int,
    totalPages: Int,
    transformMode: SupernotePreviewTransformMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFullScreen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrevious, enabled = pageIndex > 0) {
                    Text(text = "Previous")
                }
                Button(onClick = onNext, enabled = pageIndex < totalPages - 1) {
                    Text(text = "Next")
                }
                Button(onClick = onFullScreen) {
                    Text(text = "Full screen")
                }
            }
            Text(
                text = "Page ${page.pageNumber} of $totalPages • " +
                    "${page.renderedRecords}/${page.decodedRecords} rendered • ${transformMode.label}"
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(980.dp),
                factory = { androidContext ->
                    SupernoteVectorPreviewView(
                        context = androidContext,
                        pageReport = page,
                        transformMode = transformMode
                    )
                },
                update = { view ->
                    view.updatePageReport(
                        pageReport = page,
                        transformMode = transformMode
                    )
                }
            )
            Text(text = "Pan: drag inside preview. Zoom: pinch with two fingers.")
        }
    }
}

@Composable
private fun FullScreenPreview(
    fileName: String,
    page: SupernoteStrokeGeometryPageReport,
    pageIndex: Int,
    totalPages: Int,
    transformMode: SupernotePreviewTransformMode,
    onTransformSelect: (SupernotePreviewTransformMode) -> Unit,
    onExit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "$fileName • Page ${page.pageNumber} of $totalPages • ${transformMode.label}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExit) {
                Text(text = "Exit")
            }
            Button(onClick = onPrevious, enabled = pageIndex > 0) {
                Text(text = "Previous")
            }
            Button(onClick = onNext, enabled = pageIndex < totalPages - 1) {
                Text(text = "Next")
            }
        }
        Text(text = "Transform calibration: ${transformMode.label}")
        TransformModeButtonRow(
            selected = transformMode,
            onSelect = onTransformSelect
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { androidContext ->
                SupernoteVectorPreviewView(
                    context = androidContext,
                    pageReport = page,
                    transformMode = transformMode
                )
            },
            update = { view ->
                view.updatePageReport(
                    pageReport = page,
                    transformMode = transformMode
                )
            }
        )
    }
}

@Composable
private fun TransformSelectorCompact(
    selected: SupernotePreviewTransformMode,
    onSelect: (SupernotePreviewTransformMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Transform calibration: ${selected.label}")
            Text(text = "Use A5X presets to align this preview against the Supernote PDF/photo reference.")
            TransformModeButtonRow(
                selected = selected,
                onSelect = onSelect
            )
        }
    }
}


@Composable
private fun TransformModeButtonRow(
    selected: SupernotePreviewTransformMode,
    onSelect: (SupernotePreviewTransformMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SupernotePreviewTransformMode.viewerOrder.forEach { mode ->
            Button(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = { onSelect(mode) },
                enabled = mode != selected
            ) {
                Text(text = mode.label)
            }
        }
    }
}

@Composable
private fun ViewerDetailsCard(
    report: SupernoteInspectionReport,
    pageIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val page = report.strokeGeometryReport.pageReports[pageIndex]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onToggle) {
                    Text(text = if (expanded) "Hide details" else "Show details")
                }
            }
            if (!expanded) {
                Text(text = "Diagnostics are hidden in normal viewer mode.")
            } else {
                Text(text = "Note details")
                Text(text = "Version: ${report.versionMarker ?: "not detected"}")
                Text(text = "Equipment: ${report.detectedEquipment ?: "not detected"}")
                Text(text = "Pages: ${report.strokeGeometryReport.totalPages}")
                Text(text = "SHA-256: ${report.sha256.take(16)}…")
                Text(text = "Decoded records: ${report.strokeGeometryReport.totalDecodedRecords}")
                Text(text = "Rendered records: ${report.strokeGeometryReport.totalRenderedRecords}")
                Text(text = "Skipped records: ${report.strokeGeometryReport.totalSkippedRecords}")

                Text(text = "Page render details")
                Text(text = "Page: ${page.pageNumber}")
                Text(text = "Decoded: ${page.decodedRecords}")
                Text(text = "Rendered: ${page.renderedRecords}")
                Text(text = "Skipped: ${page.skippedRecords}")
                Text(text = "Unknown subtype: ${page.unknownSubtypeRecords}")
                Text(text = "Possible eraser/metadata: ${page.possibleEraserOrMetadataRecords}")
                Text(text = "Alignment note: transform candidates are visual-only; choose the closest A5X preset and compare to the Supernote PDF export.")
                if (page.warnings.isNotEmpty()) {
                    Text(text = "Page warnings")
                    page.warnings.forEach { warning -> Text(text = "• $warning") }
                }
            }
        }
    }
}
