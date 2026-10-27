package com.geppetto.faxi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Member
import com.geppetto.faxi.data.avatarOf
import com.geppetto.faxi.ui.theme.Faxi

/* 가족 모델(Member, Grade)에 기대는 표시. 모델이 없는 공통 카드·꼬리표는 :design의 Cards.kt. */

/**
 * 사진 자리. 폰 → 차 사진 전송이 정해지기 전까지는 고른 캐릭터를,
 * 캐릭터도 없으면 호출명 첫 글자를 보여 준다.
 */
@Composable
fun Avatar(member: Member, size: Dp, style: TextStyle = Faxi.type.title) {
    // 테두리는 그 사람의 가족 색: 누구인지 색으로도 한 번 더 알린다(이름·호출명과 함께)
    AvatarCircle(avatarOf(member.avatar)?.emoji, member.callsign.take(1), size, style, Faxi.colors.family(member.callsign))
}

fun gradeTone(grade: Grade): Tone = when (grade) {
    Grade.Full -> Tone.Accent
    Grade.Learner -> Tone.Neutral
    Grade.Honorary -> Tone.Ok
}
