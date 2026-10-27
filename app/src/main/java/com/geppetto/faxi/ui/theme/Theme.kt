package com.geppetto.faxi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** 간격·모서리·크기 토큰 */
object FaxiDimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space6 = 24.dp
    val space8 = 32.dp

    val radiusSm = 12.dp
    val radiusMd = 20.dp
    val radiusLg = 28.dp

    /** Pleos 최소 터치 타깃 */
    val targetMin = 76.dp
    /** 버튼 높이 */
    val controlHeight = 88.dp
    /** 터치 타깃 사이 최소 간격 */
    val targetGap = 16.dp

    val iconMd = 32.dp
    val iconLg = 48.dp
    val sidebarWidth = 168.dp
    val borderWidth = 2.dp
}

private val LocalFaxiColors = staticCompositionLocalOf { NightColors }
private val LocalFaxiType = staticCompositionLocalOf { FaxiTypography }

/** 화면에서 토큰을 꺼내 쓰는 입구: Faxi.colors.ink, Faxi.type.body */
object Faxi {
    val colors: FaxiColors
        @Composable get() = LocalFaxiColors.current
    val type: FaxiType
        @Composable get() = LocalFaxiType.current
}

/**
 * 야간이 기본. 차량은 조명 상태에 따라 시스템이 다크/라이트를 바꾸므로
 * isSystemInDarkTheme()를 따른다. 기기 배경화면 색(dynamic color)은 쓰지 않는다.
 */
@Composable
fun FaxiTheme(
    night: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (night) NightColors else DayColors
    val base = if (night) darkColorScheme() else lightColorScheme()
    val material = base.copy(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.bg, onBackground = colors.ink,
        surface = colors.surface, onSurface = colors.ink,
        error = colors.danger, onError = colors.onDanger,
    )

    CompositionLocalProvider(
        LocalFaxiColors provides colors,
        LocalFaxiType provides FaxiTypography,
    ) {
        MaterialTheme(colorScheme = material, typography = MaterialTypography, content = content)
    }
}
