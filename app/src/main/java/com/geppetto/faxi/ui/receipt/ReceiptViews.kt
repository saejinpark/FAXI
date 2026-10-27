package com.geppetto.faxi.ui.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Receipt
import com.geppetto.faxi.data.Review
import com.geppetto.faxi.data.ReviewTags
import com.geppetto.faxi.data.ga
import com.geppetto.faxi.data.owesPhrase
import com.geppetto.faxi.ui.components.BigWon
import com.geppetto.faxi.ui.components.CheckIcon
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.MoneyRow
import com.geppetto.faxi.ui.components.QrImage
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.StarIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.format.DateTimeFormatter

val ReceiptTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d HH:mm")

/**
 * 영수증 한 장. 도착 화면과 장부의 지난 영수증이 같이 쓴다.
 * 맨 위에 이번 하차로 갚을 돈(청구액)을 크게, 그 아래 항목별 요금과 이월·이자·상계.
 */
@Composable
fun ReceiptCard(
    store: FaxiStore,
    r: Receipt,
    modifier: Modifier = Modifier,
    /** 장부에서 연 지난 영수증이면 true. 지금 남은 빚과 헷갈리지 않게 "그때 청구한 돈"으로 적는다. */
    past: Boolean = false,
) {
    val c = Faxi.colors
    val t = Faxi.type
    val driver = store.member(r.driverId)
    val passenger = store.member(r.passengerId)
    FaxiCard(modifier) {
        // 영수증 머리: 왼쪽 누가·어디서, 오른쪽 위 QR(손님 폰으로 찍어 저장)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
                Text("FAXI 영수증", style = t.title, color = c.ink)
                Text(
                    "${driver.name} 택시(${driver.callsign}) · 손님 ${passenger.name}",
                    style = t.bodyStrong,
                    color = c.ink,
                )
                Text("${r.from} → ${r.to} · ${r.endedAt.format(ReceiptTimeFormat)}", style = t.caption, color = c.inkMuted)
            }
            // 진짜 QR: 영수증 + 지금 손님의 정산 현황(core의 ReceiptTicket, 69칸 이하로 줄여 싣는다)
            val link = remember(r, store.debts.toList()) { store.ticketFor(r).toLink() }
            ReceiptQrBox(link)
        }
        if (r.toLifetime) {
            Text("${passenger.name}의 평생 장부에 쌓였어요", style = t.bodyStrong, color = c.inkMuted)
            BigWon(r.fareTotal)
        } else {
            Text(
                if (past) "그때 ${driver.name.ga()} ${passenger.name}에게 청구한 돈" else "${owesPhrase(passenger.name, driver.name)} 갚을 돈",
                style = t.bodyStrong, color = c.inkMuted,
            )
            BigWon(r.billed)
        }
        Divider()
        r.lines.forEach { line ->
            MoneyRow(
                line.label, line.amount, detail = line.detail,
                amountColor = if (line.amount < 0) c.ok else c.ink,
            )
        }
        Divider()
        MoneyRow("이번 요금", r.fareTotal, strong = true)
        if (r.carriedOver > 0) MoneyRow("지난 미수금", r.carriedOver)
        if (r.lateInterest > 0) MoneyRow("연체 이자", r.lateInterest, detail = "${r.lateDays}일 · 하루 0.1%")
        if (r.offset > 0) {
            MoneyRow(
                "상계", -r.offset,
                detail = "${owesPhrase(driver.name, passenger.name)} 진 빚과 퉁침",
                amountColor = c.ok,
            )
        }
    }
}

/** 이미 남긴 후기 한 줄 요약 */
@Composable
fun ReviewSummary(review: Review) {
    val c = Faxi.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckIcon(c.ok, FaxiDimens.iconMd)
        Text("후기를 남겼어요 · 별 ${review.stars}개", style = Faxi.type.bodyStrong, color = c.ink)
    }
    if (review.tags.isNotEmpty()) Text(review.tags.joinToString(" · "), style = Faxi.type.body, color = c.inkMuted)
}

/** 운행 데이터로 묻는 질문 + 별점 + 태그. 타이핑 없이 누르기만 한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewForm(r: Receipt, onSubmit: (Review) -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    var stars by remember(r.id) { mutableIntStateOf(0) }
    var tags by remember(r.id) { mutableStateOf(emptySet<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
        Text(r.reviewQuestion, style = t.title, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
            (1..5).forEach { n ->
                Box(
                    Modifier
                        .size(FaxiDimens.targetMin)
                        .clickable(role = Role.Button) { stars = n }
                        .semantics {
                            contentDescription = "별 ${n}개"
                            selected = n == stars
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    StarIcon(if (n <= stars) c.accentInk else c.lineStrong, FaxiDimens.iconLg, filled = n <= stars)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap),
            verticalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap),
        ) {
            ReviewTags.forEach { tag ->
                val on = tag in tags
                SelectTile(selected = on, onClick = { tags = if (on) tags - tag else tags + tag }) {
                    Text(tag, style = t.label, color = c.ink)
                }
            }
        }
        FaxiPrimaryButton(
            "후기 남기기",
            enabled = stars > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSubmit(Review(stars, tags)) },
        )
    }
}

/**
 * 오늘의 승차감: 운행 동안의 흔들림을 한 줄로(심전도처럼). 급정거 자리는 위험색 점 + 글자로 표시한다.
 * 하차 뒤(주차 상태)에만 보이는 정지 그림이다. 운행 중에 흐르게 그리지 않는다.
 */
@Composable
fun RideComfortChart(r: Receipt, modifier: Modifier = Modifier) {
    if (r.shake.size < 2) return
    val c = Faxi.colors
    val t = Faxi.type
    val brakes = r.hardBrakes.size
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "오늘의 승차감 그래프. 급정거 ${brakes}번"
        },
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("오늘의 승차감")
            Text(
                if (brakes > 0) "급정거 ${brakes}번" else "급정거 없음",
                style = t.bodyStrong,
                color = if (brakes > 0) c.danger else c.ok,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(c.surfaceRaised, RoundedCornerShape(FaxiDimens.radiusSm)),
        ) {
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val step = w / (r.shake.size - 1)
            fun y(v: Int) = pad + h - h * v.coerceIn(0, 100) / 100f
            // 기준선(흔들림 없음)
            drawLine(c.line, Offset(pad, y(0)), Offset(pad + w, y(0)), 2.dp.toPx())
            val path = androidx.compose.ui.graphics.Path().apply {
                r.shake.forEachIndexed { i, v ->
                    if (i == 0) moveTo(pad, y(v)) else lineTo(pad + i * step, y(v))
                }
            }
            drawPath(path, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            r.hardBrakes.forEach { i ->
                drawCircle(c.danger, radius = 7.dp.toPx(), center = Offset(pad + i * step, y(r.shake[i])))
            }
        }
        Text("선이 높을수록 많이 흔들렸어요.", style = t.caption, color = c.inkMuted)
    }
}

/**
 * 영수증 QR. 카드 안 작은 QR은 칸이 너무 작아(차 화면은 1dp = 1px 안팎) 폰 카메라가 못 읽는다.
 * 눌러서 화면 가득 크게 띄운 상태로 찍는다. 주차 상태 화면에서만 쓰인다.
 */
@Composable
private fun ReceiptQrBox(link: String) {
    val c = Faxi.colors
    var big by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(FaxiDimens.radiusSm))
            .clickable(role = Role.Button, onClickLabel = "영수증 QR 크게 보기") { big = true }
            .padding(FaxiDimens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QrImage(link, 132.dp, "영수증 QR 코드")
        Text("눌러서 크게 보기", style = Faxi.type.caption, color = c.inkMuted)
    }
    if (big) {
        Dialog(onDismissRequest = { big = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // 가로 화면: 왼쪽 QR(화면 높이에 맞춰 최대한 크게), 오른쪽 안내 + 닫기. 닫기가 화면 밖으로 밀리지 않게.
            BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).padding(FaxiDimens.space8)) {
                val qr = minOf(maxHeight, maxWidth * 0.6f)
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space8, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.clickable(role = Role.Button, onClickLabel = "닫기") { big = false }) {
                        QrImage(link, qr, "영수증 QR 코드")
                    }
                    Column(
                        Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
                    ) {
                        Text("손님 폰의 FAXI 앱에서\n\"영수증 QR 찍기\"로 찍어 주세요.", style = Faxi.type.title, color = c.ink)
                        Text("영수증과 지금 정산 현황이 폰에 저장돼요.", style = Faxi.type.body, color = c.inkMuted)
                        FaxiSecondaryButton("닫기", onClick = { big = false })
                    }
                }
            }
        }
    }
}
