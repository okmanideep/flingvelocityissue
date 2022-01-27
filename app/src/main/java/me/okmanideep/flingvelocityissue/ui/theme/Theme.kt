package me.okmanideep.flingvelocityissue.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val PantherGray07 = Color(0xFF373C4D)
internal val PantherGray08 = Color(0xFF252833)
internal val PantherGray09 = Color(0xFF16181F)
internal val PantherGray10 = Color(0xFF0F1014)

private val DarkColorPalette = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200,
    background = PantherGray10,
    surface = PantherGray09,
)

@Composable
fun FlingVelocityIssueTheme(
    content: @Composable() () -> Unit
) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
