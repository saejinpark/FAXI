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

/** 등록할 때 고르는 운전 여부. 등급(정식·예비·명예 기사)으로 바뀐다. */
private enum class Driving(val label: String, val grade: Grade) {
    Yes("운전해요", Grade.Full),
    NotYet("아직 면허 없어요", Grade.Learner),
    NoLonger("이제 운전 안 해요", Grade.Honorary),
}

/**
 * 첫 실행: 이 집의 가장을 첫 가족으로 등록하며 택시를 연다. 가족이 아무도 없을 때만 뜬다.
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
                ) { name, grade -> store.startFamily(name, grade) }
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
                suggestions = listOf("엄마", "아빠", "첫째", "둘째", "할머니", "할아버지").filter { n -> store.members.none { it.name == n } },
                action = "가족으로 등록",
            ) { name, grade -> onAdded(store.addMember(name, grade).id) }
        }
    }
}

/** 이름(빠른 고르기 칸 + 직접 쓰기)과 운전 여부. 이름이 비어 있으면 주 버튼이 꺼져 있다. */
@Composable
private fun MemberForm(
    modifier: Modifier,
    title: String,
    suggestions: List<String>,
    action: String,
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
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(10) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FaxiDimens.targetMin),
            textStyle = t.body,
            singleLine = true,
            placeholder = { Text("직접 쓰기: 지우, 막내, 큰엄마…", style = t.body, color = c.inkMuted) },
            shape = RoundedCornerShape(FaxiDimens.radiusSm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                focusedBorderColor = c.accent, unfocusedBorderColor = c.lineStrong, cursorColor = c.accentInk,
            ),
        )
        SectionTitle("운전")
        Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
                Driving.entries.forEach { d ->
                    SelectTile(selected = driving == d, onClick = { driving = d }, modifier = Modifier.weight(1f)) {
                        Text(d.label, style = t.label, color = c.ink)
                    }
                }
            }
            Text(
                when (driving) {
                    Driving.Yes -> "기사로 운행하고 요금을 받아요. 다른 가족 차에서는 손님도 돼요."
                    Driving.NotYet -> "손님으로만 타요. 탄 요금은 청구하지 않고 평생 장부에 쌓여요."
                    Driving.NoLonger -> "손님으로 타요. 지난 운전 기록은 명예 기사로 남아요."
                },
                style = t.body, color = c.inkMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        FaxiPrimaryButton(
            action,
            enabled = name.isNotBlank(),
            onClick = { onSubmit(name.trim(), driving.grade) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@FaxiPreviews
@Composable
private fun FamilySetupPreview() = PreviewFrame {
    FamilySetupScreen(FaxiStore(FaxiStore.emptySnapshot("preview")))
}
