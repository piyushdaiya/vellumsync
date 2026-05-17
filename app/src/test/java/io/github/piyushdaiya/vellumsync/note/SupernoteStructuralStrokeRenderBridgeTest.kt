package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-totalpath-structural-render-bridge-v0-test
class SupernoteStructuralStrokeRenderBridgeTest {
    @Test
    fun keepsPerStrokeBoundariesAndSkipsFlagDrawFalse() {
        val report = SupernoteTotalPathStructuralReport(
            format = "test",
            parserModel = "test-model",
            selectedPageNumber = 1,
            pageReports = listOf(
                SupernoteTotalPathStructuralPageReport(
                    pageNumber = 1,
                    pageSectionOffset = 0,
                    totalPathOffset = 10,
                    pageStyle = "style_8mm_ruled_line_a5x",
                    layerSeq = "MAINLAYER",
                    boundaryModelStatus = "ok",
                    geometricFilteringApplied = false,
                    strokeCount = 3,
                    screenWidth = 1404,
                    screenHeight = 1872,
                    strokes = listOf(
                        sampleStroke(strokeIndex = 0, recordIndex = 4, flagDrawState = "assumed-draw-from-points"),
                        sampleStroke(strokeIndex = 1, recordIndex = 5, flagDrawState = "flag-draw-false"),
                        sampleStroke(strokeIndex = 2, recordIndex = 6, flagDrawState = "assumed-draw-from-points")
                    ),
                    warnings = emptyList()
                )
            ),
            warnings = emptyList()
        )

        val bridged = SupernoteStructuralStrokeRenderBridge.bridge(report)
        val page = bridged.pageReports.single()
        assertEquals(3, page.decodedRecords)
        assertEquals(2, page.renderedRecords)
        assertTrue(page.warnings.any { it.contains("flag-draw-false-skipped=1") })
        assertEquals(listOf(4, 6), page.records.map { it.recordIndex })
        assertTrue(page.records.all { it.source == "structural-totalpath-per-stroke" })
    }

    private fun sampleStroke(
        strokeIndex: Int,
        recordIndex: Int,
        flagDrawState: String
    ): SupernoteStructuredStroke {
        return SupernoteStructuredStroke(
            strokeIndex = strokeIndex,
            recordIndex = recordIndex,
            category = "normalStroke",
            boundarySource = "record-structure-length-chain",
            recordLengthSource = "test",
            decodedByLengthChain = true,
            declaredRecordPayloadSize = 64,
            declaredPointCount = 3,
            decodedPointCount = 3,
            pointEncoding = "u32-pairs",
            pointCountFieldAbsoluteOffset = 32,
            pointArrayAbsoluteOffset = 36,
            recordStartAbsoluteOffset = 100,
            recordEndAbsoluteOffset = 164,
            firstU32LeFields = emptyList(),
            screenWidth = 1404,
            screenHeight = 1872,
            strokeLayer = "MAINLAYER",
            flagDrawState = flagDrawState,
            penUpBoundaryBefore = strokeIndex > 0,
            pressureDecodeStatus = "not-found",
            tiltDecodeStatus = "not-found",
            bounds = SupernoteStructuredStrokeBounds(minX = 100, minY = 200, maxX = 140, maxY = 240),
            points = listOf(
                SupernoteStructuredStrokePoint(0, 100, 200, null, null),
                SupernoteStructuredStrokePoint(1, 120, 220, null, null),
                SupernoteStructuredStrokePoint(2, 140, 240, null, null)
            ),
            warnings = emptyList()
        )
    }
}
