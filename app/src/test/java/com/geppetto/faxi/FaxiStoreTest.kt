package com.geppetto.faxi

import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 하차·상계·정산이 장부를 맞게 바꾸는지. 데이터는 FaxiStore.sample(clock) 기준. */
class FaxiStoreTest {
    /** 날짜 고정: 2026-10-27 09:00 */
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }

    @Test
    fun 기획서_예시_영수증이_그대로_나온다() {
        val s = FaxiStore.sample(clock)
        s.selectDriver("dad")
        s.startRide("mom")
        val r = s.endRide()
        assertEquals(15_250, r.fareTotal)
        assertEquals(3_120, r.carriedOver)
        assertEquals(9, r.lateInterest)
        assertEquals(18_379, r.billed)
        assertEquals(18_379, s.debt("mom", "dad")!!.amount)
        assertEquals(r, s.arrival)
    }

    @Test
    fun 기사가_진_빚은_먼저_상계된다() {
        val s = FaxiStore.sample(clock)
        s.selectDriver("mom")
        s.startRide("dad")
        val r = s.endRide()
        assertEquals(3_120, r.offset)
        assertEquals(12_130, r.billed)
        assertNull(s.debt("mom", "dad"))
        assertEquals(12_130, s.debt("dad", "mom")!!.amount)
    }

    @Test
    fun 예비_기사_요금은_평생_장부로_간다() {
        val s = FaxiStore.sample(clock)
        val before = s.lifetime.first().amount
        s.selectDriver("dad")
        s.startRide("kid")
        val r = s.endRide()
        assertTrue(r.toLifetime)
        assertEquals(0, r.billed)
        assertNull(s.debt("kid", "dad"))
        assertEquals(before + 15_250, s.lifetime.first().amount)
    }

    @Test
    fun 일부만_받으면_나머지가_남고_분쟁은_풀린다() {
        val s = FaxiStore.sample(clock)
        val debt = s.debt("grandma", "mom")!!
        assertTrue(debt.disputed)
        s.requestSettlement(debt, 9_000)
        s.confirmSettlement(s.requests.first(), received = true)
        val left = s.debt("grandma", "mom")!!
        assertEquals(300, left.amount)
        assertEquals(false, left.disputed)
    }

    @Test
    fun 탕감하면_빚과_대기중_요청이_사라진다() {
        val s = FaxiStore.sample(clock)
        val debt = s.debt("mom", "dad")!!
        s.requestSettlement(debt, 1_000)
        s.forgive(debt)
        assertNull(s.debt("mom", "dad"))
        assertTrue(s.requests.isEmpty())
        assertEquals("아빠가 엄마 빚 3,120원 탕감", s.events.first().text)
    }

    @Test
    fun 하루가_지나면_연체_이자가_늘어난다() {
        var now = LocalDateTime.of(2026, 10, 27, 9, 0)
        val s = FaxiStore.sample { now }
        assertEquals(3, s.daysOf(s.debt("mom", "dad")!!))
        now = now.plusDays(2)
        s.startRide("mom")
        val r = s.endRide()
        assertEquals(5, r.lateDays)
        assertEquals(15, r.lateInterest) // 3,120 × 0.1% × 5일 = 15.6 → 15
        assertEquals(0, s.daysOf(s.debt("mom", "dad")!!))
    }

    @Test
    fun 저장했다_읽어도_장부가_같다() {
        val s = FaxiStore.sample(clock)
        s.startRide("mom")
        s.endRide()
        s.requestSettlement(s.debts.first(), 1_000)
        val snap = s.snapshot()
        assertEquals(snap, faxiSnapshotFromJson(snap.toJson()))
        val restored = FaxiStore(faxiSnapshotFromJson(snap.toJson()), clock)
        assertEquals(s.debts.toList(), restored.debts.toList())
        assertEquals(s.arrival, restored.arrival)
    }

    @Test
    fun 캐릭터를_바꾸면_저장에도_남는다() {
        val s = FaxiStore.sample(clock)
        var saved = 0
        s.onChanged = { saved++ }
        s.setAvatar("dad", "tiger")
        assertEquals(1, saved)
        assertEquals("tiger", faxiSnapshotFromJson(s.snapshot().toJson()).members.first { it.id == "dad" }.avatar)
    }

    @Test
    fun 탑승_코드는_그_손님_폰의_지금_코드만_통과한다() {
        val s = FaxiStore.sample(clock)
        val now = clock().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        fun codeOf(id: String, at: Long) =
            com.geppetto.faxi.auth.Totp.code(com.geppetto.faxi.auth.Base32.decode(s.member(id).secret)!!, at)
        assertTrue(s.verifyBoardingCode("mom", codeOf("mom", now)))
        assertTrue(s.verifyBoardingCode("mom", codeOf("mom", now - 30))) // 시계가 조금 달라도
        assertEquals(false, s.verifyBoardingCode("mom", codeOf("mom", now - 120))) // 2분 지난 코드
        // 다른 가족의 코드로는 못 탄다(우연히 같은 4자리가 아니라면)
        val grandmaCode = codeOf("grandma", now)
        if (grandmaCode != codeOf("mom", now)) assertEquals(false, s.verifyBoardingCode("mom", grandmaCode))
        // 시연 가족 열쇠는 폰 앱과 같은 고정값
        assertEquals(com.geppetto.faxi.auth.DemoFamily.secretOf("mom"), s.member("mom").secret)
    }
}
