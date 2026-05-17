package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.StrictMode
import io.github.piyushdaiya.vellumsync.util.JsonText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Safe export/share utilities for VellumSync.
 *
 * These functions export preview/package artifacts from decoded Supernote geometry and
 * local overlay sidecars only. They never modify the original Supernote .note file.
 */
object VellumSyncPackageExporter {
    fun renderCombinedPagePreviewPng(
        page: SupernoteStrokeGeometryPageReport,
        overlayStrokes: List<LocalAnnotationStroke>,
        maxLongEdgePx: Int = 1800
    ): ByteArray {
        val bitmap = renderCombinedBitmap(page = page, overlayStrokes = overlayStrokes, maxLongEdgePx = maxLongEdgePx)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    fun renderCombinedNotePreviewPdf(
        context: Context,
        noteSha256: String,
        pages: List<SupernoteStrokeGeometryPageReport>
    ): ByteArray {
        val pdf = PdfDocument()
        return try {
            pages.forEach { page ->
                val pageWidth = page.pageWidth.roundToInt().coerceAtLeast(1)
                val pageHeight = page.pageHeight.roundToInt().coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, page.pageNumber).create()
                val pdfPage = pdf.startPage(info)
                val overlay = LocalAnnotationOverlayStore.loadPage(context, noteSha256, page.pageNumber)
                drawCombinedPreview(
                    canvas = pdfPage.canvas,
                    page = page,
                    overlayStrokes = overlay,
                    scale = 1f,
                    left = 0f,
                    top = 0f
                )
                pdf.finishPage(pdfPage)
            }
            ByteArrayOutputStream().use { output ->
                pdf.writeTo(output)
                output.toByteArray()
            }
        } finally {
            pdf.close()
        }
    }

    fun buildExportPackageZip(
        context: Context,
        noteSha256: String,
        noteFileName: String,
        report: SupernoteInspectionReport,
        currentPage: SupernoteStrokeGeometryPageReport?
    ): ByteArray {
        val allPages = report.strokeGeometryReport.pageReports
        val overlayJson = LocalAnnotationOverlayStore.overlayExportJson(
            context = context,
            noteSha256 = noteSha256,
            totalPages = report.strokeGeometryReport.totalPages
        )
        val diagnosticsJson = LocalAnnotationOverlayStore.appendOverlayDiagnostics(
            baseReportJson = report.toJson(),
            context = context,
            noteSha256 = noteSha256,
            totalPages = report.strokeGeometryReport.totalPages
        )
        val previewPdf = renderCombinedNotePreviewPdf(
            context = context,
            noteSha256 = noteSha256,
            pages = allPages
        )
        val pageForPng = currentPage ?: allPages.firstOrNull()
        val currentPreviewPng = pageForPng?.let { page ->
            renderCombinedPagePreviewPng(
                page = page,
                overlayStrokes = LocalAnnotationOverlayStore.loadPage(context, noteSha256, page.pageNumber)
            )
        }
        val manifestJson = buildManifestJson(
            noteSha256 = noteSha256,
            noteFileName = noteFileName,
            pageCount = report.strokeGeometryReport.totalPages,
            currentPageNumber = pageForPng?.pageNumber,
            overlaySummary = LocalAnnotationOverlayStore.noteSummary(context, noteSha256)
        )

        return ByteArrayOutputStream().use { byteOutput ->
            ZipOutputStream(byteOutput).use { zip ->
                zip.textEntry("manifest.json", manifestJson)
                zip.textEntry("overlay/overlay.json", overlayJson)
                zip.textEntry("diagnostics/note-diagnostics.json", diagnosticsJson)
                zip.bytesEntry("preview/combined-preview.pdf", previewPdf)
                if (currentPreviewPng != null && pageForPng != null) {
                    zip.bytesEntry("preview/page-${pageForPng.pageNumber}-combined.png", currentPreviewPng)
                }
            }
            byteOutput.toByteArray()
        }
    }

    fun sharePackage(
        context: Context,
        fileName: String,
        packageBytes: ByteArray
    ): String? {
        return runCatching {
            val shareDir = File(context.cacheDir, "share")
            shareDir.mkdirs()
            val outFile = File(shareDir, fileName)
            FileOutputStream(outFile).use { it.write(packageBytes) }

            val uri = fileUriForShare(context = context, file = outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VellumSync package")
                putExtra(Intent.EXTRA_TEXT, "VellumSync package export. Original Supernote .note was not modified.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share VellumSync package"))
            null
        }.getOrElse { throwable ->
            throwable.message ?: "Unable to share package."
        }
    }

    private fun renderCombinedBitmap(
        page: SupernoteStrokeGeometryPageReport,
        overlayStrokes: List<LocalAnnotationStroke>,
        maxLongEdgePx: Int
    ): Bitmap {
        val safePageWidth = page.pageWidth.coerceAtLeast(1f)
        val safePageHeight = page.pageHeight.coerceAtLeast(1f)
        val scale = (maxLongEdgePx.toFloat() / maxOf(safePageWidth, safePageHeight)).coerceAtMost(1.5f)
        val bitmapWidth = (safePageWidth * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (safePageHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawCombinedPreview(canvas = canvas, page = page, overlayStrokes = overlayStrokes, scale = scale, left = 0f, top = 0f)
        return bitmap
    }

    private fun drawCombinedPreview(
        canvas: Canvas,
        page: SupernoteStrokeGeometryPageReport,
        overlayStrokes: List<LocalAnnotationStroke>,
        scale: Float,
        left: Float,
        top: Float
    ) {
        val pageWidth = page.pageWidth.coerceAtLeast(1f)
        val pageHeight = page.pageHeight.coerceAtLeast(1f)
        canvas.drawColor(Color.WHITE)
        drawPageFrame(canvas, pageWidth, pageHeight, scale, left, top)
        drawSupernoteBase(canvas, page, scale, left, top)
        drawOverlay(canvas, overlayStrokes, scale, left, top)
    }

    private fun drawPageFrame(canvas: Canvas, pageWidth: Float, pageHeight: Float, scale: Float, left: Float, top: Float) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(30, 30, 30)
            strokeWidth = 2f
        }
        val ruled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }
        canvas.drawRect(left, top, left + pageWidth * scale, top + pageHeight * scale, border)
        var y = top + 140f * scale
        while (y < top + pageHeight * scale - 70f * scale) {
            canvas.drawLine(left, y, left + pageWidth * scale, y, ruled)
            y += 70f * scale
        }
    }

    private fun drawSupernoteBase(canvas: Canvas, page: SupernoteStrokeGeometryPageReport, scale: Float, left: Float, top: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 3f * scale.coerceAtLeast(0.75f)
            color = Color.BLACK
        }
        page.records.forEach { record ->
            if (record.points.size < 2) return@forEach
            val path = Path()
            val first = record.points.first()
            path.moveTo(left + first.x * scale, top + first.y * scale)
            record.points.drop(1).forEach { point ->
                path.lineTo(left + point.x * scale, top + point.y * scale)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas, overlayStrokes: List<LocalAnnotationStroke>, scale: Float, left: Float, top: Float) {
        overlayStrokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
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
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(left + first.x * scale, top + first.y * scale)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(left + point.x * scale, top + point.y * scale)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun buildManifestJson(
        noteSha256: String,
        noteFileName: String,
        pageCount: Int,
        currentPageNumber: Int?,
        overlaySummary: LocalAnnotationNoteSummary
    ): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync export package")},")
            append("\"version\":1,")
            append("\"writeBackStatus\":${JsonText.quote("sidecar-only; original Supernote .note is not modified")},")
            append("\"noteFileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"pageCount\":$pageCount,")
            append("\"currentPageNumber\":${currentPageNumber ?: "null"},")
            append("\"overlaySummary\":${overlaySummary.toJson()},")
            append("\"packageContents\":[")
            append(listOf(
                "manifest.json",
                "overlay/overlay.json",
                "diagnostics/note-diagnostics.json",
                "preview/combined-preview.pdf",
                "preview/page-<current>-combined.png"
            ).joinToString(separator = ",") { JsonText.quote(it) })
            append("]")
            append("}")
        }
    }

    private fun ZipOutputStream.textEntry(name: String, text: String) {
        bytesEntry(name, text.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.bytesEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun fileUriForShare(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return runCatching {
            val fileProvider = Class.forName("androidx.core.content.FileProvider")
            val method = fileProvider.getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
            method.invoke(null, context, authority, file) as Uri
        }.getOrElse {
            runCatching {
                StrictMode::class.java.getMethod("disableDeathOnFileUriExposure").invoke(null)
            }
            Uri.fromFile(file)
        }
    }
}

private fun Float.formatForPackage(): String = String.format(Locale.US, "%.3f", this)
