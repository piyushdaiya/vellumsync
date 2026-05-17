package io.github.piyushdaiya.vellumsync.note

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.piyushdaiya.vellumsync.util.JsonText
import java.io.File

/**
 * Read-only visual reference renderer for Supernote exports.
 *
 * Some Supernote-created .note files expose MAINLAYER/BGLAYER metadata but do
 * not yet provide a decodable bitmap payload through VellumSync's conservative
 * RATTA_RLE probe. When a same-basename PDF export is cached beside the note,
 * this renderer uses Android PdfRenderer as the visual source of truth for the
 * base page and keeps TOTALPATH vectors debug-only.
 */
data class SupernoteRenderedPdfReferencePage(
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap?,
    val status: String,
    val sourcePath: String?,
    val sourceFileName: String?,
    val pageCount: Int,
    val warning: String?
) {
    val usable: Boolean
        get() = status == "decoded" && bitmap != null
}

object SupernotePdfReferenceLayer {
    fun renderPageForNote(
        notePath: String,
        noteFileName: String,
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int
    ): SupernoteRenderedPdfReferencePage {
        val pdfFile = ImportedNoteCache.findCachedPdfReference(notePath, noteFileName)
            ?: return unavailable(pageNumber, "No same-basename PDF reference is cached for this note.")
        return renderPage(
            pdfFile = pdfFile,
            pageNumber = pageNumber,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }

    fun diagnosticsJson(renderedPage: SupernoteRenderedPdfReferencePage?): String {
        return buildString {
            append("{")
            append("\"pdfReferenceDetected\":${renderedPage?.sourcePath != null},")
            append("\"pdfReferenceDecodeStatus\":${JsonText.quote(renderedPage?.status ?: "unavailable")},")
            append("\"pdfReferenceFileName\":${JsonText.quote(renderedPage?.sourceFileName)},")
            append("\"pdfReferencePageNumber\":${renderedPage?.pageNumber ?: "null"},")
            append("\"pdfReferencePageCount\":${renderedPage?.pageCount ?: 0},")
            append("\"pdfReferenceWidth\":${renderedPage?.width ?: 0},")
            append("\"pdfReferenceHeight\":${renderedPage?.height ?: 0},")
            append("\"pdfReferenceWarning\":${JsonText.quote(renderedPage?.warning)}")
            append("}")
        }
    }

    private fun renderPage(
        pdfFile: File,
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int
    ): SupernoteRenderedPdfReferencePage {
        return runCatching {
            val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    if (renderer.pageCount <= 0) {
                        unavailable(pageNumber, "PDF reference has no pages.", pdfFile)
                    } else {
                        val pageIndex = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
                        val page = renderer.openPage(pageIndex)
                        try {
                            val width = targetWidth.takeIf { it > 0 }
                                ?: page.width.coerceAtLeast(1)
                            val height = targetHeight.takeIf { it > 0 }
                                ?: page.height.coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            SupernoteRenderedPdfReferencePage(
                                pageNumber = pageNumber,
                                width = width,
                                height = height,
                                bitmap = bitmap,
                                status = "decoded",
                                sourcePath = pdfFile.absolutePath,
                                sourceFileName = pdfFile.name,
                                pageCount = renderer.pageCount,
                                warning = null
                            )
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                descriptor.close()
            }
        }.getOrElse { throwable ->
            unavailable(pageNumber, throwable.message ?: "Unable to render PDF reference layer.", pdfFile)
        }
    }

    private fun unavailable(
        pageNumber: Int,
        warning: String,
        pdfFile: File? = null
    ): SupernoteRenderedPdfReferencePage {
        return SupernoteRenderedPdfReferencePage(
            pageNumber = pageNumber,
            width = 0,
            height = 0,
            bitmap = null,
            status = "unavailable",
            sourcePath = pdfFile?.absolutePath,
            sourceFileName = pdfFile?.name,
            pageCount = 0,
            warning = warning
        )
    }
}
