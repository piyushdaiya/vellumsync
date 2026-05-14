package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText

data class SupernoteHeaderFields(
    val versionMarker: String?,
    val moduleLabel: String?,
    val fileType: String?,
    val applyEquipment: String?,
    val finalOperationPage: Int?,
    val finalOperationLayer: Int?,
    val fileId: String?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"versionMarker\":${JsonText.quote(versionMarker)},")
            append("\"moduleLabel\":${JsonText.quote(moduleLabel)},")
            append("\"fileType\":${JsonText.quote(fileType)},")
            append("\"applyEquipment\":${JsonText.quote(applyEquipment)},")
            append("\"finalOperationPage\":${finalOperationPage ?: "null"},")
            append("\"finalOperationLayer\":${finalOperationLayer ?: "null"},")
            append("\"fileId\":${JsonText.quote(fileId)}")
            append("}")
        }
    }
}

data class SupernotePageReference(
    val pageNumber: Int,
    val pageSectionOffset: Int
) {
    fun toJson(): String {
        return "{\"pageNumber\":$pageNumber,\"pageSectionOffset\":$pageSectionOffset}"
    }
}

data class SupernoteLayerOffsetIndex(
    val mainLayerOffset: Int?,
    val backgroundLayerOffset: Int?,
    val layer1Offset: Int?,
    val layer2Offset: Int?,
    val layer3Offset: Int?,
    val totalPathOffset: Int?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"mainLayerOffset\":${mainLayerOffset ?: "null"},")
            append("\"backgroundLayerOffset\":${backgroundLayerOffset ?: "null"},")
            append("\"layer1Offset\":${layer1Offset ?: "null"},")
            append("\"layer2Offset\":${layer2Offset ?: "null"},")
            append("\"layer3Offset\":${layer3Offset ?: "null"},")
            append("\"totalPathOffset\":${totalPathOffset ?: "null"}")
            append("}")
        }
    }
}

data class SupernotePageSection(
    val pageNumber: Int,
    val pageSectionOffset: Int,
    val pageStyle: String?,
    val layerInfoPresent: Boolean,
    val layerInfoOffset: Int?,
    val layerSeq: String?,
    val layerSeqOffset: Int?,
    val layerOffsets: SupernoteLayerOffsetIndex,
    val recognTextOffset: Int?,
    val externalLinkInfoPresent: Boolean,
    val realLinkMetadataPresent: Boolean
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"pageSectionOffset\":$pageSectionOffset,")
            append("\"pageStyle\":${JsonText.quote(pageStyle)},")
            append("\"layerInfoPresent\":$layerInfoPresent,")
            append("\"layerInfoOffset\":${layerInfoOffset ?: "null"},")
            append("\"layerSeq\":${JsonText.quote(layerSeq)},")
            append("\"layerSeqOffset\":${layerSeqOffset ?: "null"},")
            append("\"layerOffsets\":${layerOffsets.toJson()},")
            append("\"recognTextOffset\":${recognTextOffset ?: "null"},")
            append("\"externalLinkInfoPresent\":$externalLinkInfoPresent,")
            append("\"realLinkMetadataPresent\":$realLinkMetadataPresent")
            append("}")
        }
    }
}

data class SupernoteContainerReport(
    val header: SupernoteHeaderFields,
    val pageReferences: List<SupernotePageReference>,
    val pageSections: List<SupernotePageSection>,
    val realLinkMetadataPresent: Boolean,
    val externalLinkInfoPresent: Boolean,
    val titleMetadataPresent: Boolean,
    val keywordMetadataPresent: Boolean,
    val starMetadataPresent: Boolean,
    val parserWarnings: List<String>
) {
    val pageCount: Int
        get() = pageReferences.size

    fun toJson(): String {
        return buildString {
            append("{")
            append("\"header\":${header.toJson()},")
            append("\"pageCount\":$pageCount,")
            append("\"pageReferences\":[")
            append(pageReferences.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"pageSections\":[")
            append(pageSections.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"realLinkMetadataPresent\":$realLinkMetadataPresent,")
            append("\"externalLinkInfoPresent\":$externalLinkInfoPresent,")
            append("\"titleMetadataPresent\":$titleMetadataPresent,")
            append("\"keywordMetadataPresent\":$keywordMetadataPresent,")
            append("\"starMetadataPresent\":$starMetadataPresent,")
            append("\"parserWarnings\":${JsonText.stringArray(parserWarnings)}")
            append("}")
        }
    }
}

object SupernoteContainerParser {
    private val versionRegex = Regex("SN_FILE_VER_[0-9]+")
    private val pageReferenceRegex = Regex("<PAGE([0-9]+):([0-9]+)>")

    fun parse(bytes: ByteArray): SupernoteContainerReport {
        val ascii = bytes.toAsciiLikeString()
        val header = SupernoteHeaderFields(
            versionMarker = versionRegex.find(ascii)?.value,
            moduleLabel = tagValue(ascii, "MODULE_LABEL"),
            fileType = tagValue(ascii, "FILE_TYPE"),
            applyEquipment = tagValue(ascii, "APPLY_EQUIPMENT"),
            finalOperationPage = tagValue(ascii, "FINALOPERATION_PAGE")?.toIntOrNull(),
            finalOperationLayer = tagValue(ascii, "FINALOPERATION_LAYER")?.toIntOrNull(),
            fileId = tagValue(ascii, "FILE_ID")
        )

        val pageReferences = pageReferenceRegex.findAll(ascii)
            .mapNotNull { match ->
                val pageNumber = match.groupValues[1].toIntOrNull()
                val offset = match.groupValues[2].toIntOrNull()
                if (pageNumber == null || offset == null) {
                    null
                } else {
                    SupernotePageReference(pageNumber, offset)
                }
            }
            .distinctBy { it.pageNumber }
            .sortedBy { it.pageNumber }
            .toList()

        val pageSections = pageReferences.mapIndexed { index, reference ->
            val nextOffset = pageReferences.getOrNull(index + 1)?.pageSectionOffset ?: bytes.size
            parsePageSection(
                ascii = ascii,
                reference = reference,
                sectionStart = reference.pageSectionOffset,
                sectionEnd = nextOffset.coerceAtLeast(reference.pageSectionOffset)
            )
        }

        val realLinkMetadataPresent = ascii.contains("<LINKTYPE:") ||
            ascii.contains("<LINKBITMAP:") ||
            ascii.contains("<LINKRECT:") ||
            ascii.contains("<LINKTIMESTAMP:")

        val externalLinkInfoPresent = ascii.contains("<EXTERNALLINKINFO:")
        val titleMetadataPresent = ascii.contains("<TITLESEQNO:") || ascii.contains("<TITLEBITMAP:")
        val keywordMetadataPresent = ascii.contains("<KEYWORDPAGE:") || ascii.contains("<KEYWORD:")
        val starMetadataPresent = ascii.contains("<FIVESTAR:") || ascii.contains("<STAR:")

        val warnings = buildList {
            if (header.versionMarker == null) {
                add("No SN_FILE_VER marker was found in the container header.")
            }
            if (header.fileType != null && header.fileType != "NOTE") {
                add("Container FILE_TYPE is ${header.fileType}, not NOTE.")
            }
            if (pageReferences.isEmpty()) {
                add("No <PAGEn:offset> page table entries were found.")
            }
            val finalPage = header.finalOperationPage
            if (finalPage != null && pageReferences.isNotEmpty() && finalPage != pageReferences.size) {
                add("FINALOPERATION_PAGE=$finalPage but parsed page table count=${pageReferences.size}.")
            }
            val missingTotalPath = pageSections.filter { it.layerOffsets.totalPathOffset == null }
            if (missingTotalPath.isNotEmpty()) {
                add("${missingTotalPath.size} parsed page section(s) do not include TOTALPATH offsets.")
            }
        }

        return SupernoteContainerReport(
            header = header,
            pageReferences = pageReferences,
            pageSections = pageSections,
            realLinkMetadataPresent = realLinkMetadataPresent,
            externalLinkInfoPresent = externalLinkInfoPresent,
            titleMetadataPresent = titleMetadataPresent,
            keywordMetadataPresent = keywordMetadataPresent,
            starMetadataPresent = starMetadataPresent,
            parserWarnings = warnings
        )
    }

    private fun parsePageSection(
        ascii: String,
        reference: SupernotePageReference,
        sectionStart: Int,
        sectionEnd: Int
    ): SupernotePageSection {
        val safeStart = sectionStart.coerceIn(0, ascii.length)
        val safeEnd = sectionEnd.coerceIn(safeStart, ascii.length)
        val section = ascii.substring(safeStart, safeEnd)

        val pageStyle = tagValue(section, "PAGESTYLE")
        val layerInfoLocalOffset = tagStartOffset(section, "LAYERINFO")
        val layerSeqLocalOffset = tagStartOffset(section, "LAYERSEQ")
        val layerSeq = tagValue(section, "LAYERSEQ")

        val realLinkMetadataPresent = section.contains("<LINKTYPE:") ||
            section.contains("<LINKBITMAP:") ||
            section.contains("<LINKRECT:") ||
            section.contains("<LINKTIMESTAMP:")

        return SupernotePageSection(
            pageNumber = reference.pageNumber,
            pageSectionOffset = reference.pageSectionOffset,
            pageStyle = pageStyle,
            layerInfoPresent = layerInfoLocalOffset != null,
            layerInfoOffset = layerInfoLocalOffset?.let { safeStart + it },
            layerSeq = layerSeq,
            layerSeqOffset = layerSeqLocalOffset?.let { safeStart + it },
            layerOffsets = SupernoteLayerOffsetIndex(
                mainLayerOffset = tagValue(section, "MAINLAYER")?.toIntOrNull(),
                backgroundLayerOffset = tagValue(section, "BGLAYER")?.toIntOrNull(),
                layer1Offset = tagValue(section, "LAYER1")?.toIntOrNull(),
                layer2Offset = tagValue(section, "LAYER2")?.toIntOrNull(),
                layer3Offset = tagValue(section, "LAYER3")?.toIntOrNull(),
                totalPathOffset = tagValue(section, "TOTALPATH")?.toIntOrNull()
            ),
            recognTextOffset = tagValue(section, "RECOGNTEXT")?.toIntOrNull(),
            externalLinkInfoPresent = section.contains("<EXTERNALLINKINFO:"),
            realLinkMetadataPresent = realLinkMetadataPresent
        )
    }

    private fun tagValue(ascii: String, tagName: String): String? {
        return Regex("<$tagName:([^>]*)>").find(ascii)?.groupValues?.getOrNull(1)
    }

    private fun tagStartOffset(ascii: String, tagName: String): Int? {
        return Regex("<$tagName:").find(ascii)?.range?.first
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
