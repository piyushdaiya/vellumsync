package io.github.piyushdaiya.vellumsync.ui

/**
 * Display-only transform candidates used to align decoded Supernote raw geometry
 * against exported PDFs and photos. These transforms never write back to .note files.
 */
enum class SupernotePreviewTransformMode(
    val id: String,
    val label: String
) {
    RAW_FIT("raw-fit", "Raw fit"),
    A5X_PORTRAIT("a5x-portrait-candidate", "A5X portrait"),
    ROTATE_90("rotate-90", "Rotate 90"),
    ROTATE_180("rotate-180", "Rotate 180"),
    ROTATE_270("rotate-270", "Rotate 270"),
    FLIP_X("flip-x", "Flip X"),
    FLIP_Y("flip-y", "Flip Y");

    fun transform(
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float
    ): Pair<Float, Float> {
        val px = pageWidth.coerceAtLeast(1f)
        val py = pageHeight.coerceAtLeast(1f)
        val nx = (x / px).coerceIn(0f, 1f)
        val ny = (y / py).coerceIn(0f, 1f)

        val mapped = when (this) {
            RAW_FIT -> nx to ny
            ROTATE_90 -> ny to (1f - nx)
            ROTATE_180 -> (1f - nx) to (1f - ny)
            ROTATE_270 -> (1f - ny) to nx
            FLIP_X -> (1f - nx) to ny
            FLIP_Y -> nx to (1f - ny)
            A5X_PORTRAIT -> (1f - ny) to (1f - nx)
        }

        return (mapped.first * px).coerceIn(0f, px) to (mapped.second * py).coerceIn(0f, py)
    }

    companion object {
        fun fromId(id: String?): SupernotePreviewTransformMode {
            return values().firstOrNull { it.id == id } ?: A5X_PORTRAIT
        }
    }
}
