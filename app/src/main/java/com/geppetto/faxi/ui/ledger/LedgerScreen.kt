package com.geppetto.faxi.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.geppetto.faxi.data.Debt
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.LedgerEvent
import com.geppetto.faxi.data.SettlementRequest
import com.geppetto.faxi.data.owesPhrase
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.CheckIcon
import com.geppetto.faxi.ui.components.ChevronIcon
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.receipt.ReceiptDetailScreen
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DateFormat = DateTimeFormatter.ofPattern("M.d")

/**
 * 가족 장부 탭. 안에서 세 단계로 들어간다(내비게이션 라이브러리 없이 상태 두 개로):
 * 장부 → 두 사람 사이 빚 상세(정산·탕감) → 그 빚을 만든 운행의 영수증.
 */
@Composable
fun LedgerScreen(store: FaxiStore, today: LocalDate = LocalDate.now()) {
    // "debtorId>creditorId"
    var pair by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptId by rememberSaveable { mutableStateOf<String?>(null) }

    val receipt = receiptId?.let(store::receipt)
    if (receipt != null) {
        ReceiptDetailScreen(store, receipt, onBack = { receiptId = null })
        return
    }
    val p = pair?.split('>')
    if (p != null) {
        DebtDetailScreen(store, p[0], p[1], onBack = { pair = null }, onOpenReceipt = { receiptId = it })
        return
    }
    LedgerHome(store, today, onOpenDebt = { pair = "${it.debtorId}>${it.creditorId}" })
}

@Composable
private fun LedgerHome(store: FaxiStore, today: LocalDate, onOpenDebt: (Debt) -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type

    FaxiPage(title = "가족 장부") {
        // 손님이 "보냈어요"를 누르면 기사가 확인할 때까지 맨 위에 둔다
        if (store.requests.isNotEmpty()) {
            full { m ->
                FaxiCard(m) {
                    SectionTitle("확인 기다리는 정산 ${store.requests.size}건")
                    store.requests.forEachIndexed { i, req ->
                        if (i > 0) Divider()
                        RequestItem(store, req)
                    }
                }
            }
        }
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("갚을 돈")
                    if (store.debts.isEmpty()) {
                        Text("밀린 돈이 없어요. 다시 태워 줄 핑계가 없네요.", style = t.body, color = c.ink)
                    }
                    store.debts.forEach { debt -> DebtRow(store, debt, onClick = { onOpenDebt(debt) }) }
                    Text("눌러서 운행 영수증과 정산을 볼 수 있어요.", style = t.caption, color = c.inkMuted)
                }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("${today.monthValue}월 가족 정산서")
                    val best = store.members.filter { it.grade == Grade.Full }
                        .mapNotNull { m -> store.driverRating(m.id)?.let { m to it } }
                        .maxByOrNull { it.second }
                    val mostDriven = store.receipts.filter { it.endedAt.monthValue == today.monthValue && it.endedAt.year == today.year }
                        .groupBy { it.driverId }
                        .mapValues { (_, list) -> list.sumOf { it.km } }
                        .maxByOrNull { it.value }
                    if (best != null) SummaryLine("이달의 기사", "${best.first.name} · 별 %.1f".format(best.second))
                    if (mostDriven != null) {
                        SummaryLine("가장 많이 태워 준 사람", "${store.name(mostDriven.key)} · ${won(mostDriven.value)}km")
                    }
                    SummaryLine("다시 확인할 정산", "${store.debts.count { it.disputed }}건")
                    SummaryLine("이달 운행", "${store.receipts.count { it.endedAt.monthValue == today.monthValue }}번")
                }
            },
        )
        full { m ->
            FaxiCard(m) {
                SectionTitle("장부 기록")
                EventList(store.events.take(6))
            }
        }
    }
}

/** 빚 한 줄. 누르면 상세로 들어간다(오른쪽 꺾쇠). 기사 상세에서도 쓴다. */
@Composable
fun DebtRow(store: FaxiStore, debt: Debt, onClick: () -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FaxiDimens.targetMin)
            .background(c.surfaceRaised, RoundedCornerShape(FaxiDimens.radiusMd))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = FaxiDimens.space4, vertical = FaxiDimens.space3),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(owesPhrase(store.name(debt.debtorId), store.name(debt.creditorId)), style = t.bodyStrong, color = c.ink)
                if (debt.disputed) StatusTag("다시 확인", Tone.Neutral)
            }
            Text(
                "${won(debt.amount)}원 · " + store.daysOf(debt).let { if (it > 0) "${it}일째" else "오늘" },
                style = t.amount, color = c.ink,
            )
        }
        ChevronIcon(c.inkMuted, FaxiDimens.iconMd)
    }
}

/** 기사가 확인하는 정산 요청 한 건 */
@Composable
fun RequestItem(store: FaxiStore, req: SettlementRequest) {
    val c = Faxi.colors
    val t = Faxi.type
    Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
        Text(
            "${store.name(req.payerId)}: \"${store.name(req.payeeId)}에게 ${won(req.amount)}원 보냈어요\"",
            style = t.bodyStrong, color = c.ink,
        )
        Text("${store.name(req.payeeId)}님, 받으셨나요?", style = t.body, color = c.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            FaxiSecondaryButton(
                "받았어요",
                onClick = { store.confirmSettlement(req, received = true) },
                modifier = Modifier.weight(1f),
                leading = { CheckIcon(c.ok, FaxiDimens.iconMd) },
            )
            FaxiSecondaryButton(
                "못 받았는데요",
                onClick = { store.confirmSettlement(req, received = false) },
                modifier = Modifier.weight(1f),
                leading = { WarningIcon(c.danger, FaxiDimens.iconMd) },
            )
        }
    }
}

@Composable
fun EventList(events: List<LedgerEvent>) {
    val c = Faxi.colors
    val t = Faxi.type
    if (events.isEmpty()) Text("아직 기록이 없어요.", style = t.body, color = c.inkMuted)
    events.forEach { e ->
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
            Text(e.date.format(DateFormat), style = t.body, color = c.inkMuted)
            Text(e.text, style = t.body, color = c.ink)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Faxi.type.body, color = Faxi.colors.inkMuted)
        Text(value, style = Faxi.type.bodyStrong, color = Faxi.colors.ink)
    }
}

@FaxiPreviews
@Composable
private fun LedgerPreview() = PreviewFrame {
    val store = FaxiStore.sample().apply { requestSettlement(debts.first(), 3_000) }
    LedgerScreen(store, today = LocalDate.of(2026, 10, 27))
}
