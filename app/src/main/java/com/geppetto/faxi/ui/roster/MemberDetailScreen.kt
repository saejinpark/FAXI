package com.geppetto.faxi.ui.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.Avatars
import com.geppetto.faxi.data.Debt
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Receipt
import com.geppetto.faxi.data.Review
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.AvatarCircle
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.DriverLicenseCard
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.StarIcon
import com.geppetto.faxi.ui.ledger.DebtRow
import com.geppetto.faxi.ui.ledger.RideRow
import com.geppetto.faxi.ui.receipt.ReceiptTimeFormat
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDate

/**
 * 기사 상세 (주차 상태, 가족 모두가 보는 화면).
 * 자격증 + 받을 돈/갚을 돈 → 최근 운행 + 받은 후기 → 사진 대신 쓸 캐릭터 고르기.
 * 빚·운행을 누르면 장부와 같은 빚 상세·영수증 화면으로 들어간다.
 */
@Composable
fun MemberDetailScreen(
    store: FaxiStore,
    memberId: String,
    onBack: () -> Unit,
    onOpenDebt: (Debt) -> Unit,
    onOpenReceipt: (String) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val c = Faxi.colors
    val t = Faxi.type
    val member = store.member(memberId)
    val owedToMe = store.debts.filter { it.creditorId == memberId }
    val iOwe = store.debts.filter { it.debtorId == memberId }

    FaxiPage(title = "${member.name} · ${member.callsign}", onBack = onBack) {
        row(
            { m -> DriverLicenseCard(member, store.driverRating(memberId), m, today = today) },
            { m ->
                FaxiCard(m) {
                    MoneySection("받을 돈", owedToMe, "받을 돈이 없어요.", store, onOpenDebt)
                    Divider()
                    MoneySection("갚을 돈", iOwe, "갚을 돈이 없어요.", store, onOpenDebt)
                    store.lifetimeOf(memberId)?.let { l ->
                        Divider()
                        SectionTitle("평생 장부")
                        Text("${won(l.amount)}원 · ${won(l.rides)}번 · ${won(l.km)}km", style = t.amount, color = c.ink)
                        Text("면허를 따는 날 공개돼요.", style = t.caption, color = c.inkMuted)
                    }
                }
            },
        )
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("최근 운행")
                    val rides = store.ridesOf(memberId)
                    if (rides.isEmpty()) Text("아직 운행 기록이 없어요.", style = t.body, color = c.inkMuted)
                    rides.take(4).forEach { r -> RideRow(store, r, onClick = { onOpenReceipt(r.id) }) }
                }
            },
            { m ->
                FaxiCard(m) {
                    val reviews = store.reviewsOf(memberId)
                    val rating = store.driverRating(memberId)
                    SectionTitle(
                        if (rating != null) "받은 후기 · 별 %.1f · %d개".format(rating, reviews.size) else "받은 후기",
                    )
                    when {
                        reviews.isNotEmpty() -> reviews.take(4).forEachIndexed { i, (r, review) ->
                            if (i > 0) Divider()
                            ReviewItem(store, r, review)
                        }
                        member.grade == Grade.Full -> Text("아직 받은 후기가 없어요.", style = t.body, color = c.inkMuted)
                        member.grade == Grade.Learner -> Text("면허를 따고 기사로 운행하면 후기가 쌓여요.", style = t.body, color = c.inkMuted)
                        else -> Text("명예 기사는 손님으로만 타요.", style = t.body, color = c.inkMuted)
                    }
                }
            },
        )
        full { m -> AvatarPicker(store, memberId, m) }
    }
}

@Composable
private fun MoneySection(
    title: String,
    debts: List<Debt>,
    empty: String,
    store: FaxiStore,
    onOpenDebt: (Debt) -> Unit,
) {
    val total = debts.sumOf { it.amount }
    SectionTitle(if (debts.isEmpty()) title else "$title · ${won(total)}원")
    if (debts.isEmpty()) Text(empty, style = Faxi.type.body, color = Faxi.colors.inkMuted)
    debts.forEach { d -> DebtRow(store, d, onClick = { onOpenDebt(d) }) }
}

/** 후기 한 건: 별(채운 별 + 숫자로도), 누가 언제, 태그 */
@Composable
private fun ReviewItem(store: FaxiStore, r: Receipt, review: Review) {
    val c = Faxi.colors
    val t = Faxi.type
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "별 ${review.stars}개, ${store.name(r.passengerId)}, ${review.tags.joinToString()}"
        },
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { n ->
                StarIcon(if (n <= review.stars) c.accentInk else c.lineStrong, FaxiDimens.iconMd, filled = n <= review.stars)
            }
            Text("  ${store.name(r.passengerId)} · ${r.endedAt.format(ReceiptTimeFormat)} ${r.to}", style = t.caption, color = c.inkMuted)
        }
        if (review.tags.isNotEmpty()) Text(review.tags.joinToString(" · "), style = t.body, color = c.ink)
    }
}

/** 사진 대신 쓸 캐릭터. 고르면 바로 저장되고 자격증·탑승 화면에 반영된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarPicker(store: FaxiStore, memberId: String, modifier: Modifier) {
    val c = Faxi.colors
    val current = store.member(memberId).avatar
    FaxiCard(modifier) {
        SectionTitle("사진 대신 캐릭터")
        Text("사진은 폰 앱에서 찍어 보내는 기능이 생기면 바꿀 수 있어요.", style = Faxi.type.caption, color = c.inkMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap),
            verticalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap),
        ) {
            Avatars.forEach { a ->
                SelectTile(selected = a.key == current, onClick = { store.setAvatar(memberId, a.key) }) {
                    AvatarCircle(a.emoji, a.label.take(1), 52.dp)
                    Text(a.label, style = Faxi.type.label, color = c.ink)
                }
            }
        }
    }
}

@FaxiPreviews
@Composable
private fun MemberDetailPreview() = PreviewFrame {
    MemberDetailScreen(
        FaxiStore.sample(), "dad", onBack = {}, onOpenDebt = {}, onOpenReceipt = {},
        today = LocalDate.of(2026, 10, 27),
    )
}
