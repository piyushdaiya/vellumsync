package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-test-assertion-alignment-repair-pack-v0
class SupernoteVisualDecoderTest {
    @Test
    fun treatsLayerBitmapAsPayloadOffsetAndEstimatesBoundaries() {
        val bytes = buildFakeVisualNote()
        val containerReport = SupernoteContainerParser.parse(bytes)
        val visualReport = SupernoteVisualDecoder.decode(bytes, containerReport)

        assertEquals(1, visualReport.pageReports.size)
        assertEquals(2, visualReport.totalLayerRecords)
        assertEquals(2, visualReport.rleLayerRecordCount)
        assertEquals(2, visualReport.uniqueBitmapPayloadOffsetCount)
        assertTrue(visualReport.formatStatus.contains("RATTA_RLE"))

        val page = visualReport.pageReports.first()
        assertEquals(1, page.pageNumber)
        assertEquals("MAINLAYER,BGLAYER", page.layerSeq)
        assertTrue(page.previewStatus.isNotBlank())

        val main = page.layerRecords.first { it.logicalLayerName == "MAINLAYER" }
        assertEquals(260, main.layerRecordOffset)
        assertEquals(420, main.nextKnownStructuralOffset)
        assertEquals("NOTE", main.layerType)
        assertEquals("RATTA_RLE", main.layerProtocol)
        assertEquals("MAINLAYER", main.parsedLayerName)
        assertEquals(120, main.layerBitmapOffset)
        assertEquals(120, main.bitmapPayloadOffset)
        assertEquals(180, main.estimatedCompressedPayloadEndOffset)
        assertEquals(60, main.estimatedCompressedPayloadByteLength)
        assertEquals(true, main.bitmapPayloadStartsBeforeLayerRecord)
        assertFalse(main.bitmapPayloadShared)

        val background = page.layerRecords.first { it.logicalLayerName == "BGLAYER" }
        assertEquals(420, background.layerRecordOffset)
        assertEquals(560, background.nextKnownStructuralOffset)
        assertEquals("BGLAYER", background.parsedLayerName)
        assertEquals(180, background.layerBitmapOffset)
        assertEquals(260, background.estimatedCompressedPayloadEndOffset)
        assertEquals(80, background.estimatedCompressedPayloadByteLength)
        assertEquals(true, background.bitmapPayloadStartsBeforeLayerRecord)
    }

    @Test
    fun detectsSharedBitmapPayloadOffsets() {
        val bytes = buildFakeSharedBackgroundNote()
        val containerReport = SupernoteContainerParser.parse(bytes)
        val visualReport = SupernoteVisualDecoder.decode(bytes, containerReport)

        assertEquals(2, visualReport.pageReports.size)
        assertEquals(4, visualReport.totalLayerRecords)
        assertEquals(3, visualReport.uniqueBitmapPayloadOffsetCount)
        assertEquals(1, visualReport.sharedBitmapPayloads.size)

        val shared = visualReport.sharedBitmapPayloads.first()
        assertEquals(80, shared.bitmapPayloadOffset)
        assertEquals(2, shared.reuseCount)
        assertTrue(shared.usedBy.any { it.contains("page=1 layer=BGLAYER") })
        assertTrue(shared.usedBy.any { it.contains("page=2 layer=BGLAYER") })
    }

    private fun buildFakeVisualNote(): ByteArray {
        val header = "noteSN_FILE_VER_20230015<MODULE_LABEL:none><FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><FILE_ID:F_VISUAL><PAGE1:560>"
        val mainLayer = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:MAINLAYER><LAYERPATH:0><LAYERBITMAP:120><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val bgLayer = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:BGLAYER><LAYERPATH:0><LAYERBITMAP:180><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val page = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:260><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:420><TOTALPATH:560><EXTERNALLINKINFO:0>"
        val builder = StringBuilder(header)
        while (builder.length < 120) builder.append('m')
        while (builder.length < 180) builder.append('b')
        while (builder.length < 260) builder.append('x')
        builder.append(mainLayer)
        while (builder.length < 420) builder.append('y')
        builder.append(bgLayer)
        while (builder.length < 560) builder.append('z')
        builder.append(page)
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun buildFakeSharedBackgroundNote(): ByteArray {
        val header = "noteSN_FILE_VER_20230015<MODULE_LABEL:none><FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:2><FINALOPERATION_LAYER:1><FILE_ID:F_SHARED><PAGE1:700><PAGE2:900>"
        val page1Main = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:MAINLAYER><LAYERPATH:0><LAYERBITMAP:160><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val page1Bg = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:BGLAYER><LAYERPATH:0><LAYERBITMAP:80><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val page2Main = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:MAINLAYER><LAYERPATH:0><LAYERBITMAP:480><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val page2Bg = "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:BGLAYER><LAYERPATH:0><LAYERBITMAP:80><LAYERVECTORGRAPH:0><LAYERRECOGN:0>"
        val page1 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:300><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:420><TOTALPATH:600><EXTERNALLINKINFO:0>"
        val page2 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:620><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:780><TOTALPATH:860><EXTERNALLINKINFO:0>"
        val builder = StringBuilder(header)
        while (builder.length < 80) builder.append('s')
        while (builder.length < 160) builder.append('a')
        while (builder.length < 300) builder.append('b')
        builder.append(page1Main)
        while (builder.length < 420) builder.append('c')
        builder.append(page1Bg)
        while (builder.length < 480) builder.append('d')
        while (builder.length < 620) builder.append('e')
        builder.append(page2Main)
        while (builder.length < 700) builder.append('f')
        builder.append(page1)
        while (builder.length < 780) builder.append('g')
        builder.append(page2Bg)
        while (builder.length < 900) builder.append('h')
        builder.append(page2)
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
