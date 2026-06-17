package com.estrelladebelen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

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
            0    -> Color(0x80FFE4EB)
            1    -> Color(0x80FFF2EE)
            2    -> Color(0x80FDDEE6)
            else -> Color(0x80FFF0F5)
        }
    )
}

/**
 * Full-screen particle system. Sparks fly outward from [origin] (absolute layout coordinates).
 * Render this behind all other content.
 */
@Composable
fun StarfieldBackground(origin: Offset) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Distance from origin to the farthest screen corner
        val maxDist = maxOf(
            hypot(origin.x, origin.y),
            hypot(w - origin.x, origin.y),
            hypot(origin.x, h - origin.y),
            hypot(w - origin.x, h - origin.y)
        ) * 1.1f

        var sparks by remember(origin) { mutableStateOf(List(55) { randomSpark(maxDist) }) }

        LaunchedEffect(origin) {
            while (true) {
                delay(16L)
                val moved = sparks.map { it.copy(distance = it.distance + it.speed) }
                val alive = moved.filter { it.distance < it.maxDistance }
                val spawn = 55 - alive.size
                sparks = alive + List(spawn) { randomSpark(maxDist) }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            sparks.forEach { spark ->
                val progress = spark.distance / spark.maxDistance
                val a = sin(PI.toFloat() * progress).coerceIn(0f, 1f)
                val x = origin.x + cos(spark.angle) * spark.distance
                val y = origin.y + sin(spark.angle) * spark.distance

                drawCircle(
                    color  = spark.color.copy(alpha = a * 0.25f),
                    radius = spark.size * 3.5f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color  = spark.color.copy(alpha = a),
                    radius = spark.size,
                    center = Offset(x, y)
                )
            }
        }
    }
}
