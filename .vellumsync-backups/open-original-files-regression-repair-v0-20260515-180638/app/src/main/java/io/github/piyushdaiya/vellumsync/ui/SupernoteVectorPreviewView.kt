package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationColor
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationPoint
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStrokeStyle
import io.github.piyushdaiya.vellumsync.note.SupernoteGeometryPoint
import io.github.piyushdaiya.vellumsync.note.SupernoteRenderedPdfReferencePage
import io.github.piyushdaiya.vellumsync.note.SupernoteRenderedVisualPage
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport
import android.graphics.Color
import java.util.UUID
import kotlin.math.hypot

class SupernoteVectorPreviewView(
    context: Context,
    private var pageReport: SupernoteStrokeGeometryPageReport?,
    private var transformMode: SupernotePreviewTransformMode = SupernotePreviewTransformMode.A5X_RAW,
    private var renderMode: SupernoteRenderMode = SupernoteRenderMode.VISUAL_LAYER,
    private var renderedVisualPage: SupernoteRenderedVisualPage? = null,
    private var renderedPdfReferencePage: SupernoteRenderedPdfReferencePage? = null,
    private var overlayEditingEnabled: Boolean = false,
    private var overlayEraserEnabled: Boolean = false,
    private var panZoomEnabled: Boolean = false,
    private var overlayVisible: Boolean = true,
    private var overlayStrokes: List<LocalAnnotationStroke> = emptyList(),
    private var currentOverlayStyle: LocalAnnotationStrokeStyle = LocalAnnotationStrokeStyle.DEFAULT,
    private var onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit = {}
) : View(context) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val lightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 150
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val overlayPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 180
    }

    private val ruledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 70
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val visualBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
    }

    private var visualBitmap: Bitmap? = null
    private var visualBitmapPageNumber: Int? = null
    private var visualBitmapPayloadOffset: Int? = null
    private var panX = 0f
    private var panY = 0f
    private var zoomMultiplier = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f
    private var activeGesture = GestureMode.NONE
    private var activeOverlayPoints: MutableList<LocalAnnotationPoint>? = null
    private var activeOverlayToolType: String = "stylus"
    private var lastRenderDiagnostics = ActiveRenderDiagnostics()

    fun updatePageReport(
        pageReport: SupernoteStrokeGeometryPageReport?,
        transformMode: SupernotePreviewTransformMode = this.transformMode,
        renderMode: SupernoteRenderMode = this.renderMode,
        renderedVisualPage: SupernoteRenderedVisualPage? = this.renderedVisualPage,
        renderedPdfReferencePage: SupernoteRenderedPdfReferencePage? = this.renderedPdfReferencePage,
        overlayEditingEnabled: Boolean = this.overlayEditingEnabled,
        overlayEraserEnabled: Boolean = this.overlayEraserEnabled,
        panZoomEnabled: Boolean = this.panZoomEnabled,
        overlayVisible: Boolean = this.overlayVisible,
        overlayStrokes: List<LocalAnnotationStroke> = this.overlayStrokes,
        currentOverlayStyle: LocalAnnotationStrokeStyle = this.currentOverlayStyle,
        onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit = this.onOverlayChanged
    ) {
        val changedPage = this.pageReport?.pageNumber != pageReport?.pageNumber
        val changedTransform = this.transformMode != transformMode
        val changedRenderMode = this.renderMode != renderMode
        this.pageReport = pageReport
        this.transformMode = transformMode
        this.renderMode = renderMode
        this.renderedVisualPage = renderedVisualPage
        this.renderedPdfReferencePage = renderedPdfReferencePage
        rebuildVisualBitmapIfNeeded(renderedVisualPage)
        this.overlayEditingEnabled = overlayEditingEnabled
        this.overlayEraserEnabled = overlayEraserEnabled
        this.panZoomEnabled = panZoomEnabled
        this.overlayVisible = overlayVisible
        this.overlayStrokes = overlayStrokes
        this.currentOverlayStyle = currentOverlayStyle
        this.onOverlayChanged = onOverlayChanged
        if (changedPage || changedTransform || changedRenderMode) resetViewportSilently()
        invalidate()
    }

    fun resetViewport() {
        resetViewportSilently()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (overlayEditingEnabled || overlayEraserEnabled) {
            return handleOverlayTouch(event)
        }

        if (!panZoomEnabled) {
            // In normal View/Pen/Page/etc. modes the page canvas is stationary.
            // Finger panning and pinch zoom are enabled only when the Zoom rail
            // tool is active, which avoids accidental page movement while reading
            // or writing with the stylus.
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                performClick()
            }
            activeGesture = GestureMode.NONE
            parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeGesture = GestureMode.PAN
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    activeGesture = GestureMode.PINCH
                    lastPinchDistance = pointerDistance(event).coerceAtLeast(1f)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeGesture == GestureMode.PINCH && event.pointerCount >= 2) {
                    val distance = pointerDistance(event).coerceAtLeast(1f)
                    val scaleDelta = (distance / lastPinchDistance).coerceIn(0.75f, 1.35f)
                    zoomMultiplier = (zoomMultiplier * scaleDelta).coerceIn(1f, 5f)
                    lastPinchDistance = distance
                    invalidate()
                    return true
                }

                if (activeGesture == GestureMode.PAN && event.pointerCount == 1) {
                    panX += event.x - lastTouchX
                    panY += event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                activeGesture = GestureMode.PAN
                lastTouchX = event.getX(0)
                lastTouchY = event.getY(0)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeGesture = GestureMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val report = pageReport
        if (report == null) {
            canvas.drawText("No vector preview page selected", 24f, 48f, textPaint)
            return
        }

        val viewport = calculateViewport(report)

        val drawVisualLayer = renderMode == SupernoteRenderMode.VISUAL_LAYER && renderedVisualPage?.usable == true && visualBitmap != null
        val drawPdfReferenceLayer = renderMode == SupernoteRenderMode.VISUAL_LAYER && !drawVisualLayer && renderedPdfReferencePage?.usable == true
        val drawVectorDebug = renderMode.usesVectorFallback
        drawPageFrameAndRuledBackground(
            canvas = canvas,
            left = viewport.left,
            top = viewport.top,
            drawWidth = viewport.drawWidth,
            drawHeight = viewport.drawHeight,
            scale = viewport.scale,
            includeRuledBackground = !drawVisualLayer && !drawPdfReferenceLayer
        )

        when {
            drawVisualLayer -> {
                drawVisualLayerBitmap(canvas, viewport)
                lastRenderDiagnostics = ActiveRenderDiagnostics(
                    visualLayerDecodeStatus = renderedVisualPage?.renderStatus ?: "unavailable",
                    visualLayerPixelCoverage = renderedVisualPage?.pixelCoverage ?: 0f,
                    vectorFallbackRenderedCount = 0,
                    vectorFallbackFilteredCount = 0
                )
            }
            drawPdfReferenceLayer -> {
                drawPdfReferenceLayerBitmap(canvas, viewport)
                lastRenderDiagnostics = ActiveRenderDiagnostics(
                    visualLayerDecodeStatus = renderedVisualPage?.renderStatus ?: "unavailable",
                    visualLayerPixelCoverage = renderedVisualPage?.pixelCoverage ?: 0f,
                    pdfReferenceDecodeStatus = renderedPdfReferencePage?.status ?: "unavailable",
                    pdfReferenceRendered = true,
                    vectorFallbackRenderedCount = 0,
                    vectorFallbackFilteredCount = 0
                )
            }
            drawVectorDebug -> drawVectorFallback(canvas = canvas, report = report, viewport = viewport)
            else -> drawUnavailableVisualLayerMessage(canvas, viewport)
        }

        if (overlayVisible) {
            drawOverlayStrokes(
                canvas = canvas,
                viewport = viewport,
                strokes = overlayStrokes
            )

            activeOverlayPoints?.let { points ->
                configureOverlayPaint(overlayPreviewPaint, currentOverlayStyle)
                drawOverlayPath(
                    canvas = canvas,
                    viewport = viewport,
                    points = points,
                    paint = overlayPreviewPaint
                )
            }
        }

        if (overlayEditingEnabled || overlayEraserEnabled) {
            canvas.drawText(
                if (overlayEraserEnabled) "Overlay erase: stylus removes sidecar strokes" else "Overlay edit: stylus writes, finger ignored",
                viewport.left + 18f,
                viewport.top + 38f,
                textPaint
            )
        }
    }

    fun activeRenderDiagnosticsSummary(): String {
        val diagnostics = lastRenderDiagnostics
        return "decoded=${diagnostics.totalDecodedRecords}; rendered=${diagnostics.renderedNativeRecords}; filtered=${diagnostics.filteredDiagonalSentinelRecords}; visualStatus=${diagnostics.visualLayerDecodeStatus}; visualCoverage=${diagnostics.visualLayerPixelCoverage}; pdfReferenceStatus=${diagnostics.pdfReferenceDecodeStatus}; pdfReferenceRendered=${diagnostics.pdfReferenceRendered}; vectorFallbackRendered=${diagnostics.vectorFallbackRenderedCount}; vectorFallbackFiltered=${diagnostics.vectorFallbackFilteredCount}; indexes=${diagnostics.filteredRecordIndexes.joinToString()}"
    }

    private fun drawVisualLayerBitmap(canvas: Canvas, viewport: PreviewViewport) {
        val bitmap = visualBitmap ?: return
        val dest = RectF(viewport.left, viewport.top, viewport.left + viewport.drawWidth, viewport.top + viewport.drawHeight)
        canvas.drawBitmap(bitmap, null, dest, visualBitmapPaint)
        canvas.drawRect(viewport.left, viewport.top, viewport.left + viewport.drawWidth, viewport.top + viewport.drawHeight, borderPaint)
    }

    private fun drawPdfReferenceLayerBitmap(canvas: Canvas, viewport: PreviewViewport) {
        val bitmap = renderedPdfReferencePage?.bitmap ?: return
        val dest = RectF(viewport.left, viewport.top, viewport.left + viewport.drawWidth, viewport.top + viewport.drawHeight)
        canvas.drawBitmap(bitmap, null, dest, visualBitmapPaint)
        canvas.drawRect(viewport.left, viewport.top, viewport.left + viewport.drawWidth, viewport.top + viewport.drawHeight, borderPaint)
    }

    private fun drawUnavailableVisualLayerMessage(canvas: Canvas, viewport: PreviewViewport) {
        lastRenderDiagnostics = ActiveRenderDiagnostics(
            visualLayerDecodeStatus = renderedVisualPage?.renderStatus ?: "unavailable",
            visualLayerPixelCoverage = renderedVisualPage?.pixelCoverage ?: 0f,
            pdfReferenceDecodeStatus = renderedPdfReferencePage?.status ?: "unavailable",
            pdfReferenceRendered = false,
            vectorFallbackRenderedCount = 0,
            vectorFallbackFilteredCount = 0
        )
        canvas.drawText(
            "Visual layer unavailable. Choose View > Vector debug to inspect TOTALPATH.",
            viewport.left + 18f,
            viewport.top + 54f,
            textPaint
        )
    }

    private fun drawVectorFallback(
        canvas: Canvas,
        report: SupernoteStrokeGeometryPageReport,
        viewport: PreviewViewport
    ) {
        var decodedNativeRecords = 0
        var renderedNativeRecords = 0
        val filteredIndexes = mutableListOf<Int>()
        val filteredBounds = mutableListOf<String>()
        val effectiveTransform = if (renderMode == SupernoteRenderMode.RAW_FIT_DEBUG) {
            SupernotePreviewTransformMode.RAW_FIT
        } else {
            transformMode
        }

        report.records.forEach { record ->
            decodedNativeRecords += 1
            val displayPoints = if (effectiveTransform == SupernotePreviewTransformMode.RAW_FIT && record.rawFitPoints.size >= 2) {
                record.rawFitPoints
            } else {
                record.points
            }
            if (displayPoints.size < 2) return@forEach

            val mappedPoints = displayPoints.map { point ->
                val mapped = effectiveTransform.transform(point.x, point.y, report.pageWidth, report.pageHeight)
                SupernoteGeometryPoint(mapped.first, mapped.second)
            }

            if (shouldSuppressNativeRecordInActiveRenderer(recordIndex = record.recordIndex, subtype = record.subtype, source = record.source, points = mappedPoints, report = report)) {
                filteredIndexes.add(record.recordIndex)
                filteredBounds.add(boundsLabel(mappedPoints))
                return@forEach
            }

            val path = Path()
            val firstMapped = mappedPoints.first()
            path.moveTo(viewport.left + firstMapped.x * viewport.scale, viewport.top + firstMapped.y * viewport.scale)
            mappedPoints.drop(1).forEach { point ->
                path.lineTo(viewport.left + point.x * viewport.scale, viewport.top + point.y * viewport.scale)
            }
            val paint = if (record.subtype == "possible_eraser_or_metadata" || record.subtype == "unknown") {
                lightStrokePaint
            } else {
                strokePaint
            }
            renderedNativeRecords += 1
            canvas.drawPath(path, paint)
        }

        lastRenderDiagnostics = ActiveRenderDiagnostics(
            totalDecodedRecords = decodedNativeRecords,
            renderedNativeRecords = renderedNativeRecords,
            filteredDiagonalSentinelRecords = filteredIndexes.size,
            filteredRecordIndexes = filteredIndexes.toList(),
            filteredRecordBounds = filteredBounds.toList(),
            visualLayerDecodeStatus = renderedVisualPage?.renderStatus ?: "unavailable",
            visualLayerPixelCoverage = renderedVisualPage?.pixelCoverage ?: 0f,
            pdfReferenceDecodeStatus = renderedPdfReferencePage?.status ?: "unavailable",
            pdfReferenceRendered = false,
            vectorFallbackRenderedCount = renderedNativeRecords,
            vectorFallbackFilteredCount = filteredIndexes.size
        )
    }

    private fun shouldSuppressNativeRecordInActiveRenderer(
        recordIndex: Int,
        subtype: String,
        source: String,
        points: List<SupernoteGeometryPoint>,
        report: SupernoteStrokeGeometryPageReport
    ): Boolean {
        // Raw-fit mode is intentionally diagnostic. It should show filtered
        // records so we can inspect calibration, malformed payloads, and sentinels.
        if (renderMode == SupernoteRenderMode.RAW_FIT_DEBUG) return false
        if (source == "candidatePointRun-fallback-preview") return true
        if (subtype == "unsupported_or_misdecoded_record") return true
        if (points.size < 2) return false

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        val pageWidth = report.pageWidth.coerceAtLeast(1f)
        val pageHeight = report.pageHeight.coerceAtLeast(1f)

        val pathLength = pathLength(points)
        val first = points.first()
        val last = points.last()
        val directDistance = kotlin.math.hypot(last.x - first.x, last.y - first.y)
        val straightness = if (pathLength <= 0.0001f) 0f else directDistance / pathLength
        val pageDiagonal = kotlin.math.hypot(pageWidth, pageHeight)

        val deltaX = kotlin.math.abs(last.x - first.x)
        val deltaY = kotlin.math.abs(last.y - first.y)
        val veryLargeDiagonal = spanX > pageWidth * 0.62f && spanY > pageHeight * 0.62f && directDistance > pageDiagonal * 0.55f
        val nearStraight = straightness > 0.86f
        val diagonalOutlier =
            directDistance > pageDiagonal * 0.38f &&
                straightness > 0.82f &&
                deltaX > pageWidth * 0.28f &&
                deltaY > pageHeight * 0.22f
        val endpointNearOppositeEdges =
            ((first.x <= pageWidth * 0.08f && last.x >= pageWidth * 0.92f) || (last.x <= pageWidth * 0.08f && first.x >= pageWidth * 0.92f)) &&
                ((first.y <= pageHeight * 0.12f && last.y >= pageHeight * 0.88f) || (last.y <= pageHeight * 0.12f && first.y >= pageHeight * 0.88f))

        if (veryLargeDiagonal && nearStraight) return true
        if (endpointNearOppositeEdges && nearStraight) return true
        if (diagonalOutlier) return true

        // Fallback point-run / unknown records are often eraser regions,
        // marker bitmap metadata, or container sentinels rather than drawable
        // ink. In normal A5X rendering, suppress loop-like blobs that overdraw
        // handwriting. Raw-fit debug mode still shows them for decoder work.
        val weaklyDecodedRecord =
            source == "candidatePointRun-fallback-preview" ||
                subtype == "possible_eraser_or_metadata" ||
                subtype == "unknown"
        if (source == "candidatePointRun-fallback-preview" && veryLargeDiagonal) return true
        if (weaklyDecodedRecord && isLoopLikeMetadataBlob(points, pageWidth, pageHeight)) return true

        return false
    }

    private fun isLoopLikeMetadataBlob(
        points: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (points.size < 6) return false
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX <= 0f || spanY <= 0f) return false

        val pathLength = pathLength(points)
        val first = points.first()
        val last = points.last()
        val directDistance = kotlin.math.hypot(last.x - first.x, last.y - first.y)
        val maxSpan = kotlin.math.max(spanX, spanY)
        val minSpan = kotlin.math.min(spanX, spanY)
        val closedOrLooping = directDistance < maxSpan * 0.45f && pathLength > maxSpan * 2.0f
        val scribbleLike = pathLength > maxSpan * 3.0f && minSpan > 18f
        val largeEnoughToBeMetadata =
            spanX > pageWidth * 0.055f &&
                spanY > pageHeight * 0.018f &&
                spanY > 16f

        // Keep long, mostly-horizontal records; these may be normal pen/marker
        // strokes once exact marker subtype decoding is added. Suppress compact
        // loops/blobs that are currently rendered as circles over the content.
        val horizontalBandCandidate = spanX > spanY * 6.0f && spanY < pageHeight * 0.035f
        if (horizontalBandCandidate) return false

        return largeEnoughToBeMetadata && (closedOrLooping || scribbleLike)
    }

    private fun pathLength(points: List<SupernoteGeometryPoint>): Float {
        var total = 0f
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            total += kotlin.math.hypot(b.x - a.x, b.y - a.y)
        }
        return total
    }

    private fun boundsLabel(points: List<SupernoteGeometryPoint>): String {
        if (points.isEmpty()) return "empty"
        val minX = points.minOf { it.x }.toInt()
        val maxX = points.maxOf { it.x }.toInt()
        val minY = points.minOf { it.y }.toInt()
        val maxY = points.maxOf { it.y }.toInt()
        return "x=$minX..$maxX y=$minY..$maxY"
    }

    private fun handleOverlayTouch(event: MotionEvent): Boolean {
        val report = pageReport ?: return false
        val pointerIndex = stylusPointerIndex(event)

        // In overlay mode, finger and mouse input are ignored so accidental
        // finger touches never create annotation strokes or pan the page.
        if (pointerIndex < 0) {
            return false
        }

        if (overlayEraserEnabled) {
            return handleOverlayEraserTouch(event, report, pointerIndex)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                activeOverlayToolType = toolTypeLabel(event.getToolType(pointerIndex))
                val pagePoint = mapTouchToPreviewPagePoint(event.getX(pointerIndex), event.getY(pointerIndex), report)
                    ?: return true
                activeOverlayPoints = mutableListOf(pagePoint)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val points = activeOverlayPoints ?: return true
                for (i in 0 until event.pointerCount) {
                    if (!isStylusTool(event.getToolType(i))) continue
                    mapTouchToPreviewPagePoint(event.getX(i), event.getY(i), report)?.let { pagePoint ->
                        val previous = points.lastOrNull()
                        if (previous == null || hypot(previous.x - pagePoint.x, previous.y - pagePoint.y) > 1.5f) {
                            points.add(pagePoint)
                        }
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val points = activeOverlayPoints.orEmpty()
                if (points.size >= 2) {
                    val stroke = LocalAnnotationStroke(
                        id = UUID.randomUUID().toString(),
                        createdAtMillis = System.currentTimeMillis(),
                        transformModeId = transformMode.id,
                        toolType = activeOverlayToolType,
                        width = currentOverlayStyle.widthPx,
                        points = points,
                        style = currentOverlayStyle
                    )
                    overlayStrokes = overlayStrokes + stroke
                    onOverlayChanged(overlayStrokes)
                }
                activeOverlayPoints = null
                activeGesture = GestureMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                performClick()
                return true
            }
        }

        return true
    }

    private fun handleOverlayEraserTouch(
        event: MotionEvent,
        report: SupernoteStrokeGeometryPageReport,
        pointerIndex: Int
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                eraseNearestStroke(event.getX(pointerIndex), event.getY(pointerIndex), report)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (!isStylusTool(event.getToolType(i))) continue
                    eraseNearestStroke(event.getX(i), event.getY(i), report)
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeGesture = GestureMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                performClick()
                return true
            }
        }
        return true
    }

    private fun eraseNearestStroke(
        touchX: Float,
        touchY: Float,
        report: SupernoteStrokeGeometryPageReport
    ) {
        val pagePoint = mapTouchToPreviewPagePoint(touchX, touchY, report) ?: return
        val candidateIndex = nearestOverlayStrokeIndex(pagePoint) ?: return
        val next = overlayStrokes.toMutableList().also { it.removeAt(candidateIndex) }
        if (next != overlayStrokes) {
            overlayStrokes = next
            activeOverlayPoints = null
            onOverlayChanged(overlayStrokes)
            invalidate()
        }
    }

    private fun nearestOverlayStrokeIndex(point: LocalAnnotationPoint): Int? {
        if (overlayStrokes.isEmpty()) return null
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        overlayStrokes.forEachIndexed { index, stroke ->
            val distance = distanceToStroke(point, stroke)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        val threshold = maxOf(30f, overlayStrokes.getOrNull(bestIndex)?.style?.widthPx?.times(5f) ?: 30f)
        return bestIndex.takeIf { it >= 0 && bestDistance <= threshold }
    }

    private fun distanceToStroke(point: LocalAnnotationPoint, stroke: LocalAnnotationStroke): Float {
        val points = stroke.points
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) return hypot(point.x - points.first().x, point.y - points.first().y)
        var best = Float.MAX_VALUE
        for (i in 0 until points.lastIndex) {
            best = minOf(best, distanceToSegment(point, points[i], points[i + 1]))
        }
        return best
    }

    private fun distanceToSegment(
        p: LocalAnnotationPoint,
        a: LocalAnnotationPoint,
        b: LocalAnnotationPoint
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0001f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val projectionX = a.x + t * dx
        val projectionY = a.y + t * dy
        return hypot(p.x - projectionX, p.y - projectionY)
    }

    private fun mapTouchToPreviewPagePoint(
        touchX: Float,
        touchY: Float,
        report: SupernoteStrokeGeometryPageReport
    ): LocalAnnotationPoint? {
        val viewport = calculateViewport(report)
        val pageX = ((touchX - viewport.left) / viewport.scale).coerceIn(0f, report.pageWidth)
        val pageY = ((touchY - viewport.top) / viewport.scale).coerceIn(0f, report.pageHeight)
        if (touchX < viewport.left - 12f || touchX > viewport.left + viewport.drawWidth + 12f) return null
        if (touchY < viewport.top - 12f || touchY > viewport.top + viewport.drawHeight + 12f) return null
        return LocalAnnotationPoint(x = pageX, y = pageY)
    }

    private fun drawOverlayStrokes(
        canvas: Canvas,
        viewport: PreviewViewport,
        strokes: List<LocalAnnotationStroke>
    ) {
        strokes.forEach { stroke ->
            configureOverlayPaint(overlayPaint, stroke.style)
            drawOverlayPath(
                canvas = canvas,
                viewport = viewport,
                points = stroke.points,
                paint = overlayPaint
            )
        }
    }

    private fun drawOverlayPath(
        canvas: Canvas,
        viewport: PreviewViewport,
        points: List<LocalAnnotationPoint>,
        paint: Paint
    ) {
        if (points.size < 2) return
        val path = Path()
        val first = points.first()
        path.moveTo(viewport.left + first.x * viewport.scale, viewport.top + first.y * viewport.scale)
        points.drop(1).forEach { point ->
            path.lineTo(viewport.left + point.x * viewport.scale, viewport.top + point.y * viewport.scale)
        }
        canvas.drawPath(path, paint)
    }


    private fun configureOverlayPaint(
        paint: Paint,
        style: LocalAnnotationStrokeStyle
    ) {
        paint.strokeWidth = style.widthPx
        paint.color = when (style.color) {
            LocalAnnotationColor.WHITE -> Color.WHITE
            LocalAnnotationColor.LIGHT_GRAY -> Color.rgb(190, 190, 190)
            LocalAnnotationColor.DARK_GRAY -> Color.rgb(95, 95, 95)
            LocalAnnotationColor.BLACK -> Color.BLACK
        }
    }

    private fun calculateViewport(report: SupernoteStrokeGeometryPageReport): PreviewViewport {
        val viewWidth = width.toFloat().coerceAtLeast(1f)
        val viewHeight = height.toFloat().coerceAtLeast(1f)
        val baseScale = minOf(viewWidth / report.pageWidth, viewHeight / report.pageHeight)
        val scale = baseScale * zoomMultiplier
        val drawWidth = report.pageWidth * scale
        val drawHeight = report.pageHeight * scale
        val left = (viewWidth - drawWidth) / 2f + panX
        val top = (viewHeight - drawHeight) / 2f + panY
        return PreviewViewport(
            left = left,
            top = top,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            scale = scale
        )
    }

    private fun drawPageFrameAndRuledBackground(
        canvas: Canvas,
        left: Float,
        top: Float,
        drawWidth: Float,
        drawHeight: Float,
        scale: Float,
        includeRuledBackground: Boolean
    ) {
        canvas.drawRect(left, top, left + drawWidth, top + drawHeight, borderPaint)
        if (!includeRuledBackground) return

        // Placeholder ruled background for A5X 8mm ruled notes. Visual-layer
        // mode does not draw this behind the decoded bitmap so the visual layer
        // remains the source of truth when available.
        val lineSpacingPx = 70f * scale
        if (lineSpacingPx < 8f) return

        var y = top + lineSpacingPx * 2f
        while (y < top + drawHeight - lineSpacingPx) {
            canvas.drawLine(left, y, left + drawWidth, y, ruledLinePaint)
            y += lineSpacingPx
        }
    }

    private fun rebuildVisualBitmapIfNeeded(rendered: SupernoteRenderedVisualPage?) {
        if (rendered?.usable != true) {
            visualBitmap = null
            visualBitmapPageNumber = null
            visualBitmapPayloadOffset = null
            return
        }
        if (visualBitmap != null && visualBitmapPageNumber == rendered.pageNumber && visualBitmapPayloadOffset == rendered.payloadOffset) {
            return
        }
        val argbPixels = IntArray(rendered.pixels.size) { index ->
            val gray = rendered.pixels[index].coerceIn(0, 255)
            Color.rgb(gray, gray, gray)
        }
        visualBitmap = Bitmap.createBitmap(argbPixels, rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
        visualBitmapPageNumber = rendered.pageNumber
        visualBitmapPayloadOffset = rendered.payloadOffset
    }

    private fun resetViewportSilently() {
        panX = 0f
        panY = 0f
        zoomMultiplier = 1f
        activeOverlayPoints = null
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        return hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    }

    private fun stylusPointerIndex(event: MotionEvent): Int {
        for (index in 0 until event.pointerCount) {
            if (isStylusTool(event.getToolType(index))) return index
        }
        return -1
    }

    private fun isStylusTool(toolType: Int): Boolean {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun toolTypeLabel(toolType: Int): String {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_ERASER -> "eraser"
            MotionEvent.TOOL_TYPE_STYLUS -> "stylus"
            else -> "unknown"
        }
    }

    private val SupernoteRenderedVisualPage.pixelCoverage: Float
        get() {
            val totalPixels = (width * height).coerceAtLeast(1)
            return nonWhitePixelCount.toFloat() / totalPixels.toFloat()
        }

    private data class ActiveRenderDiagnostics(
        val totalDecodedRecords: Int = 0,
        val renderedNativeRecords: Int = 0,
        val filteredDiagonalSentinelRecords: Int = 0,
        val filteredRecordIndexes: List<Int> = emptyList(),
        val filteredRecordBounds: List<String> = emptyList(),
        val visualLayerDecodeStatus: String = "unavailable",
        val visualLayerPixelCoverage: Float = 0f,
        val pdfReferenceDecodeStatus: String = "unavailable",
        val pdfReferenceRendered: Boolean = false,
        val vectorFallbackRenderedCount: Int = 0,
        val vectorFallbackFilteredCount: Int = 0
    )

    private data class PreviewViewport(
        val left: Float,
        val top: Float,
        val drawWidth: Float,
        val drawHeight: Float,
        val scale: Float
    )

    private enum class GestureMode {
        NONE,
        PAN,
        PINCH
    }
}
