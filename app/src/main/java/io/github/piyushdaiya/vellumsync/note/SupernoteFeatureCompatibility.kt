package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import java.util.Locale

/**
 * Lightweight feature-awareness layer for Supernote-created notes.
 *
 * This detector is intentionally conservative. It does not claim full edit
 * support for typed text, OCR/recognized text, stickers, imported images, or
 * native eraser objects. The main goal is to report what appears to be present
 * and make write-back/sync flows preserve unknown structures rather than
 * rewriting sections VellumSync does not yet understand.
 */
data class SupernoteFeatureCompatibilityReport(
    val formatStatus: String,
    val preserveUnknownBlocks: Boolean,
    val typedText: FeatureSignal,
    val recognizedText: FeatureSignal,
    val stickers: FeatureSignal,
    val importedImages: FeatureSignal,
    val eraserRecords: FeatureSignal,
    val toolStyles: ToolStyleSignal,
    val grayscaleColors: GrayscaleColorSignal,
    val layers: LayerFeatureSignal,
    val unsupportedEditableFeatures: List<String>,
    val writeBackPolicy: String,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"formatStatus\":${JsonText.quote(formatStatus)},")
            append("\"preserveUnknownBlocks\":$preserveUnknownBlocks,")
            append("\"typedText\":${typedText.toJson()},")
            append("\"recognizedText\":${recognizedText.toJson()},")
            append("\"stickers\":${stickers.toJson()},")
            append("\"importedImages\":${importedImages.toJson()},")
            append("\"eraserRecords\":${eraserRecords.toJson()},")
            append("\"toolStyles\":${toolStyles.toJson()},")
            append("\"grayscaleColors\":${grayscaleColors.toJson()},")
            append("\"layers\":${layers.toJson()},")
            append("\"unsupportedEditableFeatures\":${JsonText.stringArray(unsupportedEditableFeatures)},")
            append("\"writeBackPolicy\":${JsonText.quote(writeBackPolicy)},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class FeatureSignal(
    val present: Boolean,
    val supportLevel: String,
    val markerCounts: Map<String, Int>,
    val notes: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"present\":$present,")
            append("\"supportLevel\":${JsonText.quote(supportLevel)},")
            append("\"markerCounts\":${markerCounts.toJsonObject()},")
            append("\"notes\":${JsonText.stringArray(notes)}")
            append("}")
        }
    }
}

data class ToolStyleSignal(
    val normalStrokeRecords: Int,
    val straightLineRecords: Int,
    val possibleMarkerSignals: Int,
    val possibleNeedlePointSignals: Int,
    val possibleInkPenSignals: Int,
    val supportLevel: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"normalStrokeRecords\":$normalStrokeRecords,")
            append("\"straightLineRecords\":$straightLineRecords,")
            append("\"possibleMarkerSignals\":$possibleMarkerSignals,")
            append("\"possibleNeedlePointSignals\":$possibleNeedlePointSignals,")
            append("\"possibleInkPenSignals\":$possibleInkPenSignals,")
            append("\"supportLevel\":${JsonText.quote(supportLevel)}")
            append("}")
        }
    }
}

data class GrayscaleColorSignal(
    val vellumSyncPalette: List<String>,
    val supernoteGrayMarkers: Map<String, Int>,
    val supportLevel: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"vellumSyncPalette\":${JsonText.stringArray(vellumSyncPalette)},")
            append("\"supernoteGrayMarkers\":${supernoteGrayMarkers.toJsonObject()},")
            append("\"supportLevel\":${JsonText.quote(supportLevel)}")
            append("}")
        }
    }
}

data class LayerFeatureSignal(
    val pageCount: Int,
    val pagesWithMainLayer: Int,
    val pagesWithBackgroundLayer: Int,
    val pagesWithLayer1: Int,
    val pagesWithLayer2: Int,
    val pagesWithLayer3: Int,
    val pagesWithRecognTextOffset: Int,
    val supportLevel: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageCount\":$pageCount,")
            append("\"pagesWithMainLayer\":$pagesWithMainLayer,")
            append("\"pagesWithBackgroundLayer\":$pagesWithBackgroundLayer,")
            append("\"pagesWithLayer1\":$pagesWithLayer1,")
            append("\"pagesWithLayer2\":$pagesWithLayer2,")
            append("\"pagesWithLayer3\":$pagesWithLayer3,")
            append("\"pagesWithRecognTextOffset\":$pagesWithRecognTextOffset,")
            append("\"supportLevel\":${JsonText.quote(supportLevel)}")
            append("}")
        }
    }
}

object SupernoteFeatureCompatibilityAnalyzer {
    fun analyze(
        bytes: ByteArray,
        report: SupernoteInspectionReport
    ): SupernoteFeatureCompatibilityReport {
        val ascii = bytes.toAsciiLikeString()
        val lower = ascii.lowercase(Locale.US)

        val typedText = feature(
            ascii = ascii,
            lower = lower,
            markers = listOf("<TEXT", "<TEXTBOX", "<TYPEDTEXT", "TEXTLAYER", "KEYBOARD"),
            supportWhenPresent = "preserve-only",
            supportWhenAbsent = "not-present",
            notes = listOf("Typed text is treated as an unsupported editable object and preserved during copy-only write-back.")
        )
        val recognizedText = feature(
            ascii = ascii,
            lower = lower,
            markers = listOf("RECOGNTEXT", "RECOGN", "OCR", "HANDWRITINGRECOGNITION"),
            supportWhenPresent = "preserve-and-report",
            supportWhenAbsent = "not-present",
            notes = listOf("Recognized text metadata is preserved and can be exposed/exported; VellumSync does not regenerate OCR metadata yet.")
        )
        val stickers = feature(
            ascii = ascii,
            lower = lower,
            markers = listOf("STICKER", "STICKERBITMAP", "STICKERRECT", "STICKERINFO"),
            supportWhenPresent = "preserve-only",
            supportWhenAbsent = "not-present",
            notes = listOf("Sticker objects are not edited by VellumSync v0 and should be preserved as unknown object blocks.")
        )
        val importedImages = feature(
            ascii = ascii,
            lower = lower,
            markers = listOf("IMAGE", "IMG", "PNG", "JPEG", "JPG", "PICTURE", "BITMAPIMAGE"),
            supportWhenPresent = "preserve-only",
            supportWhenAbsent = "not-present",
            notes = listOf("Imported images are not edited by VellumSync v0 and should remain in the Supernote-created base structure.")
        )
        val erasers = feature(
            ascii = ascii,
            lower = lower,
            markers = listOf("ERASER", "eraser", "erase", "REGIONERASE", "STROKEERASE"),
            supportWhenPresent = "preserve-native; local-sidecar-stroke-eraser-supported",
            supportWhenAbsent = "local-sidecar-stroke-eraser-supported",
            notes = listOf("VellumSync erases local sidecar strokes. Native Supernote eraser records are preserved but not rewritten yet.")
        )

        val toolStyles = ToolStyleSignal(
            normalStrokeRecords = report.totalPathProbeReport.pageReports.sumOf { page ->
                page.candidateRecords.count { it.category == "others" }
            },
            straightLineRecords = report.totalPathProbeReport.pageReports.sumOf { page ->
                page.candidateRecords.count { it.category == "straightLine" }
            },
            possibleMarkerSignals = markerCount(lower, "marker") + markerCount(lower, "highlighter"),
            possibleNeedlePointSignals = markerCount(lower, "needle"),
            possibleInkPenSignals = markerCount(lower, "ink"),
            supportLevel = "render-basic; local-edit-tool-choice; write-back-approximate"
        )

        val grayMarkers = listOf("GRAYS", "epaGrays", "gray", "GREY", "COLOR").associateWith { marker ->
            markerCount(ascii, marker)
        }
        val colors = GrayscaleColorSignal(
            vellumSyncPalette = listOf("white", "light_gray", "dark_gray", "black"),
            supernoteGrayMarkers = grayMarkers,
            supportLevel = "local-edit-supported; write-back-approximate"
        )

        val sections = report.containerReport.pageSections
        val layers = LayerFeatureSignal(
            pageCount = report.containerReport.pageCount,
            pagesWithMainLayer = sections.count { it.layerOffsets.mainLayerOffset != null },
            pagesWithBackgroundLayer = sections.count { it.layerOffsets.backgroundLayerOffset != null },
            pagesWithLayer1 = sections.count { it.layerOffsets.layer1Offset != null && it.layerOffsets.layer1Offset != 0 },
            pagesWithLayer2 = sections.count { it.layerOffsets.layer2Offset != null && it.layerOffsets.layer2Offset != 0 },
            pagesWithLayer3 = sections.count { it.layerOffsets.layer3Offset != null && it.layerOffsets.layer3Offset != 0 },
            pagesWithRecognTextOffset = sections.count { it.recognTextOffset != null && it.recognTextOffset != 0 },
            supportLevel = "base/main/background render; extra native layers preserved"
        )

        val unsupported = buildList {
            if (typedText.present) add("typed text editing")
            if (stickers.present) add("sticker editing")
            if (importedImages.present) add("imported image editing")
            if (erasers.present) add("native Supernote eraser record rewriting")
            if (layers.pagesWithLayer1 + layers.pagesWithLayer2 + layers.pagesWithLayer3 > 0) add("native multi-layer editing")
        }
        val warnings = buildList {
            if (unsupported.isNotEmpty()) {
                add("Unsupported editable Supernote features detected: ${unsupported.joinToString()}.")
            }
            add("Write-back remains copy-only and only appends/merges supported VellumSync sidecar strokes into Supernote-created notes.")
            add("Unknown object blocks are preserved by the copy-based writer and should not be interpreted as editable until a dedicated decoder exists.")
        }

        return SupernoteFeatureCompatibilityReport(
            formatStatus = "Feature compatibility report generated from Supernote-created .note markers and parsed page/layer metadata.",
            preserveUnknownBlocks = true,
            typedText = typedText,
            recognizedText = recognizedText,
            stickers = stickers,
            importedImages = importedImages,
            eraserRecords = erasers,
            toolStyles = toolStyles,
            grayscaleColors = colors,
            layers = layers,
            unsupportedEditableFeatures = unsupported,
            writeBackPolicy = "Only supported VellumSync local overlay strokes are written into copied Supernote-created notes. Existing unknown/native object blocks are preserved.",
            warnings = warnings
        )
    }

    private fun feature(
        ascii: String,
        lower: String,
        markers: List<String>,
        supportWhenPresent: String,
        supportWhenAbsent: String,
        notes: List<String>
    ): FeatureSignal {
        val counts = markers.associateWith { marker ->
            if (marker.any { it.isLowerCase() }) markerCount(lower, marker.lowercase(Locale.US)) else markerCount(ascii, marker)
        }.filterValues { it > 0 }
        return FeatureSignal(
            present = counts.isNotEmpty(),
            supportLevel = if (counts.isNotEmpty()) supportWhenPresent else supportWhenAbsent,
            markerCounts = counts,
            notes = notes
        )
    }

    private fun markerCount(text: String, marker: String): Int {
        if (marker.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(marker)
        while (index >= 0) {
            count++
            index = text.indexOf(marker, index + marker.length)
        }
        return count
    }

    private fun ByteArray.toAsciiLikeString(): String {
        return buildString(size) {
            this@toAsciiLikeString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(if (value in 32..126) value.toChar() else '.')
            }
        }
    }

}

private fun Map<String, Int>.toJsonObject(): String {
    return entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
        "${JsonText.quote(key)}:$value"
    }
}

