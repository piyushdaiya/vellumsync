package io.github.piyushdaiya.vellumsync.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.piyushdaiya.vellumsync.device.DeviceCapabilityDetector

private enum class Screen {
    RECENT_NOTES,
    DEVICE_CHECK,
    NOTE_INSPECTOR,
    NOTE_VIEWER
}

@Composable
// marker=vellumsync-note-open-async-logging-v0
fun VellumSyncApp() {
    // The app opens to the note app / recent notes surface. Device compatibility
    // and diagnostics remain available from Settings/More instead of being the
    // normal launch screen.
    val currentScreen = remember { mutableStateOf(Screen.RECENT_NOTES) }
    val selectedNote = remember { mutableStateOf<ViewerNoteSelection?>(null) }
    val deviceProfile = remember { DeviceCapabilityDetector.detect() }

    LaunchedEffect(currentScreen.value, selectedNote.value?.notePath) {
        Log.i("VellumSyncOpen", "screen=${currentScreen.value} selected=${selectedNote.value?.notePath ?: "none"}")
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen.value) {
                Screen.RECENT_NOTES -> RecentNotesScreen(
                    onBackToDeviceCheck = { currentScreen.value = Screen.DEVICE_CHECK },
                    onOpenDiagnostics = { currentScreen.value = Screen.NOTE_INSPECTOR },
                    onOpenViewer = { selection ->
                        selectedNote.value = selection
                        currentScreen.value = Screen.NOTE_VIEWER
                    }
                )

                Screen.DEVICE_CHECK -> DeviceCheckScreen(
                    profile = deviceProfile,
                    onContinue = { currentScreen.value = Screen.RECENT_NOTES }
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
                        currentScreen.value = Screen.RECENT_NOTES
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
