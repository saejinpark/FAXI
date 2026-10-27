package com.geppetto.faxi.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.qr.ReceiptTicket
import com.geppetto.faxi.ui.components.AvatarCircle
import com.geppetto.faxi.ui.components.ChevronIcon
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.MoneyRow
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import com.geppetto.faxi.ui.theme.FaxiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * FAXI 폰 앱.
 * - 등록 전: 차 화면 초대 QR 찍기 / 초대 링크 붙여넣기 / 시연 가족으로 등록
 * - 등록 뒤: 탑승 코드(30초마다) + 내 정산 현황(마지막 영수증 기준) + 내 영수증(차 영수증 QR 찍기)
 * QR을 찍거나 faxi:// 링크를 열면(딥 링크) 같은 곳(handleLink)으로 온다.
 * 폰은 손님이 보는 화면이라 차 앱의 주행 중 규칙(움직임 금지)은 적용하지 않는다.
 * 색·글자·버튼은 차 앱과 같은 :design(FaxiTheme)을 쓴다. 폰이라고 따로 만들지 않는다.
 */
class PhoneActivity : ComponentActivity() {
    /** 딥 링크로 들어온 링크. 화면이 꺼내 쓰고 비운다. */
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = PhoneStore(applicationContext)
        incoming.value = intent?.dataString
        setContent { FaxiTheme { PhoneApp(store, incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent.dataString
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("M.d HH:mm")

@Composable
private fun PhoneApp(store: PhoneStore, incoming: androidx.compose.runtime.MutableState<String?>) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf<String?>(null) }
    var pendingInvite by remember { mutableStateOf<FaxiInvite?>(null) }

    fun notice(text: String) = scope.launch { snackbar.showSnackbar(text) }

    /** QR·딥 링크로 들어온 글자 하나 처리 */
    fun handleLink(link: String) {
        FaxiInvite.parse(link)?.let { pendingInvite = it; return }
        val ticket = ReceiptTicket.parse(link)
        if (ticket == null) {
            notice("FAXI QR이 아니에요.")
            return
        }
        notice(
            when (store.save(ticket)) {
                PhoneStore.SaveResult.Saved -> "영수증을 저장했어요."
                PhoneStore.SaveResult.AlreadySaved -> "이미 저장한 영수증이에요."
                PhoneStore.SaveResult.NotRegistered -> "먼저 가족 등록을 해 주세요."
                PhoneStore.SaveResult.OtherFamily -> "다른 가족 차의 영수증이에요."
                PhoneStore.SaveResult.NotMine -> "${ticket.passenger}님의 영수증이에요. 그 폰으로 찍어 주세요."
            },
        )
    }

    LaunchedEffect(incoming.value) {
        incoming.value?.let { handleLink(it); incoming.value = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Faxi.colors.bg) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val title = scanning
            val me = store.me
            when {
                title != null -> QrScanner(
                    title = title,
                    onResult = { scanning = null; handleLink(it) },
                    onCancel = { scanning = null },
                )
                me == null -> RegisterScreen(store, onScan = { scanning = "초대 QR 찍기" }, onBadLink = { notice("FAXI 초대 링크가 아니에요.") })
                else -> HomeScreen(me, store.receipts, onScanReceipt = { scanning = "영수증 QR 찍기" }, onUnregister = store::unregister)
            }
        }
    }

    pendingInvite?.let { invite ->
        val otherFamily = store.me != null && store.me?.familyId != invite.familyId
        AlertDialog(
            onDismissRequest = { pendingInvite = null },
            containerColor = Faxi.colors.surface,
            shape = RoundedCornerShape(FaxiDimens.radiusLg),
            title = { Text("${invite.name} · ${invite.callsign}로 등록할까요?", style = Faxi.type.title, color = Faxi.colors.ink) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2)) {
                    if (otherFamily) WarningIcon(Faxi.colors.danger, FaxiDimens.iconMd)
                    Text(
                        if (otherFamily) "다른 가족 차예요. 등록하면 지금 가족의 영수증은 지워져요." else "이 폰에 ${invite.name}님의 탑승 코드가 떠요.",
                        style = Faxi.type.body,
                        color = Faxi.colors.ink,
                    )
                }
            },
            confirmButton = { FaxiPrimaryButton("등록", onClick = { store.register(invite); pendingInvite = null }) },
            dismissButton = { FaxiSecondaryButton("취소", onClick = { pendingInvite = null }) },
        )
    }
}

/** 화면 바깥 틀: 세로 스크롤, 가장자리 space-6(폰은 좁아서 차의 space-8보다 한 단계 좁게) */
@Composable
private fun PhonePage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FaxiDimens.space6),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
    ) { content() }
}

@Composable
private fun RegisterScreen(store: PhoneStore, onScan: () -> Unit, onBadLink: () -> Unit) {
    val c = Faxi.colors
    var link by remember { mutableStateOf("") }
    PhonePage {
        Text("FAXI 가족 등록", style = Faxi.type.headline, color = c.ink)
        FaxiCard {
            Text("차가 주차(P)된 상태에서 차 화면 기사 상세의 \"초대 QR 보이기\"를 누르고 찍어 주세요.", style = Faxi.type.body, color = c.ink)
            FaxiPrimaryButton("초대 QR 찍기", onClick = onScan, modifier = Modifier.fillMaxWidth())
        }
        FaxiCard {
            SectionTitle("초대 링크 붙여넣기")
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = FaxiDimens.targetMin),
                textStyle = Faxi.type.body,
                placeholder = { Text("faxi://join?...", style = Faxi.type.body, color = c.inkMuted) },
                shape = RoundedCornerShape(FaxiDimens.radiusSm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.lineStrong, cursorColor = c.accentInk,
                ),
            )
            FaxiSecondaryButton(
                "이 링크로 등록",
                onClick = { if (!store.register(link)) onBadLink() },
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FaxiCard {
            SectionTitle("시연용: 시연 가족으로 등록")
            Text("차 앱 시연 데이터와 같은 열쇠를 써요. 실제 가족에게는 쓰지 마세요.", style = Faxi.type.caption, color = c.inkMuted)
            DemoFamily.people.forEach { p ->
                FaxiSecondaryButton(
                    "${p.name} · ${p.callsign}로 등록",
                    onClick = { store.register(DemoFamily.inviteOf(p)) },
                    modifier = Modifier.fillMaxWidth(),
                    leading = { AvatarCircle(null, p.callsign.take(1), FaxiDimens.iconLg, Faxi.type.label, c.family(p.callsign)) },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    me: FaxiInvite,
    receipts: List<ReceiptTicket>,
    onScanReceipt: () -> Unit,
    onUnregister: () -> Unit,
) {
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

    PhonePage {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
            AvatarCircle(null, me.callsign.take(1), FaxiDimens.targetMin, Faxi.type.title, c.family(me.callsign))
            Column {
                Text(me.name, style = Faxi.type.headline, color = c.ink)
                Text(me.callsign, style = Faxi.type.bodyStrong, color = c.family(me.callsign))
            }
        }

        FaxiCard {
            SectionTitle("탑승 코드")
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

        FaxiPrimaryButton("영수증 QR 찍기", onClick = onScanReceipt, modifier = Modifier.fillMaxWidth())

        Balances(receipts.firstOrNull())

        FaxiCard {
            SectionTitle("내 영수증")
            if (receipts.isEmpty()) {
                Text("하차 때 차 화면의 영수증 QR을 찍으면 여기에 쌓여요.", style = Faxi.type.body, color = c.ink)
            }
            receipts.forEach { ReceiptItem(it) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
            Text("가족 ${me.familyId}", style = Faxi.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
            FaxiSecondaryButton("등록 해제", onClick = onUnregister)
        }
    }
}

/** 내 정산 현황: 가장 최근 영수증을 찍은 순간의 사본. 그 뒤 차에서 바뀐 건 다음 영수증 때 들어온다. */
@Composable
private fun Balances(latest: ReceiptTicket?) {
    val c = Faxi.colors
    FaxiCard {
        SectionTitle("내 정산 현황")
        if (latest == null) {
            Text("영수증을 찍으면 받을 돈·갚을 돈이 보여요.", style = Faxi.type.body, color = c.ink)
            return@FaxiCard
        }
        if (latest.balances.isEmpty()) Text("주고받을 돈이 없어요.", style = Faxi.type.body, color = c.ink)
        latest.balances.forEach { b ->
            // 분쟁 꼬리표가 어느 줄 것인지 헷갈리지 않게 그 줄과 한 묶음으로 붙인다
            Column(verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
                MoneyRow(if (b.owedToMe) "${b.other}에게 받을 돈" else "${b.other}에게 갚을 돈", b.amount)
                if (b.disputed) StatusTag("분쟁 중", Tone.Danger) { WarningIcon(it, FaxiDimens.iconMd) }
            }
        }
        Text("${when_(latest.endedAt)} 영수증 기준이에요. 차에서 바뀐 건 다음 영수증을 찍을 때 반영돼요.", style = Faxi.type.caption, color = c.inkMuted)
    }
}

/** 영수증 한 장. 누르면 항목별 요금이 펼쳐진다. */
@Composable
private fun ReceiptItem(t: ReceiptTicket) {
    val c = Faxi.colors
    var open by remember(t.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FaxiDimens.targetMin)
            .background(c.surfaceRaised, RoundedCornerShape(FaxiDimens.radiusMd))
            .clickable(role = Role.Button, onClickLabel = if (open) "접기" else "항목 펼치기") { open = !open }
            .padding(FaxiDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
    ) {
        // 폰은 좁아서 이름·시각·금액을 한 줄에 몰지 않고 세로로 쌓는다
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaxiDimens.space1)) {
                Text("${t.driver} 택시", style = Faxi.type.bodyStrong, color = c.ink)
                Text("${when_(t.endedAt)} · ${t.route}", style = Faxi.type.caption, color = c.inkMuted)
                Text(
                    "${"%,d".format(if (t.toLifetime) t.fareTotal else t.billed)}원",
                    style = Faxi.type.amount.copy(fontWeight = Faxi.type.bodyStrong.fontWeight),
                    color = c.ink,
                )
            }
            ChevronIcon(c.inkMuted, FaxiDimens.iconMd)
        }
        if (t.toLifetime) StatusTag("평생 장부", Tone.Ok)
        if (open) {
            Divider()
            t.lines.forEach { l -> MoneyRow(l.label, l.amount, detail = l.detail, amountColor = if (l.amount < 0) c.ok else c.ink) }
            Divider()
            MoneyRow("이번 요금", t.fareTotal, strong = true)
            if (t.carriedOver > 0) MoneyRow("지난 미수금", t.carriedOver)
            if (t.lateInterest > 0) MoneyRow("연체 이자", t.lateInterest)
            if (t.offset > 0) MoneyRow("상계", -t.offset, amountColor = c.ok)
        }
    }
}

private fun when_(iso: String): String = runCatching { LocalDateTime.parse(iso).format(TimeFormat) }.getOrDefault(iso)
