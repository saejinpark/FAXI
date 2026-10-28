package com.geppetto.faxi.ui.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.geppetto.faxi.ui.components.FaxiPage
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
 * 운행 중 (주행). 다른 화면과 같은 틀(FaxiPage)에 카드를 내용 크기대로 쌓는다. 화면을 채우려고 카드를 늘리지 않는다.
 * 자격증 | 손님 / 손님 한마디 / 요금 붙는 옵션.
 * - 모두 정지 표시다. 금액은 화면에 없다: 하차 때 도착 화면에서 한 번에 공개한다.
 *   움직이는 숫자·흐르는 그래프·경과 시간은 넣지 않는다(Pleos 주행 중 화면 규칙).
 * - 손님 한마디와 옵션은 바뀔 때만 바뀐다(차의 표시등과 같은 성격). 한마디 칸은 높이를 미리 잡아 둬서 와도 아래가 밀리지 않는다.
 * - 운전자가 만질 것은 없다. 글자는 24sp 이상.
 * - onDemoPark는 차량 값이 없는 기기(폰·폴드) 시연에서만 넘긴다. 차에서는 기어 P가 하차다.
 */
@Composable
fun DrivingScreen(store: FaxiStore, onDemoPark: (() -> Unit)?) {
    val driver = store.member(store.driverId)
    FaxiPage(
        title = "운행 중",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                    MeterIcon(Faxi.colors.accentInk, FaxiDimens.iconLg)
                    Text("미터기가 돌고 있어요. 요금은 내릴 때 알려 드려요.", style = Faxi.type.bodyStrong, color = Faxi.colors.ink)
                }
                if (onDemoPark != null) FaxiSecondaryButton("시연: 주차(P)", onClick = onDemoPark)
            }
        },
    ) {
        row(
            { m -> DriverLicenseCard(driver, store.driverRating(driver.id), m, large = true) },
            { m -> PassengerCell(store, m) },
        )
        full { m -> MessageCell(store, m) }
        full { m -> OptionsCell(store.cabin, m) }
    }
}

/**
 * 손님 한마디: 손님이 폰으로 보낸 마지막 한마디(아이콘 + 한 줄). 없으면 보내는 법을 흐리게.
 * 새 한마디가 와도 깜빡이거나 소리 내지 않는다(주행 중 움직임·말하기 금지). 테두리만 accent로 바뀐다.
 */
@Composable
private fun MessageCell(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member)
    val got = store.passengerMessage
    val shape = RoundedCornerShape(FaxiDimens.radiusLg)
    Row(
        modifier
            .heightIn(min = MessageHeight)
            .background(c.surface, shape)
            .border(if (got != null) FaxiDimens.borderWidth * 2 else FaxiDimens.borderWidth, if (got != null) c.accent else c.line, shape)
            .padding(horizontal = FaxiDimens.space6, vertical = FaxiDimens.space4)
            .semantics(mergeDescendants = true) {
                contentDescription = got?.let { "${p?.name ?: "손님"}의 한마디: ${it.message.text}" } ?: "손님 한마디 없음"
            },
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (got != null) {
            MessageIcon(got.message.code, c.accentInk, 72.dp)
            Text(got.message.text, style = t.display, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${got.at.format(StartFormat)} ${p?.name ?: "손님"}", style = t.body, color = c.inkMuted)
        } else {
            ChatIcon(c.inkMuted, FaxiDimens.iconLg)
            Text("${p?.name ?: "손님"} 폰에서 보낸 한마디가 여기 떠요.", style = t.title, color = c.inkMuted)
        }
    }
}

/** 한마디 칸 높이: 한마디가 와도 아래 옵션 칸이 밀리지 않게 큰 글자 한 줄 높이로 미리 잡는다 */
private val MessageHeight = 120.dp

/** 손님: 캐릭터, 이름, 출발 시각, 목적지, 이 기사 차 몇 번째인지 */
@Composable
private fun PassengerCell(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member) ?: return FaxiCard(modifier) {}
    val driver = store.member(store.driverId)
    val nth = store.ridesTogether(driver.id, p.id) + 1
    FaxiCard(modifier) {
        SectionTitle("손님")
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p, 136.dp)
            Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
                Text("${p.name} · ${p.callsign}", style = t.headline, color = c.ink)
                store.rideStartedAt?.let { Text("${it.format(StartFormat)} 출발", style = t.title, color = c.inkMuted) }
                // 내비 목적지와 경유지(금액은 운행 중에 보이지 않는다). 바뀔 때만 바뀌는 정지 표시.
                store.navi.destination?.let { Text("$it 가는 중", style = t.title, color = c.ink, maxLines = 1) }
                if (store.rideWaypoints > 0) Text("경유지 ${store.rideWaypoints}곳", style = t.title, color = c.inkMuted)
            }
        }
        Text("${driver.name} 택시 ${nth}번째 탑승이에요.", style = t.title, color = c.ink)
        if (p.grade == Grade.Learner) {
            Text("요금은 평생 장부에 쌓여요.", style = t.title, color = c.inkMuted)
        }
    }
}

/** 요금이 붙는 차량 옵션 4개의 켜짐/꺼짐. 색과 함께 "켜짐/꺼짐" 단어로 알린다. */
@Composable
private fun OptionsCell(cabin: CabinState, modifier: Modifier) {
    FaxiCard(modifier) {
        SectionTitle("요금이 붙는 옵션")
        // 넓으면 한 줄에 넷, 좁으면 둘씩
        BoxWithConstraints {
            val perRow = if (maxWidth >= TwoColumnMin) 4 else 2
            val chips: List<@Composable (Modifier) -> Unit> = listOf(
                { m ->
                    OptionChip(
                        "냉난방", "분당 30원", cabin.hvac != Hvac.Off, m,
                        onWord = if (cabin.hvac == Hvac.Heat) "난방" else "냉방",
                    ) { col, s -> if (cabin.hvac == Hvac.Heat) SunIcon(col, s) else SnowflakeIcon(col, s) }
                },
                { m -> OptionChip("엉따", "분당 20원", cabin.seatHeat, m) { col, s -> SeatHeatIcon(col, s) } },
                { m -> OptionChip("와이퍼", "요금 10%", cabin.wiper, m) { col, s -> WiperIcon(col, s) } },
                { m ->
                    OptionChip("창문", "분당 10원", cabin.windowOpen, m, onWord = "열림", offWord = "닫힘") { col, s -> WindowIcon(col, s) }
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                chips.chunked(perRow).forEach { line ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                        line.forEach { chip -> chip(Modifier.weight(1f).fillMaxHeight()) }
                    }
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
            Text(rate, style = t.title, color = c.inkMuted)
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
