package io.github.piyushdaiya.vellumsync.note

import io.github.piyushdaiya.vellumsync.util.JsonText
import java.security.MessageDigest

/**
 * Disabled placeholder for the former Boox-created native Supernote note writer.
 *
 * VellumSync now supports sync of Supernote-created notes only. This object is
 * kept only so older builds or stale UI code fail safely instead of generating
 * a native .note from scratch.
 */
@Deprecated(
    message = "Native Boox-created Supernote .note generation is disabled. Import a Supernote-created note and create copy-only sync candidates instead."
)
object BooxCreatedSupernoteNoteWriter {
    fun buildBlankA5xNoteBytes(
        noteFileName: String,
        pageCount: Int = 1
    ): ByteArray {
        error("Boox-created native Supernote .note generation is disabled. Import a Supernote-created note instead.")
    }

    fun buildBlankCanonicalDocumentJson(
        noteFileName: String,
        noteSha256: String,
        notePath: String,
        totalPages: Int
    ): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync disabled Boox-created Supernote note writer")},")
            append("\"status\":${JsonText.quote("disabled")},")
            append("\"reason\":${JsonText.quote("VellumSync supports Supernote-created note sync only. Native .note creation from scratch is not supported.")},")
            append("\"noteFileName\":${JsonText.quote(noteFileName)},")
            append("\"noteSha256\":${JsonText.quote(noteSha256)},")
            append("\"notePath\":${JsonText.quote(notePath)},")
            append("\"totalPages\":$totalPages")
            append("}")
        }
    }

    fun buildCandidateFromTemplate(
        report: SupernoteInspectionReport,
        templateNoteBytes: ByteArray,
        sourceTemplateFileName: String,
        sourceTemplateSha256: String,
        pageNumber: Int,
        transformModeId: String,
        overlayStrokes: List<LocalAnnotationStroke>
    ): BooxCreatedSupernoteNoteCandidate {
        val sha = sha256(templateNoteBytes)
        return BooxCreatedSupernoteNoteCandidate(
            bytes = templateNoteBytes.copyOf(),
            generationStrategy = "disabled-supernote-created-notes-only",
            sourceTemplateFileName = sourceTemplateFileName,
            sourceTemplateSha256 = sourceTemplateSha256,
            computedTemplateSha256 = sha,
            candidateSha256 = sha,
            pageNumber = pageNumber,
            transformModeId = transformModeId,
            originalBytes = templateNoteBytes.size,
            candidateBytes = templateNoteBytes.size,
            totalPathOffset = null,
            oldPageSectionOffset = null,
            newPageSectionOffset = null,
            oldTotalPathPayloadBytes = null,
            newTotalPathPayloadBytes = null,
            oldDeclaredPayloadSize = null,
            newDeclaredPayloadSize = null,
            oldRecordCount = null,
            newRecordCount = null,
            exportedStrokeCount = 0,
            exportedPointCount = overlayStrokes.sumOf { it.points.size },
            skippedStrokeReasons = listOf("Boox-created native Supernote .note generation is disabled."),
            shiftedOffsetReferenceCount = 0,
            shiftedOffsetReferences = emptyList(),
            parserReopenStatus = "not-attempted-disabled",
            parsedCandidateRecordCount = null,
            parserRecordCountMatchesExpected = false,
            warnings = listOf(
                "VellumSync supports Supernote-created note sync only.",
                "Use Sync → Export Supernote sync copy from an imported Supernote-created note."
            )
        )
    }

    fun buildValidationReportJson(
        candidate: BooxCreatedSupernoteNoteCandidate,
        overlayStrokes: List<LocalAnnotationStroke>,
        totalPages: Int
    ): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync Boox-created Supernote note writer report")},")
            append("\"writerVersion\":${JsonText.quote("DISABLED_SUPERNOTE_CREATED_SYNC_ONLY")},")
            append("\"compatibilityStatus\":${JsonText.quote("disabled")},")
            append("\"reason\":${JsonText.quote("VellumSync supports syncing Supernote-created notes only; native Boox-created .note generation is intentionally removed.")},")
            append("\"candidate\":${candidate.toJson()},")
            append("\"overlayInput\":{")
            append("\"strokeCount\":${overlayStrokes.size},")
            append("\"pointCount\":${overlayStrokes.sumOf { it.points.size }},")
            append("\"totalPages\":$totalPages")
            append("},")
            append("\"warnings\":${JsonText.stringArray(candidate.warnings)}")
            append("}")
        }
    }

    fun suggestedCandidateFileName(sourceTemplateFileName: String): String {
        return sourceTemplateFileName.removeSuffix(".note").removeSuffix(".NOTE") + "-vellumsync-sync-copy.note"
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

data class BooxOffsetReferenceAdjustment(
    val tag: String,
    val oldOffset: Int,
    val newOffset: Int
) {
    fun toJson(): String {
        return "{\"tag\":${JsonText.quote(tag)},\"oldOffset\":$oldOffset,\"newOffset\":$newOffset}"
    }
}

data class BooxCreatedSupernoteNoteCandidate(
    val bytes: ByteArray,
    val generationStrategy: String,
    val sourceTemplateFileName: String,
    val sourceTemplateSha256: String,
    val computedTemplateSha256: String,
    val candidateSha256: String,
    val pageNumber: Int,
    val transformModeId: String,
    val originalBytes: Int,
    val candidateBytes: Int,
    val totalPathOffset: Int?,
    val oldPageSectionOffset: Int?,
    val newPageSectionOffset: Int?,
    val oldTotalPathPayloadBytes: Int?,
    val newTotalPathPayloadBytes: Int?,
    val oldDeclaredPayloadSize: Long?,
    val newDeclaredPayloadSize: Long?,
    val oldRecordCount: Long?,
    val newRecordCount: Long?,
    val exportedStrokeCount: Int,
    val exportedPointCount: Int,
    val skippedStrokeReasons: List<String>,
    val shiftedOffsetReferenceCount: Int,
    val shiftedOffsetReferences: List<BooxOffsetReferenceAdjustment>,
    val parserReopenStatus: String,
    val parsedCandidateRecordCount: Int?,
    val parserRecordCountMatchesExpected: Boolean,
    val warnings: List<String>
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"generationStrategy\":${JsonText.quote(generationStrategy)},")
            append("\"candidateBytes\":$candidateBytes,")
            append("\"candidateSha256\":${JsonText.quote(candidateSha256)},")
            append("\"pageNumber\":$pageNumber,")
            append("\"transformModeId\":${JsonText.quote(transformModeId)},")
            append("\"exportedStrokeCount\":$exportedStrokeCount,")
            append("\"exportedPointCount\":$exportedPointCount,")
            append("\"parserReopenStatus\":${JsonText.quote(parserReopenStatus)},")
            append("\"parserRecordCountMatchesExpected\":$parserRecordCountMatchesExpected,")
            append("\"warnings\":${JsonText.stringArray(warnings)}")
            append("}")
        }
    }
}
