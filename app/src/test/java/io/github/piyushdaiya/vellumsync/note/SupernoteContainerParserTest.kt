package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteContainerParserTest {
    @Test
    fun parsesHeaderPageTableAndPageSections() {
        val bytes = buildFakeTwoPageNote()

        val report = SupernoteContainerParser.parse(bytes)

        assertEquals("SN_FILE_VER_20230015", report.header.versionMarker)
        assertEquals("none", report.header.moduleLabel)
        assertEquals("NOTE", report.header.fileType)
        assertEquals("A5X", report.header.applyEquipment)
        assertEquals(2, report.header.finalOperationPage)
        assertEquals(1, report.header.finalOperationLayer)
        assertEquals("F_TEST", report.header.fileId)
        assertEquals(2, report.pageReferences.size)
        assertEquals(1, report.pageReferences[0].pageNumber)
        assertEquals(300, report.pageReferences[0].pageSectionOffset)
        assertEquals(2, report.pageSections.size)
        assertEquals("MAINLAYER,BGLAYER", report.pageSections[0].layerSeq)
        assertEquals(250, report.pageSections[0].layerOffsets.totalPathOffset)
        assertFalse(report.pageSections[0].realLinkMetadataPresent)
        assertTrue(report.pageSections[0].externalLinkInfoPresent)
        assertTrue(report.pageSections[1].realLinkMetadataPresent)
        assertTrue(report.realLinkMetadataPresent)
        assertTrue(report.starMetadataPresent)
    }

    private fun buildFakeTwoPageNote(): ByteArray {
        val header = "noteSN_FILE_VER_20230015<MODULE_LABEL:none><FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:2><FINALOPERATION_LAYER:1><FILE_ID:F_TEST><PAGE1:300><PAGE2:700><COVER_0:0><DIRTY:0>"
        val page1 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[{\"layerId\"#3}]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:100><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:200><TOTALPATH:250><EXTERNALLINKINFO:0>"
        val page2 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[{\"layerId\"#3}]><LAYERSEQ:MAINLAYER,LAYER1,BGLAYER><MAINLAYER:310><LAYER1:360><LAYER2:0><LAYER3:0><BGLAYER:410><TOTALPATH:460><LINKTYPE:1><LINKRECT:1,2,3,4><FIVESTAR:1,2,3>"
        val builder = StringBuilder(header)
        while (builder.length < 300) {
            builder.append('.')
        }
        builder.append(page1)
        while (builder.length < 700) {
            builder.append('.')
        }
        builder.append(page2)
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
