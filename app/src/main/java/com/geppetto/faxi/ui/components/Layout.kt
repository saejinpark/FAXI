package com.geppetto.faxi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/** 콘텐츠 영역 폭이 이 이상이면 2열, 아니면 1열 (Pleos 2분할 기준) */
val TwoColumnMin = 680.dp

/** 칸 하나. 받은 modifier를 카드 맨 바깥에 붙여야 높이 맞추기가 된다. */
typealias Cell = @Composable (Modifier) -> Unit

/** 화면을 줄 단위로 쌓는 빌더 */
class PageBuilder {
    internal val rows = mutableListOf<List<Cell>>()

    /** 한 줄 = 칸 1~2개. 2열일 때 같은 줄의 칸은 높이가 같아진다. */
    fun row(vararg cells: Cell) {
        rows += cells.toList()
    }

    /** 2열이어도 폭 전체를 쓰는 칸 */
    fun full(cell: Cell) {
        rows += listOf(cell)
    }
}

/**
 * 화면 공용 틀: 제목 줄 + 2분할 본문.
 *
 * - 2열(폭 680dp 이상): 같은 줄의 두 칸은 키 큰 쪽에 높이를 맞춘다.
 *   좌우가 따로 스크롤되지 않고 페이지 전체가 한 번에 스크롤된다.
 * - 1열: 칸을 위에서부터 차례로 쌓는다.
 * - 3열은 쓰지 않는다.
 *
 * 카드 안에 버튼이 있으면 버튼 앞에 Spacer(Modifier.weight(1f))를 둬서 짝꿍 카드와 버튼 줄을 맞춘다.
 */
@Composable
fun FaxiPage(
    title: String,
    modifier: Modifier = Modifier,
    /** 상세 화면이면 제목 왼쪽에 뒤로 버튼 */
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    content: PageBuilder.() -> Unit,
) {
    val rows = PageBuilder().apply(content).rows

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = FaxiDimens.space8, vertical = FaxiDimens.space6),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    FaxiIconButton("뒤로", onClick = onBack) { BackIcon(Faxi.colors.ink, FaxiDimens.iconMd) }
                }
                Text(
                    title,
                    style = Faxi.type.headline,
                    color = Faxi.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            trailing()
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val twoColumns = maxWidth >= TwoColumnMin
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
            ) {
                rows.forEach { cells ->
                    if (twoColumns && cells.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
                        ) {
                            cells.forEach { cell -> cell(Modifier.weight(1f).fillMaxHeight()) }
                        }
                    } else {
                        cells.forEach { cell -> cell(Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}
