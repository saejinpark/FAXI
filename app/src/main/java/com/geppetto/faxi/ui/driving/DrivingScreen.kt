package com.geppetto.faxi.ui.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.CabinState
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.fare.Hvac
import com.geppetto.faxi.ui.components.Avatar
import com.geppetto.faxi.ui.components.ChatIcon
import com.geppetto.faxi.ui.components.DriverLicenseCard
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.MessageIcon
import com.geppetto.faxi.ui.components.MeterIcon
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SeatHeatIcon
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SnowflakeIcon
import com.geppetto.faxi.ui.components.SunIcon
import com.geppetto.faxi.ui.components.TwoColumnMin
import com.geppetto.faxi.ui.components.WindowIcon
import com.geppetto.faxi.ui.components.WiperIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.format.DateTimeFormatter

private val StartFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 운행 중 (주행). 화면을 2×2로 꽉 채운다: 자격증 | 손님 한마디 / 손님(+ 미터기 작동 중) | 요금 옵션.
 * - 모두 정지 표시다. 금액은 화면에 없다: 하차 때 도착 화면에서 한 번에 공개한다.
 *   움직이는 숫자·흐르는 그래프·경과 시간은 넣지 않는다(Pleos 주행 중 화면 규칙).
 * - 손님 한마디는 손님 폰에서 새로 올 때만 바뀐다(옵션 칸처럼 상태가 바뀌는 표시). 정해진 문장 + 아이콘이라 힐끗 보면 된다.
 * - 요금 옵션 칸은 에어컨을 켜고 끌 때처럼 상태가 바뀔 때만 바뀐다. 차의 표시등과 같은 성격.
 * - 운전자가 만질 것은 없다. 글자는 24sp 이상.
 * - onDemoPark는 폰·에뮬레이터 시연에서만 넘긴다. 구석의 작은 버튼으로, 차량에서는 기어 P로 대신한다.
 */
@Composable
fun DrivingScreen(store: FaxiStore, onDemoPark: (() -> Unit)?) {
    val driver = store.member(store.driverId)
    val license: @Composable (Modifier) -> Unit = { m ->
        DriverLicenseCard(driver, store.driverRating(driver.id), m, large = true)
    }
    val message: @Composable (Modifier) -> Unit = { m -> MessageCell(store, m) }
    val passenger: @Composable (Modifier) -> Unit = { m -> PassengerCell(store, m) }
    val options: @Composable (Modifier) -> Unit = { m -> OptionsCell(store.cabin, m) }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(FaxiDimens.space6)) {
            val gap = FaxiDimens.space6
            if (maxWidth >= TwoColumnMin) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        license(Modifier.weight(1f).fillMaxSize())
                        message(Modifier.weight(1f).fillMaxSize())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        passenger(Modifier.weight(1f).fillMaxSize())
                        options(Modifier.weight(1f).fillMaxSize())
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    license(Modifier)
                    message(Modifier)
                    passenger(Modifier)
                    options(Modifier)
                }
            }
        }
        if (onDemoPark != null) {
            FaxiSecondaryButton(
                "시연: 주차(P)",
                onClick = onDemoPark,
                modifier = Modifier.align(Alignment.BottomEnd).padding(FaxiDimens.space8),
            )
        }
    }
}

/**
 * 손님 한마디: 손님이 폰으로 보낸 마지막 한마디를 크게(아이콘 + 한 줄). 없으면 보내는 법만 작게.
 * 새 한마디가 와도 깜빡이거나 소리 내지 않는다(주행 중 움직임·말하기 금지). 테두리만 accent로 둘러 "온 것"을 알린다.
 */
@Composable
private fun MessageCell(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member)
    val got = store.passengerMessage
    FaxiCard(modifier) {
        SectionTitle("손님 한마디")
        val shape = RoundedCornerShape(FaxiDimens.radiusLg)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(c.surfaceRaised, shape)
                .then(if (got != null) Modifier.border(FaxiDimens.borderWidth * 2, c.accent, shape) else Modifier)
                .padding(FaxiDimens.space6)
                .semantics(mergeDescendants = true) {
                    contentDescription = got?.let { "${p?.name ?: "손님"}의 한마디: ${it.message.text}" } ?: "손님 한마디 없음"
                },
            verticalArrangement = Arrangement.Center,
        ) {
            if (got != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6), verticalAlignment = Alignment.CenterVertically) {
                    MessageIcon(got.message.code, c.accentInk, 88.dp)
                    Text(got.message.text, style = t.display, color = c.ink, maxLines = 1)
                }
                Spacer(Modifier.height(FaxiDimens.space4))
                Text("${got.at.format(StartFormat)} · ${p?.name ?: "손님"} 폰에서", style = t.body, color = c.inkMuted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4), verticalAlignment = Alignment.CenterVertically) {
                    ChatIcon(c.inkMuted, FaxiDimens.iconLg)
                    Text("아직 한마디가 없어요.", style = t.title, color = c.inkMuted)
                }
                Spacer(Modifier.height(FaxiDimens.space4))
                Text("${p?.name ?: "손님"} 폰의 FAXI에서 기사님께 한마디를 보낼 수 있어요.", style = t.body, color = c.inkMuted)
            }
        }
    }
}

/** 오늘의 손님: 캐릭터, 이름, 출발 시각, 이 기사 차 몇 번째인지 */
@Composable
private fun PassengerCell(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member) ?: return FaxiCard(modifier) {}
    val driver = store.member(store.driverId)
    val nth = store.ridesTogether(driver.id, p.id) + 1
    FaxiCard(modifier) {
        SectionTitle("오늘의 손님")
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p, 136.dp)
            Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
                Text("${p.name} · ${p.callsign}", style = t.headline, color = c.ink)
                store.rideStartedAt?.let { Text("${it.format(StartFormat)} 출발", style = t.body, color = c.inkMuted) }
                // 내비 목적지와 경유지(금액은 운행 중에 보이지 않는다). 바뀔 때만 바뀌는 정지 표시.
                store.navi.destination?.let { Text("$it 가는 중", style = t.body, color = c.ink, maxLines = 1) }
                if (store.rideWaypoints > 0) Text("경유지 ${store.rideWaypoints}곳", style = t.body, color = c.inkMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        // 미터기 상태는 따로 칸을 두지 않고 손님 칸에 한 줄로. 금액은 내릴 때 공개한다.
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
            MeterIcon(c.accentInk, FaxiDimens.iconLg)
            Text("미터기 작동 중 · 요금은 내릴 때", style = t.bodyStrong, color = c.ink)
        }
        Text("${driver.name} 택시 ${nth}번째 탑승", style = t.title, color = c.ink)
        if (p.grade == Grade.Learner) {
            Text("요금은 평생 장부에 쌓여요.", style = t.body, color = c.inkMuted)
        }
    }
}

/** 요금이 붙는 차량 옵션 4개의 켜짐/꺼짐. 색과 함께 "켜짐/꺼짐" 단어로 알린다. */
@Composable
private fun OptionsCell(cabin: CabinState, modifier: Modifier) {
    FaxiCard(modifier) {
        SectionTitle("요금 붙는 옵션")
        Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                OptionChip(
                    "냉난방", "분당 30원", cabin.hvac != Hvac.Off, Modifier.weight(1f),
                    onWord = if (cabin.hvac == Hvac.Heat) "난방" else "냉방",
                ) { col, s -> if (cabin.hvac == Hvac.Heat) SunIcon(col, s) else SnowflakeIcon(col, s) }
                OptionChip("엉따", "분당 20원", cabin.seatHeat, Modifier.weight(1f)) { col, s -> SeatHeatIcon(col, s) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                OptionChip("와이퍼", "요금 10%", cabin.wiper, Modifier.weight(1f)) { col, s -> WiperIcon(col, s) }
                OptionChip("창문", "분당 10원", cabin.windowOpen, Modifier.weight(1f), onWord = "열림", offWord = "닫힘") { col, s ->
                    WindowIcon(col, s)
                }
            }
        }
    }
}

@Composable
private fun OptionChip(
    name: String,
    rate: String,
    on: Boolean,
    modifier: Modifier,
    onWord: String = "켜짐",
    offWord: String = "꺼짐",
    icon: @Composable (Color, Dp) -> Unit,
) {
    val c = Faxi.colors
    val t = Faxi.type
    val ink = if (on) c.accentInk else c.inkMuted
    val shape = RoundedCornerShape(FaxiDimens.radiusMd)
    Row(
        modifier
            .background(if (on) c.surfaceRaised else c.surface, shape)
            .border(if (on) FaxiDimens.borderWidth * 2 else FaxiDimens.borderWidth, if (on) c.accent else c.line, shape)
            .padding(FaxiDimens.space4)
            .semantics(mergeDescendants = true) { contentDescription = "$name ${if (on) onWord else offWord}, $rate" },
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(ink, FaxiDimens.iconLg)
        Column(Modifier.weight(1f)) {
            Text(name, style = t.title, color = c.ink)
            Text(rate, style = t.body, color = c.inkMuted)
        }
        Text(if (on) onWord else offWord, style = t.bodyStrong, color = ink)
    }
}

@FaxiPreviews
@Composable
private fun DrivingPreview() = PreviewFrame {
    val store = FaxiStore.sample().apply { startRide("mom") }
    DrivingScreen(store, onDemoPark = {})
}
