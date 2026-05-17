package io.github.piyushdaiya.vellumsync.note

import kotlin.math.max
import kotlin.math.min

// marker=vellumsync-totalpath-structural-render-bridge-v0
private const val STRUCTURAL_BRIDGE_MAX_POINTS_PER_STROKE = 8192

data class SupernoteStructuralRenderBridgeDiagnostics(
    val pageNumber: Int,
    val structuralStrokeCount: Int,
    val drawableStrokeCount: Int,
    val skippedFlagDrawFalseCount: Int,
    val skippedTinyStrokeCount: Int,
    val maxStrokePointCount: Int,
    val warnings: List<String>
)

object SupernoteStructuralStrokeRenderBridge {
    fun bridge(
        structuralReport: SupernoteTotalPathStructuralReport,
        pageWidth: Float = 1404f,
        pageHeight: Float = 1872f
    ): SupernoteStrokeGeometryReport {
        val pageReports = structuralReport.pageReports.map { page ->
            bridgePage(page = page, defaultPageWidth = pageWidth, defaultPageHeight = pageHeight)
        }
        val totalDecoded = pageReports.sumOf { it.decodedRecords }
        val totalRendered = pageReports.sumOf { it.renderedRecords }
        val totalSkipped = pageReports.sumOf { it.skippedRecords }
        val totalUnknown = pageReports.sumOf { it.unknownSubtypeRecords }
        val totalPossibleMetadata = pageReports.sumOf { it.possibleEraserOrMetadataRecords }
        return SupernoteStrokeGeometryReport(
            formatStatus = if (totalRendered > 0) {
                "TOTALPATH structural render bridge ready for per-stroke debug rendering."
            } else {
                "TOTALPATH structural render bridge found no drawable structured strokes."
            },
            pageWidth = pageReports.firstOrNull()?.pageWidth ?: pageWidth,
            pageHeight = pageReports.firstOrNull()?.pageHeight ?: pageHeight,
            totalPages = pageReports.size,
            totalDecodedRecords = totalDecoded,
            totalRenderedRecords = totalRendered,
            totalSkippedRecords = totalSkipped,
            totalUnknownSubtypeRecords = totalUnknown,
            totalPossibleEraserOrMetadataRecords = totalPossibleMetadata,
            totalFilteredDiagonalSentinelRecords = 0,
            defaultTransformMode = "a5x-absolute",
            supportedTransformModes = listOf(
                "a5x-absolute",
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
            warnings = structuralReport.warnings + listOf(
                "Structural stroke bridge keeps per-stroke TOTALPATH boundaries and uses flag_draw semantics before geometric suppression.",
                "Visual bitmap decoding is unchanged; this mode is a debug bridge for structured TOTALPATH rendering."
            )
        )
    }

    private fun bridgePage(
        page: SupernoteTotalPathStructuralPageReport,
        defaultPageWidth: Float,
        defaultPageHeight: Float
    ): SupernoteStrokeGeometryPageReport {
        val effectiveWidth = max(page.screenWidth.toFloat(), defaultPageWidth)
        val effectiveHeight = max(page.screenHeight.toFloat(), defaultPageHeight)
        var skippedFlagDrawFalseCount = 0
        var skippedTinyStrokeCount = 0
        var unknownSubtypeRecords = 0
        var possibleMetadataRecords = 0
        var maxStrokePointCount = 0
        val records = buildList {
            page.strokes.forEach { stroke ->
                maxStrokePointCount = max(maxStrokePointCount, stroke.points.size)
                val flagDrawFalse = stroke.flagDrawState.contains("false", ignoreCase = true)
                val drawablePoints = stroke.points.take(STRUCTURAL_BRIDGE_MAX_POINTS_PER_STROKE)
                if (flagDrawFalse) {
                    skippedFlagDrawFalseCount += 1
                    return@forEach
                }
                if (drawablePoints.size < 2) {
                    skippedTinyStrokeCount += 1
                    return@forEach
                }
                if (stroke.category.equals("unknown", ignoreCase = true)) {
                    unknownSubtypeRecords += 1
                }
                if (stroke.warnings.any { warning -> warning.contains("pressure", ignoreCase = true) || warning.contains("tilt", ignoreCase = true) }) {
                    possibleMetadataRecords += 1
                }
                val bounds = stroke.bounds?.let {
                    SupernoteGeometryBounds(
                        minX = it.minX,
                        maxX = it.maxX,
                        minY = it.minY,
                        maxY = it.maxY
                    )
                } ?: computeBounds(drawablePoints)
                add(
                    SupernoteStrokeGeometryRecord(
                        recordIndex = stroke.recordIndex,
                        category = stroke.category,
                        subtype = when {
                            stroke.flagDrawState.contains("unknown", ignoreCase = true) -> "structured-unknown-flag-draw"
                            stroke.strokeLayer?.contains("BG", ignoreCase = true) == true -> "structured-background-stroke"
                            else -> "structured-stroke"
                        },
                        source = "structural-totalpath-per-stroke",
                        decodedPointCount = stroke.decodedPointCount,
                        renderedPointCount = drawablePoints.size,
                        rawBounds = bounds,
                        normalizedBounds = bounds,
                        points = drawablePoints.map { point ->
                            SupernoteGeometryPoint(
                                x = point.x.toFloat(),
                                y = point.y.toFloat()
                            )
                        },
                        rawFitPoints = drawablePoints.map { point ->
                            SupernoteGeometryPoint(
                                x = point.x.toFloat(),
                                y = point.y.toFloat()
                            )
                        },
                        warnings = stroke.warnings + listOf(
                            "boundary-source=${stroke.boundarySource}",
                            "flag-draw=${stroke.flagDrawState}",
                            "stroke-layer=${stroke.strokeLayer ?: "TOTALPATH"}"
                        )
                    )
                )
            }
        }
        val diagnostics = SupernoteStructuralRenderBridgeDiagnostics(
            pageNumber = page.pageNumber,
            structuralStrokeCount = page.strokeCount,
            drawableStrokeCount = records.size,
            skippedFlagDrawFalseCount = skippedFlagDrawFalseCount,
            skippedTinyStrokeCount = skippedTinyStrokeCount,
            maxStrokePointCount = maxStrokePointCount,
            warnings = page.warnings
        )
        return SupernoteStrokeGeometryPageReport(
            pageNumber = page.pageNumber,
            pageWidth = effectiveWidth,
            pageHeight = effectiveHeight,
            rawBounds = computePageBounds(records),
            transform = "totalpath-structural-render-bridge",
            decodedRecords = page.strokeCount,
            renderedRecords = records.size,
            skippedRecords = skippedFlagDrawFalseCount + skippedTinyStrokeCount,
            unknownSubtypeRecords = unknownSubtypeRecords,
            possibleEraserOrMetadataRecords = possibleMetadataRecords,
            filteredDiagonalSentinelRecords = 0,
            filteredRecordIndexes = emptyList(),
            filteredRecordBounds = emptyList(),
            records = records,
            warnings = page.warnings + listOf(
                "structural-render-bridge=${diagnostics.drawableStrokeCount}/${diagnostics.structuralStrokeCount}",
                "flag-draw-false-skipped=${diagnostics.skippedFlagDrawFalseCount}",
                "tiny-strokes-skipped=${diagnostics.skippedTinyStrokeCount}",
                "max-stroke-point-count=${diagnostics.maxStrokePointCount}"
            )
        )
    }

    private fun computeBounds(points: List<SupernoteStructuredStrokePoint>): SupernoteGeometryBounds? {
        if (points.isEmpty()) return null
        var minX = Long.MAX_VALUE
        var minY = Long.MAX_VALUE
        var maxX = Long.MIN_VALUE
        var maxY = Long.MIN_VALUE
        points.forEach { point ->
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
        return SupernoteGeometryBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
    }

    private fun computePageBounds(records: List<SupernoteStrokeGeometryRecord>): SupernoteGeometryBounds? {
        val bounds = records.mapNotNull { it.rawBounds }
        if (bounds.isEmpty()) return null
        return SupernoteGeometryBounds(
            minX = bounds.minOf { it.minX },
            maxX = bounds.maxOf { it.maxX },
            minY = bounds.minOf { it.minY },
            maxY = bounds.maxOf { it.maxY }
        )
    }
}
