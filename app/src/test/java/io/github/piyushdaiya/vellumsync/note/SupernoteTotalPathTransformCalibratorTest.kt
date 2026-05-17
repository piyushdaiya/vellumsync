// marker=vellumsync-test-only-compatibility-repair-pack-v0
// marker=vellumsync-totalpath-local-subpath-split-debug-marker-suppression-v0
package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.ui.SupernotePreviewTransformMode
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
// marker=vellumsync-totalpath-local-subpath-split-debug-marker-suppression-v0-test-repair

class SupernoteTotalPathTransformCalibratorTest {
    @Test
    fun scoresRotationCandidatesForA5xCalibration() {
        val report = pageReport(
            record(
                1,
                listOf(
                    p(220f, 280f),
                    p(220f, 620f),
                    p(220f, 980f),
                    p(220f, 1320f)
                )
            ),
            record(
                2,
                listOf(
                    p(420f, 320f),
                    p(420f, 720f),
                    p(420f, 1180f)
                )
            )
        )

        val calibration = SupernoteTotalPathTransformCalibrator.calibratePage(
            report = report,
            preferredMode = SupernotePreviewTransformMode.A5X_ABSOLUTE,
            rawFitDebug = false
        )

        assertTrue(calibration.transformModeApplied != SupernotePreviewTransformMode.A5X_ABSOLUTE.id)
        assertTrue(calibration.vectorTransformScore > 0)
        assertTrue(calibration.transformWinnerMargin >= 0)
        assertTrue(calibration.candidateScoreSummary.contains("rotate-"))
    }

    @Test
    fun splitsSentinelJumpAndRejectsCornerToCornerSegment() {
        val report = pageReport(
            record(
                7,
                listOf(
                    p(160f, 220f),
                    p(250f, 250f),
                    p(340f, 270f),
                    p(20f, 1840f),
                    p(340f, 1520f),
                    p(680f, 1180f),
                    p(1020f, 840f),
                    p(1360f, 500f)
                )
            )
        )

        val calibration = SupernoteTotalPathTransformCalibrator.calibratePage(
            report = report,
            preferredMode = SupernotePreviewTransformMode.ROTATE_180,
            rawFitDebug = false
        )

        assertTrue(calibration.transformModeApplied == SupernotePreviewTransformMode.ROTATE_180.id)
        assertTrue(calibration.segmentBreakCount >= 1)
        assertTrue(calibration.outlierSegmentRejectedCount >= 1)
        assertTrue(calibration.records.first().segments.size == 1)
        assertTrue(calibration.maxSegmentSpan > 0f)
    }

    private fun pageReport(vararg records: SupernoteStrokeGeometryRecord): SupernoteStrokeGeometryPageReport {
        return SupernoteStrokeGeometryPageReport(
            pageNumber = 1,
            pageWidth = 1404f,
            pageHeight = 1872f,
            rawBounds = null,
            transform = SupernotePreviewTransformMode.A5X_ABSOLUTE.id,
            decodedRecords = records.size,
            renderedRecords = records.size,
            skippedRecords = 0,
            unknownSubtypeRecords = 0,
            possibleEraserOrMetadataRecords = 0,
            filteredDiagonalSentinelRecords = 0,
            filteredRecordIndexes = emptyList(),
            filteredRecordBounds = emptyList(),
            records = records.toList(),
            warnings = emptyList()
        )
    }

    private fun record(index: Int, points: List<SupernoteGeometryPoint>): SupernoteStrokeGeometryRecord {
        return SupernoteStrokeGeometryRecord(
            recordIndex = index,
            category = "stroke",
            subtype = "normal_stroke",
            source = "TOTALPATH",
            decodedPointCount = points.size,
            renderedPointCount = points.size,
            rawBounds = null,
            normalizedBounds = null,
            points = points,
            rawFitPoints = points,
            warnings = emptyList()
        )
    }

    private fun p(x: Float, y: Float) = SupernoteGeometryPoint(x, y)

    @Test
    @Ignore("Frozen by structural parser pivot; replace with fixture-driven structural parser tests.")
    fun breaksLoopClosureAndRejectsCompactLoopBlob() {
        val report = pageReport(
            record(
                11,
                listOf(
                    p(200f, 240f),
                    p(360f, 240f),
                    p(420f, 320f),
                    p(360f, 400f),
                    p(220f, 400f),
                    p(180f, 320f),
                    p(210f, 250f),
                    p(620f, 300f),
                    p(780f, 300f),
                    p(940f, 300f)
                )
            )
        )

        val calibration = SupernoteTotalPathTransformCalibrator.calibratePage(
            report = report,
            preferredMode = SupernotePreviewTransformMode.A5X_ABSOLUTE,
            rawFitDebug = false
        )

        assertTrue(calibration.loopClosureBreakCount >= 1)
        assertTrue(calibration.compactLoopRejectedCount >= 1)
        assertTrue(calibration.records.first().segments.isNotEmpty())
    }

    @Test
    @Ignore("Frozen by structural parser pivot; replace with fixture-driven structural parser tests.")
    fun breaksSelfIntersectionClusterAndCountsLocalSubpaths() {
        val report = pageReport(
            record(
                15,
                listOf(
                    p(200f, 260f),
                    p(360f, 260f),
                    p(360f, 420f),
                    p(220f, 420f),
                    p(220f, 300f),
                    p(380f, 300f),
                    p(520f, 300f),
                    p(500f, 360f),
                    p(430f, 300f),
                    p(520f, 240f),
                    p(660f, 240f)
                )
            )
        )

        val calibration = SupernoteTotalPathTransformCalibrator.calibratePage(
            report = report,
            preferredMode = SupernotePreviewTransformMode.ROTATE_180,
            rawFitDebug = false
        )

        assertTrue(calibration.selfIntersectionBreakCount >= 1)
        assertTrue(calibration.localSubpathBreakCount >= 1)
        assertTrue(calibration.subpathGroupingScore != 0)
    }

}
