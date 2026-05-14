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
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationColor
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationOverlayStore
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStrokeStyle
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationWidth
import io.github.piyushdaiya.vellumsync.note.OverlayRenderExporter
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
    val pendingBinaryExport = remember { mutableStateOf<ByteArray?>(null) }

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
                exportError.value = throwable.message ?: "Unable to export JSON."
            }
        }
    }

    val pngExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeBytesToUri(
                    context = context,
                    uri = uri,
                    bytes = pendingBinaryExport.value ?: ByteArray(0)
                )
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export overlay PNG."
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeBytesToUri(
                    context = context,
                    uri = uri,
                    bytes = pendingBinaryExport.value ?: ByteArray(0)
                )
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export overlay PDF."
            }
        }
    }

    val pages = report?.strokeGeometryReport?.pageReports.orEmpty()
    val safeIndex = if (pages.isEmpty()) 0 else currentPageIndex.value.coerceIn(0, pages.lastIndex)
    if (pages.isNotEmpty() && safeIndex != currentPageIndex.value) {
        currentPageIndex.value = safeIndex
    }
    val selectedPage = pages.getOrNull(safeIndex)
    val selectedPageNumber = selectedPage?.pageNumber ?: 1
    val overlayVisible = remember(selection.sha256) { mutableStateOf(true) }
    val overlayEditingEnabled = remember(selection.sha256) { mutableStateOf(false) }
    val overlayStyle = remember(selection.sha256) { mutableStateOf(LocalAnnotationStrokeStyle.DEFAULT) }
    val overlayStrokes = remember(selection.sha256, selectedPageNumber) {
        mutableStateOf(LocalAnnotationOverlayStore.loadPage(context, selection.sha256, selectedPageNumber))
    }
    val saveOverlayStrokes: (List<LocalAnnotationStroke>) -> Unit = { strokes ->
        overlayStrokes.value = strokes
        selectedPage?.let { page ->
            LocalAnnotationOverlayStore.savePage(
                context = context,
                noteSha256 = selection.sha256,
                pageNumber = page.pageNumber,
                transformModeId = selectedTransformMode.value.id,
                strokes = strokes
            )
        }
    }

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
            },
            overlayVisible = overlayVisible.value,
            overlayEditingEnabled = overlayEditingEnabled.value,
            overlayStyle = overlayStyle.value,
            overlayStrokes = overlayStrokes.value,
            onOverlayVisibleToggle = { overlayVisible.value = !overlayVisible.value },
            onOverlayEditToggle = {
                val nextEditing = !overlayEditingEnabled.value
                overlayEditingEnabled.value = nextEditing
                if (nextEditing) overlayVisible.value = true
            },
            onOverlayStyleSelect = { overlayStyle.value = it },
            onUndoOverlay = {
                if (overlayStrokes.value.isNotEmpty()) {
                    saveOverlayStrokes(overlayStrokes.value.dropLast(1))
                }
            },
            onClearOverlay = {
                LocalAnnotationOverlayStore.clearPage(context, selection.sha256, selectedPage.pageNumber)
                saveOverlayStrokes(emptyList())
            },
            onOverlayChanged = saveOverlayStrokes
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
                    pendingExportJson.value = LocalAnnotationOverlayStore.appendOverlayDiagnostics(
                        baseReportJson = report.toJson(),
                        context = context,
                        noteSha256 = selection.sha256,
                        totalPages = report.strokeGeometryReport.totalPages
                    )
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

                OverlayControlsCard(
                    overlayVisible = overlayVisible.value,
                    editingEnabled = overlayEditingEnabled.value,
                    style = overlayStyle.value,
                    strokeCount = overlayStrokes.value.size,
                    pointCount = overlayStrokes.value.sumOf { it.points.size },
                    onToggleVisible = { overlayVisible.value = !overlayVisible.value },
                    onToggleEditing = {
                        val nextEditing = !overlayEditingEnabled.value
                        overlayEditingEnabled.value = nextEditing
                        if (nextEditing) overlayVisible.value = true
                    },
                    onStyleSelect = { overlayStyle.value = it },
                    onReload = {
                        overlayStrokes.value = LocalAnnotationOverlayStore.loadPage(
                            context = context,
                            noteSha256 = selection.sha256,
                            pageNumber = selectedPage.pageNumber
                        )
                    },
                    onExportJson = {
                        pendingExportJson.value = LocalAnnotationOverlayStore.overlayExportJson(
                            context = context,
                            noteSha256 = selection.sha256,
                            totalPages = inspection.strokeGeometryReport.totalPages
                        )
                        exportLauncher.launch("vellumsync-overlay-${selection.sha256.take(12)}.json")
                    },
                    onExportPng = {
                        pendingBinaryExport.value = OverlayRenderExporter.renderOverlayPreviewPng(
                            pageWidth = selectedPage.pageWidth,
                            pageHeight = selectedPage.pageHeight,
                            strokes = overlayStrokes.value
                        )
                        pngExportLauncher.launch("vellumsync-overlay-page-${selectedPage.pageNumber}.png")
                    },
                    onExportPdf = {
                        pendingBinaryExport.value = OverlayRenderExporter.renderOverlayPreviewPdf(
                            pageWidth = selectedPage.pageWidth,
                            pageHeight = selectedPage.pageHeight,
                            strokes = overlayStrokes.value
                        )
                        pdfExportLauncher.launch("vellumsync-overlay-page-${selectedPage.pageNumber}.pdf")
                    },
                    onUndo = {
                        if (overlayStrokes.value.isNotEmpty()) {
                            saveOverlayStrokes(overlayStrokes.value.dropLast(1))
                        }
                    },
                    onClear = {
                        LocalAnnotationOverlayStore.clearPage(context, selection.sha256, selectedPage.pageNumber)
                        saveOverlayStrokes(emptyList())
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
                    onFullScreen = { fullScreenPreview.value = true },
                    overlayVisible = overlayVisible.value,
                    overlayEditingEnabled = overlayEditingEnabled.value,
                    overlayStyle = overlayStyle.value,
                    overlayStrokes = overlayStrokes.value,
                    onOverlayChanged = saveOverlayStrokes
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
    onFullScreen: () -> Unit,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayStyle: LocalAnnotationStrokeStyle,
    overlayStrokes: List<LocalAnnotationStroke>,
    onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit
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
                        transformMode = transformMode,
                        overlayEditingEnabled = overlayEditingEnabled,
                        overlayVisible = overlayVisible,
                        overlayStrokes = overlayStrokes,
                        currentOverlayStyle = overlayStyle,
                        onOverlayChanged = onOverlayChanged
                    )
                },
                update = { view ->
                    view.updatePageReport(
                        pageReport = page,
                        transformMode = transformMode,
                        overlayEditingEnabled = overlayEditingEnabled,
                        overlayVisible = overlayVisible,
                        overlayStrokes = overlayStrokes,
                        currentOverlayStyle = overlayStyle,
                        onOverlayChanged = onOverlayChanged
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
    onNext: () -> Unit,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayStyle: LocalAnnotationStrokeStyle,
    overlayStrokes: List<LocalAnnotationStroke>,
    onOverlayVisibleToggle: () -> Unit,
    onOverlayEditToggle: () -> Unit,
    onOverlayStyleSelect: (LocalAnnotationStrokeStyle) -> Unit,
    onUndoOverlay: () -> Unit,
    onClearOverlay: () -> Unit,
    onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit
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
        OverlayControlsCompactRow(
            overlayVisible = overlayVisible,
            editingEnabled = overlayEditingEnabled,
            style = overlayStyle,
            strokeCount = overlayStrokes.size,
            onToggleVisible = onOverlayVisibleToggle,
            onToggleEditing = onOverlayEditToggle,
            onStyleSelect = onOverlayStyleSelect,
            onUndo = onUndoOverlay,
            onClear = onClearOverlay
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { androidContext ->
                SupernoteVectorPreviewView(
                    context = androidContext,
                    pageReport = page,
                    transformMode = transformMode,
                    overlayEditingEnabled = overlayEditingEnabled,
                    overlayVisible = overlayVisible,
                    overlayStrokes = overlayStrokes,
                    currentOverlayStyle = overlayStyle,
                    onOverlayChanged = onOverlayChanged
                )
            },
            update = { view ->
                view.updatePageReport(
                    pageReport = page,
                    transformMode = transformMode,
                    overlayEditingEnabled = overlayEditingEnabled,
                    overlayVisible = overlayVisible,
                    overlayStrokes = overlayStrokes,
                    currentOverlayStyle = overlayStyle,
                    onOverlayChanged = onOverlayChanged
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
private fun OverlayControlsCard(
    overlayVisible: Boolean,
    editingEnabled: Boolean,
    style: LocalAnnotationStrokeStyle,
    strokeCount: Int,
    pointCount: Int,
    onToggleVisible: () -> Unit,
    onToggleEditing: () -> Unit,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit,
    onReload: () -> Unit,
    onExportJson: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Local annotation overlay")
            Text(
                text = when {
                    editingEnabled -> "Editing is ON. Use the Boox pen/stylus to write. Finger touches are ignored for drawing."
                    overlayVisible -> "Overlay is visible but editing is OFF. Use Show/Hide and Edit independently."
                    else -> "Overlay is hidden and editing is OFF. Original Supernote .note remains read-only."
                }
            )
            Text(text = "This page overlay: $strokeCount stroke(s), $pointCount point(s)")
            Text(text = "Stroke style: ${style.color.label} / ${style.width.label}")
            OverlayControlsCompactRow(
                overlayVisible = overlayVisible,
                editingEnabled = editingEnabled,
                style = style,
                strokeCount = strokeCount,
                onToggleVisible = onToggleVisible,
                onToggleEditing = onToggleEditing,
                onStyleSelect = onStyleSelect,
                onUndo = onUndo,
                onClear = onClear
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = onReload
                ) {
                    Text(text = "Reload overlay")
                }
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = onExportJson
                ) {
                    Text(text = "Export JSON")
                }
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = onExportPng
                ) {
                    Text(text = "Export PNG")
                }
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = onExportPdf
                ) {
                    Text(text = "Export PDF")
                }
            }
        }
    }
}

@Composable
private fun OverlayControlsCompactRow(
    overlayVisible: Boolean,
    editingEnabled: Boolean,
    style: LocalAnnotationStrokeStyle,
    strokeCount: Int,
    onToggleVisible: () -> Unit,
    onToggleEditing: () -> Unit,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onToggleVisible
            ) {
                Text(text = if (overlayVisible) "Hide overlay" else "Show overlay")
            }
            Button(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onToggleEditing
            ) {
                Text(text = if (editingEnabled) "Edit on" else "Edit off")
            }
            Button(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onUndo,
                enabled = strokeCount > 0
            ) {
                Text(text = "Undo")
            }
            Button(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onClear,
                enabled = strokeCount > 0
            ) {
                Text(text = "Clear page")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalAnnotationColor.values().forEach { color ->
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = { onStyleSelect(style.copy(color = color)) },
                    enabled = style.color != color
                ) {
                    Text(text = color.label)
                }
            }
            LocalAnnotationWidth.values().forEach { width ->
                Button(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = { onStyleSelect(style.copy(width = width)) },
                    enabled = style.width != width
                ) {
                    Text(text = width.label)
                }
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
                Text(text = "Overlay sidecars: exported in diagnostics JSON when present")

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
