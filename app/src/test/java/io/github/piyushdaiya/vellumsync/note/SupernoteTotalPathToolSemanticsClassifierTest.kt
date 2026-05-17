package io.github.piyushdaiya.vellumsync.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// marker=vellumsync-test-assertion-alignment-repair-pack-v0
// marker=vellumsync-totalpath-tool-semantics-classifier-stronger-lasso-fixture-v0
class SupernoteTotalPathToolSemanticsClassifierTest {
    @Test
    fun classifiesClosedLoopAsLassoGesture() {
        val mapped = listOf(
            SupernoteGeometryPoint(200f, 200f),
            SupernoteGeometryPoint(320f, 170f),
            SupernoteGeometryPoint(430f, 210f),
            SupernoteGeometryPoint(500f, 300f),
            SupernoteGeometryPoint(470f, 410f),
            SupernoteGeometryPoint(360f, 470f),
            SupernoteGeometryPoint(250f, 450f),
            SupernoteGeometryPoint(180f, 360f),
            SupernoteGeometryPoint(220f, 250f),
            SupernoteGeometryPoint(340f, 230f),
            SupernoteGeometryPoint(430f, 290f),
            SupernoteGeometryPoint(380f, 380f),
            SupernoteGeometryPoint(270f, 390f),
            SupernoteGeometryPoint(230f, 300f),
            SupernoteGeometryPoint(320f, 220f),
            SupernoteGeometryPoint(440f, 220f),
            SupernoteGeometryPoint(500f, 330f),
            SupernoteGeometryPoint(400f, 450f),
            SupernoteGeometryPoint(260f, 460f),
            SupernoteGeometryPoint(170f, 330f),
            SupernoteGeometryPoint(200f, 200f)
        )
        val raw = mapped.map { point ->
            SupernoteRawPoint(point.x.toLong(), point.y.toLong())
        }

        val classification = SupernoteTotalPathToolSemanticsClassifier.classify(
            recordIndex = 0,
            category = "others",
            source = "decodedPointArray",
            rawPoints = raw,
            mappedPoints = mapped,
            pageWidth = 1404f,
            pageHeight = 1872f
        )

        assertEquals(SupernoteToolSemanticKind.LASSO_ERASE_GESTURE, classification.kind)
        assertTrue(classification.confidence >= 0.60f)
        assertTrue(classification.reasons.isNotEmpty())
    }

    @Test
    fun classifiesTinyCompactRecordAsNonDrawable() {
        val mapped = listOf(
            SupernoteGeometryPoint(20f, 20f),
            SupernoteGeometryPoint(22f, 21f),
            SupernoteGeometryPoint(21f, 22f),
            SupernoteGeometryPoint(20f, 20f)
        )
        val raw = mapped.map { point ->
            SupernoteRawPoint(point.x.toLong(), point.y.toLong())
        }

        val classification = SupernoteTotalPathToolSemanticsClassifier.classify(
            recordIndex = 1,
            category = "unknown",
            source = "candidatePointRun-fallback-preview",
            rawPoints = raw,
            mappedPoints = mapped,
            pageWidth = 1404f,
            pageHeight = 1872f
        )

        assertEquals(SupernoteToolSemanticKind.NON_DRAWABLE_UTILITY_DEBUG, classification.kind)
    }
}
