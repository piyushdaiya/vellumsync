package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText

// marker=vellumsync-ratta-rle-pen-ink-page-decoder-parity-v0-compile-repair

data class SupernoteVisualLayerFirstDiagnostics(
    val mainLayerBitmapDetected: Boolean,
    val bgLayerBitmapDetected: Boolean,
    val visualLayerDecodeStatus: String,
    val visualLayerPixelCoverage: Float,
    val vectorFallbackRenderedCount: Int,
    val vectorFallbackFilteredCount: Int,
    val winningDecoderName: String? = null,
    val winningHeaderSkip: Int? = null,
    val winningDecodedPixels: Int? = null,
    val visualLayerRuntimePromoted: Boolean = false,
    val visualLayerRuntimeRejectReason: String? = null,
    val winningScore: Int? = null,
    val stripeRejectTriggered: Boolean = false,
    val stripeRejectReason: String? = null,
    val candidateScoreSummary: String? = null,
    val postDecodeTransform: String? = null,
    val pageVisualDecoderName: String? = null,
    val pageVisualDecoderReason: String? = null,
    val pageVisualDecoderConfidence: Float = 0f,
    val pageVisualGrayMode: String? = null,
    val pageVisualPolarity: String? = null,
    val pageVisualStrideMode: String? = null
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"mainLayerBitmapDetected\":$mainLayerBitmapDetected,")
            append("\"bgLayerBitmapDetected\":$bgLayerBitmapDetected,")
            append("\"visualLayerDecodeStatus\":${JsonText.quote(visualLayerDecodeStatus)},")
            append("\"visualLayerPixelCoverage\":$visualLayerPixelCoverage,")
            append("\"vectorFallbackRenderedCount\":$vectorFallbackRenderedCount,")
            append("\"vectorFallbackFilteredCount\":$vectorFallbackFilteredCount,")
            append("\"winningDecoderName\":${winningDecoderName?.let(JsonText::quote) ?: "null"},")
            append("\"winningHeaderSkip\":${winningHeaderSkip ?: "null"},")
            append("\"winningDecodedPixels\":${winningDecodedPixels ?: "null"},")
            append("\"visualLayerRuntimePromoted\":$visualLayerRuntimePromoted,")
            append("\"visualLayerRuntimeRejectReason\":${visualLayerRuntimeRejectReason?.let(JsonText::quote) ?: "null"},")
            append("\"winningScore\":${winningScore ?: "null"},")
            append("\"stripeRejectTriggered\":$stripeRejectTriggered,")
            append("\"stripeRejectReason\":${stripeRejectReason?.let(JsonText::quote) ?: "null"},")
            append("\"candidateScoreSummary\":${candidateScoreSummary?.let(JsonText::quote) ?: "null"},")
            append("\"postDecodeTransform\":${postDecodeTransform?.let(JsonText::quote) ?: "null"},")
            append("\"pageVisualDecoderName\":${pageVisualDecoderName?.let(JsonText::quote) ?: "null"},")
            append("\"pageVisualDecoderReason\":${pageVisualDecoderReason?.let(JsonText::quote) ?: "null"},")
            append("\"pageVisualDecoderConfidence\":$pageVisualDecoderConfidence,")
            append("\"pageVisualGrayMode\":${pageVisualGrayMode?.let(JsonText::quote) ?: "null"},")
            append("\"pageVisualPolarity\":${pageVisualPolarity?.let(JsonText::quote) ?: "null"},")
            append("\"pageVisualStrideMode\":${pageVisualStrideMode?.let(JsonText::quote) ?: "null"}")
            append("}")
        }
    }

    companion object {
        fun fromRenderedPage(
            renderedPage: SupernoteRenderedVisualPage?,
            mainLayerBitmapDetected: Boolean = false,
            bgLayerBitmapDetected: Boolean = false,
            vectorFallbackRenderedCount: Int = 0,
            vectorFallbackFilteredCount: Int = 0
        ): SupernoteVisualLayerFirstDiagnostics {
            val coverage = if (renderedPage == null || renderedPage.width <= 0 || renderedPage.height <= 0) {
                0f
            } else {
                renderedPage.nonWhitePixelCount.toFloat() / (renderedPage.width * renderedPage.height).toFloat()
            }
            val rejectReason = if (renderedPage?.renderStatus == "decoded") null else renderedPage?.warnings?.firstOrNull()
            val transform = listOfNotNull(renderedPage?.pageVisualGrayMode, renderedPage?.pageVisualPolarity, renderedPage?.pageVisualStrideMode)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "/")
            return SupernoteVisualLayerFirstDiagnostics(
                mainLayerBitmapDetected = mainLayerBitmapDetected,
                bgLayerBitmapDetected = bgLayerBitmapDetected,
                visualLayerDecodeStatus = renderedPage?.renderStatus ?: "unavailable",
                visualLayerPixelCoverage = coverage,
                vectorFallbackRenderedCount = vectorFallbackRenderedCount,
                vectorFallbackFilteredCount = vectorFallbackFilteredCount,
                winningDecoderName = renderedPage?.pageVisualDecoderName ?: renderedPage?.decoderName,
                winningHeaderSkip = null,
                winningDecodedPixels = renderedPage?.nonWhitePixelCount,
                visualLayerRuntimePromoted = renderedPage?.usable == true,
                visualLayerRuntimeRejectReason = rejectReason ?: renderedPage?.pageVisualDecoderReason,
                winningScore = null,
                stripeRejectTriggered = false,
                stripeRejectReason = null,
                candidateScoreSummary = renderedPage?.pageVisualDecoderReason,
                postDecodeTransform = transform,
                pageVisualDecoderName = renderedPage?.pageVisualDecoderName,
                pageVisualDecoderReason = renderedPage?.pageVisualDecoderReason,
                pageVisualDecoderConfidence = renderedPage?.pageVisualDecoderConfidence ?: 0f,
                pageVisualGrayMode = renderedPage?.pageVisualGrayMode,
                pageVisualPolarity = renderedPage?.pageVisualPolarity,
                pageVisualStrideMode = renderedPage?.pageVisualStrideMode
            )
        }
    }
}
