package com.examreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.examreader.data.AppSettings

/** 学院蓝：全 App 唯一主色（不再提供换色） */
val AccentBlue = Color(0xFF1D5FC0)

val AnswerRed = Color(0xFFC62828)
val PointAmber = Color(0xFFB45309)
val PassageMuted = Color(0xFF475569)
val DividerColor = Color(0xFFDDE5EE)
val PageBackground = Color(0xFFEEF2F7)
val OkGreen = Color(0xFF1B7F4B)
val OkGreenBg = Color(0xFFE6F5EC)
val WrongRedBg = Color(0xFFFDECEC)
val PickBlueBg = Color(0xFFE8F2FD)

@Composable
fun ExamReaderTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = AccentBlue,
        onPrimary = Color.White,
        primaryContainer = AccentBlue.copy(alpha = 0.12f),
        onPrimaryContainer = AccentBlue,
        secondary = PassageMuted,
        onSecondary = Color.White,
        background = PageBackground,
        onBackground = Color(0xFF1F2937),
        surface = Color.White,
        onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFF7F9FC),
        onSurfaceVariant = PassageMuted,
        outline = DividerColor,
        outlineVariant = DividerColor,
        error = AnswerRed
    )
    val density = LocalDensity.current
    // 字号设置：直接改 fontScale，所有文字等比缩放
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
