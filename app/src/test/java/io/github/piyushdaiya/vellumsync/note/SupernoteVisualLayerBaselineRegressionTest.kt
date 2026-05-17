package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-visual-layer-baseline-lock-cross-note-regression-pack-v0
// marker=vellumsync-visual-layer-baseline-regression-test-replacement-fix-v1
class SupernoteVisualLayerBaselineRegressionTest {
    private val fixtureText: String by lazy {
        javaClass.classLoader
            ?.getResourceAsStream("io/github/piyushdaiya/vellumsync/note/synctest1_visual_layer_baseline_fixture.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing synctest1 visual baseline fixture resource.")
    }

    @Test
    fun locksSynctest1VisualWinnerFamiliesAndPromotionSource() {
        assertPage(1, "byte-rle-run-value", "byte-rle-value-run", true)
        assertPage(2, "byte-rle-run-value", "byte-rle-value-run", true)
        assertPage(3, "byte-rle-value-run", null, false)
        assertPage(4, "byte-rle-value-run", null, false)
    }

    @Test
    fun locksExportedVisualStatusAndFallbackFlagsForAllPages() {
        for (page in 1..4) {
            assertEquals("Visual layer active", stringValue(page, "previewStatus"))
            assertEquals("visual-layer-active", stringValue(page, "pageVisualStatusResolved"))
            assertFalse(booleanValue(page, "pageVisualFallbackUsed"))
        }
    }

    @Test
    fun keepsBackwardCompatibleWinnerSourceAliasAlignedToFinalFamily() {
        for (page in 1..4) {
            assertEquals(
                stringValue(page, "pageVisualFinalDecoderFamily"),
                stringValue(page, "pageVisualWinnerSource")
            )
        }
    }

    @Test
    fun fixtureCarriesProvenanceFieldsSoDecoderExportDriftIsVisible() {
        assertTrue(fixtureText.contains("\"pageVisualFinalDecoderFamily\""))
        assertTrue(fixtureText.contains("\"pageVisualPromotedFromDecoderFamily\""))
        assertTrue(fixtureText.contains("\"pageVisualWinnerSource\""))
    }

    private fun assertPage(
        pageNumber: Int,
        finalDecoderFamily: String,
        promotedFromDecoderFamily: String?,
        promoted: Boolean
    ) {
        assertEquals(finalDecoderFamily, stringValue(pageNumber, "pageVisualFinalDecoderFamily"))
        assertEquals(finalDecoderFamily, stringValue(pageNumber, "pageVisualWinnerSource"))
        assertEquals(promotedFromDecoderFamily, nullableStringValue(pageNumber, "pageVisualPromotedFromDecoderFamily"))
        assertEquals(promoted, booleanValue(pageNumber, "pageVisualWinnerPromoted"))
    }

    private fun stringValue(pageNumber: Int, key: String): String =
        nullableStringValue(pageNumber, key)
            ?: error("Expected non-null value for page $pageNumber key $key")

    private fun nullableStringValue(pageNumber: Int, key: String): String? {
        val pageBlock = pageBlock(pageNumber)
        val regex = Regex("""\"""" + key + """\"\s*:\s*(null|\"([^\"]*)\")""")
        val match = regex.find(pageBlock) ?: error("Missing key $key in page $pageNumber block.")
        return if (match.groupValues[1] == "null") null else match.groupValues[2]
    }

    private fun booleanValue(pageNumber: Int, key: String): Boolean {
        val pageBlock = pageBlock(pageNumber)
        val regex = Regex("""\"""" + key + """\"\s*:\s*(true|false)""")
        val match = regex.find(pageBlock) ?: error("Missing boolean key $key in page $pageNumber block.")
        return match.groupValues[1].toBoolean()
    }

    private fun pageBlock(pageNumber: Int): String {
        val regex = Regex(
            """\{[^{}]*"pageNumber"\s*:\s*""" + pageNumber + """[^{}]*\}""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(fixtureText)?.value
            ?: error("Missing fixture block for page $pageNumber")
    }
}
