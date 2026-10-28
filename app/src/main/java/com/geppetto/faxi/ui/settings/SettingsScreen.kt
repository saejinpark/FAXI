package com.geppetto.faxi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.geppetto.faxi.data.Dunning
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.ReviewsAloud
import com.geppetto.faxi.data.dunningLine
import com.geppetto.faxi.data.reviewLine
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.SpeakerIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/**
 * 설정 (주차 중에만, 사이드바 네 번째 탭). 차가 하는 말과 장부 규칙을 가족이 고른다.
 * 독촉·후기 낭독은 가족에 따라 공격적으로 들릴 수 있어서 말투를 바꾸거나 끌 수 있다. "들어 보기"로 미리 듣는다.
 * 바꾸면 바로 저장된다(장부와 같은 파일).
 */
@Composable
fun SettingsScreen(store: FaxiStore, speak: (String) -> Unit) {
    val s = store.settings
    val c = Faxi.colors
    val t = Faxi.type
    FaxiPage(title = "설정") {
        row(
            { m ->
                FaxiCard(m) {
                    SectionTitle("밀린 돈 알림")
                    Text("손님을 고를 때 이 기사에게 밀린 돈이 있으면 알려요.", style = t.body, color = c.inkMuted)
                    Choices(Dunning.entries, s.dunning, { it.label }) { store.updateSettings(s.copy(dunning = it)) }
                    val sample = dunningLine(3_120, 3, "엄마", s.dunning)
                    Text("\"$sample\"", style = t.bodyStrong, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    FaxiSecondaryButton(
                        "들어 보기",
                        enabled = s.dunning != Dunning.ScreenOnly,
                        onClick = { speak(sample) },
                        leading = { SpeakerIcon(c.ink, FaxiDimens.iconMd) },
                    )
                }
            },
            { m ->
                FaxiCard(m) {
                    SectionTitle("후기 읽어 주기")
                    Text("기사를 고를 때 손님 폰에서 온 후기를 읽어요. 읽지 않은 후기도 기사 상세에는 남아요.", style = t.body, color = c.inkMuted)
                    Choices(ReviewsAloud.entries, s.reviewsAloud, { it.label }) { store.updateSettings(s.copy(reviewsAloud = it)) }
                    val sample = reviewLine("엄마", 5, listOf("길 잘 앎"))
                    Text(
                        if (s.reviewsAloud == ReviewsAloud.Off) "후기를 소리 내어 읽지 않아요." else "\"$sample\"",
                        style = t.bodyStrong, color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    FaxiSecondaryButton(
                        "들어 보기",
                        enabled = s.reviewsAloud != ReviewsAloud.Off,
                        onClick = { speak(sample) },
                        leading = { SpeakerIcon(c.ink, FaxiDimens.iconMd) },
                    )
                }
            },
        )
        full { m ->
            FaxiCard(m) {
                SectionTitle("안내 음성")
                OnOff("탑승 인사", "미터기를 켤 때 \"손님, 어디로 모실까요?\"", s.greet) { store.updateSettings(s.copy(greet = it)) }
                OnOff("요금 안내", "내릴 때 요금을 읽어요.", s.fareAloud) { store.updateSettings(s.copy(fareAloud = it)) }
                Text("가족 장부는 이 차 안에만 저장돼요. 버전 ${appVersion()}", style = t.caption, color = c.inkMuted)
            }
        }
    }
}

/** 하나만 고르는 선택지 줄. 고른 칸은 accent 테두리 + 체크(SelectTile). */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FaxiDimens.targetGap)) {
        options.forEach { o ->
            SelectTile(selected = o == selected, onClick = { onSelect(o) }, modifier = Modifier.weight(1f)) {
                Text(label(o), style = Faxi.type.label, color = Faxi.colors.ink)
            }
        }
    }
}

/** 켜기/끄기 한 줄: 이름과 설명 + 두 칸 선택 */
@Composable
private fun OnOff(name: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
        Text(name, style = Faxi.type.title, color = Faxi.colors.ink)
        Text(detail, style = Faxi.type.body, color = Faxi.colors.inkMuted)
        Choices(listOf(true, false), on, { if (it) "켜기" else "끄기" }, onChange)
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
}

@FaxiPreviews
@Composable
private fun SettingsPreview() = PreviewFrame {
    SettingsScreen(FaxiStore.sample(), speak = {})
}
