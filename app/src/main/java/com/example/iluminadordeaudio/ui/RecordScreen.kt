package com.example.iluminadordeaudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iluminadordeaudio.audio.AudioEditor
import com.example.iluminadordeaudio.audio.AudioPlayer
import com.example.iluminadordeaudio.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class RecordState { IDLE, RECORDING, PAUSED_REC, PLAYING, PAUSED_PLAY }

// ── Dos tonos lavanda con rol funcional ───────────────────────────────────────
// LAV_DEEP: fondo de botones, borde activo → elemento clickeable / primario
// LAV_SOFT: símbolos, texto, onda → contenido / secundario
private val LAV_DEEP  = Color(0xFF7733BB)  // lavanda profunda
private val LAV_SOFT  = Color(0xFFCCADFF)  // lavanda suave
private val LAV_DIM   = Color(0xFF331155)  // lavanda muy oscura (línea central)
private val LAV_TEXT  = Color(0xFFCCADFF)  // = LAV_SOFT para texto

@Composable
fun RecordScreen(
    audioRecorder: AudioRecorder,
    onDone: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()

    var state           by remember { mutableStateOf(RecordState.IDLE) }
    var elapsedSec      by remember { mutableIntStateOf(0) }
    var recordedFile    by remember { mutableStateOf<File?>(null) }
    var recorderStopped by remember { mutableStateOf(false) }
    var baseFile        by remember { mutableStateOf<File?>(null) }
    var tempPlayFile    by remember { mutableStateOf<File?>(null) }   // base+new concat para escuchar
    var isTrimming      by remember { mutableStateOf(false) }
    var recBlink        by remember { mutableStateOf(false) }
    var dotBlink        by remember { mutableStateOf(true) }
    var cursorFraction  by remember { mutableFloatStateOf(1f) }
    val waveform        = remember { mutableStateListOf<Float>() }
    val player          = remember { AudioPlayer() }

    DisposableEffect(Unit) { onDispose { player.release(); tempPlayFile?.delete() } }
    BackHandler(enabled = state != RecordState.IDLE) { /* bloquear back accidental */ }

    // Timer
    LaunchedEffect(state) {
        while (isActive && state == RecordState.RECORDING) {
            delay(1000L); if (state == RecordState.RECORDING) elapsedSec++
        }
    }
    // Punto de estado parpadeante
    LaunchedEffect(state) {
        while (isActive && state == RecordState.RECORDING) { delay(550L); dotBlink = !dotBlink }
        dotBlink = true
    }
    // Parpadeo del botón rec
    LaunchedEffect(state) {
        while (isActive && state == RecordState.RECORDING) { delay(200L); recBlink = !recBlink }
        recBlink = false
    }
    // Muestreo de amplitud
    LaunchedEffect(state) {
        while (isActive && state == RecordState.RECORDING) {
            delay(80L)
            // Re-verificar el estado después del delay: si el usuario tocó STOP mientras
            // el delay estaba en curso, el click ya habrá puesto cursorFraction=0 y
            // no debemos sobreescribirlo con 1f
            if (state != RecordState.RECORDING) break
            val norm = (audioRecorder.getMaxAmplitude() / 32767f).coerceIn(0f, 1f)
            waveform.add(norm)
            if (waveform.size > 800) {
                val kept = ArrayList<Float>(400)
                for (i in waveform.indices step 2) kept.add(waveform[i])
                waveform.clear(); waveform.addAll(kept)
            }
            cursorFraction = 1f
        }
    }
    // Cursor sigue la posición de reproducción.
    // Se captura la posición inicial para no hacer un salto mientras el seek del player se asienta:
    // el cursor se queda en startPos hasta que el player lo alcanza, luego lo sigue suavemente.
    LaunchedEffect(state) {
        if (state == RecordState.PLAYING) {
            val startPos = cursorFraction
            while (isActive && state == RecordState.PLAYING) {
                val playerPos = player.currentPositionFraction
                cursorFraction = if (playerPos >= startPos - 0.01f) playerPos else startPos
                delay(80L)
            }
        }
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    fun doRecord() {
        when (state) {
            RecordState.IDLE -> {
                recordedFile = audioRecorder.start(); recorderStopped = false
                cursorFraction = 1f; state = RecordState.RECORDING
            }
            RecordState.RECORDING -> {
                // STOP: detiene la grabación y lleva el cursor al principio para revisar
                audioRecorder.stop(); recorderStopped = true
                cursorFraction = 0f
                state = RecordState.PAUSED_REC
            }
            RecordState.PAUSED_REC -> {
                if (cursorFraction >= 0.99f) {
                    if (!recorderStopped) {
                        // Recorder en pausa → reanudar normalmente
                        audioRecorder.resume(); state = RecordState.RECORDING
                    } else {
                        // Recorder detenido (fue STOP'd) → continuar grabando al final del audio actual
                        val f = recordedFile ?: return; val prevBase = baseFile
                        isTrimming = true
                        scope.launch {
                            val newBase = if (prevBase != null) {
                                val m = File(context.cacheDir, "base_${System.currentTimeMillis()}.m4a")
                                withContext(Dispatchers.IO) { AudioEditor.concatenate(prevBase, f, m) }
                                prevBase.delete(); f.delete(); m
                            } else f
                            baseFile = newBase
                            elapsedSec = 0; cursorFraction = 1f
                            recordedFile = audioRecorder.start(); recorderStopped = false
                            isTrimming = false; state = RecordState.RECORDING
                        }
                    }
                } else {
                    // Cursor movido → detener (si no lo está), recortar, grabar desde ahí
                    if (!recorderStopped) { audioRecorder.stop(); recorderStopped = true }
                    val f = recordedFile ?: return
                    val fraction = cursorFraction; val prevBase = baseFile
                    isTrimming = true
                    scope.launch {
                        val trimmed = File(context.cacheDir, "trim_${System.currentTimeMillis()}.m4a")
                        withContext(Dispatchers.IO) { AudioEditor.trimTo(f, trimmed, fraction) }
                        f.delete()
                        val newBase = if (prevBase != null) {
                            val m = File(context.cacheDir, "base_${System.currentTimeMillis()}.m4a")
                            withContext(Dispatchers.IO) { AudioEditor.concatenate(prevBase, trimmed, m) }
                            prevBase.delete(); trimmed.delete(); m
                        } else trimmed
                        baseFile = newBase
                        val keep = (fraction * waveform.size).toInt().coerceIn(0, waveform.size)
                        repeat(waveform.size - keep) { waveform.removeAt(waveform.size - 1) }
                        elapsedSec = 0; cursorFraction = 1f
                        recordedFile = audioRecorder.start(); recorderStopped = false
                        isTrimming = false; state = RecordState.RECORDING
                    }
                }
            }
            RecordState.PAUSED_PLAY -> {
                // Rec desde cursor en la reproducción del audio completo
                val tmp = tempPlayFile
                val f   = recordedFile ?: return
                val fraction = cursorFraction
                player.release()
                isTrimming = true
                scope.launch {
                    if (tmp != null) {
                        // Trim el audio completo (base+nuevo) al cursor → nuevo base
                        val trimmed = File(context.cacheDir, "trim_${System.currentTimeMillis()}.m4a")
                        withContext(Dispatchers.IO) { AudioEditor.trimTo(tmp, trimmed, fraction) }
                        tmp.delete(); tempPlayFile = null
                        baseFile?.delete(); f.delete()
                        baseFile = trimmed
                    } else {
                        // No hay audio completo → trim solo el nuevo
                        val prevBase = baseFile
                        val trimmed  = File(context.cacheDir, "trim_${System.currentTimeMillis()}.m4a")
                        withContext(Dispatchers.IO) { AudioEditor.trimTo(f, trimmed, fraction) }
                        f.delete()
                        val newBase = if (prevBase != null) {
                            val m = File(context.cacheDir, "base_${System.currentTimeMillis()}.m4a")
                            withContext(Dispatchers.IO) { AudioEditor.concatenate(prevBase, trimmed, m) }
                            prevBase.delete(); trimmed.delete(); m
                        } else trimmed
                        baseFile = newBase
                    }
                    val keep = (fraction * waveform.size).toInt().coerceIn(0, waveform.size)
                    repeat(waveform.size - keep) { waveform.removeAt(waveform.size - 1) }
                    elapsedSec = 0; cursorFraction = 1f
                    recordedFile = audioRecorder.start(); recorderStopped = false
                    isTrimming = false; state = RecordState.RECORDING
                }
            }
            else -> {}
        }
    }

    fun doPause() {
        when (state) {
            RecordState.RECORDING -> { audioRecorder.pause(); state = RecordState.PAUSED_REC }
            RecordState.PLAYING   -> { player.pause();        state = RecordState.PAUSED_PLAY }
            else -> {}
        }
    }

    fun doPlay() {
        val f = recordedFile ?: return
        when (state) {
            RecordState.PAUSED_REC -> {
                if (!recorderStopped) { audioRecorder.stop(); recorderStopped = true }
                cursorFraction = 0f
                state = RecordState.PLAYING
                val base = baseFile
                if (base != null) {
                    // Reproducir audio completo (base + nueva grabación)
                    scope.launch {
                        val tmp = File(context.cacheDir, "tmp_play_${System.currentTimeMillis()}.m4a")
                        withContext(Dispatchers.IO) { AudioEditor.concatenate(base, f, tmp) }
                        tempPlayFile?.delete()
                        tempPlayFile = tmp
                        player.play(tmp, startFraction = 0f) { state = RecordState.PAUSED_PLAY }
                    }
                } else {
                    player.play(f, startFraction = 0f) { state = RecordState.PAUSED_PLAY }
                }
            }
            RecordState.PAUSED_PLAY -> {
                // Reproducir desde la posición del cursor (el archivo ya está en tempPlayFile o f)
                val playFrom = tempPlayFile ?: f
                player.play(playFrom, startFraction = cursorFraction) { state = RecordState.PAUSED_PLAY }
                state = RecordState.PLAYING
            }
            else -> {}
        }
    }

    fun doSeek(fraction: Float) {
        cursorFraction = fraction
        if (state == RecordState.PLAYING) player.seekTo(fraction)
    }

    fun doOk() {
        player.release()
        tempPlayFile?.delete(); tempPlayFile = null
        if (!recorderStopped) { audioRecorder.stop(); recorderStopped = true }
        val rec = recordedFile ?: return
        val base = baseFile
        if (base != null) {
            scope.launch {
                val final = File(context.cacheDir, "final_${System.currentTimeMillis()}.m4a")
                withContext(Dispatchers.IO) { AudioEditor.concatenate(base, rec, final) }
                base.delete(); rec.delete()
                onDone(final)
            }
        } else onDone(rec)
    }

    fun doCancel() {
        player.release()
        tempPlayFile?.delete(); tempPlayFile = null
        val f = recordedFile
        if (f != null && !recorderStopped) audioRecorder.cancel(f)
        else if (!recorderStopped) audioRecorder.stop()
        baseFile?.delete()
        onCancel()
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    val hasAudio = state != RecordState.IDLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))

        // Estado
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == RecordState.RECORDING) {
                Box(Modifier.size(9.dp).background(
                    if (dotBlink) Color.Red else Color.Transparent, CircleShape))
            }
            val label = when (state) {
                RecordState.IDLE        -> "Listo para grabar"
                RecordState.RECORDING   -> "Grabando"
                RecordState.PAUSED_REC  -> "Grabación pausada"
                RecordState.PLAYING     -> "Reproduciendo"
                RecordState.PAUSED_PLAY -> "Reproducción pausada"
            }
            Text(label, color = if (state == RecordState.RECORDING) Color.Red.copy(0.85f) else LAV_TEXT,
                fontSize = 14.sp)
        }

        Spacer(Modifier.height(16.dp))

        // Timer
        val min = elapsedSec / 60; val sec = elapsedSec % 60
        Text("%02d:%02d".format(min, sec), color = Color.White, fontSize = 66.sp,
            fontWeight = FontWeight.Light, letterSpacing = 2.sp)

        Spacer(Modifier.height(20.dp))

        // Forma de onda con cursor
        val waveformInteractive = state == RecordState.PAUSED_REC ||
                                  state == RecordState.PLAYING ||
                                  state == RecordState.PAUSED_PLAY
        WaveformView(waveform, cursorFraction, waveformInteractive, ::doSeek,
            Modifier.fillMaxWidth().height(148.dp))

        Spacer(Modifier.weight(1f))

        // ── Fila 1: Play | Rec | Pause ────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {

            val playEnabled = state == RecordState.PAUSED_REC || state == RecordState.PAUSED_PLAY
            SmallCircleBtn(enabled = playEnabled, onClick = ::doPlay) {
                Text("▶", fontSize = 22.sp,
                    color = if (playEnabled) LAV_SOFT else LAV_SOFT.copy(alpha = 0.3f))
            }

            Box(contentAlignment = Alignment.Center) {
                RecordBtn(
                    blink       = recBlink,
                    isRecording = state == RecordState.RECORDING,
                    enabled     = !isTrimming && (state == RecordState.IDLE ||
                                  state == RecordState.RECORDING ||   // toca = STOP
                                  state == RecordState.PAUSED_REC ||
                                  state == RecordState.PAUSED_PLAY),
                    onClick     = ::doRecord
                )
                if (isTrimming) {
                    CircularProgressIndicator(color = LAV_SOFT, modifier = Modifier.size(84.dp))
                }
            }

            val pauseEnabled = state == RecordState.RECORDING || state == RecordState.PLAYING
            SmallCircleBtn(enabled = pauseEnabled, onClick = ::doPause) {
                val barColor = if (pauseEnabled) LAV_SOFT else LAV_SOFT.copy(alpha = 0.3f)
                Canvas(Modifier.size(22.dp)) {
                    val bw      = size.width * 0.26f
                    val bh      = size.height * 0.74f
                    val spacing = size.width * 0.18f
                    val startX  = (size.width - bw * 2f - spacing) / 2f   // centrado
                    val ty      = (size.height - bh) / 2f
                    drawRoundRect(barColor, Offset(startX, ty),              Size(bw, bh), CornerRadius(bw / 2f))
                    drawRoundRect(barColor, Offset(startX + bw + spacing, ty), Size(bw, bh), CornerRadius(bw / 2f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Fila 2: ✓ | ✕ ────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlineBtn("✓", hasAudio && state != RecordState.RECORDING && !isTrimming,
                Modifier.weight(1f), ::doOk)
            OutlineBtn("✕", true, Modifier.weight(1f), ::doCancel)
        }

        Spacer(Modifier.height(36.dp))
    }
}

// ── Botón de grabación ────────────────────────────────────────────────────────

@Composable
private fun RecordBtn(blink: Boolean, isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // Animación de color: rojo ↔ rosa durante grabación
    val blinkProg by animateFloatAsState(
        targetValue = if (blink) 1f else 0f, animationSpec = tween(180), label = "blink")
    // Animación de forma: círculo → cuadrado redondeado durante grabación
    val shapeProg by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f, animationSpec = tween(280), label = "shape")

    val fillColor = when {
        isRecording -> androidx.compose.ui.graphics.lerp(Color.Red, Color(0xFFFF88BB), blinkProg)
        enabled     -> Color.Red
        else        -> Color(0xFF882233)
    }
    val borderAlpha = if (enabled) 1f else 0.45f

    Box(
        modifier = Modifier
            .size(80.dp)
            .background(LAV_DEEP.copy(alpha = borderAlpha), CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val full = size.minDimension
            // Fondo negro del círculo interior — se revela cuando el rojo encoge
            drawCircle(Color.Black, radius = full / 2f)
            // Rojo: círculo lleno → cuadrado sin bordes redondeados
            val side   = full - (full - full * 0.36f) * shapeProg
            val corner = (full / 2f) * (1f - shapeProg)   // circle → 0 (sharp square)
            drawRoundRect(
                color        = fillColor,
                topLeft      = Offset((full - side) / 2f, (full - side) / 2f),
                size         = Size(side, side),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}

// ── Botón circular pequeño (play/pause) — LAV_DEEP bg, LAV_SOFT símbolo ──────

@Composable
private fun SmallCircleBtn(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(58.dp)
            .background(if (enabled) LAV_DEEP else LAV_DEEP.copy(0.22f), CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center, content = { content() }
    )
}

// ── Botón ✓ / ✕ — borde LAV_DEEP, símbolo LAV_SOFT ─────────────────────────

@Composable
private fun OutlineBtn(symbol: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(46.dp)
            .border(1.5.dp, if (enabled) LAV_DEEP else LAV_DEEP.copy(0.28f), RoundedCornerShape(50))
            .background(Color.Black, RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 22.sp,
            color = if (enabled) LAV_SOFT else LAV_SOFT.copy(alpha = 0.28f))
    }
}

// ── Forma de onda con cursor titilante ───────────────────────────────────────

@Composable
private fun WaveformView(
    samples: List<Float>, cursorFraction: Float,
    isInteractive: Boolean, onSeek: (Float) -> Unit, modifier: Modifier
) {
    var cursorBlink by remember { mutableStateOf(false) }
    LaunchedEffect(isInteractive) {
        if (isInteractive) { while (true) { delay(250L); cursorBlink = !cursorBlink } }
        else cursorBlink = false
    }
    // Cursor: LAV_SOFT cuando inactivo, alterna LAV_SOFT ↔ blanco cuando interactivo
    val cursorColor = if (isInteractive && cursorBlink) Color.White else LAV_SOFT

    Canvas(
        modifier = modifier
            .then(if (isInteractive) Modifier
                .pointerInput(onSeek) {
                    detectTapGestures { onSeek((it.x / size.width.toFloat()).coerceIn(0f, 1f)) }
                }
                .pointerInput(onSeek) {
                    detectDragGestures(
                        onDragStart = { onSeek((it.x / size.width.toFloat()).coerceIn(0f, 1f)) },
                        onDrag = { c, _ -> onSeek((c.position.x / size.width.toFloat()).coerceIn(0f, 1f)) }
                    )
                }
            else Modifier)
    ) {
        val cy = size.height / 2f
        // Línea central en LAV_DIM (lavanda muy oscura → apenas visible)
        drawLine(LAV_DIM, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)

        if (samples.isNotEmpty()) {
            val minBarW  = 2.dp.toPx()
            val maxBars  = (size.width / (minBarW + 1.dp.toPx())).toInt()
            val visible  = if (samples.size > maxBars) samples.takeLast(maxBars) else samples
            val count    = visible.size
            val step     = size.width / count.toFloat()
            val barW     = (step * 0.60f).coerceIn(minBarW, 6.dp.toPx())

            visible.forEachIndexed { i, amp ->
                val x = i * step + step / 2f
                val h = (amp * cy * 0.96f).coerceAtLeast(2f)
                drawLine(LAV_DEEP, Offset(x, cy - h), Offset(x, cy + h),
                    strokeWidth = barW, cap = StrokeCap.Round)
            }
        }

        // Cursor — clampeado 5dp de cada borde para que el círculo quede siempre visible
        val rawCx = cursorFraction.coerceIn(0f, 1f) * size.width
        val cx    = rawCx.coerceIn(5.dp.toPx(), size.width - 5.dp.toPx())
        drawLine(cursorColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
        drawCircle(cursorColor, radius = 5.dp.toPx(), center = Offset(cx, cy),
            style = Stroke(width = 1.5f * density))
        drawCircle(Color.Black, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
    }
}
