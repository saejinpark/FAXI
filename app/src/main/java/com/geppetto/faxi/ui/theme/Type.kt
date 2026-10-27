package com.geppetto.faxi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FAXI 글자 스타일. 최소 18sp, 주행 중 읽는 글자는 24sp 이상(body 이상).
 * 금액은 숫자 폭이 같은 tnum을 켜서 영수증 줄의 자릿수가 세로로 맞는다.
 */
val FaxiFont: FontFamily = FontFamily.Default

private const val TabularNumbers = "tnum"

@Immutable
data class FaxiType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** 영수증·장부 금액 */
    val amount: TextStyle,
    /** 합계·청구액 같은 큰 금액 */
    val amountLarge: TextStyle,
)

val FaxiTypography = FaxiType(
    display = TextStyle(fontFamily = FaxiFont, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold),
    headline = TextStyle(fontFamily = FaxiFont, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = FaxiFont, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = FaxiFont, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    bodyStrong = TextStyle(fontFamily = FaxiFont, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    label = TextStyle(fontFamily = FaxiFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontFamily = FaxiFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    amount = TextStyle(
        fontFamily = FaxiFont, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers,
    ),
    amountLarge = TextStyle(
        fontFamily = FaxiFont, fontSize = 64.sp, lineHeight = 72.sp,
        fontWeight = FontWeight.Bold, fontFeatureSettings = TabularNumbers,
    ),
)

/** Material 컴포넌트를 쓸 때를 위한 매핑 */
val MaterialTypography = Typography(
    displayLarge = FaxiTypography.display,
    headlineLarge = FaxiTypography.headline,
    titleLarge = FaxiTypography.title,
    bodyLarge = FaxiTypography.body,
    labelLarge = FaxiTypography.label,
    bodySmall = FaxiTypography.caption,
)
