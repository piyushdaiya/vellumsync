package io.github.piyushdaiya.vellumsync.ui

/**
 * Viewer render selection for Supernote-created notes.
 *
 * VISUAL_LAYER is the normal/default read-only path. TOTALPATH vectors remain
 * available only as fallback/debug renderers so noisy decoded vectors are not
 * drawn over a plausible Supernote visual bitmap layer.
 */
enum class SupernoteRenderMode(
    val id: String,
    val label: String
) {
    VISUAL_LAYER("visual-layer", "Visual layer"),
    VECTOR_DEBUG("vector-debug", "Vector debug"),
    RAW_FIT_DEBUG("raw-fit-debug", "Raw-fit debug");

    val usesVectorFallback: Boolean
        get() = this == VECTOR_DEBUG || this == RAW_FIT_DEBUG
}
