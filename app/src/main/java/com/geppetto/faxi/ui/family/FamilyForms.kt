package com.geppetto.faxi.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.eul
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/** 등록할 때 고르는 운전 여부. 등급(정식·예비 기사)으로 바뀐다. */
private enum class Driving(val label: String, val grade: Grade) {
    Yes("운전해요", Grade.Full),
    NotYet("아직 면허 없어요", Grade.Learner),
}

/**
 * 첫 실행: 이 집의 가장을 첫 기사로 등록하며 택시를 연다. 가족이 아무도 없을 때만 뜬다.
 * 가장은 이 차를 운전하는 사람이라 운전 여부는 묻지 않는다(정식 기사).
 * 심사·시연용으로 시연 가족(아빠·엄마·지우·할머니)을 불러올 수도 있다.
 */
@Composable
fun FamilySetupScreen(store: FaxiStore) {
    FaxiPage(title = "우리 집 택시를 엽니다") {
        full { m ->
            FaxiCard(m) {
                Text(
                    "이 차의 첫 기사는 우리 집 가장이에요. 가장을 등록하면 택시가 문을 열고, 가족은 한 사람씩 더할 수 있어요.",
                    style = Faxi.type.title, color = Faxi.colors.ink,
                )
            }
        }
        row(
            { m ->
                MemberForm(
                    modifier = m,
                    title = "가장의 이름",
                    suggestions = listOf("아빠", "엄마"),
                    action = "택시 개업하기",
                    askDriving = false,
                ) { name, _ -> store.startFamily(name) }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("먼저 둘러보기")
                    Text(
                        "아빠·엄마·지우·할머니가 사는 시연 가족으로 시작해요. 지난 운행과 장부가 들어 있어요.",
                        style = Faxi.type.body, color = Faxi.colors.inkMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    FaxiSecondaryButton("시연 가족으로 둘러보기", onClick = store::loadDemo, modifier = Modifier.fillMaxWidth())
                }
            },
        )
    }
}

/** 기사 탭 "가족 추가": 한 사람씩 더한다. 더하면 그 사람 상세(폰 연결 QR)로 간다. */
@Composable
fun AddMemberScreen(store: FaxiStore, onBack: () -> Unit, onAdded: (String) -> Unit) {
    FaxiPage(title = "가족 추가", onBack = onBack) {
        full { m ->
            MemberForm(
                modifier = m,
                title = "부르는 이름",
                // 이미 있는 이름은 빼고 보여 준다
                suggestions = listOf("엄마", "아빠", "첫째", "둘째", "할머니", "할아버지").filter { n -> store.family.none { it.name == n } },
                action = "가족으로 등록",
            ) { name, grade -> onAdded(store.addMember(name, grade).id) }
        }
    }
}

/** 기사 상세 "고치기": 이름·별명 바꾸기, 가족에서 빼기(주고받을 돈이 남았거나 운행 중이면 이유를 말하고 막는다). */
@Composable
fun EditMemberScreen(store: FaxiStore, memberId: String, onBack: () -> Unit, onRemoved: () -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    val member = store.member(memberId)
    var name by rememberSaveable(memberId) { mutableStateOf(member.name) }
    var nickname by rememberSaveable(memberId) { mutableStateOf(member.nickname) }
    var confirmRemove by rememberSaveable(memberId) { mutableStateOf(false) }
    val blocked = store.whyCannotRemove(memberId)
    FaxiPage(title = "${member.name} 고치기", onBack = onBack) {
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("부르는 이름")
                    NameField(name, "엄마, 지우, 막내…") { name = it.take(10) }
                    SectionTitle("자격증 별명")
                    NameField(nickname, "비워 두면 \"${name.ifBlank { member.name }} 기사\"") { nickname = it.take(14) }
                    Spacer(Modifier.weight(1f))
                    FaxiPrimaryButton(
                        "저장",
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { store.updateMember(memberId, name, nickname); onBack() },
                    )
                }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("가족에서 빼기")
                    Text(
                        blocked ?: "지난 영수증과 기록에는 이름이 남아요. ${member.name} 폰의 코드와 한마디는 더 받지 않아요.",
                        style = t.body, color = if (blocked != null) c.ink else c.inkMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!confirmRemove) {
                        FaxiSecondaryButton("가족에서 빼기", enabled = blocked == null, onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("${member.name.eul()} 정말 뺄까요?", style = t.bodyStrong, color = c.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                            FaxiSecondaryButton("취소", onClick = { confirmRemove = false }, modifier = Modifier.weight(1f))
                            FaxiSecondaryButton("빼기", onClick = { if (store.removeMember(memberId)) onRemoved() }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun NameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val c = Faxi.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = FaxiDimens.targetMin),
        textStyle = Faxi.type.body,
        singleLine = true,
        placeholder = { Text(placeholder, style = Faxi.type.body, color = c.inkMuted) },
        shape = RoundedCornerShape(FaxiDimens.radiusSm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.lineStrong, cursorColor = c.accentInk,
        ),
    )
}

/** 이름(빠른 고르기 칸 + 직접 쓰기)과 (askDriving이면) 운전 여부. 이름이 비어 있으면 주 버튼이 꺼져 있다. */
@Composable
private fun MemberForm(
    modifier: Modifier,
    title: String,
    suggestions: List<String>,
    action: String,
    askDriving: Boolean = true,
    onSubmit: (name: String, grade: Grade) -> Unit,
) {
    val c = Faxi.colors
    val t = Faxi.type
    var name by rememberSaveable { mutableStateOf("") }
    var driving by rememberSaveable { mutableStateOf(Driving.Yes) }
    FaxiCard(modifier) {
        SectionTitle(title)
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            suggestions.forEach { s ->
                SelectTile(selected = name == s, onClick = { name = s }) { Text(s, style = t.label, color = c.ink) }
            }
        }
        NameField(name, "직접 쓰기: 지우, 막내, 큰엄마…") { name = it.take(10) }
        if (askDriving) DrivingChoice(driving) { driving = it }
        Spacer(Modifier.weight(1f))
        FaxiPrimaryButton(
            action,
            enabled = name.isNotBlank(),
            onClick = { onSubmit(name.trim(), driving.grade) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 운전 여부 셋 중 하나 + 고른 것의 뜻 한 줄 */
@Composable
private fun DrivingChoice(driving: Driving, onPick: (Driving) -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
        SectionTitle("운전")
        Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                Driving.entries.forEach { d ->
                    SelectTile(selected = driving == d, onClick = { onPick(d) }, modifier = Modifier.weight(1f)) {
                        Text(d.label, style = t.label, color = c.ink)
                    }
                }
            }
            Text(
                when (driving) {
                    Driving.Yes -> "기사로 운행하고, 다른 사람이 운전할 때는 손님이 돼요. 운전하지 않는 어른도 이걸 골라요."
                    Driving.NotYet -> "손님으로만 타요. 탄 요금은 청구하지 않고 평생 장부에 쌓여요."
                },
                style = t.body, color = c.inkMuted,
            )
        }
    }
}

@FaxiPreviews
@Composable
private fun FamilySetupPreview() = PreviewFrame {
    FamilySetupScreen(FaxiStore(FaxiStore.emptySnapshot("preview")))
}
