package com.example.aplicacion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Aplica la paleta de las referencias; los colores del sistema no sustituyen la identidad de Vesta. */
@Composable
fun AplicacionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Cream,
            secondary = Forest, onSecondary = Cream,
            background = Cream, onBackground = Ink,
            surface = Cream, onSurface = Ink,
            surfaceVariant = Mint, onSurfaceVariant = Forest,
            error = Color(0xFF8E1725), onError = Color.White,
        ),
        typography = Typography,
        content = content,
    )
}
