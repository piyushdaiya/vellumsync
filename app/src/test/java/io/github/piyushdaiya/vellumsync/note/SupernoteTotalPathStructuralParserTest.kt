package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-snlib-guided-totalpath-structural-parser-v0-test
class SupernoteTotalPathStructuralParserTest {
    @Test
    fun buildsStructuredStrokeInspectorFromRecordBoundaries() {
        val bytes = buildFakeOnePageNoteWithTwoTotalPathRecords()
        val containerReport = SupernoteContainerParser.parse(bytes)
        val probeReport = SupernoteTotalPathStrokeProbe.probe(bytes, containerReport)

        val report = SupernoteTotalPathStructuralParser.parse(
            bytes = bytes,
            containerReport = containerReport,
            probeReport = probeReport,
            pageNumberFilter = 1
        )

        assertEquals(1, report.pageReports.size)
        val page = report.pageReports.first()
        assertEquals(1, page.pageNumber)
        assertEquals(2, page.strokeCount)
        assertTrue(page.strokes.first().decodedByLengthChain)
        assertEquals("record-structure-length-chain", page.strokes.first().boundarySource)
        assertEquals(8, page.strokes.first().decodedPointCount)
        assertTrue(page.strokes.first().points.isNotEmpty())
        assertTrue(report.singlePageJson(1).contains("\"strokeCount\":2"))
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

    private fun putBounds(bytes: ByteArray, offset: Int, a: Long, b: Long, c: Long, d: Long) {
        putU32Le(bytes, offset, a)
        putU32Le(bytes, offset + 4, b)
        putU32Le(bytes, offset + 8, c)
        putU32Le(bytes, offset + 12, d)
    }

    private fun putPointPairs(bytes: ByteArray, offset: Int, startX: Long, startY: Long) {
        putU32Le(bytes, offset, 8L)
        var cursor = offset + 4
        repeat(8) { index ->
            putU32Le(bytes, cursor, startX + index * 10L)
            putU32Le(bytes, cursor + 4, startY + index * 4L)
            cursor += 8
        }
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
