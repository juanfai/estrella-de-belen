package com.estrelladebelen.app.ui.screens

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.estrelladebelen.app.R
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

@Composable
fun SplashScreen(onReady: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000, easing = EaseInOut),
        label = "splash_fade"
    )

    LaunchedEffect(Unit) {
        delay(2000)
        onReady()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        StarfieldBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_luz),
                contentDescription = "Logo Estrella de Belén",
                modifier = Modifier
                    .size(220.dp)
                    .padding(bottom = 24.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Particle system ───────────────────────────────────────────────────────────

private data class Spark(
    val angle: Float,
    val distance: Float,
    val speed: Float,
    val maxDistance: Float,
    val size: Float,
    val color: Color
)

private fun randomSpark(maxDist: Float): Spark {
    val rng = Random.Default
    return Spark(
        angle       = rng.nextFloat() * 2f * PI.toFloat(),
        distance    = rng.nextFloat() * maxDist * 0.25f,
        speed       = rng.nextFloat() * 3f + 1.5f,
        maxDistance = maxDist * (rng.nextFloat() * 0.4f + 0.6f),
        size        = rng.nextFloat() * 2.8f + 1.4f,
        color       = when (rng.nextInt(6)) {
            0    -> Color(0x80FFE4EB) // rosa claro
            1    -> Color(0x80FFF2EE) // Moonbeam
            2    -> Color(0x80FDDEE6) // rosa muy suave
            else -> Color(0x80FFF0F5) // blanco rosado
        }
    )
}

@Composable
private fun StarfieldBackground() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val maxDist = sqrt(w * w / 4f + h * h / 4f) * 1.1f
        // El logo es el top de la Column centrada; su centro está ~40dp arriba del centro de pantalla
        val originOffsetY = with(density) { (-40).dp.toPx() }

        var sparks by remember { mutableStateOf(List(55) { randomSpark(maxDist) }) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(16L)
                val moved = sparks.map { it.copy(distance = it.distance + it.speed) }
                val alive = moved.filter { it.distance < it.maxDistance }
                val spawn = 55 - alive.size
                sparks = alive + List(spawn) { randomSpark(maxDist) }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f + originOffsetY

            sparks.forEach { spark ->
                val progress = spark.distance / spark.maxDistance
                val a = sin(PI.toFloat() * progress).coerceIn(0f, 1f)
                val x = cx + cos(spark.angle) * spark.distance
                val y = cy + sin(spark.angle) * spark.distance

                // halo suave exterior
                drawCircle(
                    color  = spark.color.copy(alpha = a * 0.25f),
                    radius = spark.size * 3.5f,
                    center = Offset(x, y)
                )
                // núcleo brillante
                drawCircle(
                    color  = spark.color.copy(alpha = a),
                    radius = spark.size,
                    center = Offset(x, y)
                )
            }
        }
    }
}
