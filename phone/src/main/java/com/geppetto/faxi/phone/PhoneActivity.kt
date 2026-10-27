package com.geppetto.faxi.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.qr.ReceiptTicket
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
 */
class PhoneActivity : ComponentActivity() {
    /** 딥 링크로 들어온 링크. 화면이 꺼내 쓰고 비운다. */
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = PhoneStore(applicationContext)
        incoming.value = intent?.dataString
        setContent { PhoneTheme { PhoneApp(store, incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent.dataString
    }
}

private val TaxiYellow = Color(0xFFFFC83D)
private val OnTaxiYellow = Color(0xFF1F1600)
private val TimeFormat = DateTimeFormatter.ofPattern("M.d HH:mm")

@Composable
private fun PhoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(primary = TaxiYellow, onPrimary = OnTaxiYellow, background = Color(0xFF0E0F12), surface = Color(0xFF17191E))
    } else {
        // 밝은 바탕에서 노란 글자는 대비가 모자라 글자 강조는 진한 황토색(차 앱 accentInk와 같은 값)
        lightColorScheme(primary = Color(0xFF7A5500), onPrimary = Color.White, secondary = TaxiYellow, onSecondary = OnTaxiYellow)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
        AlertDialog(
            onDismissRequest = { pendingInvite = null },
            title = { Text("${invite.name} · ${invite.callsign}로 등록할까요?") },
            text = {
                Text(
                    if (store.me != null && store.me?.familyId != invite.familyId) {
                        "다른 가족 차예요. 등록하면 지금 가족의 영수증은 지워져요."
                    } else {
                        "이 폰에 ${invite.name}님의 탑승 코드가 떠요."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { store.register(invite); pendingInvite = null }) { Text("등록") } },
            dismissButton = { TextButton(onClick = { pendingInvite = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun RegisterScreen(store: PhoneStore, onScan: () -> Unit, onBadLink: () -> Unit) {
    var link by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FAXI 가족 등록", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("차가 주차(P)된 상태에서, 차 화면 기사 상세의 \"초대 QR 보이기\"를 누르고 찍어 주세요.", style = MaterialTheme.typography.bodyLarge)
        BigButton("초대 QR 찍기", onClick = onScan)

        HorizontalDivider()
        Text("초대 링크 붙여넣기", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("faxi://join?...") },
        )
        OutlinedButton(
            onClick = { if (!store.register(link)) onBadLink() },
            enabled = link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("이 링크로 등록") }

        HorizontalDivider()
        Text("시연용: 시연 가족으로 등록", style = MaterialTheme.typography.titleMedium)
        Text("차 앱 시연 데이터와 같은 열쇠를 써요. 실제 가족에게는 쓰지 마세요.", style = MaterialTheme.typography.bodyMedium)
        DemoFamily.people.forEach { p ->
            OutlinedButton(
                onClick = { store.register(DemoFamily.inviteOf(p)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("${p.name} · ${p.callsign}로 등록") }
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("${me.name} · ${me.callsign}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("탑승 코드", style = MaterialTheme.typography.titleMedium)
            Text(
                code.toCharArray().joinToString(" "),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "탑승 코드 ${code.toCharArray().joinToString(" ")}" },
            )
            LinearProgressIndicator(progress = { left / Totp.STEP_SECONDS.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("${left}초 뒤에 바뀌어요 · 차 화면에 이 숫자를 눌러 주세요", style = MaterialTheme.typography.bodyMedium)
        }

        BigButton("영수증 QR 찍기", onClick = onScanReceipt)

        Balances(receipts.firstOrNull())

        Text("내 영수증", style = MaterialTheme.typography.titleMedium)
        if (receipts.isEmpty()) {
            Text("하차 때 차 화면의 영수증 QR을 찍으면 여기에 쌓여요.", style = MaterialTheme.typography.bodyMedium)
        }
        receipts.forEach { ReceiptItem(it) }

        Spacer(Modifier.heightIn(min = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가족: ${me.familyId}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onUnregister) { Text("등록 해제") }
        }
    }
}

/** 내 정산 현황: 가장 최근 영수증을 찍은 순간의 사본. 그 뒤 차에서 바뀐 건 다음 영수증 때 들어온다. */
@Composable
private fun Balances(latest: ReceiptTicket?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("내 정산 현황", style = MaterialTheme.typography.titleMedium)
        if (latest == null) {
            Text("영수증을 찍으면 받을 돈·갚을 돈이 보여요.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (latest.balances.isEmpty()) Text("주고받을 돈이 없어요.", style = MaterialTheme.typography.bodyLarge)
        latest.balances.forEach { b ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    (if (b.owedToMe) "${b.other}에게 받을 돈" else "${b.other}에게 갚을 돈") + if (b.disputed) " · 분쟁 중" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text("%,d원".format(b.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
        Text("${when_(latest.endedAt)} 영수증 기준이에요. 차에서 바뀐 건 다음 영수증을 찍을 때 반영돼요.", style = MaterialTheme.typography.bodySmall)
    }
}

/** 영수증 한 장. 누르면 항목별 요금이 펼쳐진다. */
@Composable
private fun ReceiptItem(t: ReceiptTicket) {
    var open by remember(t.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .clickable { open = !open }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("${when_(t.endedAt)} · ${t.route}", style = MaterialTheme.typography.titleSmall)
                Text("${t.driver} 택시", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (t.toLifetime) "평생 장부 %,d원".format(t.fareTotal) else "%,d원".format(t.billed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (open) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            t.lines.forEach { l -> Line(l.label + (l.detail?.let { " ($it)" } ?: ""), l.amount) }
            Line("이번 요금", t.fareTotal)
            if (t.carriedOver > 0) Line("지난 미수금", t.carriedOver)
            if (t.lateInterest > 0) Line("연체 이자", t.lateInterest)
            if (t.offset > 0) Line("상계", -t.offset)
        }
    }
}

@Composable
private fun Line(label: String, amount: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("%,d원".format(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun when_(iso: String): String = runCatching { LocalDateTime.parse(iso).format(TimeFormat) }.getOrDefault(iso)

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = OnTaxiYellow),
    ) { Text(text) }
}
