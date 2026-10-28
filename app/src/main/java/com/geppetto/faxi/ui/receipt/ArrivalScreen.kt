package com.geppetto.faxi.ui.receipt

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.PassengerTags
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
 * 기사 후기는 손님이 폰에서 쓴다(기사가 보는 차 화면에서 기사를 평가하게 하지 않는다). 차 화면에서는 기사가 손님을 평가한다.
 */
@Composable
fun ArrivalScreen(store: FaxiStore, receipt: Receipt, onClose: () -> Unit) {
    val rated = store.passengerReviews[receipt.id]
    val passenger = store.name(receipt.passengerId)
    // 디버그 빌드에서만: 에뮬레이터 카메라로 차 화면을 찍을 수 없어서, 폰 앱 시험용으로 영수증 링크를 남긴다(비밀 없음)
    val context = LocalContext.current
    LaunchedEffect(receipt.id) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("FaxiReceipt", store.ticketFor(receipt).toLink())
        }
    }
    // 닫기는 제목 줄에 둔다. 카드 안 맨 아래에 두면 영수증이 길 때 화면 밖으로 밀린다.
    // 평가 남기기가 이 화면의 주 버튼이라, 평가 전에는 닫기를 보조 버튼으로 둔다.
    FaxiPage(
        title = "도착했어요",
        trailing = {
            if (rated == null) FaxiSecondaryButton("닫기", onClick = onClose) else FaxiPrimaryButton("확인", onClick = onClose)
        },
    ) {
        row(
            { m -> ReceiptCard(store, receipt, m) },
            { m ->
                FaxiCard(m) {
                    RideComfortChart(receipt)
                    SectionTitle("손님 평가")
                    Text("기사 후기는 $passenger 폰에서 영수증 QR을 찍은 뒤 남겨요.", style = Faxi.type.caption, color = Faxi.colors.inkMuted)
                    if (rated == null) {
                        RatingForm(receipt.id, "$passenger 손님 어떠셨어요?", PassengerTags) { store.ratePassenger(receipt.id, it) }
                    } else {
                        ReviewSummary("$passenger 손님", rated, empty = "")
                    }
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
                FaxiCard(m) {
                    RideComfortChart(receipt)
                    ReviewSummary("$driver 기사 후기", store.reviews[receipt.id], empty = "$passenger 폰에서 아직 안 남겼어요.")
                    ReviewSummary("$passenger 손님 평가", store.passengerReviews[receipt.id], empty = "평가하지 않은 운행이에요.")
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
