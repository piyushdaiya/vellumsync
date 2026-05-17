package io.github.piyushdaiya.vellumsync.note

// marker=vellumsync-totalpath-tool-semantics-classifier-lasso-eraser-suppression-v0

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal enum class SupernoteToolSemanticKind(val wireName: String) {
    PEN_INK_STROKE("pen_ink_stroke"),
    MARKER_STROKE("marker_stroke"),
    LASSO_ERASE_GESTURE("lasso_erase_gesture"),
    NON_DRAWABLE_UTILITY_DEBUG("non_drawable_utility_debug")
}

internal data class SupernoteToolSemanticClassification(
    val kind: SupernoteToolSemanticKind,
    val confidence: Float,
    val reasons: List<String>
)

internal object SupernoteTotalPathToolSemanticsClassifier {
    fun classify(
        recordIndex: Int,
        category: String,
        source: String,
        rawPoints: List<SupernoteRawPoint>,
        mappedPoints: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): SupernoteToolSemanticClassification {
        if (mappedPoints.size < 2 || rawPoints.size < 2) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.NON_DRAWABLE_UTILITY_DEBUG,
                confidence = 0.97f,
                reasons = listOf("Record $recordIndex has fewer than two drawable points.")
            )
        }

        val bounds = computeBounds(mappedPoints)
        val spanX = max(1f, bounds.maxX - bounds.minX)
        val spanY = max(1f, bounds.maxY - bounds.minY)
        val diagonal = hypot(spanX, spanY)
        val pathLength = computePathLength(mappedPoints)
        val closureDistance = hypot(
            mappedPoints.last().x - mappedPoints.first().x,
            mappedPoints.last().y - mappedPoints.first().y
        )
        val closureRatio = if (pathLength <= 0.0001f) 1f else (closureDistance / pathLength)
        val dominantAxisRatio = max(spanX, spanY) / max(1f, min(spanX, spanY))
        val pageCoverage = (spanX / max(1f, pageWidth)) * (spanY / max(1f, pageHeight))
        val selfIntersections = estimateSelfIntersections(mappedPoints)
        val headingResetCount = estimateHeadingResets(mappedPoints)
        val tiny = spanX < pageWidth * 0.025f && spanY < pageHeight * 0.025f && mappedPoints.size <= 40
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val giantDiagonal = diagonal > pageDiagonal * 0.60f && closureRatio > 0.65f
        val horizontalSampleStroke = spanX > pageWidth * 0.10f && spanY < pageHeight * 0.035f && dominantAxisRatio > 4.0f
        val verticalSampleStroke = spanY > pageHeight * 0.10f && spanX < pageWidth * 0.035f && dominantAxisRatio > 4.0f
        val loopLike = closureRatio < 0.10f && mappedPoints.size >= 20 && spanX > pageWidth * 0.03f && spanY > pageHeight * 0.03f
        val lassoLike = loopLike && (selfIntersections > 0 || headingResetCount >= 3 || pageCoverage > 0.0045f)
        val debugLike = source.contains("fallback", ignoreCase = true) || category == "unknown"

        if (tiny) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.NON_DRAWABLE_UTILITY_DEBUG,
                confidence = 0.88f,
                reasons = listOf("Tiny compact record looks like calibration/debug glyph rather than note content.")
            )
        }

        if (giantDiagonal) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.NON_DRAWABLE_UTILITY_DEBUG,
                confidence = 0.90f,
                reasons = listOf("Page-scale diagonal/sentinel-like record should not be rendered as note ink.")
            )
        }

        if (lassoLike) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.LASSO_ERASE_GESTURE,
                confidence = when {
                    selfIntersections >= 2 -> 0.94f
                    headingResetCount >= 5 -> 0.91f
                    else -> 0.87f
                },
                reasons = buildList {
                    add("Closed or near-closed loop with note-selection/lasso geometry.")
                    if (selfIntersections > 0) add("Contains self-intersection / wrapback behavior.")
                    if (headingResetCount >= 3) add("Shows repeated heading resets typical of selection loops.")
                }
            )
        }

        if (debugLike && !horizontalSampleStroke && !verticalSampleStroke) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.NON_DRAWABLE_UTILITY_DEBUG,
                confidence = 0.74f,
                reasons = listOf("Unknown or fallback record lacks strong stroke semantics and is safer to keep in debug/inspector only.")
            )
        }

        if ((horizontalSampleStroke || verticalSampleStroke) && mappedPoints.size >= 18) {
            return SupernoteToolSemanticClassification(
                kind = SupernoteToolSemanticKind.MARKER_STROKE,
                confidence = 0.66f,
                reasons = listOf("Long sample-like stroke with repeated evenly-spaced points resembles marker/brush test content.")
            )
        }

        return SupernoteToolSemanticClassification(
            kind = SupernoteToolSemanticKind.PEN_INK_STROKE,
            confidence = 0.61f,
            reasons = listOf("Default drawable stroke classification after excluding lasso/eraser and non-drawable utility paths.")
        )
    }

    private data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

    private fun computeBounds(points: List<SupernoteGeometryPoint>): Bounds {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        points.forEach { point ->
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }
        return Bounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
    }

    private fun computePathLength(points: List<SupernoteGeometryPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            total += hypot(b.x - a.x, b.y - a.y)
        }
        return total
    }

    private fun estimateHeadingResets(points: List<SupernoteGeometryPoint>): Int {
        if (points.size < 4) return 0
        var resets = 0
        var previousAngle: Double? = null
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            if (abs(dx) < 0.001 && abs(dy) < 0.001) continue
            val angle = kotlin.math.atan2(dy, dx)
            val previous = previousAngle
            if (previous != null) {
                val delta = abs(angle - previous)
                val wrapped = min(delta, (Math.PI * 2) - delta)
                if (wrapped > Math.PI * 0.60) resets += 1
            }
            previousAngle = angle
        }
        return resets
    }

    private fun estimateSelfIntersections(points: List<SupernoteGeometryPoint>): Int {
        if (points.size < 5) return 0
        var intersections = 0
        val maxSegments = min(points.size - 1, 48)
        for (aIndex in 0 until maxSegments - 1) {
            val a1 = points[aIndex]
            val a2 = points[aIndex + 1]
            for (bIndex in aIndex + 2 until maxSegments) {
                if (bIndex == aIndex + 1) continue
                if (aIndex == 0 && bIndex == maxSegments - 1) continue
                val b1 = points[bIndex]
                val b2 = points[min(bIndex + 1, points.lastIndex)]
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    intersections += 1
                    if (intersections >= 4) return intersections
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
        val o1 = orientation(a1, a2, b1)
        val o2 = orientation(a1, a2, b2)
        val o3 = orientation(b1, b2, a1)
        val o4 = orientation(b1, b2, a2)
        return o1 != o2 && o3 != o4
    }

    private fun orientation(a: SupernoteGeometryPoint, b: SupernoteGeometryPoint, c: SupernoteGeometryPoint): Int {
        val value = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        return when {
            value > 0f -> 1
            value < 0f -> -1
            else -> 0
        }
    }
}
