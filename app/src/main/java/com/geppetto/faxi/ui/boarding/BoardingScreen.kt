package com.geppetto.faxi.ui.boarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.geppetto.faxi.data.Dunning
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Member
import com.geppetto.faxi.data.dunningLine
import com.geppetto.faxi.data.owesPhrase
import com.geppetto.faxi.data.reviewLine
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.Avatar
import com.geppetto.faxi.ui.components.BackspaceIcon
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/**
 * 탑승 (주차 상태). 기사 고르기 → "손님 누구세요?" → 손님 폰의 탑승 코드 4자리 → 미터기 켜기.
 * 미터기는 혼자 켤 수 없다: 다른 가족의 코드가 있어야 켜진다.
 */
@Composable
fun BoardingScreen(
    store: FaxiStore,
    speak: (String) -> Unit,
    onStarted: () -> Unit,
) {
    var passengerId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var wrongCode by remember { mutableStateOf(false) }

    val driver = store.member(store.driverId)
    val passenger = passengerId?.let(store::member)

    FaxiPage(title = "운행 시작") {
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("1  기사")
                    store.members.filter { it.canDrive }.forEach { member ->
                        MemberTile(member, selected = member.id == driver.id) {
                            store.selectDriver(member.id)
                            if (passengerId == member.id) passengerId = null
                            // 후기 낭독: 이 기사가 아직 듣지 못한 가장 최근 후기를 한 번 읽어 준다(주차 중, 차 안 가족 모두가 듣는다)
                            store.takeUnreadReview(member.id)?.let { (receipt, review) ->
                                speak(reviewLine(store.member(receipt.passengerId).name, review.stars, review.tags))
                            }
                        }
                    }
                    SectionTitle("2  손님")
                    store.members.filter { it.id != driver.id }.forEach { member ->
                        MemberTile(member, selected = member.id == passengerId) {
                            passengerId = member.id
                            // 밀린 돈 알림: 말투는 설정(단호하게/부드럽게/화면에만)
                            val tone = store.settings.dunning
                            if (tone != Dunning.ScreenOnly) {
                                store.debt(member.id, driver.id)?.let { debt ->
                                    speak(dunningLine(debt.amount, store.daysOf(debt), member.name, tone))
                                }
                            }
                        }
                    }
                    if (passenger != null) {
                        val debt = store.debt(passenger.id, driver.id)
                        val owedBack = store.debt(driver.id, passenger.id)
                        when {
                            debt != null -> DebtReminder(dunningLine(debt.amount, store.daysOf(debt), passenger.name, store.settings.dunning))
                            passenger.grade == Grade.Learner ->
                                Text("예비 기사 요금은 평생 장부에 쌓여요.", style = Faxi.type.body, color = Faxi.colors.inkMuted)
                            owedBack != null -> Text(
                                "이번 요금은 ${owesPhrase(driver.name, passenger.name)} 진 빚 ${won(owedBack.amount)}원과 먼저 퉁쳐요.",
                                style = Faxi.type.body, color = Faxi.colors.inkMuted,
                            )
                        }
                    }
                }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("3  탑승 코드")
                    Text(
                        if (passenger == null) "손님을 먼저 골라 주세요." else "${passenger.name} 폰에 뜬 4자리를 눌러 주세요.",
                        style = Faxi.type.body,
                        color = Faxi.colors.ink,
                    )
                    CodeBoxes(code)
                    if (wrongCode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WarningIcon(Faxi.colors.danger, FaxiDimens.iconMd)
                            Text("코드가 맞지 않아요. 폰의 새 코드를 눌러 주세요.", style = Faxi.type.body, color = Faxi.colors.danger)
                        }
                    }
                    Keypad(
                        enabled = passenger != null,
                        onDigit = { if (code.length < 4) code += it; wrongCode = false },
                        onDelete = { code = code.dropLast(1); wrongCode = false },
                    )
                    Spacer(Modifier.weight(1f))
                    FaxiPrimaryButton(
                        "미터기 켜기",
                        enabled = passenger != null && code.length == 4,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val p = passengerId ?: return@FaxiPrimaryButton
                            if (store.verifyBoardingCode(p, code)) {
                                if (store.settings.greet) speak("손님, 어디로 모실까요?")
                                store.startRide(p)
                                code = ""
                                passengerId = null
                                onStarted()
                            } else {
                                wrongCode = true
                                code = ""
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun MemberTile(member: Member, selected: Boolean, onClick: () -> Unit) {
    SelectTile(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth(), wide = true) {
        Avatar(member, FaxiDimens.iconLg, Faxi.type.bodyStrong)
        Column(Modifier.weight(1f)) {
            Text("${member.name} · ${member.callsign}", style = Faxi.type.bodyStrong, color = Faxi.colors.ink)
            Text(member.grade.label, style = Faxi.type.caption, color = Faxi.colors.inkMuted)
        }
    }
}

@Composable
private fun DebtReminder(text: String) {
    val c = Faxi.colors
    Row(
        Modifier
            .fillMaxWidth()
            .border(FaxiDimens.borderWidth, c.danger, RoundedCornerShape(FaxiDimens.radiusMd))
            .background(c.surfaceRaised, RoundedCornerShape(FaxiDimens.radiusMd))
            .padding(FaxiDimens.space4),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WarningIcon(c.danger, FaxiDimens.iconMd)
        Text(text, style = Faxi.type.bodyStrong, color = c.ink)
    }
}

/** 입력한 코드 4칸. 비밀번호가 아니라 30초마다 바뀌는 코드라 숫자를 그대로 보여 준다. */
@Composable
private fun CodeBoxes(code: String) {
    val c = Faxi.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap),
        modifier = Modifier.semantics { contentDescription = "입력한 코드 ${code.length}자리" },
    ) {
        repeat(4) { i ->
            val shape = RoundedCornerShape(FaxiDimens.radiusSm)
            Box(
                Modifier
                    .size(FaxiDimens.targetMin)
                    .background(c.surfaceRaised, shape)
                    .border(
                        FaxiDimens.borderWidth,
                        if (i == code.length) c.accent else c.line,
                        shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(code.getOrNull(i)?.toString() ?: "", style = Faxi.type.headline, color = c.ink)
            }
        }
    }
}

/** 키보드 없이 누르는 숫자판. 버튼 88dp, 간격 16dp. */
@Composable
private fun Keypad(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val keys = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
        keys.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                line.forEach { d ->
                    FaxiSecondaryButton(d.toString(), onClick = { onDigit(d) }, enabled = enabled, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            Spacer(Modifier.weight(1f))
            FaxiSecondaryButton("0", onClick = { onDigit('0') }, enabled = enabled, modifier = Modifier.weight(1f))
            FaxiSecondaryButton(
                "지움",
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                leading = { BackspaceIcon(Faxi.colors.ink, FaxiDimens.iconMd) },
            )
        }
    }
}

@FaxiPreviews
@Composable
private fun BoardingPreview() = PreviewFrame {
    BoardingScreen(FaxiStore.sample(), speak = {}, onStarted = {})
}
