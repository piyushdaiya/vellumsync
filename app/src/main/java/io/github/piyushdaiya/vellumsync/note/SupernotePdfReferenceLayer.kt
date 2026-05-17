package io.github.piyushdaiya.vellumsync.note

import android.graphics.Bitmap
import io.github.piyushdaiya.vellumsync.util.JsonText

/**
 * Compatibility shim for stale PDF-reference call sites.
 *
 * This keeps builds green after the PDF-reference experiment was backed out,
 * without re-enabling PDF fallback rendering in Visual mode.
 */
data class SupernoteRenderedPdfReferencePage(
    val bitmap: Bitmap? = null,
    val status: String = "unavailable",
    val sourcePath: String? = null,
    val sourceFileName: String? = null,
    val pageNumber: Int? = null,
    val pageCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val warning: String? = "PDF reference fallback disabled",
    val usable: Boolean = false
)

object SupernotePdfReferenceLayer {
    @Suppress("UNUSED_PARAMETER")
    fun renderPageForNote(
        notePath: String,
        noteFileName: String,
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int
    ): SupernoteRenderedPdfReferencePage {
        return SupernoteRenderedPdfReferencePage(
            bitmap = null,
            status = "unavailable",
            sourcePath = null,
            sourceFileName = noteFileName,
            pageNumber = pageNumber,
            pageCount = 0,
            width = targetWidth,
            height = targetHeight,
            warning = "PDF reference fallback disabled in compatibility shim.",
            usable = false
        )
    }

    fun diagnosticsJson(renderedPage: SupernoteRenderedPdfReferencePage?): String {
        return buildString {
            append("{")
            append("\"pdfReferenceDetected\":false,")
            append("\"pdfReferenceDecodeStatus\":${JsonText.quote(renderedPage?.status ?: "unavailable")},")
            append("\"pdfReferenceFileName\":${JsonText.quote(renderedPage?.sourceFileName)},")
            append("\"pdfReferencePageNumber\":${renderedPage?.pageNumber ?: "null"},")
            append("\"pdfReferencePageCount\":${renderedPage?.pageCount ?: 0},")
            append("\"pdfReferenceWidth\":${renderedPage?.width ?: 0},")
            append("\"pdfReferenceHeight\":${renderedPage?.height ?: 0},")
            append("\"pdfReferenceWarning\":${JsonText.quote(renderedPage?.warning ?: "PDF reference fallback disabled")}")
            append("}")
        }
    }
}
