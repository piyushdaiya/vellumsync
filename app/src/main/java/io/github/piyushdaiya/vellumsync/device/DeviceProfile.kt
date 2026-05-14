package io.github.piyushdaiya.vellumsync.device

enum class StylusSupportStatus {
    DETECTED,
    NOT_DETECTED,
    UNKNOWN,
    PROBE_REQUIRED
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val productDevice: String = "",
    val stylusSupportStatus: StylusSupportStatus,
    val isKnownEInkTarget: Boolean,
    val compatibilityNotes: List<String>
)