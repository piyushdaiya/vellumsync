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

enum class LocalAnnotationColor(
    val id: String,
    val label: String
) {
    BLACK("black", "Black"),
    GRAY("gray", "Gray");

    companion object {
        fun fromId(id: String?): LocalAnnotationColor {
            return values().firstOrNull { it.id == id } ?: BLACK
        }
    }
}

enum class LocalAnnotationWidth(
    val id: String,
    val label: String,
    val width: Float
) {
    THIN("thin", "Thin", 3f),
    MEDIUM("medium", "Medium", 5f),
    THICK("thick", "Thick", 8f);

    companion object {
        fun fromId(id: String?): LocalAnnotationWidth {
            return values().firstOrNull { it.id == id } ?: MEDIUM
        }

        fun fromWidth(width: Float): LocalAnnotationWidth {
            return values().minByOrNull { kotlin.math.abs(it.width - width) } ?: MEDIUM
        }
    }
}

data class LocalAnnotationStrokeStyle(
    val color: LocalAnnotationColor = LocalAnnotationColor.BLACK,
    val width: LocalAnnotationWidth = LocalAnnotationWidth.MEDIUM
) {
    val widthPx: Float get() = width.width

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"color\":${JsonText.quote(color.id)},")
            append("\"width\":${JsonText.quote(width.id)},")
            append("\"widthPx\":${widthPx.formatFloat()}")
            append("}")
        }
    }

    companion object {
        val DEFAULT = LocalAnnotationStrokeStyle()
    }
}

data class LocalAnnotationStroke(
    val id: String,
    val createdAtMillis: Long,
    val transformModeId: String,
    val toolType: String,
    val width: Float,
    val points: List<LocalAnnotationPoint>,
    val style: LocalAnnotationStrokeStyle = LocalAnnotationStrokeStyle(
        color = LocalAnnotationColor.BLACK,
        width = LocalAnnotationWidth.fromWidth(width)
    )
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"id\":${JsonText.quote(id)},")
            append("\"createdAtMillis\":$createdAtMillis,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"toolType\":${JsonText.quote(toolType)},")
            append("\"width\":${width.formatFloat()},")
            append("\"style\":${style.toJson()},")
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

data class LocalAnnotationNoteSummary(
    val noteSha256: String,
    val annotatedPageCount: Int,
    val totalOverlayStrokes: Int,
    val totalOverlayPoints: Int,
    val sidecarDirectory: String?
) {
    val hasOverlay: Boolean get() = totalOverlayStrokes > 0

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"annotatedPageCount\":$annotatedPageCount,")
            append("\"totalOverlayStrokes\":$totalOverlayStrokes,")
            append("\"totalOverlayPoints\":$totalOverlayPoints,")
            append("\"sidecarDirectory\":${JsonText.quote(sidecarDirectory)}")
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
                appendLine("createdBy=VellumSync Overlay Persistence + Export v0")
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

    fun noteSummary(
        context: Context,
        noteSha256: String
    ): LocalAnnotationNoteSummary {
        val noteDir = overlayNoteDirectory(context, noteSha256)
        if (!noteDir.exists() || !noteDir.isDirectory) {
            return LocalAnnotationNoteSummary(
                noteSha256 = noteSha256,
                annotatedPageCount = 0,
                totalOverlayStrokes = 0,
                totalOverlayPoints = 0,
                sidecarDirectory = noteDir.absolutePath
            )
        }

        val summaries = noteDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("page-") && it.name.endsWith(".vlo") }
            .mapNotNull { file ->
                val page = file.name.removePrefix("page-").removeSuffix(".vlo").toIntOrNull()
                page?.let { pageSummary(context, noteSha256, it) }
            }

        return LocalAnnotationNoteSummary(
            noteSha256 = noteSha256,
            annotatedPageCount = summaries.count { it.strokeCount > 0 },
            totalOverlayStrokes = summaries.sumOf { it.strokeCount },
            totalOverlayPoints = summaries.sumOf { it.pointCount },
            sidecarDirectory = noteDir.absolutePath
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
            append("\"formatStatus\":${JsonText.quote("Local annotation overlay sidecars are stored separately; original Supernote .note files remain read-only.")},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"sidecarFormat\":${JsonText.quote(VERSION_LINE)},")
            append("\"sidecarDirectory\":${JsonText.quote(overlayNoteDirectory(context, noteSha256).absolutePath)},")
            append("\"totalOverlayStrokes\":${pages.sumOf { it.strokeCount }},")
            append("\"totalOverlayPoints\":${pages.sumOf { it.pointCount }},")
            append("\"annotatedPageCount\":${pages.count { it.strokeCount > 0 }},")
            append("\"pages\":[")
            append(pages.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }

    fun overlayExportJson(
        context: Context,
        noteSha256: String,
        totalPages: Int
    ): String {
        val pageEntries = (1..totalPages.coerceAtLeast(1)).map { pageNumber ->
            val strokes = loadPage(context, noteSha256, pageNumber)
            val summary = pageSummary(context, noteSha256, pageNumber)
            buildString {
                append("{")
                append("\"pageNumber\":$pageNumber,")
                append("\"summary\":${summary.toJson()},")
                append("\"strokes\":[")
                append(strokes.joinToString(separator = ",") { it.toJson() })
                append("]")
                append("}")
            }
        }

        val summary = noteSummary(context, noteSha256)
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync local annotation overlay export")},")
            append("\"version\":1,")
            append("\"writeBackStatus\":${JsonText.quote("sidecar-only; original Supernote .note is not modified")},")
            append("\"noteSummary\":${summary.toJson()},")
            append("\"pages\":[")
            append(pageEntries.joinToString(separator = ","))
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

    fun overlayNoteDirectory(
        context: Context,
        noteSha256: String
    ): File {
        val safeSha = noteSha256.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "annotation-overlays/$safeSha")
    }

    private fun LocalAnnotationStroke.toSidecarLine(): String {
        return listOf(
            "stroke",
            id,
            createdAtMillis.toString(),
            transformModeId,
            toolType,
            width.formatFloat(),
            style.color.id,
            style.width.id,
            points.joinToString(separator = ";") { point ->
                "${point.x.formatFloat()},${point.y.formatFloat()}"
            }
        ).joinToString(separator = "\t")
    }

    private fun parseStrokeLine(line: String): LocalAnnotationStroke? {
        val parts = line.split("\t")
        if (parts.size < 7 || parts[0] != "stroke") return null

        val hasStyleFields = parts.size >= 9
        val widthValue = parts[5].toFloatOrNull() ?: LocalAnnotationStrokeStyle.DEFAULT.widthPx
        val color = if (hasStyleFields) LocalAnnotationColor.fromId(parts[6]) else LocalAnnotationColor.BLACK
        val widthClass = if (hasStyleFields) {
            LocalAnnotationWidth.fromId(parts[7])
        } else {
            LocalAnnotationWidth.fromWidth(widthValue)
        }
        val pointPartIndex = if (hasStyleFields) 8 else 6

        val points = parts[pointPartIndex]
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
            width = widthValue,
            points = points,
            style = LocalAnnotationStrokeStyle(color = color, width = widthClass)
        )
    }
}

private fun Float.formatFloat(): String {
    return String.format(Locale.US, "%.3f", this)
}
