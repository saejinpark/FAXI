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

/** 원 안에 캐릭터 이모지(있으면) 또는 글자 하나. 이모지는 원 지름의 55% 크기. */
@Composable
fun AvatarCircle(
    emoji: String?,
    fallback: String,
    size: Dp,
    style: TextStyle = Faxi.type.title,
    ring: Color = Faxi.colors.lineStrong,
) {
    val c = Faxi.colors
    Box(
        Modifier
            .size(size)
            .background(c.surfaceRaised, CircleShape)
            .border(FaxiDimens.borderWidth, ring, CircleShape)
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
        // 좁은 폰 화면에서는 항목 쪽이 줄을 바꾸고 금액은 늘 한 줄로 남는다
        Row(
            Modifier.weight(1f, fill = false).padding(end = FaxiDimens.space4),
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
            maxLines = 1,
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
        Text(" 원", style = Faxi.type.title, color = Faxi.colors.inkMuted, modifier = Modifier.padding(bottom = FaxiDimens.space2))
    }
}

/** 1원 단위 금액 글자: 18379 → "18,379". 반올림하지 않는다. 차 앱 data.won()과 같은 모양. */
internal fun won(value: Int): String = "%,d".format(value)
