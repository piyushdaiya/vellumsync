package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernotePdfReferenceLayerTest {
    @Test
    fun derivesSameBasenamePdfReferenceCacheName() {
        assertEquals("sync-test.pdf", ImportedNoteCache.referencePdfCacheName("sync-test.note"))
        assertEquals("folder_note.pdf", ImportedNoteCache.referencePdfCacheName("folder note.note"))
    }

    @Test
    fun reportsUnavailablePdfReferenceDiagnostics() {
        val diagnostics = SupernotePdfReferenceLayer.diagnosticsJson(null)
        assertTrue(diagnostics.contains("\"pdfReferenceDetected\":false"))
        assertTrue(diagnostics.contains("\"pdfReferenceDecodeStatus\":\"unavailable\""))
    }
}
