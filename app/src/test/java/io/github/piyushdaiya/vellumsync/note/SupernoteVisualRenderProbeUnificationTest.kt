package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteVisualRenderProbeUnificationTest {
    @Test
    fun pageReportPopulatesStatusFieldsFromRenderObject() {
        val bytes = ("noteSN_FILE_VER_20230015<FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><FILE_ID:F1><PAGE1:420>" +
            "x".repeat(80) +
            "<LAYERTYPE:NOTE><LAYERPROTOCOL:RATTA_RLE><LAYERNAME:MAINLAYER><LAYERPATH:0><LAYERBITMAP:120><LAYERVECTORGRAPH:0><LAYERRECOGN:0>" +
            "y".repeat(160) +
            "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER><MAINLAYER:200><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:0><TOTALPATH:420><EXTERNALLINKINFO:0>").toByteArray(Charsets.ISO_8859_1)
        val container = SupernoteContainerParser.parse(bytes)
        val visual = SupernoteVisualDecoder.decode(bytes, container)
        val page = visual.pageReports.first()
        assertNotNull(page.pageVisualStatusResolved)
        assertNotNull(page.pageVisualStatusReason)
        assertTrue(page.previewStatus.isNotBlank())
    }
}
