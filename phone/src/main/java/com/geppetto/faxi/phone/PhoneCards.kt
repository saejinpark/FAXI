package com.geppetto.faxi.phone

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.message.DriverTag
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.RideMessage
import com.geppetto.faxi.qr.ReceiptTicket
import com.geppetto.faxi.ui.components.ChevronIcon
import com.geppetto.faxi.ui.components.Divider
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.MessageIcon
import com.geppetto.faxi.ui.components.MoneyRow
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.SelectTile
import com.geppetto.faxi.ui.components.StarIcon
import com.geppetto.faxi.ui.components.StatusTag
import com.geppetto.faxi.ui.components.Tone
import com.geppetto.faxi.ui.components.WarningIcon
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** API 37의 Manifest.permission.ACCESS_LOCAL_NETWORK. compileSdk 37이지만 글자로 둬서 옛 기기에서도 읽기 쉽게 */
internal const val LocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
private val TimeFormat = DateTimeFormatter.ofPattern("M.d HH:mm")

/**
 * Android 17부터 같은 Wi-Fi의 차와 이야기하려면 "근처 기기" 권한이 필요하다. 처음 보낼 때 한 번 묻고, 허용하면 하려던 일을 한다.
 * 돌려주는 함수에 보낼 일을 넘긴다.
 */
@Composable
internal fun rememberNearbyGate(onDenied: () -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = waiting
        waiting = null
        if (granted && action != null) action() else onDenied()
    }
    return { action ->
        val needsAsk = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, LocalNetworkPermission) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            waiting = action
            ask.launch(LocalNetworkPermission)
        } else {
            action()
        }
    }
}

/** 보내기 결과 한 줄. 자리를 미리 잡아 둬서 답이 와도 위 버튼이 밀리지 않는다. */
@Composable
internal fun StatusLine(text: String, failed: Boolean) {
    val c = Faxi.colors
    Row(
        Modifier.heightIn(min = FaxiDimens.iconLg),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed) WarningIcon(c.danger, FaxiDimens.iconMd)
        Text(text, style = Faxi.type.caption, color = if (failed) c.ink else c.inkMuted)
    }
}

internal fun MessageReply?.problem(): String? = when (this) {
    MessageReply.Rejected -> "차가 받지 않았어요. 폰 시각이 맞는지 확인해 주세요."
    null -> "차를 찾지 못했어요. 차와 같은 Wi-Fi인지 확인해 주세요."
    else -> null
}

/** 기사님께 한마디: 정해진 문장 중 하나를 눌러 운행 중인 차 화면에 띄운다(같은 Wi-Fi). */
@Composable
internal fun MessageCard(me: FaxiInvite, sender: MessageSender) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("타고 가는 동안 누르면 기사님 화면에 떠요.") }
    var failed by remember { mutableStateOf(false) }
    val gate = rememberNearbyGate { failed = true; status = "차에 보내려면 근처 기기 권한을 허용해 주세요." }

    fun send(msg: RideMessage) {
        sending = true
        status = "보내는 중이에요."
        failed = false
        scope.launch {
            val reply = sender.send(me, msg)
            failed = reply != MessageReply.Shown
            status = reply.problem() ?: when (reply) {
                MessageReply.NotRiding -> "지금 이 차에 타고 있지 않아요. 탑승 코드부터 눌러 주세요."
                MessageReply.TooSoon -> "방금 보냈어요. 10초 뒤에 다시 보내 주세요."
                else -> "\"${msg.text}\" 기사님 화면에 떴어요."
            }
            sending = false
        }
    }

    FaxiCard {
        SectionTitle("기사님께 한마디")
        RideMessage.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
                pair.forEach { msg ->
                    MessageTile(msg, enabled = !sending, modifier = Modifier.weight(1f)) { gate { send(msg) } }
                }
            }
        }
        StatusLine(status, failed)
    }
}

/**
 * 기사 후기: 가장 최근에 찍은 영수증의 기사에게 별점과 태그를 보낸다(같은 Wi-Fi로 차에, 서명해서).
 * 차는 다음에 그 기사를 고를 때 소리 내어 읽어 준다. 보내거나 건너뛰면 카드가 사라진다.
 */
@Composable
internal fun ReviewCard(me: FaxiInvite, ticket: ReceiptTicket, sender: MessageSender, onDone: (String) -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    val scope = rememberCoroutineScope()
    var stars by remember(ticket.id) { mutableIntStateOf(0) }
    var tags by remember(ticket.id) { mutableStateOf(emptyList<DriverTag>()) }
    var sending by remember { mutableStateOf(false) }
    var status by remember(ticket.id) { mutableStateOf("기사님은 다음 운행 때 차가 읽어 주는 후기로 들어요.") }
    var failed by remember { mutableStateOf(false) }
    val gate = rememberNearbyGate { failed = true; status = "차에 보내려면 근처 기기 권한을 허용해 주세요." }
    val driverName = ticket.driver.substringBefore(" · ")
    val brakes = ticket.lines.firstOrNull { it.label == "기사 귀책 환급" }?.detail?.filter(Char::isDigit)?.toIntOrNull() ?: 0

    fun send() {
        sending = true
        failed = false
        status = "보내는 중이에요."
        scope.launch {
            val reply = sender.sendReview(me, ticket.id, stars, tags)
            sending = false
            when (reply) {
                MessageReply.Shown -> onDone("$driverName 기사님께 후기를 보냈어요.")
                MessageReply.AlreadyReviewed -> onDone("이 운행은 이미 후기가 있어요.")
                else -> {
                    failed = true
                    status = reply.problem() ?: "차 장부에 없는 운행이에요."
                }
            }
        }
    }

    FaxiCard {
        SectionTitle("$driverName 기사 후기")
        Text(
            if (brakes > 0) "급정거가 ${brakes}번 있었어요. 괜찮으셨어요?" else "이번 운행 어떠셨어요?",
            style = t.title, color = c.ink,
        )
        // 폰은 좁아서 별 다섯 개가 폭을 나눠 쓴다
        Row(Modifier.fillMaxWidth()) {
            (1..5).forEach { n ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = FaxiDimens.targetMin)
                        .clickable(role = Role.Button) { stars = n }
                        .semantics {
                            contentDescription = "별 ${n}개"
                            selected = n == stars
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    StarIcon(if (n <= stars) c.accentInk else c.lineStrong, FaxiDimens.iconLg, filled = n <= stars)
                }
            }
        }
        DriverTag.entries.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
                pair.forEach { tag ->
                    val on = tag in tags
                    SelectTile(
                        selected = on,
                        onClick = { tags = if (on) tags - tag else tags + tag },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(tag.text, style = t.label, color = c.ink)
                    }
                }
            }
        }
        FaxiPrimaryButton("후기 보내기", enabled = stars > 0 && !sending, onClick = { gate { send() } }, modifier = Modifier.fillMaxWidth())
        FaxiSecondaryButton("안 남길래요", enabled = !sending, onClick = { onDone("후기는 건너뛰었어요.") }, modifier = Modifier.fillMaxWidth())
        StatusLine(status, failed)
    }
}

/** 한마디 버튼: 아이콘 위, 문장 아래(폰은 좁아서 두 줄까지) */
@Composable
internal fun MessageTile(msg: RideMessage, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Faxi.colors
    val shape = RoundedCornerShape(FaxiDimens.radiusMd)
    Column(
        modifier
            .heightIn(min = FaxiDimens.controlHeight + FaxiDimens.space6)
            .background(c.surface, shape)
            .border(FaxiDimens.borderWidth, c.lineStrong, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(FaxiDimens.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FaxiDimens.space2, Alignment.CenterVertically),
    ) {
        MessageIcon(msg.code, if (enabled) c.accentInk else c.inkDisabled, FaxiDimens.iconMd)
        Text(msg.tileText(), style = Faxi.type.label, color = if (enabled) c.ink else c.inkDisabled, textAlign = TextAlign.Center)
    }
}

/** 좁은 타일에서 "주세요"가 혼자 끊기지 않게 앞 낱말에 붙인다: "천천히 / 가 주세요" */
private fun RideMessage.tileText() = text.replace(" 주세요", "\u00A0주세요")

/** 영수증 한 장. 누르면 항목별 요금이 펼쳐진다. */
@Composable
internal fun ReceiptItem(t: ReceiptTicket) {
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

internal fun when_(iso: String): String = runCatching { LocalDateTime.parse(iso).format(TimeFormat) }.getOrDefault(iso)
