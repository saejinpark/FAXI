package com.geppetto.faxi.phone

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.ui.components.AvatarCircle
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.GearIcon
import com.geppetto.faxi.ui.components.MeterIcon
import com.geppetto.faxi.ui.components.MoneyRow
import com.geppetto.faxi.ui.components.ReceiptIcon
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.SwapIcon
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import kotlinx.coroutines.delay

/** 폰 앱 아래 탭 넷. 손님이 차에 탈 때 여는 탑승이 첫 탭이다. */
enum class PhoneTab(val label: String, val icon: @Composable (Color, Dp) -> Unit) {
    Ride("탑승", { c, s -> MeterIcon(c, s) }),
    Receipts("영수증", { c, s -> ReceiptIcon(c, s) }),
    Balance("정산", { c, s -> SwapIcon(c, s) }),
    Settings("설정", { c, s -> GearIcon(c, s) }),
}

/**
 * 등록한 뒤의 폰 앱: 위에 탭 화면, 아래 탭 막대. 탭 상태는 PhoneApp이 들고 있다(영수증을 찍으면 영수증 탭으로 옮기려고).
 * - 탑승: 탑승 코드와 기사님께 한마디. 차에 타서 여는 화면.
 * - 영수증: 영수증 QR 찍기, 방금 찍은 영수증의 기사 후기, 내 영수증 목록.
 * - 정산: 마지막 영수증 기준 받을 돈·갚을 돈.
 * - 설정: 내 정보, 후기 묻기, 차와 연결, 등록 해제.
 */
@Composable
internal fun PhoneMain(
    me: FaxiInvite,
    store: PhoneStore,
    tab: PhoneTab,
    onTab: (PhoneTab) -> Unit,
    onScanReceipt: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val sender = remember { MessageSender(context.applicationContext) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                PhoneTab.Ride -> RideTab(me, store, sender, onReview = { onTab(PhoneTab.Receipts) })
                PhoneTab.Receipts -> ReceiptsTab(me, store, sender, onScanReceipt, onNotice)
                PhoneTab.Balance -> BalanceTab(store)
                PhoneTab.Settings -> SettingsTab(me, store)
            }
        }
        BottomTabs(tab, reviewWaiting = store.pendingReview != null, onTab = onTab)
    }
}

/** 아래 탭 막대. 고른 탭은 아이콘 뒤에 둥근 바탕 + 굵은 글자. 후기를 기다리는 영수증이 있으면 영수증 탭에 점. */
@Composable
private fun BottomTabs(current: PhoneTab, reviewWaiting: Boolean, onTab: (PhoneTab) -> Unit) {
    val c = Faxi.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(FaxiDimens.borderWidth).background(c.line))
        Row(Modifier.fillMaxWidth().padding(vertical = FaxiDimens.space2)) {
            PhoneTab.entries.forEach { t ->
                val on = t == current
                val dot = t == PhoneTab.Receipts && reviewWaiting
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = FaxiDimens.targetMin)
                        .clickable(role = Role.Tab) { onTab(t) }
                        .semantics {
                            selected = on
                            if (dot) contentDescription = "${t.label}, 후기를 기다리는 영수증 있음"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1, Alignment.CenterVertically),
                ) {
                    Box {
                        Box(
                            Modifier
                                .background(if (on) c.surfaceRaised else Color.Transparent, RoundedCornerShape(50))
                                .padding(horizontal = FaxiDimens.space4, vertical = FaxiDimens.space1),
                        ) {
                            t.icon(if (on) c.accentInk else c.inkMuted, FaxiDimens.iconMd)
                        }
                        if (dot) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp).size(10.dp).background(c.accent, CircleShape))
                        }
                    }
                    Text(t.label, style = Faxi.type.caption, color = if (on) c.ink else c.inkMuted)
                }
            }
        }
    }
}

/** 탑승: 탑승 코드(30초마다) + 기사님께 한마디. 후기를 기다리는 영수증이 있으면 맨 위에 한 줄로 알린다. */
@Composable
private fun RideTab(me: FaxiInvite, store: PhoneStore, sender: MessageSender, onReview: () -> Unit) {
    val c = Faxi.colors
    val secret = remember(me) { Base32.decode(me.secret) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis() / 1000
        }
    }
    val code = secret?.let { Totp.code(it, now) } ?: "----"
    val left = Totp.secondsLeft(now)

    PhonePage("탑승") {
        store.pendingReview?.let { t ->
            FaxiCard {
                Text("${t.driver.substringBefore(" · ")} 기사님 후기를 기다리고 있어요.", style = Faxi.type.bodyStrong, color = c.ink)
                FaxiSecondaryButton("후기 쓰기", onClick = onReview, modifier = Modifier.fillMaxWidth())
            }
        }
        FaxiCard {
            SectionTitle("${me.name} 탑승 코드")
            Text(
                code.toCharArray().joinToString(" "),
                style = Faxi.type.amountLarge,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "탑승 코드 ${code.toCharArray().joinToString(" ")}" },
            )
            LinearProgressIndicator(
                progress = { left / Totp.STEP_SECONDS.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.surfaceRaised,
            )
            Text("${left}초 뒤에 바뀌어요. 차 화면에 이 숫자를 눌러 주세요.", style = Faxi.type.caption, color = c.inkMuted)
        }
        MessageCard(me, sender)
    }
}

/** 영수증: 찍기 + (방금 찍은 영수증의) 기사 후기 + 목록. 주 버튼은 하나: 후기 카드가 있으면 후기 보내기. */
@Composable
private fun ReceiptsTab(me: FaxiInvite, store: PhoneStore, sender: MessageSender, onScan: () -> Unit, onNotice: (String) -> Unit) {
    val c = Faxi.colors
    val pending = store.pendingReview
    PhonePage("영수증") {
        if (pending == null) {
            FaxiPrimaryButton("영수증 QR 찍기", onClick = onScan, modifier = Modifier.fillMaxWidth())
        } else {
            ReviewCard(me, pending, sender, onDone = { text ->
                store.markReviewed(pending.id)
                onNotice(text)
            })
            FaxiSecondaryButton("영수증 QR 찍기", onClick = onScan, modifier = Modifier.fillMaxWidth())
        }
        if (store.receipts.isEmpty()) {
            FaxiCard {
                Text("아직 저장한 영수증이 없어요.", style = Faxi.type.bodyStrong, color = c.ink)
                Text("내릴 때 차 화면의 영수증 QR을 찍으면 여기에 쌓여요.", style = Faxi.type.body, color = c.inkMuted)
            }
        } else {
            FaxiCard {
                SectionTitle("내 영수증 ${store.receipts.size}장")
                store.receipts.forEach { ReceiptItem(it) }
            }
        }
    }
}

/**
 * 정산: 가장 최근 영수증을 찍은 순간의 사본. 폰은 차 장부를 바꾸지 못한다(보냈다고 알리기는 차 화면 장부에서).
 * 맨 위에 받을 돈·갚을 돈 합계를 크게, 그 아래 사람별.
 */
@Composable
private fun BalanceTab(store: PhoneStore) {
    val c = Faxi.colors
    val latest = store.receipts.firstOrNull()
    PhonePage("정산") {
        if (latest == null) {
            FaxiCard {
                Text("영수증을 찍으면 받을 돈과 갚을 돈이 보여요.", style = Faxi.type.body, color = c.ink)
            }
            return@PhonePage
        }
        val owe = latest.balances.filter { !it.owedToMe }
        val get = latest.balances.filter { it.owedToMe }
        // 합계 두 칸. 폰은 좁아서 큰 금액(BigWon) 대신 금액 글자로 한 줄에 둔다
        FaxiCard {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Total("갚을 돈", owe.sumOf { it.amount }, Modifier.weight(1f))
                Box(Modifier.width(FaxiDimens.borderWidth).fillMaxHeight().background(c.line))
                Total("받을 돈", get.sumOf { it.amount }, Modifier.weight(1f).padding(start = FaxiDimens.space4))
            }
        }
        FaxiCard {
            SectionTitle("사람별")
            if (latest.balances.isEmpty()) Text("주고받을 돈이 없어요.", style = Faxi.type.body, color = c.ink)
            latest.balances.forEach { b ->
                // 분쟁 꼬리표가 어느 줄 것인지 헷갈리지 않게 그 줄과 한 묶음으로 붙인다
                Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
                    MoneyRow(if (b.owedToMe) "${b.other}에게 받을 돈" else "${b.other}에게 갚을 돈", b.amount)
                    if (b.disputed) StatusTag("분쟁 중", Tone.Danger) { WarningIcon(it, FaxiDimens.iconMd) }
                }
            }
        }
        Text(
            "${when_(latest.endedAt)} 영수증을 찍은 때 기준이에요. 보냈다고 알리거나 확인하는 건 차 화면 장부에서 해요.",
            style = Faxi.type.caption, color = c.inkMuted,
        )
    }
}

@Composable
private fun Total(label: String, amount: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
        Text(label, style = Faxi.type.body, color = Faxi.colors.inkMuted)
        Text("%,d원".format(amount), style = Faxi.type.amount.copy(fontWeight = Faxi.type.headline.fontWeight), color = Faxi.colors.ink)
    }
}

/** 설정: 내 정보, 후기 묻기, 차와 연결(근처 기기 권한), 등록 해제(한 번 더 묻는다), 버전 */
@Composable
private fun SettingsTab(me: FaxiInvite, store: PhoneStore) {
    val c = Faxi.colors
    val t = Faxi.type
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }
    PhonePage("설정") {
        FaxiCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
                AvatarCircle(null, me.callsign.take(1), FaxiDimens.targetMin, t.title, c.family(me.callsign))
                Column {
                    Text(me.name, style = t.headline, color = c.ink)
                    Text(me.callsign, style = t.bodyStrong, color = c.family(me.callsign))
                    Text("가족 ${me.familyId}", style = t.caption, color = c.inkMuted)
                }
            }
        }
        FaxiCard {
            SectionTitle("기사 후기")
            Text("영수증을 찍으면 기사님 후기를 물어봐요. 후기는 다음 운행 때 차가 기사님께 읽어 줘요.", style = t.body, color = c.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                listOf(true to "묻기", false to "묻지 않기").forEach { (value, label) ->
                    SelectTile(selected = store.askReview == value, onClick = { store.updateAskReview(value) }, modifier = Modifier.weight(1f)) {
                        Text(label, style = t.label, color = c.ink)
                    }
                }
            }
        }
        FaxiCard {
            SectionTitle("차와 연결")
            Text("한마디와 후기는 차와 같은 Wi-Fi(차 핫스팟)에서 차로 바로 보내요. 인터넷이나 서버를 거치지 않아요.", style = t.body, color = c.inkMuted)
            if (Build.VERSION.SDK_INT >= 37) {
                val granted = ContextCompat.checkSelfPermission(context, LocalNetworkPermission) == PackageManager.PERMISSION_GRANTED
                StatusTag(if (granted) "근처 기기 권한 허용됨" else "근처 기기 권한 아직 안 받음", if (granted) Tone.Ok else Tone.Neutral)
            }
        }
        FaxiCard {
            SectionTitle("등록 해제")
            Text("이 폰에서 가족 등록과 저장한 영수증을 지워요. 차의 가족 장부는 그대로예요.", style = t.body, color = c.inkMuted)
            FaxiSecondaryButton("등록 해제", onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth())
        }
        Text("FAXI ${appVersion()}", style = t.caption, color = c.inkMuted)
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(FaxiDimens.radiusLg),
            title = { Text("등록을 해제할까요?", style = t.title, color = c.ink) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
                    WarningIcon(c.danger, FaxiDimens.iconMd)
                    Text("저장한 영수증 ${store.receipts.size}장도 지워져요. 다시 쓰려면 차 화면 초대 QR을 다시 찍어야 해요.", style = t.body, color = c.ink)
                }
            },
            confirmButton = { FaxiPrimaryButton("해제", onClick = { confirmLeave = false; store.unregister() }) },
            dismissButton = { FaxiSecondaryButton("취소", onClick = { confirmLeave = false }) },
        )
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
}
