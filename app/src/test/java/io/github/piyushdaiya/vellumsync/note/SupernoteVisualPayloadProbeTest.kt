package io.github.piyushdaiya.vellumsync.note

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteVisualPayloadProbeTest {
    @Test
    fun reportsLayerAndTotalPathPresencePerPageUsingInspectedReport() {
        val bytes = buildVisualNote(
            payload = encodeValueRun(
                rows = listOf(
                    rowWithSegments(10, 1 to 3),
                    rowWithSegments(10, 4 to 6),
                    rowWithSegments(10, 2 to 8),
                    rowWithSegments(10, 0 to 2)
                ),
                header = byteArrayOf(0x5C, 0x41, 0x01, 0x00)
            )
        )
        val inspection = SupernoteNoteInspector.inspect(
            fileName = "sync-test.note",
            fileSizeBytes = bytes.size.toLong(),
            bytes = bytes
        )

        val probe = SupernoteVisualPayloadProbe.probe(bytes = bytes, report = inspection, width = 10, height = 10)

        assertEquals(1, probe.pagesWithMainLayerBitmap)
        assertEquals(1, probe.pagesWithTotalPath)
        assertEquals("visual-and-totalpath", probe.pages.single().pageContentMode)
        assertTrue(probe.pages.single().layerPayloads.any { it.logicalLayerName == "MAINLAYER" && it.bitmapPayloadDetected })
    }

    private fun buildVisualNote(payload: ByteArray): ByteArray {
        val pagePlaceholder = "0000000000"
        val totalPathPlaceholder = "0000000000"
        val stream = ByteArrayOutputStream()

        val header = "noteSN_FILE_VER_20230015<MODULE_LABEL:none><FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><FILE_ID:F_PROBE><PAGE1:$pagePlaceholder>"
        stream.write(header.toByteArray(Charsets.ISO_8859_1))

        val mainPayloadOffset = stream.size()
        stream.write(payload)

        val mainMetadataOffset = stream.size()
        val mainLayer = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:MAINLAYER><LAYERPATH:0><LAYERBITMAP:${fixed(mainPayloadOffset)}><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        stream.write(mainLayer.toByteArray(Charsets.ISO_8859_1))

        val bgPayloadOffset = stream.size()
        stream.write(byteArrayOf(0xFC.toByte(), 0x01, 0x00, 0x00))

        val bgMetadataOffset = stream.size()
        val bgLayer = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:BGLAYER><LAYERPATH:0><LAYERBITMAP:${fixed(bgPayloadOffset)}><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        stream.write(bgLayer.toByteArray(Charsets.ISO_8859_1))

        val pageOffset = stream.size()
        val page = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:${fixed(mainMetadataOffset)}><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:${fixed(bgMetadataOffset)}><TOTALPATH:$totalPathPlaceholder><EXTERNALLINKINFO:0>"
        stream.write(page.toByteArray(Charsets.ISO_8859_1))

        val bytes = stream.toByteArray()
        patchFirst(bytes, pagePlaceholder, fixed(pageOffset))
        patchFirst(bytes, totalPathPlaceholder, fixed(pageOffset + page.length))
        return bytes
    }

    private fun encodeValueRun(rows: List<BooleanArray>, header: ByteArray): ByteArray {
        val linear = buildList<Boolean> { rows.forEach { row -> row.forEach { add(it) } } }
        val stream = ByteArrayOutputStream()
        stream.write(header)
        var index = 0
        while (index < linear.size) {
            val value = linear[index]
            var run = 1
            while (index + run < linear.size && linear[index + run] == value && run < 255) {
                run += 1
            }
            stream.write(if (value) 0x01 else 0x00)
            stream.write(run)
            index += run
        }
        return stream.toByteArray()
    }

    private fun rowWithSegments(width: Int, vararg segments: Pair<Int, Int>): BooleanArray {
        val row = BooleanArray(width) { false }
        segments.forEach { (startInclusive, endExclusive) ->
            for (index in startInclusive until endExclusive.coerceAtMost(width)) {
                if (index in 0 until width) row[index] = true
            }
        }
        return row
    }

    private fun fixed(value: Int): String = String.format("%010d", value)

    private fun patchFirst(bytes: ByteArray, target: String, replacement: String) {
        require(target.length == replacement.length)
        val haystack = bytes.toString(Charsets.ISO_8859_1)
        val index = haystack.indexOf(target)
        require(index >= 0) { "Target $target not found in note bytes." }
        replacement.toByteArray(Charsets.ISO_8859_1).copyInto(bytes, destinationOffset = index)
    }
}
