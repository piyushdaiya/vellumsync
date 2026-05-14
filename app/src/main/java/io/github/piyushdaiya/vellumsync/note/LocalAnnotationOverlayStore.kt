package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import io.github.piyushdaiya.vellumsync.util.JsonText
import java.io.File
import java.util.Locale

/**
 * Local-only annotation sidecar storage for Android-created strokes.
 *
 * This store intentionally never writes into the original Supernote .note file.
 * Sidecars are keyed by note SHA-256 and page number so the Supernote source
 * remains read-only until a future write-back validator exists.
 */
data class LocalAnnotationPoint(
    val x: Float,
    val y: Float
) {
    fun toJson(): String {
        return "{\"x\":${x.formatFloat()},\"y\":${y.formatFloat()}}"
    }
}

data class LocalAnnotationStroke(
    val id: String,
    val createdAtMillis: Long,
    val transformModeId: String,
    val toolType: String,
    val width: Float,
    val points: List<LocalAnnotationPoint>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"id\":${JsonText.quote(id)},")
            append("\"createdAtMillis\":$createdAtMillis,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"toolType\":${JsonText.quote(toolType)},")
            append("\"width\":${width.formatFloat()},")
            append("\"pointCount\":${points.size},")
            append("\"points\":[")
            append(points.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }
}

data class LocalAnnotationPageSummary(
    val pageNumber: Int,
    val strokeCount: Int,
    val pointCount: Int,
    val sidecarPath: String?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"strokeCount\":$strokeCount,")
            append("\"pointCount\":$pointCount,")
            append("\"sidecarPath\":${JsonText.quote(sidecarPath)}")
            append("}")
        }
    }
}

object LocalAnnotationOverlayStore {
    private const val VERSION_LINE = "VELLUMSYNC_OVERLAY_V1"

    fun loadPage(
        context: Context,
        noteSha256: String,
        pageNumber: Int
    ): List<LocalAnnotationStroke> {
        val file = overlayFile(context, noteSha256, pageNumber)
        if (!file.exists()) return emptyList()

        return file.readLines()
            .asSequence()
            .dropWhile { it != VERSION_LINE }
            .drop(1)
            .filter { line -> line.startsWith("stroke\t") }
            .mapNotNull { line -> parseStrokeLine(line) }
            .toList()
    }

    fun savePage(
        context: Context,
        noteSha256: String,
        pageNumber: Int,
        transformModeId: String,
        strokes: List<LocalAnnotationStroke>
    ): File {
        val file = overlayFile(context, noteSha256, pageNumber)
        file.parentFile?.mkdirs()

        if (strokes.isEmpty()) {
            if (file.exists()) file.delete()
            return file
        }

        file.writeText(
            buildString {
                appendLine(VERSION_LINE)
                appendLine("noteSha256=$noteSha256")
                appendLine("pageNumber=$pageNumber")
                appendLine("lastTransformModeId=$transformModeId")
                appendLine("createdBy=VellumSync Local Annotation Overlay v0")
                strokes.forEach { stroke -> appendLine(stroke.toSidecarLine()) }
            }
        )
        return file
    }

    fun clearPage(
        context: Context,
        noteSha256: String,
        pageNumber: Int
    ) {
        val file = overlayFile(context, noteSha256, pageNumber)
        if (file.exists()) file.delete()
    }

    fun pageSummary(
        context: Context,
        noteSha256: String,
        pageNumber: Int
    ): LocalAnnotationPageSummary {
        val strokes = loadPage(context, noteSha256, pageNumber)
        val file = overlayFile(context, noteSha256, pageNumber)
        return LocalAnnotationPageSummary(
            pageNumber = pageNumber,
            strokeCount = strokes.size,
            pointCount = strokes.sumOf { it.points.size },
            sidecarPath = file.takeIf { it.exists() }?.absolutePath
        )
    }

    fun diagnosticsJson(
        context: Context,
        noteSha256: String,
        totalPages: Int
    ): String {
        val pages = (1..totalPages.coerceAtLeast(1)).map { page ->
            pageSummary(context, noteSha256, page)
        }
        return buildString {
            append("{")
            append("\"formatStatus\":${JsonText.quote("Local annotation overlay v0 sidecars are stored separately; original Supernote .note files remain read-only.")},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"totalOverlayStrokes\":${pages.sumOf { it.strokeCount }},")
            append("\"totalOverlayPoints\":${pages.sumOf { it.pointCount }},")
            append("\"pages\":[")
            append(pages.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }

    fun appendOverlayDiagnostics(
        baseReportJson: String,
        context: Context,
        noteSha256: String,
        totalPages: Int
    ): String {
        val trimmed = baseReportJson.trim()
        val overlayJson = diagnosticsJson(context, noteSha256, totalPages)
        return if (trimmed.endsWith("}")) {
            trimmed.dropLast(1) + ",\"overlayDiagnostics\":$overlayJson}"
        } else {
            trimmed
        }
    }

    fun overlayFile(
        context: Context,
        noteSha256: String,
        pageNumber: Int
    ): File {
        val safeSha = noteSha256.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "annotation-overlays/$safeSha/page-${pageNumber}.vlo")
    }

    private fun LocalAnnotationStroke.toSidecarLine(): String {
        return listOf(
            "stroke",
            id,
            createdAtMillis.toString(),
            transformModeId,
            toolType,
            width.formatFloat(),
            points.joinToString(separator = ";") { point ->
                "${point.x.formatFloat()},${point.y.formatFloat()}"
            }
        ).joinToString(separator = "\t")
    }

    private fun parseStrokeLine(line: String): LocalAnnotationStroke? {
        val parts = line.split("\t")
        if (parts.size < 7 || parts[0] != "stroke") return null
        val points = parts[6]
            .split(";")
            .mapNotNull { pointText ->
                val xy = pointText.split(",")
                if (xy.size != 2) return@mapNotNull null
                val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
                val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
                LocalAnnotationPoint(x = x, y = y)
            }
        return LocalAnnotationStroke(
            id = parts[1],
            createdAtMillis = parts[2].toLongOrNull() ?: 0L,
            transformModeId = parts[3],
            toolType = parts[4],
            width = parts[5].toFloatOrNull() ?: 4f,
            points = points
        )
    }
}

private fun Float.formatFloat(): String {
    return String.format(Locale.US, "%.3f", this)
}
