package com.geppetto.faxi.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.BigWon
import com.geppetto.faxi.ui.components.DriverLicenseCard
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.LedgerIcon
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.ledger.DebtDetailScreen
import com.geppetto.faxi.ui.receipt.ReceiptDetailScreen
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDate

/**
 * 기사 탭. 안에서 단계로 들어간다(내비게이션 라이브러리 없이 상태 셋으로, 장부 탭과 같은 방식):
 * 명단 → 기사 상세 → 빚 상세 → 영수증. 뒤로는 한 단계씩.
 */
@Composable
fun RosterScreen(store: FaxiStore, today: LocalDate = LocalDate.now()) {
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val id = memberId
    if (id != null) {
        MemberDetailScreen(
            store, id,
            onBack = { memberId = null },
            onOpenDebt = { pair = "${it.debtorId}>${it.creditorId}" },
            onOpenReceipt = { receiptId = it },
            today = today,
        )
        return
    }
    RosterHome(store, today, onOpenMember = { memberId = it })
}

/** 명단: 가족 자격증 카드(누르면 기사 상세) + 예비 기사의 평생 장부 */
@Composable
private fun RosterHome(store: FaxiStore, today: LocalDate, onOpenMember: (String) -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type

    FaxiPage(
        title = "기사 명단",
        trailing = { Text("카드를 누르면 자세히 볼 수 있어요", style = t.caption, color = c.inkMuted) },
    ) {
        store.members.chunked(2).forEach { pair ->
            val cells = pair.map { member ->
                @Composable { m: Modifier ->
                    DriverLicenseCard(
                        member, store.driverRating(member.id),
                        m.clip(RoundedCornerShape(FaxiDimens.radiusLg))
                            .clickable(role = Role.Button) { onOpenMember(member.id) },
                        today = today,
                    )
                }
            }
            row(*cells.toTypedArray())
        }
        store.lifetime.forEach { ledger ->
            val learner = store.member(ledger.learnerId)
            full { m ->
                FaxiCard(m) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LedgerIcon(c.accentInk, FaxiDimens.iconMd)
                        SectionTitle("평생 장부 · ${learner.name}(${learner.callsign})")
                        StatusTag("${learner.name}에게는 면허 따는 날 공개")
                    }
                    Text(
                        "${ledger.sinceYear}년부터 ${won(ledger.rides)}번, ${won(ledger.km)}km를 태워 줬어요.",
                        style = t.title, color = c.ink,
                    )
                    BigWon(ledger.amount)
                    Text(
                        "정식 기사가 되면 부모님을 태운 요금으로 갚아 나가요. 갚을 수는 있지만, 다 갚기는 어려워요.",
                        style = t.body, color = c.inkMuted,
                    )
                }
            }
        }
    }
}

@FaxiPreviews
@Composable
private fun RosterPreview() = PreviewFrame {
    RosterScreen(FaxiStore.sample(), today = LocalDate.of(2026, 10, 27))
}
