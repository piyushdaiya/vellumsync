package io.github.piyushdaiya.vellumsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
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
import io.github.piyushdaiya.vellumsync.device.DeviceProfile
import io.github.piyushdaiya.vellumsync.device.StylusProbeView
import io.github.piyushdaiya.vellumsync.device.StylusSupportStatus

@Composable
fun DeviceCheckScreen(
    profile: DeviceProfile,
    onContinue: () -> Unit
) {
    val stylusConfirmed = remember {
        mutableStateOf(profile.stylusSupportStatus == StylusSupportStatus.DETECTED)
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
                Text(text = "Android: ${profile.androidRelease} / SDK ${profile.sdkInt}")
                Text(text = "Known e-ink target: ${profile.isKnownEInkTarget}")
                Text(text = "Stylus status: ${if (stylusConfirmed.value) "DETECTED" else profile.stylusSupportStatus}")
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
                .height(180.dp),
            factory = { context ->
                StylusProbeView(context) {
                    stylusConfirmed.value = true
                }
            }
        )

        Button(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = onContinue
        ) {
            Text(text = "Continue to .note inspector")
        }
    }
}

