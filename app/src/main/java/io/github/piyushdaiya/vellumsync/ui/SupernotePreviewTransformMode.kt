package io.github.piyushdaiya.vellumsync.ui

/**
 * Display-only transform modes used to align decoded Supernote geometry
 * against Supernote PDF exports. These transforms never write back to .note files.
 */
enum class SupernotePreviewTransformMode(
    val id: String,
    val label: String,
    val family: String
) {
    A5X_ABSOLUTE("a5x-absolute", "A5X absolute", "A5X calibration"),
    PDF_REFERENCE_ALIGNMENT("pdf-reference-alignment", "PDF reference", "A5X calibration"),
    A5X_RAW("a5x-raw", "A5X raw", "A5X calibration"),
    A5X_PORTRAIT("a5x-portrait-candidate", "A5X portrait 1", "A5X calibration"),
    A5X_PORTRAIT_2("a5x-portrait-candidate-2", "A5X portrait 2", "A5X calibration"),
    A5X_FLIPPED_PORTRAIT("a5x-flipped-portrait", "A5X flipped portrait", "A5X calibration"),
    A5X_ROTATED_PORTRAIT("a5x-rotated-portrait", "A5X rotated portrait", "A5X calibration"),
    RAW_FIT("raw-fit", "Raw fit debug", "Generic"),
    ROTATE_90("rotate-90", "Rotate 90", "Generic"),
    ROTATE_180("rotate-180", "Rotate 180", "Generic"),
    ROTATE_270("rotate-270", "Rotate 270", "Generic"),
    FLIP_X("flip-x", "Flip X", "Generic"),
    FLIP_Y("flip-y", "Flip Y", "Generic");

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
            A5X_ABSOLUTE,
            PDF_REFERENCE_ALIGNMENT,
            A5X_RAW,
            RAW_FIT -> nx to ny

            A5X_PORTRAIT -> (1f - ny) to (1f - nx)
            A5X_PORTRAIT_2 -> ny to nx
            A5X_FLIPPED_PORTRAIT -> (1f - ny) to nx
            A5X_ROTATED_PORTRAIT -> nx to (1f - ny)

            ROTATE_90 -> ny to (1f - nx)
            ROTATE_180 -> (1f - nx) to (1f - ny)
            ROTATE_270 -> (1f - ny) to nx
            FLIP_X -> (1f - nx) to ny
            FLIP_Y -> nx to (1f - ny)
        }

        return (mapped.first * px).coerceIn(0f, px) to (mapped.second * py).coerceIn(0f, py)
    }

    companion object {
        val viewerOrder: List<SupernotePreviewTransformMode> = listOf(
            A5X_ABSOLUTE,
            PDF_REFERENCE_ALIGNMENT,
            A5X_RAW,
            A5X_PORTRAIT,
            A5X_PORTRAIT_2,
            A5X_FLIPPED_PORTRAIT,
            A5X_ROTATED_PORTRAIT,
            RAW_FIT,
            ROTATE_90,
            ROTATE_180,
            ROTATE_270,
            FLIP_X,
            FLIP_Y
        )

        fun fromId(id: String?): SupernotePreviewTransformMode {
            return values().firstOrNull { it.id == id } ?: A5X_ABSOLUTE
        }
    }
}
