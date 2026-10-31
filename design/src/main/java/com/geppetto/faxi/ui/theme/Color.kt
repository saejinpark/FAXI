package com.geppetto.faxi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * FAXI 색 토큰. 무드는 산호(#F2845F)와 밤 남색(#1F2B42), 앱 아이콘과 같다.
 * 값과 이름은 FAXI 디자인 시스템(https://claude.ai/artifact/13q7cCn7wsigv23nJYGwV2)의 tokens.json과 1:1로 맞춘다.
 * 한쪽을 고치면 다른 쪽도 고친다.
 *
 * 글자 색(ink, inkMuted, accentInk, danger, ok)은 bg·surface·surfaceRaised 어디에 올려도 4.5:1 이상이다.
 * accent는 버튼 면 색이라 글자로 쓰지 않는다. 글자로 강조할 땐 accentInk.
 * danger는 산호와 밝기가 비슷하니 늘 단어나 경고 아이콘과 함께 쓴다.
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
    /** 가족 색 네 칸: 그 사람을 가리키는 표시(자격증 띠, 아바타 테두리)에만. surface 위 글자로도 4.5:1. */
    val family1: Color,
    val family2: Color,
    val family3: Color,
    val family4: Color,
    /** QR은 테마와 상관없이 밝은 종이 위 어두운 점(폰 카메라가 읽도록) */
    val qrPaper: Color,
    val qrInk: Color,
) {
    /** 가족 색 네 칸. 등록한 순서대로 돌려 쓴다(가족이 다섯 명 넘으면 다시 첫 색부터). */
    fun family(slot: Int): Color = when (Math.floorMod(slot, 4)) {
        0 -> family1
        1 -> family2
        2 -> family3
        else -> family4
    }
}

/** 야간(기본) 테마 */
val NightColors = FaxiColors(
    bg = Color(0xFF121A29),
    surface = Color(0xFF1F2B42),
    surfaceRaised = Color(0xFF2A3955),
    line = Color(0xFF33435F),
    lineStrong = Color(0xFF7486A6),
    ink = Color(0xFFF5F1EC),
    inkMuted = Color(0xFFB5BFCE),
    inkDisabled = Color(0xFF7486A6),
    accent = Color(0xFFF2845F),
    onAccent = Color(0xFF1F2B42),
    accentInk = Color(0xFFF79D7D),
    danger = Color(0xFFFF7A93),
    onDanger = Color(0xFF2A0A12),
    ok = Color(0xFF6FD3A8),
    onOk = Color(0xFF0B2A1E),
    family1 = Color(0xFF7FA3D9),
    family2 = Color(0xFFF2845F),
    family3 = Color(0xFF5CC9A0),
    family4 = Color(0xFFB79CE6),
    qrPaper = Color(0xFFFFFFFF),
    qrInk = Color(0xFF121A29),
)

/** 주간 테마 */
val DayColors = FaxiColors(
    bg = Color(0xFFF2F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE6EAF1),
    line = Color(0xFFD2D9E5),
    lineStrong = Color(0xFF6B7890),
    ink = Color(0xFF1F2B42),
    inkMuted = Color(0xFF48566F),
    inkDisabled = Color(0xFF9AA4B5),
    accent = Color(0xFFF2845F),
    onAccent = Color(0xFF1F2B42),
    accentInk = Color(0xFFB0462A),
    danger = Color(0xFFBF2549),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF1B7550),
    onOk = Color(0xFFFFFFFF),
    family1 = Color(0xFF2F5597),
    family2 = Color(0xFFB5472B),
    family3 = Color(0xFF1D7F5B),
    family4 = Color(0xFF6A4EA6),
    qrPaper = Color(0xFFFFFFFF),
    qrInk = Color(0xFF121A29),
)
