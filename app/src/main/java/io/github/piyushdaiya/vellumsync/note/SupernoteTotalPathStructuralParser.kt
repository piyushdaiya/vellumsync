package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import kotlin.math.max
import kotlin.math.min

// marker=vellumsync-snlib-guided-totalpath-structural-parser-v0
private const val STRUCTURAL_DEFAULT_PAGE_WIDTH = 1404
private const val STRUCTURAL_DEFAULT_PAGE_HEIGHT = 1872
private const val MAX_STRUCTURAL_STROKES_PER_PAGE = 1024
private const val MAX_STRUCTURAL_POINTS_PER_STROKE = 8192

data class SupernoteStructuredStrokePoint(
    val index: Int,
    val x: Long,
    val y: Long,
    val pressure: Int?,
    val tilt: Int?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"index\":$index,")
            append("\"x\":$x,")
            append("\"y\":$y,")
            append("\"pressure\":${pressure ?: "null"},")
            append("\"tilt\":${tilt ?: "null"}")
            append("}")
        }
    }
}

data class SupernoteStructuredStrokeBounds(
    val minX: Long,
    val minY: Long,
    val maxX: Long,
    val maxY: Long
) {
    fun toJson(): String {
        return "{\"minX\":$minX,\"minY\":$minY,\"maxX\":$maxX,\"maxY\":$maxY}"
    }
}

data class SupernoteStructuredStroke(
    val strokeIndex: Int,
    val recordIndex: Int,
    val category: String,
    val boundarySource: String,
    val recordLengthSource: String,
    val decodedByLengthChain: Boolean,
    val declaredRecordPayloadSize: Long?,
    val declaredPointCount: Int?,
    val decodedPointCount: Int,
    val pointEncoding: String?,
    val pointCountFieldAbsoluteOffset: Int?,
    val pointArrayAbsoluteOffset: Int?,
    val recordStartAbsoluteOffset: Int,
    val recordEndAbsoluteOffset: Int,
    val firstU32LeFields: List<Long>,
    val screenWidth: Int,
    val screenHeight: Int,
    val strokeLayer: String?,
    val flagDrawState: String,
    val penUpBoundaryBefore: Boolean,
    val pressureDecodeStatus: String,
    val tiltDecodeStatus: String,
    val bounds: SupernoteStructuredStrokeBounds?,
    val points: List<SupernoteStructuredStrokePoint>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"strokeIndex\":$strokeIndex,")
            append("\"recordIndex\":$recordIndex,")
            append("\"category\":${JsonText.quote(category)},")
            append("\"boundarySource\":${JsonText.quote(boundarySource)},")
            append("\"recordLengthSource\":${JsonText.quote(recordLengthSource)},")
            append("\"decodedByLengthChain\":$decodedByLengthChain,")
            append("\"declaredRecordPayloadSize\":${declaredRecordPayloadSize ?: "null"},")
            append("\"declaredPointCount\":${declaredPointCount ?: "null"},")
            append("\"decodedPointCount\":$decodedPointCount,")
            append("\"pointEncoding\":${JsonText.quote(pointEncoding)},")
            append("\"pointCountFieldAbsoluteOffset\":${pointCountFieldAbsoluteOffset ?: "null"},")
            append("\"pointArrayAbsoluteOffset\":${pointArrayAbsoluteOffset ?: "null"},")
            append("\"recordStartAbsoluteOffset\":$recordStartAbsoluteOffset,")
            append("\"recordEndAbsoluteOffset\":$recordEndAbsoluteOffset,")
            append("\"firstU32LeFields\":[${firstU32LeFields.joinToString(separator = ",")}],")
            append("\"screenWidth\":$screenWidth,")
            append("\"screenHeight\":$screenHeight,")
            append("\"strokeLayer\":${JsonText.quote(strokeLayer)},")
            append("\"flagDrawState\":${JsonText.quote(flagDrawState)},")
            append("\"penUpBoundaryBefore\":$penUpBoundaryBefore,")
            append("\"pressureDecodeStatus\":${JsonText.quote(pressureDecodeStatus)},")
            append("\"tiltDecodeStatus\":${JsonText.quote(tiltDecodeStatus)},")
            append("\"bounds\":${bounds?.toJson() ?: "null"},")
            append("\"points\":[")
            append(points.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathStructuralPageReport(
    val pageNumber: Int,
    val pageSectionOffset: Int,
    val totalPathOffset: Int?,
    val pageStyle: String?,
    val layerSeq: String?,
    val boundaryModelStatus: String,
    val geometricFilteringApplied: Boolean,
    val strokeCount: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val strokes: List<SupernoteStructuredStroke>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"pageSectionOffset\":$pageSectionOffset,")
            append("\"totalPathOffset\":${totalPathOffset ?: "null"},")
            append("\"pageStyle\":${JsonText.quote(pageStyle)},")
            append("\"layerSeq\":${JsonText.quote(layerSeq)},")
            append("\"boundaryModelStatus\":${JsonText.quote(boundaryModelStatus)},")
            append("\"geometricFilteringApplied\":$geometricFilteringApplied,")
            append("\"strokeCount\":$strokeCount,")
            append("\"screenWidth\":$screenWidth,")
            append("\"screenHeight\":$screenHeight,")
            append("\"strokes\":[")
            append(strokes.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteTotalPathStructuralReport(
    val format: String,
    val parserModel: String,
    val selectedPageNumber: Int?,
    val pageReports: List<SupernoteTotalPathStructuralPageReport>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote(format)},")
            append("\"parserModel\":${JsonText.quote(parserModel)},")
            append("\"selectedPageNumber\":${selectedPageNumber ?: "null"},")
            append("\"pageReports\":[")
            append(pageReports.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }

    fun singlePageJson(pageNumber: Int): String {
        return pageReports.firstOrNull { it.pageNumber == pageNumber }?.toJson() ?: toJson()
    }
}

object SupernoteTotalPathStructuralParser {
    fun parse(
        bytes: ByteArray,
        containerReport: SupernoteContainerReport,
        probeReport: SupernoteTotalPathProbeReport,
        pageNumberFilter: Int? = null
    ): SupernoteTotalPathStructuralReport {
        val pageSections = containerReport.pageSections.associateBy { it.pageNumber }
        val filteredPages = probeReport.pageReports.filter { page -> pageNumberFilter == null || page.pageNumber == pageNumberFilter }
        val pageReports = filteredPages.map { page ->
            val section = pageSections[page.pageNumber]
            parsePage(bytes = bytes, section = section, page = page)
        }
        val warnings = buildList {
            if (filteredPages.isEmpty()) {
                add("No TOTALPATH page reports matched the requested page filter.")
            }
            if (pageReports.all { it.strokeCount == 0 }) {
                add("Structural parser found no decoded TOTALPATH strokes with point arrays.")
            }
            addAll(pageReports.flatMap { it.warnings })
        }.distinct()
        return SupernoteTotalPathStructuralReport(
            format = "VellumSync SNLib-guided TOTALPATH structural parser report",
            parserModel = "snlib-guided-totalpath-structural-v0",
            selectedPageNumber = pageNumberFilter,
            pageReports = pageReports,
            warnings = warnings
        )
    }

    private fun parsePage(
        bytes: ByteArray,
        section: SupernotePageSection?,
        page: SupernoteTotalPathPageReport
    ): SupernoteTotalPathStructuralPageReport {
        val (screenWidth, screenHeight) = determineScreenSize(section, page)
        val candidateRecords = page.candidateRecords
            .sortedBy { it.recordIndex }
            .take(MAX_STRUCTURAL_STROKES_PER_PAGE)
        val strokes = candidateRecords.mapIndexed { index, record ->
            parseStroke(
                bytes = bytes,
                section = section,
                page = page,
                record = record,
                strokeIndex = index,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }
        val warnings = buildList {
            if (page.totalPathOffset == null) {
                add("Page does not expose a TOTALPATH offset.")
            }
            if (strokes.isEmpty()) {
                add("No structural strokes were decoded for this page.")
            }
            if (candidateRecords.size >= MAX_STRUCTURAL_STROKES_PER_PAGE) {
                add("Stroke list was truncated to $MAX_STRUCTURAL_STROKES_PER_PAGE entries for inspector output.")
            }
        }
        return SupernoteTotalPathStructuralPageReport(
            pageNumber = page.pageNumber,
            pageSectionOffset = page.pageSectionOffset,
            totalPathOffset = page.totalPathOffset,
            pageStyle = section?.pageStyle,
            layerSeq = section?.layerSeq,
            boundaryModelStatus = page.recordBoundaryModelStatus,
            geometricFilteringApplied = false,
            strokeCount = strokes.size,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            strokes = strokes,
            warnings = warnings
        )
    }

    private fun parseStroke(
        bytes: ByteArray,
        section: SupernotePageSection?,
        page: SupernoteTotalPathPageReport,
        record: SupernoteTotalPathRecordBoundary,
        strokeIndex: Int,
        screenWidth: Int,
        screenHeight: Int
    ): SupernoteStructuredStroke {
        val recordStart = record.estimatedRecordStartAbsoluteOffset.coerceIn(0, bytes.size)
        val recordEnd = record.estimatedRecordEndAbsoluteOffset.coerceIn(recordStart, bytes.size)
        val recordBytes = bytes.copyOfRange(recordStart, recordEnd)
        val decoded = record.decodedPointArray
        val rawPoints = decoded?.rawPoints?.ifEmpty {
            decoded.rawPointPreview
        }.orEmpty().take(MAX_STRUCTURAL_POINTS_PER_STROKE)
        val pointCount = decoded?.decodedPointCount ?: rawPoints.size
        val pointStride = estimatePointStride(decoded)
        val pointArrayRelativeOffsetInRecord = decoded?.pointArrayRelativeOffsetInRecord ?: -1
        val firstDataOffset = if (pointArrayRelativeOffsetInRecord >= 0 && pointStride > 0 && pointCount > 0) {
            pointArrayRelativeOffsetInRecord + pointCount * pointStride
        } else {
            -1
        }
        val pressureValues = if (pointCount > 0) {
            findOptionalInt16Series(
                recordBytes = recordBytes,
                count = pointCount,
                searchStartOffset = firstDataOffset,
                minValue = 0,
                maxValue = 4096,
                signed = false
            )
        } else {
            null
        }
        val tiltValues = if (pointCount > 0) {
            findOptionalInt16Series(
                recordBytes = recordBytes,
                count = pointCount,
                searchStartOffset = firstDataOffset,
                minValue = -180,
                maxValue = 180,
                signed = true
            )
        } else {
            null
        }
        val points = rawPoints.mapIndexed { index, point ->
            SupernoteStructuredStrokePoint(
                index = index,
                x = point.x,
                y = point.y,
                pressure = pressureValues?.getOrNull(index),
                tilt = tiltValues?.getOrNull(index)
            )
        }
        val bounds = points.takeIf { it.isNotEmpty() }?.let { buildBounds(it) }
        val warnings = buildList {
            if (decoded == null) {
                add("No decoded point array was available for this record.")
            }
            if (pressureValues == null) {
                add("Pressure series not confidently decoded from the structured record payload.")
            }
            if (tiltValues == null) {
                add("Tilt series not confidently decoded from the structured record payload.")
            }
            if (decoded != null && rawPoints.size < decoded.decodedPointCount) {
                add("Point preview was truncated to $MAX_STRUCTURAL_POINTS_PER_STROKE entries for inspector output.")
            }
            addAll(record.warnings)
        }.distinct()
        return SupernoteStructuredStroke(
            strokeIndex = strokeIndex,
            recordIndex = record.recordIndex,
            category = record.category,
            boundarySource = if (record.decodedByLengthChain) "record-structure-length-chain" else "record-structure-marker-window",
            recordLengthSource = record.recordLengthSource,
            decodedByLengthChain = record.decodedByLengthChain,
            declaredRecordPayloadSize = record.declaredRecordPayloadSize,
            declaredPointCount = decoded?.declaredPointCount,
            decodedPointCount = pointCount,
            pointEncoding = decoded?.encoding,
            pointCountFieldAbsoluteOffset = decoded?.pointCountFieldAbsoluteOffset,
            pointArrayAbsoluteOffset = decoded?.pointArrayAbsoluteOffset,
            recordStartAbsoluteOffset = recordStart,
            recordEndAbsoluteOffset = recordEnd,
            firstU32LeFields = record.firstU32LeFields,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            strokeLayer = inferStrokeLayer(section, recordBytes),
            flagDrawState = inferFlagDrawState(recordBytes, record.category, pointCount),
            penUpBoundaryBefore = strokeIndex > 0,
            pressureDecodeStatus = if (pressureValues == null) "not-found" else "decoded-int16-series",
            tiltDecodeStatus = if (tiltValues == null) "not-found" else "decoded-int16-series",
            bounds = bounds,
            points = points,
            warnings = warnings
        )
    }

    private fun determineScreenSize(
        section: SupernotePageSection?,
        page: SupernoteTotalPathPageReport
    ): Pair<Int, Int> {
        val style = section?.pageStyle?.lowercase().orEmpty()
        if (style.contains("a5x") || style.contains("ruled_line")) {
            return STRUCTURAL_DEFAULT_PAGE_WIDTH to STRUCTURAL_DEFAULT_PAGE_HEIGHT
        }
        val maxX = page.candidateRecords.maxOfOrNull { it.decodedPointArray?.maxX ?: 0L } ?: 0L
        val maxY = page.candidateRecords.maxOfOrNull { it.decodedPointArray?.maxY ?: 0L } ?: 0L
        if (maxX > 0L && maxY > 0L) {
            return max(maxX.toInt() + 1, STRUCTURAL_DEFAULT_PAGE_WIDTH) to max(maxY.toInt() + 1, STRUCTURAL_DEFAULT_PAGE_HEIGHT)
        }
        return STRUCTURAL_DEFAULT_PAGE_WIDTH to STRUCTURAL_DEFAULT_PAGE_HEIGHT
    }

    private fun estimatePointStride(decoded: SupernoteTotalPathPointArrayDecode?): Int {
        if (decoded == null) return 0
        val relativeCountField = decoded.pointCountFieldRelativeOffsetInRecord
        val relativePoints = decoded.pointArrayRelativeOffsetInRecord
        val gap = relativePoints - relativeCountField
        return when {
            gap >= 8 -> 8
            gap >= 4 -> 4
            else -> 8
        }
    }

    private fun findOptionalInt16Series(
        recordBytes: ByteArray,
        count: Int,
        searchStartOffset: Int,
        minValue: Int,
        maxValue: Int,
        signed: Boolean
    ): List<Int>? {
        if (count <= 0 || recordBytes.size < count * 2) {
            return null
        }
        val safeStart = if (searchStartOffset >= 0) searchStartOffset.coerceIn(0, recordBytes.size - 2) else 0
        val latestStart = recordBytes.size - count * 2
        var best: List<Int>? = null
        for (start in safeStart..latestStart) {
            if (start % 2 != 0) continue
            val values = mutableListOf<Int>()
            var cursor = start
            var ok = true
            while (values.size < count && cursor + 1 < recordBytes.size) {
                val value = readInt16(recordBytes, cursor, signed)
                if (value !in minValue..maxValue) {
                    ok = false
                    break
                }
                values += value
                cursor += 2
            }
            if (!ok || values.size != count) continue
            val distinct = values.toSet().size
            val nonZero = values.count { it != 0 }
            if (distinct >= min(4, count) || nonZero >= max(2, count / 8)) {
                best = values
                break
            }
        }
        return best
    }

    private fun inferFlagDrawState(recordBytes: ByteArray, category: String, pointCount: Int): String {
        val ascii = recordBytes.toAsciiLikeString()
        return when {
            ascii.contains("flagDraw", ignoreCase = true) && ascii.contains("false", ignoreCase = true) -> "flag-draw-false"
            ascii.contains("flagDraw", ignoreCase = true) && ascii.contains("true", ignoreCase = true) -> "flag-draw-true"
            category.equals("straightLine", ignoreCase = true) -> "assumed-draw-straight-line"
            pointCount > 0 -> "assumed-draw-from-points"
            else -> "unknown"
        }
    }

    private fun inferStrokeLayer(section: SupernotePageSection?, recordBytes: ByteArray): String? {
        val ascii = recordBytes.toAsciiLikeString()
        return when {
            ascii.contains("MAINLAYER") -> "MAINLAYER"
            ascii.contains("BGLAYER") -> "BGLAYER"
            ascii.contains("LAYER1") -> "LAYER1"
            ascii.contains("LAYER2") -> "LAYER2"
            ascii.contains("LAYER3") -> "LAYER3"
            section?.layerSeq != null -> "TOTALPATH:${section.layerSeq}"
            else -> "TOTALPATH"
        }
    }

    private fun buildBounds(points: List<SupernoteStructuredStrokePoint>): SupernoteStructuredStrokeBounds {
        var minX = Long.MAX_VALUE
        var minY = Long.MAX_VALUE
        var maxX = Long.MIN_VALUE
        var maxY = Long.MIN_VALUE
        points.forEach { point ->
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
        return SupernoteStructuredStrokeBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

    private fun readInt16(bytes: ByteArray, offset: Int, signed: Boolean): Int {
        val lo = bytes[offset].toInt() and 0xff
        val hi = bytes[offset + 1].toInt() and 0xff
        val raw = lo or (hi shl 8)
        return if (!signed) raw else raw.toShort().toInt()
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
