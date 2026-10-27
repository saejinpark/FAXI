package com.geppetto.faxi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import com.geppetto.faxi.design.R

/**
 * 화면 안 FAXI 로고: 앱 아이콘과 같은 그림(산호 F + 남색 두 칸 + A·X·I)을 둥근 네모로.
 * 앱 아이콘 파일(108 격자)을 그대로 쓰고, 기기 마스크가 보여 주는 가운데 72(18~90)만 잘라 낸다.
 * 산호·남색은 테마와 상관없는 브랜드 색이라 야간·주간 모두 같은 모양이다.
 * 야간 테마의 surface가 로고의 남색과 같아서, 테두리(line-strong)로 둥근 네모의 윤곽을 잡아 준다.
 */
@Composable
fun FaxiMark(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(BrandCoral)
            .semantics { contentDescription = "FAXI" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * (108f / 72f)),
        )
        // 작은 로고(32dp)에서는 테두리가 그림을 먹지 않게 가늘게
        Box(Modifier.matchParentSize().border(if (size < FaxiDimens.iconLg) 1.dp else FaxiDimens.borderWidth, Faxi.colors.lineStrong, shape))
    }
}

/** 앱 아이콘 바탕(ic_launcher_background)과 같은 값. 화면 토큰(accent)이 아니라 로고 전용. */
private val BrandCoral = Color(0xFFF2845F)
