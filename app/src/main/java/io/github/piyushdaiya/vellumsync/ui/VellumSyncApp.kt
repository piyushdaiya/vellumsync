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
    RECENT_NOTES,
    NOTE_INSPECTOR,
    NOTE_VIEWER
}

@Composable
fun VellumSyncApp() {
    val currentScreen = remember { mutableStateOf(Screen.DEVICE_CHECK) }
    val selectedNote = remember { mutableStateOf<ViewerNoteSelection?>(null) }
    val deviceProfile = remember { DeviceCapabilityDetector.detect() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen.value) {
                Screen.DEVICE_CHECK -> DeviceCheckScreen(
                    profile = deviceProfile,
                    onContinue = { currentScreen.value = Screen.RECENT_NOTES }
                )

                Screen.RECENT_NOTES -> RecentNotesScreen(
                    onBackToDeviceCheck = { currentScreen.value = Screen.DEVICE_CHECK },
                    onOpenDiagnostics = { currentScreen.value = Screen.NOTE_INSPECTOR },
                    onOpenViewer = { selection ->
                        selectedNote.value = selection
                        currentScreen.value = Screen.NOTE_VIEWER
                    }
                )

                Screen.NOTE_INSPECTOR -> NoteInspectorScreen(
                    onBack = { currentScreen.value = Screen.RECENT_NOTES },
                    onOpenViewer = { selection ->
                        selectedNote.value = selection
                        currentScreen.value = Screen.NOTE_VIEWER
                    }
                )

                Screen.NOTE_VIEWER -> {
                    val note = selectedNote.value
                    if (note == null) {
                        RecentNotesScreen(
                            onBackToDeviceCheck = { currentScreen.value = Screen.DEVICE_CHECK },
                            onOpenDiagnostics = { currentScreen.value = Screen.NOTE_INSPECTOR },
                            onOpenViewer = { selection ->
                                selectedNote.value = selection
                                currentScreen.value = Screen.NOTE_VIEWER
                            }
                        )
                    } else {
                        NoteViewerScreen(
                            selection = note,
                            onBack = { currentScreen.value = Screen.RECENT_NOTES }
                        )
                    }
                }
            }
        }
    }
}
