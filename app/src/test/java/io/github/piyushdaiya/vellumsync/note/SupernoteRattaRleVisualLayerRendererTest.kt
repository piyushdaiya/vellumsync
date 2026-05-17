package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-test-assertion-alignment-repair-pack-v0
class SupernoteRattaRleVisualLayerRendererTest {
    @Test
    fun promotedWinnerExposesDecoderFamilyProvenanceFields() {
        val page = SupernoteRenderedVisualPage(
            pageNumber = 1,
            width = 1404,
            height = 1872,
            pixels = IntArray(1404 * 1872) { if (it % 31 == 0) 0 else 255 },
            renderStatus = "decoded",
            sourceLayerName = "MAINLAYER",
            payloadOffset = 2759,
            payloadByteLength = 38960,
            decoderName = "byte-rle-run-value-skip-126",
            nonWhitePixelCount = 24000,
            darkPixelCount = 12000,
            warnings = listOf("Promoted best plausible per-page decoder."),
            pageVisualDecoderName = "byte-rle-run-value",
            pageVisualDecoderReason = "Promoted best plausible per-page decoder over a denser global winner candidate.",
            pageVisualDecoderConfidence = 0.92f,
            pageVisualGrayMode = "byte-gray",
            pageVisualPolarity = "normal",
            pageVisualStrideMode = "linear",
            pageVisualWinnerPromoted = true,
            pageVisualWinnerSource = "byte-rle-run-value",
            pageVisualFinalDecoderFamily = "byte-rle-run-value",
            pageVisualPromotedFromDecoderFamily = "byte-rle-value-run",
            pageVisualStatusResolved = "visual-layer-active",
            pageVisualStatusReason = "Promoted best plausible per-page decoder over a denser global winner candidate.",
            pageVisualFallbackUsed = false
        )

        assertTrue(page.usable)
        assertTrue(page.pageVisualWinnerPromoted)
        assertEquals("byte-rle-run-value", page.pageVisualWinnerSource)
        assertEquals("byte-rle-run-value", page.pageVisualFinalDecoderFamily)
        assertEquals("byte-rle-value-run", page.pageVisualPromotedFromDecoderFamily)
        assertEquals("visual-layer-active", page.pageVisualStatusResolved)
        assertFalse(page.pageVisualFallbackUsed)
    }

    @Test
    fun nonPromotedWinnerKeepsAliasAlignedAndPromotedFromNull() {
        val page = SupernoteRenderedVisualPage(
            pageNumber = 3,
            width = 1404,
            height = 1872,
            pixels = IntArray(1404 * 1872) { if (it % 53 == 0) 32 else 255 },
            renderStatus = "decoded",
            sourceLayerName = "MAINLAYER",
            payloadOffset = 438579,
            payloadByteLength = 26746,
            decoderName = "byte-rle-value-run-skip-1",
            nonWhitePixelCount = 18000,
            darkPixelCount = 9000,
            warnings = listOf("Using highest-scoring plausible decoder for the current page."),
            pageVisualDecoderName = "byte-rle-value-run",
            pageVisualDecoderReason = "Using highest-scoring plausible decoder for the current page.",
            pageVisualDecoderConfidence = 0.89f,
            pageVisualGrayMode = "byte-gray",
            pageVisualPolarity = "normal",
            pageVisualStrideMode = "linear",
            pageVisualWinnerPromoted = false,
            pageVisualWinnerSource = "byte-rle-value-run",
            pageVisualFinalDecoderFamily = "byte-rle-value-run",
            pageVisualPromotedFromDecoderFamily = null,
            pageVisualStatusResolved = "visual-layer-active",
            pageVisualStatusReason = "Using highest-scoring plausible decoder for the current page.",
            pageVisualFallbackUsed = false
        )

        assertFalse(page.pageVisualWinnerPromoted)
        assertEquals(page.pageVisualFinalDecoderFamily, page.pageVisualWinnerSource)
        assertEquals(null, page.pageVisualPromotedFromDecoderFamily)
        assertEquals("visual-layer-active", page.pageVisualStatusResolved)
        assertFalse(page.pageVisualFallbackUsed)
    }
}
