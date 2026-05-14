package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

class SupernoteVectorPreviewView(
    context: Context,
    private var pageReport: SupernoteStrokeGeometryPageReport?
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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun updatePageReport(pageReport: SupernoteStrokeGeometryPageReport?) {
        this.pageReport = pageReport
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

        canvas.drawRect(left, top, left + drawWidth, top + drawHeight, borderPaint)

        report.records.forEach { record ->
            if (record.points.size < 2) return@forEach
            val path = Path()
            val first = record.points.first()
            path.moveTo(left + first.x * scale, top + first.y * scale)
            record.points.drop(1).forEach { point ->
                path.lineTo(left + point.x * scale, top + point.y * scale)
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
            "Transform: raw-bounds diagnostic fit",
            24f,
            78f,
            textPaint
        )
    }
}
