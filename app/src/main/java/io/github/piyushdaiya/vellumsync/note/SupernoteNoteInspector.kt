package io.github.piyushdaiya.vellumsync.note

import java.security.MessageDigest
import java.util.Locale

object SupernoteNoteInspector {
    private val versionRegex = Regex("SN_FILE_VER_[0-9]+")

    private val markers = listOf(
        "SN_FILE_VER_",
        "SN_FILE_VER_20230015",
        "NOTE",
        "A5X",
        "A6X",
        "A5X2",
        "A6X2",
        "PAGE",
        "PAGESTYLE",
        "MAINLAYER",
        "BGLAYER",
        "LAYERINFO",
        "LAYERSEQ",
        "TOTALPATH",
        "TITLE",
        "KEYWORD",
        "LINK",
        "STAR"
    )

    fun inspect(
        fileName: String,
        fileSizeBytes: Long,
        bytes: ByteArray,
        cachedCopyPath: String? = null
    ): SupernoteInspectionReport {
        val ascii = bytes.toAsciiLikeString()
        val markerHits = markers.map { marker -> markerHit(bytes, marker) }

        val versionMarker = versionRegex.find(ascii)?.value
        val detectedEquipment = detectEquipment(ascii)
        val estimatedPageCount = estimatePageCount(ascii)

        val hasNoteMarker = hasMarker(markerHits, "NOTE")
        val hasMainLayer = hasMarker(markerHits, "MAINLAYER")
        val hasBackgroundLayer = hasMarker(markerHits, "BGLAYER")
        val hasLayerInfo = hasMarker(markerHits, "LAYERINFO")
        val hasLayerSequence = hasMarker(markerHits, "LAYERSEQ")
        val hasTotalPath = hasMarker(markerHits, "TOTALPATH")
        val hasPageStyle = hasMarker(markerHits, "PAGESTYLE")
        val hasTitleMetadata = hasMarker(markerHits, "TITLE")
        val hasKeywordMetadata = hasMarker(markerHits, "KEYWORD")
        val hasLinkMetadata = hasMarker(markerHits, "LINK")
        val hasStarMetadata = hasMarker(markerHits, "STAR")

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
                add("No PAGE number markers were detected.")
            }
            if (hasLinkMetadata && estimatedPageCount <= 1) {
                add("LINK marker found in a small note. Review offset context to confirm whether this is real link metadata or a broad marker hit.")
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
            sha256 = sha256(bytes),
            headerPreviewHex = headerHex(bytes),
            headerPreviewAscii = headerAscii(bytes),
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
            markerHits = markerHits,
            compatibilityStatus = compatibilityStatus,
            warnings = warnings,
            cachedCopyPath = cachedCopyPath
        )
    }

    private fun hasMarker(markerHits: List<MarkerHit>, marker: String): Boolean {
        return markerHits.firstOrNull { it.marker == marker }?.count?.let { it > 0 } == true
    }

    private fun markerHit(bytes: ByteArray, marker: String): MarkerHit {
        val markerBytes = marker.encodeToByteArray()
        val offsets = findOffsets(bytes, markerBytes)
        val contexts = offsets.take(5).map { offset -> contextAround(bytes, offset, markerBytes.size) }
        return MarkerHit(
            marker = marker,
            count = offsets.size,
            offsets = offsets.take(20),
            contexts = contexts
        )
    }

    private fun findOffsets(bytes: ByteArray, marker: ByteArray): List<Int> {
        if (marker.isEmpty() || bytes.size < marker.size) {
            return emptyList()
        }

        val offsets = mutableListOf<Int>()
        var index = 0
        while (index <= bytes.size - marker.size) {
            var matched = true
            for (markerIndex in marker.indices) {
                if (bytes[index + markerIndex] != marker[markerIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                offsets += index
                index += marker.size
            } else {
                index += 1
            }
        }
        return offsets
    }

    private fun contextAround(
        bytes: ByteArray,
        offset: Int,
        markerSize: Int
    ): String {
        val start = (offset - 32).coerceAtLeast(0)
        val end = (offset + markerSize + 64).coerceAtMost(bytes.size)
        return bytes.copyOfRange(start, end).toAsciiLikeString()
    }

    private fun detectEquipment(ascii: String): String? {
        return listOf("A5X2", "A6X2", "A5X", "A6X", "NOMAD", "MANTA")
            .firstOrNull { marker -> ascii.contains(marker, ignoreCase = true) }
    }

    private fun estimatePageCount(ascii: String): Int {
        val numberedPages = Regex("PAGE[0-9]+").findAll(ascii).map { it.value }.toSet()
        return numberedPages.size
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun headerHex(bytes: ByteArray): String {
        return bytes.take(64).joinToString(separator = " ") { byte ->
            "%02X".format(Locale.US, byte.toInt() and 0xff)
        }
    }

    private fun headerAscii(bytes: ByteArray): String {
        return bytes.take(64).toByteArray().toAsciiLikeString()
    }

    private fun ByteArray.toAsciiLikeString(): String {
        return buildString {
            this@toAsciiLikeString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(
                    if (value in 32..126) {
                        value.toChar()
                    } else {
                        '.'
                    }
                )
            }
        }
    }
}
