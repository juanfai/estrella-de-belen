package com.example.iluminadordeaudio.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private val PRESET_COLORS = listOf(
    "Negro"    to AndroidColor.BLACK,
    "Blanco"   to AndroidColor.WHITE,
    "Rojo"     to AndroidColor.RED,
    "Verde"    to AndroidColor.GREEN,
    "Azul"     to AndroidColor.BLUE,
    "Cian"     to AndroidColor.CYAN,
    "Magenta"  to AndroidColor.MAGENTA,
    "Amarillo" to AndroidColor.YELLOW,
    "Naranja"  to AndroidColor.rgb(255, 165, 0),
    "Violeta"  to AndroidColor.rgb(138, 43, 226),
    "Rosa"     to AndroidColor.rgb(255, 105, 180),
    "Gris"     to AndroidColor.GRAY,
)

@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PRESET_COLORS) { (_, colorInt) ->
                    val isSelected = colorInt == currentColor
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(colorInt), RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.DarkGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onColorSelected(colorInt) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
