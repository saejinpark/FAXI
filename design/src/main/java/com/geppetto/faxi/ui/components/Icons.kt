package com.geppetto.faxi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/*
 * 선 아이콘. 28 × 28 격자에 선 3 단위로 그리고 size에 맞춰 늘린다.
 * 아이콘 라이브러리를 추가하지 않으려고 직접 그렸다. Pleos 공식 아이콘을 받으면 교체한다.
 * 전부 정지 그림이다. 움직이는 아이콘은 만들지 않는다.
 */

private fun DrawScope.u(): Float = size.width / 28f
private fun DrawScope.stroke(): Stroke = Stroke(width = 3f * u(), cap = StrokeCap.Round, join = StrokeJoin.Round)
private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    val s = u()
    drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), 3f * s, StrokeCap.Round)
}
private fun DrawScope.path(color: Color, vararg points: Pair<Float, Float>, close: Boolean = false) {
    val s = u()
    val p = Path().apply {
        moveTo(points[0].first * s, points[0].second * s)
        points.drop(1).forEach { (x, y) -> lineTo(x * s, y * s) }
        if (close) close()
    }
    drawPath(p, color, style = stroke())
}

@Composable
private fun Icon28(size: Dp, draw: DrawScope.() -> Unit) {
    Canvas(Modifier.size(size), onDraw = draw)
}

@Composable
fun CheckIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 6f to 14.5f, 11.5f to 20f, 22f to 9f)
}

@Composable
fun WarningIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 14f to 4f, 26f to 24f, 2f to 24f, close = true)
    line(color, 14f, 11f, 14f, 16f)
    line(color, 14f, 20f, 14f, 20.5f)
}

@Composable
fun PlusIcon(color: Color, size: Dp) = Icon28(size) {
    line(color, 14f, 6f, 14f, 22f)
    line(color, 6f, 14f, 22f, 14f)
}

@Composable
fun MinusIcon(color: Color, size: Dp) = Icon28(size) {
    line(color, 6f, 14f, 22f, 14f)
}

/** 키패드 지우기 */
@Composable
fun BackspaceIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 10f to 6f, 25f to 6f, 25f to 22f, 10f to 22f, 3f to 14f, close = true)
    line(color, 14f, 10f, 20f, 18f)
    line(color, 20f, 10f, 14f, 18f)
}

/** 별점. filled면 속을 채운다(선택 상태가 색만이 아니라 모양으로도 보이게). */
@Composable
fun StarIcon(color: Color, size: Dp, filled: Boolean) = Icon28(size) {
    val s = u()
    val p = Path().apply {
        val pts = listOf(
            14f to 3f, 17.2f to 10.4f, 25f to 11f, 19f to 16.2f, 20.8f to 24f,
            14f to 19.8f, 7.2f to 24f, 9f to 16.2f, 3f to 11f, 10.8f to 10.4f,
        )
        moveTo(pts[0].first * s, pts[0].second * s)
        pts.drop(1).forEach { (x, y) -> lineTo(x * s, y * s) }
        close()
    }
    if (filled) drawPath(p, color)
    drawPath(p, color, style = stroke())
}

/** 미터기: 열린 눈금판(계기판 모양) + 눈금 + 고정된 바늘. 바늘은 움직이지 않는다. */
@Composable
fun MeterIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    drawArc(
        color, startAngle = 150f, sweepAngle = 240f, useCenter = false,
        topLeft = Offset(3f * s, 4f * s), size = Size(22f * s, 22f * s), style = stroke(),
    )
    line(color, 14f, 8.5f, 14f, 10f)
    line(color, 7.5f, 11.5f, 8.6f, 12.6f)
    line(color, 20.5f, 11.5f, 19.4f, 12.6f)
    line(color, 14f, 15f, 19f, 10.5f)
    drawCircle(color, radius = 2f * s, center = Offset(14f * s, 15f * s))
}

/** 탑승: 사람 */
@Composable
fun PersonIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    drawCircle(color, radius = 5f * s, center = Offset(14f * s, 9f * s), style = stroke())
    val body = Path().apply {
        moveTo(4f * s, 25f * s)
        quadraticTo(4f * s, 17f * s, 14f * s, 17f * s)
        quadraticTo(24f * s, 17f * s, 24f * s, 25f * s)
    }
    drawPath(body, color, style = stroke())
}

/** 영수증: 아래가 톱니인 종이 */
@Composable
fun ReceiptIcon(color: Color, size: Dp) = Icon28(size) {
    path(
        color,
        6f to 3f, 22f to 3f, 22f to 25f, 19f to 22.5f, 16.5f to 25f, 14f to 22.5f,
        11.5f to 25f, 9f to 22.5f, 6f to 25f, close = true,
    )
    line(color, 10f, 9f, 18f, 9f)
    line(color, 10f, 14f, 18f, 14f)
}

/** 가족 장부: 펼친 책 */
@Composable
fun LedgerIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 14f to 7f, 14f to 24f)
    path(color, 14f to 7f, 8f to 4.5f, 3f to 5f, 3f to 22f, 8f to 21.5f, 14f to 24f)
    path(color, 14f to 7f, 20f to 4.5f, 25f to 5f, 25f to 22f, 20f to 21.5f, 14f to 24f)
}

/** 기사 명단: 사원증 카드 */
@Composable
fun BadgeIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    drawRoundRect(
        color, topLeft = Offset(3f * s, 6f * s), size = Size(22f * s, 17f * s),
        cornerRadius = CornerRadius(3f * s), style = stroke(),
    )
    drawCircle(color, radius = 3f * s, center = Offset(10f * s, 13f * s), style = stroke())
    line(color, 6.5f, 19.5f, 13.5f, 19.5f)
    line(color, 17f, 12f, 21f, 12f)
    line(color, 17f, 17f, 21f, 17f)
}

/** 정산: 주고받는 두 화살표 */
@Composable
fun SwapIcon(color: Color, size: Dp) = Icon28(size) {
    line(color, 4f, 9f, 23f, 9f)
    path(color, 18f to 4f, 23f to 9f, 18f to 14f)
    line(color, 24f, 19f, 5f, 19f)
    path(color, 10f to 14f, 5f to 19f, 10f to 24f)
}

/** 소리 내어 읽기 */
@Composable
fun SpeakerIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 4f to 11f, 9f to 11f, 15f to 6f, 15f to 22f, 9f to 17f, 4f to 17f, close = true)
    val s = u()
    val wave = Path().apply {
        moveTo(19f * s, 10f * s); quadraticTo(22.5f * s, 14f * s, 19f * s, 18f * s)
    }
    drawPath(wave, color, style = stroke())
}

/** 뒤로가기 */
@Composable
fun BackIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 17f to 6f, 9f to 14f, 17f to 22f)
}

/** 눌러서 들어가는 줄의 끝 표시 */
@Composable
fun ChevronIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 11f to 6f, 19f to 14f, 11f to 22f)
}

/** 냉방: 눈송이 */
@Composable
fun SnowflakeIcon(color: Color, size: Dp) = Icon28(size) {
    line(color, 14f, 3f, 14f, 25f)
    line(color, 4.5f, 8.5f, 23.5f, 19.5f)
    line(color, 4.5f, 19.5f, 23.5f, 8.5f)
    path(color, 10.5f to 5f, 14f to 8f, 17.5f to 5f)
    path(color, 10.5f to 23f, 14f to 20f, 17.5f to 23f)
}

/** 엉따(시트 열선): 의자 + 김 세 줄 */
@Composable
fun SeatHeatIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 6f to 4f, 6f to 18f, 20f to 18f, 22f to 25f)
    line(color, 6f, 18f, 4f, 25f)
    val s = u()
    listOf(11f, 15f, 19f).forEach { x ->
        val p = Path().apply {
            moveTo(x * s, 14f * s)
            quadraticTo((x - 2f) * s, 11f * s, x * s, 8.5f * s)
            quadraticTo((x + 2f) * s, 6f * s, x * s, 3f * s)
        }
        drawPath(p, color, style = stroke())
    }
}

/** 와이퍼: 앞유리 호 + 팔 */
@Composable
fun WiperIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    drawArc(
        color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(2f * s, 6f * s), size = Size(24f * s, 24f * s), style = stroke(),
    )
    line(color, 14f, 24f, 7f, 10f)
    line(color, 2f, 24f, 26f, 24f)
}

/** 창문: 창틀 + 내려간 유리 */
@Composable
fun WindowIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 4f to 24f, 4f to 10f, 12f to 4f, 24f to 4f, 24f to 24f, close = true)
    line(color, 8f, 15f, 20f, 15f)
}

/** 손님 한마디 "조금 더워요": 해 */
@Composable
fun SunIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    drawCircle(color, radius = 5.5f * s, center = Offset(14f * s, 14f * s), style = stroke())
    line(color, 14f, 2.5f, 14f, 4.5f)
    line(color, 14f, 23.5f, 14f, 25.5f)
    line(color, 2.5f, 14f, 4.5f, 14f)
    line(color, 23.5f, 14f, 25.5f, 14f)
    line(color, 5.9f, 5.9f, 7.3f, 7.3f)
    line(color, 20.7f, 20.7f, 22.1f, 22.1f)
    line(color, 5.9f, 22.1f, 7.3f, 20.7f)
    line(color, 20.7f, 7.3f, 22.1f, 5.9f)
}

/** 손님 한마디 "잠깐 세워 주세요": 지도 핀 */
@Composable
fun PinIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    val p = Path().apply {
        moveTo(14f * s, 25f * s)
        cubicTo(6f * s, 17f * s, 5f * s, 13f * s, 5f * s, 11f * s)
        cubicTo(5f * s, 6f * s, 9f * s, 2.5f * s, 14f * s, 2.5f * s)
        cubicTo(19f * s, 2.5f * s, 23f * s, 6f * s, 23f * s, 11f * s)
        cubicTo(23f * s, 13f * s, 22f * s, 17f * s, 14f * s, 25f * s)
        close()
    }
    drawPath(p, color, style = stroke())
    drawCircle(color, radius = 2.5f * s, center = Offset(14f * s, 11f * s), style = stroke())
}

/** 손님 한마디 "음악 바꿔 주세요": 음표 */
@Composable
fun MusicIcon(color: Color, size: Dp) = Icon28(size) {
    val s = u()
    path(color, 11f to 20f, 11f to 5f, 23f to 3f, 23f to 18f)
    line(color, 11f, 9f, 23f, 7f)
    drawCircle(color, radius = 3f * s, center = Offset(8f * s, 20.5f * s), style = stroke())
    drawCircle(color, radius = 3f * s, center = Offset(20f * s, 18.5f * s), style = stroke())
}

/** 손님 한마디 "천천히 가 주세요": 아래로 누르는 화살표 두 개(속도 줄이기) */
@Composable
fun SlowIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 7f to 5f, 14f to 12f, 21f to 5f)
    path(color, 7f to 14f, 14f to 21f, 21f to 14f)
    line(color, 6f, 25f, 22f, 25f)
}

/** 손님 한마디 말풍선(빈 칸 표시) */
@Composable
fun ChatIcon(color: Color, size: Dp) = Icon28(size) {
    path(color, 4f to 5f, 24f to 5f, 24f to 19f, 13f to 19f, 7f to 24f, 7f to 19f, 4f to 19f, close = true)
}

/** 손님 한마디 아이콘. code는 :core RideMessage.code(차·폰이 같은 그림을 쓰도록 여기 한 곳에 둔다). */
@Composable
fun MessageIcon(code: String, color: Color, size: Dp) = when (code) {
    "SLOW" -> SlowIcon(color, size)
    "STOP" -> PinIcon(color, size)
    "COLD" -> SnowflakeIcon(color, size)
    "HOT" -> SunIcon(color, size)
    "MUSIC" -> MusicIcon(color, size)
    "GOOD" -> StarIcon(color, size, filled = true)
    else -> ChatIcon(color, size)
}
