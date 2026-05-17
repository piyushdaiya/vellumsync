package io.github.piyushdaiya.vellumsync.note

/**
 * Heuristic subtype classifier for feature-rich Supernote TOTALPATH records.
 *
 * This is intentionally conservative.  It improves local render/export styling
 * for Needle / Ink / Marker-like records without claiming exact Supernote tool
 * fidelity yet.
 */
data class SupernoteInkMarkerSubtype(
    val subtype: String,
    val strokeWidthHintPx: Float,
    val grayscaleHint: Int,
    val renderPriority: Int,
    val warning: String? = null
)

object SupernoteInkMarkerSubtypeDecoder {
    fun classify(
        category: String,
        source: String,
        pointCount: Int,
        bounds: SupernoteGeometryBounds?,
        mappedPoints: List<SupernoteGeometryPoint>
    ): SupernoteInkMarkerSubtype {
        if (category == "straightLine") {
            return SupernoteInkMarkerSubtype("straightLine", 3.0f, 24, 20)
        }
        if (source == "candidatePointRun-fallback-preview") {
            return SupernoteInkMarkerSubtype("fallback_or_metadata", 1.5f, 160, -20, "Fallback point-run record is diagnostic unless visual layer is unavailable.")
        }
        if (mappedPoints.size < 2) {
            return SupernoteInkMarkerSubtype("possible_eraser_or_metadata", 1.0f, 200, -10)
        }

        val width = boundsWidth(mappedPoints)
        val height = boundsHeight(mappedPoints)
        val density = if (width <= 0f || height <= 0f) 0f else pointCount / ((width * height) / 1000f).coerceAtLeast(1f)
        return when {
            height >= 18f && width >= 80f && density >= 0.18f -> SupernoteInkMarkerSubtype("marker_or_highlighter", 12f, 95, 30)
            height >= 8f && pointCount >= 12 -> SupernoteInkMarkerSubtype("ink_pen", 4.5f, 40, 25)
            height <= 7f && width >= 20f -> SupernoteInkMarkerSubtype("needle_point_pen", 2.25f, 32, 25)
            else -> SupernoteInkMarkerSubtype("normal_stroke", 3.0f, 32, 10)
        }
    }

    private fun boundsWidth(points: List<SupernoteGeometryPoint>): Float {
        if (points.isEmpty()) return 0f
        return points.maxOf { it.x } - points.minOf { it.x }
    }

    private fun boundsHeight(points: List<SupernoteGeometryPoint>): Float {
        if (points.isEmpty()) return 0f
        return points.maxOf { it.y } - points.minOf { it.y }
    }
}
