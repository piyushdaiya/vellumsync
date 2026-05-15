package io.github.piyushdaiya.vellumsync.ui

import android.content.Context

enum class RailPosition(
    val id: String,
    val label: String
) {
    LEFT("left", "Left rail"),
    RIGHT("right", "Right rail");

    companion object {
        fun fromId(id: String?): RailPosition {
            return values().firstOrNull { it.id == id } ?: LEFT
        }
    }
}

object RailPositionPersistence {
    private const val PREFS = "vellumsync_note_surface"
    private const val KEY_RAIL_POSITION = "rail_position"

    fun load(context: Context): RailPosition {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return RailPosition.fromId(prefs.getString(KEY_RAIL_POSITION, RailPosition.LEFT.id))
    }

    fun save(context: Context, position: RailPosition) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RAIL_POSITION, position.id)
            .apply()
    }
}
