package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationPoint
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport
import java.util.UUID
import kotlin.math.hypot

class SupernoteVectorPreviewView(
    context: Context,
    private var pageReport: SupernoteStrokeGeometryPageReport?,
    private var transformMode: SupernotePreviewTransformMode = SupernotePreviewTransformMode.A5X_RAW,
    private var overlayEnabled: Boolean = false,
    private var overlayStrokes: List<LocalAnnotationStroke> = emptyList(),
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
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val overlayPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
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

    private var panX = 0f
    private var panY = 0f
    private var zoomMultiplier = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f
    private var activeGesture = GestureMode.NONE
    private var activeOverlayPoints: MutableList<LocalAnnotationPoint>? = null
    private var activeOverlayToolType: String = "stylus"

    fun updatePageReport(
        pageReport: SupernoteStrokeGeometryPageReport?,
        transformMode: SupernotePreviewTransformMode = this.transformMode,
        overlayEnabled: Boolean = this.overlayEnabled,
        overlayStrokes: List<LocalAnnotationStroke> = this.overlayStrokes,
        onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit = this.onOverlayChanged
    ) {
        val changedPage = this.pageReport?.pageNumber != pageReport?.pageNumber
        val changedTransform = this.transformMode != transformMode
        this.pageReport = pageReport
        this.transformMode = transformMode
        this.overlayEnabled = overlayEnabled
        this.overlayStrokes = overlayStrokes
        this.onOverlayChanged = onOverlayChanged
        if (changedPage || changedTransform) resetViewportSilently()
        invalidate()
    }

    fun resetViewport() {
        resetViewportSilently()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (overlayEnabled) {
            return handleOverlayTouch(event)
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

        drawPageFrameAndRuledBackground(
            canvas = canvas,
            left = viewport.left,
            top = viewport.top,
            drawWidth = viewport.drawWidth,
            drawHeight = viewport.drawHeight,
            scale = viewport.scale
        )

        report.records.forEach { record ->
            if (record.points.size < 2) return@forEach
            val path = Path()
            val first = record.points.first()
            val firstMapped = transformMode.transform(first.x, first.y, report.pageWidth, report.pageHeight)
            path.moveTo(viewport.left + firstMapped.first * viewport.scale, viewport.top + firstMapped.second * viewport.scale)
            record.points.drop(1).forEach { point ->
                val mapped = transformMode.transform(point.x, point.y, report.pageWidth, report.pageHeight)
                path.lineTo(viewport.left + mapped.first * viewport.scale, viewport.top + mapped.second * viewport.scale)
            }
            val paint = if (record.subtype == "possible_eraser_or_metadata" || record.subtype == "unknown") {
                lightStrokePaint
            } else {
                strokePaint
            }
            canvas.drawPath(path, paint)
        }

        drawOverlayStrokes(
            canvas = canvas,
            viewport = viewport,
            strokes = overlayStrokes,
            paint = overlayPaint
        )

        activeOverlayPoints?.let { points ->
            drawOverlayPath(
                canvas = canvas,
                viewport = viewport,
                points = points,
                paint = overlayPreviewPaint
            )
        }

        if (overlayEnabled) {
            canvas.drawText(
                "Overlay on: stylus writes, finger ignored",
                viewport.left + 18f,
                viewport.top + 38f,
                textPaint
            )
        }
    }

    private fun handleOverlayTouch(event: MotionEvent): Boolean {
        val report = pageReport ?: return false
        val pointerIndex = stylusPointerIndex(event)

        // In overlay mode, finger and mouse input are ignored so accidental
        // finger touches never create annotation strokes or pan the page.
        if (pointerIndex < 0) {
            return false
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
                        width = 5f,
                        points = points
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
        strokes: List<LocalAnnotationStroke>,
        paint: Paint
    ) {
        strokes.forEach { stroke ->
            drawOverlayPath(
                canvas = canvas,
                viewport = viewport,
                points = stroke.points,
                paint = paint
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
        scale: Float
    ) {
        canvas.drawRect(left, top, left + drawWidth, top + drawHeight, borderPaint)

        // Placeholder ruled background for A5X 8mm ruled notes. This remains
        // only an alignment aid until RATTA_RLE BGLAYER decode is implemented.
        val lineSpacingPx = 70f * scale
        if (lineSpacingPx < 8f) return

        var y = top + lineSpacingPx * 2f
        while (y < top + drawHeight - lineSpacingPx) {
            canvas.drawLine(left, y, left + drawWidth, y, ruledLinePaint)
            y += lineSpacingPx
        }
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
