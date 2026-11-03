package com.geppetto.faxi.ui.receipt

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.geppetto.faxi.ui.theme.FaxiDimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Receipt
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPage
import com.geppetto.faxi.ui.components.FaxiPreviews
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.PreviewFrame
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.theme.Faxi

/**
 * 도착 (하차 직후, 주차 상태). 운행 중 감춰 둔 금액을 여기서 한 번에 공개한다.
 * 사이드바 없이 화면 전체를 쓰고, 닫으면 탑승 화면으로 돌아간다. 지난 영수증은 장부에서 다시 본다.
 * 후기는 손님이 폰에서 기사에게 하나만 쓴다(기사가 보는 차 화면에서 기사를 평가하게 하지 않는다). 오면 이 화면에도 바로 뜬다.
 */
@Composable
fun ArrivalScreen(store: FaxiStore, receipt: Receipt, onClose: () -> Unit) {
    val driver = store.name(receipt.driverId)
    val passenger = store.name(receipt.passengerId)
    // 디버그 빌드이거나 시연 가족일 때만: 에뮬레이터 카메라로 차 화면을 찍을 수 없어서, 폰 앱 시험용으로 영수증 링크를 남긴다(비밀 없음).
    // 심사용 release 빌드도 시연 가족으로는 에뮬레이터 두 대로 영수증을 옮겨 볼 수 있다. 실제 가족 장부는 로그에 남기지 않는다.
    val context = LocalContext.current
    LaunchedEffect(receipt.id) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 || store.familyId == DemoFamily.FAMILY_ID) {
            Log.d("FaxiReceipt", store.ticketFor(receipt).toLink())
        }
    }
    // 확인은 제목 줄에 둔다. 카드 안 맨 아래에 두면 영수증이 길 때 화면 밖으로 밀린다.
    FaxiPage(
        title = "도착했어요",
        trailing = { FaxiPrimaryButton("확인", onClick = onClose) },
    ) {
        row(
            { m -> ReceiptCard(store, receipt, m) },
            { m ->
                // 밤 바탕 위에 상자 없이: 승차감 그래프와 기사 후기
                Column(m, verticalArrangement = Arrangement.spacedBy(FaxiDimens.space8)) {
                    RideComfortChart(receipt)
                    ReviewSummary("$driver 기사 후기", store.reviews[receipt.id], empty = "$passenger 폰에서 후기를 쓰면 여기 떠요.")
                }
            },
        )
    }
}

/** 장부에서 연 지난 영수증 */
@Composable
fun ReceiptDetailScreen(store: FaxiStore, receipt: Receipt, onBack: () -> Unit) {
    val driver = store.name(receipt.driverId)
    val passenger = store.name(receipt.passengerId)
    FaxiPage(title = "영수증 · ${receipt.endedAt.format(ReceiptTimeFormat)}", onBack = onBack) {
        row(
            { m -> ReceiptCard(store, receipt, m, past = true) },
            { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(FaxiDimens.space8)) {
                    RideComfortChart(receipt)
                    ReviewSummary("$driver 기사 후기", store.reviews[receipt.id], empty = "$passenger 폰에서 아직 안 남겼어요.")
                }
            },
        )
    }
}

@FaxiPreviews
@Composable
private fun ArrivalPreview() = PreviewFrame {
    val store = FaxiStore.sample().apply { startRide("mom") }
    val receipt = store.endRide()
    ArrivalScreen(store, receipt, onClose = {})
}
