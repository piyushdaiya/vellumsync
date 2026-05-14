package io.github.piyushdaiya.vellumsync.note

import java.nio.charset.StandardCharsets

object SupernoteNoteInspector {
    private val versionRegex = Regex("SN_FILE_VER_[0-9]+")

    fun inspect(
        fileName: String,
        fileSizeBytes: Long,
        bytes: ByteArray
    ): SupernoteInspectionReport {
        val ascii = bytes.toString(StandardCharsets.ISO_8859_1)

        val versionMarker = versionRegex.find(ascii)?.value
        val detectedEquipment = detectEquipment(ascii)
        val estimatedPageCount = estimatePageCount(ascii)

        val hasNoteMarker = ascii.contains("NOTE")
        val hasMainLayer = ascii.contains("MAINLAYER")
        val hasBackgroundLayer = ascii.contains("BGLAYER")
        val hasLayerInfo = ascii.contains("LAYERINFO")
        val hasLayerSequence = ascii.contains("LAYERSEQ")
        val hasTotalPath = ascii.contains("TOTALPATH")
        val hasPageStyle = ascii.contains("PAGESTYLE")

        val hasTitleMetadata = ascii.contains("TITLE", ignoreCase = true)
        val hasKeywordMetadata = ascii.contains("KEYWORD", ignoreCase = true)
        val hasLinkMetadata = ascii.contains("LINK", ignoreCase = true)
        val hasStarMetadata = ascii.contains("STAR", ignoreCase = true)

        val warnings = buildList {
            if (versionMarker == null) {
                add("No SN_FILE_VER marker was detected.")
            }
            if (!hasNoteMarker) {
                add("NOTE marker was not detected.")
            }
            if (!hasTotalPath) {
                add("TOTALPATH marker was not detected; stroke-level decoding may not be possible.")
            }
            if (estimatedPageCount == 0) {
                add("No PAGE markers were detected.")
            }
        }

        val compatibilityStatus = when {
            versionMarker == "SN_FILE_VER_20230015" && hasNoteMarker ->
                "Read-only compatible candidate: Supernote NOTE file with known version marker detected."

            versionMarker != null && hasNoteMarker ->
                "Read-only candidate: Supernote NOTE file detected, but version is not yet validated."

            else ->
                "Unsupported or unknown file: Supernote NOTE markers were not confidently detected."
        }

        return SupernoteInspectionReport(
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            versionMarker = versionMarker,
            detectedEquipment = detectedEquipment,
            estimatedPageCount = estimatedPageCount,
            hasNoteMarker = hasNoteMarker,
            hasMainLayer = hasMainLayer,
            hasBackgroundLayer = hasBackgroundLayer,
            hasLayerInfo = hasLayerInfo,
            hasLayerSequence = hasLayerSequence,
            hasTotalPath = hasTotalPath,
            hasPageStyle = hasPageStyle,
            hasTitleMetadata = hasTitleMetadata,
            hasKeywordMetadata = hasKeywordMetadata,
            hasLinkMetadata = hasLinkMetadata,
            hasStarMetadata = hasStarMetadata,
            compatibilityStatus = compatibilityStatus,
            warnings = warnings
        )
    }

    private fun detectEquipment(ascii: String): String? {
        return listOf("A5X2", "A6X2", "A5X", "A6X", "NOMAD", "MANTA")
            .firstOrNull { marker -> ascii.contains(marker, ignoreCase = true) }
    }

    private fun estimatePageCount(ascii: String): Int {
        val matches = Regex("PAGE[0-9]+").findAll(ascii).map { it.value }.toSet()
        if (matches.isNotEmpty()) {
            return matches.size
        }

        return Regex("PAGE").findAll(ascii).count()
    }
}

