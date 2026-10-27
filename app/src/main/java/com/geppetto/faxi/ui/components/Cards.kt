package com.geppetto.faxi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Member
import com.geppetto.faxi.data.avatarOf
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/** 기본 카드: surface 면, 모서리 28, 패딩 24 */
@Composable
fun FaxiCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Faxi.colors.surface, RoundedCornerShape(FaxiDimens.radiusLg))
            .padding(FaxiDimens.space6),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Faxi.type.bodyStrong,
        color = Faxi.colors.inkMuted,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(FaxiDimens.borderWidth).background(Faxi.colors.line))
}

enum class Tone { Neutral, Accent, Danger, Ok }

/** 상태 꼬리표. 색만으로 전하지 않도록 단어(+ 선택적으로 아이콘)를 함께 쓴다. */
@Composable
fun StatusTag(text: String, tone: Tone = Tone.Neutral, icon: (@Composable (Color) -> Unit)? = null) {
    val c = Faxi.colors
    val ink = when (tone) {
        Tone.Neutral -> c.inkMuted
        Tone.Accent -> c.accentInk
        Tone.Danger -> c.danger
        Tone.Ok -> c.ok
    }
    Row(
        Modifier
            .border(FaxiDimens.borderWidth, ink, RoundedCornerShape(50))
            .padding(horizontal = FaxiDimens.space3, vertical = FaxiDimens.space1),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke(ink)
        Text(text, style = Faxi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = ink)
    }
}

/**
 * 사진 자리. 폰 → 차 사진 전송이 정해지기 전까지는 고른 캐릭터를,
 * 캐릭터도 없으면 호출명 첫 글자를 보여 준다.
 */
@Composable
fun Avatar(member: Member, size: Dp, style: TextStyle = Faxi.type.title) {
    AvatarCircle(avatarOf(member.avatar)?.emoji, member.callsign.take(1), size, style)
}

/** 원 안에 캐릭터 이모지(있으면) 또는 글자 하나. 이모지는 원 지름의 55% 크기. */
@Composable
fun AvatarCircle(emoji: String?, fallback: String, size: Dp, style: TextStyle = Faxi.type.title) {
    val c = Faxi.colors
    Box(
        Modifier
            .size(size)
            .background(c.surfaceRaised, CircleShape)
            .border(FaxiDimens.borderWidth, c.lineStrong, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            val fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
            Text(emoji, fontSize = fontSize, lineHeight = fontSize)
        } else {
            Text(fallback, style = style, color = c.ink)
        }
    }
}

fun gradeTone(grade: Grade): Tone = when (grade) {
    Grade.Full -> Tone.Accent
    Grade.Learner -> Tone.Neutral
    Grade.Honorary -> Tone.Ok
}

/** 영수증·장부 한 줄: 왼쪽 항목(+ 설명), 오른쪽 금액 */
@Composable
fun MoneyRow(
    label: String,
    amount: Int,
    modifier: Modifier = Modifier,
    detail: String? = null,
    strong: Boolean = false,
    amountColor: Color = Faxi.colors.ink,
) {
    val t = Faxi.type
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = if (strong) t.bodyStrong else t.body, color = Faxi.colors.ink)
            if (detail != null) Text(detail, style = t.caption, color = Faxi.colors.inkMuted)
        }
        Text(
            "${won(amount)}원",
            style = if (strong) t.amount.copy(fontWeight = FontWeight.Bold) else t.amount,
            color = amountColor,
        )
    }
}

/** "18,379원" 큰 숫자. 1원 단위 그대로, 단위는 낮춘다. */
@Composable
fun BigWon(amount: Int, color: Color = Faxi.colors.ink) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Text(won(amount), style = Faxi.type.amountLarge, color = color)
        Text(" 원", style = Faxi.type.title, color = Faxi.colors.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
    }
}
