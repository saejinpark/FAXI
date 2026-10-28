package com.geppetto.faxi.qr

import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.fare.FakeTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDateTime

class QrTest {
    private fun sampleTicket(): ReceiptTicket {
        val meter = FakeTrip.run(FakeTrip.planExample(LocalDateTime.of(2026, 10, 27, 11, 7)))
        return ReceiptTicket(
            familyId = DemoFamily.FAMILY_ID, id = "r100", passengerId = "mom", passenger = "엄마",
            driver = "아빠 · PAXI", route = "우리집 → 이마트 성수점", endedAt = "2026-10-27T11:07",
            lines = meter.lines(), fareTotal = meter.total, billed = 18_370, carriedOver = 3_120,
            balances = listOf(Balance("아빠", owedToMe = false, amount = 18_370), Balance("할머니", owedToMe = true, amount = 9_300, disputed = true)),
        )
    }

    /** QR 그림을 카메라가 본 것처럼 밝기 값으로 그린다(칸 하나 = scale픽셀, 둘레 4칸 여백) */
    private fun render(m: QrMatrix, scale: Int = 4): Triple<ByteArray, Int, Int> {
        val margin = 4
        val px = (m.size + margin * 2) * scale
        val y = ByteArray(px * px) { 0xFF.toByte() }
        for (yy in 0 until m.size) for (xx in 0 until m.size) if (m[xx, yy]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                y[((yy + margin) * scale + dy) * px + (xx + margin) * scale + dx] = 0
            }
        }
        return Triple(y, px, px)
    }

    @Test
    fun 영수증_링크_왕복() {
        val t = sampleTicket()
        val link = t.toLink()
        assertTrue(link.startsWith("faxi://receipt?d="))
        assertEquals(t, ReceiptTicket.parse(link))
        assertNull(ReceiptTicket.parse("faxi://receipt?d=@@@"))
        assertNull(ReceiptTicket.parse("faxi://join?f=x"))
    }

    @Test
    fun 영수증_QR은_찍기_좋은_크기다() {
        val link = sampleTicket().toLink()
        // 칸이 많은(버전이 높은) QR은 데이터 속 정렬 무늬를 찾기 무늬로 잘못 잡아 못 읽는 일이 잦다.
        // 차 화면 스크린샷으로 시험했을 때 77칸은 주변에 따라 실패했다. 오류 정정 M으로 69칸(버전 13) 이하를 지킨다.
        val m = QrCodec.encode(link, ErrorCorrectionLevel.M)
        assertTrue("링크 ${link.length}자, QR ${m.size}칸", m.size <= 69)
    }

    @Test
    fun QR로_그렸다_읽으면_같은_글자() {
        listOf(sampleTicket().toLink(), DemoFamily.inviteOf(DemoFamily.people[1]).toLink()).forEach { link ->
            val (y, w, h) = render(QrCodec.encode(link))
            assertEquals(link, QrCodec.decodeLuminance(y, w, h))
        }
    }

    @Test
    fun QR이_없는_장면은_null() {
        assertNull(QrCodec.decodeLuminance(ByteArray(200 * 200) { 0x7F }, 200, 200))
    }

    @Test
    fun 영수증_줄은_기호로_실었다가_그대로_되돌린다() {
        val lines = FakeTrip.run(FakeTrip.planExample(LocalDateTime.of(2026, 10, 27, 23, 0))).lines() +
            listOf(
                com.geppetto.faxi.fare.FareLine("우천 할증", "와이퍼 10%", 480),
                com.geppetto.faxi.fare.FareLine("경유지 추가", "2곳", 1_000),
                com.geppetto.faxi.fare.FareLine("표에 없는 줄", "아무거나", 7),
            )
        lines.forEach { l ->
            assertEquals(l, com.geppetto.faxi.fare.FareCodes.expand(com.geppetto.faxi.fare.FareCodes.shorten(l)))
        }
        assertEquals("W", com.geppetto.faxi.fare.FareCodes.shorten(lines.first { it.label == "대기요금" }).label)
    }

    /** 차 화면처럼: 어두운 바탕 위 흰 종이(여백 4칸) 위 QR. 카메라 한 장면에 QR이 칸당 scale픽셀로 들어온 모습 */
    private fun renderOnDarkScreen(m: QrMatrix, scale: Int, darkPad: Int): Triple<ByteArray, Int, Int> {
        val margin = 4
        val paper = (m.size + margin * 2) * scale
        val px = paper + darkPad * 2
        val y = ByteArray(px * px) { 0x10 }
        for (yy in 0 until paper) for (xx in 0 until paper) y[(yy + darkPad) * px + xx + darkPad] = 0xFF.toByte()
        for (yy in 0 until m.size) for (xx in 0 until m.size) if (m[xx, yy]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                y[((yy + margin) * scale + dy + darkPad) * px + (xx + margin) * scale + dx + darkPad] = 0
            }
        }
        return Triple(y, px, px)
    }

    @Test
    fun 어두운_화면_위_영수증_QR도_여러_크기에서_읽힌다() {
        val link = sampleTicket().toLink()
        val m = QrCodec.encode(link)
        for (scale in 3..6) for (pad in listOf(0, 40, 200)) {
            val (y, w, h) = renderOnDarkScreen(m, scale, pad)
            assertEquals("칸당 ${scale}px, 어두운 테두리 ${pad}px", link, QrCodec.decodeLuminance(y, w, h))
        }
    }
}
