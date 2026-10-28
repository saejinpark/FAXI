package com.geppetto.faxi.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.qr.ReceiptTicket
import com.geppetto.faxi.ui.components.AvatarCircle
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import com.geppetto.faxi.ui.theme.FaxiTheme
import kotlinx.coroutines.launch

/**
 * FAXI 폰 앱.
 * - 등록 전: 차 화면 초대 QR 찍기 / 초대 링크 붙여넣기 / 시연 가족으로 등록
 * - 등록 뒤: 아래 탭 넷(PhoneMain). 탑승(코드·한마디), 영수증(찍기·후기·목록), 정산, 설정
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


@Composable
private fun PhoneApp(store: PhoneStore, incoming: androidx.compose.runtime.MutableState<String?>) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf<String?>(null) }
    var pendingInvite by remember { mutableStateOf<FaxiInvite?>(null) }
    var tab by rememberSaveable { mutableStateOf(PhoneTab.Ride) }

    fun notice(text: String) = scope.launch { snackbar.showSnackbar(text) }

    /** QR·딥 링크로 들어온 글자 하나 처리 */
    fun handleLink(link: String) {
        FaxiInvite.parse(link)?.let { pendingInvite = it; return }
        val ticket = ReceiptTicket.parse(link)
        if (ticket == null) {
            notice("FAXI QR이 아니에요.")
            return
        }
        val result = store.save(ticket)
        // 찍은 영수증(과 후기 카드)이 보이게 영수증 탭으로
        if (result == PhoneStore.SaveResult.Saved || result == PhoneStore.SaveResult.AlreadySaved) tab = PhoneTab.Receipts
        notice(
            when (result) {
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
                else -> PhoneMain(
                    me, store, tab,
                    onTab = { tab = it },
                    onScanReceipt = { scanning = "영수증 QR 찍기" },
                    onNotice = { notice(it) },
                )
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

/** 화면 바깥 틀: 맨 위 제목 + 세로 스크롤, 가장자리 space-6(폰은 좁아서 차의 space-8보다 한 단계 좁게) */
@Composable
internal fun PhonePage(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FaxiDimens.space6),
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space6),
    ) {
        if (title != null) Text(title, style = Faxi.type.headline, color = Faxi.colors.ink)
        content()
    }
}

@Composable
private fun RegisterScreen(store: PhoneStore, onScan: () -> Unit, onBadLink: () -> Unit) {
    val c = Faxi.colors
    var link by remember { mutableStateOf("") }
    PhonePage("FAXI 가족 등록") {
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
