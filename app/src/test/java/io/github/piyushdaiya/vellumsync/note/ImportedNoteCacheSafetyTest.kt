package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// marker=vellumsync-missing-cached-note-fast-fail-guard-v0-test
class ImportedNoteCacheSafetyTest {
    @Test
    fun rejectsDiagnosticsJsonAsNoteSource() {
        val bytes = """
            {"fileName":"synctest1.note","containerReport":{},"visualReport":{}}
        """.trimIndent().toByteArray()

        val decision = ImportedNoteCache.validateImportSource(
            fileName = "vellumsync-note-diagnostics.json",
            bytes = bytes,
            openedSourcePath = "/tmp/vellumsync-note-diagnostics.json"
        )

        assertEquals("diagnostics_json", decision.openedSourceKind)
        assertFalse(decision.cacheNotePresent)
        assertNull(decision.noteBytes)
        assertNotNull(decision.cacheFailureReason)
    }

    @Test
    fun acceptsRealNoteExtensionWhenPayloadIsNotDiagnosticsJson() {
        val bytes = "noteSN_FILE_VER_20230015<FILE_TYPE:NOTE>".toByteArray()

        val decision = ImportedNoteCache.validateImportSource(
            fileName = "synctest1.note",
            bytes = bytes,
            openedSourcePath = "/tmp/synctest1.note"
        )

        assertEquals("note", decision.openedSourceKind)
        assertEquals(true, decision.cacheNotePresent)
        assertEquals(bytes.size, decision.noteBytes?.size)
        assertNull(decision.cacheFailureReason)
    }
}
