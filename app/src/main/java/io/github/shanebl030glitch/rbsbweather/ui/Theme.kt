// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TempLine = Color(0xFFE8742F)
val RainBar = Color(0xFF6FA8E8)

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FAF), onPrimary = Color.White,
    secondary = Color(0xFFE8742F), surface = Color(0xFFF6F8FB), background = Color(0xFFF6F8FB),
    surfaceVariant = Color(0xFFE6ECF4),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8DB8F2), onPrimary = Color(0xFF0B1F3A),
    secondary = Color(0xFFF2A065), surface = Color(0xFF10151D), background = Color(0xFF10151D),
    surfaceVariant = Color(0xFF1C2430),
)

@Composable
fun RbsbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
