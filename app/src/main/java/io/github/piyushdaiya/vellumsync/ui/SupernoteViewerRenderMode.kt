package io.github.piyushdaiya.vellumsync.ui

/**
 * Display-only base rendering modes for Supernote pages.
 *
 * VISUAL prefers decoded MAINLAYER/BGLAYER bitmap content and intentionally
 * suppresses TOTALPATH vector fallback when no trustworthy visual layer is
 * available, so the user does not see misleading scribbles as if they were the
 * original note content.
 */
enum class SupernoteViewerRenderMode(
    val id: String,
    val label: String,
    val statusLabel: String
) {
    VISUAL("visual", "Visual layer", "Visual layer"),
    VECTOR_DEBUG("vector-debug", "Vector debug", "Vector fallback"),
    RAW_FIT_DEBUG("raw-fit-debug", "Raw-fit debug", "Raw-fit debug");
}
