package com.example.iluminadordeaudio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.iluminadordeaudio.audio.AudioRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen { PREVIEW, RECORDING }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val audioRecorder by lazy { AudioRecorder(this) }
    private var currentScreen by mutableStateOf(AppScreen.PREVIEW)

    // ── Pickers y permisos ────────────────────────────────────────────────────

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadAudio(it, resolveDisplayName(it)) }
    }

    private val requestRecordPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentScreen = AppScreen.RECORDING
        else Toast.makeText(this, "Se necesita permiso de micrófono para grabar",
            Toast.LENGTH_SHORT).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause()  { super.onPause();  viewModel.pausePreviewPlayback() }
    override fun onResume() { super.onResume(); viewModel.resumePreviewPlayback() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                // Pausar/reanudar el preview según la pantalla activa
                androidx.compose.runtime.LaunchedEffect(currentScreen) {
                    when (currentScreen) {
                        AppScreen.RECORDING -> viewModel.pausePreviewPlayback()
                        AppScreen.PREVIEW   -> viewModel.resumePreviewPlayback()
                    }
                }
                when (currentScreen) {
                    AppScreen.PREVIEW -> PreviewScreen(
                        viewModel   = viewModel,
                        onPickAudio = { pickAudio.launch("audio/*") },
                        onRecord    = { handleRecord() },
                        onExport    = { viewModel.startExport() }
                    )
                    AppScreen.RECORDING -> RecordScreen(
                        audioRecorder = audioRecorder,
                        onDone        = { file -> onRecordingDone(file) },
                        onCancel      = { currentScreen = AppScreen.PREVIEW }
                    )
                }
            }
        }
    }

    // ── Grabación ─────────────────────────────────────────────────────────────

    private fun handleRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            currentScreen = AppScreen.RECORDING
        } else {
            requestRecordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onRecordingDone(file: File) {
        val name = "Grabación ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        viewModel.loadAudio(Uri.fromFile(file), name)
        currentScreen = AppScreen.PREVIEW
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        } catch (_: Exception) { null }
    }
}
