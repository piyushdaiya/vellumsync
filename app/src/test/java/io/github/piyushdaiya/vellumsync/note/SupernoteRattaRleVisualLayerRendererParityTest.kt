package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteRattaRleVisualLayerRendererParityTest {
    @Test
    fun promotesBestPlausibleWinnerOverHigherScoringNonPlausibleCandidate() {
        val pixels = IntArray(1404 * 1872) { 255 }
        val nonPlausible = SupernoteRattaRleVisualLayerRenderer.DecodeCandidate(
            name = "byte-rle-value-run-skip-10",
            familyName = "byte-rle-value-run",
            grayMode = "byte-gray",
            polarity = "normal",
            strideMode = "linear",
            headerSkip = 10,
            pixels = pixels,
            decodedPixels = pixels.size,
            nonWhitePixelCount = (pixels.size / 2) + 100,
            darkPixelCount = pixels.size / 4,
            transitionCount = 12000,
            score = 9000
        )
        val plausible = SupernoteRattaRleVisualLayerRenderer.DecodeCandidate(
            name = "byte-rle-run-value-skip-12",
            familyName = "byte-rle-run-value",
            grayMode = "byte-gray",
            polarity = "normal",
            strideMode = "linear",
            headerSkip = 12,
            pixels = pixels,
            decodedPixels = pixels.size,
            nonWhitePixelCount = pixels.size / 4,
            darkPixelCount = pixels.size / 8,
            transitionCount = 12000,
            score = 6500
        )

        val selected = SupernoteRattaRleVisualLayerRenderer.selectPageWinner(listOf(nonPlausible, plausible))

        assertNotNull(selected)
        assertEquals("byte-rle-run-value", selected!!.familyName)
        assertTrue(plausible.isPlausible)
        assertFalse(nonPlausible.isPlausible)
    }

    @Test
    fun returnsNullWhenNoPlausibleCandidateExists() {
        val pixels = IntArray(1404 * 1872) { 255 }
        val none = SupernoteRattaRleVisualLayerRenderer.DecodeCandidate(
            name = "nibble-rle-skip-0",
            familyName = "nibble-rle",
            grayMode = "nibble-gray",
            polarity = "normal",
            strideMode = "linear",
            headerSkip = 0,
            pixels = pixels,
            decodedPixels = pixels.size / 10,
            nonWhitePixelCount = pixels.size / 50,
            darkPixelCount = pixels.size / 100,
            transitionCount = 10,
            score = 10
        )

        val selected = SupernoteRattaRleVisualLayerRenderer.selectPageWinner(listOf(none))
        assertEquals(null, selected)
    }
}
