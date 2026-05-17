package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val TOTAL_PATH_PREVIEW_BYTES = 128
private const val MAX_MARKER_CONTEXTS = 5
private const val MAX_NUMERIC_RUNS_PER_PAGE = 10
private const val RECORD_CATEGORY_MARKER_OFFSET_HINT = 60
// Feature-rich Supernote pages can contain many small TOTALPATH records (for example
// one record per pen/marker segment). 64 was enough for early smoke notes, but it
// truncated sync-test.note and made only the left/top portion of the page render.
// Keep a bounded window for performance, but allow enough records for realistic
// one-page Supernote notes.
private const val MAX_RECORD_REPORTS_PER_PAGE = 512
private const val MAX_POINT_PREVIEW_PAIRS = 16
private const val MAX_POINT_TAIL_PAIRS = 6
private const val POINT_COUNT_OFFSET_FROM_CATEGORY_MARKER = 164

data class SupernoteTotalPathMarkerHit(
    val marker: String,
    val count: Int,
    val relativeOffsets: List<Int>,
    val absoluteOffsets: List<Int>,
    val contexts: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"marker\":${JsonText.quote(marker)},")
            append("\"count\":$count,")
            append("\"relativeOffsets\":[${relativeOffsets.joinToString(separator = ",")}],")
            append("\"absoluteOffsets\":[${absoluteOffsets.joinToString(separator = ",")}],")
            append("\"contexts\":${JsonText.stringArray(contexts)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathNumericRun(
    val encoding: String,
    val absoluteOffset: Int,
    val relativeOffset: Int,
    val valueCount: Int,
    val minValue: Long,
    val maxValue: Long,
    val previewValues: List<Long>,
    val reason: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"encoding\":${JsonText.quote(encoding)},")
            append("\"absoluteOffset\":$absoluteOffset,")
            append("\"relativeOffset\":$relativeOffset,")
            append("\"valueCount\":$valueCount,")
            append("\"minValue\":$minValue,")
            append("\"maxValue\":$maxValue,")
            append("\"previewValues\":[${previewValues.joinToString(separator = ",")}],")
            append("\"reason\":${JsonText.quote(reason)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathBinarySummary(
    val zeroByteCount: Int,
    val printableAsciiByteCount: Int,
    val distinctByteCount: Int,
    val likelyBinary: Boolean
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"zeroByteCount\":$zeroByteCount,")
            append("\"printableAsciiByteCount\":$printableAsciiByteCount,")
            append("\"distinctByteCount\":$distinctByteCount,")
            append("\"likelyBinary\":$likelyBinary")
            append("}")
        }
    }
}

data class SupernoteTotalPathBoundsProbe(
    val sourceRelativeOffset: Int,
    val sourceAbsoluteOffset: Int,
    val values: List<Long>,
    val interpretation: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"sourceRelativeOffset\":$sourceRelativeOffset,")
            append("\"sourceAbsoluteOffset\":$sourceAbsoluteOffset,")
            append("\"values\":[${values.joinToString(separator = ",")}],")
            append("\"interpretation\":${JsonText.quote(interpretation)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathPointRunProbe(
    val encoding: String,
    val absoluteOffset: Int,
    val relativeOffsetInPayload: Int,
    val relativeOffsetInRecord: Int,
    val pairCount: Int,
    val previewPairs: List<List<Long>>,
    val reason: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"encoding\":${JsonText.quote(encoding)},")
            append("\"absoluteOffset\":$absoluteOffset,")
            append("\"relativeOffsetInPayload\":$relativeOffsetInPayload,")
            append("\"relativeOffsetInRecord\":$relativeOffsetInRecord,")
            append("\"pairCount\":$pairCount,")
            append("\"previewPairs\":[")
            append(previewPairs.joinToString(separator = ",") { pair -> "[${pair.joinToString(separator = ",")}]" })
            append("],")
            append("\"reason\":${JsonText.quote(reason)}")
            append("}")
        }
    }
}

data class SupernoteRawPoint(
    val x: Long,
    val y: Long
) {
    fun toJson(): String = "{\"x\":$x,\"y\":$y}"
}

data class SupernoteTotalPathPointArrayDecode(
    val status: String,
    val encoding: String,
    val pointCountFieldRelativeOffsetInRecord: Int,
    val pointCountFieldAbsoluteOffset: Int,
    val pointArrayRelativeOffsetInRecord: Int,
    val pointArrayRelativeOffsetInPayload: Int,
    val pointArrayAbsoluteOffset: Int,
    val declaredPointCount: Int,
    val decodedPointCount: Int,
    val minX: Long?,
    val maxX: Long?,
    val minY: Long?,
    val maxY: Long?,
    /**
     * Complete decoded point array retained for internal render/write-back alignment.
     * Diagnostics still serialize only preview/tail samples so JSON exports stay compact.
     */
    val rawPoints: List<SupernoteRawPoint> = emptyList(),
    val rawPointPreview: List<SupernoteRawPoint>,
    val rawPointTailPreview: List<SupernoteRawPoint>,
    val interpretation: String,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"status\":${JsonText.quote(status)},")
            append("\"encoding\":${JsonText.quote(encoding)},")
            append("\"pointCountFieldRelativeOffsetInRecord\":$pointCountFieldRelativeOffsetInRecord,")
            append("\"pointCountFieldAbsoluteOffset\":$pointCountFieldAbsoluteOffset,")
            append("\"pointArrayRelativeOffsetInRecord\":$pointArrayRelativeOffsetInRecord,")
            append("\"pointArrayRelativeOffsetInPayload\":$pointArrayRelativeOffsetInPayload,")
            append("\"pointArrayAbsoluteOffset\":$pointArrayAbsoluteOffset,")
            append("\"declaredPointCount\":$declaredPointCount,")
            append("\"decodedPointCount\":$decodedPointCount,")
            append("\"minX\":${minX ?: "null"},")
            append("\"maxX\":${maxX ?: "null"},")
            append("\"minY\":${minY ?: "null"},")
            append("\"maxY\":${maxY ?: "null"},")
            append("\"rawPointPreview\":[")
            append(rawPointPreview.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"rawPointTailPreview\":[")
            append(rawPointTailPreview.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"interpretation\":${JsonText.quote(interpretation)},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathRecordBoundary(
    val recordIndex: Int,
    val category: String,
    val categoryMarkerRelativeOffset: Int,
    val categoryMarkerAbsoluteOffset: Int,
    val estimatedRecordStartRelativeOffset: Int,
    val estimatedRecordStartAbsoluteOffset: Int,
    val estimatedRecordEndRelativeOffset: Int,
    val estimatedRecordEndAbsoluteOffset: Int,
    val estimatedRecordByteLength: Int,
    val declaredRecordPayloadSize: Long?,
    val recordLengthSource: String,
    val decodedByLengthChain: Boolean,
    val firstU32LeFields: List<Long>,
    val candidateBounds: SupernoteTotalPathBoundsProbe?,
    val candidatePointRun: SupernoteTotalPathPointRunProbe?,
    val decodedPointArray: SupernoteTotalPathPointArrayDecode?,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"recordIndex\":$recordIndex,")
            append("\"category\":${JsonText.quote(category)},")
            append("\"categoryMarkerRelativeOffset\":$categoryMarkerRelativeOffset,")
            append("\"categoryMarkerAbsoluteOffset\":$categoryMarkerAbsoluteOffset,")
            append("\"estimatedRecordStartRelativeOffset\":$estimatedRecordStartRelativeOffset,")
            append("\"estimatedRecordStartAbsoluteOffset\":$estimatedRecordStartAbsoluteOffset,")
            append("\"estimatedRecordEndRelativeOffset\":$estimatedRecordEndRelativeOffset,")
            append("\"estimatedRecordEndAbsoluteOffset\":$estimatedRecordEndAbsoluteOffset,")
            append("\"estimatedRecordByteLength\":$estimatedRecordByteLength,")
            append("\"declaredRecordPayloadSize\":${declaredRecordPayloadSize ?: "null"},")
            append("\"recordLengthSource\":${JsonText.quote(recordLengthSource)},")
            append("\"decodedByLengthChain\":$decodedByLengthChain,")
            append("\"firstU32LeFields\":[${firstU32LeFields.joinToString(separator = ",")}],")
            append("\"candidateBounds\":${candidateBounds?.toJson() ?: "null"},")
            append("\"candidatePointRun\":${candidatePointRun?.toJson() ?: "null"},")
            append("\"decodedPointArray\":${decodedPointArray?.toJson() ?: "null"},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathPageReport(
    val pageNumber: Int,
    val totalPathOffset: Int?,
    val pageSectionOffset: Int,
    val estimatedPayloadStartOffset: Int?,
    val estimatedPayloadEndOffset: Int?,
    val estimatedPayloadByteLength: Int?,
    val declaredPayloadSize: Long?,
    val declaredRecordCount: Int?,
    val headerSizeMatchesPayload: Boolean?,
    val semanticRecordMarkerCount: Int,
    val recordCountMatchesSemanticMarkers: Boolean?,
    val recordBoundaryModelStatus: String,
    val recordChainDecoderStatus: String,
    val pointArrayDecodeStatus: String,
    val recordsDecodedByLengthChain: Int,
    val recordsWithDecodedPointArrays: Int,
    val candidateRecords: List<SupernoteTotalPathRecordBoundary>,
    val firstPreviewHex: String,
    val firstPreviewAscii: String,
    val markerHits: List<SupernoteTotalPathMarkerHit>,
    val numericRuns: List<SupernoteTotalPathNumericRun>,
    val binarySummary: SupernoteTotalPathBinarySummary?,
    val candidateStrokeRecordCount: Int?,
    val candidateStrokeRecordSignals: List<String>,
    val candidateToolSignals: List<String>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"totalPathOffset\":${totalPathOffset ?: "null"},")
            append("\"pageSectionOffset\":$pageSectionOffset,")
            append("\"estimatedPayloadStartOffset\":${estimatedPayloadStartOffset ?: "null"},")
            append("\"estimatedPayloadEndOffset\":${estimatedPayloadEndOffset ?: "null"},")
            append("\"estimatedPayloadByteLength\":${estimatedPayloadByteLength ?: "null"},")
            append("\"declaredPayloadSize\":${declaredPayloadSize ?: "null"},")
            append("\"declaredRecordCount\":${declaredRecordCount ?: "null"},")
            append("\"headerSizeMatchesPayload\":${headerSizeMatchesPayload ?: "null"},")
            append("\"semanticRecordMarkerCount\":$semanticRecordMarkerCount,")
            append("\"recordCountMatchesSemanticMarkers\":${recordCountMatchesSemanticMarkers ?: "null"},")
            append("\"recordBoundaryModelStatus\":${JsonText.quote(recordBoundaryModelStatus)},")
            append("\"recordChainDecoderStatus\":${JsonText.quote(recordChainDecoderStatus)},")
            append("\"pointArrayDecodeStatus\":${JsonText.quote(pointArrayDecodeStatus)},")
            append("\"recordsDecodedByLengthChain\":$recordsDecodedByLengthChain,")
            append("\"recordsWithDecodedPointArrays\":$recordsWithDecodedPointArrays,")
            append("\"candidateRecords\":[")
            append(candidateRecords.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"firstPreviewHex\":${JsonText.quote(firstPreviewHex)},")
            append("\"firstPreviewAscii\":${JsonText.quote(firstPreviewAscii)},")
            append("\"markerHits\":[")
            append(markerHits.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"numericRuns\":[")
            append(numericRuns.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"binarySummary\":${binarySummary?.toJson() ?: "null"},")
            append("\"candidateStrokeRecordCount\":${candidateStrokeRecordCount ?: "null"},")
            append("\"candidateStrokeRecordSignals\":${JsonText.stringArray(candidateStrokeRecordSignals)},")
            append("\"candidateToolSignals\":${JsonText.stringArray(candidateToolSignals)},")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathProbeReport(
    val formatStatus: String,
    val pagesWithTotalPath: Int,
    val totalEstimatedPayloadBytes: Int,
    val pageReports: List<SupernoteTotalPathPageReport>,
    val probeWarnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"formatStatus\":${JsonText.quote(formatStatus)},")
            append("\"pagesWithTotalPath\":$pagesWithTotalPath,")
            append("\"totalEstimatedPayloadBytes\":$totalEstimatedPayloadBytes,")
            append("\"pageReports\":[")
            append(pageReports.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"probeWarnings\":${JsonText.stringArray(probeWarnings)}")
            append("}")
        }
    }
}

object SupernoteTotalPathStrokeProbe {
    private val semanticMarkers = listOf(
        "TOTALPATH",
        "POINT",
        "POINTS",
        "PRESSURE",
        "PRESSURES",
        "ANGLE",
        "ANGLES",
        "PEN",
        "WIDTH",
        "THICK",
        "COLOR",
        "GRAYS",
        "straightLine",
        "others",
        "Trail",
        "TrailContainer",
        "flagDraw",
        "PointContours",
        "epaPoints",
        "epaGrays"
    )

    private val recordCategoryMarkers = listOf("straightLine", "others")

    fun probe(
        bytes: ByteArray,
        containerReport: SupernoteContainerReport
    ): SupernoteTotalPathProbeReport {
        val pageReports = containerReport.pageSections.map { page ->
            probePage(bytes = bytes, page = page)
        }
        val pagesWithTotalPath = pageReports.count { it.totalPathOffset != null }
        val totalBytes = pageReports.sumOf { it.estimatedPayloadByteLength ?: 0 }
        val warnings = buildList {
            if (containerReport.pageSections.isEmpty()) {
                add("No page sections were available for TOTALPATH probing.")
            }
            val missing = pageReports.count { it.totalPathOffset == null }
            if (missing > 0) {
                add("$missing page(s) do not expose a TOTALPATH offset.")
            }
            add("TOTALPATH Record Chain + Point Array Decode v0 is read-only; vector rendering and write-back are deferred.")
        }
        val status = when {
            pagesWithTotalPath == 0 -> "No TOTALPATH payloads detected."
            else -> "TOTALPATH record-boundary model, record-chain decoder, and point-array decode v0 built; vector rendering is deferred."
        }
        return SupernoteTotalPathProbeReport(
            formatStatus = status,
            pagesWithTotalPath = pagesWithTotalPath,
            totalEstimatedPayloadBytes = totalBytes,
            pageReports = pageReports,
            probeWarnings = warnings
        )
    }

    private fun probePage(
        bytes: ByteArray,
        page: SupernotePageSection
    ): SupernoteTotalPathPageReport {
        val totalPathOffset = page.layerOffsets.totalPathOffset
        if (totalPathOffset == null || totalPathOffset <= 0) {
            return SupernoteTotalPathPageReport(
                pageNumber = page.pageNumber,
                totalPathOffset = null,
                pageSectionOffset = page.pageSectionOffset,
                estimatedPayloadStartOffset = null,
                estimatedPayloadEndOffset = null,
                estimatedPayloadByteLength = null,
                declaredPayloadSize = null,
                declaredRecordCount = null,
                headerSizeMatchesPayload = null,
                semanticRecordMarkerCount = 0,
                recordCountMatchesSemanticMarkers = null,
                recordBoundaryModelStatus = if (totalPathOffset == null) "No TOTALPATH offset parsed for this page." else "TOTALPATH offset is zero/invalid; treating page as blank.",
                recordChainDecoderStatus = "No chain decoded because TOTALPATH is absent.",
                pointArrayDecodeStatus = "No point arrays decoded because TOTALPATH is absent.",
                recordsDecodedByLengthChain = 0,
                recordsWithDecodedPointArrays = 0,
                candidateRecords = emptyList(),
                firstPreviewHex = "",
                firstPreviewAscii = "",
                markerHits = emptyList(),
                numericRuns = emptyList(),
                binarySummary = null,
                candidateStrokeRecordCount = null,
                candidateStrokeRecordSignals = emptyList(),
                candidateToolSignals = emptyList(),
                warnings = listOf(if (totalPathOffset == null) "No TOTALPATH offset was parsed for this page." else "TOTALPATH offset is zero/invalid; treating page as blank.")
            )
        }

        val start = totalPathOffset.coerceIn(0, bytes.size)
        val end = estimateEndOffset(start, page.pageSectionOffset, bytes.size)
        val payload = bytes.copyOfRange(start, end)
        val markerHits = semanticMarkers.map { marker -> markerHit(payload, start, marker) }
        val semanticMarkersForRecords = findRecordCategoryMarkers(payload, start)
        val declaredPayloadSize = readDeclaredPayloadSize(payload)
        val declaredRecordCount = readDeclaredRecordCount(payload)
        val headerSizeMatchesPayload = declaredPayloadSize?.let { it + 4L == payload.size.toLong() }
        val semanticRecordMarkerCount = semanticMarkersForRecords.size
        val recordCountMatchesSemanticMarkers = declaredRecordCount?.let { it == semanticRecordMarkerCount }
        val candidateRecords = buildCandidateRecords(
            payload = payload,
            absolutePayloadStart = start,
            semanticMarkers = semanticMarkersForRecords,
            declaredRecordCount = declaredRecordCount
        )
        val numericRuns = findNumericRuns(payload, start)
        val binarySummary = summarizeBinary(payload)
        val candidateToolSignals = markerHits
            .filter { it.count > 0 && it.marker !in listOf("TOTALPATH", "POINT", "POINTS") }
            .map { "${it.marker}: count=${it.count}" }
        val recordsDecodedByLengthChain = candidateRecords.count { it.decodedByLengthChain }
        val recordsWithDecodedPointArrays = candidateRecords.count { it.decodedPointArray != null }
        val recordChainDecoderStatus = when {
            candidateRecords.isEmpty() -> "No candidate records decoded."
            declaredRecordCount != null && recordsDecodedByLengthChain == declaredRecordCount ->
                "Length-chain decoder produced the declared record count."
            declaredRecordCount != null ->
                "Length-chain decoder produced $recordsDecodedByLengthChain of $declaredRecordCount declared records."
            else -> "Length-chain decoder used semantic fallback because no declared count was available."
        }
        val pointArrayDecodeStatus = when {
            recordsWithDecodedPointArrays == 0 -> "No point arrays decoded."
            recordsWithDecodedPointArrays == candidateRecords.size ->
                "Point arrays decoded for all candidate records."
            else -> "Point arrays decoded for $recordsWithDecodedPointArrays of ${candidateRecords.size} candidate records."
        }
        val candidateStrokeCount = when {
            declaredRecordCount != null && declaredRecordCount >= 0 -> declaredRecordCount
            semanticRecordMarkerCount > 0 -> semanticRecordMarkerCount
            else -> null
        }
        val boundaryStatus = when {
            payload.isEmpty() -> "No payload available for boundary modeling."
            declaredRecordCount != null && recordCountMatchesSemanticMarkers == true ->
                "Declared record count matches semantic path marker count."
            declaredRecordCount != null ->
                "Declared record count parsed; length-chain decoder is authoritative when semantic marker count differs."
            semanticRecordMarkerCount > 0 ->
                "Semantic path markers found, but declared record count was not parsed."
            else -> "No reliable path-record boundary markers found."
        }
        val candidateStrokeSignals = buildList {
            if (payload.isNotEmpty()) {
                add("TOTALPATH payload exists for page ${page.pageNumber}.")
            }
            if (declaredPayloadSize != null) {
                add("Declared payload size field=$declaredPayloadSize; estimated payload length=${payload.size}.")
            }
            if (declaredRecordCount != null) {
                add("Declared candidate path-record count=$declaredRecordCount.")
            }
            add("Semantic record marker count=$semanticRecordMarkerCount.")
            if (recordCountMatchesSemanticMarkers == true) {
                add("Declared record count matches semantic marker count.")
            }
            if (numericRuns.isNotEmpty()) {
                add("${numericRuns.size} coordinate/pressure-like numeric run(s) found.")
            }
            if (candidateRecords.isNotEmpty()) {
                add("${candidateRecords.size} candidate record report(s) generated; record decode window=$MAX_RECORD_REPORTS_PER_PAGE.")
                add(recordChainDecoderStatus)
                add(pointArrayDecodeStatus)
            }
            add("Stroke vector rendering and write-back remain deferred until coordinate transform validation.")
        }
        val warnings = buildList {
            if (totalPathOffset >= page.pageSectionOffset) {
                add("TOTALPATH offset is not before the page section offset; boundary estimate may be invalid.")
            }
            if (payload.isEmpty()) {
                add("TOTALPATH payload boundary estimate produced an empty payload.")
            }
            if (declaredPayloadSize == null) {
                add("Could not parse the leading declared payload-size field.")
            } else if (headerSizeMatchesPayload == false) {
                add("Declared payload size + 4 does not match estimated payload length.")
            }
            if (declaredRecordCount != null && recordCountMatchesSemanticMarkers == false) {
                add("Declared record count does not match semantic marker count; length-chain decoder is used and semantic markers are annotations only.")
            }
            if (candidateRecords.size >= MAX_RECORD_REPORTS_PER_PAGE && declaredRecordCount != null && declaredRecordCount > MAX_RECORD_REPORTS_PER_PAGE) {
                add("TOTALPATH record decode reached MAX_RECORD_REPORTS_PER_PAGE=$MAX_RECORD_REPORTS_PER_PAGE; additional records may exist after the diagnostics/render window.")
            }
            if (numericRuns.isEmpty()) {
                add("No coordinate-like numeric runs were detected by the conservative heuristic.")
            }
        }

        return SupernoteTotalPathPageReport(
            pageNumber = page.pageNumber,
            totalPathOffset = totalPathOffset,
            pageSectionOffset = page.pageSectionOffset,
            estimatedPayloadStartOffset = start,
            estimatedPayloadEndOffset = end,
            estimatedPayloadByteLength = end - start,
            declaredPayloadSize = declaredPayloadSize,
            declaredRecordCount = declaredRecordCount,
            headerSizeMatchesPayload = headerSizeMatchesPayload,
            semanticRecordMarkerCount = semanticRecordMarkerCount,
            recordCountMatchesSemanticMarkers = recordCountMatchesSemanticMarkers,
            recordBoundaryModelStatus = boundaryStatus,
            recordChainDecoderStatus = recordChainDecoderStatus,
            pointArrayDecodeStatus = pointArrayDecodeStatus,
            recordsDecodedByLengthChain = recordsDecodedByLengthChain,
            recordsWithDecodedPointArrays = recordsWithDecodedPointArrays,
            candidateRecords = candidateRecords,
            firstPreviewHex = previewHex(payload),
            firstPreviewAscii = previewAscii(payload),
            markerHits = markerHits,
            numericRuns = numericRuns,
            binarySummary = binarySummary,
            candidateStrokeRecordCount = candidateStrokeCount,
            candidateStrokeRecordSignals = candidateStrokeSignals,
            candidateToolSignals = candidateToolSignals,
            warnings = warnings
        )
    }

    private fun estimateEndOffset(
        totalPathOffset: Int,
        pageSectionOffset: Int,
        fileSize: Int
    ): Int {
        val boundedPageSection = pageSectionOffset.coerceIn(0, fileSize)
        return when {
            totalPathOffset < boundedPageSection -> boundedPageSection
            totalPathOffset < fileSize -> fileSize
            else -> totalPathOffset.coerceIn(0, fileSize)
        }
    }

    private fun readDeclaredPayloadSize(payload: ByteArray): Long? {
        return if (payload.size >= 4) u32le(payload, 0) else null
    }

    private fun readDeclaredRecordCount(payload: ByteArray): Int? {
        if (payload.size < 8) return null
        val value = u32le(payload, 4)
        return if (value in 0..10_000L) value.toInt() else null
    }

    private data class CategoryMarker(
        val category: String,
        val relativeOffset: Int,
        val absoluteOffset: Int
    )

    private fun findRecordCategoryMarkers(
        payload: ByteArray,
        absolutePayloadStart: Int
    ): List<CategoryMarker> {
        val markers = mutableListOf<CategoryMarker>()
        recordCategoryMarkers.forEach { category ->
            findOffsets(payload, category.encodeToByteArray()).forEach { relativeOffset ->
                markers += CategoryMarker(
                    category = category,
                    relativeOffset = relativeOffset,
                    absoluteOffset = absolutePayloadStart + relativeOffset
                )
            }
        }
        return markers.sortedBy { it.relativeOffset }
    }

    private fun buildCandidateRecords(
        payload: ByteArray,
        absolutePayloadStart: Int,
        semanticMarkers: List<CategoryMarker>,
        declaredRecordCount: Int?
    ): List<SupernoteTotalPathRecordBoundary> {
        if (payload.isEmpty()) return emptyList()

        val chainRecords = if (declaredRecordCount != null && declaredRecordCount > 0) {
            buildLengthChainRecords(
                payload = payload,
                absolutePayloadStart = absolutePayloadStart,
                declaredRecordCount = declaredRecordCount,
                semanticMarkers = semanticMarkers
            )
        } else {
            emptyList()
        }

        if (chainRecords.isNotEmpty()) {
            return chainRecords
        }

        if (semanticMarkers.isEmpty()) return emptyList()
        val starts = semanticMarkers.map { marker ->
            (marker.relativeOffset - RECORD_CATEGORY_MARKER_OFFSET_HINT).coerceAtLeast(0)
        }
        return semanticMarkers.take(MAX_RECORD_REPORTS_PER_PAGE).mapIndexed { index, marker ->
            val startRelative = starts[index]
            val endRelative = if (index + 1 < starts.size) {
                starts[index + 1].coerceIn(startRelative, payload.size)
            } else {
                payload.size
            }
            buildCandidateRecord(
                payload = payload,
                absolutePayloadStart = absolutePayloadStart,
                recordIndex = index,
                category = marker.category,
                categoryMarkerRelativeOffset = marker.relativeOffset,
                categoryMarkerAbsoluteOffset = marker.absoluteOffset,
                startRelative = startRelative,
                endRelative = endRelative,
                declaredRecordPayloadSize = null,
                recordLengthSource = "semantic-marker-fallback",
                decodedByLengthChain = false
            )
        }
    }

    private fun buildLengthChainRecords(
        payload: ByteArray,
        absolutePayloadStart: Int,
        declaredRecordCount: Int,
        semanticMarkers: List<CategoryMarker>
    ): List<SupernoteTotalPathRecordBoundary> {
        val records = mutableListOf<SupernoteTotalPathRecordBoundary>()
        var cursor = 0
        var index = 0
        while (index < declaredRecordCount && index < MAX_RECORD_REPORTS_PER_PAGE && cursor < payload.size) {
            if (cursor + 12 > payload.size) {
                break
            }
            val declaredRecordPayloadSize = u32le(payload, cursor + 8)
            val proposedLength = declaredRecordPayloadSize + 4L
            val safeLength = when {
                proposedLength <= 0L -> payload.size - cursor
                proposedLength > Int.MAX_VALUE -> payload.size - cursor
                cursor + proposedLength.toInt() > payload.size -> payload.size - cursor
                index == declaredRecordCount - 1 -> payload.size - cursor
                else -> proposedLength.toInt()
            }.coerceAtLeast(0)
            val endRelative = (cursor + safeLength).coerceIn(cursor, payload.size)
            val marker = semanticMarkers.firstOrNull { it.relativeOffset in cursor until endRelative }
            val category = marker?.category ?: inferCategoryFromRecord(payload, cursor, endRelative)
            val categoryRelativeOffset = marker?.relativeOffset ?: findCategoryMarkerRelativeOffsetInRecord(payload, cursor, endRelative, category)
            records += buildCandidateRecord(
                payload = payload,
                absolutePayloadStart = absolutePayloadStart,
                recordIndex = index,
                category = category,
                categoryMarkerRelativeOffset = categoryRelativeOffset,
                categoryMarkerAbsoluteOffset = if (categoryRelativeOffset >= 0) absolutePayloadStart + categoryRelativeOffset else -1,
                startRelative = cursor,
                endRelative = endRelative,
                declaredRecordPayloadSize = declaredRecordPayloadSize,
                recordLengthSource = if (index == declaredRecordCount - 1 && cursor + proposedLength.toIntOrNullSafe() != payload.size) {
                    "length-field-plus-tail"
                } else {
                    "length-field-plus-four"
                },
                decodedByLengthChain = true
            )
            if (endRelative <= cursor) break
            cursor = endRelative
            index += 1
        }
        return records
    }

    private fun Long.toIntOrNullSafe(): Int {
        return if (this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) this.toInt() else Int.MAX_VALUE
    }

    private fun inferCategoryFromRecord(payload: ByteArray, startRelative: Int, endRelative: Int): String {
        val record = payload.copyOfRange(startRelative, endRelative.coerceIn(startRelative, payload.size))
        return when {
            findOffsets(record, "straightLine".encodeToByteArray()).isNotEmpty() -> "straightLine"
            findOffsets(record, "others".encodeToByteArray()).isNotEmpty() -> "others"
            else -> "unknown"
        }
    }

    private fun findCategoryMarkerRelativeOffsetInRecord(
        payload: ByteArray,
        startRelative: Int,
        endRelative: Int,
        category: String
    ): Int {
        if (category == "unknown") return -1
        val record = payload.copyOfRange(startRelative, endRelative.coerceIn(startRelative, payload.size))
        val local = findOffsets(record, category.encodeToByteArray()).firstOrNull() ?: return -1
        return startRelative + local
    }

    private fun buildCandidateRecord(
        payload: ByteArray,
        absolutePayloadStart: Int,
        recordIndex: Int,
        category: String,
        categoryMarkerRelativeOffset: Int,
        categoryMarkerAbsoluteOffset: Int,
        startRelative: Int,
        endRelative: Int,
        declaredRecordPayloadSize: Long?,
        recordLengthSource: String,
        decodedByLengthChain: Boolean
    ): SupernoteTotalPathRecordBoundary {
        val safeStart = startRelative.coerceIn(0, payload.size)
        val safeEnd = endRelative.coerceIn(safeStart, payload.size)
        val record = payload.copyOfRange(safeStart, safeEnd)
        val firstFields = firstU32LeFields(record)
        val bounds = candidateBoundsProbe(record, absolutePayloadStart, safeStart)
        val decodedPointArray = decodePointArray(
            record = record,
            absolutePayloadStart = absolutePayloadStart,
            recordStartRelative = safeStart,
            categoryMarkerRelativeOffsetInRecord = if (categoryMarkerRelativeOffset >= safeStart) {
                categoryMarkerRelativeOffset - safeStart
            } else {
                null
            }
        )
        val pointRun = decodedPointArray?.toPointRunProbe() ?: findBestPointRun(record, absolutePayloadStart, safeStart)
        val warnings = buildList {
            if (category == "unknown") {
                add("No known category marker was found inside this record; length-chain boundary is still used.")
            }
            if (!decodedByLengthChain && categoryMarkerRelativeOffset < RECORD_CATEGORY_MARKER_OFFSET_HINT) {
                add("Category marker appears earlier than the expected header offset; record start was clamped to payload start.")
            }
            if (safeEnd <= safeStart) {
                add("Candidate record has empty or negative boundary.")
            }
            if (decodedPointArray == null && pointRun == null) {
                add("No plausible point array or fallback u32le point-pair run was found inside this candidate record.")
            }
        }
        return SupernoteTotalPathRecordBoundary(
            recordIndex = recordIndex,
            category = category,
            categoryMarkerRelativeOffset = categoryMarkerRelativeOffset,
            categoryMarkerAbsoluteOffset = categoryMarkerAbsoluteOffset,
            estimatedRecordStartRelativeOffset = safeStart,
            estimatedRecordStartAbsoluteOffset = absolutePayloadStart + safeStart,
            estimatedRecordEndRelativeOffset = safeEnd,
            estimatedRecordEndAbsoluteOffset = absolutePayloadStart + safeEnd,
            estimatedRecordByteLength = safeEnd - safeStart,
            declaredRecordPayloadSize = declaredRecordPayloadSize,
            recordLengthSource = recordLengthSource,
            decodedByLengthChain = decodedByLengthChain,
            firstU32LeFields = firstFields,
            candidateBounds = bounds,
            candidatePointRun = pointRun,
            decodedPointArray = decodedPointArray,
            warnings = warnings
        )
    }

    private fun firstU32LeFields(record: ByteArray): List<Long> {
        val values = mutableListOf<Long>()
        var offset = 0
        while (offset + 3 < record.size && values.size < 16) {
            values += u32le(record, offset)
            offset += 4
        }
        return values
    }

    private fun candidateBoundsProbe(
        record: ByteArray,
        absolutePayloadStart: Int,
        recordStartRelative: Int
    ): SupernoteTotalPathBoundsProbe? {
        val boundsOffset = 108
        if (boundsOffset + 15 >= record.size) return null
        val values = listOf(
            u32le(record, boundsOffset),
            u32le(record, boundsOffset + 4),
            u32le(record, boundsOffset + 8),
            u32le(record, boundsOffset + 12)
        )
        return SupernoteTotalPathBoundsProbe(
            sourceRelativeOffset = recordStartRelative + boundsOffset,
            sourceAbsoluteOffset = absolutePayloadStart + recordStartRelative + boundsOffset,
            values = values,
            interpretation = "Provisional four-field bounds/anchor probe at record-relative offset 108."
        )
    }

    private fun decodePointArray(
        record: ByteArray,
        absolutePayloadStart: Int,
        recordStartRelative: Int,
        categoryMarkerRelativeOffsetInRecord: Int?
    ): SupernoteTotalPathPointArrayDecode? {
        val candidates = mutableListOf<SupernoteTotalPathPointArrayDecode>()
        val preferredOffsets = buildList {
            if (categoryMarkerRelativeOffsetInRecord != null) {
                add(categoryMarkerRelativeOffsetInRecord + POINT_COUNT_OFFSET_FROM_CATEGORY_MARKER)
            }
            add(224)
        }.distinct()

        preferredOffsets.forEach { offset ->
            decodePointArrayAtOffset(
                record = record,
                absolutePayloadStart = absolutePayloadStart,
                recordStartRelative = recordStartRelative,
                pointCountOffset = offset,
                preferred = true
            )?.let { candidates += it }
        }

        if (candidates.isEmpty()) {
            var offset = 96
            val maxScan = min(record.size - 12, 512)
            while (offset <= maxScan) {
                decodePointArrayAtOffset(
                    record = record,
                    absolutePayloadStart = absolutePayloadStart,
                    recordStartRelative = recordStartRelative,
                    pointCountOffset = offset,
                    preferred = false
                )?.let { candidates += it }
                offset += 4
            }
        }

        return candidates
            .distinctBy { it.pointCountFieldRelativeOffsetInRecord }
            .maxWithOrNull(
                compareBy<SupernoteTotalPathPointArrayDecode> { it.decodedPointCount }
                    .thenByDescending { if (it.pointCountFieldRelativeOffsetInRecord in preferredOffsets) 1 else 0 }
                    .thenBy { -it.pointCountFieldRelativeOffsetInRecord }
            )
    }

    private fun decodePointArrayAtOffset(
        record: ByteArray,
        absolutePayloadStart: Int,
        recordStartRelative: Int,
        pointCountOffset: Int,
        preferred: Boolean
    ): SupernoteTotalPathPointArrayDecode? {
        if (pointCountOffset < 0 || pointCountOffset + 12 > record.size) return null
        val declaredPointCountLong = u32le(record, pointCountOffset)
        if (declaredPointCountLong !in 1L..10_000L) return null
        val declaredPointCount = declaredPointCountLong.toInt()
        val pointArrayOffset = pointCountOffset + 4
        val requiredBytes = declaredPointCount.toLong() * 8L
        if (pointArrayOffset + requiredBytes > record.size.toLong()) return null

        val points = mutableListOf<SupernoteRawPoint>()
        var cursor = pointArrayOffset
        repeat(declaredPointCount) {
            val x = u32le(record, cursor)
            val y = u32le(record, cursor + 4)
            if (!isPlausiblePointPair(x, y)) {
                return null
            }
            points += SupernoteRawPoint(x = x, y = y)
            cursor += 8
        }
        if (points.isEmpty()) return null

        val distinct = points.toSet().size
        if (declaredPointCount > 3 && distinct < 2) return null

        val minX = points.minOfOrNull { it.x }
        val maxX = points.maxOfOrNull { it.x }
        val minY = points.minOfOrNull { it.y }
        val maxY = points.maxOfOrNull { it.y }
        val warnings = buildList {
            if (!preferred) {
                add("Point array found by fallback scan rather than preferred category-relative offset.")
            }
            if (declaredPointCount <= 2) {
                add("Very small point array; this may represent a straight line or compact object path.")
            }
        }
        return SupernoteTotalPathPointArrayDecode(
            status = "decoded",
            encoding = "u32le-point-count-plus-u32le-xy-pairs",
            pointCountFieldRelativeOffsetInRecord = pointCountOffset,
            pointCountFieldAbsoluteOffset = absolutePayloadStart + recordStartRelative + pointCountOffset,
            pointArrayRelativeOffsetInRecord = pointArrayOffset,
            pointArrayRelativeOffsetInPayload = recordStartRelative + pointArrayOffset,
            pointArrayAbsoluteOffset = absolutePayloadStart + recordStartRelative + pointArrayOffset,
            declaredPointCount = declaredPointCount,
            decodedPointCount = points.size,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            rawPoints = points.toList(),
            rawPointPreview = points.take(MAX_POINT_PREVIEW_PAIRS),
            rawPointTailPreview = points.takeLast(min(MAX_POINT_TAIL_PAIRS, points.size)),
            interpretation = "Raw Supernote coordinate points decoded. Coordinate transform to screen/page space is deferred.",
            warnings = warnings
        )
    }

    private fun SupernoteTotalPathPointArrayDecode.toPointRunProbe(): SupernoteTotalPathPointRunProbe {
        return SupernoteTotalPathPointRunProbe(
            encoding = encoding,
            absoluteOffset = pointArrayAbsoluteOffset,
            relativeOffsetInPayload = pointArrayRelativeOffsetInPayload,
            relativeOffsetInRecord = pointArrayRelativeOffsetInRecord,
            pairCount = decodedPointCount,
            previewPairs = rawPointPreview.map { listOf(it.x, it.y) },
            reason = "Decoded from explicit point-count field at record-relative offset $pointCountFieldRelativeOffsetInRecord."
        )
    }

    private fun isPlausiblePointPair(x: Long, y: Long): Boolean {
        if (x == 0L && y == 0L) return false
        if (x !in 0..25_000L || y !in 0..25_000L) return false
        if (x < 100L && y < 100L) return false
        return x > 500L || y > 500L
    }

    private fun findBestPointRun(
        record: ByteArray,
        absolutePayloadStart: Int,
        recordStartRelative: Int
    ): SupernoteTotalPathPointRunProbe? {
        var best: SupernoteTotalPathPointRunProbe? = null
        var bestScore = Long.MIN_VALUE
        var offset = 0
        while (offset + 31 < record.size) {
            val pairs = mutableListOf<List<Long>>()
            var cursor = offset
            while (cursor + 7 < record.size && pairs.size < 64) {
                val x = u32le(record, cursor)
                val y = u32le(record, cursor + 4)
                if (!isPlausiblePointValue(x) || !isPlausiblePointValue(y)) {
                    break
                }
                if (x == 0L && y == 0L) {
                    break
                }
                pairs += listOf(x, y)
                cursor += 8
            }
            if (pairs.size >= 4) {
                val nonZeroPairs = pairs.count { pair -> pair[0] != 0L || pair[1] != 0L }
                val averageMagnitude = pairs.take(12).flatMap { it }.average().toLong()
                val score = pairs.size * 10_000L + averageMagnitude - offset
                if (nonZeroPairs >= 4 && score > bestScore) {
                    bestScore = score
                    best = SupernoteTotalPathPointRunProbe(
                        encoding = "u32le-pairs",
                        absoluteOffset = absolutePayloadStart + recordStartRelative + offset,
                        relativeOffsetInPayload = recordStartRelative + offset,
                        relativeOffsetInRecord = offset,
                        pairCount = pairs.size,
                        previewPairs = pairs.take(12),
                        reason = "Plausible consecutive u32le x/y coordinate pairs inside candidate record."
                    )
                }
            }
            offset += 4
        }
        return best
    }

    private fun isPlausiblePointValue(value: Long): Boolean {
        return value in 0..25_000L
    }

    private fun markerHit(
        payload: ByteArray,
        absolutePayloadStart: Int,
        marker: String
    ): SupernoteTotalPathMarkerHit {
        val markerBytes = marker.encodeToByteArray()
        val relativeOffsets = findOffsets(payload, markerBytes)
        val contexts = relativeOffsets.take(MAX_MARKER_CONTEXTS).map { offset ->
            contextAround(payload, offset, markerBytes.size)
        }
        return SupernoteTotalPathMarkerHit(
            marker = marker,
            count = relativeOffsets.size,
            relativeOffsets = relativeOffsets.take(20),
            absoluteOffsets = relativeOffsets.take(20).map { absolutePayloadStart + it },
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

    private fun findNumericRuns(
        payload: ByteArray,
        absolutePayloadStart: Int
    ): List<SupernoteTotalPathNumericRun> {
        val runs = mutableListOf<SupernoteTotalPathNumericRun>()
        runs += findU16Runs(payload, absolutePayloadStart, littleEndian = true)
        runs += findU16Runs(payload, absolutePayloadStart, littleEndian = false)
        runs += findU32Runs(payload, absolutePayloadStart, littleEndian = true)
        runs += findU32Runs(payload, absolutePayloadStart, littleEndian = false)
        return runs
            .sortedWith(compareBy<SupernoteTotalPathNumericRun> { it.absoluteOffset }.thenBy { it.encoding })
            .distinctBy { "${it.encoding}:${it.absoluteOffset}" }
            .take(MAX_NUMERIC_RUNS_PER_PAGE)
    }

    private fun findU16Runs(
        payload: ByteArray,
        absolutePayloadStart: Int,
        littleEndian: Boolean
    ): List<SupernoteTotalPathNumericRun> {
        val runs = mutableListOf<SupernoteTotalPathNumericRun>()
        var index = 0
        while (index + 1 < payload.size && runs.size < MAX_NUMERIC_RUNS_PER_PAGE) {
            val values = mutableListOf<Long>()
            val startIndex = index
            var cursor = index
            while (cursor + 1 < payload.size) {
                val value = if (littleEndian) {
                    u16le(payload, cursor)
                } else {
                    u16be(payload, cursor)
                }
                if (value !in 0..65535) {
                    break
                }
                values += value.toLong()
                cursor += 2
                if (values.size >= 32) {
                    break
                }
            }
            val candidate = buildNumericRunOrNull(
                encoding = if (littleEndian) "u16le" else "u16be",
                absolutePayloadStart = absolutePayloadStart,
                relativeOffset = startIndex,
                values = values,
                minLength = 12,
                maxAllowedValue = 65535L,
                reason = "Consecutive 16-bit values; possible compact coordinate, pressure, or angle run."
            )
            if (candidate != null) {
                runs += candidate
                index = cursor.coerceAtLeast(index + 2)
            } else {
                index += 2
            }
        }
        return runs
    }

    private fun findU32Runs(
        payload: ByteArray,
        absolutePayloadStart: Int,
        littleEndian: Boolean
    ): List<SupernoteTotalPathNumericRun> {
        val runs = mutableListOf<SupernoteTotalPathNumericRun>()
        var index = 0
        while (index + 3 < payload.size && runs.size < MAX_NUMERIC_RUNS_PER_PAGE) {
            val values = mutableListOf<Long>()
            val startIndex = index
            var cursor = index
            while (cursor + 3 < payload.size) {
                val value = if (littleEndian) {
                    u32le(payload, cursor)
                } else {
                    u32be(payload, cursor)
                }
                if (value !in 0..250_000L) {
                    break
                }
                values += value
                cursor += 4
                if (values.size >= 24) {
                    break
                }
            }
            val candidate = buildNumericRunOrNull(
                encoding = if (littleEndian) "u32le" else "u32be",
                absolutePayloadStart = absolutePayloadStart,
                relativeOffset = startIndex,
                values = values,
                minLength = 8,
                maxAllowedValue = 250_000L,
                reason = "Consecutive 32-bit values in a plausible touch-coordinate range."
            )
            if (candidate != null) {
                runs += candidate
                index = cursor.coerceAtLeast(index + 4)
            } else {
                index += 4
            }
        }
        return runs
    }

    private fun buildNumericRunOrNull(
        encoding: String,
        absolutePayloadStart: Int,
        relativeOffset: Int,
        values: List<Long>,
        minLength: Int,
        maxAllowedValue: Long,
        reason: String
    ): SupernoteTotalPathNumericRun? {
        if (values.size < minLength) {
            return null
        }
        val distinct = values.toSet().size
        if (distinct < 3) {
            return null
        }
        val minValue = values.minOrNull() ?: return null
        val maxValue = values.maxOrNull() ?: return null
        if (maxValue > maxAllowedValue) {
            return null
        }
        val nonZeroCount = values.count { it != 0L }
        if (nonZeroCount < max(3, values.size / 3)) {
            return null
        }
        return SupernoteTotalPathNumericRun(
            encoding = encoding,
            absoluteOffset = absolutePayloadStart + relativeOffset,
            relativeOffset = relativeOffset,
            valueCount = values.size,
            minValue = minValue,
            maxValue = maxValue,
            previewValues = values.take(12),
            reason = reason
        )
    }

    private fun summarizeBinary(payload: ByteArray): SupernoteTotalPathBinarySummary {
        val zeroCount = payload.count { it.toInt() == 0 }
        val printableCount = payload.count { byte ->
            val value = byte.toInt() and 0xff
            value in 32..126
        }
        val distinct = payload.map { it.toInt() and 0xff }.toSet().size
        val likelyBinary = payload.isNotEmpty() && printableCount < payload.size / 2
        return SupernoteTotalPathBinarySummary(
            zeroByteCount = zeroCount,
            printableAsciiByteCount = printableCount,
            distinctByteCount = distinct,
            likelyBinary = likelyBinary
        )
    }

    private fun previewHex(payload: ByteArray): String {
        return payload.take(TOTAL_PATH_PREVIEW_BYTES).joinToString(separator = " ") { byte ->
            "%02X".format(Locale.US, byte.toInt() and 0xff)
        }
    }

    private fun previewAscii(payload: ByteArray): String {
        return payload.take(TOTAL_PATH_PREVIEW_BYTES).toByteArray().toAsciiLikeString()
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

    private fun u16le(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun u16be(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)
    }

    private fun u32le(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun u32be(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
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
