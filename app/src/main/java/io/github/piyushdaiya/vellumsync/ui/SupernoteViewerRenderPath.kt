package io.github.piyushdaiya.vellumsync.ui

// marker=vellumsync-totalpath-structural-render-bridge-v0
enum class SupernoteViewerRenderPath(
    val id: String,
    val label: String,
    val description: String
) {
    LEGACY_VECTOR(
        id = "legacy-vector",
        label = "Vector debug",
        description = "Existing geometry decoder using candidate TOTALPATH record paths."
    ),
    STRUCTURAL_STROKE(
        id = "structural-stroke",
        label = "Structural stroke",
        description = "SNLib-guided per-stroke rendering from structured TOTALPATH boundaries."
    );

    companion object {
        val viewerOrder: List<SupernoteViewerRenderPath> = listOf(
            LEGACY_VECTOR,
            STRUCTURAL_STROKE
        )
    }
}
