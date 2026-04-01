package me.jhot.meld.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun MeldTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = try {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } catch (e: Exception) {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
