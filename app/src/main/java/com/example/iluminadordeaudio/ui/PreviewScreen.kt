package com.example.iluminadordeaudio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iluminadordeaudio.render.GlowRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PreviewScreen(
    viewModel: MainViewModel,
    onPickAudio: () -> Unit,
    onExport: () -> Unit
) {
    val context     = LocalContext.current
    val audioUri    by viewModel.audioUri.collectAsState()
    val audioName   by viewModel.audioName.collectAsState()
    val rmsFrames   by viewModel.rmsFrames.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val outputName  by viewModel.outputName.collectAsState()

    // Bitmap fijo 9:16 para la preview
    val previewBitmap     = remember { Bitmap.createBitmap(270, 480, Bitmap.Config.ARGB_8888) }
    val softCanvas        = remember { ACanvas(previewBitmap) }
    val previewImageBitmap = remember { previewBitmap.asImageBitmap() }
    val renderer          = remember { GlowRenderer() }
    var renderTick        by remember { mutableIntStateOf(0) }

    LaunchedEffect(rmsFrames, softCanvas) {
        var audioFrame  = 0
        var displayTick = 0
        var smoothedAmp = 0f
        while (isActive) {
            val frames = rmsFrames
            if (displayTick % 2 == 0 && frames != null && frames.isNotEmpty()) audioFrame++
            val targetAmp = if (frames != null && frames.isNotEmpty())
                frames[audioFrame % frames.size] else 0f
            val factor = if (targetAmp >= smoothedAmp) 0.35f else 0.07f
            smoothedAmp += (targetAmp - smoothedAmp) * factor
            renderer.drawFrame(softCanvas, smoothedAmp,
                android.graphics.Color.BLACK, android.graphics.Color.WHITE)
            renderTick++
            displayTick++
            delay(16L)
        }
    }

    val previewShape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // ── Preview centrada horizontalmente ───────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
                    .border(1.dp, Color(0xFF444444), previewShape)
                    .clip(previewShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") renderTick
                    drawImage(previewImageBitmap)
                }
                if (rmsFrames == null && audioUri != null) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // ── Controles ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nombre del archivo de audio
            Text(
                text = audioName ?: "Sin audio seleccionado",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            // Nombre del archivo de salida
            OutlinedTextField(
                value = outputName,
                onValueChange = { viewModel.outputName.value = it },
                label = { Text("Nombre del archivo") },
                suffix = { Text(".mp4", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Importar audio
            Button(onClick = onPickAudio, modifier = Modifier.fillMaxWidth()) {
                Text(if (audioUri == null) "Importar audio" else "Cambiar audio")
            }

            // Exportar / progreso
            if (exportState.isExporting) {
                LinearProgressIndicator(
                    progress = { exportState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Exportando… ${(exportState.progress * 100).toInt()}%",
                    color = Color.White, fontSize = 13.sp
                )
            } else {
                Button(
                    onClick  = onExport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = audioUri != null && rmsFrames != null
                ) { Text("Exportar video") }
            }

            // Resultado — toca para abrir el video
            exportState.exportedUri?.let { uri ->
                Text(
                    text = "¡Video guardado! Toca para abrirlo.",
                    color = Color(0xFF66BB6A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/mp4")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp)
                )
                TextButton(
                    onClick = viewModel::clearExportState,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("OK", color = Color.Gray) }
            }

            exportState.error?.let { err ->
                Text(
                    err, color = Color(0xFFEF5350), fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = viewModel::clearExportState,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Cerrar", color = Color.Gray) }
            }
        }
    }
}
