package io.github.piyushdaiya.vellumsync.device

import io.github.piyushdaiya.vellumsync.util.JsonText

enum class StylusSupportStatus {
    DETECTED,
    NOT_DETECTED,
    UNKNOWN,
    PROBE_REQUIRED
}

enum class InputDeviceCategory {
    STYLUS,
    TOUCH,
    POINTER,
    KEYBOARD,
    MISC
}

data class InputDeviceDiagnostic(
    val id: Int,
    val name: String,
    val descriptor: String,
    val sourcesHex: String,
    val sourceNames: List<String>,
    val keyboardType: String,
    val category: InputDeviceCategory
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"id\":$id,")
            append("\"name\":${JsonText.quote(name)},")
            append("\"descriptor\":${JsonText.quote(descriptor)},")
            append("\"sourcesHex\":${JsonText.quote(sourcesHex)},")
            append("\"sourceNames\":${JsonText.stringArray(sourceNames)},")
            append("\"keyboardType\":${JsonText.quote(keyboardType)},")
            append("\"category\":${JsonText.quote(category.name)}")
            append("}")
        }
    }
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val productDevice: String,
    val androidRelease: String,
    val sdkInt: Int,
    val stylusSupportStatus: StylusSupportStatus,
    val isKnownEInkTarget: Boolean,
    val compatibilityNotes: List<String>,
    val inputDevices: List<InputDeviceDiagnostic>
) {
    val stylusDevices: List<InputDeviceDiagnostic>
        get() = inputDevices.filter { it.category == InputDeviceCategory.STYLUS }

    val touchDevices: List<InputDeviceDiagnostic>
        get() = inputDevices.filter { it.category == InputDeviceCategory.TOUCH }

    val otherDevices: List<InputDeviceDiagnostic>
        get() = inputDevices.filter {
            it.category != InputDeviceCategory.STYLUS && it.category != InputDeviceCategory.TOUCH
        }

    fun toDiagnosticsJson(stylusProbeConfirmed: Boolean): String {
        return buildString {
            append("{")
            append("\"app\":${JsonText.quote("VellumSync")},")
            append("\"manufacturer\":${JsonText.quote(manufacturer)},")
            append("\"model\":${JsonText.quote(model)},")
            append("\"productDevice\":${JsonText.quote(productDevice)},")
            append("\"androidRelease\":${JsonText.quote(androidRelease)},")
            append("\"sdkInt\":$sdkInt,")
            append("\"stylusSupportStatus\":${JsonText.quote(stylusSupportStatus.name)},")
            append("\"stylusProbeConfirmed\":$stylusProbeConfirmed,")
            append("\"knownEInkTarget\":$isKnownEInkTarget,")
            append("\"compatibilityNotes\":${JsonText.stringArray(compatibilityNotes)},")
            append("\"inputDevices\":[")
            append(inputDevices.joinToString(separator = ",") { it.toJson() })
            append("]")
            append("}")
        }
    }
}
