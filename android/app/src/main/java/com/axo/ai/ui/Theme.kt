package com.axo.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Neon1 = Color(0xFF8B5CF6)
val Neon2 = Color(0xFF3B82F6)
val Neon3 = Color(0xFF22D3EE)
val neonBrush = Brush.linearGradient(listOf(Neon1, Neon2, Neon3))

private val Dark = darkColorScheme(
    primary = Neon1, secondary = Neon2, tertiary = Neon3,
    background = Color(0xFF0B0B14), surface = Color(0xFF151526), surfaceVariant = Color(0xFF1C1C33),
    onBackground = Color(0xFFEDEDF7), onSurface = Color(0xFFEDEDF7),
)
private val Light = lightColorScheme(
    primary = Neon1, secondary = Neon2, tertiary = Neon3,
    background = Color(0xFFF7F7FC), surface = Color.White, surfaceVariant = Color(0xFFEDEBFA),
)

@Composable
fun AxoTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

@Composable
fun Logo(size: TextUnit = 24.sp) = Text(
    "AXO AI",
    style = TextStyle(brush = neonBrush, fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
)

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.fillMaxWidth().height(52.dp)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = Neon1, spotColor = Neon2)
            .clip(shape)
            .background(if (enabled) neonBrush else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
