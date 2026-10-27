package com.geppetto.faxi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * FAXI 글자 스타일. 최소 18sp, 주행 중 읽는 글자는 24sp 이상(body 이상).
 * 금액은 숫자 폭이 같은 tnum을 켜서 영수증 줄의 자릿수가 세로로 맞는다.
 */
val FaxiFont: FontFamily = FontFamily.Default

private const val TabularNumbers = "tnum"

/**
 * 한국어 줄바꿈을 글자 단위가 아니라 어절(띄어쓰기) 단위로: "차 화\n면" 대신 "차\n화면".
 * WordBreak.Phrase는 Android 13+에서 적용되고, 그 아래 기기에서는 무시된다(글자 단위로 돌아감).
 */
private val KoreanLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

/** 기기 언어가 영어여도 글자는 한국어 규칙(줄바꿈, 글꼴 선택)으로 */
private val Korean = LocaleList("ko-KR")

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
    display = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold),
    headline = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    bodyStrong = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    label = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    amount = TextStyle(
        fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers,
    ),
    amountLarge = TextStyle(
        fontFamily = FaxiFont, lineBreak = KoreanLineBreak, localeList = Korean, fontSize = 64.sp, lineHeight = 72.sp,
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
