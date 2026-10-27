package com.geppetto.faxi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * FAXI 색 토큰. 택시 노랑 하나를 포인트로 쓰고 나머지는 무채색.
 * 글자 색(ink, inkMuted, accentInk, danger, ok)은 bg·surface·surfaceRaised 어디에 올려도 4.5:1 이상이다.
 * accent는 버튼 면 색이라 글자로 쓰지 않는다. 글자로 강조할 땐 accentInk.
 */
@Immutable
data class FaxiColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val danger: Color,
    val onDanger: Color,
    val ok: Color,
    val onOk: Color,
)

/** 야간(기본) 테마 */
val NightColors = FaxiColors(
    bg = Color(0xFF0E0F12),
    surface = Color(0xFF17191E),
    surfaceRaised = Color(0xFF22252C),
    line = Color(0xFF2E323A),
    lineStrong = Color(0xFF6E7682),
    ink = Color(0xFFF4F5F7),
    inkMuted = Color(0xFFAEB4BE),
    inkDisabled = Color(0xFF6E7682),
    accent = Color(0xFFFFC83D),
    onAccent = Color(0xFF1F1600),
    accentInk = Color(0xFFFFC83D),
    danger = Color(0xFFFF8A6B),
    onDanger = Color(0xFF2B0B03),
    ok = Color(0xFF5FD39A),
    onOk = Color(0xFF04210F),
)

/** 주간 테마 */
val DayColors = FaxiColors(
    bg = Color(0xFFF1F2F4),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE6E8EC),
    line = Color(0xFFD5D8DE),
    lineStrong = Color(0xFF747B87),
    ink = Color(0xFF111317),
    inkMuted = Color(0xFF4B5260),
    inkDisabled = Color(0xFF9BA1AB),
    accent = Color(0xFFFFC83D),
    onAccent = Color(0xFF1F1600),
    accentInk = Color(0xFF7A5500),
    danger = Color(0xFFB53A12),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF176B42),
    onOk = Color(0xFFFFFFFF),
)
