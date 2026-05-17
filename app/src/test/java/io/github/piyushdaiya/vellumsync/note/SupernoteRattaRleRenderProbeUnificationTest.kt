package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteRattaRleRenderProbeUnificationTest {
    @Test
    fun promotesPlausibleRunValueWinner() {
        val pixels = IntArray(1404 * 1872) { 255 }
        val dense = SupernoteRattaRleVisualLayerRenderer.DecodeCandidate(
            name = "byte-rle-value-run-skip-95",
            familyName = "byte-rle-value-run",
            grayMode = "byte-gray",
            polarity = "normal",
            strideMode = "linear",
            headerSkip = 95,
            pixels = pixels,
            decodedPixels = pixels.size,
            nonWhitePixelCount = 1_360_005,
            darkPixelCount = 830_024,
            transitionCount = 13_840,
            score = 6884
        )
        val promoted = SupernoteRattaRleVisualLayerRenderer.DecodeCandidate(
            name = "byte-rle-run-value-skip-98",
            familyName = "byte-rle-run-value",
            grayMode = "byte-gray",
            polarity = "normal",
            strideMode = "linear",
            headerSkip = 98,
            pixels = pixels,
            decodedPixels = pixels.size,
            nonWhitePixelCount = 1_009_074,
            darkPixelCount = 554_025,
            transitionCount = 13_841,
            score = 5425
        )
        val winner = SupernoteRattaRleVisualLayerRenderer.selectPageWinner(listOf(dense, promoted))
        assertEquals("byte-rle-run-value", winner?.familyName)
        assertTrue(promoted.isPlausible)
    }
}
