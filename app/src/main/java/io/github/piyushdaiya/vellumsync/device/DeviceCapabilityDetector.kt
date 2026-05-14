package io.github.piyushdaiya.vellumsync.device

import android.os.Build
import android.view.InputDevice

object DeviceCapabilityDetector {
    fun detect(): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val androidRelease = Build.VERSION.RELEASE.orEmpty()
        val sdkInt = Build.VERSION.SDK_INT

        val knownBooxTarget = isKnownBooxLikeTarget(manufacturer, model)
        val stylusDetected = hasStylusInputDevice()

        val status = when {
            stylusDetected -> StylusSupportStatus.DETECTED
            knownBooxTarget -> StylusSupportStatus.PROBE_REQUIRED
            else -> StylusSupportStatus.UNKNOWN
        }

        val notes = buildList {
            if (knownBooxTarget) {
                add("Known Android e-ink tablet family detected.")
            }
            if (stylusDetected) {
                add("Android input device scan reports stylus-like input.")
            } else {
                add("Stylus input was not confirmed from the static input device scan.")
            }
            add("Use the pen probe screen to confirm MotionEvent stylus support.")
        }

        return DeviceProfile(
            manufacturer = manufacturer,
            model = model,
            androidRelease = androidRelease,
            sdkInt = sdkInt,
            stylusSupportStatus = status,
            isKnownEInkTarget = knownBooxTarget,
            compatibilityNotes = notes
        )
    }

    private fun isKnownBooxLikeTarget(
        manufacturer: String,
        model: String
    ): Boolean {
        val normalized = "$manufacturer $model".lowercase()
        return normalized.contains("onyx") ||
                normalized.contains("boox") ||
                normalized.contains("note air")
    }

    private fun hasStylusInputDevice(): Boolean {
        return InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .any { device ->
                val sources = device.sources
                val supportsStylusSource =
                    sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
                val supportsTouchscreen =
                    sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN

                supportsStylusSource || device.name.contains("stylus", ignoreCase = true) || supportsTouchscreen
            }
    }
}

