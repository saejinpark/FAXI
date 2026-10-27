package com.geppetto.faxi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Member
import com.geppetto.faxi.data.avatarOf
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/**
 * FAXI 기사 자격증. 택시 조수석 앞 자격증처럼 운행이 시작되면 차 화면에 뜬다.
 * 신분증 모양: 왼쪽 가장자리 가족 색 띠, 3:4 사진 칸, 오른쪽에 이름표처럼 칸(성명·등록·운행).
 * 실제 면허증을 흉내 내지 않는다: 면허번호 칸 없음, FAXI 로고와 호출명만.
 *
 * large = true(운행 중 화면)면 주행 중에 읽히도록 칸 글자를 24sp 이상으로 키운다.
 */
@Composable
fun DriverLicenseCard(
    member: Member,
    /** 기사로 받은 평균 별점(store.driverRating). 후기가 없으면 null */
    rating: Double?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    today: LocalDate = LocalDate.now(),
) {
    val c = Faxi.colors
    val t = Faxi.type
    val shape = RoundedCornerShape(FaxiDimens.radiusLg)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(c.surface)
            .border(FaxiDimens.borderWidth, c.lineStrong, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "FAXI 기사 자격증, ${member.name}, ${member.callsign}, ${member.grade.label}, ${member.nickname}"
            },
    ) {
        // 왼쪽 띠: 그 사람의 가족 색(아빠 남색, 엄마 산호, 아이 민트, 조부모 라벤더)
        Box(Modifier.width(if (large) 20.dp else 14.dp).fillMaxHeight().background(c.family(member.callsign)))
        Column(
            Modifier.weight(1f).padding(FaxiDimens.space6),
            verticalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 발급처 표시는 글자 "FAXI" 대신 앱 아이콘과 같은 로고(사이드바와 같은 모양)
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                    FaxiMark(if (large) FaxiDimens.iconLg else FaxiDimens.iconMd)
                    Text("기사 자격증", style = if (large) t.bodyStrong else t.label, color = c.accentInk)
                }
                Text(member.callsign, style = if (large) t.title else t.label, color = c.ink)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
                verticalAlignment = Alignment.Top,
            ) {
                PhotoFrame(member, if (large) 200.dp else 120.dp)
                Column(verticalArrangement = Arrangement.spacedBy(if (large) FaxiDimens.space3 else FaxiDimens.space2)) {
                    StatusTag(member.grade.label, gradeTone(member.grade))
                    Text(member.nickname, style = if (large) t.headline else t.title, color = c.ink)
                    Field("성명", member.name, large)
                    Field("등록", member.registered.format(DateFormat), large)
                    when (member.grade) {
                        Grade.Full -> Field(
                            "운행",
                            "${won(member.drivenKm)}km · " + (rating?.let { "★ %.1f".format(it) } ?: "별점 없음"),
                            large,
                        )
                        Grade.Learner -> {
                            val dday = member.licenseEligible?.let { ChronoUnit.DAYS.between(today, it).toInt() }
                            Field("면허까지", dday?.let { "D-${won(it)}" } ?: "-", large)
                        }
                        Grade.Honorary -> Field("태운 거리", "${won(member.drivenKm)}km", large)
                    }
                }
            }
        }
    }
}

/** 이름표 한 칸: 왼쪽 작은 칸 이름, 오른쪽 값 */
@Composable
private fun Field(label: String, value: String, large: Boolean) {
    val t = Faxi.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (large) t.body else t.caption,
            color = Faxi.colors.inkMuted,
            modifier = Modifier.width(if (large) 112.dp else 96.dp),
        )
        Text(value, style = t.bodyStrong, color = Faxi.colors.ink)
    }
}

/** 3:4 증명사진 칸. 사진이 생기기 전에는 캐릭터, 캐릭터도 없으면 호출명 첫 글자. */
@Composable
private fun PhotoFrame(member: Member, width: Dp) {
    val c = Faxi.colors
    val shape = RoundedCornerShape(FaxiDimens.radiusSm)
    Box(
        Modifier
            .width(width)
            .aspectRatio(3f / 4f)
            .background(c.surfaceRaised, shape)
            .border(FaxiDimens.borderWidth, c.lineStrong, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val emoji = avatarOf(member.avatar)?.emoji
        if (emoji != null) {
            val fontSize = with(LocalDensity.current) { (width * 0.6f).toSp() }
            Text(emoji, fontSize = fontSize, lineHeight = fontSize)
        } else {
            Text(member.callsign.take(1), style = Faxi.type.display, color = c.ink)
        }
    }
}

@FaxiPreviews
@Composable
private fun LicenseCardPreview() = PreviewFrame {
    val m = com.geppetto.faxi.data.FaxiStore.sampleSnapshot(LocalDate.of(2026, 10, 27)).members
    Column(Modifier.padding(FaxiDimens.space8), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6)) {
        DriverLicenseCard(m[0], 2.5, Modifier.width(820.dp))
        DriverLicenseCard(m[2], null, Modifier.width(820.dp), large = true)
    }
}
