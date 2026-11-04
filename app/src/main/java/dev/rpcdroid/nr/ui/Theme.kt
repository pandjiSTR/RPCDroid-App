package dev.rpcdroid.nr.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blurple = Color(0xFF5865F2)

private val Light = lightColorScheme(primary = Blurple)
private val Dark = darkColorScheme(primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF1B2178))

/** Warna dinamis (Material You) di Android 12+, skema Blurple Discord di bawahnya. Ikut mode gelap sistem. */
@Composable
fun RpcTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
