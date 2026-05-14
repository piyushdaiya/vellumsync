package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteNoteInspectorTest {
    @Test
    fun detectsKnownSupernoteMarkersAndContainerFields() {
        val fakeNote = buildFakeNote(
            header = "noteSN_FILE_VER_20230015<MODULE_LABEL:none><FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><FILE_ID:F123><PAGE1:300>",
            page1Offset = 300,
            page1 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[{\"layerId\"#3}]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:100><LAYER1:0><LAYER2:0><LAYER3:0><BGLAYER:200><TOTALPATH:250><EXTERNALLINKINFO:0><TITLESEQNO:0><KEYWORDPAGE:1><LINKTYPE:1><LINKBITMAP:10><LINKRECT:1,2,3,4><FIVESTAR:1,2,3>"
        )

        val report = SupernoteNoteInspector.inspect(
            fileName = "fake.note",
            fileSizeBytes = fakeNote.size.toLong(),
            bytes = fakeNote
        )

        assertEquals("SN_FILE_VER_20230015", report.versionMarker)
        assertEquals("A5X", report.detectedEquipment)
        assertEquals(1, report.estimatedPageCount)
        assertTrue(report.sha256.length == 64)
        assertTrue(report.headerPreviewHex.isNotBlank())
        assertTrue(report.headerPreviewAscii.isNotBlank())
        assertTrue(report.hasNoteMarker)
        assertTrue(report.hasMainLayer)
        assertTrue(report.hasBackgroundLayer)
        assertTrue(report.hasLayerInfo)
        assertTrue(report.hasLayerSequence)
        assertTrue(report.hasTotalPath)
        assertTrue(report.hasPageStyle)
        assertTrue(report.hasTitleMetadata)
        assertTrue(report.hasKeywordMetadata)
        assertTrue(report.hasExternalLinkInfoField)
        assertTrue(report.hasLinkMetadata)
        assertTrue(report.hasStarMetadata)
        assertTrue(report.markerHits.any { it.marker == "TOTALPATH" && it.count == 1 })
        assertEquals("NOTE", report.containerReport.header.fileType)
        assertEquals(1, report.containerReport.pageReferences.size)
        assertEquals(1, report.containerReport.pageSections.size)
        assertEquals("style_8mm_ruled_line_a5x", report.containerReport.pageSections[0].pageStyle)
        assertEquals(250, report.containerReport.pageSections[0].layerOffsets.totalPathOffset)
    }

    @Test
    fun separatesExternalLinkInfoFromRealLinkMetadata() {
        val fakeNote = buildFakeNote(
            header = "noteSN_FILE_VER_20230015<FILE_TYPE:NOTE><APPLY_EQUIPMENT:A5X><FINALOPERATION_PAGE:1><FINALOPERATION_LAYER:1><PAGE1:180>",
            page1Offset = 180,
            page1 = "<PAGESTYLE:style_8mm_ruled_line_a5x><LAYERINFO:[]><LAYERSEQ:MAINLAYER,BGLAYER><MAINLAYER:10><BGLAYER:20><TOTALPATH:30><EXTERNALLINKINFO:0>"
        )

        val report = SupernoteNoteInspector.inspect(
            fileName = "small.note",
            fileSizeBytes = fakeNote.size.toLong(),
            bytes = fakeNote
        )

        assertTrue(report.hasExternalLinkInfoField)
        assertFalse(report.hasLinkMetadata)
    }

    @Test
    fun reportsUnknownFileWithoutCrashing() {
        val bytes = "not a supernote file".toByteArray(Charsets.ISO_8859_1)

        val report = SupernoteNoteInspector.inspect(
            fileName = "not-note.bin",
            fileSizeBytes = bytes.size.toLong(),
            bytes = bytes
        )

        assertEquals(null, report.versionMarker)
        assertEquals(0, report.estimatedPageCount)
        assertTrue(report.warnings.isNotEmpty())
    }

    private fun buildFakeNote(
        header: String,
        page1Offset: Int,
        page1: String
    ): ByteArray {
        val builder = StringBuilder(header)
        while (builder.length < page1Offset) {
            builder.append('.')
        }
        builder.append(page1)
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
