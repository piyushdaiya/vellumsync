package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

class SupernoteVectorPreviewView(
    context: Context,
    private var pageReport: SupernoteStrokeGeometryPageReport?,
    private var transformMode: SupernotePreviewTransformMode = SupernotePreviewTransformMode.A5X_PORTRAIT
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

    fun updatePageReport(
        pageReport: SupernoteStrokeGeometryPageReport?,
        transformMode: SupernotePreviewTransformMode = this.transformMode
    ) {
        this.pageReport = pageReport
        this.transformMode = transformMode
        invalidate()
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
        val scale = minOf(viewWidth / report.pageWidth, viewHeight / report.pageHeight)
        val drawWidth = report.pageWidth * scale
        val drawHeight = report.pageHeight * scale
        val left = (viewWidth - drawWidth) / 2f
        val top = (viewHeight - drawHeight) / 2f

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

        canvas.drawText(
            "Page ${report.pageNumber}: rendered ${report.renderedRecords}/${report.decodedRecords} records",
            24f,
            40f,
            textPaint
        )
        canvas.drawText(
            "Transform: ${transformMode.label}",
            24f,
            78f,
            textPaint
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

        // Placeholder ruled background for A5X 8mm ruled notes. This is only a
        // visual alignment aid until RATTA_RLE BGLAYER decoding is implemented.
        val lineSpacingPx = 70f * scale
        if (lineSpacingPx < 8f) return

        var y = top + lineSpacingPx * 2f
        while (y < top + drawHeight - lineSpacingPx) {
            canvas.drawLine(left, y, left + drawWidth, y, ruledLinePaint)
            y += lineSpacingPx
        }
    }
}
