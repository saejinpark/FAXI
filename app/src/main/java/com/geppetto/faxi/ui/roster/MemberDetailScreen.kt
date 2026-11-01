package com.geppetto.faxi.ui.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.QrImage
import com.geppetto.faxi.ui.components.WarningIcon
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
import com.geppetto.faxi.data.ga
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
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
    onEdit: () -> Unit = {},
    onGraduate: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val c = Faxi.colors
    val t = Faxi.type
    val member = store.member(memberId)
    val owedToMe = store.debts.filter { it.creditorId == memberId }
    val iOwe = store.debts.filter { it.debtorId == memberId }

    FaxiPage(title = member.name, onBack = onBack, trailing = { FaxiSecondaryButton("고치기", onClick = onEdit) }) {
        // 예비 기사: 면허를 따면 정식 기사가 되고 평생 장부를 공개한다(자기 선언, 면허증은 받지 않는다)
        if (member.grade == Grade.Learner) {
            full { m ->
                FaxiCard(m) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
                            Text("${member.name.ga()} 면허를 땄나요?", style = t.title, color = c.ink)
                            Text("정식 기사가 되고, 그동안 쌓인 평생 장부를 공개해요. 면허증은 받지 않아요.", style = t.body, color = c.inkMuted)
                        }
                        FaxiPrimaryButton("면허 땄어요", onClick = onGraduate)
                    }
                }
            }
        }
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
                        if (rating != null) "받은 후기 %d개, 평균 별 %.1f".format(reviews.size, rating) else "받은 후기",
                    )
                    when {
                        reviews.isNotEmpty() -> reviews.take(4).forEachIndexed { i, (r, review) ->
                            if (i > 0) Divider()
                            ReviewItem(store, r, review)
                        }
                        member.grade == Grade.Full -> Text("아직 받은 후기가 없어요.", style = t.body, color = c.inkMuted)
                        else -> Text("면허를 따고 기사로 운행하면 후기가 쌓여요.", style = t.body, color = c.inkMuted)
                    }
                }
            },
        )
        full { m -> PhoneLinkCard(store, memberId, m) }
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

/**
 * 폰 연결: 이 사람 폰의 FAXI 앱이 찍을 초대 QR. 탑승 비밀 열쇠가 들어 있어서 눌러야 보이고,
 * 화면을 떠나면 다시 숨는다. 주차 상태의 차 안에서 그 사람이 직접 찍는 것을 전제로 한다.
 */
@Composable
internal fun PhoneLinkCard(store: FaxiStore, memberId: String, modifier: Modifier) {
    val c = Faxi.colors
    val t = Faxi.type
    val member = store.member(memberId)
    var shown by remember(memberId) { mutableStateOf(false) }
    FaxiCard(modifier) {
        SectionTitle("폰 연결")
        Text(
            "${member.name} 폰의 FAXI 앱에서 \"초대 QR 찍기\"를 누르고 이 QR을 찍으면, 그 폰으로 한마디·기사 후기를 보내고 영수증을 모아요. 없어도 탈 수 있어요.",
            style = t.body, color = c.ink,
        )
        if (!shown) {
            FaxiSecondaryButton("초대 QR 보이기", onClick = { shown = true })
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrImage(store.inviteFor(memberId).toLink(), 240.dp, "${member.name} 초대 QR 코드")
                Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WarningIcon(c.danger, FaxiDimens.iconMd)
                        Text("${member.name}의 탑승 열쇠가 들어 있어요. 다른 사람이 찍지 않게 해 주세요.", style = t.body, color = c.ink)
                    }
                    FaxiSecondaryButton("QR 숨기기", onClick = { shown = false })
                }
            }
        }
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
        Text(
            if (store.member(memberId).photo != null) "${store.name(memberId)} 폰에서 보낸 사진을 쓰고 있어요. 캐릭터는 사진이 없을 때 보여요."
            else "사진은 ${store.name(memberId)} 폰의 FAXI 앱 설정에서 찍어 보내요.",
            style = Faxi.type.caption, color = c.inkMuted,
        )
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
