package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import io.github.piyushdaiya.vellumsync.util.JsonText
import java.util.Locale

/**
 * Canonical VellumSync note model.
 *
 * This model is intentionally independent from Supernote container offsets.
 * It gives VellumSync one normalized representation that can be used by
 * previews, overlay editing, export packages, future Supernote write-back,
 * Boox-created notes, conflict review, and two-way sync.
 */
data class VellumDocument(
    val documentId: String,
    val title: String,
    val source: VellumSourceMetadata,
    val pages: List<VellumPage>,
    val transformModeId: String,
    val warnings: List<String> = emptyList()
) {
    val totalPages: Int get() = pages.size
    val totalSupernoteStrokes: Int get() = pages.sumOf { page -> page.layers.sumOf { layer -> layer.strokes.count { it.source == VellumStrokeSource.SUPERNOTE } } }
    val totalOverlayStrokes: Int get() = pages.sumOf { page -> page.layers.sumOf { layer -> layer.strokes.count { it.source == VellumStrokeSource.VELLUMSYNC_OVERLAY } } }

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync canonical note model")},")
            append("\"version\":1,")
            append("\"documentId\":${JsonText.quote(documentId)},")
            append("\"title\":${JsonText.quote(title)},")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"totalPages\":$totalPages,")
            append("\"totalSupernoteStrokes\":$totalSupernoteStrokes,")
            append("\"totalOverlayStrokes\":$totalOverlayStrokes,")
            append("\"source\":${source.toJson()},")
            append("\"pages\":[")
            append(pages.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class VellumSourceMetadata(
    val sourceType: String,
    val noteFileName: String,
    val noteSha256: String,
    val cachedCopyPath: String?,
    val fileSizeBytes: Long,
    val supernoteVersionMarker: String?,
    val equipment: String?,
    val compatibilityStatus: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"sourceType\":${JsonText.quote(sourceType)},")
            append("\"noteFileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"cachedCopyPath\":${JsonText.quote(cachedCopyPath)},")
            append("\"fileSizeBytes\":$fileSizeBytes,")
            append("\"supernoteVersionMarker\":${JsonText.quote(supernoteVersionMarker)},")
            append("\"equipment\":${JsonText.quote(equipment)},")
            append("\"compatibilityStatus\":${JsonText.quote(compatibilityStatus)}")
            append("}")
        }
    }
}

data class VellumPage(
    val pageNumber: Int,
    val width: Float,
    val height: Float,
    val pageStyle: String?,
    val layers: List<VellumLayer>,
    val warnings: List<String> = emptyList()
) {
    val strokeCount: Int get() = layers.sumOf { it.strokes.size }

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"width\":${width.formatCanonicalFloat()},")
            append("\"height\":${height.formatCanonicalFloat()},")
            append("\"pageStyle\":${JsonText.quote(pageStyle)},")
            append("\"strokeCount\":$strokeCount,")
            append("\"layers\":[")
            append(layers.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class VellumLayer(
    val layerId: String,
    val label: String,
    val source: String,
    val visible: Boolean,
    val editable: Boolean,
    val strokes: List<VellumStroke>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"layerId\":${JsonText.quote(layerId)},")
            append("\"label\":${JsonText.quote(label)},")
            append("\"source\":${JsonText.quote(source)},")
            append("\"visible\":$visible,")
            append("\"editable\":$editable,")
            append("\"strokeCount\":${strokes.size},")
            append("\"strokes\":[")
            append(strokes.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }
}

enum class VellumStrokeSource(val id: String) {
    SUPERNOTE("supernote"),
    VELLUMSYNC_OVERLAY("vellumsync_overlay")
}

data class VellumStroke(
    val strokeId: String,
    val source: VellumStrokeSource,
    val pageNumber: Int,
    val layerId: String,
    val toolType: String,
    val color: String,
    val width: String,
    val widthPx: Float,
    val transformModeId: String,
    val createdAtMillis: Long?,
    val points: List<VellumPoint>,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"strokeId\":${JsonText.quote(strokeId)},")
            append("\"source\":${JsonText.quote(source.id)},")
            append("\"pageNumber\":$pageNumber,")
            append("\"layerId\":${JsonText.quote(layerId)},")
            append("\"toolType\":${JsonText.quote(toolType)},")
            append("\"color\":${JsonText.quote(color)},")
            append("\"width\":${JsonText.quote(width)},")
            append("\"widthPx\":${widthPx.formatCanonicalFloat()},")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"createdAtMillis\":${createdAtMillis ?: "null"},")
            append("\"pointCount\":${points.size},")
            append("\"points\":[")
            append(points.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"metadata\":${metadataToJson(metadata)}")
            append("}")
        }
    }
}

data class VellumPoint(
    val x: Float,
    val y: Float,
    val pressure: Float? = null
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"x\":${x.formatCanonicalFloat()},")
            append("\"y\":${y.formatCanonicalFloat()},")
            append("\"pressure\":${pressure?.formatCanonicalFloat() ?: "null"}")
            append("}")
        }
    }
}

object VellumCanonicalNoteModelBuilder {
    fun fromSupernoteAndOverlay(
        context: Context,
        report: SupernoteInspectionReport,
        noteSha256: String = report.sha256,
        noteFileName: String = report.fileName,
        transformModeId: String = report.strokeGeometryReport.defaultTransformMode
    ): VellumDocument {
        val pageStyleByNumber = report.containerReport.pageSections.associate { section ->
            section.pageNumber to section.pageStyle
        }
        val pages = report.strokeGeometryReport.pageReports.map { pageReport ->
            val supernoteStrokes = pageReport.records.map { record ->
                VellumStroke(
                    strokeId = "supernote-p${pageReport.pageNumber}-r${record.recordIndex}",
                    source = VellumStrokeSource.SUPERNOTE,
                    pageNumber = pageReport.pageNumber,
                    layerId = "supernote-base",
                    toolType = record.subtype,
                    color = "black",
                    width = "unknown",
                    widthPx = 1f,
                    transformModeId = transformModeId,
                    createdAtMillis = null,
                    points = record.points.map { point -> VellumPoint(point.x, point.y) },
                    metadata = mapOf(
                        "category" to record.category,
                        "source" to record.source,
                        "decodedPointCount" to record.decodedPointCount.toString(),
                        "renderedPointCount" to record.renderedPointCount.toString()
                    )
                )
            }
            val overlayStrokes = LocalAnnotationOverlayStore.loadPage(
                context = context,
                noteSha256 = noteSha256,
                pageNumber = pageReport.pageNumber
            ).map { stroke ->
                VellumStroke(
                    strokeId = stroke.id,
                    source = VellumStrokeSource.VELLUMSYNC_OVERLAY,
                    pageNumber = pageReport.pageNumber,
                    layerId = "vellumsync-overlay",
                    toolType = stroke.toolType,
                    color = stroke.style.color.id,
                    width = stroke.style.width.id,
                    widthPx = stroke.style.widthPx,
                    transformModeId = stroke.transformModeId,
                    createdAtMillis = stroke.createdAtMillis,
                    points = stroke.points.map { point -> VellumPoint(point.x, point.y) },
                    metadata = mapOf("sidecar" to "true")
                )
            }
            VellumPage(
                pageNumber = pageReport.pageNumber,
                width = pageReport.pageWidth,
                height = pageReport.pageHeight,
                pageStyle = pageStyleByNumber[pageReport.pageNumber],
                layers = listOf(
                    VellumLayer(
                        layerId = "supernote-base",
                        label = "Supernote base",
                        source = "supernote",
                        visible = true,
                        editable = false,
                        strokes = supernoteStrokes
                    ),
                    VellumLayer(
                        layerId = "vellumsync-overlay",
                        label = "VellumSync overlay",
                        source = "local-sidecar",
                        visible = true,
                        editable = true,
                        strokes = overlayStrokes
                    )
                )
            )
        }
        return VellumDocument(
            documentId = noteSha256,
            title = noteFileName,
            source = VellumSourceMetadata(
                sourceType = "supernote-note-read-only-cache",
                noteFileName = noteFileName,
                noteSha256 = noteSha256,
                cachedCopyPath = report.cachedCopyPath,
                fileSizeBytes = report.fileSizeBytes,
                supernoteVersionMarker = report.versionMarker,
                equipment = report.detectedEquipment,
                compatibilityStatus = report.compatibilityStatus
            ),
            pages = pages,
            transformModeId = transformModeId,
            warnings = listOf(
                "Canonical model is an internal interchange model; it does not modify the original Supernote .note file.",
                "Supernote write-back remains gated behind copied-file validation."
            )
        )
    }
}

private fun metadataToJson(metadata: Map<String, String>): String {
    return buildString {
        append("{")
        append(metadata.entries.joinToString(separator = ",") { (key, value) ->
            "${JsonText.quote(key)}:${JsonText.quote(value)}"
        })
        append("}")
    }
}

private fun Float.formatCanonicalFloat(): String {
    return String.format(Locale.US, "%.3f", this)
}
