package io.github.piyushdaiya.vellumsync.device

import android.os.Build
import android.view.InputDevice
import java.util.Locale

object DeviceCapabilityDetector {
    fun detect(): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val productDevice = Build.DEVICE.orEmpty()
        val androidRelease = Build.VERSION.RELEASE.orEmpty()
        val sdkInt = Build.VERSION.SDK_INT
        val inputDevices = collectInputDeviceDiagnostics()

        val knownBooxTarget = isKnownBooxLikeTarget(
            manufacturer = manufacturer,
            model = model,
            productDevice = productDevice
        )

        val stylusInputDevices = inputDevices.filter {
            it.category == InputDeviceCategory.STYLUS_DIGITIZER
        }
        val stylusDetected = stylusInputDevices.isNotEmpty()

        val status = when {
            stylusDetected -> StylusSupportStatus.DETECTED
            knownBooxTarget -> StylusSupportStatus.PROBE_REQUIRED
            else -> StylusSupportStatus.UNKNOWN
        }

        val touchStylusSourceDevices = inputDevices.filter {
            it.category == InputDeviceCategory.TOUCH_WITH_STYLUS_SOURCE
        }

        val notes = buildList {
            if (knownBooxTarget) {
                add("Known Android e-ink tablet family detected.")
            }

            if (isBooxNoteAir2Plus(manufacturer, model, productDevice)) {
                add("Boox Note Air 2 Plus profile detected: ONYX / NoteAir2P / BOOX.")
            }

            if (stylusDetected) {
                add("Pen digitizer devices detected: ${stylusInputDevices.joinToString { it.name }}.")
            } else {
                add("Dedicated pen digitizer was not confirmed from the static input device scan.")
            }

            if (touchStylusSourceDevices.isNotEmpty()) {
                add("Touch devices with stylus source flags detected separately: ${touchStylusSourceDevices.joinToString { it.name }}.")
            }

            add("Use the pen probe area to confirm MotionEvent tool type from the physical pen.")
        }

        return DeviceProfile(
            manufacturer = manufacturer,
            model = model,
            productDevice = productDevice,
            androidRelease = androidRelease,
            sdkInt = sdkInt,
            stylusSupportStatus = status,
            isKnownEInkTarget = knownBooxTarget,
            compatibilityNotes = notes,
            inputDevices = inputDevices
        )
    }

    private fun collectInputDeviceDiagnostics(): List<InputDeviceDiagnostic> {
        return InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .map { device ->
                val sourceNames = sourceNames(device.sources)
                InputDeviceDiagnostic(
                    id = device.id,
                    name = device.name.orEmpty(),
                    descriptor = device.descriptor.orEmpty(),
                    sourcesHex = String.format(Locale.US, "0x%08X", device.sources),
                    sourceNames = sourceNames,
                    keyboardType = keyboardTypeName(device.keyboardType),
                    category = classifyDevice(device, sourceNames)
                )
            }
            .sortedWith(compareBy<InputDeviceDiagnostic> { it.category.ordinal }.thenBy { it.name })
            .toList()
    }

    private fun classifyDevice(
        device: InputDevice,
        sourceNames: List<String>
    ): InputDeviceCategory {
        val name = device.name.lowercase()
        val sources = device.sources

        val supportsStylusSource =
            sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS

        val hasDedicatedDigitizerName =
            name.contains("wacom") ||
                name.contains("digitizer") ||
                name.contains("stylus") ||
                name.contains("pen") ||
                name.contains("emp")

        val hasTouchName =
            name.contains("touch") ||
                name.contains("cyttsp") ||
                name.contains("goodix") ||
                name.contains("ft5") ||
                name.endsWith("_mt")

        val supportsTouchscreen = sourceNames.contains("TOUCHSCREEN")
        val supportsPointer =
            sourceNames.contains("MOUSE") ||
                sourceNames.contains("TOUCHPAD") ||
                sourceNames.contains("TRACKBALL")

        return when {
            hasDedicatedDigitizerName -> InputDeviceCategory.STYLUS_DIGITIZER
            hasTouchName && supportsStylusSource -> InputDeviceCategory.TOUCH_WITH_STYLUS_SOURCE
            hasTouchName || supportsTouchscreen -> InputDeviceCategory.TOUCH
            supportsStylusSource -> InputDeviceCategory.TOUCH_WITH_STYLUS_SOURCE
            device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE || sourceNames.contains("KEYBOARD") ->
                InputDeviceCategory.KEYBOARD
            supportsPointer -> InputDeviceCategory.POINTER
            else -> InputDeviceCategory.MISC
        }
    }

    private fun sourceNames(sources: Int): List<String> {
        return buildList {
            if (sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN) {
                add("TOUCHSCREEN")
            }
            if (sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) {
                add("STYLUS")
            }
            if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
                add("MOUSE")
            }
            if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) {
                add("KEYBOARD")
            }
            if (sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD) {
                add("TOUCHPAD")
            }
            if (sources and InputDevice.SOURCE_TRACKBALL == InputDevice.SOURCE_TRACKBALL) {
                add("TRACKBALL")
            }
            if (sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
                add("DPAD")
            }
            if (isEmpty()) {
                add("UNKNOWN")
            }
        }
    }

    private fun keyboardTypeName(keyboardType: Int): String {
        return when (keyboardType) {
            InputDevice.KEYBOARD_TYPE_NONE -> "NONE"
            InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC -> "NON_ALPHABETIC"
            InputDevice.KEYBOARD_TYPE_ALPHABETIC -> "ALPHABETIC"
            else -> "UNKNOWN_$keyboardType"
        }
    }

    private fun isKnownBooxLikeTarget(
        manufacturer: String,
        model: String,
        productDevice: String
    ): Boolean {
        val normalized = "$manufacturer $model $productDevice".lowercase()
        return normalized.contains("onyx") ||
            normalized.contains("boox") ||
            normalized.contains("noteair") ||
            normalized.contains("note air")
    }

    private fun isBooxNoteAir2Plus(
        manufacturer: String,
        model: String,
        productDevice: String
    ): Boolean {
        return manufacturer.equals("ONYX", ignoreCase = true) &&
            model.equals("NoteAir2P", ignoreCase = true) &&
            productDevice.equals("BOOX", ignoreCase = true)
    }
}
