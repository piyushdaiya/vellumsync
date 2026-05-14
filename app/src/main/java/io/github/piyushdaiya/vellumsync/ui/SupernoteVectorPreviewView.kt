package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport
import kotlin.math.hypot

class SupernoteVectorPreviewView(
    context: Context,
    private var pageReport: SupernoteStrokeGeometryPageReport?,
    private var transformMode: SupernotePreviewTransformMode = SupernotePreviewTransformMode.A5X_RAW
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

    fun updatePageReport(
        pageReport: SupernoteStrokeGeometryPageReport?,
        transformMode: SupernotePreviewTransformMode = this.transformMode
    ) {
        val changedPage = this.pageReport?.pageNumber != pageReport?.pageNumber
        val changedTransform = this.transformMode != transformMode
        this.pageReport = pageReport
        this.transformMode = transformMode
        if (changedPage || changedTransform) resetViewportSilently()
        invalidate()
    }

    fun resetViewport() {
        resetViewportSilently()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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

        val viewWidth = width.toFloat().coerceAtLeast(1f)
        val viewHeight = height.toFloat().coerceAtLeast(1f)
        val baseScale = minOf(viewWidth / report.pageWidth, viewHeight / report.pageHeight)
        val scale = baseScale * zoomMultiplier
        val drawWidth = report.pageWidth * scale
        val drawHeight = report.pageHeight * scale
        val left = (viewWidth - drawWidth) / 2f + panX
        val top = (viewHeight - drawHeight) / 2f + panY

        drawPageFrameAndRuledBackground(
            canvas = canvas,
            left = left,
            top = top,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            scale = scale
        )

        report.records.forEach { record ->
            if (record.points.size < 2) return@forEach
            val path = Path()
            val first = record.points.first()
            val firstMapped = transformMode.transform(first.x, first.y, report.pageWidth, report.pageHeight)
            path.moveTo(left + firstMapped.first * scale, top + firstMapped.second * scale)
            record.points.drop(1).forEach { point ->
                val mapped = transformMode.transform(point.x, point.y, report.pageWidth, report.pageHeight)
                path.lineTo(left + mapped.first * scale, top + mapped.second * scale)
            }
            val paint = if (record.subtype == "possible_eraser_or_metadata" || record.subtype == "unknown") {
                lightStrokePaint
            } else {
                strokePaint
            }
            canvas.drawPath(path, paint)
        }
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
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        return hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    }

    private enum class GestureMode {
        NONE,
        PAN,
        PINCH
    }
}
