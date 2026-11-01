package com.geppetto.faxi.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.CabinState
import com.geppetto.faxi.data.CabinWindow
import com.geppetto.faxi.data.WiperLevel
import com.geppetto.faxi.fare.Hvac
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SeatHeatIcon
import com.geppetto.faxi.ui.components.SnowflakeIcon
import com.geppetto.faxi.ui.components.SunIcon
import com.geppetto.faxi.ui.components.TwoColumnMin
import com.geppetto.faxi.ui.components.WindowIcon
import com.geppetto.faxi.ui.components.WiperIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/**
 * 운행 중 화면 아래: 요금이 붙는 차량 옵션 넷을 정도까지 보여 주는 게이지.
 * - 냉난방: 설정 온도 막대(16~30°) + 실내 온도 눈금. 엉따: 3단 계단. 와이퍼: 5단 막대. 창문: 위에서 본 차와 네 창.
 * - 요금은 켜짐/꺼짐으로만 매긴다(정도는 화면용). 칸마다 이름·요금 아래에 지금 상태를 단어로 쓴다(색만으로 알리지 않는다).
 * - 모두 정지 그림이다. 차량 값이 바뀔 때만 다시 그리고, 움직이거나 깜빡이지 않는다(주행 중 애니메이션 금지).
 * - 칸 경계: 칸마다 윗줄(켜지면 산호, 두껍게)과 칸 사이 세로선으로 나눈다.
 */
@Composable
fun CabinGauges(cabin: CabinState, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val perRow = if (maxWidth >= TwoColumnMin) 4 else 2
        val cells: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> HvacGauge(cabin, m) },
            { m -> SeatGauge(cabin, m) },
            { m -> WiperGauge(cabin, m) },
            { m -> WindowGauge(cabin, m) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space8)) {
            cells.chunked(perRow).forEach { line ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    line.forEachIndexed { i, cell ->
                        if (i > 0) Divider()
                        cell(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** 칸 사이 세로선: 칸 경계를 분명하게(간격만으로는 이웃 칸 글자와 섞여 보였다) */
@Composable
private fun Divider() {
    val c = Faxi.colors
    Box(
        Modifier
            .width(FaxiDimens.space8 * 2)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(FaxiDimens.borderWidth).fillMaxHeight().background(c.line))
    }
}

/** 칸 하나: 윗줄 / 아이콘·이름·요금 + 큰 상태 단어 / 그림(칸 폭 가득) / 덧붙임 한 줄 */
@Composable
private fun GaugeCell(
    name: String,
    rate: String,
    on: Boolean,
    value: String,
    detail: String,
    modifier: Modifier,
    icon: @Composable (Color, Dp) -> Unit,
    picture: DrawScope.(Palette) -> Unit,
) {
    val c = Faxi.colors
    val t = Faxi.type
    val ink = if (on) c.accentInk else c.inkMuted
    val palette = Palette(on = c.accent, off = c.line, ink = c.ink, muted = c.inkMuted, lit = on)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$name $value, $detail, $rate" },
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
    ) {
        Box(Modifier.fillMaxWidth().height(if (on) 6.dp else 2.dp).background(if (on) c.accent else c.line, RoundedCornerShape(3.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
            icon(ink, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = t.title, color = c.ink)
                Text(rate, style = t.body, color = c.inkMuted)
            }
            Text(value, style = t.headline, color = ink, maxLines = 1)
        }
        Canvas(Modifier.fillMaxWidth().height(PictureHeight)) { picture(palette) }
        Text(detail, style = t.body, color = c.inkMuted, maxLines = 1)
    }
}

private val PictureHeight = 240.dp

/** 그림 색: 켜진 칸만 산호로 채운다. 꺼진 칸은 선 색 */
private class Palette(val on: Color, val off: Color, val ink: Color, val muted: Color, val lit: Boolean) {
    val fill get() = if (lit) on else muted
}

// ---- 냉난방 ----

private const val TempMin = 16f
private const val TempMax = 30f

@Composable
private fun HvacGauge(cabin: CabinState, modifier: Modifier) {
    val on = cabin.hvac != Hvac.Off
    val heat = cabin.hvac == Hvac.Heat
    val target = cabin.targetTempC
    val inside = cabin.cabinTempC
    GaugeCell(
        name = "냉난방",
        rate = "분당 30원",
        on = on,
        value = when (cabin.hvac) {
            Hvac.Off -> "꺼짐"
            Hvac.Cool -> "냉방"
            Hvac.Heat -> "난방"
        },
        detail = listOfNotNull(target?.let { "설정 ${it.deg()}" }, inside?.let { "실내 ${it.deg()}" })
            .joinToString(" · ").ifEmpty { "온도 정보 없음" },
        modifier = modifier,
        icon = { col, s -> if (heat) SunIcon(col, s) else SnowflakeIcon(col, s) },
    ) { p ->
        // 16~30° 막대: 설정 온도까지 채우고, 실내 온도는 굵은 세로선. 아래 눈금은 2°마다
        val h = 48.dp.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        // 2°마다 눈금(막대 아래)
        val tickTop = top + h + 16.dp.toPx()
        for (deg in TempMin.toInt()..TempMax.toInt() step 2) {
            val x = size.width * fraction(deg.toFloat())
            val long = deg % 4 == 0
            drawLine(p.muted, Offset(x, tickTop), Offset(x, tickTop + (if (long) 20 else 10).dp.toPx()), 3.dp.toPx())
        }
        drawRoundRect(p.off, Offset(0f, top), Size(size.width, h), r)
        if (target != null) {
            val w = size.width * fraction(target)
            drawRoundRect(p.fill, Offset(0f, top), Size(w.coerceAtLeast(h), h), r)
        }
        if (inside != null) {
            val x = size.width * fraction(inside)
            drawLine(p.ink, Offset(x, top - 20.dp.toPx()), Offset(x, top + h + 20.dp.toPx()), 5.dp.toPx())
        }
    }
}

private fun fraction(c: Float) = ((c - TempMin) / (TempMax - TempMin)).coerceIn(0f, 1f)

private fun Float.deg(): String = if (this % 1f == 0f) "${toInt()}°" else "${"%.1f".format(this)}°"

// ---- 엉따 ----

private const val SeatSteps = 3

@Composable
private fun SeatGauge(cabin: CabinState, modifier: Modifier) {
    val level = cabin.seatLevel
    val on = level > 0
    GaugeCell(
        name = "엉따",
        rate = "분당 20원",
        on = on,
        value = when {
            level > 0 -> "${level.coerceAtMost(SeatSteps)}단"
            level < 0 -> "통풍"
            else -> "꺼짐"
        },
        detail = if (level < 0) "조수석 통풍은 요금 없음" else "조수석 열선, 최대 ${SeatSteps}단",
        modifier = modifier,
        icon = { col, s -> SeatHeatIcon(col, s) },
    ) { p ->
        // 오른쪽으로 갈수록 높아지는 계단 3칸. 켜진 단까지 채운다
        steps(SeatSteps, level.coerceIn(0, SeatSteps), p, rising = true)
    }
}

// ---- 와이퍼 ----

private const val WiperSteps = 5

@Composable
private fun WiperGauge(cabin: CabinState, modifier: Modifier) {
    val lv = cabin.wiperLevel
    val on = cabin.wiper || (lv != null && lv != WiperLevel.Off)
    val (value, step) = when (lv) {
        is WiperLevel.Intermittent -> "간헐" to lv.step
        is WiperLevel.Continuous -> "연속" to lv.step
        WiperLevel.Auto -> "자동" to 0
        WiperLevel.Mist -> "한 번" to 1
        WiperLevel.Off -> "꺼짐" to 0
        null -> (if (cabin.wiper) "켜짐" else "꺼짐") to (if (cabin.wiper) WiperSteps else 0)
    }
    GaugeCell(
        name = "와이퍼",
        rate = "우천 할증 10%",
        on = on,
        value = value,
        detail = when (lv) {
            is WiperLevel.Intermittent, is WiperLevel.Continuous -> "$value ${step}단, 최대 ${WiperSteps}단"
            WiperLevel.Auto -> "비 오는 만큼 저절로"
            else -> if (on) "앞유리 와이퍼" else "비 오면 할증"
        },
        modifier = modifier,
        icon = { col, s -> WiperIcon(col, s) },
    ) { p -> steps(WiperSteps, step.coerceIn(0, WiperSteps), p, rising = false) }
}

/** 막대 n칸. lit칸까지 채운다. rising이면 오른쪽으로 갈수록 높게(열선 단계) */
private fun DrawScope.steps(n: Int, lit: Int, p: Palette, rising: Boolean) {
    val gap = 10.dp.toPx()
    val w = (size.width - gap * (n - 1)) / n
    val r = CornerRadius(6.dp.toPx())
    for (i in 0 until n) {
        val h = if (rising) size.height * (0.45f + 0.55f * i / (n - 1)) else size.height * 0.5f
        val x = i * (w + gap)
        val y = (if (rising) size.height else (size.height + h) / 2) - h
        drawRoundRect(if (i < lit) p.fill else p.off, Offset(x, y), Size(w, h), r)
    }
}

// ---- 창문 ----

@Composable
private fun WindowGauge(cabin: CabinState, modifier: Modifier) {
    val open = CabinWindow.entries.filter { (cabin.windows[it] ?: 0) > 0 }
    val on = cabin.windowOpen || open.isNotEmpty()
    GaugeCell(
        name = "창문",
        rate = "분당 10원",
        on = on,
        value = if (on) "${open.size.coerceAtLeast(1)}곳" else "닫힘",
        detail = when {
            open.isEmpty() -> if (on) "열림" else "네 창 모두 닫힘"
            open.size == 1 -> "${open[0].label} ${cabin.windows[open[0]]}%"
            else -> open.joinToString(" · ") { it.label }
        },
        modifier = modifier,
        icon = { col, s -> WindowIcon(col, s) },
    ) { p -> carFromAbove(cabin.windows, p) }
}

/**
 * 위에서 본 차(앞이 오른쪽이라 운전석 쪽이 위): 몸통 윤곽 + 앞유리 + 양옆 창 네 개. 창은 열린 만큼 채운다.
 */
private fun DrawScope.carFromAbove(windows: Map<CabinWindow, Int>, p: Palette) {
    val bodyW = minOf(size.width, size.height * 2.4f)
    val bodyH = size.height
    val left = (size.width - bodyW) / 2
    val stroke = 3.dp.toPx()
    // 몸통
    drawRoundRect(p.muted, Offset(left, 0f), Size(bodyW, bodyH), CornerRadius(bodyH * 0.35f), style = Stroke(stroke))
    // 앞유리(오른쪽)
    val glass = Path().apply {
        moveTo(left + bodyW * 0.78f, bodyH * 0.22f)
        lineTo(left + bodyW * 0.70f, bodyH * 0.30f)
        lineTo(left + bodyW * 0.70f, bodyH * 0.70f)
        lineTo(left + bodyW * 0.78f, bodyH * 0.78f)
        close()
    }
    drawPath(glass, p.muted, style = Stroke(stroke))
    // 창 네 개: 위가 운전석(왼쪽) 줄, 아래가 조수석(오른쪽) 줄. 앞줄이 앞유리 쪽
    val slotW = bodyW * 0.22f
    val slotH = bodyH * 0.14f
    val r = CornerRadius(slotH / 2)
    fun slot(w: CabinWindow, x: Float, y: Float) {
        drawRoundRect(p.off, Offset(x, y), Size(slotW, slotH), r)
        val pct = (windows[w] ?: 0).coerceIn(0, 100)
        if (pct > 0) drawRoundRect(p.on, Offset(x, y), Size((slotW * pct / 100f).coerceAtLeast(slotH), slotH), r)
    }
    val frontX = left + bodyW * 0.44f
    val rearX = left + bodyW * 0.18f
    val topY = bodyH * 0.06f
    val bottomY = bodyH * 0.94f - slotH
    slot(CabinWindow.FrontLeft, frontX, topY)
    slot(CabinWindow.RearLeft, rearX, topY)
    slot(CabinWindow.FrontRight, frontX, bottomY)
    slot(CabinWindow.RearRight, rearX, bottomY)
}

@FaxiPreviews
@Composable
private fun CabinGaugesPreview() = PreviewFrame {
    CabinGauges(
        CabinState(
            Hvac.Cool, seatHeat = true, wiper = true, windowOpen = true,
            targetTempC = 21f, cabinTempC = 26.5f, seatLevel = 2,
            wiperLevel = WiperLevel.Intermittent(3),
            windows = mapOf(CabinWindow.FrontRight to 40, CabinWindow.RearLeft to 100),
        ),
        Modifier.fillMaxWidth(),
    )
}
