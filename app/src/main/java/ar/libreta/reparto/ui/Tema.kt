package ar.libreta.reparto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colores = lightColorScheme(
    primary = Color(0xFF0B5394),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF062B4D),
    secondary = Color(0xFF2E7D32),
    secondaryContainer = Color(0xFFD7F0D8),
    tertiary = Color(0xFFB45F06),
    tertiaryContainer = Color(0xFFFCE5CD),
    error = Color(0xFFB3261E),
)

@Composable
fun TemaLibreta(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colores, content = content)
}
