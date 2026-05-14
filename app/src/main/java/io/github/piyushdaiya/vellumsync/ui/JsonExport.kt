package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.net.Uri

fun writeTextToUri(
    context: Context,
    uri: Uri,
    text: String
) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    } ?: error("Unable to open export destination.")
}
