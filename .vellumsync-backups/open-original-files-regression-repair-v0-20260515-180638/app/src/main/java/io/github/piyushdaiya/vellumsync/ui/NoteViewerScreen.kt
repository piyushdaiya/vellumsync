package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Space
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.piyushdaiya.vellumsync.R
import io.github.piyushdaiya.vellumsync.device.DeviceCapabilityDetector
import io.github.piyushdaiya.vellumsync.device.StylusProbePersistence
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationColor
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationOverlayStore
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStrokeStyle
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationToolKind
import io.github.piyushdaiya.vellumsync.note.SupernoteFeatureCompatibilityAnalyzer
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationWidth
import io.github.piyushdaiya.vellumsync.note.OverlayRenderExporter
import io.github.piyushdaiya.vellumsync.note.SupernoteInspectionReport
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector
import io.github.piyushdaiya.vellumsync.note.SupernotePdfReferenceLayer
import io.github.piyushdaiya.vellumsync.note.SupernoteRattaRleVisualLayerRenderer
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport
import io.github.piyushdaiya.vellumsync.note.SupernoteVisualLayerFirstDiagnosticsBuilder
import io.github.piyushdaiya.vellumsync.note.SupernoteWriteBackResearchHarness
import io.github.piyushdaiya.vellumsync.note.VellumCanonicalNoteModelBuilder
import io.github.piyushdaiya.vellumsync.note.VellumSyncPackageExporter

private enum class NoteTool(
    val railLabel: String,
    val panelTitle: String,
    val iconGlyph: String
) {
    VIEW("View", "View", "▣"),
    PEN("Pen", "Pen", "✎"),
    ERASER("Erase", "Erase", "⌫"),
    STYLE("Style", "Style", "●"),
    LAYERS("Layer", "Layers", "▤"),
    PAGE("Page", "Page", "▥"),
    ZOOM("Zoom", "Zoom", "+"),
    EXPORT("Export", "Export", "⇧"),
    SYNC("Sync", "Sync", "⟳"),
    SETTINGS("Settings", "Settings", "⚙"),
    MORE("More", "More", "⋯")
}

@Composable
fun NoteViewerScreen(
    selection: ViewerNoteSelection,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val noteBytesResult = remember(selection.notePath) {
        runCatching { ImportedNoteCache.readCachedNote(selection.notePath) }
    }
    val noteBytes = noteBytesResult.getOrNull()
    val reportResult = remember(selection.notePath, noteBytesResult) {
        runCatching {
            val bytes = noteBytesResult.getOrThrow()
            SupernoteNoteInspector.inspect(
                fileName = selection.fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes,
                cachedCopyPath = selection.notePath
            )
        }
    }
    val report = reportResult.getOrNull()
    val error = reportResult.exceptionOrNull()?.message ?: noteBytesResult.exceptionOrNull()?.message

    val initialPageNumber = remember(selection.sha256) { LocalNoteLibrary.loadLastOpenedPage(context, selection.sha256) }
    val currentPageIndex = remember(selection.sha256) { mutableStateOf((initialPageNumber - 1).coerceAtLeast(0)) }
    val selectedTransformMode = remember(selection.sha256) {
        mutableStateOf(ViewerTransformPersistence.load(context, selection.sha256))
    }
    val renderMode = remember(selection.sha256) { mutableStateOf(SupernoteRenderMode.VISUAL_LAYER) }
    val railPosition = remember { mutableStateOf(RailPositionPersistence.load(context)) }
    val activeTool = remember(selection.sha256) { mutableStateOf(NoteTool.VIEW) }
    val activePanel = remember(selection.sha256) { mutableStateOf<NoteTool?>(null) }
    val fullScreenPreview = remember { mutableStateOf(false) }
    val exportError = remember { mutableStateOf<String?>(null) }
    val pendingExportJson = remember { mutableStateOf<String?>(null) }
    val pendingBinaryExport = remember { mutableStateOf<ByteArray?>(null) }
    val showNoteDetailsPanel = remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeTextToUri(context, uri, pendingExportJson.value.orEmpty())
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
                writeBytesToUri(context, uri, pendingBinaryExport.value ?: ByteArray(0))
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
                writeBytesToUri(context, uri, pendingBinaryExport.value ?: ByteArray(0))
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export overlay PDF."
            }
        }
    }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeBytesToUri(context, uri, pendingBinaryExport.value ?: ByteArray(0))
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export VellumSync package."
            }
        }
    }

    val pdfReferenceRevision = remember(selection.sha256) { mutableStateOf(0) }
    val pdfReferenceImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: error("Unable to read selected PDF reference.")
                ImportedNoteCache.cachePdfReferenceForNote(
                    notePath = selection.notePath,
                    noteFileName = selection.fileName,
                    pdfBytes = bytes
                )
                pdfReferenceRevision.value += 1
                exportError.value = null
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to attach PDF reference."
            }
        }
    }

    val noteCopyExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeBytesToUri(context, uri, pendingBinaryExport.value ?: ByteArray(0))
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export Supernote research copy."
            }
        }
    }

    val pages = report?.strokeGeometryReport?.pageReports.orEmpty()
    val totalPages = report?.strokeGeometryReport?.totalPages?.takeIf { it > 0 } ?: pages.size
    val safeIndex = if (pages.isEmpty()) 0 else currentPageIndex.value.coerceIn(0, pages.lastIndex)
    if (pages.isNotEmpty() && safeIndex != currentPageIndex.value) {
        currentPageIndex.value = safeIndex
    }
    val selectedPage = pages.getOrNull(safeIndex)
    val selectedPageNumber = selectedPage?.pageNumber ?: 1
    val renderedVisualPage = remember(selection.sha256, selectedPageNumber, report, noteBytes) {
        val bytes = noteBytes
        val inspection = report
        val page = selectedPage
        if (bytes != null && inspection != null && page != null) {
            SupernoteRattaRleVisualLayerRenderer.renderPage(
                bytes = bytes,
                report = inspection,
                pageNumber = selectedPageNumber,
                width = page.pageWidth.toInt().coerceAtLeast(1),
                height = page.pageHeight.toInt().coerceAtLeast(1)
            )
        } else {
            null
        }
    }
    val renderedPdfReferencePage = remember(selection.sha256, selection.notePath, selectedPageNumber, selectedPage?.pageWidth, selectedPage?.pageHeight, pdfReferenceRevision.value) {
        val page = selectedPage
        if (page != null) {
            SupernotePdfReferenceLayer.renderPageForNote(
                notePath = selection.notePath,
                noteFileName = selection.fileName,
                pageNumber = selectedPageNumber,
                targetWidth = page.pageWidth.toInt().coerceAtLeast(1),
                targetHeight = page.pageHeight.toInt().coerceAtLeast(1)
            )
        } else {
            null
        }
    }
    val currentRenderStatusText = renderStatusText(renderMode.value, renderedVisualPage, renderedPdfReferencePage)

    LaunchedEffect(selection.sha256, selectedPageNumber, selectedTransformMode.value.id, totalPages) {
        LocalNoteLibrary.markOpened(
            context = context,
            selection = selection,
            pageNumber = selectedPageNumber,
            totalPages = totalPages,
            transformModeId = selectedTransformMode.value.id
        )
    }

    val overlayVisible = remember(selection.sha256) { mutableStateOf(true) }
    val overlayEditingEnabled = remember(selection.sha256) { mutableStateOf(false) }
    val overlayStyle = remember(selection.sha256) { mutableStateOf(LocalAnnotationStrokeStyle.DEFAULT) }
    val overlayStrokes = remember(selection.sha256, selectedPageNumber) {
        mutableStateOf(LocalAnnotationOverlayStore.loadPage(context, selection.sha256, selectedPageNumber))
    }
    val overlayUndoStack = remember(selection.sha256, selectedPageNumber) {
        mutableStateOf<List<List<LocalAnnotationStroke>>>(emptyList())
    }
    val overlayRedoStack = remember(selection.sha256, selectedPageNumber) {
        mutableStateOf<List<List<LocalAnnotationStroke>>>(emptyList())
    }

    fun saveOverlayStrokesDirect(strokes: List<LocalAnnotationStroke>) {
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

    fun commitOverlayStrokes(strokes: List<LocalAnnotationStroke>) {
        if (strokes == overlayStrokes.value) return
        overlayUndoStack.value = overlayUndoStack.value + listOf(overlayStrokes.value)
        overlayRedoStack.value = emptyList()
        saveOverlayStrokesDirect(strokes)
        LocalNoteLibrary.markOpened(
            context = context,
            selection = selection,
            pageNumber = selectedPageNumber,
            totalPages = totalPages,
            transformModeId = selectedTransformMode.value.id
        )
    }

    val saveOverlayStrokes: (List<LocalAnnotationStroke>) -> Unit = { strokes ->
        commitOverlayStrokes(strokes)
    }

    fun setRailPosition(position: RailPosition) {
        railPosition.value = position
        RailPositionPersistence.save(context, position)
    }

    fun undoOverlay() {
        val previous = overlayUndoStack.value.lastOrNull() ?: return
        overlayUndoStack.value = overlayUndoStack.value.dropLast(1)
        overlayRedoStack.value = overlayRedoStack.value + listOf(overlayStrokes.value)
        saveOverlayStrokesDirect(previous)
    }

    fun redoOverlay() {
        val next = overlayRedoStack.value.lastOrNull() ?: return
        overlayRedoStack.value = overlayRedoStack.value.dropLast(1)
        overlayUndoStack.value = overlayUndoStack.value + listOf(overlayStrokes.value)
        saveOverlayStrokesDirect(next)
    }

    fun clearOverlayPage() {
        selectedPage ?: return
        if (overlayStrokes.value.isEmpty()) return
        commitOverlayStrokes(emptyList())
    }

    fun clearAllOverlaysForNote() {
        if (LocalAnnotationOverlayStore.noteSummary(context, selection.sha256).totalOverlayStrokes == 0) return
        overlayUndoStack.value = overlayUndoStack.value + listOf(overlayStrokes.value)
        overlayRedoStack.value = emptyList()
        LocalAnnotationOverlayStore.clearNote(context, selection.sha256)
        overlayStrokes.value = emptyList()
    }

    fun attachPdfReference() {
        pdfReferenceImportLauncher.launch(
            arrayOf(
                "application/pdf",
                "*/*"
            )
        )
    }

    fun exportDiagnostics() {
        val inspection = report ?: return
        val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
        val featureJson = SupernoteFeatureCompatibilityAnalyzer.analyze(
            bytes = originalBytes,
            report = inspection
        ).toJson()
        val withOverlay = LocalAnnotationOverlayStore.appendOverlayDiagnostics(
            baseReportJson = inspection.toJson(),
            context = context,
            noteSha256 = selection.sha256,
            totalPages = inspection.strokeGeometryReport.totalPages
        )
        val visualLayerFirstDiagnosticsJson = SupernoteVisualLayerFirstDiagnosticsBuilder.build(
            report = inspection,
            pageNumber = selectedPageNumber,
            renderedVisualPage = renderedVisualPage
        ).toJson()
        val pdfReferenceLayerDiagnosticsJson = SupernotePdfReferenceLayer.diagnosticsJson(renderedPdfReferencePage)
        pendingExportJson.value = appendJsonObjectField(
            appendJsonObjectField(
                appendJsonObjectField(withOverlay, "featureCompatibilityReport", featureJson),
                "visualLayerFirstDiagnostics",
                visualLayerFirstDiagnosticsJson
            ),
            "pdfReferenceLayerDiagnostics",
            pdfReferenceLayerDiagnosticsJson
        )
        exportLauncher.launch("vellumsync-note-diagnostics-${inspection.sha256.take(12)}.json")
    }

    fun exportCanonicalModelJson() {
        val inspection = report ?: return
        pendingExportJson.value = VellumCanonicalNoteModelBuilder.fromSupernoteAndOverlay(
            context = context,
            report = inspection,
            noteSha256 = selection.sha256,
            noteFileName = selection.fileName,
            transformModeId = selectedTransformMode.value.id
        ).toJson()
        exportLauncher.launch("vellumsync-canonical-${selection.sha256.take(12)}.json")
    }

    fun exportFeatureCompatibilityJson() {
        val inspection = report ?: return
        val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
        pendingExportJson.value = SupernoteFeatureCompatibilityAnalyzer.analyze(
            bytes = originalBytes,
            report = inspection
        ).toJson()
        exportLauncher.launch("vellumsync-feature-compatibility-${selection.sha256.take(12)}.json")
    }

    fun exportOverlayJson() {
        val inspection = report ?: return
        pendingExportJson.value = LocalAnnotationOverlayStore.overlayExportJson(
            context = context,
            noteSha256 = selection.sha256,
            totalPages = inspection.strokeGeometryReport.totalPages
        )
        exportLauncher.launch("vellumsync-overlay-${selection.sha256.take(12)}.json")
    }

    fun exportOverlayPng() {
        val page = selectedPage ?: return
        pendingBinaryExport.value = OverlayRenderExporter.renderOverlayPreviewPng(
            pageWidth = page.pageWidth,
            pageHeight = page.pageHeight,
            strokes = overlayStrokes.value
        )
        pngExportLauncher.launch("vellumsync-overlay-page-${page.pageNumber}.png")
    }

    fun exportOverlayPdf() {
        val page = selectedPage ?: return
        pendingBinaryExport.value = OverlayRenderExporter.renderOverlayPreviewPdf(
            pageWidth = page.pageWidth,
            pageHeight = page.pageHeight,
            strokes = overlayStrokes.value
        )
        pdfExportLauncher.launch("vellumsync-overlay-page-${page.pageNumber}.pdf")
    }


    fun exportCombinedPreviewPng() {
        val page = selectedPage ?: return
        pendingBinaryExport.value = VellumSyncPackageExporter.renderCombinedPagePreviewPng(
            page = page,
            overlayStrokes = overlayStrokes.value
        )
        pngExportLauncher.launch("vellumsync-combined-page-${page.pageNumber}.png")
    }

    fun exportCombinedPreviewPdf() {
        val inspection = report ?: return
        pendingBinaryExport.value = VellumSyncPackageExporter.renderCombinedNotePreviewPdf(
            context = context,
            noteSha256 = selection.sha256,
            pages = inspection.strokeGeometryReport.pageReports
        )
        pdfExportLauncher.launch("vellumsync-combined-preview-${selection.sha256.take(12)}.pdf")
    }

    fun exportVellumSyncPackage() {
        val inspection = report ?: return
        pendingBinaryExport.value = VellumSyncPackageExporter.buildExportPackageZip(
            context = context,
            noteSha256 = selection.sha256,
            noteFileName = selection.fileName,
            report = inspection,
            currentPage = selectedPage
        )
        zipExportLauncher.launch("vellumsync-package-${selection.sha256.take(12)}.zip")
    }

    fun shareVellumSyncPackage() {
        val inspection = report ?: return
        val bytes = VellumSyncPackageExporter.buildExportPackageZip(
            context = context,
            noteSha256 = selection.sha256,
            noteFileName = selection.fileName,
            report = inspection,
            currentPage = selectedPage
        )
        exportError.value = VellumSyncPackageExporter.sharePackage(
            context = context,
            fileName = "vellumsync-package-${selection.sha256.take(12)}.zip",
            packageBytes = bytes
        )
    }

    fun exportWriteBackSafeCopy() {
        runCatching {
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            pendingBinaryExport.value = SupernoteWriteBackResearchHarness.buildSafeCopy(originalBytes)
            val safeName = selection.fileName.removeSuffix(".note").removeSuffix(".NOTE")
            noteCopyExportLauncher.launch("${safeName}-vellumsync-safe-copy.note")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export safe research copy."
        }
    }

    fun exportWriteBackAppendProbeCopy() {
        runCatching {
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            val candidate = SupernoteWriteBackResearchHarness.buildAppendProbeCopy(
                originalNoteBytes = originalBytes,
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                transformModeId = selectedTransformMode.value.id,
                overlayStrokes = overlayStrokes.value
            )
            pendingBinaryExport.value = candidate.bytes
            val safeName = selection.fileName.removeSuffix(".note").removeSuffix(".NOTE")
            noteCopyExportLauncher.launch("${safeName}-vellumsync-append-probe-copy.note")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export append-probe research copy."
        }
    }

    fun exportWriteBackWriterCandidateCopy() {
        runCatching {
            val inspection = report ?: error("No parsed Supernote report is available for writer candidate export.")
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            val candidate = SupernoteWriteBackResearchHarness.buildTotalPathWriterCandidateCopy(
                report = inspection,
                originalNoteBytes = originalBytes,
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                transformModeId = selectedTransformMode.value.id,
                overlayStrokes = overlayStrokes.value
            )
            pendingBinaryExport.value = candidate.bytes
            val safeName = selection.fileName.removeSuffix(".note").removeSuffix(".NOTE")
            noteCopyExportLauncher.launch("${safeName}-vellumsync-writer-candidate.note")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export writer candidate copy."
        }
    }

    fun exportWriteBackResearchReport() {
        runCatching {
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            val candidate = SupernoteWriteBackResearchHarness.buildAppendProbeCopy(
                originalNoteBytes = originalBytes,
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                transformModeId = selectedTransformMode.value.id,
                overlayStrokes = overlayStrokes.value
            )
            pendingExportJson.value = SupernoteWriteBackResearchHarness.buildResearchReportJson(
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                totalPages = totalPages,
                transformModeId = selectedTransformMode.value.id,
                originalNoteBytes = originalBytes,
                overlayStrokes = overlayStrokes.value,
                appendProbeCandidate = candidate
            )
            exportLauncher.launch("vellumsync-writeback-research-${selection.sha256.take(12)}.json")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export write-back research report."
        }
    }


    fun exportWriteBackValidationReport() {
        runCatching {
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            pendingExportJson.value = SupernoteWriteBackResearchHarness.buildCompatibilityValidationReportJson(
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                totalPages = totalPages,
                transformModeId = selectedTransformMode.value.id,
                originalNoteBytes = originalBytes,
                overlayStrokes = overlayStrokes.value
            )
            exportLauncher.launch("vellumsync-writeback-validation-${selection.sha256.take(12)}.json")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export write-back validation report."
        }
    }

    fun exportWriteBackWriterValidationReport() {
        runCatching {
            val inspection = report ?: error("No parsed Supernote report is available for writer validation export.")
            val originalBytes = ImportedNoteCache.readCachedNote(selection.notePath)
            val candidate = SupernoteWriteBackResearchHarness.buildTotalPathWriterCandidateCopy(
                report = inspection,
                originalNoteBytes = originalBytes,
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                transformModeId = selectedTransformMode.value.id,
                overlayStrokes = overlayStrokes.value
            )
            pendingExportJson.value = SupernoteWriteBackResearchHarness.buildWriterValidationReportJson(
                noteFileName = selection.fileName,
                noteSha256 = selection.sha256,
                pageNumber = selectedPageNumber,
                totalPages = totalPages,
                transformModeId = selectedTransformMode.value.id,
                originalNoteBytes = originalBytes,
                overlayStrokes = overlayStrokes.value,
                writerCandidate = candidate
            )
            exportLauncher.launch("vellumsync-writeback-writer-validation-${selection.sha256.take(12)}.json")
        }.onFailure { throwable ->
            exportError.value = throwable.message ?: "Unable to export writer validation report."
        }
    }

    fun exportDeviceDiagnostics() {
        val profile = DeviceCapabilityDetector.detect()
        pendingExportJson.value = profile.toDiagnosticsJson(
            stylusProbeConfirmed = StylusProbePersistence.isStylusConfirmed(context)
        )
        exportLauncher.launch("vellumsync-device-diagnostics.json")
    }

    fun selectTool(tool: NoteTool) {
        when (tool) {
            NoteTool.VIEW -> {
                activeTool.value = tool
                overlayEditingEnabled.value = false
                activePanel.value = if (activePanel.value == tool) null else tool
            }
            NoteTool.PEN -> {
                activeTool.value = tool
                activePanel.value = NoteTool.PEN
                overlayVisible.value = true
                overlayEditingEnabled.value = true
            }
            NoteTool.ERASER -> {
                activeTool.value = tool
                activePanel.value = NoteTool.ERASER
                overlayVisible.value = true
                overlayEditingEnabled.value = false
            }
            NoteTool.STYLE,
            NoteTool.LAYERS,
            NoteTool.PAGE,
            NoteTool.ZOOM,
            NoteTool.EXPORT,
            NoteTool.SYNC,
            NoteTool.SETTINGS,
            NoteTool.MORE -> {
                activeTool.value = tool
                activePanel.value = if (activePanel.value == tool) null else tool
            }
        }
    }

    AndroidView(
        factory = { androidContext ->
            LayoutInflater.from(androidContext).inflate(
                R.layout.activity_main,
                null,
                false
            )
        },
        update = { root ->
            bindXmlNoteSurface(
                root = root,
                selection = selection,
                report = report,
                error = error,
                page = selectedPage,
                pageIndex = safeIndex,
                totalPages = totalPages,
                transformMode = selectedTransformMode.value,
                renderMode = renderMode.value,
                renderedVisualPage = renderedVisualPage,
                renderedPdfReferencePage = renderedPdfReferencePage,
                renderStatusText = currentRenderStatusText,
                railPosition = railPosition.value,
                activeTool = activeTool.value,
                activePanel = activePanel.value,
                fullScreen = fullScreenPreview.value,
                overlayVisible = overlayVisible.value,
                overlayEditingEnabled = overlayEditingEnabled.value,
                overlayEraserEnabled = activeTool.value == NoteTool.ERASER,
                canUndoOverlay = overlayUndoStack.value.isNotEmpty(),
                canRedoOverlay = overlayRedoStack.value.isNotEmpty(),
                overlayStyle = overlayStyle.value,
                overlayStrokes = overlayStrokes.value,
                showNoteDetails = showNoteDetailsPanel.value,
                exportError = exportError.value,
                onBack = {
                    if (fullScreenPreview.value) {
                        fullScreenPreview.value = false
                    } else {
                        onBack()
                    }
                },
                onToolSelected = { tool -> selectTool(tool) },
                onPrevious = { currentPageIndex.value = (currentPageIndex.value - 1).coerceAtLeast(0) },
                onNext = { currentPageIndex.value = (currentPageIndex.value + 1).coerceAtMost(pages.lastIndex) },
                onFullScreen = { fullScreenPreview.value = !fullScreenPreview.value },
                onOverlayChanged = saveOverlayStrokes,
                onStyleSelect = { overlayStyle.value = it },
                onToggleOverlayVisible = { overlayVisible.value = !overlayVisible.value },
                onToggleOverlayEditing = {
                    val nextEditing = !overlayEditingEnabled.value
                    overlayEditingEnabled.value = nextEditing
                    if (nextEditing) {
                        overlayVisible.value = true
                        activeTool.value = NoteTool.PEN
                        activePanel.value = null
                    }
                },
                onUndo = { undoOverlay() },
                onRedo = { redoOverlay() },
                onClear = { clearOverlayPage() },
                onClearAll = { clearAllOverlaysForNote() },
                onExportDiagnostics = { exportDiagnostics() },
                onExportCanonicalModelJson = { exportCanonicalModelJson() },
                onExportFeatureCompatibilityJson = { exportFeatureCompatibilityJson() },
                onExportOverlayJson = { exportOverlayJson() },
                onExportOverlayPng = { exportOverlayPng() },
                onExportOverlayPdf = { exportOverlayPdf() },
                onExportCombinedPng = { exportCombinedPreviewPng() },
                onExportCombinedPdf = { exportCombinedPreviewPdf() },
                onExportPackage = { exportVellumSyncPackage() },
                onSharePackage = { shareVellumSyncPackage() },
                onExportWriteBackSafeCopy = { exportWriteBackSafeCopy() },
                onExportWriteBackAppendProbeCopy = { exportWriteBackAppendProbeCopy() },
                onExportWriteBackWriterCandidateCopy = { exportWriteBackWriterCandidateCopy() },
                onExportWriteBackResearchReport = { exportWriteBackResearchReport() },
                onExportWriteBackValidationReport = { exportWriteBackValidationReport() },
                onExportWriteBackWriterValidationReport = { exportWriteBackWriterValidationReport() },
                onExportDeviceDiagnostics = { exportDeviceDiagnostics() },
                onTransformSelect = { mode ->
                    selectedTransformMode.value = mode
                    ViewerTransformPersistence.save(context, selection.sha256, mode)
                },
                onRenderModeSelect = { mode -> renderMode.value = mode },
                onRailPositionChange = { position -> setRailPosition(position) },
                onToggleDetails = { showNoteDetailsPanel.value = !showNoteDetailsPanel.value },
                onAttachPdfReference = { attachPdfReference() }
            )
        }
    )
}

private fun bindXmlNoteSurface(
    root: View,
    selection: ViewerNoteSelection,
    report: SupernoteInspectionReport?,
    error: String?,
    page: SupernoteStrokeGeometryPageReport?,
    pageIndex: Int,
    totalPages: Int,
    transformMode: SupernotePreviewTransformMode,
    renderMode: SupernoteRenderMode,
    renderedVisualPage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedVisualPage?,
    renderedPdfReferencePage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedPdfReferencePage?,
    renderStatusText: String,
    railPosition: RailPosition,
    activeTool: NoteTool,
    activePanel: NoteTool?,
    fullScreen: Boolean,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayEraserEnabled: Boolean,
    canUndoOverlay: Boolean,
    canRedoOverlay: Boolean,
    overlayStyle: LocalAnnotationStrokeStyle,
    overlayStrokes: List<LocalAnnotationStroke>,
    showNoteDetails: Boolean,
    exportError: String?,
    onBack: () -> Unit,
    onToolSelected: (NoteTool) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFullScreen: () -> Unit,
    onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit,
    onToggleOverlayVisible: () -> Unit,
    onToggleOverlayEditing: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onExportCanonicalModelJson: () -> Unit,
    onExportFeatureCompatibilityJson: () -> Unit,
    onExportOverlayJson: () -> Unit,
    onExportOverlayPng: () -> Unit,
    onExportOverlayPdf: () -> Unit,
    onExportCombinedPng: () -> Unit,
    onExportCombinedPdf: () -> Unit,
    onExportPackage: () -> Unit,
    onSharePackage: () -> Unit,
    onExportWriteBackSafeCopy: () -> Unit,
    onExportWriteBackAppendProbeCopy: () -> Unit,
    onExportWriteBackWriterCandidateCopy: () -> Unit,
    onExportWriteBackResearchReport: () -> Unit,
    onExportWriteBackValidationReport: () -> Unit,
    onExportWriteBackWriterValidationReport: () -> Unit,
    onExportDeviceDiagnostics: () -> Unit,
    onTransformSelect: (SupernotePreviewTransformMode) -> Unit,
    onRenderModeSelect: (SupernoteRenderMode) -> Unit,
    onRailPositionChange: (RailPosition) -> Unit,
    onToggleDetails: () -> Unit,
    onAttachPdfReference: () -> Unit
) {
    val titleView = root.findViewById<TextView>(R.id.text_note_title)
    val statusView = root.findViewById<TextView>(R.id.text_status)
    val pageIndicator = root.findViewById<TextView>(R.id.text_page_indicator)
    val back = root.findViewById<TextView>(R.id.button_back)
    val undo = root.findViewById<TextView>(R.id.button_undo)
    val redo = root.findViewById<TextView>(R.id.button_redo)
    val full = root.findViewById<TextView>(R.id.button_fullscreen)
    val canvasFull = root.findViewById<TextView>(R.id.button_canvas_fullscreen)
    val prev = root.findViewById<TextView>(R.id.button_previous_page)
    val next = root.findViewById<TextView>(R.id.button_next_page)
    val canvasStatus = root.findViewById<TextView>(R.id.text_canvas_status)
    val panel = root.findViewById<LinearLayout>(R.id.floating_panel_container)
    val panelTitle = root.findViewById<TextView>(R.id.panel_title)
    val panelContent = root.findViewById<LinearLayout>(R.id.panel_content_area)

    titleView.text = selection.fileName
    statusView.text = if (report == null) {
        "Unable to open note: ${error ?: "No decoded preview pages"}"
    } else {
        "Read-only base · $renderStatusText · ${surfaceStatus(overlayVisible, overlayEditingEnabled)}"
    }
    pageIndicator.text = if (totalPages > 0 && page != null) "${page.pageNumber} / $totalPages" else "—"
    back.text = if (fullScreen) "×" else "←"
    back.setOnClickListener { onBack() }
    undo.setTextColor(root.color(if (canUndoOverlay) R.color.eink_text_primary else R.color.eink_disabled))
    undo.setOnClickListener { if (canUndoOverlay) onUndo() }
    redo.setTextColor(root.color(if (canRedoOverlay) R.color.eink_text_primary else R.color.eink_disabled))
    redo.setOnClickListener { if (canRedoOverlay) onRedo() }
    full.text = if (fullScreen) "Exit" else "⛶"
    full.setOnClickListener { onFullScreen() }
    canvasFull.text = if (fullScreen) "Exit" else "⛶"
    canvasFull.setOnClickListener { onFullScreen() }

    prev.setEnabledState(root, pageIndex > 0)
    next.setEnabledState(root, pageIndex < totalPages - 1)
    prev.setOnClickListener { if (pageIndex > 0) onPrevious() }
    next.setOnClickListener { if (pageIndex < totalPages - 1) onNext() }
    canvasStatus.text = if (page != null) {
        "Page ${page.pageNumber}/$totalPages · $renderStatusText · ${toolStatus(activeTool, overlayVisible, overlayEditingEnabled, overlayEraserEnabled)}"
    } else {
        "No preview available"
    }

    updateRailPosition(root, railPosition)
    bindRail(root, activeTool, activePanel, onToolSelected)
    bindPreview(
        root = root,
        page = page,
        transformMode = transformMode,
        renderMode = renderMode,
        renderedVisualPage = renderedVisualPage,
        renderedPdfReferencePage = renderedPdfReferencePage,
        overlayVisible = overlayVisible,
        overlayEditingEnabled = overlayEditingEnabled,
        overlayEraserEnabled = overlayEraserEnabled,
        panZoomEnabled = activeTool == NoteTool.ZOOM,
        overlayStyle = overlayStyle,
        overlayStrokes = overlayStrokes,
        onOverlayChanged = onOverlayChanged
    )

    if (activePanel == null && exportError == null) {
        panel.visibility = View.GONE
    } else {
        panel.visibility = View.VISIBLE
        positionPanel(panel, railPosition)
        panelContent.removeAllViews()
        exportError?.let { message ->
            panelTitle.text = "Export error"
            panelContent.addView(panelText(root, message))
        }
        when (activePanel) {
            NoteTool.VIEW -> renderViewPanel(root, panelTitle, panelContent, transformMode, renderMode, renderStatusText, onTransformSelect, onRenderModeSelect, onAttachPdfReference)
            NoteTool.PEN -> renderPenPanel(root, panelTitle, panelContent, overlayStyle, onStyleSelect, onToggleOverlayEditing, overlayEditingEnabled)
            NoteTool.STYLE -> renderStylePanel(root, panelTitle, panelContent, overlayStyle, onStyleSelect)
            NoteTool.LAYERS -> renderLayerPanel(root, panelTitle, panelContent, overlayVisible, overlayEditingEnabled, overlayEraserEnabled, overlayStrokes.size, onToggleOverlayVisible, onToggleOverlayEditing, onClear, onClearAll)
            NoteTool.PAGE -> renderPagePanel(root, panelTitle, panelContent, page, pageIndex, totalPages, onPrevious, onNext, onFullScreen)
            NoteTool.ZOOM -> renderZoomPanel(root, panelTitle, panelContent)
            NoteTool.EXPORT -> renderExportPanel(root, panelTitle, panelContent, onExportOverlayJson, onExportOverlayPng, onExportOverlayPdf, onExportCombinedPng, onExportCombinedPdf, onExportPackage, onSharePackage)
            NoteTool.SYNC -> renderSyncPanel(
                root,
                panelTitle,
                panelContent,
                overlayStrokes.size,
                onExportWriteBackSafeCopy,
                onExportWriteBackAppendProbeCopy,
                onExportWriteBackWriterCandidateCopy,
                onExportWriteBackResearchReport,
                onExportWriteBackValidationReport,
                onExportWriteBackWriterValidationReport
            )
            NoteTool.SETTINGS -> renderSettingsPanel(root, panelTitle, panelContent, railPosition, onRailPositionChange, onExportDeviceDiagnostics, onExportDiagnostics, onExportCanonicalModelJson, onExportFeatureCompatibilityJson)
            NoteTool.MORE -> renderMorePanel(root, panelTitle, panelContent, report, page, showNoteDetails, canUndoOverlay, canRedoOverlay, onUndo, onRedo, onClear, onClearAll, onToggleDetails)
            NoteTool.ERASER -> renderEraserPanel(root, panelTitle, panelContent, canUndoOverlay, canRedoOverlay, overlayStrokes.size, onUndo, onRedo, onClear, onClearAll)
            else -> Unit
        }
    }
}

private fun bindPreview(
    root: View,
    page: SupernoteStrokeGeometryPageReport?,
    transformMode: SupernotePreviewTransformMode,
    renderMode: SupernoteRenderMode,
    renderedVisualPage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedVisualPage?,
    renderedPdfReferencePage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedPdfReferencePage?,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayEraserEnabled: Boolean,
    panZoomEnabled: Boolean,
    overlayStyle: LocalAnnotationStrokeStyle,
    overlayStrokes: List<LocalAnnotationStroke>,
    onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit
) {
    root.findViewById<VellumNoteSurfaceView>(R.id.overlay_layer).bindPage(
        pageReport = page,
        transformMode = transformMode,
        renderMode = renderMode,
        renderedVisualPage = renderedVisualPage,
        renderedPdfReferencePage = renderedPdfReferencePage,
        overlayEditingEnabled = overlayEditingEnabled,
        overlayEraserEnabled = overlayEraserEnabled,
        overlayVisible = overlayVisible,
        panZoomEnabled = panZoomEnabled,
        overlayStrokes = overlayStrokes,
        currentOverlayStyle = overlayStyle,
        onOverlayChanged = onOverlayChanged
    )
}

private fun bindRail(
    root: View,
    activeTool: NoteTool,
    activePanel: NoteTool?,
    onToolSelected: (NoteTool) -> Unit
) {
    val rail = root.findViewById<LinearLayout>(R.id.tool_rail)
    val inflater = LayoutInflater.from(root.context)
    rail.removeAllViews()

    val primaryTools = listOf(
        NoteTool.VIEW,
        NoteTool.PEN,
        NoteTool.ERASER,
        NoteTool.STYLE,
        NoteTool.LAYERS,
        NoteTool.PAGE,
        NoteTool.ZOOM,
        NoteTool.EXPORT,
        NoteTool.SYNC,
        NoteTool.SETTINGS
    )

    primaryTools.forEach { tool ->
        rail.addView(railItemView(inflater, rail, root, tool, activeTool == tool || activePanel == tool, onToolSelected))
    }

    rail.addView(Space(root.context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    rail.addView(railItemView(inflater, rail, root, NoteTool.MORE, activeTool == NoteTool.MORE || activePanel == NoteTool.MORE, onToolSelected))
}

private fun railItemView(
    inflater: LayoutInflater,
    parent: ViewGroup,
    root: View,
    tool: NoteTool,
    active: Boolean,
    onToolSelected: (NoteTool) -> Unit
): View {
    return inflater.inflate(R.layout.item_rail_button, parent, false).apply {
        findViewById<TextView>(R.id.rail_icon).text = tool.iconGlyph
        findViewById<TextView>(R.id.tool_label).text = tool.railLabel
        setBackgroundResource(if (active) R.drawable.bg_active_tool else R.drawable.bg_tool_clear)
        val color = root.color(if (active) R.color.eink_text_primary else R.color.eink_text_secondary)
        findViewById<TextView>(R.id.rail_icon).setTextColor(color)
        findViewById<TextView>(R.id.tool_label).setTextColor(color)
        findViewById<TextView>(R.id.tool_label).setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        setOnClickListener { onToolSelected(tool) }
    }
}

private fun updateRailPosition(root: View, railPosition: RailPosition) {
    val row = root.findViewById<LinearLayout>(R.id.surface_row)
    val rail = root.findViewById<LinearLayout>(R.id.tool_rail)
    val divider = root.findViewById<View>(R.id.rail_divider)
    val shell = root.findViewById<FrameLayout>(R.id.note_canvas_container)
    row.removeView(rail)
    row.removeView(divider)
    row.removeView(shell)
    if (railPosition == RailPosition.LEFT) {
        row.addView(rail)
        row.addView(divider)
        row.addView(shell)
    } else {
        row.addView(shell)
        row.addView(divider)
        row.addView(rail)
    }
}

private fun positionPanel(panel: LinearLayout, railPosition: RailPosition) {
    val params = panel.layoutParams as FrameLayout.LayoutParams
    params.gravity = if (railPosition == RailPosition.LEFT) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
    panel.layoutParams = params
}

private fun renderViewPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    transformMode: SupernotePreviewTransformMode,
    renderMode: SupernoteRenderMode,
    renderStatusText: String,
    onTransformSelect: (SupernotePreviewTransformMode) -> Unit,
    onRenderModeSelect: (SupernoteRenderMode) -> Unit,
    onAttachPdfReference: () -> Unit
) {
    title.text = "View"
    content.addView(panelText(root, "Render mode: $renderStatusText"))
    content.addView(panelText(root, "Visual layer uses decoded NOTE bitmap first, then a same-basename cached PDF reference. Vector modes are debug only."))
    content.addView(textButton(root, "Attach PDF reference", false, onAttachPdfReference))
    content.addView(textButton(root, "Visual layer", renderMode == SupernoteRenderMode.VISUAL_LAYER) { onRenderModeSelect(SupernoteRenderMode.VISUAL_LAYER) })
    content.addView(textButton(root, "Vector debug", renderMode == SupernoteRenderMode.VECTOR_DEBUG) { onRenderModeSelect(SupernoteRenderMode.VECTOR_DEBUG) })
    content.addView(textButton(root, "Raw-fit debug", renderMode == SupernoteRenderMode.RAW_FIT_DEBUG) { onRenderModeSelect(SupernoteRenderMode.RAW_FIT_DEBUG) })
    content.addView(panelText(root, "Transform calibration"))
    content.addView(panelText(root, "Used by Vector debug. Raw-fit debug uses the raw-fit transform directly."))
    val a5xModes = preferredA5xViewModes()
    a5xModes.forEach { mode ->
        content.addView(textButton(root, mode.label, transformMode == mode) { onTransformSelect(mode) })
    }
}

private fun preferredA5xViewModes(): List<SupernotePreviewTransformMode> {
    val preferred = listOf("a5x-raw", "a5x-portrait-candidate", "a5x-portrait-candidate-2")
    val byId = preferred.mapNotNull { wanted ->
        SupernotePreviewTransformMode.viewerOrder.firstOrNull { it.id.equals(wanted, ignoreCase = true) }
    }
    if (byId.size == preferred.size) {
        return byId
    }
    val byLabel = SupernotePreviewTransformMode.viewerOrder.filter { mode ->
        val label = mode.label.lowercase()
        label.contains("a5x raw") ||
            label.contains("portrait 1") ||
            label.contains("portrait candidate 1") ||
            label.contains("portrait 2") ||
            label.contains("portrait candidate 2")
    }
    return if (byLabel.isNotEmpty()) byLabel else SupernotePreviewTransformMode.viewerOrder
}

private fun renderPenPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    style: LocalAnnotationStrokeStyle,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit,
    onToggleOverlayEditing: () -> Unit,
    overlayEditingEnabled: Boolean
) {
    title.text = "Pen"
    content.addView(panelText(root, "Choose the local sidecar pen. Supernote-native tool encoding is approximate during copy-only write-back."))
    content.addView(panelText(root, "Pen type"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Needle", style.toolKind == LocalAnnotationToolKind.NEEDLE_POINT) { onStyleSelect(style.copy(toolKind = LocalAnnotationToolKind.NEEDLE_POINT, width = LocalAnnotationWidth.THIN)) },
        textButton(root, "Ink", style.toolKind == LocalAnnotationToolKind.INK_PEN) { onStyleSelect(style.copy(toolKind = LocalAnnotationToolKind.INK_PEN)) },
        textButton(root, "Marker", style.toolKind == LocalAnnotationToolKind.MARKER) { onStyleSelect(style.copy(toolKind = LocalAnnotationToolKind.MARKER, width = LocalAnnotationWidth.THICK)) }
    )))
    content.addView(textButton(root, if (overlayEditingEnabled) "Overlay editing on" else "Enable overlay editing", overlayEditingEnabled, onToggleOverlayEditing))
}

private fun renderStylePanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    style: LocalAnnotationStrokeStyle,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit
) {
    title.text = "Style"
    content.addView(panelText(root, "Grayscale color"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "White", style.color == LocalAnnotationColor.WHITE) { onStyleSelect(style.copy(color = LocalAnnotationColor.WHITE)) },
        textButton(root, "Light", style.color == LocalAnnotationColor.LIGHT_GRAY) { onStyleSelect(style.copy(color = LocalAnnotationColor.LIGHT_GRAY)) }
    )))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Dark", style.color == LocalAnnotationColor.DARK_GRAY) { onStyleSelect(style.copy(color = LocalAnnotationColor.DARK_GRAY)) },
        textButton(root, "Black", style.color == LocalAnnotationColor.BLACK) { onStyleSelect(style.copy(color = LocalAnnotationColor.BLACK)) }
    )))
    content.addView(panelText(root, "Width"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Thin", style.width == LocalAnnotationWidth.THIN) { onStyleSelect(style.copy(width = LocalAnnotationWidth.THIN)) },
        textButton(root, "Medium", style.width == LocalAnnotationWidth.MEDIUM) { onStyleSelect(style.copy(width = LocalAnnotationWidth.MEDIUM)) },
        textButton(root, "Thick", style.width == LocalAnnotationWidth.THICK) { onStyleSelect(style.copy(width = LocalAnnotationWidth.THICK)) }
    )))
}

private fun renderLayerPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayEraserEnabled: Boolean,
    strokeCount: Int,
    onToggleOverlayVisible: () -> Unit,
    onToggleOverlayEditing: () -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit
) {
    title.text = "Layers"
    content.addView(panelText(root, "Supernote base: visible, read-only"))
    content.addView(panelText(root, "VellumSync overlay: ${if (overlayVisible) "visible" else "hidden"}, ${if (overlayEditingEnabled) "pen editable" else if (overlayEraserEnabled) "eraser active" else "locked"}, $strokeCount stroke(s)"))
    content.addView(textButton(root, if (overlayVisible) "Hide overlay" else "Show overlay", false, onToggleOverlayVisible))
    content.addView(textButton(root, if (overlayEditingEnabled) "Lock overlay" else "Edit overlay", overlayEditingEnabled, onToggleOverlayEditing))
    content.addView(textButton(root, "Clear current page", false, onClear))
    content.addView(textButton(root, "Clear all overlays for this note", false, onClearAll))
}

private fun renderPagePanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    page: SupernoteStrokeGeometryPageReport?,
    pageIndex: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFullScreen: () -> Unit
) {
    title.text = "Page"
    content.addView(panelText(root, if (page == null) "No page loaded" else "Page ${page.pageNumber} of $totalPages"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Previous", false, onPrevious).also { it.isEnabled = pageIndex > 0 },
        textButton(root, "Next", false, onNext).also { it.isEnabled = pageIndex < totalPages - 1 },
        textButton(root, "Full screen", false, onFullScreen)
    )))
}

private fun renderZoomPanel(root: View, title: TextView, content: LinearLayout) {
    title.text = "Zoom"
    content.addView(panelText(root, "Use finger pan/pinch only while Zoom is selected. Other tools keep the page stationary."))
    content.addView(panelText(root, "Switch away from Zoom to lock the page position for writing and navigation."))
}

private fun renderExportPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    onExportOverlayJson: () -> Unit,
    onExportOverlayPng: () -> Unit,
    onExportOverlayPdf: () -> Unit,
    onExportCombinedPng: () -> Unit,
    onExportCombinedPdf: () -> Unit,
    onExportPackage: () -> Unit,
    onSharePackage: () -> Unit
) {
    title.text = "Export"
    content.addView(panelText(root, "Export previews and local sidecar packages. Diagnostics live in Settings."))
    content.addView(panelText(root, "Combined preview"))
    content.addView(textButton(root, "Export current page PNG", false, onExportCombinedPng))
    content.addView(textButton(root, "Export note preview PDF", false, onExportCombinedPdf))
    content.addView(panelText(root, "Overlay only"))
    content.addView(textButton(root, "Export overlay JSON", false, onExportOverlayJson))
    content.addView(textButton(root, "Export overlay PNG", false, onExportOverlayPng))
    content.addView(textButton(root, "Export overlay PDF", false, onExportOverlayPdf))
    content.addView(panelText(root, "Package"))
    content.addView(textButton(root, "Export VellumSync package", false, onExportPackage))
    content.addView(textButton(root, "Share VellumSync package", false, onSharePackage))
}

private fun renderSyncPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    currentPageOverlayStrokeCount: Int,
    onExportWriteBackSafeCopy: () -> Unit,
    onExportWriteBackAppendProbeCopy: () -> Unit,
    onExportWriteBackWriterCandidateCopy: () -> Unit,
    onExportWriteBackResearchReport: () -> Unit,
    onExportWriteBackValidationReport: () -> Unit,
    onExportWriteBackWriterValidationReport: () -> Unit
) {
    title.text = "Sync"
    content.addView(panelText(root, "Supernote-created notes only. VellumSync creates copy-only sync candidates and never overwrites the original .note."))
    content.addView(panelText(root, "Current page local overlay strokes available for sync copy: $currentPageOverlayStrokeCount"))
    content.addView(panelText(root, "Create Supernote sync copy"))
    content.addView(textButton(root, "Export Supernote sync copy", false, onExportWriteBackWriterCandidateCopy))
    content.addView(textButton(root, "Export sync validation report", false, onExportWriteBackWriterValidationReport))
    content.addView(panelText(root, "Compatibility safety"))
    content.addView(textButton(root, "Export byte-for-byte safe copy", false, onExportWriteBackSafeCopy))
    content.addView(textButton(root, "Export compatibility validation report", false, onExportWriteBackValidationReport))
    content.addView(panelText(root, "Research/debug actions"))
    content.addView(textButton(root, "Export append-probe research copy", false, onExportWriteBackAppendProbeCopy))
    content.addView(textButton(root, "Export write-back research report", false, onExportWriteBackResearchReport))
    content.addView(panelText(root, "Use Supernote Partner/device validation before syncing a candidate back to a production notebook."))
}

private fun renderSettingsPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    railPosition: RailPosition,
    onRailPositionChange: (RailPosition) -> Unit,
    onExportDeviceDiagnostics: () -> Unit,
    onExportNoteDiagnostics: () -> Unit,
    onExportCanonicalModelJson: () -> Unit,
    onExportFeatureCompatibilityJson: () -> Unit
) {
    title.text = "Settings"
    content.addView(panelText(root, "Rail position: ${railPosition.label}"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Left rail", railPosition == RailPosition.LEFT) { onRailPositionChange(RailPosition.LEFT) },
        textButton(root, "Right rail", railPosition == RailPosition.RIGHT) { onRailPositionChange(RailPosition.RIGHT) }
    )))
    val profile = DeviceCapabilityDetector.detect()
    val stylusConfirmed = StylusProbePersistence.isStylusConfirmed(root.context)
    content.addView(panelText(root, "Device"))
    content.addView(panelText(root, "Stylus support: ${profile.stylusSupportStatus.name}; probe confirmed: $stylusConfirmed"))
    content.addView(panelText(root, "Device diagnostics: ${profile.manufacturer} ${profile.model} / Android ${profile.androidRelease} / SDK ${profile.sdkInt}"))
    content.addView(textButton(root, "Export device diagnostics", false, onExportDeviceDiagnostics))
    content.addView(panelText(root, "Note diagnostics"))
    content.addView(textButton(root, "Export note diagnostics JSON", false, onExportNoteDiagnostics))
    content.addView(textButton(root, "Export feature compatibility JSON", false, onExportFeatureCompatibilityJson))
    content.addView(textButton(root, "Export canonical model JSON", false, onExportCanonicalModelJson))
}

private fun renderMorePanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    report: SupernoteInspectionReport?,
    page: SupernoteStrokeGeometryPageReport?,
    showNoteDetails: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit,
    onToggleDetails: () -> Unit
) {
    title.text = "More"
    content.addView(panelText(root, "Quick actions"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Undo", canUndo, onUndo).also { it.isEnabled = canUndo },
        textButton(root, "Redo", canRedo, onRedo).also { it.isEnabled = canRedo }
    )))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Clear page", false, onClear),
        textButton(root, "Clear all", false, onClearAll)
    )))
    content.addView(textButton(root, if (showNoteDetails) "Hide note details" else "Show note details", showNoteDetails, onToggleDetails))
    if (showNoteDetails && report != null) {
        content.addView(panelText(root, "Note: ${report.fileName}"))
        content.addView(panelText(root, "Version: ${report.versionMarker ?: "not detected"}"))
        content.addView(panelText(root, "Equipment: ${report.detectedEquipment ?: "not detected"}"))
        content.addView(panelText(root, "Pages: ${report.strokeGeometryReport.totalPages}"))
        page?.let { content.addView(panelText(root, "Current page rendered: ${it.renderedRecords}/${it.decodedRecords}")) }
        content.addView(panelText(root, "Original .note remains read-only. Local annotations are sidecar overlays."))
    }
}

private fun renderEraserPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    canUndo: Boolean,
    canRedo: Boolean,
    strokeCount: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit
) {
    title.text = "Erase"
    content.addView(panelText(root, "Eraser type"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Stroke", true) {},
        textButton(root, "Regular", false) {},
        textButton(root, "Region", false) {}
    )))
    content.addView(panelText(root, "Stroke eraser is active: drag the Boox pen over local overlay ink to remove the nearest sidecar stroke. Regular and region eraser modes are preserved for the roadmap."))
    content.addView(panelText(root, "Current page overlay strokes: $strokeCount"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Undo", canUndo, onUndo).also { it.isEnabled = canUndo },
        textButton(root, "Redo", canRedo, onRedo).also { it.isEnabled = canRedo }
    )))
    content.addView(textButton(root, "Clear current page", false, onClear))
    content.addView(textButton(root, "Clear all overlays for this note", false, onClearAll))
}

private fun renderStatusText(
    renderMode: SupernoteRenderMode,
    renderedVisualPage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedVisualPage?,
    renderedPdfReferencePage: io.github.piyushdaiya.vellumsync.note.SupernoteRenderedPdfReferencePage?
): String {
    return when (renderMode) {
        SupernoteRenderMode.VISUAL_LAYER -> when {
            renderedVisualPage?.usable == true -> "Visual layer active"
            renderedPdfReferencePage?.usable == true -> "PDF reference layer active"
            else -> "Visual layer unavailable"
        }
        SupernoteRenderMode.VECTOR_DEBUG,
        SupernoteRenderMode.RAW_FIT_DEBUG -> "Vector fallback active"
    }
}

private fun appendJsonObjectField(baseJson: String, fieldName: String, fieldJson: String): String {
    val trimmed = baseJson.trim()
    return if (trimmed.endsWith("}")) {
        trimmed.dropLast(1) + ",\"" + fieldName + "\":" + fieldJson + "}"
    } else {
        baseJson
    }
}

private fun panelText(root: View, value: String): TextView {
    return TextView(root.context).apply {
        text = value
        setTextColor(root.color(R.color.eink_text_secondary))
        textSize = 13f
        setPadding(0, 6, 0, 6)
    }
}

private fun textButton(root: View, label: String, active: Boolean, onClick: () -> Unit): TextView {
    return TextView(root.context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(root.color(R.color.eink_text_primary))
        setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        setBackgroundResource(if (active) R.drawable.bg_text_button_active else R.drawable.bg_text_button)
        setPadding(12, 8, 12, 8)
        minHeight = 36
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 4, 0, 4)
        }
    }
}

private fun rowOfButtons(root: View, buttons: List<TextView>): LinearLayout {
    return LinearLayout(root.context).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { button ->
            addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 4, 2, 4)
            })
        }
    }
}

private fun horizontalScroll(root: View, child: View): HorizontalScrollView {
    return HorizontalScrollView(root.context).apply {
        addView(child)
    }
}

private fun TextView.setEnabledState(root: View, enabledState: Boolean) {
    isEnabled = enabledState
    setTextColor(root.color(if (enabledState) R.color.eink_text_primary else R.color.eink_disabled))
}

private fun View.color(resourceId: Int): Int = context.getColor(resourceId)

private fun surfaceStatus(overlayVisible: Boolean, overlayEditingEnabled: Boolean): String {
    return when {
        overlayEditingEnabled -> "Overlay pen editing"
        overlayVisible -> "Overlay visible"
        else -> "Overlay hidden"
    }
}

private fun toolStatus(
    activeTool: NoteTool,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayEraserEnabled: Boolean
): String {
    return when (activeTool) {
        NoteTool.VIEW -> "View"
        NoteTool.PEN -> if (overlayEditingEnabled) "Pen writes sidecar" else "Pen"
        NoteTool.ERASER -> if (overlayEraserEnabled) "Eraser removes sidecar strokes" else "Erase"
        NoteTool.STYLE -> "Style"
        NoteTool.LAYERS -> if (overlayVisible) "Overlay visible" else "Overlay hidden"
        NoteTool.PAGE -> "Page"
        NoteTool.ZOOM -> "Zoom"
        NoteTool.EXPORT -> "Export"
        NoteTool.SYNC -> "Sync copy"
        NoteTool.SETTINGS -> "Settings"
        NoteTool.MORE -> "More"
    }
}
