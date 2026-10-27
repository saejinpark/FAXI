package com.geppetto.faxi.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR 한 장: size × size 칸, true = 검은 칸. 여백(quiet zone)은 그리는 쪽이 둔다. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

/**
 * QR 만들기(차 화면)와 읽기(폰 카메라). 인터넷·구글 서비스 없이 zxing으로 한다.
 * 오류 정정 기본은 M(약 15%). 내용이 긴 영수증 QR은 칸 수를 줄이려고 L(약 7%)로 그린다(밝은 화면에 평평하게 뜨므로 충분).
 */
object QrCodec {
    fun encode(text: String, ecc: ErrorCorrectionLevel = ErrorCorrectionLevel.M): QrMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ecc,
            EncodeHintType.MARGIN to 0,
        )
        // 크기 0으로 부르면 칸 하나 = 1픽셀인 최소 크기로 나온다
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = m.width
        return QrMatrix(size, BooleanArray(size * size) { i -> m.get(i % size, i / size) })
    }

    /**
     * 카메라 한 장면의 밝기 값(YUV의 Y 평면)에서 QR 글자를 읽는다. 못 찾으면 null.
     * rowStride는 한 줄의 실제 바이트 수(카메라에 따라 width보다 클 수 있다).
     */
    fun decodeLuminance(y: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
        val source = PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false)
        val hints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.TRY_HARDER to true)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: ChecksumException) {
            null
        } catch (e: FormatException) {
            null
        }
    }
}
