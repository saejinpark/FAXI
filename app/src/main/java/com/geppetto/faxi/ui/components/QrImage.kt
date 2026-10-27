package com.geppetto.faxi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.geppetto.faxi.qr.QrCodec
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.geppetto.faxi.ui.theme.Faxi

/**
 * 진짜 QR 그림. 폰 카메라가 읽을 수 있게 야간 테마에서도 밝은 종이(qrPaper)에 어두운 점(qrInk)으로,
 * 둘레에 4칸 여백(quiet zone)을 둔다. 정지 그림이라 주행 중 규칙과 무관하다.
 * label은 TalkBack이 읽는 이름(링크 내용은 읽어 주지 않는다. 초대 QR에는 비밀 열쇠가 들어 있다).
 */
@Composable
fun QrImage(
    text: String,
    size: Dp,
    label: String,
    ecc: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
) {
    val matrix = remember(text, ecc) { QrCodec.encode(text, ecc) }
    val paper = Faxi.colors.qrPaper
    val dot = Faxi.colors.qrInk
    Canvas(Modifier.size(size).semantics { contentDescription = label }) {
        val quiet = 4
        val cells = matrix.size + quiet * 2
        val cell = this.size.width / cells
        drawRect(paper)
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                // 칸 사이 머리카락 틈이 생기지 않게 살짝 겹쳐 그린다
                drawRect(dot, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
