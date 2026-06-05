package com.example.iluminadordeaudio.ui

import android.content.Intent
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.iluminadordeaudio.render.GlowPreviewRenderer
import com.example.iluminadordeaudio.render.VisualConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PreviewScreen(
    viewModel: MainViewModel,
    onPickAudio: () -> Unit,
    onRecord: () -> Unit,
    onExport: () -> Unit
) {
    val context     = LocalContext.current
    val audioUri         by viewModel.audioUri.collectAsState()
    val audioName        by viewModel.audioName.collectAsState()
    val rmsFrames        by viewModel.rmsFrames.collectAsState()
    val exportState      by viewModel.exportState.collectAsState()
    val outputName       by viewModel.outputName.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()

    var showExportDialog  by remember { mutableStateOf(false) }
    var showCancelDialog  by remember { mutableStateOf(false) }
    var exportStartMs    by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exportState.isExporting) {
        exportStartMs = if (exportState.isExporting) System.currentTimeMillis() else 0L
    }

    // Tick cada 1 segundo para que el countdown del ETA siempre avance,
    // independientemente de si el progreso está cambiando o no.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(exportState.isExporting) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // GL renderer — mismo shader que el export, renderiza directo en la GPU
    val glRenderer = remember { GlowPreviewRenderer() }
    DisposableEffect(Unit) { onDispose { glRenderer.release() } }

    LaunchedEffect(rmsFrames, isPreviewPlaying) {
        var smoothedAmp = 0f
        var haloAmp     = 0f
        while (isActive) {
            val frames = rmsFrames
            if (isPreviewPlaying && frames != null && frames.isNotEmpty()) {
                val posMs    = viewModel.getPreviewPositionMs()
                val frameIdx = ((posMs * 30f) / 1000f).toInt().coerceIn(0, frames.size - 1)
                val target   = frames[frameIdx]
                smoothedAmp += (target - smoothedAmp) * (if (target >= smoothedAmp) VisualConfig.GLOW_ATTACK else VisualConfig.GLOW_DECAY)
                haloAmp     += (target - haloAmp)     * (if (target >= haloAmp)     VisualConfig.HALO_ATTACK else VisualConfig.HALO_DECAY_PREVIEW)
                delay(16L)
            } else {
                smoothedAmp += (0f - smoothedAmp) * 0.08f
                haloAmp     += (0f - haloAmp)     * 0.08f
                delay(50L)
            }

            val amp    = (smoothedAmp * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)
            val hamp   = (haloAmp     * VisualConfig.SENSITIVITY).coerceIn(0f, 1f)
            val excess = ((smoothedAmp - VisualConfig.STRETCH_THRESHOLD) /
                         (1f - VisualConfig.STRETCH_THRESHOLD)).coerceIn(0f, 1f)
            glRenderer.drawFrame(amp, hamp, 1f + excess * VisualConfig.STRETCH_FACTOR)
        }
    }

    val previewShape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // ── Preview centrada, bordecito ────────────────────────────────────────
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
                // TextureView recibe el output del GL shader directamente — sin bitmap intermediario
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = glRenderer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (rmsFrames == null && audioUri != null) {
                    val loadingProgress by viewModel.loadingProgress.collectAsState()

                    // Spinner + porcentaje: centrados en el recuadro
                    Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF7733BB))
                        if (loadingProgress > 0f) {
                            Text(
                                text     = "${(loadingProgress * 100).toInt()} %",
                                color    = Color(0xFFCCADFF),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Botón cancel: a medio camino entre el porcentaje (centro = 50%) y el borde
                    // inferior (100%). El contenedor ocupa el 75% de la altura del recuadro; el
                    // botón se alinea al fondo de ese contenedor → su centro queda en ~75%.
                    if (loadingProgress > 0f) {
                        Box(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.75f)
                                .align(Alignment.TopCenter)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .border(1.dp, LAV_DEEP, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { viewModel.cancelImport() }
                            ) {
                                Text(
                                    text       = "✕",
                                    color      = LAV_SOFT,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
            // Botones ocultos una vez que el video está exportado
            if (exportState.exportedUri == null) {
                Text(
                    text      = audioName ?: "Sin audio seleccionado",
                    color     = Color.Gray,
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines  = 1,
                    modifier  = Modifier.fillMaxWidth()
                )

                // Botón de preview
                val previewEnabled = audioUri != null && rmsFrames != null && !exportState.isExporting
                AppButton(
                    onClick  = { viewModel.togglePreviewPlay() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = previewEnabled,
                    filled   = isPreviewPlaying && previewEnabled
                ) { Text("PREVIEW") }

                val isImporting = audioUri != null && rmsFrames == null
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppButton(onClick = onRecord,    modifier = Modifier.weight(1f),
                        enabled = !exportState.isExporting && !isImporting) { Text("GRABAR") }
                    AppButton(onClick = onPickAudio, modifier = Modifier.weight(1f),
                        enabled = !exportState.isExporting && !isImporting) { Text("IMPORTAR") }
                }

                if (exportState.isExporting) {
                    val pct = (exportState.progress * 100).toInt()
                    val etaText = if (exportState.progress > 0.03f && exportStartMs > 0L) {
                        val elapsedMs   = nowMs - exportStartMs
                        val remainingMs = (elapsedMs / exportState.progress * (1f - exportState.progress)).toLong()
                        val sec = (remainingMs / 1000).toInt().coerceAtLeast(0)
                        if (sec < 60) "~${sec}s" else "~${sec / 60}m ${sec % 60}s"
                    } else "..."

                    // Fila 1: barra de progreso centrada verticalmente con el botón
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { exportState.progress },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .border(1.5.dp, LAV_DEEP, CircleShape)
                                .clip(CircleShape)
                                .clickable { showCancelDialog = true }
                        ) {
                            Text(
                                text       = "✕",
                                color      = LAV_SOFT,
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Fila 2: porcentaje y tiempo estimado
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Exportando… $pct %", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(etaText, color = Color(0xFFCCADFF), fontSize = 13.sp)
                    }
                } else {
                    AppButton(
                        onClick  = {
                            if (isPreviewPlaying) viewModel.togglePreviewPlay()
                            showExportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = audioUri != null && rmsFrames != null
                    ) { Text("EXPORTAR VIDEO") }
                }
            }

            // Resultado — toca para abrir el video
            exportState.exportedUri?.let { uri ->
                val savedText = buildAnnotatedString {
                    append("¡Video guardado! Tocá ")
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append("acá para abrirlo.")
                    }
                }
                Text(
                    text      = savedText,
                    color     = Color(0xFF66BB6A),
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
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
                AppButton(
                    onClick  = viewModel::resetToInitial,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    filled   = true
                ) { Text("NUEVO") }
            }

            exportState.error?.let { err ->
                Text(
                    err,
                    color     = Color(0xFFEF5350),
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick  = viewModel::clearExportState,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Cerrar", color = Color.Gray) }
            }
        }
    }

    // ── Modal cancelar exportación ────────────────────────────────────────────
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title   = { Text("¿Cancelar exportación?") },
            text    = { Text("Se perderá el progreso actual.") },
            confirmButton = {
                AppButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelExport()
                    },
                    filled = true
                ) { Text("SÍ, CANCELAR") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("No, continuar", color = Color.Gray)
                }
            }
        )
    }

    // ── Modal nombre de archivo ────────────────────────────────────────────────
    if (showExportDialog) {
        ExportNameDialog(
            initialName = outputName,
            onConfirm   = { name ->
                viewModel.outputName.value = name
                showExportDialog = false
                onExport()
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun ExportNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Nombre del archivo") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                suffix        = { Text(".mp4", color = Color.Gray) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("EXPORTAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ── Estilo unificado de botón: borde lavanda, fondo negro, texto en lavanda ─────

// Dos tonos: DEEP para fondos/bordes activos, SOFT para texto/símbolos
private val LAV_DEEP = Color(0xFF7733BB)  // profunda → elementos clickeables
private val LAV_SOFT = Color(0xFFCCADFF)  // suave    → texto, etiquetas

@Composable
private fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,   // PREVIEW activo → fondo DEEP, texto negro
    content: @Composable RowScope.() -> Unit
) {
    if (filled) {
        // Estado activo: fondo LAV_DEEP, texto negro
        Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors  = ButtonDefaults.buttonColors(
                containerColor         = LAV_DEEP,
                contentColor           = Color.Black,
                disabledContainerColor = LAV_DEEP.copy(alpha = 0.35f),
                disabledContentColor   = Color.Black.copy(alpha = 0.5f)
            ),
            content = content
        )
    } else {
        // Estado normal: borde LAV_DEEP, texto LAV_SOFT, fondo negro
        OutlinedButton(
            onClick  = onClick,
            modifier = modifier,
            enabled  = enabled,
            border   = BorderStroke(1.5.dp, if (enabled) LAV_DEEP else LAV_DEEP.copy(0.35f)),
            colors   = ButtonDefaults.outlinedButtonColors(
                containerColor         = Color.Black,
                contentColor           = if (enabled) LAV_SOFT else LAV_SOFT.copy(0.35f),
                disabledContainerColor = Color.Black,
                disabledContentColor   = LAV_SOFT.copy(alpha = 0.35f)
            ),
            content  = content
        )
    }
}

