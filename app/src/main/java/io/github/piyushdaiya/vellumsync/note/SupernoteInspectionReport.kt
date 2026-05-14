package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText

data class MarkerHit(
    val marker: String,
    val count: Int,
    val offsets: List<Int>,
    val contexts: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"marker\":${JsonText.quote(marker)},")
            append("\"count\":$count,")
            append("\"offsets\":[${offsets.joinToString(separator = ",")}],")
            append("\"contexts\":${JsonText.stringArray(contexts)}")
            append("}")
        }
    }
}

data class SupernoteInspectionReport(
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val headerPreviewHex: String,
    val headerPreviewAscii: String,
    val versionMarker: String?,
    val detectedEquipment: String?,
    val estimatedPageCount: Int,
    val hasNoteMarker: Boolean,
    val hasMainLayer: Boolean,
    val hasBackgroundLayer: Boolean,
    val hasLayerInfo: Boolean,
    val hasLayerSequence: Boolean,
    val hasTotalPath: Boolean,
    val hasPageStyle: Boolean,
    val hasTitleMetadata: Boolean,
    val hasKeywordMetadata: Boolean,
    val hasLinkMetadata: Boolean,
    val hasExternalLinkInfoField: Boolean,
    val hasStarMetadata: Boolean,
    val markerHits: List<MarkerHit>,
    val containerReport: SupernoteContainerReport,
    val visualReport: SupernoteVisualReport,
    val totalPathProbeReport: SupernoteTotalPathProbeReport,
    val strokeGeometryReport: SupernoteStrokeGeometryReport,
    val compatibilityStatus: String,
    val warnings: List<String>,
    val cachedCopyPath: String?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"fileName\":${JsonText.quote(fileName)},")
            append("\"fileSizeBytes\":$fileSizeBytes,")
            append("\"sha256\":${JsonText.quote(sha256)},")
            append("\"headerPreviewHex\":${JsonText.quote(headerPreviewHex)},")
            append("\"headerPreviewAscii\":${JsonText.quote(headerPreviewAscii)},")
            append("\"versionMarker\":${JsonText.quote(versionMarker)},")
            append("\"detectedEquipment\":${JsonText.quote(detectedEquipment)},")
            append("\"estimatedPageCount\":$estimatedPageCount,")
            append("\"hasNoteMarker\":$hasNoteMarker,")
            append("\"hasMainLayer\":$hasMainLayer,")
            append("\"hasBackgroundLayer\":$hasBackgroundLayer,")
            append("\"hasLayerInfo\":$hasLayerInfo,")
            append("\"hasLayerSequence\":$hasLayerSequence,")
            append("\"hasTotalPath\":$hasTotalPath,")
            append("\"hasPageStyle\":$hasPageStyle,")
            append("\"hasTitleMetadata\":$hasTitleMetadata,")
            append("\"hasKeywordMetadata\":$hasKeywordMetadata,")
            append("\"hasLinkMetadata\":$hasLinkMetadata,")
            append("\"hasExternalLinkInfoField\":$hasExternalLinkInfoField,")
            append("\"hasStarMetadata\":$hasStarMetadata,")
            append("\"compatibilityStatus\":${JsonText.quote(compatibilityStatus)},")
            append("\"warnings\":${JsonText.stringArray(warnings)},")
            append("\"cachedCopyPath\":${JsonText.quote(cachedCopyPath)},")
            append("\"containerReport\":${containerReport.toJson()},")
            append("\"visualReport\":${visualReport.toJson()},")
            append("\"totalPathProbeReport\":${totalPathProbeReport.toJson()},")
            append("\"strokeGeometryReport\":${strokeGeometryReport.toJson()},")
            append("\"markerHits\":[")
            append(markerHits.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }
}
