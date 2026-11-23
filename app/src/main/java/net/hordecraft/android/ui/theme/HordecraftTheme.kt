package net.hordecraft.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.hordecraft.android.domain.SliderZone

object HordeColors {
    val BgCanvas = Color(0xFF0F131A)
    val BarSurface = Color(0xFF151A23)
    val CardSurface = Color(0xFF171C26)
    val SubCardSurface = Color(0xFF111620)
    val ElevatedButton = Color(0xFF212836)
    val InputSurface = Color(0xFF0F131A)
    val BorderSubtle = Color(0xFF263042)
    val BorderStrong = Color(0xFF2D3648)

    val TealPrimary = Color(0xFF2DD4BF)
    val TealSoft = Color(0xFF5EEAD4)
    val TealContainer = Color(0x262DD4BF)

    val AmberZone = Color(0xFFFBBF24)
    val AmberText = Color(0xFFFCD34D)
    val RoseZone = Color(0xFFF43F5E)
    val RoseText = Color(0xFFFDA4AF)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)
}

fun sliderZoneColor(zone: SliderZone): Color = when (zone) {
    SliderZone.NORMAL -> HordeColors.TealPrimary
    SliderZone.YELLOW -> HordeColors.AmberZone
    SliderZone.RED -> HordeColors.RoseZone
}

private val DarkScheme = darkColorScheme(
    primary = HordeColors.TealPrimary,
    onPrimary = Color(0xFF020617),
    primaryContainer = HordeColors.TealContainer,
    onPrimaryContainer = HordeColors.TealSoft,
    secondary = HordeColors.TealSoft,
    onSecondary = Color(0xFF020617),
    background = HordeColors.BgCanvas,
    onBackground = HordeColors.TextPrimary,
    surface = HordeColors.CardSurface,
    onSurface = HordeColors.TextPrimary,
    surfaceVariant = HordeColors.BarSurface,
    onSurfaceVariant = HordeColors.TextSecondary,
    outline = HordeColors.BorderStrong,
    error = HordeColors.RoseZone,
)

@Composable
fun HordecraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
