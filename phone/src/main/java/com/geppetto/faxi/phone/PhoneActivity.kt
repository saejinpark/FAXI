package com.geppetto.faxi.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

/**
 * FAXI 폰 앱(뼈대). 화면 둘:
 * - 등록 전: 차 화면 초대 QR 찍기(준비 중) / 초대 링크 붙여넣기 / 시연 가족으로 등록
 * - 등록 뒤: 탑승 코드(30초마다 바뀌는 4자리) + 내 자격증·내 영수증 자리
 * 폰은 손님이 보는 화면이라 차 앱의 주행 중 규칙(움직임 금지)은 적용하지 않는다. 남은 초가 줄어드는 막대를 보여 준다.
 */
class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = PhoneStore(applicationContext)
        setContent { PhoneTheme { PhoneApp(store) } }
    }
}

private val TaxiYellow = Color(0xFFFFC83D)
private val OnTaxiYellow = Color(0xFF1F1600)

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
private fun PhoneApp(store: PhoneStore) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            val me = store.me
            if (me == null) RegisterScreen(store) else HomeScreen(me, onUnregister = store::unregister)
        }
    }
}

@Composable
private fun RegisterScreen(store: PhoneStore) {
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FAXI 가족 등록", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("차가 주차(P)된 상태에서, 차 화면의 내 기사 상세에 뜨는 초대 QR을 찍으면 등록돼요.", style = MaterialTheme.typography.bodyLarge)
        BigButton("초대 QR 찍기 · 준비 중", enabled = false, onClick = {})

        HorizontalDivider()
        Text("초대 링크 붙여넣기", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = link,
            onValueChange = { link = it; error = false },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("faxi://join?...") },
            isError = error,
            supportingText = { if (error) Text("FAXI 초대 링크가 아니에요.") },
        )
        OutlinedButton(
            onClick = { error = !store.register(link) },
            enabled = link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("이 링크로 등록") }

        HorizontalDivider()
        Text("시연용: 시연 가족으로 등록", style = MaterialTheme.typography.titleMedium)
        Text("차 앱 시연 데이터와 같은 열쇠를 써요. 실제 가족에게는 쓰지 마세요.", style = MaterialTheme.typography.bodyMedium)
        DemoFamily.people.forEach { p ->
            OutlinedButton(
                onClick = { store.register(DemoFamily.inviteOf(p).toLink()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("${p.name} · ${p.callsign}로 등록") }
        }
    }
}

@Composable
private fun HomeScreen(me: FaxiInvite, onUnregister: () -> Unit) {
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
            LinearProgressIndicator(
                progress = { left / Totp.STEP_SECONDS.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${left}초 뒤에 바뀌어요 · 차 화면에 이 숫자를 눌러 주세요", style = MaterialTheme.typography.bodyMedium)
        }

        Section("내 자격증", "차 화면 기사 명단에서 볼 수 있어요. 사진 보내기는 준비 중이에요.")
        Section("내 영수증", "하차 때 차 화면의 영수증 QR을 찍으면 여기에 쌓여요. 준비 중이에요.")

        Spacer(Modifier.heightIn(min = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가족: ${me.familyId}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onUnregister) { Text("등록 해제") }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = OnTaxiYellow),
    ) { Text(text) }
}
