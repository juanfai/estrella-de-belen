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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.drawscope.Stroke as DStroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iluminadordeaudio.render.GlowRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val HALO_COLOR = android.graphics.Color.rgb(60, 0, 255)  // violeta eléctrico

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

    var showExportDialog by remember { mutableStateOf(false) }

    // Bitmap 9:16 para la preview — 405×720 da buena base sin penalizar el blur en CPU
    val previewBitmap      = remember { Bitmap.createBitmap(405, 720, Bitmap.Config.ARGB_8888) }
    val softCanvas         = remember { ACanvas(previewBitmap) }
    val previewImageBitmap = remember { previewBitmap.asImageBitmap() }
    val renderer           = remember { GlowRenderer() }
    var renderTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(rmsFrames, isPreviewPlaying) {
        var smoothedAmp = 0f
        var haloAmp     = 0f
        while (isActive) {
            val frames = rmsFrames
            if (isPreviewPlaying && frames != null && frames.isNotEmpty()) {
                // Sincronizar con la posición real del audio en lugar de contar ticks
                val posMs     = viewModel.getPreviewPositionMs()
                val frameIdx  = ((posMs * 30f) / 1000f).toInt().coerceIn(0, frames.size - 1)
                val targetAmp = frames[frameIdx]
                smoothedAmp += (targetAmp - smoothedAmp) * (if (targetAmp >= smoothedAmp) 0.35f else 0.07f)
                haloAmp     += (targetAmp - haloAmp)     * (if (targetAmp >= haloAmp)     0.35f else 0.025f)
                delay(16L)
            } else {
                // Preview detenido: decaer suavemente a negro
                smoothedAmp += (0f - smoothedAmp) * 0.08f
                haloAmp     += (0f - haloAmp)     * 0.08f
                delay(50L)
            }

            val excess = ((smoothedAmp - 0.44f) / (1f - 0.44f)).coerceIn(0f, 1f)
            softCanvas.save()
            if (excess > 0f) {
                softCanvas.scale(1f, 1f + excess * 0.77f,
                    previewBitmap.width * 0.5f, previewBitmap.height * 0.5f)
            }
            renderer.drawFrame(softCanvas, haloAmp,
                android.graphics.Color.BLACK, HALO_COLOR, clearBackground = true)
            renderer.drawFrame(softCanvas, smoothedAmp,
                android.graphics.Color.BLACK, android.graphics.Color.WHITE, clearBackground = false)
            softCanvas.restore()
            renderTick++
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") renderTick
                    drawImage(
                        image         = previewImageBitmap,
                        dstSize       = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.High
                    )
                }
                if (rmsFrames == null && audioUri != null) {
                    CircularProgressIndicator(
                        color    = Color.White,
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
            Text(
                text      = audioName ?: "Sin audio seleccionado",
                color     = Color.Gray,
                fontSize  = 12.sp,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                modifier  = Modifier.fillMaxWidth()
            )

            // Botón de preview: siempre "PREVIEW", relleno lavanda cuando reproduce
            if (audioUri != null) {
                AppButton(
                    onClick  = { viewModel.togglePreviewPlay() },
                    modifier = Modifier.fillMaxWidth(),
                    filled   = isPreviewPlaying
                ) { Text("PREVIEW") }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppButton(onClick = onRecord,    modifier = Modifier.weight(1f)) { Text("GRABAR") }
                AppButton(onClick = onPickAudio, modifier = Modifier.weight(1f)) {
                    Text(if (audioUri == null) "IMPORTAR" else "CAMBIAR")
                }
            }

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
                AppButton(
                    onClick  = {
                        if (isPreviewPlaying) viewModel.togglePreviewPlay()
                        showExportDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = audioUri != null && rmsFrames != null
                ) { Text("EXPORTAR VIDEO") }
            }

            // Resultado — toca para abrir el video
            exportState.exportedUri?.let { uri ->
                Text(
                    text      = "¡Video guardado! Toca para abrirlo.",
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
                    onClick  = viewModel::clearExportState,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    filled   = true
                ) { Text("OK") }
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

// ── Icono de parlante dibujado con líneas (sin emoji) ────────────────────────

@Composable
private fun SpeakerIcon(isMuted: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val sw = density * 1.5f
        val w = size.width; val h = size.height

        // Cuerpo del parlante: barra izquierda + bocina trapezoidal
        drawLine(color, Offset(0f, h * 0.28f), Offset(0f, h * 0.72f), strokeWidth = sw * 2.2f)
        drawLine(color, Offset(0f, h * 0.28f), Offset(w * 0.42f, 0f),  strokeWidth = sw)
        drawLine(color, Offset(0f, h * 0.72f), Offset(w * 0.42f, h),   strokeWidth = sw)
        drawLine(color, Offset(w * 0.42f, 0f),  Offset(w * 0.42f, h),  strokeWidth = sw)

        if (!isMuted) {
            // Ondas de sonido: 2 arcos
            drawArc(color, startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(w * 0.50f, h * 0.18f),
                size    = GeoSize(w * 0.26f, h * 0.64f),
                style   = DStroke(width = sw))
            drawArc(color, startAngle = -62f, sweepAngle = 124f, useCenter = false,
                topLeft = Offset(w * 0.66f, h * 0.04f),
                size    = GeoSize(w * 0.32f, h * 0.92f),
                style   = DStroke(width = sw))
        } else {
            // Cruz para silenciado
            drawLine(color, Offset(w * 0.52f, h * 0.18f), Offset(w * 0.96f, h * 0.82f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.96f, h * 0.18f), Offset(w * 0.52f, h * 0.82f), strokeWidth = sw)
        }
    }
}
