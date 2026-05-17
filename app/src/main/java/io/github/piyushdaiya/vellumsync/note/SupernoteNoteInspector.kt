package io.github.piyushdaiya.vellumsync.note

import java.security.MessageDigest
import java.util.Locale

enum class SupernoteInspectionDepth {
    FULL_DIAGNOSTICS,
    VIEWER_FAST
}

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
        "EXTERNALLINKINFO",
        "LINKTYPE",
        "LINKBITMAP",
        "LINKRECT",
        "LINK",
        "FIVESTAR",
        "STAR"
    )

    fun inspect(
        fileName: String,
        fileSizeBytes: Long,
        bytes: ByteArray,
        cachedCopyPath: String? = null,
        openedSourceKind: String = "note",
        openedSourcePath: String? = cachedCopyPath,
        cacheNotePresent: Boolean = cachedCopyPath != null,
        cacheFailureReason: String? = null,
        depth: SupernoteInspectionDepth = SupernoteInspectionDepth.FULL_DIAGNOSTICS
    ): SupernoteInspectionReport {
        val fullDiagnostics = depth == SupernoteInspectionDepth.FULL_DIAGNOSTICS
        val ascii = if (fullDiagnostics) bytes.toAsciiLikeString() else ""
        val containerReport = SupernoteContainerParser.parse(bytes)
        val markerHits = if (fullDiagnostics) markers.map { marker -> markerHit(bytes, marker) } else emptyList()
        val visualReport = if (fullDiagnostics) {
            SupernoteVisualDecoder.decode(bytes, containerReport)
        } else {
            SupernoteVisualDecoder.skippedForViewer(containerReport)
        }
        val totalPathProbeReport = SupernoteTotalPathStrokeProbe.probe(
            bytes = bytes,
            containerReport = containerReport,
            includeDiagnostics = fullDiagnostics
        )
        val baseStrokeGeometryReport = SupernoteStrokeGeometryDecoder.decode(totalPathProbeReport)
        val strokeGeometryReport = if (fullDiagnostics) {
            SupernoteTotalPathErasureMismatchGate.enrich(
                strokeGeometryReport = baseStrokeGeometryReport,
                bytes = bytes,
                visualReport = visualReport
            )
        } else {
            baseStrokeGeometryReport.copy(
                formatStatus = "Viewer-fast vector render model built without visual-layer diagnostics.",
                warnings = baseStrokeGeometryReport.warnings + "Viewer open skipped visual-layer probing and erasure mismatch diagnostics; Export note diagnostics JSON still runs the full parser."
            )
        }
        val totalPathStructuralReport = if (fullDiagnostics) {
            SupernoteTotalPathStructuralParser.parse(
                bytes = bytes,
                containerReport = containerReport,
                probeReport = totalPathProbeReport,
                pageNumberFilter = 1
            )
        } else {
            skippedStructuralReport()
        }

        val versionMarker = containerReport.header.versionMarker ?: if (fullDiagnostics) versionRegex.find(ascii)?.value else null
        val detectedEquipment = containerReport.header.applyEquipment ?: if (fullDiagnostics) detectEquipment(ascii) else null
        val estimatedPageCount = containerReport.pageCount

        val hasNoteMarker = containerReport.header.fileType == "NOTE" || hasMarker(markerHits, "NOTE")
        val hasMainLayer = containerReport.pageSections.any { it.layerOffsets.mainLayerOffset != null } || hasMarker(markerHits, "MAINLAYER")
        val hasBackgroundLayer = containerReport.pageSections.any { it.layerOffsets.backgroundLayerOffset != null } || hasMarker(markerHits, "BGLAYER")
        val hasLayerInfo = containerReport.pageSections.any { it.layerInfoPresent } || hasMarker(markerHits, "LAYERINFO")
        val hasLayerSequence = containerReport.pageSections.any { it.layerSeq != null } || hasMarker(markerHits, "LAYERSEQ")
        val hasTotalPath = containerReport.pageSections.any { it.layerOffsets.totalPathOffset != null } || hasMarker(markerHits, "TOTALPATH")
        val hasPageStyle = containerReport.pageSections.any { it.pageStyle != null } || hasMarker(markerHits, "PAGESTYLE")
        val hasTitleMetadata = containerReport.titleMetadataPresent
        val hasKeywordMetadata = containerReport.keywordMetadataPresent
        val hasExternalLinkInfoField = containerReport.externalLinkInfoPresent
        val hasLinkMetadata = containerReport.realLinkMetadataPresent
        val hasStarMetadata = containerReport.starMetadataPresent

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
                add("No <PAGEn:offset> page table entries were detected.")
            }
            if (!hasLinkMetadata && hasExternalLinkInfoField) {
                add("EXTERNALLINKINFO field is present, but no real link metadata markers were detected.")
            }
            if (!fullDiagnostics) {
                add("Viewer-fast open skipped marker-sweep and structural diagnostics; use Export note diagnostics JSON for the full diagnostic report.")
            }
            addAll(containerReport.parserWarnings)
        }.distinct()

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
            hasExternalLinkInfoField = hasExternalLinkInfoField,
            hasStarMetadata = hasStarMetadata,
            markerHits = markerHits,
            containerReport = containerReport,
            visualReport = visualReport,
            totalPathProbeReport = totalPathProbeReport,
            strokeGeometryReport = strokeGeometryReport,
            totalPathStructuralReport = totalPathStructuralReport,
            compatibilityStatus = compatibilityStatus,
            warnings = warnings,
            cachedCopyPath = cachedCopyPath,
            openedSourceKind = openedSourceKind,
            openedSourcePath = openedSourcePath,
            cacheNotePresent = cacheNotePresent,
            cacheFailureReason = cacheFailureReason
        )
    }

    fun inspectForViewer(
        fileName: String,
        fileSizeBytes: Long,
        bytes: ByteArray,
        cachedCopyPath: String? = null,
        openedSourceKind: String = "note",
        openedSourcePath: String? = cachedCopyPath,
        cacheNotePresent: Boolean = cachedCopyPath != null,
        cacheFailureReason: String? = null
    ): SupernoteInspectionReport {
        return inspect(
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            bytes = bytes,
            cachedCopyPath = cachedCopyPath,
            openedSourceKind = openedSourceKind,
            openedSourcePath = openedSourcePath,
            cacheNotePresent = cacheNotePresent,
            cacheFailureReason = cacheFailureReason,
            depth = SupernoteInspectionDepth.VIEWER_FAST
        )
    }

    private fun skippedStructuralReport(): SupernoteTotalPathStructuralReport {
        return SupernoteTotalPathStructuralReport(
            format = "VellumSync SNLib-guided TOTALPATH structural parser report",
            parserModel = "skipped-for-viewer-fast-open",
            selectedPageNumber = null,
            pageReports = emptyList(),
            warnings = listOf("Structural diagnostics skipped during viewer open for faster note loading. Export diagnostics to run the full structural parser.")
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
