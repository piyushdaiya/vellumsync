// marker=vellumsync-totalpath-local-subpath-split-debug-marker-suppression-v0
package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.ui.SupernotePreviewTransformMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Calibrates TOTALPATH preview geometry against plausible A5X/viewer transforms
 * and splits raw paths on sentinel-like discontinuities before drawing.
 */
data class SupernoteTransformedVectorRecord(
    val recordIndex: Int,
    val subtype: String,
    val source: String,
    val segments: List<List<SupernoteGeometryPoint>>,
    val segmentBreakCount: Int,
    val loopClosureBreakCount: Int,
    val localSubpathBreakCount: Int,
    val selfIntersectionBreakCount: Int,
    val outlierSegmentRejectedCount: Int,
    val compactLoopRejectedCount: Int,
    val compactBlobRejectedCount: Int,
    val maxSegmentSpan: Float,
    val boundsSummary: String?
)

data class SupernoteVectorTransformCalibration(
    val transformModeApplied: String,
    val vectorTransformScore: Int,
    val transformWinnerLocked: Boolean,
    val transformWinnerMargin: Int,
    val segmentBreakCount: Int,
    val loopClosureBreakCount: Int,
    val localSubpathBreakCount: Int,
    val selfIntersectionBreakCount: Int,
    val outlierSegmentRejectedCount: Int,
    val compactLoopRejectedCount: Int,
    val compactBlobRejectedCount: Int,
    val maxSegmentSpan: Float,
    val subpathGroupingScore: Int,
    val candidateScoreSummary: String,
    val records: List<SupernoteTransformedVectorRecord>
)

object SupernoteTotalPathTransformCalibrator {
    fun calibratePage(
        report: SupernoteStrokeGeometryPageReport,
        preferredMode: SupernotePreviewTransformMode,
        rawFitDebug: Boolean = false
    ): SupernoteVectorTransformCalibration {
        val candidates = candidateModes(preferredMode = preferredMode, rawFitDebug = rawFitDebug)
            .map { mode -> evaluateMode(report = report, mode = mode, rawFitDebug = rawFitDebug) }
            .sortedWith(
                compareByDescending<TransformCandidate> { it.score }
                    .thenByDescending { if (it.mode == preferredMode) 1 else 0 }
                    .thenByDescending { if (it.mode.id == report.transform) 1 else 0 }
            )
        val winner = candidates.firstOrNull() ?: TransformCandidate(
            mode = preferredMode,
            score = 0,
            segmentBreakCount = 0,
            loopClosureBreakCount = 0,
            localSubpathBreakCount = 0,
            selfIntersectionBreakCount = 0,
            outlierSegmentRejectedCount = 0,
            compactLoopRejectedCount = 0,
            compactBlobRejectedCount = 0,
            maxSegmentSpan = 0f,
            subpathGroupingScore = 0,
            records = emptyList(),
            summary = "none"
        )
        val runnerUp = candidates.getOrNull(1)
        val winnerMargin = if (runnerUp == null) winner.score else winner.score - runnerUp.score
        val winnerLocked = winner.records.isNotEmpty() && winnerMargin >= if (rawFitDebug) 120 else 45
        val summary = candidates.joinToString(separator = "; ") { candidate -> candidate.summary }
        return SupernoteVectorTransformCalibration(
            transformModeApplied = winner.mode.id,
            vectorTransformScore = winner.score,
            transformWinnerLocked = winnerLocked,
            transformWinnerMargin = winnerMargin,
            segmentBreakCount = winner.segmentBreakCount,
            loopClosureBreakCount = winner.loopClosureBreakCount,
            localSubpathBreakCount = winner.localSubpathBreakCount,
            selfIntersectionBreakCount = winner.selfIntersectionBreakCount,
            outlierSegmentRejectedCount = winner.outlierSegmentRejectedCount,
            compactLoopRejectedCount = winner.compactLoopRejectedCount,
            compactBlobRejectedCount = winner.compactBlobRejectedCount,
            maxSegmentSpan = winner.maxSegmentSpan,
            subpathGroupingScore = winner.subpathGroupingScore,
            candidateScoreSummary = summary,
            records = winner.records
        )
    }

    private fun candidateModes(
        preferredMode: SupernotePreviewTransformMode,
        rawFitDebug: Boolean
    ): List<SupernotePreviewTransformMode> {
        val base = when {
            rawFitDebug -> listOf(
                SupernotePreviewTransformMode.RAW_FIT,
                SupernotePreviewTransformMode.ROTATE_90,
                SupernotePreviewTransformMode.ROTATE_180,
                SupernotePreviewTransformMode.ROTATE_270,
                SupernotePreviewTransformMode.FLIP_X,
                SupernotePreviewTransformMode.FLIP_Y
            )

            preferredMode.family == "A5X calibration" -> listOf(
                preferredMode,
                SupernotePreviewTransformMode.A5X_ABSOLUTE,
                SupernotePreviewTransformMode.A5X_RAW,
                SupernotePreviewTransformMode.A5X_PORTRAIT,
                SupernotePreviewTransformMode.A5X_PORTRAIT_2,
                SupernotePreviewTransformMode.A5X_FLIPPED_PORTRAIT,
                SupernotePreviewTransformMode.A5X_ROTATED_PORTRAIT,
                SupernotePreviewTransformMode.ROTATE_90,
                SupernotePreviewTransformMode.ROTATE_180,
                SupernotePreviewTransformMode.ROTATE_270
            )

            else -> listOf(preferredMode)
        }
        return base.distinctBy { it.id }
    }

    private fun evaluateMode(
        report: SupernoteStrokeGeometryPageReport,
        mode: SupernotePreviewTransformMode,
        rawFitDebug: Boolean
    ): TransformCandidate {
        val records = mutableListOf<SupernoteTransformedVectorRecord>()
        var segmentBreakCount = 0
        var loopClosureBreakCount = 0
        var outlierSegmentRejectedCount = 0
        var compactLoopRejectedCount = 0
        var compactBlobRejectedCount = 0
        var localSubpathBreakCount = 0
        var selfIntersectionBreakCount = 0
        var maxSegmentSpan = 0f
        var acceptedSegmentCount = 0
        var wideSegments = 0
        var tallSegments = 0
        var totalHorizontalSpan = 0f
        var totalVerticalSpan = 0f
        var leftColumnSegments = 0
        var midColumnSegments = 0
        var rightColumnSegments = 0
        var topRegionSegments = 0
        var lowerRegionSegments = 0
        val allPoints = mutableListOf<SupernoteGeometryPoint>()

        report.records.forEach { record ->
            val displayPoints = if (rawFitDebug && record.rawFitPoints.size >= 2) record.rawFitPoints else record.points
            if (displayPoints.size < 2) return@forEach
            val mappedPoints = displayPoints.map { point ->
                val mapped = mode.transform(point.x, point.y, report.pageWidth, report.pageHeight)
                SupernoteGeometryPoint(mapped.first, mapped.second)
            }
            val segmented = splitRecord(mappedPoints, report.pageWidth, report.pageHeight)
            segmentBreakCount += segmented.segmentBreakCount
            loopClosureBreakCount += segmented.loopClosureBreakCount
            localSubpathBreakCount += segmented.localSubpathBreakCount
            selfIntersectionBreakCount += segmented.selfIntersectionBreakCount
            outlierSegmentRejectedCount += segmented.outlierSegmentRejectedCount
            compactLoopRejectedCount += segmented.compactLoopRejectedCount
            compactBlobRejectedCount += segmented.compactBlobRejectedCount
            maxSegmentSpan = max(maxSegmentSpan, segmented.maxSegmentSpan)
            segmented.segments.forEach { segment ->
                acceptedSegmentCount += 1
                allPoints.addAll(segment)
                val segmentBounds = bounds(segment)
                if (segmentBounds != null) {
                    val spanX = segmentBounds.second.x - segmentBounds.first.x
                    val spanY = segmentBounds.second.y - segmentBounds.first.y
                    val centerX = (segmentBounds.first.x + segmentBounds.second.x) / 2f / report.pageWidth.coerceAtLeast(1f)
                    val centerY = (segmentBounds.first.y + segmentBounds.second.y) / 2f / report.pageHeight.coerceAtLeast(1f)
                    if (spanX > spanY * 1.35f) wideSegments += 1
                    if (spanY > spanX * 1.35f) tallSegments += 1
                    totalHorizontalSpan += spanX
                    totalVerticalSpan += spanY
                    when {
                        centerX < 0.32f -> leftColumnSegments += 1
                        centerX < 0.70f -> midColumnSegments += 1
                        else -> rightColumnSegments += 1
                    }
                    if (centerY < 0.42f) topRegionSegments += 1 else lowerRegionSegments += 1
                }
            }
            records += SupernoteTransformedVectorRecord(
                recordIndex = record.recordIndex,
                subtype = record.subtype,
                source = record.source,
                segments = segmented.segments,
                segmentBreakCount = segmented.segmentBreakCount,
                loopClosureBreakCount = segmented.loopClosureBreakCount,
                localSubpathBreakCount = segmented.localSubpathBreakCount,
                selfIntersectionBreakCount = segmented.selfIntersectionBreakCount,
                outlierSegmentRejectedCount = segmented.outlierSegmentRejectedCount,
                compactLoopRejectedCount = segmented.compactLoopRejectedCount,
                compactBlobRejectedCount = segmented.compactBlobRejectedCount,
                maxSegmentSpan = segmented.maxSegmentSpan,
                boundsSummary = boundsSummary(segmented.segments)
            )
        }

        val globalBounds = bounds(allPoints)
        val score = if (globalBounds == null || acceptedSegmentCount == 0) {
            Int.MIN_VALUE / 4
        } else {
            val contentSpanX = globalBounds.second.x - globalBounds.first.x
            val contentSpanY = globalBounds.second.y - globalBounds.first.y
            val contentWidthRatio = contentSpanX / report.pageWidth.coerceAtLeast(1f)
            val contentHeightRatio = contentSpanY / report.pageHeight.coerceAtLeast(1f)
            val centerX = (globalBounds.first.x + globalBounds.second.x) / 2f / report.pageWidth.coerceAtLeast(1f)
            val centerY = (globalBounds.first.y + globalBounds.second.y) / 2f / report.pageHeight.coerceAtLeast(1f)
            val groupingScore = buildGroupingScore(
                acceptedSegmentCount = acceptedSegmentCount,
                wideSegments = wideSegments,
                tallSegments = tallSegments,
                localSubpathBreakCount = localSubpathBreakCount,
                selfIntersectionBreakCount = selfIntersectionBreakCount,
                outlierSegmentRejectedCount = outlierSegmentRejectedCount,
                compactBlobRejectedCount = compactBlobRejectedCount,
                leftColumnSegments = leftColumnSegments,
                midColumnSegments = midColumnSegments,
                rightColumnSegments = rightColumnSegments,
                contentWidthRatio = contentWidthRatio,
                contentHeightRatio = contentHeightRatio
            )
            var candidateScore = 0
            candidateScore += acceptedSegmentCount * 10
            candidateScore += wideSegments * 16
            candidateScore -= tallSegments * 12
            candidateScore += ((contentWidthRatio - contentHeightRatio) * 420f).toInt()
            candidateScore += (totalHorizontalSpan / report.pageWidth.coerceAtLeast(1f) * 18f).toInt()
            candidateScore -= (totalVerticalSpan / report.pageHeight.coerceAtLeast(1f) * 9f).toInt()
            candidateScore -= (abs(centerX - 0.5f) * 180f).toInt()
            candidateScore -= (abs(centerY - 0.38f) * 120f).toInt()
            candidateScore -= segmentBreakCount * 2
            candidateScore -= loopClosureBreakCount * 3
            candidateScore += localSubpathBreakCount * 4
            candidateScore += selfIntersectionBreakCount * 6
            candidateScore -= outlierSegmentRejectedCount * 28
            candidateScore -= compactLoopRejectedCount * 35
            candidateScore -= compactBlobRejectedCount * 18
            candidateScore += groupingScore / 4
            if (contentWidthRatio < 0.18f) candidateScore -= 140
            if (contentHeightRatio < 0.08f) candidateScore -= 140
            if (contentWidthRatio > 0.96f && contentHeightRatio > 0.94f) candidateScore -= 90
            if (leftColumnSegments > 0) candidateScore += 18
            if (midColumnSegments > 0) candidateScore += 18
            if (rightColumnSegments > 0) candidateScore += 14
            if (topRegionSegments > 0 && lowerRegionSegments > 0) candidateScore += 12
            if (rightColumnSegments == 0 && leftColumnSegments > 2 && midColumnSegments > 2) candidateScore -= 24
            candidateScore
        }
        val subpathGroupingScore = if (globalBounds == null || acceptedSegmentCount == 0) 0 else buildGroupingScore(
            acceptedSegmentCount = acceptedSegmentCount,
            wideSegments = wideSegments,
            tallSegments = tallSegments,
            localSubpathBreakCount = localSubpathBreakCount,
            selfIntersectionBreakCount = selfIntersectionBreakCount,
            outlierSegmentRejectedCount = outlierSegmentRejectedCount,
            compactBlobRejectedCount = compactBlobRejectedCount,
            leftColumnSegments = leftColumnSegments,
            midColumnSegments = midColumnSegments,
            rightColumnSegments = rightColumnSegments,
            contentWidthRatio = (globalBounds.second.x - globalBounds.first.x) / report.pageWidth.coerceAtLeast(1f),
            contentHeightRatio = (globalBounds.second.y - globalBounds.first.y) / report.pageHeight.coerceAtLeast(1f)
        )

        val summary = buildString {
            append(mode.id)
            append(" score=")
            append(score)
            append(" segments=")
            append(acceptedSegmentCount)
            append(" wide=")
            append(wideSegments)
            append(" tall=")
            append(tallSegments)
            append(" breaks=")
            append(segmentBreakCount)
            append(" loopBreaks=")
            append(loopClosureBreakCount)
            append(" rejected=")
            append(outlierSegmentRejectedCount)
            append(" loopRejected=")
            append(compactLoopRejectedCount)
            append(" compactBlobs=")
            append(compactBlobRejectedCount)
            append(" localBreaks=")
            append(localSubpathBreakCount)
            append(" selfIntersections=")
            append(selfIntersectionBreakCount)
            append(" grouping=")
            append(subpathGroupingScore)
            append(" cols=")
            append(leftColumnSegments)
            append('/')
            append(midColumnSegments)
            append('/')
            append(rightColumnSegments)
            append(" maxSpan=")
            append(formatFloat(maxSegmentSpan))
        }

        return TransformCandidate(
            mode = mode,
            score = score,
            segmentBreakCount = segmentBreakCount,
            loopClosureBreakCount = loopClosureBreakCount,
            localSubpathBreakCount = localSubpathBreakCount,
            selfIntersectionBreakCount = selfIntersectionBreakCount,
            outlierSegmentRejectedCount = outlierSegmentRejectedCount,
            compactLoopRejectedCount = compactLoopRejectedCount,
            compactBlobRejectedCount = compactBlobRejectedCount,
            maxSegmentSpan = maxSegmentSpan,
            subpathGroupingScore = subpathGroupingScore,
            records = records,
            summary = summary
        )
    }

    private fun splitRecord(
        points: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): SegmentedRecord {
        if (points.size < 2) {
            return SegmentedRecord(emptyList(), 0, 0, 0, 0, 0, 0, 0, 0f)
        }
        val segments = mutableListOf<List<SupernoteGeometryPoint>>()
        var current = mutableListOf(points.first())
        var sentinelBreaks = 0
        var loopBreaks = 0
        var localBreaks = 0
        var selfIntersectionBreaks = 0
        var rejected = 0
        var compactLoopRejected = 0
        var compactBlobRejected = 0
        var maxSpan = 0f
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val currentPoint = points[index]
            val breakReason = segmentBreakReason(current, previous, currentPoint, pageWidth, pageHeight)
            if (breakReason != null) {
                when (breakReason) {
                    SegmentBreakReason.SENTINEL_OR_OUTLIER -> sentinelBreaks += 1
                    SegmentBreakReason.SELF_INTERSECTION_CLUSTER -> {
                        loopBreaks += 1
                        selfIntersectionBreaks += 1
                    }
                    SegmentBreakReason.LOCAL_SUBPATH_RESET -> localBreaks += 1
                }
                val finalized = finalizeSegment(current, pageWidth, pageHeight)
                if (finalized.accepted != null) {
                    segments += finalized.accepted
                    maxSpan = max(maxSpan, finalized.span)
                } else if (finalized.rejected) {
                    rejected += 1
                    if (finalized.compactLoopRejected) compactLoopRejected += 1
                    if (finalized.compactLoopRejected) compactBlobRejected += 1
                    maxSpan = max(maxSpan, finalized.span)
                }
                current = mutableListOf(currentPoint)
            } else {
                current.add(currentPoint)
            }
        }
        val finalized = finalizeSegment(current, pageWidth, pageHeight)
        if (finalized.accepted != null) {
            segments += finalized.accepted
            maxSpan = max(maxSpan, finalized.span)
        } else if (finalized.rejected) {
            rejected += 1
            if (finalized.compactLoopRejected) compactLoopRejected += 1
            if (finalized.compactLoopRejected) compactBlobRejected += 1
            maxSpan = max(maxSpan, finalized.span)
        }
        return SegmentedRecord(
            segments = segments,
            segmentBreakCount = sentinelBreaks + loopBreaks + localBreaks,
            loopClosureBreakCount = loopBreaks,
            localSubpathBreakCount = localBreaks,
            selfIntersectionBreakCount = selfIntersectionBreaks,
            outlierSegmentRejectedCount = rejected,
            compactLoopRejectedCount = compactLoopRejected,
            compactBlobRejectedCount = compactBlobRejected,
            maxSegmentSpan = maxSpan
        )
    }

    private fun segmentBreakReason(
        existingSegment: List<SupernoteGeometryPoint>,
        previous: SupernoteGeometryPoint,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): SegmentBreakReason? {
        if (shouldBreakSegment(previous, current, pageWidth, pageHeight)) {
            return SegmentBreakReason.SENTINEL_OR_OUTLIER
        }
        if (shouldBreakOnSelfIntersectionCluster(existingSegment, previous, current, pageWidth, pageHeight)) {
            return SegmentBreakReason.SELF_INTERSECTION_CLUSTER
        }
        if (shouldBreakOnLoopClosure(existingSegment, current, pageWidth, pageHeight)) {
            return SegmentBreakReason.SELF_INTERSECTION_CLUSTER
        }
        if (shouldBreakOnSharpHeadingReset(existingSegment, previous, current, pageWidth, pageHeight)) {
            return SegmentBreakReason.LOCAL_SUBPATH_RESET
        }
        if (shouldBreakOnLocalReversalCluster(existingSegment, previous, current, pageWidth, pageHeight)) {
            return SegmentBreakReason.LOCAL_SUBPATH_RESET
        }
        return null
    }

    private fun finalizeSegment(
        candidate: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): FinalizedSegment {
        if (candidate.size < 2) return FinalizedSegment(null, false, false, 0f)
        val span = segmentSpan(candidate)
        return when {
            isExtremeCornerToCornerSegment(candidate, pageWidth, pageHeight) -> FinalizedSegment(null, true, false, span)
            isCompactLoopBlob(candidate, pageWidth, pageHeight) -> FinalizedSegment(null, true, true, span)
            else -> FinalizedSegment(candidate.toList(), false, false, span)
        }
    }

    private fun shouldBreakSegment(
        previous: SupernoteGeometryPoint,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val distance = hypot(current.x - previous.x, current.y - previous.y)
        val hugeJump = distance > pageDiagonal * 0.22f
        val crossCornerJump = nearCorner(previous, pageWidth, pageHeight) && nearOppositeCorner(previous, current, pageWidth, pageHeight)
        val teleportedAcrossPage = abs(current.x - previous.x) > pageWidth * 0.36f && abs(current.y - previous.y) > pageHeight * 0.28f
        return hugeJump || crossCornerJump || teleportedAcrossPage
    }

    private fun shouldBreakOnSelfIntersectionCluster(
        existingSegment: List<SupernoteGeometryPoint>,
        previous: SupernoteGeometryPoint,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (existingSegment.size < 6) return false
        val newSegmentStart = previous
        val newSegmentEnd = current
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val localBounds = bounds(existingSegment.takeLast(6) + current)
        val localCluster = localBounds?.let { (minPoint, maxPoint) ->
            (maxPoint.x - minPoint.x) < pageWidth * 0.20f && (maxPoint.y - minPoint.y) < pageHeight * 0.14f
        } ?: false
        val recentLength = pathLength(existingSegment.takeLast(min(8, existingSegment.size)) + current)
        for (index in 0 until existingSegment.lastIndex - 2) {
            val a = existingSegment[index]
            val b = existingSegment[index + 1]
            if (segmentsIntersect(a, b, newSegmentStart, newSegmentEnd)) {
                val intersectionDistance = hypot(newSegmentEnd.x - a.x, newSegmentEnd.y - a.y)
                if (localCluster || intersectionDistance < pageDiagonal * 0.08f || recentLength > pageDiagonal * 0.06f) {
                    return true
                }
            }
        }
        return false
    }

    private fun shouldBreakOnLoopClosure(
        existingSegment: List<SupernoteGeometryPoint>,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (existingSegment.size < 7) return false
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val proximityThreshold = pageDiagonal * 0.028f
        val maxIndex = existingSegment.lastIndex - 3
        if (maxIndex < 2) return false
        for (index in 0..maxIndex) {
            val anchor = existingSegment[index]
            val distance = hypot(current.x - anchor.x, current.y - anchor.y)
            if (distance <= proximityThreshold) {
                val tail = existingSegment.subList(index, existingSegment.size)
                if (pathLength(tail) > pageDiagonal * 0.10f) {
                    return true
                }
            }
        }
        return false
    }

    private fun shouldBreakOnSharpHeadingReset(
        existingSegment: List<SupernoteGeometryPoint>,
        previous: SupernoteGeometryPoint,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (existingSegment.size < 3) return false
        val secondPrevious = existingSegment[existingSegment.lastIndex - 1]
        val vector1X = previous.x - secondPrevious.x
        val vector1Y = previous.y - secondPrevious.y
        val vector2X = current.x - previous.x
        val vector2Y = current.y - previous.y
        val length1 = hypot(vector1X, vector1Y)
        val length2 = hypot(vector2X, vector2Y)
        if (length1 < 2f || length2 < 2f) return false
        val dot = vector1X * vector2X + vector1Y * vector2Y
        val cosine = (dot / (length1 * length2)).coerceIn(-1f, 1f)
        val headingReset = cosine < -0.78f
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val largeEnough = length1 > pageDiagonal * 0.010f && length2 > pageDiagonal * 0.010f
        val segmentSoFar = pathLength(existingSegment)
        return headingReset && largeEnough && segmentSoFar > pageDiagonal * 0.035f
    }

    private fun shouldBreakOnLocalReversalCluster(
        existingSegment: List<SupernoteGeometryPoint>,
        previous: SupernoteGeometryPoint,
        current: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (existingSegment.size < 5) return false
        val recent = (existingSegment.takeLast(5) + previous + current).distinct()
        if (recent.size < 5) return false
        val localBounds = bounds(recent) ?: return false
        val spanX = localBounds.second.x - localBounds.first.x
        val spanY = localBounds.second.y - localBounds.first.y
        if (spanX > pageWidth * 0.18f || spanY > pageHeight * 0.12f) return false
        var reversalCount = 0
        for (index in 2 until recent.size) {
            val a = recent[index - 2]
            val b = recent[index - 1]
            val c = recent[index]
            val v1x = b.x - a.x
            val v1y = b.y - a.y
            val v2x = c.x - b.x
            val v2y = c.y - b.y
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 < 1.5f || len2 < 1.5f) continue
            val cosine = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1f, 1f)
            if (cosine < -0.28f) reversalCount += 1
        }
        val localPath = pathLength(recent)
        val maxSpan = max(spanX, spanY)
        return reversalCount >= 2 && localPath > maxSpan * 2.2f
    }

    private fun isExtremeCornerToCornerSegment(
        points: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (points.size < 2) return false
        val first = points.first()
        val last = points.last()
        val span = segmentSpan(points)
        val pathLength = pathLength(points)
        val straightness = if (pathLength <= 0.0001f) 0f else span / pathLength
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val b = bounds(points) ?: return false
        val spanX = b.second.x - b.first.x
        val spanY = b.second.y - b.first.y
        val cornerToCorner = nearOppositeCorner(first, last, pageWidth, pageHeight)
        val largeDiagonal = spanX > pageWidth * 0.55f && spanY > pageHeight * 0.55f && span > pageDiagonal * 0.60f
        val outlierStraight = span > pageDiagonal * 0.36f && straightness > 0.86f && abs(last.x - first.x) > pageWidth * 0.24f && abs(last.y - first.y) > pageHeight * 0.18f
        return (cornerToCorner && straightness > 0.80f) || (largeDiagonal && straightness > 0.90f) || outlierStraight
    }

    private fun isCompactLoopBlob(
        points: List<SupernoteGeometryPoint>,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        if (points.size < 6) return false
        val b = bounds(points) ?: return false
        val spanX = b.second.x - b.first.x
        val spanY = b.second.y - b.first.y
        if (spanX <= 0f || spanY <= 0f) return false
        val span = max(spanX, spanY)
        val minSpan = min(spanX, spanY)
        val path = pathLength(points)
        val direct = segmentSpan(points)
        val pageDiagonal = hypot(pageWidth, pageHeight)
        val closed = direct < span * 0.45f
        val scribbly = path > span * 3.4f
        val compact = span < pageWidth * 0.28f && spanY < pageHeight * 0.16f
        val notSimpleUnderline = !(spanX > spanY * 5.5f && spanY < pageHeight * 0.03f)
        val topBand = b.first.y < pageHeight * 0.28f || b.second.y > pageHeight * 0.46f
        return closed && scribbly && compact && minSpan > pageDiagonal * 0.008f && notSimpleUnderline && topBand
    }

    private fun segmentsIntersect(
        a1: SupernoteGeometryPoint,
        a2: SupernoteGeometryPoint,
        b1: SupernoteGeometryPoint,
        b2: SupernoteGeometryPoint
    ): Boolean {
        fun orientation(p: SupernoteGeometryPoint, q: SupernoteGeometryPoint, r: SupernoteGeometryPoint): Float {
            return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
        }
        fun onSegment(p: SupernoteGeometryPoint, q: SupernoteGeometryPoint, r: SupernoteGeometryPoint): Boolean {
            return q.x >= min(p.x, r.x) - 0.5f && q.x <= max(p.x, r.x) + 0.5f &&
                q.y >= min(p.y, r.y) - 0.5f && q.y <= max(p.y, r.y) + 0.5f
        }
        val o1 = orientation(a1, a2, b1)
        val o2 = orientation(a1, a2, b2)
        val o3 = orientation(b1, b2, a1)
        val o4 = orientation(b1, b2, a2)
        if ((o1 > 0f) != (o2 > 0f) && (o3 > 0f) != (o4 > 0f)) return true
        if (abs(o1) < 0.001f && onSegment(a1, b1, a2)) return true
        if (abs(o2) < 0.001f && onSegment(a1, b2, a2)) return true
        if (abs(o3) < 0.001f && onSegment(b1, a1, b2)) return true
        if (abs(o4) < 0.001f && onSegment(b1, a2, b2)) return true
        return false
    }

    private fun buildGroupingScore(
        acceptedSegmentCount: Int,
        wideSegments: Int,
        tallSegments: Int,
        localSubpathBreakCount: Int,
        selfIntersectionBreakCount: Int,
        outlierSegmentRejectedCount: Int,
        compactBlobRejectedCount: Int,
        leftColumnSegments: Int,
        midColumnSegments: Int,
        rightColumnSegments: Int,
        contentWidthRatio: Float,
        contentHeightRatio: Float
    ): Int {
        var score = acceptedSegmentCount * 14
        score += wideSegments * 10
        score -= tallSegments * 6
        score += localSubpathBreakCount * 12
        score += selfIntersectionBreakCount * 14
        score -= outlierSegmentRejectedCount * 18
        score -= compactBlobRejectedCount * 22
        if (leftColumnSegments > 0) score += 18
        if (midColumnSegments > 0) score += 18
        if (rightColumnSegments > 0) score += 10
        if (contentWidthRatio in 0.24f..0.82f) score += 20 else score -= 12
        if (contentHeightRatio in 0.10f..0.72f) score += 16 else score -= 10
        return score
    }

    private fun nearCorner(point: SupernoteGeometryPoint, pageWidth: Float, pageHeight: Float): Boolean {
        val nearLeft = point.x <= pageWidth * 0.05f
        val nearRight = point.x >= pageWidth * 0.95f
        val nearTop = point.y <= pageHeight * 0.05f
        val nearBottom = point.y >= pageHeight * 0.95f
        return (nearLeft || nearRight) && (nearTop || nearBottom)
    }

    private fun nearOppositeCorner(
        first: SupernoteGeometryPoint,
        second: SupernoteGeometryPoint,
        pageWidth: Float,
        pageHeight: Float
    ): Boolean {
        val nearLeft = { x: Float -> x <= pageWidth * 0.08f }
        val nearRight = { x: Float -> x >= pageWidth * 0.92f }
        val nearTop = { y: Float -> y <= pageHeight * 0.10f }
        val nearBottom = { y: Float -> y >= pageHeight * 0.90f }
        return (nearLeft(first.x) && nearBottom(first.y) && nearRight(second.x) && nearTop(second.y)) ||
            (nearRight(first.x) && nearTop(first.y) && nearLeft(second.x) && nearBottom(second.y)) ||
            (nearLeft(second.x) && nearBottom(second.y) && nearRight(first.x) && nearTop(first.y)) ||
            (nearRight(second.x) && nearTop(second.y) && nearLeft(first.x) && nearBottom(first.y))
    }

    private fun bounds(points: List<SupernoteGeometryPoint>): Pair<SupernoteGeometryPoint, SupernoteGeometryPoint>? {
        if (points.isEmpty()) return null
        return SupernoteGeometryPoint(points.minOf { it.x }, points.minOf { it.y }) to
            SupernoteGeometryPoint(points.maxOf { it.x }, points.maxOf { it.y })
    }

    private fun boundsSummary(segments: List<List<SupernoteGeometryPoint>>): String? {
        val points = segments.flatten()
        val bounds = bounds(points) ?: return null
        return "x=${bounds.first.x.toInt()}..${bounds.second.x.toInt()} y=${bounds.first.y.toInt()}..${bounds.second.y.toInt()}"
    }

    private fun segmentSpan(points: List<SupernoteGeometryPoint>): Float {
        if (points.size < 2) return 0f
        val first = points.first()
        val last = points.last()
        return hypot(last.x - first.x, last.y - first.y)
    }

    private fun pathLength(points: List<SupernoteGeometryPoint>): Float {
        var total = 0f
        for (index in 0 until points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            total += hypot(next.x - current.x, next.y - current.y)
        }
        return total
    }

    private fun formatFloat(value: Float): String = ((value * 10f).toInt() / 10f).toString()

    private enum class SegmentBreakReason {
        SENTINEL_OR_OUTLIER,
        SELF_INTERSECTION_CLUSTER,
        LOCAL_SUBPATH_RESET
    }

    private data class TransformCandidate(
        val mode: SupernotePreviewTransformMode,
        val score: Int,
        val segmentBreakCount: Int,
        val loopClosureBreakCount: Int,
        val localSubpathBreakCount: Int,
        val selfIntersectionBreakCount: Int,
        val outlierSegmentRejectedCount: Int,
        val compactLoopRejectedCount: Int,
        val compactBlobRejectedCount: Int,
        val maxSegmentSpan: Float,
        val subpathGroupingScore: Int,
        val records: List<SupernoteTransformedVectorRecord>,
        val summary: String
    )

    private data class SegmentedRecord(
        val segments: List<List<SupernoteGeometryPoint>>,
        val segmentBreakCount: Int,
        val loopClosureBreakCount: Int,
        val localSubpathBreakCount: Int,
        val selfIntersectionBreakCount: Int,
        val outlierSegmentRejectedCount: Int,
        val compactLoopRejectedCount: Int,
        val compactBlobRejectedCount: Int,
        val maxSegmentSpan: Float
    )

    private data class FinalizedSegment(
        val accepted: List<SupernoteGeometryPoint>?,
        val rejected: Boolean,
        val compactLoopRejected: Boolean,
        val span: Float
    )
}
