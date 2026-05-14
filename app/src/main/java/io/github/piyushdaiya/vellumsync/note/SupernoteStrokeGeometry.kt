package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_PREVIEW_WIDTH = 1404f
private const val DEFAULT_PREVIEW_HEIGHT = 1872f
private const val MAX_GEOMETRY_POINTS_PER_RECORD = 512

data class SupernoteGeometryPoint(
    val x: Float,
    val y: Float
) {
    fun toJson(): String = "{\"x\":${x.formatForJson()},\"y\":${y.formatForJson()}}"
}

data class SupernoteGeometryBounds(
    val minX: Long,
    val maxX: Long,
    val minY: Long,
    val maxY: Long
) {
    fun toJson(): String {
        return "{\"minX\":$minX,\"maxX\":$maxX,\"minY\":$minY,\"maxY\":$maxY}"
    }
}

data class SupernoteStrokeGeometryRecord(
    val recordIndex: Int,
    val category: String,
    val subtype: String,
    val source: String,
    val decodedPointCount: Int,
    val renderedPointCount: Int,
    val rawBounds: SupernoteGeometryBounds?,
    val normalizedBounds: SupernoteGeometryBounds?,
    val points: List<SupernoteGeometryPoint>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"recordIndex\":$recordIndex,")
            append("\"category\":${JsonText.quote(category)},")
            append("\"subtype\":${JsonText.quote(subtype)},")
            append("\"source\":${JsonText.quote(source)},")
            append("\"decodedPointCount\":$decodedPointCount,")
            append("\"renderedPointCount\":$renderedPointCount,")
            append("\"rawBounds\":${rawBounds?.toJson() ?: "null"},")
            append("\"normalizedBounds\":${normalizedBounds?.toJson() ?: "null"},")
            append("\"points\":[")
            append(points.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteStrokeGeometryPageReport(
    val pageNumber: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    val rawBounds: SupernoteGeometryBounds?,
    val transform: String,
    val decodedRecords: Int,
    val renderedRecords: Int,
    val skippedRecords: Int,
    val unknownSubtypeRecords: Int,
    val possibleEraserOrMetadataRecords: Int,
    val records: List<SupernoteStrokeGeometryRecord>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"pageWidth\":${pageWidth.formatForJson()},")
            append("\"pageHeight\":${pageHeight.formatForJson()},")
            append("\"rawBounds\":${rawBounds?.toJson() ?: "null"},")
            append("\"transform\":${JsonText.quote(transform)},")
            append("\"decodedRecords\":$decodedRecords,")
            append("\"renderedRecords\":$renderedRecords,")
            append("\"skippedRecords\":$skippedRecords,")
            append("\"unknownSubtypeRecords\":$unknownSubtypeRecords,")
            append("\"possibleEraserOrMetadataRecords\":$possibleEraserOrMetadataRecords,")
            append("\"records\":[")
            append(records.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteStrokeGeometryReport(
    val formatStatus: String,
    val pageWidth: Float,
    val pageHeight: Float,
    val totalPages: Int,
    val totalDecodedRecords: Int,
    val totalRenderedRecords: Int,
    val totalSkippedRecords: Int,
    val totalUnknownSubtypeRecords: Int,
    val totalPossibleEraserOrMetadataRecords: Int,
    val defaultTransformMode: String = "a5x-raw",
    val supportedTransformModes: List<String> = listOf(
        "a5x-raw",
        "a5x-portrait-candidate",
        "a5x-portrait-candidate-2",
        "a5x-flipped-portrait",
        "a5x-rotated-portrait",
        "raw-fit",
        "rotate-90",
        "rotate-180",
        "rotate-270",
        "flip-x",
        "flip-y"
    ),
    val pageReports: List<SupernoteStrokeGeometryPageReport>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"formatStatus\":${JsonText.quote(formatStatus)},")
            append("\"pageWidth\":${pageWidth.formatForJson()},")
            append("\"pageHeight\":${pageHeight.formatForJson()},")
            append("\"totalPages\":$totalPages,")
            append("\"totalDecodedRecords\":$totalDecodedRecords,")
            append("\"totalRenderedRecords\":$totalRenderedRecords,")
            append("\"totalSkippedRecords\":$totalSkippedRecords,")
            append("\"totalUnknownSubtypeRecords\":$totalUnknownSubtypeRecords,")
            append("\"totalPossibleEraserOrMetadataRecords\":$totalPossibleEraserOrMetadataRecords,")
            append("\"defaultTransformMode\":${JsonText.quote(defaultTransformMode)},")
            append("\"supportedTransformModes\":${JsonText.stringArray(supportedTransformModes)},")
            append("\"pageReports\":[")
            append(pageReports.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

object SupernoteStrokeGeometryDecoder {
    fun decode(
        totalPathProbeReport: SupernoteTotalPathProbeReport,
        pageWidth: Float = DEFAULT_PREVIEW_WIDTH,
        pageHeight: Float = DEFAULT_PREVIEW_HEIGHT
    ): SupernoteStrokeGeometryReport {
        val pageReports = totalPathProbeReport.pageReports.map { page ->
            decodePage(page = page, pageWidth = pageWidth, pageHeight = pageHeight)
        }
        val totalDecoded = pageReports.sumOf { it.decodedRecords }
        val totalRendered = pageReports.sumOf { it.renderedRecords }
        val totalSkipped = pageReports.sumOf { it.skippedRecords }
        val totalUnknown = pageReports.sumOf { it.unknownSubtypeRecords }
        val totalPossibleEraser = pageReports.sumOf { it.possibleEraserOrMetadataRecords }
        val status = when {
            totalRendered == 0 -> "No stroke geometry could be rendered from decoded TOTALPATH records."
            totalSkipped == 0 -> "Stroke geometry decoded and vector preview-ready for all candidate records."
            else -> "Stroke geometry decoded for preview; some records are skipped or fallback-only."
        }
        val warnings = buildList {
            if (totalSkipped > 0) {
                add("$totalSkipped record(s) could not be converted into vector preview geometry.")
            }
            if (totalPossibleEraser > 0) {
                add("$totalPossibleEraser record(s) look like possible eraser, metadata, or non-stroke paths and need subtype work.")
            }
            add("Render fidelity preview supports compact A5X calibration presets plus generic rotation/flip modes.")
            add("Vector preview is still read-only and uses transform candidates for visual alignment against Supernote PDF exports.")
        }
        return SupernoteStrokeGeometryReport(
            formatStatus = status,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            totalPages = pageReports.size,
            totalDecodedRecords = totalDecoded,
            totalRenderedRecords = totalRendered,
            totalSkippedRecords = totalSkipped,
            totalUnknownSubtypeRecords = totalUnknown,
            totalPossibleEraserOrMetadataRecords = totalPossibleEraser,
            defaultTransformMode = "a5x-raw",
            supportedTransformModes = listOf(
                "a5x-raw",
                "a5x-portrait-candidate",
                "a5x-portrait-candidate-2",
                "a5x-flipped-portrait",
                "a5x-rotated-portrait",
                "raw-fit",
                "rotate-90",
                "rotate-180",
                "rotate-270",
                "flip-x",
                "flip-y"
            ),
            pageReports = pageReports,
            warnings = warnings
        )
    }

    private fun decodePage(
        page: SupernoteTotalPathPageReport,
        pageWidth: Float,
        pageHeight: Float
    ): SupernoteStrokeGeometryPageReport {
        val rawRecords = page.candidateRecords.map { record -> rawRecord(record) }
        val allRawPoints = rawRecords.flatMap { it.points }
        val rawBounds = computeRawBounds(allRawPoints)
        val normalizedRecords = rawRecords.map { raw ->
            buildGeometryRecord(
                rawRecord = raw,
                rawBounds = rawBounds,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
        }
        val rendered = normalizedRecords.count { it.points.size >= 2 }
        val skipped = normalizedRecords.size - rendered
        val unknown = normalizedRecords.count { it.subtype == "unknown" }
        val possibleEraser = normalizedRecords.count { it.subtype == "possible_eraser_or_metadata" }
        val warnings = buildList {
            if (rawBounds == null && page.candidateRecords.isNotEmpty()) {
                add("No raw point bounds could be computed for this page.")
            }
            if (skipped > 0) {
                add("$skipped record(s) skipped because fewer than two previewable points were available.")
            }
            if (possibleEraser > 0) {
                add("$possibleEraser record(s) may be eraser/metadata paths and need a later subtype decoder.")
            }
        }
        return SupernoteStrokeGeometryPageReport(
            pageNumber = page.pageNumber,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rawBounds = rawBounds,
            transform = "raw-fit source geometry; viewer can apply A5X calibration, rotation, and flip transform modes",
            decodedRecords = page.candidateRecords.size,
            renderedRecords = rendered,
            skippedRecords = skipped,
            unknownSubtypeRecords = unknown,
            possibleEraserOrMetadataRecords = possibleEraser,
            records = normalizedRecords,
            warnings = warnings
        )
    }

    private data class RawRecord(
        val recordIndex: Int,
        val category: String,
        val source: String,
        val declaredPointCount: Int,
        val points: List<SupernoteRawPoint>
    )

    private fun rawRecord(record: SupernoteTotalPathRecordBoundary): RawRecord {
        val decoded = record.decodedPointArray
        if (decoded != null && decoded.decodedPointCount > 0) {
            val points = buildList {
                addAll(decoded.rawPointPreview)
                decoded.rawPointTailPreview.forEach { point ->
                    if (!contains(point)) add(point)
                }
            }
            return RawRecord(
                recordIndex = record.recordIndex,
                category = record.category,
                source = "decodedPointArray-sampled-preview",
                declaredPointCount = decoded.declaredPointCount,
                points = points
            )
        }

        val fallback = record.candidatePointRun
        if (fallback != null && fallback.previewPairs.isNotEmpty()) {
            val points = fallback.previewPairs.mapNotNull { pair ->
                if (pair.size >= 2) SupernoteRawPoint(x = pair[0], y = pair[1]) else null
            }
            return RawRecord(
                recordIndex = record.recordIndex,
                category = record.category,
                source = "candidatePointRun-fallback-preview",
                declaredPointCount = fallback.pairCount,
                points = points
            )
        }

        return RawRecord(
            recordIndex = record.recordIndex,
            category = record.category,
            source = "none",
            declaredPointCount = 0,
            points = emptyList()
        )
    }

    private fun buildGeometryRecord(
        rawRecord: RawRecord,
        rawBounds: SupernoteGeometryBounds?,
        pageWidth: Float,
        pageHeight: Float
    ): SupernoteStrokeGeometryRecord {
        val sourcePoints = sample(rawRecord.points, MAX_GEOMETRY_POINTS_PER_RECORD)
        val normalized = if (rawBounds == null) {
            emptyList()
        } else {
            sourcePoints.map { point -> normalize(point, rawBounds, pageWidth, pageHeight) }
        }
        val subtype = classifySubtype(rawRecord, normalized)
        val normalizedBounds = if (normalized.isEmpty()) null else computeNormalizedBounds(normalized)
        val rawRecordBounds = computeRawBounds(rawRecord.points)
        val warnings = buildList {
            if (rawRecord.source == "candidatePointRun-fallback-preview") {
                add("Rendered from fallback point-run preview because explicit decodedPointArray was unavailable.")
            }
            if (normalized.size < 2) {
                add("Fewer than two normalized points are available for vector preview.")
            }
            if (rawRecord.category == "unknown") {
                add("Record category is unknown; length-chain boundary is used without semantic path type.")
            }
        }
        return SupernoteStrokeGeometryRecord(
            recordIndex = rawRecord.recordIndex,
            category = rawRecord.category,
            subtype = subtype,
            source = rawRecord.source,
            decodedPointCount = rawRecord.declaredPointCount,
            renderedPointCount = normalized.size,
            rawBounds = rawRecordBounds,
            normalizedBounds = normalizedBounds,
            points = normalized,
            warnings = warnings
        )
    }

    private fun classifySubtype(
        rawRecord: RawRecord,
        normalized: List<SupernoteGeometryPoint>
    ): String {
        if (rawRecord.category == "straightLine") return "straightLine"
        if (rawRecord.category == "unknown" && normalized.size < 2) return "possible_eraser_or_metadata"
        if (rawRecord.category == "unknown") return "unknown"
        if (rawRecord.source == "candidatePointRun-fallback-preview") return "possible_eraser_or_metadata"
        return "normal_stroke"
    }

    private fun sample(points: List<SupernoteRawPoint>, maxPoints: Int): List<SupernoteRawPoint> {
        if (points.size <= maxPoints) return points
        val step = max(1, points.size / maxPoints)
        return points.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)
    }

    private fun computeRawBounds(points: List<SupernoteRawPoint>): SupernoteGeometryBounds? {
        if (points.isEmpty()) return null
        return SupernoteGeometryBounds(
            minX = points.minOf { it.x },
            maxX = points.maxOf { it.x },
            minY = points.minOf { it.y },
            maxY = points.maxOf { it.y }
        )
    }

    private fun computeNormalizedBounds(points: List<SupernoteGeometryPoint>): SupernoteGeometryBounds? {
        if (points.isEmpty()) return null
        return SupernoteGeometryBounds(
            minX = points.minOf { it.x }.toLong(),
            maxX = points.maxOf { it.x }.toLong(),
            minY = points.minOf { it.y }.toLong(),
            maxY = points.maxOf { it.y }.toLong()
        )
    }

    private fun normalize(
        point: SupernoteRawPoint,
        bounds: SupernoteGeometryBounds,
        pageWidth: Float,
        pageHeight: Float
    ): SupernoteGeometryPoint {
        val widthRange = max(1L, bounds.maxX - bounds.minX).toFloat()
        val heightRange = max(1L, bounds.maxY - bounds.minY).toFloat()
        val marginX = pageWidth * 0.04f
        val marginY = pageHeight * 0.04f
        val usableWidth = pageWidth - marginX * 2f
        val usableHeight = pageHeight - marginY * 2f
        val x = marginX + ((point.x - bounds.minX).toFloat() / widthRange) * usableWidth
        val y = marginY + ((point.y - bounds.minY).toFloat() / heightRange) * usableHeight
        return SupernoteGeometryPoint(x = x.coerceIn(0f, pageWidth), y = y.coerceIn(0f, pageHeight))
    }
}

private fun Float.formatForJson(): String {
    if (!isFinite()) return "0"
    return String.format(java.util.Locale.US, "%.3f", this)
}
