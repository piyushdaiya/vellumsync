package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.net.Uri

fun writeTextToUri(
    context: Context,
    uri: Uri,
    text: String
) {
    writeBytesToUri(
        context = context,
        uri = uri,
        bytes = text.toByteArray(Charsets.UTF_8)
    )
}

fun writeBytesToUri(
    context: Context,
    uri: Uri,
    bytes: ByteArray
) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(bytes)
        output.flush()
    } ?: error("Unable to open export destination.")
}
