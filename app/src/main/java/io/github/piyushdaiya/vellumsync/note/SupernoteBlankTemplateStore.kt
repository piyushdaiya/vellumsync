package io.github.piyushdaiya.vellumsync.note

import android.content.Context
import io.github.piyushdaiya.vellumsync.util.JsonText

/** Disabled placeholder. VellumSync no longer supports native Boox-created Supernote note creation. */
data class SupernoteBlankTemplateInfo(
    val fileName: String,
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
    val importedAtMillis: Long,
    val pageCount: Int,
    val equipment: String,
    val validationStatus: String
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"fileName\":${JsonText.quote(fileName)},")
            append("\"path\":${JsonText.quote(path)},")
            append("\"sha256\":${JsonText.quote(sha256)},")
            append("\"sizeBytes\":$sizeBytes,")
            append("\"importedAtMillis\":$importedAtMillis,")
            append("\"pageCount\":$pageCount,")
            append("\"equipment\":${JsonText.quote(equipment)},")
            append("\"validationStatus\":${JsonText.quote(validationStatus)}")
            append("}")
        }
    }
}

@Deprecated("Blank Supernote template import is disabled. VellumSync supports syncing Supernote-created notes only.")
object SupernoteBlankTemplateStore {
    fun currentInfo(context: Context): SupernoteBlankTemplateInfo? = null
    fun templateBytes(context: Context): ByteArray? = null
    fun importA5xBlankTemplate(
        context: Context,
        sourceFileName: String,
        bytes: ByteArray
    ): SupernoteBlankTemplateInfo {
        error("Import blank template is disabled. Import a Supernote-created note instead.")
    }

    fun diagnosticsJson(context: Context): String {
        return buildString {
            append("{")
            append("\"format\":${JsonText.quote("VellumSync blank template diagnostics")},")
            append("\"templateConfigured\":false,")
            append("\"status\":${JsonText.quote("disabled")},")
            append("\"reason\":${JsonText.quote("VellumSync supports Supernote-created note sync only; native .note creation is removed.")}")
            append("}")
        }
    }
}
