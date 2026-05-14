package io.github.piyushdaiya.vellumsync.note

data class SupernoteInspectionReport(
    val fileName: String,
    val fileSizeBytes: Long,
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
    val hasStarMetadata: Boolean,
    val compatibilityStatus: String,
    val warnings: List<String>
)