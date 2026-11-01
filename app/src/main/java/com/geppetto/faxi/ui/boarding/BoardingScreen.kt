package com.geppetto.faxi.ui.boarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import com.geppetto.faxi.fare.FamilyExtra
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.geppetto.faxi.ui.components.ReceiptIcon
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.roster.PhoneLinkCard
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/**
 * 탑승 (주차 상태). 두 단계다.
 * 1) 기사 로그인: 운전할 사람이 자기 폰 코드를 누르면 차가 기억한다(기사가 바뀔 때만 다시).
 * 2) 손님 탑승: 매 운행 "손님 누구세요?" → 손님 폰 코드 4자리 → 미터기 켜기.
 * 미터기는 혼자 켤 수 없다: 요금을 낼 손님이 자기 폰으로 확인해야 켜진다.
 */
@Composable
fun BoardingScreen(
    store: FaxiStore,
    speak: (String) -> Unit,
    onStarted: () -> Unit,
) {
    // 기사 바꾸기를 누르면 로그인 단계로 돌아간다(지금 기사는 새 기사가 로그인할 때까지 그대로)
    var changingDriver by remember { mutableStateOf(false) }
    if (!store.driverSignedIn || changingDriver) {
        DriverSignIn(store, speak, onCancel = if (store.driverSignedIn) ({ changingDriver = false }) else null, onSignedIn = { changingDriver = false })
    } else {
        PassengerBoarding(store, speak, onChangeDriver = { changingDriver = true }, onStarted = onStarted)
    }
}

/** 기사 로그인: 운전할 사람 고르기 → 그 사람 폰 코드 → 로그인. 로그인하면 아직 못 들은 후기를 읽어 준다. */
@Composable
private fun DriverSignIn(store: FaxiStore, speak: (String) -> Unit, onCancel: (() -> Unit)?, onSignedIn: () -> Unit) {
    var pickedId by remember { mutableStateOf<String?>(null) }
    val picked = pickedId?.let(store::member)
    FaxiPage(title = "기사 로그인", trailing = { onCancel?.let { FaxiSecondaryButton("취소", onClick = it) } }) {
        row(
            { m ->
                Plain(m) {
                    SectionTitle("1  누가 운전하나요?")
                    Text("한 번 로그인하면 차가 기억해요. 운전하는 사람이 바뀔 때만 다시 해요.", style = Faxi.type.body, color = Faxi.colors.inkMuted)
                    val drivers = store.family.filter { it.canDrive }
                    if (drivers.isEmpty()) {
                        Text("운전하는 가족이 아직 없어요. 기사 탭에서 \"가족 추가\"로 등록해 주세요.", style = Faxi.type.body, color = Faxi.colors.ink)
                    }
                    drivers.forEach { member ->
                        MemberTile(member, selected = member.id == pickedId) { pickedId = member.id }
                    }
                }
            },
            { m ->
                CodeCard(
                    modifier = m,
                    title = "2  기사 코드",
                    prompt = if (picked == null) "운전할 사람을 먼저 골라 주세요." else "${picked.name} 폰에 뜬 4자리를 눌러 주세요.",
                    enabled = picked != null,
                    action = "로그인",
                ) { code ->
                    val id = pickedId ?: return@CodeCard false
                    if (!store.signInDriver(id, code)) return@CodeCard false
                    // 후기 낭독: 이 기사가 아직 듣지 못한 가장 최근 후기를 한 번 읽어 준다(주차 중, 차 안 가족 모두가 듣는다)
                    store.takeUnreadReview(id)?.let { (receipt, review) ->
                        speak(reviewLine(store.member(receipt.passengerId).name, review.stars, review.tags))
                    }
                    onSignedIn()
                    true
                }
            },
        )
        // 폰을 아직 연결하지 않았다면(처음 등록한 가장 등): 여기서 초대 QR을 찍으면 그 폰에 코드가 뜬다
        picked?.let { p -> full { m -> PhoneLinkCard(store, p.id, m) } }
    }
}

/** 손님 탑승: 로그인한 기사의 택시에 탈 손님 고르기 → 손님 폰 코드 → 미터기 켜기 */
@Composable
private fun PassengerBoarding(store: FaxiStore, speak: (String) -> Unit, onChangeDriver: () -> Unit, onStarted: () -> Unit) {
    var passengerId by remember { mutableStateOf<String?>(null) }
    var extras by remember { mutableStateOf(emptySet<FamilyExtra>()) }
    val driver = store.member(store.driverId)
    val passenger = passengerId?.let(store::member)

    FaxiPage(title = "운행 시작") {
        row(
            { m ->
                Plain(m) {
                    SectionTitle("기사")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(driver, FaxiDimens.targetMin, Faxi.type.title)
                        Column(Modifier.weight(1f)) {
                            Text("${driver.name} 택시", style = Faxi.type.title, color = Faxi.colors.ink)
                            Text("운전석에 로그인돼 있어요", style = Faxi.type.caption, color = Faxi.colors.inkMuted)
                        }
                        FaxiSecondaryButton("기사 바꾸기", onClick = onChangeDriver)
                    }
                    SectionTitle("1  손님 누구세요?")
                    store.family.filter { it.id != driver.id }.forEach { member ->
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
                    // 가족 할증·할인: 기사가 지금(주차 중) 고르고, 손님이 코드를 누르면 동의한 것. 운행 중에는 못 바꾼다.
                    SectionTitle("가족 할증·할인")
                    FamilyExtra.entries.chunked(2).forEach { pair ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                            pair.forEach { x ->
                                val on = x in extras
                                SelectTile(
                                    selected = on,
                                    onClick = { extras = if (on) extras - x else extras + x },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${x.label} ${x.tag}", style = Faxi.type.bodyStrong, color = Faxi.colors.ink)
                                        Text(x.hint, style = Faxi.type.caption, color = Faxi.colors.inkMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            { m ->
                CodeCard(
                    modifier = m,
                    title = "2  탑승 코드",
                    prompt = when {
                        passenger == null -> "손님을 먼저 골라 주세요."
                        extras.isEmpty() -> "${passenger.name} 폰에 뜬 4자리를 눌러 주세요."
                        else -> "${passenger.name} 폰에 뜬 4자리를 누르면 고른 할증·할인에도 동의한 거예요."
                    },
                    enabled = passenger != null,
                    action = "미터기 켜기",
                ) { code ->
                    val p = passengerId ?: return@CodeCard false
                    if (!store.verifyCode(p, code)) return@CodeCard false
                    if (store.settings.greet) speak("손님, 어디로 모실까요?")
                    store.startRide(p, extras)
                    passengerId = null
                    extras = emptySet()
                    onStarted()
                    true
                }
            },
        )
    }
}

/**
 * 코드 4칸 + 숫자판 + 주 버튼. 기사 로그인과 손님 탑승이 같이 쓴다.
 * submit이 false면 "코드가 맞지 않아요"를 띄우고 칸을 비운다. 안내 줄은 자리를 미리 잡아 버튼이 밀리지 않는다.
 */
@Composable
private fun CodeCard(
    modifier: Modifier,
    title: String,
    prompt: String,
    enabled: Boolean,
    action: String,
    submit: (String) -> Boolean,
) {
    var code by remember { mutableStateOf("") }
    var wrongCode by remember { mutableStateOf(false) }
    Plain(modifier) {
        SectionTitle(title)
        Text(prompt, style = Faxi.type.body, color = Faxi.colors.ink)
        CodeBoxes(code)
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = FaxiDimens.iconMd),
        ) {
            if (wrongCode) {
                WarningIcon(Faxi.colors.danger, FaxiDimens.iconMd)
                Text("코드가 맞지 않아요. 폰의 새 코드를 눌러 주세요.", style = Faxi.type.body, color = Faxi.colors.danger)
            }
        }
        Keypad(
            enabled = enabled,
            onDigit = { if (code.length < 4) code += it; wrongCode = false },
            onDelete = { code = code.dropLast(1); wrongCode = false },
        )
        Spacer(Modifier.weight(1f))
        FaxiPrimaryButton(
            action,
            enabled = enabled && code.length == 4,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                wrongCode = !submit(code)
                code = ""
            },
        )
    }
}

/**
 * "밤 택시" 스타일: 상자 없이 밤 바탕 위에 바로 쌓는다. 묶음은 테두리가 아니라 간격으로 나눈다.
 */
@Composable
private fun Plain(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4), content = content)
}

@Composable
private fun MemberTile(member: Member, selected: Boolean, onClick: () -> Unit) {
    SelectTile(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth(), wide = true) {
        Avatar(member, FaxiDimens.iconLg, Faxi.type.bodyStrong)
        Column(Modifier.weight(1f)) {
            Text(member.name, style = Faxi.type.bodyStrong, color = Faxi.colors.ink)
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
            .border(FaxiDimens.borderWidth, c.lineStrong, RoundedCornerShape(FaxiDimens.radiusMd))
            .background(c.surfaceRaised, RoundedCornerShape(FaxiDimens.radiusMd))
            .padding(FaxiDimens.space4),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptIcon(c.accentInk, FaxiDimens.iconMd)
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

@FaxiPreviews
@Composable
private fun DriverSignInPreview() = PreviewFrame {
    BoardingScreen(FaxiStore.sample().apply { signOutDriver() }, speak = {}, onStarted = {})
}
