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
        assertEquals(720, page.pageSectionOffset)
        assertEquals(220, page.estimatedPayloadStartOffset)
        assertEquals(720, page.estimatedPayloadEndOffset)
        assertEquals(500, page.estimatedPayloadByteLength)
        assertEquals(496L, page.declaredPayloadSize)
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
        assertEquals(240, first.estimatedRecordEndRelativeOffset)
        assertNotNull(first.candidateBounds)
        assertNotNull(first.candidatePointRun)
        assertFalse(first.firstU32LeFields.isEmpty())

        val second = page.candidateRecords[1]
        assertEquals(1, second.recordIndex)
        assertEquals("straightLine", second.category)
        assertEquals(300, second.categoryMarkerRelativeOffset)
        assertEquals(240, second.estimatedRecordStartRelativeOffset)
        assertEquals(500, second.estimatedRecordEndRelativeOffset)
        assertNotNull(second.candidateBounds)
        assertNotNull(second.candidatePointRun)
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
        val bytes = ByteArray(960) { '.'.code.toByte() }
        putText(
            bytes,
            0,
            "noteSN_FILE_VER_20230015<FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X>" +
                "<FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><PAGE1:720>"
        )
        putText(
            bytes,
            720,
            "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]>" +
                "<LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:120><BGLAYER:180>" +
                "<TOTALPATH:220><EXTERNALLINKINFO:0>"
        )

        val totalPathOffset = 220
        val totalPathLength = 500
        putU32Le(bytes, totalPathOffset, totalPathLength - 4L)
        putU32Le(bytes, totalPathOffset + 4, 2L)

        putText(bytes, totalPathOffset + 60, "others")
        putText(bytes, totalPathOffset + 300, "straightLine")

        putBounds(bytes, totalPathOffset + 108, 100, 200, 300, 400)
        putPointPairs(bytes, totalPathOffset + 128, startX = 12000, startY = 2500)

        val secondRecordStart = totalPathOffset + 240
        putBounds(bytes, secondRecordStart + 108, 500, 600, 700, 800)
        putPointPairs(bytes, secondRecordStart + 128, startX = 13000, startY = 2600)

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
        var cursor = offset
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
