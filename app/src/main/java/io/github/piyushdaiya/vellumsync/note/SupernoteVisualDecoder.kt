package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText

/**
 * Read-only visual-layer probe for Supernote NOTE files.
 *
 * This keeps the repository-compatible probe implementation and only adds
 * page-level export fields sourced from the same render object used by the UI.
 */
// marker=vellumsync-ratta-rle-render-object-page-export-v0
// marker=vellumsync-ratta-rle-decoder-family-provenance-alias-v0
// marker=vellumsync-ratta-rle-decoder-family-provenance-export-v1-compile-repair
// marker=vellumsync-ratta-rle-page-report-constructor-provenance-wiring-v0
data class SupernoteVisualLayerRecord(
    val pageNumber: Int,
    val logicalLayerName: String,
    val layerRecordOffset: Int,
    val nextKnownStructuralOffset: Int?,
    val metadataRecordByteLength: Int?,
    val layerType: String?,
    val layerProtocol: String?,
    val parsedLayerName: String?,
    val layerPath: Int?,
    val layerBitmapOffset: Int?,
    val bitmapPayloadOffset: Int?,
    val estimatedCompressedPayloadEndOffset: Int?,
    val estimatedCompressedPayloadByteLength: Int?,
    val bitmapPayloadStartsBeforeLayerRecord: Boolean?,
    val bitmapPayloadShared: Boolean,
    val bitmapPayloadReuseCount: Int,
    val bitmapPayloadSharedBy: List<String>,
    val layerVectorGraphOffset: Int?,
    val layerRecognOffset: Int?,
    val recordPreviewAscii: String,
    val bitmapPayloadStatus: String,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"logicalLayerName\":${JsonText.quote(logicalLayerName)},")
            append("\"layerRecordOffset\":$layerRecordOffset,")
            append("\"nextKnownStructuralOffset\":${nextKnownStructuralOffset ?: "null"},")
            append("\"metadataRecordByteLength\":${metadataRecordByteLength ?: "null"},")
            append("\"layerType\":${JsonText.quote(layerType)},")
            append("\"layerProtocol\":${JsonText.quote(layerProtocol)},")
            append("\"parsedLayerName\":${JsonText.quote(parsedLayerName)},")
            append("\"layerPath\":${layerPath ?: "null"},")
            append("\"layerBitmapOffset\":${layerBitmapOffset ?: "null"},")
            append("\"bitmapPayloadOffset\":${bitmapPayloadOffset ?: "null"},")
            append("\"estimatedCompressedPayloadEndOffset\":${estimatedCompressedPayloadEndOffset ?: "null"},")
            append("\"estimatedCompressedPayloadByteLength\":${estimatedCompressedPayloadByteLength ?: "null"},")
            append("\"bitmapPayloadStartsBeforeLayerRecord\":${bitmapPayloadStartsBeforeLayerRecord ?: "null"},")
            append("\"bitmapPayloadShared\":$bitmapPayloadShared,")
            append("\"bitmapPayloadReuseCount\":$bitmapPayloadReuseCount,")
            append("\"bitmapPayloadSharedBy\":${JsonText.stringArray(bitmapPayloadSharedBy)},")
            append("\"layerVectorGraphOffset\":${layerVectorGraphOffset ?: "null"},")
            append("\"layerRecognOffset\":${layerRecognOffset ?: "null"},")
            append("\"recordPreviewAscii\":${JsonText.quote(recordPreviewAscii)},")
            append("\"bitmapPayloadStatus\":${JsonText.quote(bitmapPayloadStatus)},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteVisualPageReport(
    val pageNumber: Int,
    val pageStyle: String?,
    val layerSeq: String?,
    val layerRecords: List<SupernoteVisualLayerRecord>,
    val expectedPdfReference: String?,
    val previewStatus: String,
    val pageVisualWinnerPromoted: Boolean = false,
    val pageVisualWinnerSource: String? = null,
    val pageVisualFinalDecoderFamily: String? = null,
    val pageVisualPromotedFromDecoderFamily: String? = null,
    val pageVisualStatusResolved: String? = null,
    val pageVisualStatusReason: String? = null,
    val pageVisualFallbackUsed: Boolean = false,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"pageStyle\":${JsonText.quote(pageStyle)},")
            append("\"layerSeq\":${JsonText.quote(layerSeq)},")
            append("\"layerRecords\":[")
            append(layerRecords.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"expectedPdfReference\":${JsonText.quote(expectedPdfReference)},")
            append("\"previewStatus\":${JsonText.quote(previewStatus)},")
            append("\"pageVisualWinnerPromoted\":$pageVisualWinnerPromoted,")
            append("\"pageVisualWinnerSource\":${JsonText.quote(pageVisualWinnerSource)},")
        append("\"pageVisualFinalDecoderFamily\":${JsonText.quote(pageVisualFinalDecoderFamily)},")
        append("\"pageVisualPromotedFromDecoderFamily\":${JsonText.quote(pageVisualPromotedFromDecoderFamily)},")
            append("\"pageVisualStatusResolved\":${JsonText.quote(pageVisualStatusResolved)},")
            append("\"pageVisualStatusReason\":${JsonText.quote(pageVisualStatusReason)},")
            append("\"pageVisualFallbackUsed\":$pageVisualFallbackUsed,")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteSharedBitmapPayload(
    val bitmapPayloadOffset: Int,
    val reuseCount: Int,
    val usedBy: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"bitmapPayloadOffset\":$bitmapPayloadOffset,")
            append("\"reuseCount\":$reuseCount,")
            append("\"usedBy\":${JsonText.stringArray(usedBy)}")
            append("}")
        }
    }
}

data class SupernoteVisualReport(
    val formatStatus: String,
    val totalLayerRecords: Int,
    val rleLayerRecordCount: Int,
    val uniqueBitmapPayloadOffsetCount: Int,
    val sharedBitmapPayloads: List<SupernoteSharedBitmapPayload>,
    val pageReports: List<SupernoteVisualPageReport>,
    val decoderWarnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"formatStatus\":${JsonText.quote(formatStatus)},")
            append("\"totalLayerRecords\":$totalLayerRecords,")
            append("\"rleLayerRecordCount\":$rleLayerRecordCount,")
            append("\"uniqueBitmapPayloadOffsetCount\":$uniqueBitmapPayloadOffsetCount,")
            append("\"sharedBitmapPayloads\":[")
            append(sharedBitmapPayloads.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"pageReports\":[")
            append(pageReports.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"decoderWarnings\":${JsonText.stringArray(decoderWarnings)}")
            append("}")
        }
    }
}

object SupernoteVisualDecoder {
    fun decode(
        bytes: ByteArray,
        containerReport: SupernoteContainerReport,
        pdfReferenceName: String? = null
    ): SupernoteVisualReport {
        val ascii = bytes.toAsciiLikeString()
        val drafts = containerReport.pageSections.flatMap { page ->
            val layerOffsets = page.visualLayerOffsets()
            layerOffsets.map { layer ->
                decodeLayerDraft(
                    bytes = bytes,
                    ascii = ascii,
                    pageNumber = page.pageNumber,
                    logicalLayerName = layer.name,
                    layerRecordOffset = layer.offset,
                    orderedKnownOffsets = layerOffsets.map { it.offset } + listOfNotNull(page.layerOffsets.totalPathOffset)
                )
            }
        }

        val payloadUsage = drafts
            .mapNotNull { draft ->
                draft.layerBitmapOffset?.let { offset -> offset to draft.payloadUseLabel() }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val structuralOffsets = collectStructuralOffsets(
            bytes = bytes,
            containerReport = containerReport,
            drafts = drafts
        )

        val enrichedByPage = drafts
            .map { draft -> draft.enrich(bytes.size, payloadUsage, structuralOffsets) }
            .groupBy { it.pageNumber }

        val pageReports = containerReport.pageSections.map { page ->
            buildPageReport(
                bytes = bytes,
                page = page,
                layerRecords = enrichedByPage[page.pageNumber].orEmpty(),
                pdfReferenceName = pdfReferenceName
            )
        }

        val allLayers = pageReports.flatMap { it.layerRecords }
        val decoderWarnings = buildList {
            if (containerReport.pageSections.isEmpty()) {
                add("No page sections were available for visual layer probing.")
            }
            val missingMain = pageReports.filter { report ->
                report.layerRecords.none { it.logicalLayerName == "MAINLAYER" }
            }
            if (missingMain.isNotEmpty()) {
                add("${missingMain.size} page(s) do not have a parsed MAINLAYER visual record.")
            }
            val nonRle = allLayers.filter { it.layerProtocol != null && it.layerProtocol != "RATTA_RLE" }
            if (nonRle.isNotEmpty()) {
                add("${nonRle.size} parsed layer record(s) use a protocol other than RATTA_RLE.")
            }
            val unresolvedPayloads = allLayers.filter { it.bitmapPayloadOffset != null && it.estimatedCompressedPayloadByteLength == null }
            if (unresolvedPayloads.isNotEmpty()) {
                add("${unresolvedPayloads.size} bitmap payload pointer(s) did not have a conservative boundary estimate.")
            }
        }

        val rleCount = allLayers.count { it.layerProtocol == "RATTA_RLE" }
        val sharedPayloads = payloadUsage
            .filter { it.value.size > 1 }
            .map { entry ->
                SupernoteSharedBitmapPayload(
                    bitmapPayloadOffset = entry.key,
                    reuseCount = entry.value.size,
                    usedBy = entry.value.sorted()
                )
            }
            .sortedBy { it.bitmapPayloadOffset }

        val status = when {
            allLayers.isEmpty() -> "No visual layer records parsed."
            rleCount == allLayers.size -> "RATTA_RLE visual layer records isolated; probe and render winner logic are unified per page."
            else -> "Visual layer records isolated with mixed/unknown protocols; probe and render winner logic are unified per page."
        }

        return SupernoteVisualReport(
            formatStatus = status,
            totalLayerRecords = allLayers.size,
            rleLayerRecordCount = rleCount,
            uniqueBitmapPayloadOffsetCount = payloadUsage.keys.size,
            sharedBitmapPayloads = sharedPayloads,
            pageReports = pageReports,
            decoderWarnings = decoderWarnings
        )
    }

    private fun buildPageReport(
        bytes: ByteArray,
        page: SupernotePageSection,
        layerRecords: List<SupernoteVisualLayerRecord>,
        pdfReferenceName: String?
    ): SupernoteVisualPageReport {
        val warnings = buildList {
            if (layerRecords.isEmpty()) {
                add("No non-zero visual layer offsets were available for this page.")
            }
            if (page.layerSeq == null) {
                add("LAYERSEQ was not parsed for this page.")
            }
            val declaredSeq = page.layerSeq.orEmpty()
            layerRecords.forEach { record ->
                if (declaredSeq.isNotBlank() && !declaredSeq.split(',').contains(record.logicalLayerName)) {
                    add("${record.logicalLayerName} has an offset but is not present in LAYERSEQ=$declaredSeq.")
                }
            }
        }
        val legacyPreviewStatus = when {
            layerRecords.isEmpty() -> "No preview available."
            layerRecords.any { it.layerProtocol == "RATTA_RLE" } -> "Payload-boundary probe only: RATTA_RLE bitmap decode is not implemented."
            else -> "Payload-boundary probe only: no decodable bitmap protocol is enabled."
        }

        val probePage = SupernoteVisualPageReport(
            pageNumber = page.pageNumber,
            pageStyle = page.pageStyle,
            layerSeq = page.layerSeq,
            layerRecords = layerRecords.sortedBy { it.layerRecordOffset },
            expectedPdfReference = pdfReferenceName,
            previewStatus = legacyPreviewStatus,
            warnings = warnings
        )
        val rendered = SupernoteRattaRleVisualLayerRenderer.renderPageFromVisualPage(bytes, probePage)
        val resolvedPreviewStatus = when (rendered.pageVisualStatusResolved) {
            "visual-layer-active" -> "Visual layer active"
            "visual-layer-unavailable" -> "Visual layer unavailable"
            else -> legacyPreviewStatus
        }

        return SupernoteVisualPageReport(
            pageNumber = probePage.pageNumber,
            pageStyle = probePage.pageStyle,
            layerSeq = probePage.layerSeq,
            layerRecords = probePage.layerRecords,
            expectedPdfReference = probePage.expectedPdfReference,
            previewStatus = resolvedPreviewStatus,
            pageVisualWinnerPromoted = rendered.pageVisualWinnerPromoted,
            pageVisualWinnerSource = rendered.pageVisualWinnerSource,
            pageVisualFinalDecoderFamily = rendered.pageVisualFinalDecoderFamily,
            pageVisualPromotedFromDecoderFamily = rendered.pageVisualPromotedFromDecoderFamily,
            pageVisualStatusResolved = rendered.pageVisualStatusResolved,
            pageVisualStatusReason = rendered.pageVisualStatusReason,
            pageVisualFallbackUsed = rendered.pageVisualFallbackUsed,
            warnings = probePage.warnings + rendered.warnings
        )
    }

    private fun collectStructuralOffsets(
        bytes: ByteArray,
        containerReport: SupernoteContainerReport,
        drafts: List<LayerRecordDraft>
    ): List<Int> {
        return buildList {
            add(0)
            add(bytes.size)
            containerReport.pageReferences.forEach { add(it.pageSectionOffset) }
            containerReport.pageSections.forEach { page ->
                add(page.pageSectionOffset)
                page.layerInfoOffset?.let(::add)
                page.layerSeqOffset?.let(::add)
                page.layerOffsets.totalPathOffset?.let(::add)
                page.layerOffsets.mainLayerOffset?.takeIf { it > 0 }?.let(::add)
                page.layerOffsets.backgroundLayerOffset?.takeIf { it > 0 }?.let(::add)
                page.layerOffsets.layer1Offset?.takeIf { it > 0 }?.let(::add)
                page.layerOffsets.layer2Offset?.takeIf { it > 0 }?.let(::add)
                page.layerOffsets.layer3Offset?.takeIf { it > 0 }?.let(::add)
            }
            drafts.forEach { draft ->
                draft.layerRecordOffset.takeIf { it >= 0 }?.let(::add)
                draft.metadataEndOffset?.let(::add)
                draft.layerBitmapOffset?.takeIf { it >= 0 }?.let(::add)
            }
        }.filter { it in 0..bytes.size }.distinct().sorted()
    }

    private fun decodeLayerDraft(
        bytes: ByteArray,
        ascii: String,
        pageNumber: Int,
        logicalLayerName: String,
        layerRecordOffset: Int,
        orderedKnownOffsets: List<Int>
    ): LayerRecordDraft {
        val safeOffset = layerRecordOffset.coerceIn(0, bytes.size)
        val nextKnownOffset = orderedKnownOffsets
            .filter { it > safeOffset }
            .minOrNull()
            ?.coerceAtMost(bytes.size)
        val probeEnd = (safeOffset + 512).coerceAtMost(bytes.size)
        val probeBytes = bytes.copyOfRange(safeOffset, probeEnd.coerceAtLeast(safeOffset))
        val probeAscii = probeBytes.toAsciiLikeString()

        val layerType = tagValue(probeAscii, "LAYERTYPE")
        val layerProtocol = tagValue(probeAscii, "LAYERPROTOCOL")
        val parsedLayerName = tagValue(probeAscii, "LAYERNAME")
        val layerPath = tagValue(probeAscii, "LAYERPATH")?.toIntOrNull()
        val bitmapOffset = tagValue(probeAscii, "LAYERBITMAP")?.toIntOrNull()
        val vectorGraphOffset = tagValue(probeAscii, "LAYERVECTORGRAPH")?.toIntOrNull()
        val recognOffset = tagValue(probeAscii, "LAYERRECOGN")?.toIntOrNull()
        val metadataEndOffset = metadataEndOffset(
            probeAscii = probeAscii,
            layerRecordOffset = safeOffset
        )
        val metadataLength = metadataEndOffset?.let { it - safeOffset }
        val recordPreview = if (metadataEndOffset != null && metadataEndOffset > safeOffset) {
            bytes.copyOfRange(safeOffset, metadataEndOffset).toAsciiLikeString()
        } else {
            probeAscii.take(180)
        }

        val warnings = buildList {
            if (layerType == null) add("LAYERTYPE was not parsed from the layer record window.")
            if (layerProtocol == null) add("LAYERPROTOCOL was not parsed from the layer record window.")
            if (parsedLayerName != null && parsedLayerName != logicalLayerName) {
                add("Parsed LAYERNAME=$parsedLayerName differs from section reference $logicalLayerName.")
            }
            if (bitmapOffset == null) {
                add("LAYERBITMAP offset was not parsed.")
            } else if (bitmapOffset !in 0 until bytes.size) {
                add("LAYERBITMAP offset $bitmapOffset is outside file bounds 0..${bytes.size - 1}.")
            }
            if (metadataEndOffset == null) add("Layer metadata end was not found; using probe window only.")
        }

        return LayerRecordDraft(
            pageNumber = pageNumber,
            logicalLayerName = logicalLayerName,
            layerRecordOffset = safeOffset,
            nextKnownStructuralOffset = nextKnownOffset,
            metadataEndOffset = metadataEndOffset,
            metadataRecordByteLength = metadataLength,
            layerType = layerType,
            layerProtocol = layerProtocol,
            parsedLayerName = parsedLayerName,
            layerPath = layerPath,
            layerBitmapOffset = bitmapOffset,
            layerVectorGraphOffset = vectorGraphOffset,
            layerRecognOffset = recognOffset,
            recordPreviewAscii = recordPreview.take(180),
            warnings = warnings
        )
    }

    private fun LayerRecordDraft.enrich(
        fileSize: Int,
        payloadUsage: Map<Int, List<String>>,
        structuralOffsets: List<Int>
    ): SupernoteVisualLayerRecord {
        val payloadOffset = layerBitmapOffset?.takeIf { it in 0 until fileSize }
        val sharedBy = payloadOffset?.let { payloadUsage[it].orEmpty().sorted() }.orEmpty()
        val payloadEnd = payloadOffset?.let { start ->
            structuralOffsets
                .filter { it > start }
                .minOrNull()
                ?.coerceAtMost(fileSize)
        }
        val estimatedLength = if (payloadOffset != null && payloadEnd != null && payloadEnd > payloadOffset) {
            payloadEnd - payloadOffset
        } else null
        val startsBeforeLayerRecord = payloadOffset?.let { it < layerRecordOffset }
        val warningsWithBoundary = buildList {
            addAll(warnings)
            if (payloadOffset != null && estimatedLength == null) {
                add("Could not estimate compressed payload boundary for LAYERBITMAP offset $payloadOffset.")
            }
            if (payloadOffset != null && payloadOffset == layerRecordOffset) {
                add("LAYERBITMAP offset equals layer record offset; confirm this file version before decode work.")
            }
            if (payloadOffset != null && startsBeforeLayerRecord == false) {
                add("LAYERBITMAP payload starts after this layer metadata record; this can happen on layered pages and should be handled by boundary probing.")
            }
        }
        val payloadStatus = when {
            payloadOffset == null -> "No valid LAYERBITMAP payload offset found."
            layerProtocol == "RATTA_RLE" -> "RATTA_RLE payload pointer isolated; bitmap decode is deferred."
            else -> "Bitmap payload pointer isolated with unsupported/unknown protocol."
        }

        return SupernoteVisualLayerRecord(
            pageNumber = pageNumber,
            logicalLayerName = logicalLayerName,
            layerRecordOffset = layerRecordOffset,
            nextKnownStructuralOffset = nextKnownStructuralOffset,
            metadataRecordByteLength = metadataRecordByteLength,
            layerType = layerType,
            layerProtocol = layerProtocol,
            parsedLayerName = parsedLayerName,
            layerPath = layerPath,
            layerBitmapOffset = layerBitmapOffset,
            bitmapPayloadOffset = payloadOffset,
            estimatedCompressedPayloadEndOffset = payloadEnd,
            estimatedCompressedPayloadByteLength = estimatedLength,
            bitmapPayloadStartsBeforeLayerRecord = startsBeforeLayerRecord,
            bitmapPayloadShared = sharedBy.size > 1,
            bitmapPayloadReuseCount = sharedBy.size,
            bitmapPayloadSharedBy = sharedBy,
            layerVectorGraphOffset = layerVectorGraphOffset,
            layerRecognOffset = layerRecognOffset,
            recordPreviewAscii = recordPreviewAscii,
            bitmapPayloadStatus = payloadStatus,
            warnings = warningsWithBoundary
        )
    }

    private fun LayerRecordDraft.payloadUseLabel(): String {
        return "page=$pageNumber layer=$logicalLayerName recordOffset=$layerRecordOffset"
    }

    private data class LayerRecordDraft(
        val pageNumber: Int,
        val logicalLayerName: String,
        val layerRecordOffset: Int,
        val nextKnownStructuralOffset: Int?,
        val metadataEndOffset: Int?,
        val metadataRecordByteLength: Int?,
        val layerType: String?,
        val layerProtocol: String?,
        val parsedLayerName: String?,
        val layerPath: Int?,
        val layerBitmapOffset: Int?,
        val layerVectorGraphOffset: Int?,
        val layerRecognOffset: Int?,
        val recordPreviewAscii: String,
        val warnings: List<String>
    )

    private data class NamedLayerOffset(
        val name: String,
        val offset: Int
    )

    private fun SupernotePageSection.visualLayerOffsets(): List<NamedLayerOffset> {
        val layers = listOfNotNull(
            layerOffsets.mainLayerOffset?.takeIf { it > 0 }?.let { NamedLayerOffset("MAINLAYER", it) },
            layerOffsets.layer1Offset?.takeIf { it > 0 }?.let { NamedLayerOffset("LAYER1", it) },
            layerOffsets.layer2Offset?.takeIf { it > 0 }?.let { NamedLayerOffset("LAYER2", it) },
            layerOffsets.layer3Offset?.takeIf { it > 0 }?.let { NamedLayerOffset("LAYER3", it) },
            layerOffsets.backgroundLayerOffset?.takeIf { it > 0 }?.let { NamedLayerOffset("BGLAYER", it) }
        )
        return layers.distinctBy { it.name }.sortedBy { it.offset }
    }

    private fun tagValue(ascii: String, tagName: String): String? {
        return Regex("<$tagName:([^>]*)>").find(ascii)?.groupValues?.getOrNull(1)
    }

    private fun metadataEndOffset(
        probeAscii: String,
        layerRecordOffset: Int
    ): Int? {
        val layerRecogn = Regex("<LAYERRECOGN:[^>]*>").find(probeAscii)
        if (layerRecogn != null) {
            return layerRecordOffset + layerRecogn.range.last + 1
        }
        val layerBitmap = Regex("<LAYERBITMAP:[^>]*>").find(probeAscii)
        return layerBitmap?.let { layerRecordOffset + it.range.last + 1 }
    }

    private fun ByteArray.toAsciiLikeString(): String {
        return buildString {
            this@toAsciiLikeString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(if (value in 32..126) value.toChar() else '.')
            }
        }
    }
}
