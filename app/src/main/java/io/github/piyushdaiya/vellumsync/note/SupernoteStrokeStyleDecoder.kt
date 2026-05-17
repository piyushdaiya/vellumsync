package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import kotlin.math.roundToInt

/**
 * Read-only Supernote TOTALPATH stroke-style metadata decoder.
 *
 * The fields below are intentionally named as empirical fields because this is
 * reverse-engineered from Supernote A5X NOTE records, not from a public schema.
 * Diagnostics keep the raw values so future fixture notes can refine the map
 * without hiding unknown values behind a generic fallback.
 */
data class SupernoteStrokeStyleMetadata(
    val toolTypeRaw: Long?,
    val colorRaw: Long?,
    val widthRaw: Long?,
    val pageNumberRaw: Long?,
    val drawModifierRaw: Long?,
    val toolFamily: String,
    val styleLabel: String,
    val grayPaletteName: String,
    val mappedGray: Int,
    val mappedAndroidColor: Int,
    val strokeWidthPx: Float,
    val strokeAlpha: Int,
    val capStyle: String,
    val joinStyle: String,
    val isEraser: Boolean,
    val eraserMode: String?,
    val unknownMetadataSample: String?,
    val warnings: List<String> = emptyList()
) {
    val styleCountKey: String
        get() = if (isEraser) "eraser:$eraserMode" else "$toolFamily:$styleLabel:${strokeWidthPx.roundToInt()}px"

    val colorCountKey: String
        get() = "raw=${colorRaw ?: "null"}->gray=$mappedGray"

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"toolTypeRaw\":${toolTypeRaw ?: "null"},")
            append("\"colorRaw\":${colorRaw ?: "null"},")
            append("\"widthRaw\":${widthRaw ?: "null"},")
            append("\"pageNumberRaw\":${pageNumberRaw ?: "null"},")
            append("\"drawModifierRaw\":${drawModifierRaw ?: "null"},")
            append("\"toolFamily\":${JsonText.quote(toolFamily)},")
            append("\"styleLabel\":${JsonText.quote(styleLabel)},")
            append("\"grayPaletteName\":${JsonText.quote(grayPaletteName)},")
            append("\"mappedGray\":$mappedGray,")
            append("\"mappedAndroidColor\":$mappedAndroidColor,")
            append("\"strokeWidthPx\":${strokeWidthPx.formatForStyleJson()},")
            append("\"strokeAlpha\":$strokeAlpha,")
            append("\"capStyle\":${JsonText.quote(capStyle)},")
            append("\"joinStyle\":${JsonText.quote(joinStyle)},")
            append("\"isEraser\":$isEraser,")
            append("\"eraserMode\":${JsonText.quote(eraserMode)},")
            append("\"unknownMetadataSample\":${JsonText.quote(unknownMetadataSample)},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }

    companion object {
        val UNKNOWN = SupernoteStrokeStyleMetadata(
            toolTypeRaw = null,
            colorRaw = null,
            widthRaw = null,
            pageNumberRaw = null,
            drawModifierRaw = null,
            toolFamily = "unknown",
            styleLabel = "unknown-style",
            grayPaletteName = "supernote-grayscale-v0",
            mappedGray = 0,
            mappedAndroidColor = -0x1000000,
            strokeWidthPx = 3f,
            strokeAlpha = 255,
            capStyle = "round",
            joinStyle = "round",
            isEraser = false,
            eraserMode = null,
            unknownMetadataSample = "missing-first-u32-fields",
            warnings = listOf("No Supernote stroke-style fields were available for this record.")
        )
    }
}

object SupernoteStrokeStyleDecoder {
    private const val DEFAULT_STROKE_WIDTH_PX = 3f

    fun decode(firstU32LeFields: List<Long>, recordIndex: Int): SupernoteStrokeStyleMetadata {
        val toolTypeRaw = firstU32LeFields.getOrNull(3)
        val colorRaw = firstU32LeFields.getOrNull(4)
        val widthRaw = firstU32LeFields.getOrNull(5)
        val pageNumberRaw = firstU32LeFields.getOrNull(10)
        val drawModifierRaw = firstU32LeFields.getOrNull(13)
        if (toolTypeRaw == null && colorRaw == null && widthRaw == null) {
            return SupernoteStrokeStyleMetadata.UNKNOWN
        }

        val eraser = isEraserRecord(toolTypeRaw, colorRaw, drawModifierRaw)
        val eraserMode = if (eraser) eraserMode(toolTypeRaw, drawModifierRaw) else null
        val toolFamily = if (eraser) {
            "eraser"
        } else {
            toolFamily(toolTypeRaw)
        }
        val styleLabel = if (eraser) {
            eraserMode ?: "eraser-knockout"
        } else {
            styleLabel(toolTypeRaw)
        }
        val mappedGray = if (eraser) 255 else mapSupernoteGray(colorRaw)
        val alpha = when {
            eraser -> 255
            toolTypeRaw == 10L -> 220
            toolTypeRaw == 11L -> 210
            else -> 255
        }
        val widthPx = mapWidth(widthRaw, toolTypeRaw, eraser)
        val unknownSample = if (isKnownTool(toolTypeRaw) && colorRaw != null && widthRaw != null) {
            null
        } else {
            "record=$recordIndex fields=${firstU32LeFields.take(16).joinToString(prefix = "[", postfix = "]")}" 
        }
        val warnings = buildList {
            if (!isKnownTool(toolTypeRaw)) {
                add("Unknown Supernote tool/style raw value $toolTypeRaw; rendering with conservative visible fallback and logging metadata sample.")
            }
            if (colorRaw != null && colorRaw !in 0L..255L) {
                add("Supernote grayscale raw value $colorRaw is outside byte range; clamped into Android grayscale palette.")
            }
            if (widthRaw == null) {
                add("No Supernote width raw value was available; using default width.")
            }
            if (eraser) {
                add("Record is treated as eraser knockout; renderer clears native ink layer instead of drawing this path as visible ink.")
            }
        }
        return SupernoteStrokeStyleMetadata(
            toolTypeRaw = toolTypeRaw,
            colorRaw = colorRaw,
            widthRaw = widthRaw,
            pageNumberRaw = pageNumberRaw,
            drawModifierRaw = drawModifierRaw,
            toolFamily = toolFamily,
            styleLabel = styleLabel,
            grayPaletteName = "supernote-grayscale-v0",
            mappedGray = mappedGray,
            mappedAndroidColor = androidArgb(alpha = alpha, gray = mappedGray),
            strokeWidthPx = widthPx,
            strokeAlpha = alpha,
            capStyle = capStyle(toolTypeRaw, eraser),
            joinStyle = joinStyle(toolTypeRaw, eraser),
            isEraser = eraser,
            eraserMode = eraserMode,
            unknownMetadataSample = unknownSample,
            warnings = warnings
        )
    }

    fun summarizeStyles(records: List<SupernoteStrokeGeometryRecord>): Map<String, Int> {
        return records.groupingBy { it.styleMetadata.styleCountKey }.eachCount().toSortedMap()
    }

    fun summarizeColors(records: List<SupernoteStrokeGeometryRecord>): Map<String, Int> {
        return records.filterNot { it.styleMetadata.isEraser }
            .groupingBy { it.styleMetadata.colorCountKey }
            .eachCount()
            .toSortedMap()
    }

    fun unknownSamples(records: List<SupernoteStrokeGeometryRecord>, maxSamples: Int = 8): List<String> {
        return records.mapNotNull { it.styleMetadata.unknownMetadataSample }.distinct().take(maxSamples)
    }

    private fun isKnownTool(toolTypeRaw: Long?): Boolean {
        return toolTypeRaw in setOf(1L, 3L, 4L, 10L, 11L)
    }

    private fun isEraserRecord(toolTypeRaw: Long?, colorRaw: Long?, drawModifierRaw: Long?): Boolean {
        if (toolTypeRaw in setOf(3L, 4L)) return true
        if (colorRaw == 255L && drawModifierRaw in setOf(0xffffffffL, 0xfffffffeL, 0xfffffffbL)) return true
        return false
    }

    private fun eraserMode(toolTypeRaw: Long?, drawModifierRaw: Long?): String {
        return when {
            toolTypeRaw == 4L || drawModifierRaw == 0xfffffffbL -> "precision-eraser-knockout"
            toolTypeRaw == 3L || drawModifierRaw == 0xfffffffeL -> "area-eraser-knockout"
            else -> "bitmap-eraser-knockout"
        }
    }

    private fun toolFamily(toolTypeRaw: Long?): String {
        return when (toolTypeRaw) {
            1L -> "pen"
            10L -> "marker"
            11L -> "wide-marker"
            else -> "unknown"
        }
    }

    private fun styleLabel(toolTypeRaw: Long?): String {
        return when (toolTypeRaw) {
            1L -> "round-ink"
            10L -> "soft-marker"
            11L -> "wide-soft-marker"
            else -> "unknown-style"
        }
    }

    private fun capStyle(toolTypeRaw: Long?, eraser: Boolean): String {
        return when {
            eraser -> "round"
            toolTypeRaw == 10L || toolTypeRaw == 11L -> "square"
            else -> "round"
        }
    }

    private fun joinStyle(toolTypeRaw: Long?, eraser: Boolean): String {
        return when {
            eraser -> "round"
            toolTypeRaw == 10L || toolTypeRaw == 11L -> "bevel"
            else -> "round"
        }
    }

    private fun mapSupernoteGray(colorRaw: Long?): Int {
        val raw = colorRaw ?: return 0
        return when {
            raw <= 0L -> 0
            raw >= 255L -> 255
            else -> raw.toInt().coerceIn(0, 255)
        }
    }

    private fun mapWidth(widthRaw: Long?, toolTypeRaw: Long?, eraser: Boolean): Float {
        val base = widthRaw?.toFloat()?.div(100f) ?: DEFAULT_STROKE_WIDTH_PX
        val adjusted = when {
            eraser -> base.coerceIn(8f, 42f)
            toolTypeRaw == 10L || toolTypeRaw == 11L -> (base * 1.25f).coerceIn(3.5f, 40f)
            else -> base.coerceIn(1.5f, 30f)
        }
        return adjusted
    }

    private fun androidArgb(alpha: Int, gray: Int): Int {
        val a = alpha.coerceIn(0, 255)
        val g = gray.coerceIn(0, 255)
        return (a shl 24) or (g shl 16) or (g shl 8) or g
    }
}

private fun Float.formatForStyleJson(): String {
    return if (this.isFinite()) {
        String.format(java.util.Locale.US, "%.3f", this)
    } else {
        "0"
    }
}
