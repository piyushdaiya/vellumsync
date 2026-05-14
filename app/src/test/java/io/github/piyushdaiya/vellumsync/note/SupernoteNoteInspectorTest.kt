package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteNoteInspectorTest {
    @Test
    fun detectsKnownSupernoteMarkers() {
        val fakeNote = """
            noteSN_FILE_VER_20230015
            NOTE
            APPLY_EQUIPMENT:A5X
            PAGE1
            PAGESTYLE
            MAINLAYER
            BGLAYER
            LAYERINFO
            LAYERSEQ
            TOTALPATH
            TITLE
            KEYWORD
            LINK
            STAR
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

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
        assertTrue(report.hasLinkMetadata)
        assertTrue(report.hasStarMetadata)
        assertTrue(report.markerHits.any { it.marker == "TOTALPATH" && it.count == 1 })
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
}
