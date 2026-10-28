package com.geppetto.faxi.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Receipt
import com.geppetto.faxi.data.ga
import com.geppetto.faxi.data.owesPhrase
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.BigWon
import com.geppetto.faxi.ui.components.CheckIcon
import com.geppetto.faxi.ui.components.ChevronIcon
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.receipt.ReceiptTimeFormat
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import kotlin.math.abs

/**
 * 두 사람 사이 빚 상세 (주차 상태).
 * 왼쪽: 남은 돈 → 손님이 보낸 금액을 적어 확인 요청 → 기사가 [받았어요]/[못 받았는데요].
 * 오른쪽: 이 빚을 만든 운행 영수증, 둘 사이 기록, 탕감(확인을 한 번 더 받는다).
 */
@Composable
fun DebtDetailScreen(
    store: FaxiStore,
    debtorId: String,
    creditorId: String,
    onBack: () -> Unit,
    onOpenReceipt: (String) -> Unit,
) {
    val c = Faxi.colors
    val t = Faxi.type
    val debtor = store.name(debtorId)
    val creditor = store.name(creditorId)
    val debt = store.debt(debtorId, creditorId)
    val pending = store.requests.firstOrNull { it.payerId == debtorId && it.payeeId == creditorId }
    var amount by remember(debt) { mutableIntStateOf(debt?.amount ?: 0) }
    var confirmForgive by remember(debt) { mutableStateOf(false) }

    FaxiPage(title = "$debtor · $creditor 장부", onBack = onBack) {
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("${owesPhrase(debtor, creditor)} 갚을 돈")
                    if (debt == null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckIcon(c.ok, FaxiDimens.iconLg)
                            Text("다 갚았어요.", style = t.title, color = c.ink)
                        }
                        return@FaxiCard
                    }
                    BigWon(debt.amount)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val days = store.daysOf(debt)
                        Text(if (days > 0) "${days}일째" else "오늘 생긴 빚", style = t.body, color = c.inkMuted)
                        if (debt.disputed) StatusTag("분쟁 중", Tone.Danger) { WarningIcon(it, FaxiDimens.space6) }
                    }
                    Divider()
                    if (pending != null) {
                        SectionTitle("확인 기다리는 중")
                        RequestItem(store, pending)
                        return@FaxiCard
                    }
                    SectionTitle("${debtor}: 보낸 금액 적기")
                    Text("앱은 돈을 옮기지 않아요. 따로 보낸 뒤 금액만 적어요.", style = t.caption, color = c.inkMuted)
                    Text("${won(amount)}원", style = t.amountLarge.copy(fontSize = t.headline.fontSize), color = c.ink)
                    // 줄이 생겼다 사라지면 아래 버튼이 손가락 밑에서 움직이므로 항상 한 줄을 둔다
                    Text(
                        if (amount < debt.amount) "남는 ${won(debt.amount - amount)}원은 미수금으로 남아요." else "전액이에요.",
                        style = t.body, color = c.inkMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                        listOf(-1_000, -100, 100, 1_000).forEach { step ->
                            FaxiSecondaryButton(
                                (if (step > 0) "+" else "−") + won(abs(step)),
                                onClick = { amount = (amount + step).coerceIn(0, debt.amount) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        FaxiSecondaryButton("전액", onClick = { amount = debt.amount }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.weight(1f))
                    FaxiPrimaryButton(
                        "${creditor}에게 보냈다고 알리기",
                        enabled = amount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { store.requestSettlement(debt, amount) },
                    )
                }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("둘 사이 운행")
                    val rides = store.receiptsBetween(debtorId, creditorId)
                    if (rides.isEmpty()) Text("운행 기록이 없어요.", style = t.body, color = c.inkMuted)
                    rides.take(4).forEach { r -> RideRow(store, r, onClick = { onOpenReceipt(r.id) }) }
                    Divider()
                    SectionTitle("기록")
                    EventList(store.events.filter { it.isBetween(debtorId, creditorId) }.take(4))
                    if (debt != null) {
                        Divider()
                        if (!confirmForgive) {
                            FaxiSecondaryButton(
                                "${creditor.ga()} 탕감해 주기",
                                onClick = { confirmForgive = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            ForgiveConfirm(
                                text = "${creditor.ga()} $debtor 빚 ${won(debt.amount)}원을 탕감할까요? 되돌릴 수 없어요.",
                                onConfirm = { store.forgive(debt) },
                                onCancel = { confirmForgive = false },
                            )
                        }
                    }
                }
            },
        )
    }
}

/** 운행 한 줄. 누르면 그 영수증으로. 기사 상세에서도 쓴다. */
@Composable
fun RideRow(store: FaxiStore, r: Receipt, onClick: () -> Unit) {
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
        Column(Modifier.weight(1f)) {
            Text(
                "${r.endedAt.format(ReceiptTimeFormat)} · ${r.from} → ${r.to}",
                style = t.bodyStrong, color = c.ink,
            )
            Text("기사 ${store.name(r.driverId)} · 손님 ${store.name(r.passengerId)}", style = t.caption, color = c.inkMuted)
        }
        Text(
            if (r.toLifetime) "평생 장부" else "${won(r.fareTotal)}원",
            style = t.amount, color = c.ink,
        )
        ChevronIcon(c.inkMuted, FaxiDimens.iconMd)
    }
}

@Composable
private fun ForgiveConfirm(text: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = Faxi.colors
    Column(
        Modifier
            .fillMaxWidth()
            .border(FaxiDimens.borderWidth, c.danger, RoundedCornerShape(FaxiDimens.radiusMd))
            .padding(FaxiDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningIcon(c.danger, FaxiDimens.iconMd)
            Text(text, style = Faxi.type.bodyStrong, color = c.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            FaxiSecondaryButton("취소", onClick = onCancel, modifier = Modifier.weight(1f))
            FaxiSecondaryButton("탕감하기", onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@FaxiPreviews
@Composable
private fun DebtDetailPreview() = PreviewFrame {
    DebtDetailScreen(FaxiStore.sample(), "mom", "dad", onBack = {}, onOpenReceipt = {})
}
