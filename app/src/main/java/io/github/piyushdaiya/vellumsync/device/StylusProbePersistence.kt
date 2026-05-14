package io.github.piyushdaiya.vellumsync.device

import android.content.Context

object StylusProbePersistence {
    private const val PREFS_NAME = "vellumsync_device_probe"
    private const val KEY_STYLUS_CONFIRMED = "stylus_confirmed"
    private const val KEY_LAST_TOOL_TYPE = "last_tool_type"
    private const val KEY_LAST_X = "last_x"
    private const val KEY_LAST_Y = "last_y"

    fun isStylusConfirmed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_STYLUS_CONFIRMED, false)
    }

    fun saveProbeResult(
        context: Context,
        stylusConfirmed: Boolean,
        lastToolType: String,
        x: Float,
        y: Float
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STYLUS_CONFIRMED, stylusConfirmed)
            .putString(KEY_LAST_TOOL_TYPE, lastToolType)
            .putFloat(KEY_LAST_X, x)
            .putFloat(KEY_LAST_Y, y)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
