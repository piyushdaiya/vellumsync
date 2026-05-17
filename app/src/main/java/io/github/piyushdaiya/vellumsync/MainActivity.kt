package io.github.piyushdaiya.vellumsync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.piyushdaiya.vellumsync.ui.VellumSyncApp

// marker=vellumsync-note-open-async-logging-v0
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("VellumSyncOpen", "app onCreate start")
        super.onCreate(savedInstanceState)

        setContent {
            Log.i("VellumSyncOpen", "app setContent attached")
            VellumSyncApp()
        }
    }
}
