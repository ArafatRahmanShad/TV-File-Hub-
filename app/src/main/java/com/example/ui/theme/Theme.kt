package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvDarkColorScheme = darkColorScheme(
  primary = TvBlue,
  onPrimary = Color(0xFF003258),
  primaryContainer = TvBlueContainer,
  onPrimaryContainer = Color(0xFFD1E4FF),
  secondary = TvEmerald,
  onSecondary = Color(0xFF003822),
  secondaryContainer = TvEmeraldContainer,
  onSecondaryContainer = Color(0xFFB8F5D0),
  tertiary = TvPurple,
  onTertiary = Color.White,
  background = TvBackground,
  onBackground = Color(0xFFF1F5F9),
  surface = TvSurface,
  onSurface = Color(0xFFF1F5F9),
  surfaceVariant = TvSurfaceVariant,
  onSurfaceVariant = Color(0xFF94A3B8),
  outline = TvOutline,
  outlineVariant = TvOutlineVariant,
  error = TvRose,
  onError = Color(0xFF600010)
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  // Always use dark theme for Android TV
  MaterialTheme(
    colorScheme = TvDarkColorScheme,
    typography = Typography,
    content = content
  )
}
