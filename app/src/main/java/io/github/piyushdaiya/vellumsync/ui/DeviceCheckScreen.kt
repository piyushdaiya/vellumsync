package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.piyushdaiya.vellumsync.device.DeviceProfile
import io.github.piyushdaiya.vellumsync.device.InputDeviceDiagnostic
import io.github.piyushdaiya.vellumsync.device.StylusProbePersistence
import io.github.piyushdaiya.vellumsync.device.StylusProbeView
import io.github.piyushdaiya.vellumsync.device.StylusSupportStatus

@Composable
fun DeviceCheckScreen(
    profile: DeviceProfile,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val persistedStylus = remember { StylusProbePersistence.isStylusConfirmed(context) }
    val stylusConfirmed = remember {
        mutableStateOf(
            persistedStylus || profile.stylusSupportStatus == StylusSupportStatus.DETECTED
        )
    }
    val exportError = remember { mutableStateOf<String?>(null) }
    val pendingExportJson = remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeTextToUri(
                    context = context,
                    uri = uri,
                    text = pendingExportJson.value.orEmpty()
                )
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export diagnostics JSON."
            }
        }
    }

    val message = when {
        stylusConfirmed.value ->
            "Stylus support detected. VellumSync is ready for handwritten .note workflows on this device."

        profile.stylusSupportStatus == StylusSupportStatus.PROBE_REQUIRED ->
            "Stylus support could not be confirmed from static device data. Touch the probe area with your pen."

        profile.stylusSupportStatus == StylusSupportStatus.UNKNOWN ->
            "Stylus support is unknown. You can inspect .note files now, but handwriting/editing features require a stylus-capable e-ink Android device."

        else ->
            "No active stylus support was detected. You can browse and inspect compatible .note files, but editing features require stylus support."
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "VellumSync")
        Text(text = "Device check")

        Card(colors = CardDefaults.cardColors()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Manufacturer: ${profile.manufacturer}")
                Text(text = "Model: ${profile.model}")
                Text(text = "Product device: ${profile.productDevice}")
                Text(text = "Android: ${profile.androidRelease} / SDK ${profile.sdkInt}")
                Text(text = "Known e-ink target: ${profile.isKnownEInkTarget}")
                Text(
                    text = "Stylus status: ${if (stylusConfirmed.value) "DETECTED" else profile.stylusSupportStatus}"
                )
            }
        }

        Text(text = message)

        profile.compatibilityNotes.forEach { note ->
            Text(text = "• $note")
        }

        Text(text = "Pen probe")

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            factory = { androidContext ->
                StylusProbeView(androidContext) { result ->
                    if (result.stylusDetected) {
                        stylusConfirmed.value = true
                    }
                    StylusProbePersistence.saveProbeResult(
                        context = androidContext,
                        stylusConfirmed = stylusConfirmed.value,
                        lastToolType = result.toolTypeName,
                        x = result.x,
                        y = result.y
                    )
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                onClick = onContinue
            ) {
                Text(text = "Continue to .note inspector")
            }

            Button(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                onClick = {
                    StylusProbePersistence.clear(context)
                    stylusConfirmed.value = profile.stylusSupportStatus == StylusSupportStatus.DETECTED
                }
            ) {
                Text(text = "Reset probe")
            }
        }

        Button(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = {
                pendingExportJson.value = profile.toDiagnosticsJson(stylusConfirmed.value)
                exportLauncher.launch("vellumsync-device-diagnostics.json")
            }
        ) {
            Text(text = "Export device diagnostics JSON")
        }

        exportError.value?.let { error ->
            Text(text = "Export error: $error")
        }

        Text(text = "Device diagnostics")
        Text(text = "Stylus / digitizer devices")
        DeviceList(devices = profile.stylusDevices)
        Text(text = "Touch devices")
        DeviceList(devices = profile.touchDevices)
        Text(text = "Other input devices")
        DeviceList(devices = profile.otherDevices)
    }
}

@Composable
private fun DeviceList(devices: List<InputDeviceDiagnostic>) {
    if (devices.isEmpty()) {
        Text(text = "None detected")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "${device.category}: ${device.name}")
                    Text(text = "id=${device.id} sources=${device.sourcesHex}")
                    Text(text = "source names=${device.sourceNames.joinToString()}")
                    Text(text = "keyboard=${device.keyboardType}")
                    if (device.descriptor.isNotBlank()) {
                        Text(text = "descriptor=${device.descriptor}")
                    }
                }
            }
        }
    }
}
