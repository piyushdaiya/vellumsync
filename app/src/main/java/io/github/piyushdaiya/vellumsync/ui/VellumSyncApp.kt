package io.github.piyushdaiya.vellumsync.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.piyushdaiya.vellumsync.device.DeviceCapabilityDetector

private enum class Screen {
    DEVICE_CHECK,
    NOTE_INSPECTOR
}

@Composable
fun VellumSyncApp() {
    val currentScreen = remember { mutableStateOf(Screen.DEVICE_CHECK) }
    val deviceProfile = remember { DeviceCapabilityDetector.detect() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen.value) {
                Screen.DEVICE_CHECK -> DeviceCheckScreen(
                    profile = deviceProfile,
                    onContinue = { currentScreen.value = Screen.NOTE_INSPECTOR }
                )

                Screen.NOTE_INSPECTOR -> NoteInspectorScreen(
                    onBack = { currentScreen.value = Screen.DEVICE_CHECK }
                )
            }
        }
    }
}
