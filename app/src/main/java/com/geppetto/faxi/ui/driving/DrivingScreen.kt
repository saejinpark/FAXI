package com.geppetto.faxi.ui.driving

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.geppetto.faxi.ui.components.FaxiPage
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.ui.components.TaxiLampIcon
import com.geppetto.faxi.fare.Tariff
import com.geppetto.faxi.ui.components.ChatIcon
import com.geppetto.faxi.ui.components.DriverLicenseCard
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.MessageIcon
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.fare.FamilyExtra
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val StartFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 운행 중 (주행). "밤 택시" 스타일: 밤 바탕 위에 상자 없이, 종이 물건은 기사 자격증 하나.
 * 화면의 주인공은 옆자리 손님이다. 위에서부터:
 * 표시등 + "엄마 손님 모시는 중" + 지금 붙는 할증 꼬리표 / 경로 한 줄 / 자격증(종이) | 손님 한마디 / 옵션 게이지(CabinGauges).
 * 한마디를 한 번도 받지 않은 집은 한마디 칸 없이 자격증 | 경로.
 * - 모두 정지 표시다. 금액은 화면에 없다: 하차 때 도착 화면에서 한 번에 공개한다.
 *   움직이는 숫자·흐르는 그래프·경과 시간은 넣지 않는다(Pleos 주행 중 화면 규칙).
 * - 할증 꼬리표·한마디·옵션은 바뀔 때만 바뀐다(차의 표시등과 같은 성격). 한마디 칸은 높이를 미리 잡아 둬서 와도 아래가 밀리지 않는다.
 * - 운전자가 만질 것은 없다. 글자는 24sp 이상.
 * - onDemoPark는 차량 값이 없는 기기(폰·폴드) 시연에서만 넘긴다. 차에서는 기어 P가 하차다.
 */
@Composable
fun DrivingScreen(store: FaxiStore, onDemoPark: (() -> Unit)?) {
    val c = Faxi.colors
    val driver = store.member(store.driverId)
    val p = store.passengerId?.let(store::member)
    FaxiPage(
        title = "${p?.name ?: "손님"} 손님 모시는 중",
        leading = { TaxiLampIcon(c.accent, c.bg, 72.dp) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                surchargeTags(store).forEach { LampTag(it) }
                if (onDemoPark != null) FaxiSecondaryButton("시연: 주차(P)", onClick = onDemoPark)
            }
        },
    ) {
        // 한마디를 받은 적 없는 집(폰을 안 쓰는 집)은 한마디 칸 대신 경로를 자격증 옆에 크게
        if (store.messagesSeen) {
            full { m -> RouteLine(store, m) }
            row(
                { m -> DriverLicenseCard(driver, store.driverRating(driver.id), m, large = true) },
                { m -> MessageCell(store, m) },
            )
        } else {
            row(
                { m -> DriverLicenseCard(driver, store.driverRating(driver.id), m, large = true) },
                { m -> RouteLine(store, m.padding(vertical = FaxiDimens.space4)) },
            )
        }
        full { m -> CabinGauges(store.cabin, m) }
    }
}

/** 지금 붙는 할증 이름(금액 아님): 차량 조건(우천·심야) + 탑승 때 정한 가족 할증·할인 + 예비 기사 손님 */
@Composable
private fun surchargeTags(store: FaxiStore): List<String> {
    val tariff = Tariff()
    val p = store.passengerId?.let(store::member)
    return buildList {
        if (store.cabin.wiper) add("우천 할증")
        if (tariff.isNight(LocalDateTime.now())) add("심야 할증")
        FamilyExtra.entries.filter { it in store.rideExtras }.forEach { add(it.label) }
        if (p?.grade == Grade.Learner) add("평생 장부")
    }
}

/** 할증 꼬리표. 주행 중 읽는 글자라 24sp 이상(title) */
@Composable
private fun LampTag(text: String) {
    val c = Faxi.colors
    Text(
        text,
        style = Faxi.type.title,
        color = c.accentInk,
        modifier = Modifier
            .border(FaxiDimens.borderWidth, c.accentInk, RoundedCornerShape(50))
            .padding(horizontal = FaxiDimens.space4, vertical = FaxiDimens.space1),
    )
}

/** 경로 한 줄(상자 없이): 출발지 → 목적지(내비) · 출발 시각 · 경유지 · 이 기사 차 몇 번째 */
@Composable
private fun RouteLine(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member) ?: return
    val driver = store.member(store.driverId)
    val nth = store.ridesTogether(driver.id, p.id) + 1
    val dest = store.navi.destination ?: "목적지 미정"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
        Text("${store.rideFrom ?: "여기"} → $dest", style = t.headline, color = c.ink, maxLines = 2)
        Text(
            listOfNotNull(
                store.rideStartedAt?.let { "${it.format(StartFormat)} 출발" },
                store.rideWaypoints.takeIf { it > 0 }?.let { "경유지 ${it}곳" },
                "${driver.name} 택시 ${nth}번째 탑승",
                "요금은 내릴 때 한 번에",
            ).joinToString(" · "),
            style = t.title, color = c.inkMuted,
        )
    }
}

/**
 * 손님 한마디: 손님이 폰으로 보낸 마지막 한마디(아이콘 + 한 줄). 없으면 보내는 법을 흐리게.
 * 새 한마디가 와도 깜빡이거나 소리 내지 않는다(주행 중 움직임·말하기 금지). 상자 없이 밤 바탕 위에 큰 글자로.
 */
@Composable
private fun MessageCell(store: FaxiStore, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val p = store.passengerId?.let(store::member)
    val got = store.passengerMessage
    Column(
        modifier
            .heightIn(min = MessageHeight)
            .padding(vertical = FaxiDimens.space4)
            .semantics(mergeDescendants = true) {
                contentDescription = got?.let { "${p?.name ?: "손님"}의 한마디: ${it.message.text}" } ?: "손님 한마디 없음"
            },
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3, Alignment.CenterVertically),
    ) {
        Text(
            if (got != null) "${p?.name ?: "손님"}의 한마디 · ${got.at.format(StartFormat)}" else "${p?.name ?: "손님"}의 한마디",
            style = t.title, color = c.inkMuted,
        )
        if (got != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4), verticalAlignment = Alignment.CenterVertically) {
                MessageIcon(got.message.code, c.accentInk, 72.dp)
                Text(got.message.text, style = t.display, color = c.ink, maxLines = 2)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                ChatIcon(c.inkMuted, FaxiDimens.iconLg)
                Text("폰에서 보내면 여기 떠요.", style = t.title, color = c.inkMuted)
            }
        }
    }
}

/** 한마디 칸 높이: 한마디가 와도 아래 옵션 칸이 밀리지 않게 큰 글자 한 줄 높이로 미리 잡는다 */
private val MessageHeight = 200.dp

@FaxiPreviews
@Composable
private fun DrivingPreview() = PreviewFrame {
    val store = FaxiStore.sample().apply { startRide("mom") }
    DrivingScreen(store, onDemoPark = {})
}
