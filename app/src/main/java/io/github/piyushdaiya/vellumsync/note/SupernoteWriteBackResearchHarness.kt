package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Supernote write-back research and validation harness.
 *
 * This code never mutates the source Supernote .note file. All write-back
 * attempts are exported as copied candidate files so the candidate can be
 * validated by VellumSync, Supernote Partner, and a physical Supernote device
 * before any future sync workflow is allowed to overwrite a production note.
 */
object SupernoteWriteBackResearchHarness {
    private const val HARNESS_VERSION = "VELLUMSYNC_WRITEBACK_RESEARCH_V1"
    private const val SYNTHETIC_RECORD_CATEGORY = "others"
    private const val SYNTHETIC_POINT_COUNT_OFFSET = 224
    private const val SYNTHETIC_POINT_ARRAY_OFFSET = 228

    fun buildSafeCopy(originalNoteBytes: ByteArray): ByteArray {
        return originalNoteBytes.copyOf()
    }

    fun buildAppendProbeCopy(
        originalNoteBytes: ByteArray,
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        transformModeId: String,
        overlayStrokes: List<LocalAnnotationStroke>
    ): WriteBackResearchCandidate {
        val markerBlock = buildResearchMarkerBlock(
            noteFileName = noteFileName,
            noteSha256 = noteSha256,
            pageNumber = pageNumber,
            transformModeId = transformModeId,
            overlayStrokes = overlayStrokes
        )
        val markerBytes = markerBlock.toByteArray(Charsets.UTF_8)
        val out = ByteArray(originalNoteBytes.size + markerBytes.size)
        System.arraycopy(originalNoteBytes, 0, out, 0, originalNoteBytes.size)
        System.arraycopy(markerBytes, 0, out, originalNoteBytes.size, markerBytes.size)
        return WriteBackResearchCandidate(
            bytes = out,
            appendedMarkerBytes = markerBytes.size,
            markerBlock = markerBlock,
            originalSha256 = sha256(originalNoteBytes),
            candidateSha256 = sha256(out)
        )
    }

    /**
     * Build a copied .note candidate with VellumSync overlay strokes encoded as
     * provisional Supernote TOTALPATH records.
     *
     * The writer updates the selected page TOTALPATH header and inserts new
     * records before the page section. It also shifts known ASCII offset tags
     * that point to structural regions after the insertion point. This is still
     * a validation-lab writer, not a production sync writer.
     */
    fun buildTotalPathWriterCandidateCopy(
        report: SupernoteInspectionReport,
        originalNoteBytes: ByteArray,
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        transformModeId: String,
        overlayStrokes: List<LocalAnnotationStroke>
    ): SupernoteWriteBackWriterCandidate {
        val originalSha = sha256(originalNoteBytes)
        val pageSection = report.containerReport.pageSections.firstOrNull { it.pageNumber == pageNumber }
        val pageGeometry = report.strokeGeometryReport.pageReports.firstOrNull { it.pageNumber == pageNumber }
        val totalPathOffset = pageSection?.layerOffsets?.totalPathOffset
        val pageSectionOffset = pageSection?.pageSectionOffset
        val usableStrokes = overlayStrokes.filter { it.points.size >= 2 }
        val warnings = mutableListOf<String>()

        if (pageSection == null || totalPathOffset == null || pageSectionOffset == null) {
            warnings += "Selected page does not contain a parsed TOTALPATH offset; returning safe copy only."
            return safeNoopWriterCandidate(
                bytes = originalNoteBytes.copyOf(),
                originalSha = originalSha,
                pageNumber = pageNumber,
                warnings = warnings
            )
        }
        if (totalPathOffset < 0 || pageSectionOffset <= totalPathOffset || pageSectionOffset > originalNoteBytes.size) {
            warnings += "TOTALPATH boundary is invalid: totalPathOffset=$totalPathOffset pageSectionOffset=$pageSectionOffset fileBytes=${originalNoteBytes.size}."
            return safeNoopWriterCandidate(
                bytes = originalNoteBytes.copyOf(),
                originalSha = originalSha,
                pageNumber = pageNumber,
                warnings = warnings
            )
        }
        if (usableStrokes.isEmpty()) {
            warnings += "No local overlay strokes with at least two points were available for TOTALPATH write-back."
            return safeNoopWriterCandidate(
                bytes = originalNoteBytes.copyOf(),
                originalSha = originalSha,
                pageNumber = pageNumber,
                warnings = warnings
            )
        }

        val oldPayload = originalNoteBytes.copyOfRange(totalPathOffset, pageSectionOffset)
        if (oldPayload.size < 8) {
            warnings += "TOTALPATH payload is too small to contain declared size and record-count fields."
            return safeNoopWriterCandidate(
                bytes = originalNoteBytes.copyOf(),
                originalSha = originalSha,
                pageNumber = pageNumber,
                warnings = warnings
            )
        }

        val oldDeclaredSize = u32le(oldPayload, 0)
        val oldRecordCount = u32le(oldPayload, 4)
        if (oldRecordCount > 100_000L) {
            warnings += "Existing TOTALPATH record count is implausible: $oldRecordCount. Returning safe copy only."
            return safeNoopWriterCandidate(
                bytes = originalNoteBytes.copyOf(),
                originalSha = originalSha,
                pageNumber = pageNumber,
                warnings = warnings
            )
        }

        val recordBytes = usableStrokes.map { stroke ->
            buildSyntheticTotalPathRecord(
                stroke = stroke,
                pageGeometry = pageGeometry,
                transformModeId = transformModeId
            )
        }
        val insertedRecordBytes = recordBytes.sumOf { it.size }
        val newPayload = ByteArray(oldPayload.size + insertedRecordBytes)
        System.arraycopy(oldPayload, 0, newPayload, 0, oldPayload.size)
        var cursor = oldPayload.size
        recordBytes.forEach { record ->
            System.arraycopy(record, 0, newPayload, cursor, record.size)
            cursor += record.size
        }
        putU32le(newPayload, 0, newPayload.size.toLong() - 4L)
        putU32le(newPayload, 4, oldRecordCount + usableStrokes.size)

        val expanded = ByteArray(originalNoteBytes.size + insertedRecordBytes)
        System.arraycopy(originalNoteBytes, 0, expanded, 0, totalPathOffset)
        System.arraycopy(newPayload, 0, expanded, totalPathOffset, newPayload.size)
        System.arraycopy(
            originalNoteBytes,
            pageSectionOffset,
            expanded,
            totalPathOffset + newPayload.size,
            originalNoteBytes.size - pageSectionOffset
        )

        val shiftResult = shiftKnownOffsetTags(
            bytes = expanded,
            insertionOffset = pageSectionOffset,
            delta = insertedRecordBytes
        )
        val candidateBytes = shiftResult.bytes
        val candidateSha = sha256(candidateBytes)
        val candidateReport = runCatching {
            SupernoteNoteInspector.inspect(
                fileName = noteFileName,
                fileSizeBytes = candidateBytes.size.toLong(),
                bytes = candidateBytes,
                cachedCopyPath = null
            )
        }.getOrNull()
        val candidatePage = candidateReport?.totalPathProbeReport?.pageReports?.firstOrNull { it.pageNumber == pageNumber }
        val expectedCount = oldRecordCount + usableStrokes.size
        val parserCountMatches = candidatePage?.declaredRecordCount?.toLong() == expectedCount
        if (!parserCountMatches) {
            warnings += "Candidate parser record-count check did not match expected count. expected=$expectedCount parsed=${candidatePage?.declaredRecordCount}."
        }
        if (shiftResult.changedReferenceCount == 0) {
            warnings += "No shifted ASCII offset references were changed; verify candidate externally before use."
        }
        warnings += "Candidate is a copied validation artifact only; do not overwrite a production Supernote .note yet."

        return SupernoteWriteBackWriterCandidate(
            bytes = candidateBytes,
            originalSha256 = originalSha,
            candidateSha256 = candidateSha,
            pageNumber = pageNumber,
            insertionOffset = pageSectionOffset,
            oldTotalPathPayloadBytes = oldPayload.size,
            newTotalPathPayloadBytes = newPayload.size,
            oldDeclaredPayloadSize = oldDeclaredSize,
            newDeclaredPayloadSize = newPayload.size.toLong() - 4L,
            oldRecordCount = oldRecordCount,
            newRecordCount = expectedCount,
            insertedStrokeCount = usableStrokes.size,
            insertedRecordBytes = insertedRecordBytes,
            shiftedOffsetReferenceCount = shiftResult.changedReferenceCount,
            shiftedOffsetReferences = shiftResult.adjustments,
            parserReopenStatus = if (candidateReport == null) {
                "failed"
            } else {
                "parsed"
            },
            parsedCandidateRecordCount = candidatePage?.declaredRecordCount,
            parserRecordCountMatchesExpected = parserCountMatches,
            warnings = warnings
        )
    }

    fun buildResearchReportJson(
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        totalPages: Int,
        transformModeId: String,
        originalNoteBytes: ByteArray,
        overlayStrokes: List<LocalAnnotationStroke>,
        appendProbeCandidate: WriteBackResearchCandidate? = null
    ): String {
        val overlayPointCount = overlayStrokes.sumOf { it.points.size }
        val firstStroke = overlayStrokes.firstOrNull()
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync Supernote write-back research harness report")},")
            append("\"version\":1,")
            append("\"harnessVersion\":${JsonText.quote(HARNESS_VERSION)},")
            append("\"safetyStatus\":${JsonText.quote("copy-only; original Supernote .note is never modified")},")
            append("\"writeBackStatus\":${JsonText.quote("experimental disabled; generated files are research copies only")},")
            append("\"note\":{")
            append("\"fileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"originalBytes\":${originalNoteBytes.size},")
            append("\"originalComputedSha256\":${JsonText.quote(sha256(originalNoteBytes))},")
            append("\"pageNumber\":$pageNumber,")
            append("\"totalPages\":$totalPages,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)}")
            append("},")
            append("\"overlayInput\":{")
            append("\"strokeCount\":${overlayStrokes.size},")
            append("\"pointCount\":$overlayPointCount,")
            append("\"firstStroke\":")
            if (firstStroke == null) {
                append("null")
            } else {
                append("{")
                append("\"id\":${JsonText.quote(firstStroke.id)},")
                append("\"pointCount\":${firstStroke.points.size},")
                append("\"color\":${JsonText.quote(firstStroke.style.color.id)},")
                append("\"width\":${JsonText.quote(firstStroke.style.width.id)},")
                append("\"transformModeId\":${JsonText.quote(firstStroke.transformModeId)}")
                append("}")
            }
            append("},")
            append("\"generatedArtifacts\":{")
            append("\"safeCopy\":{")
            append("\"description\":${JsonText.quote("byte-for-byte copy of cached/imported .note for safe external compatibility testing")},")
            append("\"mutatesOriginal\":false,")
            append("\"expectedSha256\":${JsonText.quote(sha256(originalNoteBytes))}")
            append("},")
            append("\"appendProbeCopy\":")
            if (appendProbeCandidate == null) {
                append("null")
            } else {
                append("{")
                append("\"description\":${JsonText.quote("research-only copy with VellumSync marker block appended after original bytes")},")
                append("\"mutatesOriginal\":false,")
                append("\"supernoteCompatibilityExpected\":${JsonText.quote("unknown; may be rejected by Supernote; use only for parser/device research")},")
                append("\"candidateBytes\":${appendProbeCandidate.bytes.size},")
                append("\"appendedMarkerBytes\":${appendProbeCandidate.appendedMarkerBytes},")
                append("\"originalSha256\":${JsonText.quote(appendProbeCandidate.originalSha256)},")
                append("\"candidateSha256\":${JsonText.quote(appendProbeCandidate.candidateSha256)}")
                append("}")
            }
            append("},")
            append("\"nextResearchQuestions\":[")
            append(JsonText.quote("Can Supernote device/app open an unmodified safe copy exported by VellumSync?"))
            append(",")
            append(JsonText.quote("Can a copied candidate with updated TOTALPATH records be opened by VellumSync and external Supernote tools?"))
            append(",")
            append(JsonText.quote("Which visual-layer or page-table fields must be refreshed for Supernote device compatibility?"))
            append("],")
            append("\"warnings\":[")
            append(JsonText.quote("Do not sync append-probe or writer-candidate copies back to a production Supernote notebook."))
            append(",")
            append(JsonText.quote("Writer validation is not complete; original .note files remain read-only."))
            append(",")
            append(JsonText.quote("Append probe is intentionally not claimed to be a valid Supernote write-back format."))
            append("]")
            append("}")
        }
    }

    fun buildCompatibilityValidationReportJson(
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        totalPages: Int,
        transformModeId: String,
        originalNoteBytes: ByteArray,
        overlayStrokes: List<LocalAnnotationStroke>
    ): String {
        val safeCopy = buildSafeCopy(originalNoteBytes)
        val appendProbeCandidate = buildAppendProbeCopy(
            originalNoteBytes = originalNoteBytes,
            noteFileName = noteFileName,
            noteSha256 = noteSha256,
            pageNumber = pageNumber,
            transformModeId = transformModeId,
            overlayStrokes = overlayStrokes
        )
        val markerOffset = indexOfAscii(appendProbeCandidate.bytes, HARNESS_VERSION)
        val originalVersionMarker = findFirstAsciiMarker(originalNoteBytes, listOf("SN_FILE_VER_20230015", "SN_FILE_VER_"))
        val safeVersionMarker = findFirstAsciiMarker(safeCopy, listOf("SN_FILE_VER_20230015", "SN_FILE_VER_"))
        val appendVersionMarker = findFirstAsciiMarker(appendProbeCandidate.bytes, listOf("SN_FILE_VER_20230015", "SN_FILE_VER_"))
        val prefixUnchanged = appendProbeCandidate.bytes.size >= originalNoteBytes.size &&
            originalNoteBytes.indices.all { index -> originalNoteBytes[index] == appendProbeCandidate.bytes[index] }
        val safeCopySameBytes = originalNoteBytes.contentEquals(safeCopy)
        val originalSha = sha256(originalNoteBytes)
        val safeSha = sha256(safeCopy)
        val pageMarkers = countAsciiOccurrences(originalNoteBytes, "<PAGE")
        val totalPathMarkers = countAsciiOccurrences(originalNoteBytes, "TOTALPATH")
        val overlayPointCount = overlayStrokes.sumOf { it.points.size }
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync Supernote write-back compatibility validation report")},")
            append("\"version\":1,")
            append("\"harnessVersion\":${JsonText.quote(HARNESS_VERSION)},")
            append("\"safetyStatus\":${JsonText.quote("validation-only; source and cached .note bytes are not modified")},")
            append("\"compatibilityStatus\":${JsonText.quote("internal validation only; external Supernote device/app validation is still required")},")
            append("\"note\":{")
            append("\"fileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"computedOriginalSha256\":${JsonText.quote(originalSha)},")
            append("\"pageNumber\":$pageNumber,")
            append("\"totalPages\":$totalPages,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"originalBytes\":${originalNoteBytes.size},")
            append("\"versionMarker\":${JsonText.quote(originalVersionMarker ?: "not-detected")},")
            append("\"pageMarkerCount\":$pageMarkers,")
            append("\"totalPathMarkerCount\":$totalPathMarkers")
            append("},")
            append("\"safeCopyValidation\":{")
            append("\"safeCopyBytes\":${safeCopy.size},")
            append("\"safeCopySha256\":${JsonText.quote(safeSha)},")
            append("\"byteForByteEqualToOriginal\":$safeCopySameBytes,")
            append("\"hashMatchesOriginal\":${safeSha == originalSha},")
            append("\"versionMarkerPreserved\":${safeVersionMarker == originalVersionMarker}")
            append("},")
            append("\"appendProbeValidation\":{")
            append("\"candidateBytes\":${appendProbeCandidate.bytes.size},")
            append("\"candidateSha256\":${JsonText.quote(appendProbeCandidate.candidateSha256)},")
            append("\"originalPrefixUnchanged\":$prefixUnchanged,")
            append("\"appendedMarkerBytes\":${appendProbeCandidate.appendedMarkerBytes},")
            append("\"markerOffset\":$markerOffset,")
            append("\"markerOffsetEqualsOriginalSize\":${markerOffset == originalNoteBytes.size + 1 || markerOffset == originalNoteBytes.size + 2 || markerOffset == originalNoteBytes.size},")
            append("\"versionMarkerPreserved\":${appendVersionMarker == originalVersionMarker},")
            append("\"supernoteCompatibilityExpected\":${JsonText.quote("unknown; this is a research artifact and may be rejected")}")
            append("},")
            append("\"overlayInput\":{")
            append("\"strokeCount\":${overlayStrokes.size},")
            append("\"pointCount\":$overlayPointCount,")
            append("\"firstStrokeId\":${JsonText.quote(overlayStrokes.firstOrNull()?.id ?: "none")}")
            append("},")
            append("\"recommendedExternalTests\":[")
            append(JsonText.quote("Open the byte-for-byte safe copy in Supernote Partner or on the Supernote device."))
            append(",")
            append(JsonText.quote("Confirm the safe copy renders identically and does not trigger repair or rejection."))
            append(",")
            append(JsonText.quote("Open append-probe copies only as disposable research tests, never as production notebooks."))
            append("],")
            append("\"acceptanceChecks\":{")
            append("\"sourceUnmodified\":true,")
            append("\"safeCopyMatchesOriginal\":$safeCopySameBytes,")
            append("\"appendProbeKeepsOriginalPrefix\":$prefixUnchanged,")
            append("\"readyForWriteBackImplementation\":false")
            append("},")
            append("\"warnings\":[")
            append(JsonText.quote("This report does not validate Supernote write-back compatibility by itself."))
            append(",")
            append(JsonText.quote("Do not sync append-probe copies into real Supernote notebooks."))
            append(",")
            append(JsonText.quote("A real writer must update Supernote container structures and page offsets, not append a marker block."))
            append("]")
            append("}")
        }
    }

    fun buildWriterValidationReportJson(
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        totalPages: Int,
        transformModeId: String,
        originalNoteBytes: ByteArray,
        overlayStrokes: List<LocalAnnotationStroke>,
        writerCandidate: SupernoteWriteBackWriterCandidate
    ): String {
        val originalVersionMarker = findFirstAsciiMarker(originalNoteBytes, listOf("SN_FILE_VER_20230015", "SN_FILE_VER_"))
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync Supernote write-back writer validation report")},")
            append("\"version\":1,")
            append("\"harnessVersion\":${JsonText.quote(HARNESS_VERSION)},")
            append("\"safetyStatus\":${JsonText.quote("copy-only writer; original .note bytes are never modified")},")
            append("\"compatibilityStatus\":${JsonText.quote("candidate generated for validation; external Supernote Partner/device test required")},")
            append("\"note\":{")
            append("\"fileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"pageNumber\":$pageNumber,")
            append("\"totalPages\":$totalPages,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"originalBytes\":${originalNoteBytes.size},")
            append("\"versionMarker\":${JsonText.quote(originalVersionMarker ?: "not-detected")}")
            append("},")
            append("\"overlayInput\":{")
            append("\"strokeCount\":${overlayStrokes.size},")
            append("\"pointCount\":${overlayStrokes.sumOf { it.points.size }},")
            append("\"firstStrokeId\":${JsonText.quote(overlayStrokes.firstOrNull()?.id ?: "none")}")
            append("},")
            append("\"writerCandidate\":${writerCandidate.toJson()},")
            append("\"validationChecklist\":[")
            append(JsonText.quote("Open candidate in VellumSync and confirm the selected page record count increased by insertedStrokeCount."))
            append(",")
            append(JsonText.quote("Open candidate in Supernote Partner as a disposable copy."))
            append(",")
            append(JsonText.quote("Open candidate on the physical Supernote A5X only after safe-copy validation succeeds."))
            append(",")
            append(JsonText.quote("Confirm Supernote does not repair, reject, or drop the candidate strokes."))
            append("],")
            append("\"warnings\":[")
            append(JsonText.quote("Candidate writes only copied bytes; never overwrite production .note files from this lab."))
            append(",")
            append(JsonText.quote("Visual RATTA_RLE layers and thumbnails may still be stale until visual refresh support is implemented."))
            append(",")
            append(JsonText.quote("Supernote compatibility is not complete until tested on Supernote Partner and device firmware."))
            append("]")
            append("}")
        }
    }

    private fun buildSyntheticTotalPathRecord(
        stroke: LocalAnnotationStroke,
        pageGeometry: SupernoteStrokeGeometryPageReport?,
        transformModeId: String
    ): ByteArray {
        val rawPoints = stroke.points
            .take(4096)
            .map { point -> point.toRawSupernotePoint(pageGeometry, transformModeId) }
            .ifEmpty { listOf(SupernoteRawPoint(2500, 9000), SupernoteRawPoint(2600, 9100)) }
        val recordLength = align4(SYNTHETIC_POINT_ARRAY_OFFSET + rawPoints.size * 8)
        val declaredRecordPayloadSize = recordLength.toLong() - 4L
        val bytes = ByteArray(recordLength)
        putU32le(bytes, 0, 0)
        putU32le(bytes, 4, 0)
        putU32le(bytes, 8, declaredRecordPayloadSize)
        putU32le(bytes, 12, 1)
        putU32le(bytes, 16, 0)
        putU32le(bytes, 20, stroke.style.width.toSupernoteWidthSignal())
        putU32le(bytes, 24, 10)
        putU32le(bytes, 28, 0)
        putU32le(bytes, 32, 32)
        putU32le(bytes, 36, 0xffffffffL)
        putU32le(bytes, 40, stroke.style.color.toSupernoteColorSignal())
        putU32le(bytes, 44, 0)
        putU32le(bytes, 48, 0)
        putU32le(bytes, 52, 5000)
        putU32le(bytes, 56, 0)
        SYNTHETIC_RECORD_CATEGORY.toByteArray(Charsets.US_ASCII).forEachIndexed { index, value ->
            bytes[60 + index] = value
        }
        writeBoundsHint(bytes, rawPoints)
        putU32le(bytes, SYNTHETIC_POINT_COUNT_OFFSET, rawPoints.size.toLong())
        var offset = SYNTHETIC_POINT_ARRAY_OFFSET
        rawPoints.forEach { point ->
            putU32le(bytes, offset, point.x)
            putU32le(bytes, offset + 4, point.y)
            offset += 8
        }
        return bytes
    }

    private fun writeBoundsHint(bytes: ByteArray, points: List<SupernoteRawPoint>) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        putU32le(bytes, 108, 0)
        putU32le(bytes, 112, (minY / 32L).coerceIn(0, 20_000))
        putU32le(bytes, 116, (minX / 16L).coerceIn(0, 20_000))
        putU32le(bytes, 120, (maxY / 32L).coerceIn(0, 20_000))
        putU32le(bytes, 124, (maxX / 16L).coerceIn(0, 20_000))
    }

    private fun LocalAnnotationPoint.toRawSupernotePoint(
        pageGeometry: SupernoteStrokeGeometryPageReport?,
        transformModeId: String
    ): SupernoteRawPoint {
        val pageWidth = (pageGeometry?.pageWidth ?: 1404f).coerceAtLeast(1f)
        val pageHeight = (pageGeometry?.pageHeight ?: 1872f).coerceAtLeast(1f)
        val rawBounds = pageGeometry?.rawBounds
        val normalizedX = (x / pageWidth).coerceIn(0f, 1f)
        val normalizedY = (y / pageHeight).coerceIn(0f, 1f)
        val fallbackMinX = 2200L
        val fallbackMaxX = 3600L
        val fallbackMinY = 7200L
        val fallbackMaxY = 12800L
        val minX = rawBounds?.minX ?: fallbackMinX
        val maxX = rawBounds?.maxX ?: fallbackMaxX
        val minY = rawBounds?.minY ?: fallbackMinY
        val maxY = rawBounds?.maxY ?: fallbackMaxY
        val spanX = max(1L, maxX - minX)
        val spanY = max(1L, maxY - minY)
        val rawX = when (transformModeId) {
            "a5x-portrait-candidate-2" -> minX + ((1f - normalizedY) * spanX).roundToLong()
            "a5x-portrait-candidate" -> minX + (normalizedY * spanX).roundToLong()
            else -> minX + (normalizedX * spanX).roundToLong()
        }
        val rawY = when (transformModeId) {
            "a5x-portrait-candidate-2" -> minY + (normalizedX * spanY).roundToLong()
            "a5x-portrait-candidate" -> minY + ((1f - normalizedX) * spanY).roundToLong()
            else -> minY + (normalizedY * spanY).roundToLong()
        }
        return SupernoteRawPoint(
            x = rawX.coerceIn(0L, 100_000L),
            y = rawY.coerceIn(0L, 100_000L)
        )
    }

    private fun LocalAnnotationWidth.toSupernoteWidthSignal(): Long {
        return when (this) {
            LocalAnnotationWidth.THIN -> 300L
            LocalAnnotationWidth.MEDIUM -> 400L
            LocalAnnotationWidth.THICK -> 700L
        }
    }

    private fun LocalAnnotationColor.toSupernoteColorSignal(): Long {
        return when (this) {
            LocalAnnotationColor.WHITE -> 0L
            LocalAnnotationColor.LIGHT_GRAY -> 3L
            LocalAnnotationColor.DARK_GRAY -> 2L
            LocalAnnotationColor.BLACK -> 1L
        }
    }

    private fun shiftKnownOffsetTags(
        bytes: ByteArray,
        insertionOffset: Int,
        delta: Int
    ): OffsetShiftResult {
        if (delta == 0) return OffsetShiftResult(bytes, 0, emptyList())
        val ascii = bytes.toString(Charsets.ISO_8859_1)
        val adjustments = mutableListOf<OffsetReferenceAdjustment>()
        val regex = Regex("<(PAGE\\d+|MAINLAYER|BGLAYER|LAYER1|LAYER2|LAYER3|TOTALPATH|RECOGNTEXT|RECOGNFILE|IDTABLE|PAGETEXTBOX|COVER_0|TITLEBITMAP|KEYWORDBITMAP|LINKBITMAP|LAYERINFO|LAYERSEQ):([0-9]+)>")
        val shifted = regex.replace(ascii) { match ->
            val tag = match.groupValues[1]
            val oldValue = match.groupValues[2].toLongOrNull() ?: return@replace match.value
            if (oldValue > 0 && oldValue >= insertionOffset) {
                val newValue = oldValue + delta
                adjustments += OffsetReferenceAdjustment(tag = tag, oldOffset = oldValue, newOffset = newValue)
                "<$tag:$newValue>"
            } else {
                match.value
            }
        }
        return OffsetShiftResult(
            bytes = shifted.toByteArray(Charsets.ISO_8859_1),
            changedReferenceCount = adjustments.size,
            adjustments = adjustments.take(128)
        )
    }

    private fun safeNoopWriterCandidate(
        bytes: ByteArray,
        originalSha: String,
        pageNumber: Int,
        warnings: List<String>
    ): SupernoteWriteBackWriterCandidate {
        return SupernoteWriteBackWriterCandidate(
            bytes = bytes,
            originalSha256 = originalSha,
            candidateSha256 = sha256(bytes),
            pageNumber = pageNumber,
            insertionOffset = null,
            oldTotalPathPayloadBytes = null,
            newTotalPathPayloadBytes = null,
            oldDeclaredPayloadSize = null,
            newDeclaredPayloadSize = null,
            oldRecordCount = null,
            newRecordCount = null,
            insertedStrokeCount = 0,
            insertedRecordBytes = 0,
            shiftedOffsetReferenceCount = 0,
            shiftedOffsetReferences = emptyList(),
            parserReopenStatus = "not-attempted",
            parsedCandidateRecordCount = null,
            parserRecordCountMatchesExpected = false,
            warnings = warnings
        )
    }

    private fun buildResearchMarkerBlock(
        noteFileName: String,
        noteSha256: String,
        pageNumber: Int,
        transformModeId: String,
        overlayStrokes: List<LocalAnnotationStroke>
    ): String {
        val exportedStroke = overlayStrokes.firstOrNull()
        return buildString {
            append("\n<$HARNESS_VERSION>\n")
            append("status=research-only-copy\n")
            append("sourceNoteFileName=").append(noteFileName).append("\n")
            append("sourceNoteSha256=").append(noteSha256).append("\n")
            append("pageNumber=").append(pageNumber).append("\n")
            append("transformModeId=").append(transformModeId).append("\n")
            append("overlayStrokeCount=").append(overlayStrokes.size).append("\n")
            append("overlayPointCount=").append(overlayStrokes.sumOf { it.points.size }).append("\n")
            if (exportedStroke != null) {
                append("candidateStrokeId=").append(exportedStroke.id).append("\n")
                append("candidateStrokeColor=").append(exportedStroke.style.color.id).append("\n")
                append("candidateStrokeWidth=").append(exportedStroke.style.width.id).append("\n")
                append("candidateStrokePointCount=").append(exportedStroke.points.size).append("\n")
                append("candidateStrokePoints=")
                append(exportedStroke.points.take(256).joinToString(separator = ";") { point ->
                    "${point.x.formatResearchFloat()},${point.y.formatResearchFloat()}"
                })
                append("\n")
            } else {
                append("candidateStrokeId=none\n")
            }
            append("note=This marker block is appended for research only and is not a validated Supernote write-back section.\n")
            append("</$HARNESS_VERSION>\n")
        }
    }

    private fun findFirstAsciiMarker(bytes: ByteArray, markers: List<String>): String? {
        return markers.firstOrNull { marker -> indexOfAscii(bytes, marker) >= 0 }
    }

    private fun countAsciiOccurrences(bytes: ByteArray, marker: String): Int {
        if (marker.isEmpty()) return 0
        val needle = marker.toByteArray(Charsets.UTF_8)
        var count = 0
        var index = 0
        while (index <= bytes.size - needle.size) {
            if (matchesAt(bytes, needle, index)) {
                count++
                index += needle.size
            } else {
                index++
            }
        }
        return count
    }

    private fun indexOfAscii(bytes: ByteArray, marker: String): Int {
        if (marker.isEmpty()) return -1
        val needle = marker.toByteArray(Charsets.UTF_8)
        for (index in 0..(bytes.size - needle.size).coerceAtLeast(-1)) {
            if (index >= 0 && matchesAt(bytes, needle, index)) return index
        }
        return -1
    }

    private fun matchesAt(bytes: ByteArray, needle: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + needle.size > bytes.size) return false
        for (i in needle.indices) {
            if (bytes[offset + i] != needle[i]) return false
        }
        return true
    }

    private fun u32le(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0L
        return (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun putU32le(bytes: ByteArray, offset: Int, value: Long) {
        if (offset < 0 || offset + 4 > bytes.size) return
        bytes[offset] = (value and 0xffL).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xffL).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xffL).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xffL).toByte()
    }

    private fun align4(value: Int): Int {
        return if (value % 4 == 0) value else value + (4 - value % 4)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { value -> "%02x".format(value.toInt() and 0xff) }
    }
}

data class WriteBackResearchCandidate(
    val bytes: ByteArray,
    val appendedMarkerBytes: Int,
    val markerBlock: String,
    val originalSha256: String,
    val candidateSha256: String
)

data class OffsetReferenceAdjustment(
    val tag: String,
    val oldOffset: Long,
    val newOffset: Long
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"tag\":${JsonText.quote(tag)},")
            append("\"oldOffset\":$oldOffset,")
            append("\"newOffset\":$newOffset")
            append("}")
        }
    }
}

private data class OffsetShiftResult(
    val bytes: ByteArray,
    val changedReferenceCount: Int,
    val adjustments: List<OffsetReferenceAdjustment>
)

data class SupernoteWriteBackWriterCandidate(
    val bytes: ByteArray,
    val originalSha256: String,
    val candidateSha256: String,
    val pageNumber: Int,
    val insertionOffset: Int?,
    val oldTotalPathPayloadBytes: Int?,
    val newTotalPathPayloadBytes: Int?,
    val oldDeclaredPayloadSize: Long?,
    val newDeclaredPayloadSize: Long?,
    val oldRecordCount: Long?,
    val newRecordCount: Long?,
    val insertedStrokeCount: Int,
    val insertedRecordBytes: Int,
    val shiftedOffsetReferenceCount: Int,
    val shiftedOffsetReferences: List<OffsetReferenceAdjustment>,
    val parserReopenStatus: String,
    val parsedCandidateRecordCount: Int?,
    val parserRecordCountMatchesExpected: Boolean,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"candidateBytes\":${bytes.size},")
            append("\"originalSha256\":${JsonText.quote(originalSha256)},")
            append("\"candidateSha256\":${JsonText.quote(candidateSha256)},")
            append("\"pageNumber\":$pageNumber,")
            append("\"insertionOffset\":${insertionOffset ?: "null"},")
            append("\"oldTotalPathPayloadBytes\":${oldTotalPathPayloadBytes ?: "null"},")
            append("\"newTotalPathPayloadBytes\":${newTotalPathPayloadBytes ?: "null"},")
            append("\"oldDeclaredPayloadSize\":${oldDeclaredPayloadSize ?: "null"},")
            append("\"newDeclaredPayloadSize\":${newDeclaredPayloadSize ?: "null"},")
            append("\"oldRecordCount\":${oldRecordCount ?: "null"},")
            append("\"newRecordCount\":${newRecordCount ?: "null"},")
            append("\"insertedStrokeCount\":$insertedStrokeCount,")
            append("\"insertedRecordBytes\":$insertedRecordBytes,")
            append("\"shiftedOffsetReferenceCount\":$shiftedOffsetReferenceCount,")
            append("\"shiftedOffsetReferences\":[")
            append(shiftedOffsetReferences.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"parserReopenStatus\":${JsonText.quote(parserReopenStatus)},")
            append("\"parsedCandidateRecordCount\":${parsedCandidateRecordCount ?: "null"},")
            append("\"parserRecordCountMatchesExpected\":$parserRecordCountMatchesExpected,")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

private fun Float.formatResearchFloat(): String {
    return String.format(Locale.US, "%.3f", this)
}
