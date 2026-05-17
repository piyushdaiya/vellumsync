package io.github.piyushdaiya.vellumsync.note

import kotlin.math.abs
import kotlin.math.min

/**
 * Experimental read-only RATTA_RLE visual-layer renderer.
 *
 * Render/probe unification note:
 * both the UI render path and the exported visual probe should source their
 * page-level winner/status fields from the same resolved render object.
 */
// marker=vellumsync-ratta-rle-render-object-page-export-v0
// marker=vellumsync-ratta-rle-decoder-family-provenance-alias-v0
// marker=vellumsync-ratta-rle-decoder-family-provenance-export-v1
data class SupernoteRenderedVisualPage(
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val renderStatus: String,
    val sourceLayerName: String?,
    val payloadOffset: Int?,
    val payloadByteLength: Int?,
    val decoderName: String?,
    val nonWhitePixelCount: Int,
    val darkPixelCount: Int,
    val warnings: List<String>,
    val pageVisualDecoderName: String? = null,
    val pageVisualDecoderReason: String? = null,
    val pageVisualDecoderConfidence: Float = 0f,
    val pageVisualGrayMode: String? = null,
    val pageVisualPolarity: String? = null,
    val pageVisualStrideMode: String? = null,
    val pageVisualWinnerPromoted: Boolean = false,
    val pageVisualWinnerSource: String? = null,
    val pageVisualFinalDecoderFamily: String? = null,
    val pageVisualPromotedFromDecoderFamily: String? = null,
    val pageVisualStatusResolved: String? = null,
    val pageVisualStatusReason: String? = null,
    val pageVisualFallbackUsed: Boolean = false
) {
    val usable: Boolean
        get() = renderStatus == "decoded" &&
            pixels.size == width * height &&
            nonWhitePixelCount > width * height / 2000
}

object SupernoteRattaRleVisualLayerRenderer {
    private const val DEFAULT_PAGE_WIDTH = 1404
    private const val DEFAULT_PAGE_HEIGHT = 1872
    private const val MAX_HEADER_SCAN_BYTES = 128

    fun renderPage(
        bytes: ByteArray,
        report: SupernoteInspectionReport,
        pageNumber: Int,
        width: Int = DEFAULT_PAGE_WIDTH,
        height: Int = DEFAULT_PAGE_HEIGHT
    ): SupernoteRenderedVisualPage? {
        val visualPage = report.visualReport.pageReports.firstOrNull { it.pageNumber == pageNumber }
            ?: return unavailable(pageNumber, width, height, "No visual report available for page $pageNumber.")
        return renderPageFromVisualPage(bytes, visualPage, width, height)
    }

    internal fun renderPageFromVisualPage(
        bytes: ByteArray,
        visualPage: SupernoteVisualPageReport,
        width: Int = DEFAULT_PAGE_WIDTH,
        height: Int = DEFAULT_PAGE_HEIGHT
    ): SupernoteRenderedVisualPage {
        val layer = visualPage.layerRecords.firstOrNull {
            it.logicalLayerName == "MAINLAYER" && it.layerProtocol == "RATTA_RLE" && it.bitmapPayloadOffset != null
        } ?: visualPage.layerRecords.firstOrNull { it.layerProtocol == "RATTA_RLE" && it.bitmapPayloadOffset != null }
            ?: return unavailable(visualPage.pageNumber, width, height, "No RATTA_RLE visual layer payload pointer found for page ${visualPage.pageNumber}.")

        val start = layer.bitmapPayloadOffset ?: return unavailable(visualPage.pageNumber, width, height, "Visual layer payload offset is missing.")
        val end = layer.estimatedCompressedPayloadEndOffset
            ?.coerceIn(start, bytes.size)
            ?: layer.nextKnownStructuralOffset?.coerceIn(start, bytes.size)
            ?: bytes.size
        if (start !in bytes.indices || end <= start) {
            return unavailable(visualPage.pageNumber, width, height, "Invalid RATTA_RLE payload range: start=$start end=$end bytes=${bytes.size}.")
        }

        val payload = bytes.copyOfRange(start, end)
        val candidates = mutableListOf<DecodeCandidate>()
        decodeByteRunValue(payload, width, height, runFirst = TrueFalse.RUN_FIRST)?.let { candidates.add(it) }
        decodeByteRunValue(payload, width, height, runFirst = TrueFalse.VALUE_FIRST)?.let { candidates.add(it) }
        decodeNibbleRun(payload, width, height)?.let { candidates.add(it) }
        decodePacked1Bpp(payload, width, height, invert = false)?.let { candidates.add(it) }
        decodePacked1Bpp(payload, width, height, invert = true)?.let { candidates.add(it) }

        val bestOverall = candidates.maxByOrNull { it.score }
        val promotedWinner = selectPageWinner(candidates)
        if (promotedWinner == null) {
            return SupernoteRenderedVisualPage(
                pageNumber = visualPage.pageNumber,
                width = width,
                height = height,
                pixels = IntArray(width * height) { 255 },
                renderStatus = "unavailable",
                sourceLayerName = layer.logicalLayerName,
                payloadOffset = start,
                payloadByteLength = payload.size,
                decoderName = bestOverall?.familyName,
                nonWhitePixelCount = bestOverall?.nonWhitePixelCount ?: 0,
                darkPixelCount = bestOverall?.darkPixelCount ?: 0,
                warnings = listOf(
                    "RATTA_RLE payload isolated, but no visual-layer decoding strategy looked plausible yet.",
                    "Payload offset=$start byteLength=${payload.size}; vector TOTALPATH fallback remains active."
                ),
                pageVisualDecoderName = bestOverall?.familyName,
                pageVisualDecoderReason = "No plausible per-page winner was available.",
                pageVisualDecoderConfidence = 0f,
                pageVisualGrayMode = bestOverall?.grayMode,
                pageVisualPolarity = bestOverall?.polarity,
                pageVisualStrideMode = bestOverall?.strideMode,
                pageVisualWinnerPromoted = false,
                pageVisualWinnerSource = null,
                pageVisualFinalDecoderFamily = null,
                pageVisualPromotedFromDecoderFamily = bestOverall?.familyName,
                pageVisualStatusResolved = "visual-layer-unavailable",
                pageVisualStatusReason = "No plausible decoder family was found for the current page.",
                pageVisualFallbackUsed = true
            )
        }

        val promoted = bestOverall != null && bestOverall.name != promotedWinner.name
        val statusReason = when {
            promoted -> "Promoted best plausible per-page decoder over a denser global winner candidate."
            else -> "Using highest-scoring plausible decoder for the current page."
        }

        return SupernoteRenderedVisualPage(
            pageNumber = visualPage.pageNumber,
            width = width,
            height = height,
            pixels = promotedWinner.pixels,
            renderStatus = "decoded",
            sourceLayerName = layer.logicalLayerName,
            payloadOffset = start,
            payloadByteLength = payload.size,
            decoderName = promotedWinner.familyName,
            nonWhitePixelCount = promotedWinner.nonWhitePixelCount,
            darkPixelCount = promotedWinner.darkPixelCount,
            warnings = buildList {
                add("Experimental RATTA_RLE visual layer renderer used ${promotedWinner.name}; no .note bytes were modified.")
                add(statusReason)
                if (promoted && bestOverall != null) add("Best overall candidate was ${bestOverall.name}, but it was not the best plausible page-level winner.")
            },
            pageVisualDecoderName = promotedWinner.familyName,
            pageVisualDecoderReason = statusReason,
            pageVisualDecoderConfidence = promotedWinner.confidence,
            pageVisualGrayMode = promotedWinner.grayMode,
            pageVisualPolarity = promotedWinner.polarity,
            pageVisualStrideMode = promotedWinner.strideMode,
            pageVisualWinnerPromoted = promoted,
            pageVisualWinnerSource = promotedWinner.familyName,
            pageVisualFinalDecoderFamily = promotedWinner.familyName,
            pageVisualPromotedFromDecoderFamily = if (promoted) bestOverall?.familyName else null,
            pageVisualStatusResolved = "visual-layer-active",
            pageVisualStatusReason = statusReason,
            pageVisualFallbackUsed = false
        )
    }

    internal fun selectPageWinner(candidates: List<DecodeCandidate>): DecodeCandidate? {
        val plausible = candidates.filter { it.isPlausible }
        if (plausible.isEmpty()) return null
        return plausible.maxWithOrNull(
            compareBy<DecodeCandidate> { it.score }
                .thenBy { it.confidence }
                .thenBy { preferredFamilyRank(it.familyName) }
        )
    }

    private fun preferredFamilyRank(familyName: String): Int = when (familyName) {
        "byte-rle-run-value" -> 3
        "byte-rle-value-run" -> 2
        "packed-1bpp-inverted" -> 1
        "packed-1bpp" -> 0
        else -> -1
    }

    private fun unavailable(pageNumber: Int, width: Int, height: Int, warning: String): SupernoteRenderedVisualPage {
        return SupernoteRenderedVisualPage(
            pageNumber = pageNumber,
            width = width,
            height = height,
            pixels = IntArray(width * height) { 255 },
            renderStatus = "unavailable",
            sourceLayerName = null,
            payloadOffset = null,
            payloadByteLength = null,
            decoderName = null,
            nonWhitePixelCount = 0,
            darkPixelCount = 0,
            warnings = listOf(warning),
            pageVisualFinalDecoderFamily = null,
            pageVisualPromotedFromDecoderFamily = null,
            pageVisualStatusResolved = "visual-layer-unavailable",
            pageVisualStatusReason = warning,
            pageVisualFallbackUsed = true
        )
    }

    internal data class DecodeCandidate(
        val name: String,
        val familyName: String,
        val grayMode: String,
        val polarity: String,
        val strideMode: String,
        val headerSkip: Int,
        val pixels: IntArray,
        val decodedPixels: Int,
        val nonWhitePixelCount: Int,
        val darkPixelCount: Int,
        val transitionCount: Int,
        val score: Int
    ) {
        val isPlausible: Boolean
            get() = decodedPixels >= pixels.size / 2 &&
                nonWhitePixelCount in 20..(pixels.size / 2) &&
                transitionCount in 1000..200000

        val confidence: Float
            get() {
                val coverage = decodedPixels.toFloat() / pixels.size.toFloat()
                val density = nonWhitePixelCount.toFloat() / pixels.size.toFloat()
                return when {
                    !isPlausible -> 0f
                    familyName == "byte-rle-run-value" && coverage > 0.95f -> 0.92f
                    familyName == "byte-rle-value-run" && density in 0.10f..0.35f -> 0.89f
                    else -> 0.75f
                }
            }
    }

    private enum class TrueFalse { RUN_FIRST, VALUE_FIRST }

    private fun decodeByteRunValue(payload: ByteArray, width: Int, height: Int, runFirst: TrueFalse): DecodeCandidate? {
        val target = width * height
        var best: DecodeCandidate? = null
        for (headerSkip in 0..min(MAX_HEADER_SCAN_BYTES, payload.size - 2)) {
            val pixels = IntArray(target) { 255 }
            var out = 0
            var i = headerSkip
            while (i + 1 < payload.size && out < target) {
                val a = payload[i].toInt() and 0xff
                val b = payload[i + 1].toInt() and 0xff
                val run = ((if (runFirst == TrueFalse.RUN_FIRST) a else b).takeIf { it > 0 } ?: 1).coerceAtMost(255)
                val raw = if (runFirst == TrueFalse.RUN_FIRST) b else a
                val gray = rawToGray(raw)
                repeat(run) {
                    if (out < target) pixels[out++] = gray
                }
                i += 2
            }
            val family = if (runFirst == TrueFalse.RUN_FIRST) "byte-rle-run-value" else "byte-rle-value-run"
            val candidate = scoreCandidate(
                name = "$family-skip-$headerSkip",
                familyName = family,
                grayMode = "byte-gray",
                polarity = "normal",
                strideMode = "linear",
                headerSkip = headerSkip,
                pixels = pixels,
                decodedPixels = out
            )
            if (best == null || candidate.score > best!!.score) best = candidate
        }
        return best
    }

    private fun decodeNibbleRun(payload: ByteArray, width: Int, height: Int): DecodeCandidate? {
        val target = width * height
        var best: DecodeCandidate? = null
        for (headerSkip in 0..min(MAX_HEADER_SCAN_BYTES, payload.size - 1)) {
            val pixels = IntArray(target) { 255 }
            var out = 0
            for (i in headerSkip until payload.size) {
                val byte = payload[i].toInt() and 0xff
                val run = ((byte ushr 4) + 1).coerceAtMost(16)
                val gray = 255 - ((byte and 0x0f) * 17)
                repeat(run) {
                    if (out < target) pixels[out++] = gray
                }
                if (out >= target) break
            }
            val candidate = scoreCandidate("nibble-rle-skip-$headerSkip", "nibble-rle", "nibble-gray", "normal", "linear", headerSkip, pixels, out)
            if (best == null || candidate.score > best!!.score) best = candidate
        }
        return best
    }

    private fun decodePacked1Bpp(payload: ByteArray, width: Int, height: Int, invert: Boolean): DecodeCandidate? {
        val target = width * height
        var best: DecodeCandidate? = null
        for (headerSkip in 0..min(MAX_HEADER_SCAN_BYTES, payload.size - 1)) {
            val pixels = IntArray(target) { 255 }
            var out = 0
            for (i in headerSkip until payload.size) {
                val byte = payload[i].toInt() and 0xff
                for (bit in 7 downTo 0) {
                    val one = ((byte ushr bit) and 1) == 1
                    val dark = if (invert) !one else one
                    if (out < target) pixels[out++] = if (dark) 0 else 255
                }
                if (out >= target) break
            }
            val family = if (invert) "packed-1bpp-inverted" else "packed-1bpp"
            val polarity = if (invert) "inverted" else "normal"
            val candidate = scoreCandidate("${family}-skip-$headerSkip", family, "binary", polarity, "linear", headerSkip, pixels, out)
            if (best == null || candidate.score > best!!.score) best = candidate
        }
        return best
    }

    private fun scoreCandidate(
        name: String,
        familyName: String,
        grayMode: String,
        polarity: String,
        strideMode: String,
        headerSkip: Int,
        pixels: IntArray,
        decodedPixels: Int
    ): DecodeCandidate {
        var nonWhite = 0
        var dark = 0
        var transitions = 0
        var prev = pixels.firstOrNull() ?: 255
        pixels.forEach { gray ->
            if (gray < 245) nonWhite += 1
            if (gray < 120) dark += 1
            if (abs(gray - prev) > 40) transitions += 1
            prev = gray
        }
        val target = pixels.size
        val coveragePenalty = abs(nonWhite - target / 18)
        val familyBias = when (familyName) {
            "byte-rle-run-value" -> 96
            "byte-rle-value-run" -> 64
            else -> 0
        }
        val score = decodedPixels / 1024 + transitions / 64 + dark / 128 - coveragePenalty / 512 + familyBias
        return DecodeCandidate(
            name = name,
            familyName = familyName,
            grayMode = grayMode,
            polarity = polarity,
            strideMode = strideMode,
            headerSkip = headerSkip,
            pixels = pixels,
            decodedPixels = decodedPixels,
            nonWhitePixelCount = nonWhite,
            darkPixelCount = dark,
            transitionCount = transitions,
            score = score
        )
    }

    private fun rawToGray(raw: Int): Int = when {
        raw <= 3 -> 255 - raw * 85
        raw <= 15 -> 255 - raw * 17
        else -> raw.coerceIn(0, 255)
    }
}
