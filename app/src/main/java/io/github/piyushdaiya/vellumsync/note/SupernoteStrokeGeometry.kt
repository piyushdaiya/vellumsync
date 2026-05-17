package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_PREVIEW_WIDTH = 1404f
private const val DEFAULT_PREVIEW_HEIGHT = 1872f
private const val MAX_GEOMETRY_POINTS_PER_RECORD = 1024

// Empirical A5X page-space calibration for SN_FILE_VER_20230015 TOTALPATH
// records. Supernote raw path points use a transposed coordinate frame for
// A5X portrait notes: raw Y primarily maps to visual X, and raw X maps to
// visual Y. These constants replace the old per-page raw-bounds fit that
// stretched native Supernote handwriting into random-looking strokes.
private const val A5X_RAW_Y_RIGHT = 12650f
private const val A5X_RAW_TO_PAGE_X_SCALE = 0.061f
private const val A5X_PAGE_X_LEFT = 82f
private const val A5X_RAW_X_TOP = 2150f
private const val A5X_RAW_TO_PAGE_Y_SCALE = 0.090f
private const val A5X_PAGE_Y_TOP = 125f

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

// marker=vellumsync-erasure-mismatch-gate-compile-repair-v1
data class SupernoteStrokeGeometryRecord(
    val recordIndex: Int,
    val category: String,
    val subtype: String,
    val source: String,
    val decodedPointCount: Int,
    val renderedPointCount: Int,
    val rawBounds: SupernoteGeometryBounds?,
    val normalizedBounds: SupernoteGeometryBounds?,
    /** Page-space points used for normal rendering. For Supernote-native TOTALPATH strokes,
     * this is the absolute A5X mapping, not a raw-bounds fit. */
    val points: List<SupernoteGeometryPoint>,
    /** Debug-only raw-bounds fit points retained for calibration mode. */
    val rawFitPoints: List<SupernoteGeometryPoint> = emptyList(),
    val eraseActionScore: Float = 0f,
    val visualMismatchScore: Float = 0f,
    val suppressedAsErasure: Boolean = false,
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
            append("\"rawFitPointCount\":${rawFitPoints.size},")
            append("\"eraseActionScore\":${eraseActionScore.formatForJson()},")
            append("\"visualMismatchScore\":${visualMismatchScore.formatForJson()},")
            append("\"suppressedAsErasure\":$suppressedAsErasure,")
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
    val filteredDiagonalSentinelRecords: Int = 0,
    val filteredRecordIndexes: List<Int> = emptyList(),
    val filteredRecordBounds: List<SupernoteGeometryBounds> = emptyList(),
    val suppressedRecordCount: Int = 0,
    val suppressedRecordIndexes: List<Int> = emptyList(),
    val visualLayerActiveForNormalRender: Boolean = false,
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
            append("\"filteredDiagonalSentinelRecords\":$filteredDiagonalSentinelRecords,")
            append("\"filteredRecordIndexes\":[")
            append(filteredRecordIndexes.joinToString(separator = ","))
            append("],")
            append("\"filteredRecordBounds\":[")
            append(filteredRecordBounds.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"suppressedRecordCount\":$suppressedRecordCount,")
            append("\"suppressedRecordIndexes\":[")
            append(suppressedRecordIndexes.joinToString(separator = ","))
            append("],")
            append("\"visualLayerActiveForNormalRender\":$visualLayerActiveForNormalRender,")
            append("\"activeRendererDiagnostics\":{")
            append("\"totalDecodedRecords\":$decodedRecords,")
            append("\"renderedNativeRecords\":$renderedRecords,")
            append("\"filteredDiagonalOrSentinelRecords\":$filteredDiagonalSentinelRecords")
            append("},")
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
    val totalFilteredDiagonalSentinelRecords: Int = 0,
    val defaultTransformMode: String = "a5x-absolute",
    val supportedTransformModes: List<String> = listOf(
        "a5x-absolute",
        "pdf-reference-alignment",
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
            append("\"totalFilteredDiagonalSentinelRecords\":$totalFilteredDiagonalSentinelRecords,")
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
        val totalFilteredDiagonalSentinel = pageReports.sumOf { it.filteredDiagonalSentinelRecords }
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
            if (totalFilteredDiagonalSentinel > 0) {
                add("$totalFilteredDiagonalSentinel record(s) were filtered from normal A5X rendering as diagonal/sentinel/non-stroke payloads; raw-fit debug can still show them.")
            }
            add("Default renderer uses A5X absolute TOTALPATH mapping for Supernote-native strokes; raw-fit is debug-only.")
            add("PDF reference alignment mode is display-only and helps compare VellumSync render against Supernote PDF exports.")
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
            totalFilteredDiagonalSentinelRecords = totalFilteredDiagonalSentinel,
            defaultTransformMode = "a5x-absolute",
            supportedTransformModes = listOf(
                "a5x-absolute",
                "pdf-reference-alignment",
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
        val filteredDiagonalSentinel = normalizedRecords.filter { it.subtype == "unsupported_or_misdecoded_record" }
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
            if (filteredDiagonalSentinel.isNotEmpty()) {
                add("${filteredDiagonalSentinel.size} record(s) are filtered from the active A5X renderer as diagonal/sentinel/non-stroke payloads: ${filteredDiagonalSentinel.joinToString { it.recordIndex.toString() }}.")
            }
        }
        return SupernoteStrokeGeometryPageReport(
            pageNumber = page.pageNumber,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rawBounds = rawBounds,
            transform = "a5x-absolute source geometry; raw-fit remains debug-only for calibration",
            decodedRecords = page.candidateRecords.size,
            renderedRecords = rendered,
            skippedRecords = skipped,
            unknownSubtypeRecords = unknown,
            possibleEraserOrMetadataRecords = possibleEraser,
            filteredDiagonalSentinelRecords = filteredDiagonalSentinel.size,
            filteredRecordIndexes = filteredDiagonalSentinel.map { it.recordIndex },
            filteredRecordBounds = filteredDiagonalSentinel.mapNotNull { it.rawBounds },
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
            val points = decoded.rawPoints.ifEmpty {
                buildList {
                    addAll(decoded.rawPointPreview)
                    decoded.rawPointTailPreview.forEach { point ->
                        if (!contains(point)) add(point)
                    }
                }
            }
            return RawRecord(
                recordIndex = record.recordIndex,
                category = record.category,
                source = if (decoded.rawPoints.isNotEmpty()) "decodedPointArray-full" else "decodedPointArray-sampled-preview",
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
        val mappedPagePoints = sourcePoints.map { point -> mapA5xRawPointToPage(point, pageWidth, pageHeight) }
        val rawFitPoints = if (rawBounds == null) {
            emptyList()
        } else {
            sourcePoints.map { point -> normalize(point, rawBounds, pageWidth, pageHeight) }
        }
        val productionSubtype = classifySubtype(rawRecord, mappedPagePoints)
        val heuristicFallbackRecord = rawRecord.source == "candidatePointRun-fallback-preview"
        val suspicious = heuristicFallbackRecord ||
            isSuspiciousProductionStroke(rawRecord, mappedPagePoints, pageWidth, pageHeight)
        val absolutePagePoints = if (suspicious) emptyList() else mappedPagePoints
        val subtype = if (suspicious) "unsupported_or_misdecoded_record" else productionSubtype
        val normalizedBounds = if (absolutePagePoints.isEmpty()) null else computeNormalizedBounds(absolutePagePoints)
        val rawRecordBounds = computeRawBounds(rawRecord.points)
        val warnings = buildList {
            if (rawRecord.source == "candidatePointRun-fallback-preview") {
                add("Rendered from fallback point-run preview because explicit decodedPointArray was unavailable.")
            }
            if (rawRecord.source == "decodedPointArray-sampled-preview") {
                add("Rendered from compact preview/tail point samples; full point array was unavailable.")
            }
            if (absolutePagePoints.size < 2) {
                add("Fewer than two mapped points are available for vector preview.")
            }
            if (rawRecord.category == "unknown") {
                add("Record category is unknown; length-chain boundary is used without semantic path type.")
            }
            if (heuristicFallbackRecord) {
                add("Record suppressed from normal rendering because it came from heuristic fallback point-run decoding. Raw-fit debug can still show it for diagnostics.")
            }
            if (suspicious && !heuristicFallbackRecord) {
                add("Record suppressed from normal rendering because it looks like a misdecoded non-stroke, eraser, marker payload, or page-corner sentinel. Raw-fit debug remains available for calibration.")
            }
        }
        return SupernoteStrokeGeometryRecord(
            recordIndex = rawRecord.recordIndex,
            category = rawRecord.category,
            subtype = subtype,
            source = rawRecord.source,
            decodedPointCount = rawRecord.declaredPointCount,
            renderedPointCount = absolutePagePoints.size,
            rawBounds = rawRecordBounds,
            normalizedBounds = normalizedBounds,
            points = absolutePagePoints,
            rawFitPoints = rawFitPoints,
            warnings = warnings
        )
    }

    private fun isSuspiciousProductionStroke(
        rawRecord: RawRecord,
        points: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (points.size < 2) return false

        val bounds = computeNormalizedBounds(points) ?: return false
        val spanX = (bounds.maxX - bounds.minX).toFloat()
        val spanY = (bounds.maxY - bounds.minY).toFloat()
        val diagonalSpan = spanX > pageWidth * 0.70f && spanY > pageHeight * 0.70f

        val pathLength = pathLength(points)
        val firstPoint = points.first()
        val lastPoint = points.last()
        val directDistance = kotlin.math.hypot(lastPoint.x - firstPoint.x, lastPoint.y - firstPoint.y)
        val straightness = if (pathLength <= 0.0001f) 0f else directDistance / pathLength
        val pageDiagonal = kotlin.math.hypot(pageWidth, pageHeight)

        val diagonalDeltaX = kotlin.math.abs(lastPoint.x - firstPoint.x)
        val diagonalDeltaY = kotlin.math.abs(lastPoint.y - firstPoint.y)
        val diagonalOutlier =
            directDistance > pageDiagonal * 0.38f &&
                straightness > 0.82f &&
                diagonalDeltaX > pageWidth * 0.28f &&
                diagonalDeltaY > pageHeight * 0.22f

        val first = firstPoint
        val last = lastPoint
        val nearLeft = { x: Float -> x <= pageWidth * 0.04f }
        val nearRight = { x: Float -> x >= pageWidth * 0.96f }
        val nearTop = { y: Float -> y <= pageHeight * 0.04f }
        val nearBottom = { y: Float -> y >= pageHeight * 0.96f }
        val cornerToCorner =
            (nearLeft(first.x) && nearBottom(first.y) && nearRight(last.x) && nearTop(last.y)) ||
                (nearRight(first.x) && nearTop(first.y) && nearLeft(last.x) && nearBottom(last.y)) ||
                (nearLeft(last.x) && nearBottom(last.y) && nearRight(first.x) && nearTop(first.y)) ||
                (nearRight(last.x) && nearTop(last.y) && nearLeft(first.x) && nearBottom(first.y))
        if (cornerToCorner && straightness > 0.86f) return true
        if (diagonalSpan && straightness > 0.94f && directDistance > pageDiagonal * 0.65f) return true
        if (diagonalOutlier) return true

        // Preserve normal Supernote straight-line strokes unless they already
        // matched the large diagonal/sentinel checks above. The sync-test
        // payload has one page-scale diagonal record that can be categorized as
        // a straight line even though it is not visible in the Supernote PDF.
        if (rawRecord.category == "straightLine") return false

        // Do not broadly suppress fallback records here. Feature-rich Supernote
        // notes store marker/ink/gray samples in records that can look unusual
        // before exact subtype decoding. The active renderer still filters true
        // corner-to-corner sentinels, but normal records are preserved so the
        // page can render closer to Supernote PDF exports.
        return false
    }

    private fun pathLength(points: List<SupernoteGeometryPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            total += kotlin.math.hypot(b.x - a.x, b.y - a.y)
        }
        return total
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

    private fun mapA5xRawPointToPage(
        point: SupernoteRawPoint,
        pageWidth: Float,
        pageHeight: Float
    ): SupernoteGeometryPoint {
        val x = A5X_PAGE_X_LEFT + (A5X_RAW_Y_RIGHT - point.y.toFloat()) * A5X_RAW_TO_PAGE_X_SCALE
        val y = A5X_PAGE_Y_TOP + (point.x.toFloat() - A5X_RAW_X_TOP) * A5X_RAW_TO_PAGE_Y_SCALE
        return SupernoteGeometryPoint(
            x = x.coerceIn(0f, pageWidth),
            y = y.coerceIn(0f, pageHeight)
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
