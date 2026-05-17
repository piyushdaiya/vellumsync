package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import kotlin.math.abs
import kotlin.math.min

data class SupernoteVisualPayloadDecodeAttempt(
    val decoderName: String,
    val headerSkip: Int,
    val decodedPixels: Int,
    val expectedPixels: Int,
    val nonWhitePixelCount: Int,
    val darkPixelCount: Int,
    val transitionCount: Int,
    val score: Int,
    val plausible: Boolean,
    val failureReason: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"decoderName\":${JsonText.quote(decoderName)},")
            append("\"headerSkip\":$headerSkip,")
            append("\"decodedPixels\":$decodedPixels,")
            append("\"expectedPixels\":$expectedPixels,")
            append("\"nonWhitePixelCount\":$nonWhitePixelCount,")
            append("\"darkPixelCount\":$darkPixelCount,")
            append("\"transitionCount\":$transitionCount,")
            append("\"score\":$score,")
            append("\"plausible\":$plausible,")
            append("\"failureReason\":${JsonText.quote(failureReason)}")
            append("}")
        }
    }
}

data class SupernoteVisualPayloadLayerProbe(
    val pageNumber: Int,
    val logicalLayerName: String,
    val layerProtocol: String?,
    val parsedLayerName: String?,
    val layerRecordOffset: Int,
    val metadataRecordByteLength: Int?,
    val bitmapPayloadDetected: Boolean,
    val bitmapPayloadOffset: Int?,
    val bitmapPayloadEndOffset: Int?,
    val bitmapPayloadByteLength: Int?,
    val payloadTypeGuess: String,
    val payloadHeaderHex: String,
    val payloadHeaderAscii: String,
    val decodeStatus: String,
    val bestDecoderName: String?,
    val decodeAttempts: List<SupernoteVisualPayloadDecodeAttempt>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"logicalLayerName\":${JsonText.quote(logicalLayerName)},")
            append("\"layerProtocol\":${JsonText.quote(layerProtocol)},")
            append("\"parsedLayerName\":${JsonText.quote(parsedLayerName)},")
            append("\"layerRecordOffset\":$layerRecordOffset,")
            append("\"metadataRecordByteLength\":${metadataRecordByteLength ?: "null"},")
            append("\"bitmapPayloadDetected\":$bitmapPayloadDetected,")
            append("\"bitmapPayloadOffset\":${bitmapPayloadOffset ?: "null"},")
            append("\"bitmapPayloadEndOffset\":${bitmapPayloadEndOffset ?: "null"},")
            append("\"bitmapPayloadByteLength\":${bitmapPayloadByteLength ?: "null"},")
            append("\"payloadTypeGuess\":${JsonText.quote(payloadTypeGuess)},")
            append("\"payloadHeaderHex\":${JsonText.quote(payloadHeaderHex)},")
            append("\"payloadHeaderAscii\":${JsonText.quote(payloadHeaderAscii)},")
            append("\"decodeStatus\":${JsonText.quote(decodeStatus)},")
            append("\"bestDecoderName\":${JsonText.quote(bestDecoderName)},")
            append("\"decodeAttempts\":[")
            append(decodeAttempts.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteVisualPayloadPageProbe(
    val pageNumber: Int,
    val pageSectionOffset: Int,
    val pageStyle: String?,
    val layerSeq: String?,
    val mainLayerBitmapDetected: Boolean,
    val bgLayerBitmapDetected: Boolean,
    val totalPathDetected: Boolean,
    val pageContentMode: String,
    val layerPayloads: List<SupernoteVisualPayloadLayerProbe>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"pageNumber\":$pageNumber,")
            append("\"pageSectionOffset\":$pageSectionOffset,")
            append("\"pageStyle\":${JsonText.quote(pageStyle)},")
            append("\"layerSeq\":${JsonText.quote(layerSeq)},")
            append("\"mainLayerBitmapDetected\":$mainLayerBitmapDetected,")
            append("\"bgLayerBitmapDetected\":$bgLayerBitmapDetected,")
            append("\"totalPathDetected\":$totalPathDetected,")
            append("\"pageContentMode\":${JsonText.quote(pageContentMode)},")
            append("\"layerPayloads\":[")
            append(layerPayloads.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

data class SupernoteVisualPayloadProbeReport(
    val format: String,
    val version: Int,
    val fileName: String,
    val sha256: String,
    val versionMarker: String?,
    val totalPages: Int,
    val pagesWithMainLayerBitmap: Int,
    val pagesWithBackgroundBitmap: Int,
    val pagesWithTotalPath: Int,
    val pages: List<SupernoteVisualPayloadPageProbe>,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote(format)},")
            append("\"version\":$version,")
            append("\"fileName\":${JsonText.quote(fileName)},")
            append("\"sha256\":${JsonText.quote(sha256)},")
            append("\"versionMarker\":${JsonText.quote(versionMarker)},")
            append("\"totalPages\":$totalPages,")
            append("\"pagesWithMainLayerBitmap\":$pagesWithMainLayerBitmap,")
            append("\"pagesWithBackgroundBitmap\":$pagesWithBackgroundBitmap,")
            append("\"pagesWithTotalPath\":$pagesWithTotalPath,")
            append("\"pages\":[")
            append(pages.joinToString(separator = ",") { it.toJson() })
            append("],")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}

object SupernoteVisualPayloadProbe {
    private const val DEFAULT_PAGE_WIDTH = 1404
    private const val DEFAULT_PAGE_HEIGHT = 1872
    private const val MAX_HEADER_SCAN_BYTES = 128
    private const val HEADER_PREVIEW_BYTES = 32

    fun probe(
        bytes: ByteArray,
        report: SupernoteInspectionReport,
        width: Int = DEFAULT_PAGE_WIDTH,
        height: Int = DEFAULT_PAGE_HEIGHT
    ): SupernoteVisualPayloadProbeReport {
        val visualByPage = report.visualReport.pageReports.associateBy { it.pageNumber }
        val totalPathByPage = report.totalPathProbeReport.pageReports.associateBy { it.pageNumber }

        val pages = report.containerReport.pageSections.map { page ->
            val visualPage = visualByPage[page.pageNumber]
            val totalPathPage = totalPathByPage[page.pageNumber]
            val layerPayloads = visualPage?.layerRecords.orEmpty().map { layer ->
                probeLayer(bytes = bytes, layer = layer, width = width, height = height)
            }
            val mainLayerBitmapDetected = layerPayloads.any { it.logicalLayerName == "MAINLAYER" && it.bitmapPayloadDetected }
            val bgLayerBitmapDetected = layerPayloads.any { it.logicalLayerName == "BGLAYER" && it.bitmapPayloadDetected }
            val anyVisualBitmapDetected = layerPayloads.any { it.bitmapPayloadDetected }
            val totalPathDetected = (totalPathPage?.totalPathOffset != null) || ((totalPathPage?.estimatedPayloadByteLength ?: 0) > 0)
            val pageContentMode = when {
                anyVisualBitmapDetected && totalPathDetected -> "visual-and-totalpath"
                anyVisualBitmapDetected -> "visual-only"
                totalPathDetected -> "totalpath-only"
                else -> "no-visual-or-totalpath"
            }
            val warnings = buildList {
                addAll(visualPage?.warnings.orEmpty())
                addAll(totalPathPage?.warnings.orEmpty())
                if (!mainLayerBitmapDetected) add("MAINLAYER bitmap payload was not detected for this page.")
                if (!bgLayerBitmapDetected) add("BGLAYER bitmap payload was not detected for this page.")
                if (!totalPathDetected) add("TOTALPATH payload was not detected for this page.")
            }.distinct()
            SupernoteVisualPayloadPageProbe(
                pageNumber = page.pageNumber,
                pageSectionOffset = page.pageSectionOffset,
                pageStyle = page.pageStyle,
                layerSeq = page.layerSeq,
                mainLayerBitmapDetected = mainLayerBitmapDetected,
                bgLayerBitmapDetected = bgLayerBitmapDetected,
                totalPathDetected = totalPathDetected,
                pageContentMode = pageContentMode,
                layerPayloads = layerPayloads,
                warnings = warnings
            )
        }

        val warnings = buildList {
            if (pages.isEmpty()) {
                add("No page sections were available for visual payload probing.")
            }
            if (pages.none { it.mainLayerBitmapDetected }) {
                add("No page exposed a MAINLAYER bitmap payload.")
            }
            if (pages.none { it.bgLayerBitmapDetected }) {
                add("No page exposed a BGLAYER bitmap payload.")
            }
            if (pages.none { it.totalPathDetected }) {
                add("No page exposed a TOTALPATH payload.")
            }
            addAll(report.visualReport.decoderWarnings)
        }.distinct()

        return SupernoteVisualPayloadProbeReport(
            format = "VellumSync Supernote visual payload probe",
            version = 1,
            fileName = report.fileName,
            sha256 = report.sha256,
            versionMarker = report.versionMarker,
            totalPages = pages.size,
            pagesWithMainLayerBitmap = pages.count { it.mainLayerBitmapDetected },
            pagesWithBackgroundBitmap = pages.count { it.bgLayerBitmapDetected },
            pagesWithTotalPath = pages.count { it.totalPathDetected },
            pages = pages,
            warnings = warnings
        )
    }

    private fun probeLayer(
        bytes: ByteArray,
        layer: SupernoteVisualLayerRecord,
        width: Int,
        height: Int
    ): SupernoteVisualPayloadLayerProbe {
        val payloadOffset = layer.bitmapPayloadOffset
        val payloadEnd = layer.estimatedCompressedPayloadEndOffset
            ?.coerceIn(payloadOffset ?: 0, bytes.size)
            ?: layer.nextKnownStructuralOffset?.coerceIn(payloadOffset ?: 0, bytes.size)
        val payloadLength = if (payloadOffset != null && payloadEnd != null && payloadEnd > payloadOffset) {
            payloadEnd - payloadOffset
        } else {
            null
        }
        val payloadDetected = payloadOffset != null && payloadLength != null && payloadLength > 0 && payloadOffset in bytes.indices
        val payload = if (payloadDetected) {
            bytes.copyOfRange(payloadOffset!!, payloadEnd!!)
        } else {
            ByteArray(0)
        }
        val attempts = if (payloadDetected) {
            listOfNotNull(
                bestByteRunValueAttempt(payload, width, height, runFirst = true),
                bestByteRunValueAttempt(payload, width, height, runFirst = false),
                bestNibbleRunAttempt(payload, width, height),
                bestPacked1BppAttempt(payload, width, height, invert = false),
                bestPacked1BppAttempt(payload, width, height, invert = true)
            ).sortedByDescending { it.score }
        } else {
            emptyList()
        }
        val bestAttempt = attempts.maxByOrNull { it.score }
        val decodeStatus = when {
            !payloadDetected -> "no-payload"
            bestAttempt == null -> "payload-present-but-no-attempts"
            bestAttempt.plausible -> "plausible-${bestAttempt.decoderName}"
            else -> "payload-present-but-no-plausible-decoder"
        }
        val warnings = buildList {
            addAll(layer.warnings)
            when {
                !payloadDetected -> add("No valid payload range was available for decode probing.")
                bestAttempt == null -> add("Payload was present but no decode family produced a score.")
                !bestAttempt.plausible -> add(bestAttempt.failureReason)
            }
        }.distinct()

        return SupernoteVisualPayloadLayerProbe(
            pageNumber = layer.pageNumber,
            logicalLayerName = layer.logicalLayerName,
            layerProtocol = layer.layerProtocol,
            parsedLayerName = layer.parsedLayerName,
            layerRecordOffset = layer.layerRecordOffset,
            metadataRecordByteLength = layer.metadataRecordByteLength,
            bitmapPayloadDetected = payloadDetected,
            bitmapPayloadOffset = payloadOffset,
            bitmapPayloadEndOffset = if (payloadDetected) payloadEnd else null,
            bitmapPayloadByteLength = payloadLength,
            payloadTypeGuess = guessPayloadType(layer.layerProtocol, payload),
            payloadHeaderHex = payload.take(HEADER_PREVIEW_BYTES).joinToString(separator = " ") { "%02X".format(it.toInt() and 0xff) },
            payloadHeaderAscii = payload.take(HEADER_PREVIEW_BYTES).toByteArray().toAsciiLikeString(),
            decodeStatus = decodeStatus,
            bestDecoderName = bestAttempt?.decoderName,
            decodeAttempts = attempts,
            warnings = warnings
        )
    }

    private fun guessPayloadType(layerProtocol: String?, payload: ByteArray): String {
        if (payload.isEmpty()) return layerProtocol ?: "none"
        val asciiPreview = payload.take(HEADER_PREVIEW_BYTES).toByteArray().toAsciiLikeString()
        return when {
            layerProtocol != null -> layerProtocol
            payload.size >= 8 && payload[0].toInt() == 0x89 && payload[1].toInt() and 0xff == 0x50 && payload[2].toInt() and 0xff == 0x4e && payload[3].toInt() and 0xff == 0x47 -> "png"
            payload.size >= 2 && (payload[0].toInt() and 0xff) == 0x78 -> "zlib-or-deflate"
            asciiPreview.contains("<") -> "ascii-structured"
            asciiPreview.count { it in ' '..'~' } >= asciiPreview.length / 2 -> "ascii-like-binary"
            else -> "unknown-binary"
        }
    }

    private data class AttemptStats(
        val decoderName: String,
        val headerSkip: Int,
        val decodedPixels: Int,
        val nonWhitePixelCount: Int,
        val darkPixelCount: Int,
        val transitionCount: Int,
        val score: Int,
        val plausible: Boolean,
        val failureReason: String
    ) {
        fun toReport(expectedPixels: Int): SupernoteVisualPayloadDecodeAttempt {
            return SupernoteVisualPayloadDecodeAttempt(
                decoderName = decoderName,
                headerSkip = headerSkip,
                decodedPixels = decodedPixels,
                expectedPixels = expectedPixels,
                nonWhitePixelCount = nonWhitePixelCount,
                darkPixelCount = darkPixelCount,
                transitionCount = transitionCount,
                score = score,
                plausible = plausible,
                failureReason = failureReason
            )
        }
    }

    private fun bestByteRunValueAttempt(
        payload: ByteArray,
        width: Int,
        height: Int,
        runFirst: Boolean
    ): SupernoteVisualPayloadDecodeAttempt? {
        if (payload.size < 2) return null
        val target = width * height
        var best: AttemptStats? = null
        val maxSkip = min(MAX_HEADER_SCAN_BYTES, payload.size - 2)
        for (headerSkip in 0..maxSkip) {
            var out = 0
            var nonWhite = 0
            var dark = 0
            var transitions = 0
            var prev = 255
            var i = headerSkip
            while (i + 1 < payload.size && out < target) {
                val a = payload[i].toInt() and 0xff
                val b = payload[i + 1].toInt() and 0xff
                val run = ((if (runFirst) a else b).takeIf { it > 0 } ?: 1).coerceAtMost(255)
                val raw = if (runFirst) b else a
                val gray = rawToGray(raw)
                if (gray < 245) nonWhite += min(run, target - out)
                if (gray < 120) dark += min(run, target - out)
                if (out > 0 && abs(gray - prev) > 40) transitions += 1
                prev = gray
                out += min(run, target - out)
                i += 2
            }
            val attempt = scoreAttempt(
                decoderName = if (runFirst) "byte-rle-run-value" else "byte-rle-value-run",
                headerSkip = headerSkip,
                target = target,
                decodedPixels = out,
                nonWhite = nonWhite,
                dark = dark,
                transitions = transitions
            )
            if (best == null || attempt.score > best.score) best = attempt
        }
        return best?.toReport(target)
    }

    private fun bestNibbleRunAttempt(payload: ByteArray, width: Int, height: Int): SupernoteVisualPayloadDecodeAttempt? {
        if (payload.isEmpty()) return null
        val target = width * height
        var best: AttemptStats? = null
        val maxSkip = min(MAX_HEADER_SCAN_BYTES, payload.size - 1)
        for (headerSkip in 0..maxSkip) {
            var out = 0
            var nonWhite = 0
            var dark = 0
            var transitions = 0
            var prev = 255
            for (i in headerSkip until payload.size) {
                val byte = payload[i].toInt() and 0xff
                val run = ((byte ushr 4) + 1).coerceAtMost(16)
                val gray = 255 - ((byte and 0x0f) * 17)
                val written = min(run, target - out)
                if (gray < 245) nonWhite += written
                if (gray < 120) dark += written
                if (out > 0 && abs(gray - prev) > 40) transitions += 1
                prev = gray
                out += written
                if (out >= target) break
            }
            val attempt = scoreAttempt(
                decoderName = "nibble-rle",
                headerSkip = headerSkip,
                target = target,
                decodedPixels = out,
                nonWhite = nonWhite,
                dark = dark,
                transitions = transitions
            )
            if (best == null || attempt.score > best.score) best = attempt
        }
        return best?.toReport(target)
    }

    private fun bestPacked1BppAttempt(
        payload: ByteArray,
        width: Int,
        height: Int,
        invert: Boolean
    ): SupernoteVisualPayloadDecodeAttempt? {
        if (payload.isEmpty()) return null
        val target = width * height
        var best: AttemptStats? = null
        val maxSkip = min(MAX_HEADER_SCAN_BYTES, payload.size - 1)
        for (headerSkip in 0..maxSkip) {
            var out = 0
            var nonWhite = 0
            var dark = 0
            var transitions = 0
            var prev = 255
            loop@ for (i in headerSkip until payload.size) {
                val byte = payload[i].toInt() and 0xff
                for (bit in 7 downTo 0) {
                    val one = ((byte ushr bit) and 1) == 1
                    val gray = if (if (invert) !one else one) 0 else 255
                    if (gray < 245) nonWhite += 1
                    if (gray < 120) dark += 1
                    if (out > 0 && abs(gray - prev) > 40) transitions += 1
                    prev = gray
                    out += 1
                    if (out >= target) break@loop
                }
            }
            val attempt = scoreAttempt(
                decoderName = if (invert) "packed-1bpp-inverted" else "packed-1bpp",
                headerSkip = headerSkip,
                target = target,
                decodedPixels = out,
                nonWhite = nonWhite,
                dark = dark,
                transitions = transitions
            )
            if (best == null || attempt.score > best.score) best = attempt
        }
        return best?.toReport(target)
    }

    private fun scoreAttempt(
        decoderName: String,
        headerSkip: Int,
        target: Int,
        decodedPixels: Int,
        nonWhite: Int,
        dark: Int,
        transitions: Int
    ): AttemptStats {
        val plausible = decodedPixels >= target / 2 && nonWhite in 20..(target / 2)
        val coveragePenalty = abs(nonWhite - target / 18)
        val score = decodedPixels / 1024 + transitions / 64 + dark / 128 - coveragePenalty / 512
        val failureReason = when {
            plausible -> "Decode family produced plausible coverage."
            decodedPixels < target / 2 -> "Decode family never reached 50% of the expected page pixels."
            nonWhite < 20 -> "Decode family produced an almost blank page."
            nonWhite > target / 2 -> "Decode family produced overly dense/noisy output, likely not the visual bitmap encoding."
            else -> "Decode family did not satisfy the plausibility heuristics."
        }
        return AttemptStats(
            decoderName = decoderName,
            headerSkip = headerSkip,
            decodedPixels = decodedPixels,
            nonWhitePixelCount = nonWhite,
            darkPixelCount = dark,
            transitionCount = transitions,
            score = score,
            plausible = plausible,
            failureReason = failureReason
        )
    }

    private fun rawToGray(raw: Int): Int {
        return when {
            raw <= 3 -> 255 - raw * 85
            raw <= 15 -> 255 - raw * 17
            else -> raw.coerceIn(0, 255)
        }
    }

    private fun ByteArray.toAsciiLikeString(): String {
        return buildString {
            this@toAsciiLikeString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(if (value in 32..126) value.toChar() else '.')
            }
        }
    }
}
