package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernoteStrokeStyleDecoderTest {
    @Test
    fun mapsSupernoteGrayscaleAndWidthFields() {
        val style = SupernoteStrokeStyleDecoder.decode(
            listOf(0, 0, 1200, 1, 157, 600, 10, 0, 32, 0xffffffffL, 2, 0, 0, 5000, 0, 1701344367),
            recordIndex = 7
        )

        assertEquals("pen", style.toolFamily)
        assertEquals("round-ink", style.styleLabel)
        assertEquals(157, style.mappedGray)
        assertEquals(6f, style.strokeWidthPx, 0.001f)
        assertFalse(style.isEraser)
    }

    @Test
    fun mapsMarkerAsVisibleWideStyle() {
        val style = SupernoteStrokeStyleDecoder.decode(
            listOf(0, 0, 1200, 10, 201, 1000, 10, 0, 32, 0xffffffffL, 1, 0, 0, 5000, 0, 1701344367),
            recordIndex = 8
        )

        assertEquals("marker", style.toolFamily)
        assertEquals("soft-marker", style.styleLabel)
        assertEquals(201, style.mappedGray)
        assertEquals(12.5f, style.strokeWidthPx, 0.001f)
        assertFalse(style.isEraser)
    }

    @Test
    fun detectsPageFourEraseRecordsAsKnockouts() {
        val areaEraser = SupernoteStrokeStyleDecoder.decode(
            listOf(0, 0, 7347, 3, 255, 400, 10, 0, 32, 0xffffffffL, 4, 0, 0, 0xfffffffeL, 0, 1701344367),
            recordIndex = 94
        )
        val precisionEraser = SupernoteStrokeStyleDecoder.decode(
            listOf(0, 0, 5983, 4, 0, 200, 10, 0, 32, 0xffffffffL, 4, 0, 0, 0xfffffffbL, 0, 1701344367),
            recordIndex = 95
        )

        assertTrue(areaEraser.isEraser)
        assertEquals("area-eraser-knockout", areaEraser.eraserMode)
        assertTrue(precisionEraser.isEraser)
        assertEquals("precision-eraser-knockout", precisionEraser.eraserMode)
    }

    @Test
    fun logsUnknownToolSamples() {
        val style = SupernoteStrokeStyleDecoder.decode(
            listOf(0, 0, 600, 99, 0, 600, 10, 0, 32, 0xffffffffL, 1, 0, 0, 5000, 0, 1701344367),
            recordIndex = 42
        )

        assertEquals("unknown", style.toolFamily)
        assertTrue(style.unknownMetadataSample?.contains("record=42") == true)
        assertTrue(style.warnings.any { it.contains("Unknown Supernote tool/style") })
    }
}
