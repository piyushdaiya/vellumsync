package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteTotalPathStrokeProbeTest {
    @Test
    fun buildsTotalPathRecordBoundaryModelFromDeclaredCountAndSemanticMarkers() {
        val bytes = buildFakeOnePageNoteWithTwoTotalPathRecords()
        val containerReport = SupernoteContainerParser.parse(bytes)

        val report = SupernoteTotalPathStrokeProbe.probe(bytes, containerReport)

        assertEquals(1, report.pagesWithTotalPath)
        assertTrue(report.formatStatus.contains("record-boundary model"))
        assertTrue(report.totalEstimatedPayloadBytes > 0)

        val page = report.pageReports.first()
        assertEquals(1, page.pageNumber)
        assertEquals(220, page.totalPathOffset)
        assertEquals(900, page.pageSectionOffset)
        assertEquals(220, page.estimatedPayloadStartOffset)
        assertEquals(900, page.estimatedPayloadEndOffset)
        assertEquals(680, page.estimatedPayloadByteLength)
        assertEquals(676L, page.declaredPayloadSize)
        assertEquals(2, page.declaredRecordCount)
        assertEquals(true, page.headerSizeMatchesPayload)
        assertEquals(2, page.semanticRecordMarkerCount)
        assertEquals(true, page.recordCountMatchesSemanticMarkers)
        assertEquals(2, page.candidateStrokeRecordCount)
        assertEquals(2, page.candidateRecords.size)
        assertTrue(page.recordBoundaryModelStatus.contains("matches"))

        val first = page.candidateRecords[0]
        assertEquals(0, first.recordIndex)
        assertEquals("others", first.category)
        assertEquals(60, first.categoryMarkerRelativeOffset)
        assertEquals(0, first.estimatedRecordStartRelativeOffset)
        assertEquals(320, first.estimatedRecordEndRelativeOffset)
        assertEquals(316L, first.declaredRecordPayloadSize)
        assertTrue(first.decodedByLengthChain)
        assertNotNull(first.candidateBounds)
        assertNotNull(first.candidatePointRun)
        assertNotNull(first.decodedPointArray)
        assertEquals(8, first.decodedPointArray?.decodedPointCount)
        assertFalse(first.firstU32LeFields.isEmpty())

        val second = page.candidateRecords[1]
        assertEquals(1, second.recordIndex)
        assertEquals("straightLine", second.category)
        assertEquals(380, second.categoryMarkerRelativeOffset)
        assertEquals(320, second.estimatedRecordStartRelativeOffset)
        assertEquals(680, second.estimatedRecordEndRelativeOffset)
        assertEquals(356L, second.declaredRecordPayloadSize)
        assertTrue(second.decodedByLengthChain)
        assertNotNull(second.candidateBounds)
        assertNotNull(second.candidatePointRun)
        assertNotNull(second.decodedPointArray)
        assertEquals(8, second.decodedPointArray?.decodedPointCount)
    }

    @Test
    fun reportsPagesWithoutTotalPathWithoutCrashing() {
        val bytes = buildFakeNoteWithoutTotalPath()
        val containerReport = SupernoteContainerParser.parse(bytes)

        val report = SupernoteTotalPathStrokeProbe.probe(bytes, containerReport)

        assertEquals(0, report.pagesWithTotalPath)
        assertEquals(1, report.pageReports.size)
        assertNull(report.pageReports.first().totalPathOffset)
        assertTrue(report.probeWarnings.any { it.contains("do not expose") })
    }

    private fun buildFakeOnePageNoteWithTwoTotalPathRecords(): ByteArray {
        val bytes = ByteArray(1200) { '.'.code.toByte() }
        putText(
            bytes,
            0,
            "noteSN_FILE_VER_20230015<FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X>" +
                "<FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><PAGE1:900>"
        )
        putText(
            bytes,
            900,
            "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]>" +
                "<LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:120><BGLAYER:180>" +
                "<TOTALPATH:220><EXTERNALLINKINFO:0>"
        )

        val totalPathOffset = 220
        val totalPathLength = 680
        val firstRecordStart = totalPathOffset
        val firstRecordLength = 320
        val secondRecordStart = totalPathOffset + firstRecordLength
        val secondRecordLength = 360

        putU32Le(bytes, totalPathOffset, totalPathLength - 4L)
        putU32Le(bytes, totalPathOffset + 4, 2L)
        putU32Le(bytes, firstRecordStart + 8, firstRecordLength - 4L)
        putU32Le(bytes, secondRecordStart + 8, secondRecordLength - 4L)

        putText(bytes, firstRecordStart + 60, "others")
        putText(bytes, secondRecordStart + 60, "straightLine")

        putBounds(bytes, firstRecordStart + 108, 100, 200, 300, 400)
        putPointPairs(bytes, firstRecordStart + 224, startX = 12000, startY = 2500)

        putBounds(bytes, secondRecordStart + 108, 500, 600, 700, 800)
        putPointPairs(bytes, secondRecordStart + 224, startX = 13000, startY = 2600)

        return bytes
    }

    private fun putBounds(
        bytes: ByteArray,
        offset: Int,
        a: Long,
        b: Long,
        c: Long,
        d: Long
    ) {
        putU32Le(bytes, offset, a)
        putU32Le(bytes, offset + 4, b)
        putU32Le(bytes, offset + 8, c)
        putU32Le(bytes, offset + 12, d)
    }

    private fun putPointPairs(
        bytes: ByteArray,
        offset: Int,
        startX: Long,
        startY: Long
    ) {
        putU32Le(bytes, offset, 8L)
        var cursor = offset + 4
        repeat(8) { index ->
            putU32Le(bytes, cursor, startX + index * 10L)
            putU32Le(bytes, cursor + 4, startY + index * 4L)
            cursor += 8
        }
    }

    private fun buildFakeNoteWithoutTotalPath(): ByteArray {
        val header = "noteSN_FILE_VER_20230015<FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><PAGE1:180>"
        val page = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:10><BGLAYER:20><EXTERNALLINKINFO:0>"
        val builder = StringBuilder(header)
        while (builder.length < 180) builder.append('.')
        builder.append(page)
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun putText(bytes: ByteArray, offset: Int, text: String) {
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        textBytes.copyInto(bytes, destinationOffset = offset)
    }

    private fun putU32Le(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
    }
}
