package com.geppetto.faxi.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Gear
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.boarding.BoardingScreen
import com.geppetto.faxi.ui.components.FaxiMark
import com.geppetto.faxi.ui.components.BadgeIcon
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.LedgerIcon
import com.geppetto.faxi.ui.components.PersonIcon
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.driving.DrivingScreen
import com.geppetto.faxi.ui.ledger.LedgerScreen
import com.geppetto.faxi.ui.receipt.ArrivalScreen
import com.geppetto.faxi.ui.roster.RosterScreen
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/**
 * 탭은 셋. 영수증은 하차 때 도착 화면으로 뜨고, 지난 영수증·정산은 장부 안에서 연다.
 */
enum class Tab(val label: String, val icon: @Composable (Color, Dp) -> Unit) {
    Board("운행", { c, s -> PersonIcon(c, s) }),
    Ledger("장부", { c, s -> LedgerIcon(c, s) }),
    Roster("기사", { c, s -> BadgeIcon(c, s) }),
}

/**
 * 앱 전체 틀. 내비게이션 라이브러리 없이 탭 하나를 rememberSaveable로 들고 있다.
 * - 주행 중(Gear.Driving): 사이드바 없이 운행 중 화면만. 운전자가 만질 곳이 없다.
 * - 하차 직후(store.arrival): 사이드바 없이 도착 화면(영수증 공개 + 후기). 닫으면 탑승으로.
 * - 주차(Gear.Parked): 왼쪽 사이드바 + 탭 화면. 화면 전환은 즉시(애니메이션 없음).
 * demo = true 면 운행 중 화면에 시연용 하차 버튼이 나온다(폰·에뮬레이터 전용).
 */
@Composable
fun AppShell(store: FaxiStore, speak: (String) -> Unit, demo: Boolean) {
    var tab by rememberSaveable { mutableStateOf(Tab.Board) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Faxi.colors.bg)
            .safeDrawingPadding(),
    ) {
        if (store.gear == Gear.Driving) {
            DrivingScreen(
                store,
                onDemoPark = if (demo) {
                    {
                        val r = store.endRide()
                        speak(
                            if (r.toLifetime) {
                                "도착했습니다. 오늘 요금 ${won(r.fareTotal)}원은 평생 장부에 쌓아 둘게요."
                            } else {
                                "도착했습니다. 요금은 ${won(r.billed)}원입니다."
                            },
                        )
                    }
                } else null,
            )
            return@Box
        }
        val arrival = store.arrival
        if (arrival != null) {
            ArrivalScreen(store, arrival, onClose = {
                store.dismissArrival()
                tab = Tab.Board
            })
            return@Box
        }
        Row(Modifier.fillMaxSize()) {
            Sidebar(tab, ledgerBadge = store.requests.size, onSelect = { tab = it })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    Tab.Board -> BoardingScreen(store, speak, onStarted = {})
                    Tab.Ledger -> LedgerScreen(store)
                    Tab.Roster -> RosterScreen(store)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Tab, ledgerBadge: Int, onSelect: (Tab) -> Unit) {
    val c = Faxi.colors
    Column(
        Modifier
            .width(FaxiDimens.sidebarWidth)
            .fillMaxHeight()
            .background(c.surface)
            .padding(FaxiDimens.space3),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 앱 아이콘과 같은 로고. 글자 "FAXI"는 로고 안 표시등(빈 첫 칸 + AXI)이 대신한다
        FaxiMark(FaxiDimens.controlHeight, Modifier.padding(vertical = FaxiDimens.space4))
        Tab.entries.forEach { t ->
            val on = t == current
            val ink = if (on) c.onAccent else c.ink
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FaxiDimens.controlHeight)
                    .background(if (on) c.accent else c.surface, RoundedCornerShape(FaxiDimens.radiusMd))
                    .clickable(role = Role.Tab) { onSelect(t) }
                    .semantics { selected = on }
                    .padding(vertical = FaxiDimens.space3),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1),
            ) {
                t.icon(ink, FaxiDimens.iconMd)
                Text(t.label, style = Faxi.type.label, color = ink)
                // 기사가 확인할 정산 요청이 있으면 장부 탭에 단어로 알린다
                if (t == Tab.Ledger && ledgerBadge > 0) {
                    Text("확인 ${ledgerBadge}건", style = Faxi.type.caption, color = if (on) c.onAccent else c.danger)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        StatusTag("P 주차 중", Tone.Ok)
    }
}

@FaxiPreviews
@Composable
private fun ShellPreview() = PreviewFrame {
    AppShell(FaxiStore.sample(), speak = {}, demo = true)
}
