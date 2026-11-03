package com.geppetto.faxi

import com.geppetto.faxi.data.Climate
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.Music
import com.geppetto.faxi.data.Pace
import com.geppetto.faxi.data.RidePrefs
import com.geppetto.faxi.data.Talk
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
        assertEquals(3, r.lateDays)
        assertEquals(18_370, r.billed)
        assertEquals(18_370, s.debt("mom", "dad")!!.amount)
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
    fun 받았어요를_누르면_그만큼_줄고_다_받으면_사라진다() {
        val s = FaxiStore.sample(clock)
        val debt = s.debt("grandma", "mom")!!
        s.receivePayment(debt, 9_000)
        val left = s.debt("grandma", "mom")!!
        assertEquals(300, left.amount)
        assertEquals(debt.since, left.since) // 일부만 갚아도 연체 일수는 처음 날짜부터
        assertEquals("할머니가 엄마에게 9,000원 갚음 · 300원 남음", s.events.first().text)
        s.receivePayment(left, 300)
        assertNull(s.debt("grandma", "mom"))
        assertEquals("할머니가 엄마에게 300원 갚음 · 다 갚음", s.events.first().text)
    }

    @Test
    fun 탕감하면_빚이_사라진다() {
        val s = FaxiStore.sample(clock)
        val debt = s.debt("mom", "dad")!!
        s.forgive(debt)
        assertNull(s.debt("mom", "dad"))
        assertEquals("아빠가 엄마 빚 3,120원 탕감", s.events.first().text)
    }

    @Test
    fun 밀린_돈에는_이자가_붙지_않고_일수만_센다() {
        var now = LocalDateTime.of(2026, 10, 27, 9, 0)
        val s = FaxiStore.sample { now }
        assertEquals(3, s.daysOf(s.debt("mom", "dad")!!))
        now = now.plusDays(2)
        s.startRide("mom")
        val r = s.endRide()
        assertEquals(5, r.lateDays)
        assertEquals(15_250 + 3_120, r.billed)
        assertEquals(0, s.daysOf(s.debt("mom", "dad")!!))
    }

    @Test
    fun 저장했다_읽어도_장부가_같다() {
        val s = FaxiStore.sample(clock)
        s.startRide("mom")
        s.endRide()
        s.receivePayment(s.debts.first(), 1_000)
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
        assertTrue(s.verifyCode("mom", codeOf("mom", now)))
        assertTrue(s.verifyCode("mom", codeOf("mom", now - 30))) // 시계가 조금 달라도
        assertEquals(false, s.verifyCode("mom", codeOf("mom", now - 120))) // 2분 지난 코드
        // 다른 가족의 코드로는 못 탄다(우연히 같은 4자리가 아니라면)
        val grandmaCode = codeOf("grandma", now)
        if (grandmaCode != codeOf("mom", now)) assertEquals(false, s.verifyCode("mom", grandmaCode))
        // 시연 가족 열쇠는 폰 앱과 같은 고정값
        assertEquals(com.geppetto.faxi.auth.DemoFamily.secretOf("mom"), s.member("mom").secret)
    }

    @Test
    fun 탑승_때_고른_가족_할증은_앱이_다시_켜져도_남고_영수증에_붙는다() {
        val s = FaxiStore.sample(clock)
        s.startRide("mom", setOf(com.geppetto.faxi.fare.FamilyExtra.Late, com.geppetto.faxi.fare.FamilyExtra.Luggage))
        val reopened = FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock)
        assertEquals(s.rideExtras, reopened.rideExtras)
        val r = reopened.endRide()
        // 기획서 예시 15,250원 + 지각 20% 3,050원 + 짐 500원
        assertEquals(15_250 + 3_050 + 500, r.fareTotal)
        assertEquals(listOf("지각 할증", "짐 할증"), r.lines.takeLast(2).map { it.label })
        assertTrue(reopened.rideExtras.isEmpty())
    }

    @Test
    fun 손님_취향은_저장되고_고칠_수_있다() {
        val s = FaxiStore.sample(clock)
        val m = s.addMember("막내", Grade.Full, RidePrefs(pace = Pace.Gentle, music = Music.Off))
        assertEquals(listOf("부드럽게 운전", "음악 없이"), s.member(m.id).prefs.summary)
        s.updateMember(m.id, "막내", "", RidePrefs(climate = Climate.Warm, talk = Talk.Chatty))
        val reopened = FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock)
        assertEquals(listOf("따뜻하게", "이야기 좋아요"), reopened.member(m.id).prefs.summary)
        // 이름만 고치면 취향은 그대로
        reopened.updateMember(m.id, "막둥이", "")
        assertEquals(Climate.Warm, reopened.member(m.id).prefs.climate)
        // 고르지 않으면 요약이 비어 있다(탑승 화면에 줄이 없다)
        assertTrue(RidePrefs().summary.isEmpty())
    }
}
