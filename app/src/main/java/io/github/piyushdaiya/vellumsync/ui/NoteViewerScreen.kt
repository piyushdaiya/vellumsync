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
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationWidth
import io.github.piyushdaiya.vellumsync.note.OverlayRenderExporter
import io.github.piyushdaiya.vellumsync.note.SupernoteInspectionReport
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

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

    fun setRailPosition(position: RailPosition) {
        railPosition.value = position
        RailPositionPersistence.save(context, position)
    }

    fun undoOverlay() {
        if (overlayStrokes.value.isNotEmpty()) {
            saveOverlayStrokes(overlayStrokes.value.dropLast(1))
        }
    }

    fun clearOverlayPage() {
        val page = selectedPage ?: return
        LocalAnnotationOverlayStore.clearPage(context, selection.sha256, page.pageNumber)
        saveOverlayStrokes(emptyList())
    }

    fun exportDiagnostics() {
        val inspection = report ?: return
        pendingExportJson.value = LocalAnnotationOverlayStore.appendOverlayDiagnostics(
            baseReportJson = inspection.toJson(),
            context = context,
            noteSha256 = selection.sha256,
            totalPages = inspection.strokeGeometryReport.totalPages
        )
        exportLauncher.launch("vellumsync-note-diagnostics-${inspection.sha256.take(12)}.json")
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
                activePanel.value = null
                overlayVisible.value = true
                overlayEditingEnabled.value = true
            }
            NoteTool.ERASER -> {
                activeTool.value = tool
                activePanel.value = NoteTool.ERASER
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
                totalPages = pages.size,
                transformMode = selectedTransformMode.value,
                railPosition = railPosition.value,
                activeTool = activeTool.value,
                activePanel = activePanel.value,
                fullScreen = fullScreenPreview.value,
                overlayVisible = overlayVisible.value,
                overlayEditingEnabled = overlayEditingEnabled.value,
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
                onClear = { clearOverlayPage() },
                onExportDiagnostics = { exportDiagnostics() },
                onExportOverlayJson = { exportOverlayJson() },
                onExportOverlayPng = { exportOverlayPng() },
                onExportOverlayPdf = { exportOverlayPdf() },
                onExportDeviceDiagnostics = { exportDeviceDiagnostics() },
                onTransformSelect = { mode ->
                    selectedTransformMode.value = mode
                    ViewerTransformPersistence.save(context, selection.sha256, mode)
                },
                onRailPositionChange = { position -> setRailPosition(position) },
                onToggleDetails = { showNoteDetailsPanel.value = !showNoteDetailsPanel.value }
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
    railPosition: RailPosition,
    activeTool: NoteTool,
    activePanel: NoteTool?,
    fullScreen: Boolean,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
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
    onClear: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onExportOverlayJson: () -> Unit,
    onExportOverlayPng: () -> Unit,
    onExportOverlayPdf: () -> Unit,
    onExportDeviceDiagnostics: () -> Unit,
    onTransformSelect: (SupernotePreviewTransformMode) -> Unit,
    onRailPositionChange: (RailPosition) -> Unit,
    onToggleDetails: () -> Unit
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
        "Read-only base · ${surfaceStatus(overlayVisible, overlayEditingEnabled)} · ${page?.renderedRecords ?: 0}/${page?.decodedRecords ?: 0} rendered"
    }
    pageIndicator.text = if (totalPages > 0 && page != null) "${page.pageNumber} / $totalPages" else "—"
    back.text = if (fullScreen) "×" else "←"
    back.setOnClickListener { onBack() }
    undo.setTextColor(root.color(if (overlayStrokes.isNotEmpty()) R.color.eink_text_primary else R.color.eink_disabled))
    undo.setOnClickListener { onUndo() }
    redo.setTextColor(root.color(R.color.eink_disabled))
    redo.setOnClickListener { }
    full.text = if (fullScreen) "Exit" else "⛶"
    full.setOnClickListener { onFullScreen() }
    canvasFull.text = if (fullScreen) "Exit" else "⛶"
    canvasFull.setOnClickListener { onFullScreen() }

    prev.setEnabledState(root, pageIndex > 0)
    next.setEnabledState(root, pageIndex < totalPages - 1)
    prev.setOnClickListener { if (pageIndex > 0) onPrevious() }
    next.setOnClickListener { if (pageIndex < totalPages - 1) onNext() }
    canvasStatus.text = if (page != null) {
        "Page ${page.pageNumber}/$totalPages · ${page.renderedRecords}/${page.decodedRecords} · ${transformMode.label} · ${toolStatus(activeTool, overlayVisible, overlayEditingEnabled)}"
    } else {
        "No preview available"
    }

    updateRailPosition(root, railPosition)
    bindRail(root, activeTool, activePanel, onToolSelected)
    bindPreview(
        root = root,
        page = page,
        transformMode = transformMode,
        overlayVisible = overlayVisible,
        overlayEditingEnabled = overlayEditingEnabled,
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
            NoteTool.VIEW -> renderViewPanel(root, panelTitle, panelContent, transformMode, onTransformSelect)
            NoteTool.STYLE -> renderStylePanel(root, panelTitle, panelContent, overlayStyle, onStyleSelect)
            NoteTool.LAYERS -> renderLayerPanel(root, panelTitle, panelContent, overlayVisible, overlayEditingEnabled, overlayStrokes.size, onToggleOverlayVisible, onToggleOverlayEditing, onClear)
            NoteTool.PAGE -> renderPagePanel(root, panelTitle, panelContent, page, pageIndex, totalPages, onPrevious, onNext, onFullScreen)
            NoteTool.ZOOM -> renderZoomPanel(root, panelTitle, panelContent)
            NoteTool.EXPORT -> renderExportPanel(root, panelTitle, panelContent, onExportDiagnostics, onExportOverlayJson, onExportOverlayPng, onExportOverlayPdf)
            NoteTool.SYNC -> renderSyncPanel(root, panelTitle, panelContent)
            NoteTool.SETTINGS -> renderSettingsPanel(root, panelTitle, panelContent, railPosition, onRailPositionChange, onExportDeviceDiagnostics)
            NoteTool.MORE -> renderMorePanel(root, panelTitle, panelContent, report, page, showNoteDetails, onUndo, onClear, onToggleDetails)
            NoteTool.ERASER -> renderEraserPanel(root, panelTitle, panelContent, onUndo, onClear)
            else -> Unit
        }
    }
}

private fun bindPreview(
    root: View,
    page: SupernoteStrokeGeometryPageReport?,
    transformMode: SupernotePreviewTransformMode,
    overlayVisible: Boolean,
    overlayEditingEnabled: Boolean,
    overlayStyle: LocalAnnotationStrokeStyle,
    overlayStrokes: List<LocalAnnotationStroke>,
    onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit
) {
    root.findViewById<VellumNoteSurfaceView>(R.id.overlay_layer).bindPage(
        pageReport = page,
        transformMode = transformMode,
        overlayEditingEnabled = overlayEditingEnabled,
        overlayVisible = overlayVisible,
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
    onTransformSelect: (SupernotePreviewTransformMode) -> Unit
) {
    title.text = "View"
    content.addView(panelText(root, "Transform calibration"))
    content.addView(panelText(root, "Choose the A5X orientation used for the note surface."))
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

private fun renderStylePanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    style: LocalAnnotationStrokeStyle,
    onStyleSelect: (LocalAnnotationStrokeStyle) -> Unit
) {
    title.text = "Style"
    content.addView(panelText(root, "Color"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Black", style.color == LocalAnnotationColor.BLACK) { onStyleSelect(style.copy(color = LocalAnnotationColor.BLACK)) },
        textButton(root, "Gray", style.color == LocalAnnotationColor.GRAY) { onStyleSelect(style.copy(color = LocalAnnotationColor.GRAY)) }
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
    strokeCount: Int,
    onToggleOverlayVisible: () -> Unit,
    onToggleOverlayEditing: () -> Unit,
    onClear: () -> Unit
) {
    title.text = "Layers"
    content.addView(panelText(root, "Supernote base: visible, read-only"))
    content.addView(panelText(root, "VellumSync overlay: ${if (overlayVisible) "visible" else "hidden"}, ${if (overlayEditingEnabled) "editable" else "locked"}, $strokeCount stroke(s)"))
    content.addView(textButton(root, if (overlayVisible) "Hide overlay" else "Show overlay", false, onToggleOverlayVisible))
    content.addView(textButton(root, if (overlayEditingEnabled) "Lock overlay" else "Edit overlay", overlayEditingEnabled, onToggleOverlayEditing))
    content.addView(textButton(root, "Clear page overlay", false, onClear))
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
    content.addView(panelText(root, "Use pinch with two fingers. Pan in View mode."))
    content.addView(panelText(root, "Fit page and reset pan controls will be added after XML UI migration is accepted."))
}

private fun renderExportPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    onExportDiagnostics: () -> Unit,
    onExportOverlayJson: () -> Unit,
    onExportOverlayPng: () -> Unit,
    onExportOverlayPdf: () -> Unit
) {
    title.text = "Export"
    content.addView(panelText(root, "Export note, overlay, and diagnostics from the local sidecar workflow."))
    content.addView(textButton(root, "Export note diagnostics JSON", false, onExportDiagnostics))
    content.addView(textButton(root, "Export overlay JSON", false, onExportOverlayJson))
    content.addView(textButton(root, "Export overlay PNG", false, onExportOverlayPng))
    content.addView(textButton(root, "Export overlay PDF", false, onExportOverlayPdf))
}

private fun renderSyncPanel(
    root: View,
    title: TextView,
    content: LinearLayout
) {
    title.text = "Sync"
    content.addView(panelText(root, "Local sidecar active."))
    content.addView(panelText(root, "Supernote .note write-back is disabled until writer validation is complete."))
    content.addView(panelText(root, "Use Export for overlay JSON, PNG, PDF, and diagnostics."))
}

private fun renderSettingsPanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    railPosition: RailPosition,
    onRailPositionChange: (RailPosition) -> Unit,
    onExportDeviceDiagnostics: () -> Unit
) {
    title.text = "Settings"
    content.addView(panelText(root, "Rail position: ${railPosition.label}"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Left rail", railPosition == RailPosition.LEFT) { onRailPositionChange(RailPosition.LEFT) },
        textButton(root, "Right rail", railPosition == RailPosition.RIGHT) { onRailPositionChange(RailPosition.RIGHT) }
    )))
    val profile = DeviceCapabilityDetector.detect()
    val stylusConfirmed = StylusProbePersistence.isStylusConfirmed(root.context)
    content.addView(panelText(root, "Device compatibility"))
    content.addView(panelText(root, "Stylus support: ${profile.stylusSupportStatus.name}; probe confirmed: $stylusConfirmed"))
    content.addView(panelText(root, "Device diagnostics: ${profile.manufacturer} ${profile.model} / Android ${profile.androidRelease} / SDK ${profile.sdkInt}"))
    content.addView(textButton(root, "Export device diagnostics", false, onExportDeviceDiagnostics))
}

private fun renderMorePanel(
    root: View,
    title: TextView,
    content: LinearLayout,
    report: SupernoteInspectionReport?,
    page: SupernoteStrokeGeometryPageReport?,
    showNoteDetails: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onToggleDetails: () -> Unit
) {
    title.text = "More"
    content.addView(panelText(root, "Quick actions"))
    content.addView(rowOfButtons(root, listOf(
        textButton(root, "Undo", false, onUndo),
        textButton(root, "Clear page", false, onClear)
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

private fun renderEraserPanel(root: View, title: TextView, content: LinearLayout, onUndo: () -> Unit, onClear: () -> Unit) {
    title.text = "Erase"
    content.addView(panelText(root, "Eraser v0 uses sidecar undo/clear. Stroke hit-testing comes later."))
    content.addView(textButton(root, "Undo last stroke", false, onUndo))
    content.addView(textButton(root, "Clear page overlay", false, onClear))
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
        overlayEditingEnabled -> "Overlay editing"
        overlayVisible -> "Overlay visible"
        else -> "Overlay hidden"
    }
}

private fun toolStatus(activeTool: NoteTool, overlayVisible: Boolean, overlayEditingEnabled: Boolean): String {
    return when (activeTool) {
        NoteTool.VIEW -> "View"
        NoteTool.PEN -> if (overlayEditingEnabled) "Pen writes sidecar" else "Pen"
        NoteTool.ERASER -> "Erase"
        NoteTool.STYLE -> "Style"
        NoteTool.LAYERS -> if (overlayVisible) "Overlay visible" else "Overlay hidden"
        NoteTool.PAGE -> "Page"
        NoteTool.ZOOM -> "Zoom"
        NoteTool.EXPORT -> "Export"
        NoteTool.SYNC -> "Sync disabled"
        NoteTool.SETTINGS -> "Settings"
        NoteTool.MORE -> "More"
    }
}
