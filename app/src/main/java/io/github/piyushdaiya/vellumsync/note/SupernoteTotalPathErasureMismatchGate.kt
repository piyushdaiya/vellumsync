package io.github.piyushdaiya.vellumsync.note

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// marker=vellumsync-totalpath-erasure-action-suppression-visual-mismatch-gate-v0
object SupernoteTotalPathErasureMismatchGate {
    fun enrich(
        strokeGeometryReport: SupernoteStrokeGeometryReport,
        bytes: ByteArray,
        visualReport: SupernoteVisualReport
    ): SupernoteStrokeGeometryReport {
        val visualPagesByNumber = visualReport.pageReports.associateBy { it.pageNumber }
        val pageReports = strokeGeometryReport.pageReports.map { page ->
            val visualPage = visualPagesByNumber[page.pageNumber]
            val renderedVisual = visualPage?.let {
                runCatching {
                    SupernoteRattaRleVisualLayerRenderer.renderPageFromVisualPage(
                        bytes = bytes,
                        visualPage = it,
                        width = page.pageWidth.roundToInt().coerceAtLeast(1),
                        height = page.pageHeight.roundToInt().coerceAtLeast(1)
                    )
                }.getOrNull()
            }
            val visualLayerActive = renderedVisual?.usable == true || visualPage?.previewStatus == "Visual layer active"

            val enrichedRecords = page.records.map { record ->
                val eraseActionScore = eraseActionScore(record, page.pageWidth, page.pageHeight)
                val visualMismatchScore = visualMismatchScore(record, renderedVisual)
                val suppressedAsErasure = visualLayerActive &&
                    eraseActionScore >= 0.60f &&
                    visualMismatchScore >= 0.55f

                record.copy(
                    eraseActionScore = eraseActionScore,
                    visualMismatchScore = visualMismatchScore,
                    suppressedAsErasure = suppressedAsErasure,
                    warnings = if (suppressedAsErasure) {
                        record.warnings + "Suppressed from normal rendering because it looks like a lasso/erase action and is visually unsupported by the final bitmap."
                    } else {
                        record.warnings
                    }
                )
            }

            val suppressedIndexes = enrichedRecords
                .filter { it.suppressedAsErasure }
                .map { it.recordIndex }

            page.copy(
                suppressedRecordCount = suppressedIndexes.size,
                suppressedRecordIndexes = suppressedIndexes,
                visualLayerActiveForNormalRender = visualLayerActive,
                records = enrichedRecords,
                warnings = if (suppressedIndexes.isNotEmpty()) {
                    page.warnings + "${suppressedIndexes.size} record(s) suppressed as likely erasure actions: ${suppressedIndexes.joinToString()}."
                } else {
                    page.warnings
                }
            )
        }

        return strokeGeometryReport.copy(
            pageReports = pageReports,
            warnings = if (pageReports.any { it.suppressedRecordCount > 0 }) {
                strokeGeometryReport.warnings + "TOTALPATH erasure-action suppression + visual mismatch gate was applied to pages with an active visual layer."
            } else {
                strokeGeometryReport.warnings
            }
        )
    }

    private fun eraseActionScore(
        record: SupernoteStrokeGeometryRecord,
        pageWidth: Float,
        pageHeight: Float
    ): Float {
        val points = when {
            record.points.size >= 4 -> record.points
            record.rawFitPoints.size >= 4 -> record.rawFitPoints
            else -> return 0f
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        val maxSpan = max(spanX, spanY).coerceAtLeast(1f)
        val pathLength = pathLength(points)
        val directDistance = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
        val closureScore = (1f - (directDistance / maxSpan).coerceIn(0f, 1.5f)).coerceIn(0f, 1f)

        val headingResets = countHeadingResets(points)
        val headingScore = (headingResets / 7f).coerceIn(0f, 1f)

        val intersections = countSelfIntersections(points)
        val intersectionScore = (intersections / 3f).coerceIn(0f, 1f)

        val pageArea = (pageWidth * pageHeight).coerceAtLeast(1f)
        val bboxArea = (spanX * spanY).coerceAtLeast(0f)
        val areaScore = (bboxArea / (pageArea * 0.04f)).coerceIn(0f, 1f)

        val continuityScore = if (pathLength <= 0.0001f) 0f else ((pathLength / maxSpan) / 5f).coerceIn(0f, 1f)

        val subtypeBias = when (record.subtype) {
            "possible_eraser_or_metadata" -> 0.18f
            "unknown" -> 0.10f
            else -> 0f
        }

        val sourceBias = when (record.source) {
            "candidatePointRun-fallback-preview" -> 0.16f
            else -> 0f
        }

        return (
            closureScore * 0.28f +
                headingScore * 0.20f +
                intersectionScore * 0.22f +
                areaScore * 0.12f +
                continuityScore * 0.10f +
                subtypeBias +
                sourceBias
            ).coerceIn(0f, 1f)
    }

    private fun visualMismatchScore(
        record: SupernoteStrokeGeometryRecord,
        renderedVisual: SupernoteRenderedVisualPage?
    ): Float {
        if (renderedVisual == null || !renderedVisual.usable) return 0f
        val points = when {
            record.points.size >= 2 -> record.points
            record.rawFitPoints.size >= 2 -> record.rawFitPoints
            else -> return 0f
        }

        val sampled = samplePoints(points, 96)
        if (sampled.isEmpty()) return 0f

        val supported = sampled.count { point ->
            renderedVisual.grayAt(
                x = point.x.roundToInt().coerceIn(0, renderedVisual.width - 1),
                y = point.y.roundToInt().coerceIn(0, renderedVisual.height - 1)
            ) < 220
        }
        val supportRatio = supported / sampled.size.toFloat()

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        val coverageBias = if (spanX > 40f || spanY > 40f) 0.12f else 0f

        return ((1f - supportRatio) + coverageBias).coerceIn(0f, 1f)
    }

    private fun samplePoints(points: List<SupernoteGeometryPoint>, maxPoints: Int): List<SupernoteGeometryPoint> {
        if (points.size <= maxPoints) return points
        val step = (points.size / maxPoints).coerceAtLeast(1)
        return points.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)
    }

    private fun countHeadingResets(points: List<SupernoteGeometryPoint>): Int {
        if (points.size < 3) return 0
        var resets = 0
        for (i in 1 until points.lastIndex) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            val abx = b.x - a.x
            val aby = b.y - a.y
            val bcx = c.x - b.x
            val bcy = c.y - b.y
            val mag1 = hypot(abx, aby)
            val mag2 = hypot(bcx, bcy)
            if (mag1 < 0.001f || mag2 < 0.001f) continue
            val dot = (abx * bcx + aby * bcy) / (mag1 * mag2)
            if (dot < 0.25f) resets += 1
        }
        return resets
    }

    private fun countSelfIntersections(points: List<SupernoteGeometryPoint>): Int {
        if (points.size < 4) return 0
        var intersections = 0
        for (i in 0 until points.size - 3) {
            val a1 = points[i]
            val a2 = points[i + 1]
            for (j in i + 2 until points.size - 1) {
                if (j == i + 1) continue
                val b1 = points[j]
                val b2 = points[j + 1]
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    intersections += 1
                }
            }
        }
        return intersections
    }

    private fun segmentsIntersect(
        a1: SupernoteGeometryPoint,
        a2: SupernoteGeometryPoint,
        b1: SupernoteGeometryPoint,
        b2: SupernoteGeometryPoint
    ): Boolean {
        val d1 = direction(a1, a2, b1)
        val d2 = direction(a1, a2, b2)
        val d3 = direction(b1, b2, a1)
        val d4 = direction(b1, b2, a2)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
            ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    private fun direction(
        a: SupernoteGeometryPoint,
        b: SupernoteGeometryPoint,
        c: SupernoteGeometryPoint
    ): Float = (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x)

    private fun pathLength(points: List<SupernoteGeometryPoint>): Float {
        var total = 0f
        for (index in 0 until points.lastIndex) {
            total += hypot(points[index + 1].x - points[index].x, points[index + 1].y - points[index].y)
        }
        return total
    }

    private fun SupernoteRenderedVisualPage.grayAt(x: Int, y: Int): Int {
        val index = y * width + x
        return if (index in pixels.indices) pixels[index] else 255
    }
}
