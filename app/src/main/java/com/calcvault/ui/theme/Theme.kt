package com.calcvault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF4C6EF5)
private val AccentDark = Color(0xFF93A8FF)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF5C6478),
    surface = Color(0xFFFAFAFC),
    background = Color(0xFFFAFAFC),
    surfaceVariant = Color(0xFFEDEEF3),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF10214F),
    secondary = Color(0xFFBFC5D6),
    surface = Color(0xFF13151B),
    background = Color(0xFF0E1014),
    surfaceVariant = Color(0xFF23262F),
    error = Color(0xFFFFB4AB),
)

/** The display needs a tabular font so digits do not jitter while typing. */
private val CalcTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
        ),
    )
}

val DisplayNumberStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
)

val DisplayExpressionStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
)

@Composable
fun CalcVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = CalcTypography, content = content)
}
