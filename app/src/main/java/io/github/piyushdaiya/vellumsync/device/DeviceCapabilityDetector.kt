package io.github.piyushdaiya.vellumsync.device

import android.os.Build
import android.view.InputDevice

object DeviceCapabilityDetector {
    fun detect(): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val androidRelease = Build.VERSION.RELEASE.orEmpty()
        val sdkInt = Build.VERSION.SDK_INT
        val productDevice = Build.DEVICE.orEmpty()

        val knownBooxTarget = isKnownBooxLikeTarget(
            manufacturer = manufacturer,
            model = model,
            productDevice = productDevice
        )

        val stylusInputDevices = findStylusLikeInputDevices()
        val stylusDetected = stylusInputDevices.isNotEmpty()

        val status = when {
            stylusDetected -> StylusSupportStatus.DETECTED
            knownBooxTarget -> StylusSupportStatus.PROBE_REQUIRED
            else -> StylusSupportStatus.UNKNOWN
        }

        val notes = buildList {
            if (knownBooxTarget) {
                add("Known Android e-ink tablet family detected.")
            }

            if (isBooxNoteAir2Plus(manufacturer, model, productDevice)) {
                add("Boox Note Air 2 Plus profile detected: ONYX / NoteAir2P / BOOX.")
            }

            if (stylusDetected) {
                add("Stylus-like input devices detected: ${stylusInputDevices.joinToString()}.")
            } else {
                add("Stylus input was not confirmed from static input device scan.")
            }

            add("Use the pen probe area to confirm MotionEvent tool type from the physical pen.")
        }

        return DeviceProfile(
            manufacturer = manufacturer,
            model = model,
            androidRelease = androidRelease,
            sdkInt = sdkInt,
            productDevice = productDevice,
            stylusSupportStatus = status,
            isKnownEInkTarget = knownBooxTarget,
            compatibilityNotes = notes
        )
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

    private fun findStylusLikeInputDevices(): List<String> {
        return InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .filter { device -> isStylusLikeDevice(device) }
            .map { device -> device.name }
            .distinct()
            .toList()
    }

    private fun isStylusLikeDevice(device: InputDevice): Boolean {
        val sources = device.sources
        val name = device.name.lowercase()

        val supportsStylusSource =
            sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS

        val hasStylusName =
            name.contains("stylus") ||
                    name.contains("pen") ||
                    name.contains("wacom") ||
                    name.contains("digitizer") ||
                    name.contains("emp")

        return supportsStylusSource || hasStylusName
    }
}