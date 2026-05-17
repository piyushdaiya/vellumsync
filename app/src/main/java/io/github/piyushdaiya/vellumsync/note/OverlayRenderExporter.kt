package io.github.piyushdaiya.vellumsync.note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object OverlayRenderExporter {
    fun renderOverlayPreviewPng(
        pageWidth: Float,
        pageHeight: Float,
        strokes: List<LocalAnnotationStroke>,
        maxLongEdgePx: Int = 1800
    ): ByteArray {
        val bitmap = renderBitmap(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            strokes = strokes,
            maxLongEdgePx = maxLongEdgePx
        )
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    fun renderOverlayPreviewPdf(
        pageWidth: Float,
        pageHeight: Float,
        strokes: List<LocalAnnotationStroke>
    ): ByteArray {
        val pdf = PdfDocument()
        return try {
            val width = pageWidth.roundToInt().coerceAtLeast(1)
            val height = pageHeight.roundToInt().coerceAtLeast(1)
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdf.startPage(pageInfo)
            drawPreview(
                canvas = page.canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                strokes = strokes,
                scale = 1f,
                left = 0f,
                top = 0f
            )
            pdf.finishPage(page)
            ByteArrayOutputStream().use { output ->
                pdf.writeTo(output)
                output.toByteArray()
            }
        } finally {
            pdf.close()
        }
    }

    private fun renderBitmap(
        pageWidth: Float,
        pageHeight: Float,
        strokes: List<LocalAnnotationStroke>,
        maxLongEdgePx: Int
    ): Bitmap {
        val safePageWidth = pageWidth.coerceAtLeast(1f)
        val safePageHeight = pageHeight.coerceAtLeast(1f)
        val scale = (maxLongEdgePx.toFloat() / maxOf(safePageWidth, safePageHeight)).coerceAtMost(1.5f)
        val bitmapWidth = (safePageWidth * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (safePageHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPreview(
            canvas = canvas,
            pageWidth = safePageWidth,
            pageHeight = safePageHeight,
            strokes = strokes,
            scale = scale,
            left = 0f,
            top = 0f
        )
        return bitmap
    }

    private fun drawPreview(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        strokes: List<LocalAnnotationStroke>,
        scale: Float,
        left: Float,
        top: Float
    ) {
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 2f
        }
        val ruledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f * scale.coerceAtLeast(0.75f)
        }

        canvas.drawRect(left, top, left + pageWidth * scale, top + pageHeight * scale, borderPaint)
        var y = top + 140f * scale
        while (y < top + pageHeight * scale - 70f * scale) {
            canvas.drawLine(left, y, left + pageWidth * scale, y, ruledPaint)
            y += 70f * scale
        }

        if (strokes.isEmpty()) {
            canvas.drawText("No local overlay strokes on this page", left + 24f, top + 56f, textPaint)
            return
        }

        strokes.forEach { stroke ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.style.width.width * scale
                color = when (stroke.style.color) {
                    LocalAnnotationColor.WHITE -> Color.WHITE
                    LocalAnnotationColor.LIGHT_GRAY -> Color.rgb(190, 190, 190)
                    LocalAnnotationColor.DARK_GRAY -> Color.rgb(95, 95, 95)
                    LocalAnnotationColor.BLACK -> Color.BLACK
                }
            }
            if (stroke.points.size >= 2) {
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(left + first.x * scale, top + first.y * scale)
                stroke.points.drop(1).forEach { point ->
                    path.lineTo(left + point.x * scale, top + point.y * scale)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
