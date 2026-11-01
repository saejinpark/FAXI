package com.geppetto.faxi.ui.roster

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.ga
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.components.BigWon
import com.geppetto.faxi.ui.components.DriverLicenseCard
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.theme.Faxi

/**
 * 면허 따는 날: 예비 기사가 정식 기사가 되고, 그동안 가족이 태워 준 평생 장부를 처음으로 보여 준다.
 * 차가 같은 내용을 소리 내어 읽는다(licenseLine). 기획서의 "이제 네가 기사다" 장면.
 */
@Composable
fun LicenseRevealScreen(store: FaxiStore, memberId: String, onDone: () -> Unit) {
    val c = Faxi.colors
    val t = Faxi.type
    val member = store.member(memberId)
    val ledger = store.lifetime.firstOrNull { it.learnerId == memberId }
    FaxiPage(title = "${member.name} 면허 축하해요", trailing = { FaxiPrimaryButton("확인", onClick = onDone) }) {
        row(
            { m -> DriverLicenseCard(member, store.driverRating(memberId), m) },
            { m ->
                FaxiCard(m) {
                    SectionTitle("평생 장부 공개")
                    if (ledger == null) {
                        Text("${member.name.ga()} 손님으로 탄 기록이 아직 없어요. 오늘부터 기사예요.", style = t.title, color = c.ink)
                    } else {
                        Text(
                            "${ledger.sinceYear}년부터 ${won(ledger.rides)}번, ${won(ledger.km)}km를 태워 줬어요.",
                            style = t.title, color = c.ink,
                        )
                        BigWon(ledger.amount)
                        Text(
                            "이제 ${member.name.ga()} 기사예요. 가족을 태우며 갚아 나가요. 갚을 수는 있지만, 다 갚기는 어려워요.",
                            style = t.body, color = c.inkMuted,
                        )
                    }
                }
            },
        )
    }
}

@FaxiPreviews
@Composable
private fun LicenseRevealPreview() = PreviewFrame {
    val store = FaxiStore.sample().apply { graduate("kid") }
    LicenseRevealScreen(store, "kid", onDone = {})
}
