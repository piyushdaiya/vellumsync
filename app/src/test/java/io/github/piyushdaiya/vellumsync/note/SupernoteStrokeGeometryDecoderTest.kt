package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteStrokeGeometryDecoderTest {
    @Test
    fun buildsGeometryFromDecodedPointArrays() {
        val pointArray = SupernoteTotalPathPointArrayDecode(
            status = "decoded",
            encoding = "u32le-point-count-plus-u32le-xy-pairs",
            pointCountFieldRelativeOffsetInRecord = 224,
            pointCountFieldAbsoluteOffset = 1024,
            pointArrayRelativeOffsetInRecord = 228,
            pointArrayRelativeOffsetInPayload = 228,
            pointArrayAbsoluteOffset = 1028,
            declaredPointCount = 4,
            decodedPointCount = 4,
            minX = 1000,
            maxX = 1600,
            minY = 9000,
            maxY = 9600,
            rawPointPreview = listOf(
                SupernoteRawPoint(1000, 9000),
                SupernoteRawPoint(1200, 9200),
                SupernoteRawPoint(1400, 9400),
                SupernoteRawPoint(1600, 9600)
            ),
            rawPointTailPreview = emptyList(),
            interpretation = "test",
            warnings = emptyList()
        )
        val record = SupernoteTotalPathRecordBoundary(
            recordIndex = 0,
            category = "others",
            categoryMarkerRelativeOffset = 60,
            categoryMarkerAbsoluteOffset = 1060,
            estimatedRecordStartRelativeOffset = 0,
            estimatedRecordStartAbsoluteOffset = 1000,
            estimatedRecordEndRelativeOffset = 400,
            estimatedRecordEndAbsoluteOffset = 1400,
            estimatedRecordByteLength = 400,
            declaredRecordPayloadSize = 396,
            recordLengthSource = "test",
            decodedByLengthChain = true,
            firstU32LeFields = emptyList(),
            candidateBounds = null,
            candidatePointRun = null,
            decodedPointArray = pointArray,
            warnings = emptyList()
        )
        val page = SupernoteTotalPathPageReport(
            pageNumber = 1,
            totalPathOffset = 1000,
            pageSectionOffset = 2000,
            estimatedPayloadStartOffset = 1000,
            estimatedPayloadEndOffset = 2000,
            estimatedPayloadByteLength = 1000,
            declaredPayloadSize = 996,
            declaredRecordCount = 1,
            headerSizeMatchesPayload = true,
            semanticRecordMarkerCount = 1,
            recordCountMatchesSemanticMarkers = true,
            recordBoundaryModelStatus = "test",
            recordChainDecoderStatus = "test",
            pointArrayDecodeStatus = "test",
            recordsDecodedByLengthChain = 1,
            recordsWithDecodedPointArrays = 1,
            candidateRecords = listOf(record),
            firstPreviewHex = "",
            firstPreviewAscii = "",
            markerHits = emptyList(),
            numericRuns = emptyList(),
            binarySummary = null,
            candidateStrokeRecordCount = 1,
            candidateStrokeRecordSignals = emptyList(),
            candidateToolSignals = emptyList(),
            warnings = emptyList()
        )
        val report = SupernoteTotalPathProbeReport(
            formatStatus = "test",
            pagesWithTotalPath = 1,
            totalEstimatedPayloadBytes = 1000,
            pageReports = listOf(page),
            probeWarnings = emptyList()
        )

        val geometry = SupernoteStrokeGeometryDecoder.decode(report)

        assertEquals(1, geometry.totalPages)
        assertEquals(1, geometry.totalDecodedRecords)
        assertEquals(1, geometry.totalRenderedRecords)
        val geometryPage = geometry.pageReports.first()
        assertEquals(1, geometryPage.renderedRecords)
        assertTrue(geometryPage.records.first().points.size >= 2)
        assertEquals("normal_stroke", geometryPage.records.first().subtype)
        assertTrue(geometry.toJson().contains("stroke".removePrefix("x")))
    }
}
