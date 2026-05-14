package io.github.piyushdaiya.vellumsync.ui

import android.content.Context

object ViewerTransformPersistence {
    private const val PREFS_NAME = "vellumsync-viewer-preferences"
    private const val KEY_PREFIX = "transform-mode-"

    fun load(
        context: Context,
        noteSha256: String
    ): SupernotePreviewTransformMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SupernotePreviewTransformMode.fromId(prefs.getString(KEY_PREFIX + noteSha256, null))
    }

    fun save(
        context: Context,
        noteSha256: String,
        mode: SupernotePreviewTransformMode
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + noteSha256, mode.id)
            .apply()
    }
}
