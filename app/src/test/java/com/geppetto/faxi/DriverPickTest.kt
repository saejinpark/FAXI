package com.geppetto.faxi

import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 오늘의 기사: 고르기만 하면 차가 기억한다. 손님은 정해진 기사의 택시에만 탄다. 폰 코드 확인은 설정에서 켜는 추가 기능. */
class DriverPickTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }

    @Test
    fun 시연_데이터는_아빠가_기사다() {
        val s = FaxiStore.sample(clock)
        assertTrue(s.driverSignedIn)
        assertEquals("dad", s.driverId)
        assertFalse(s.settings.phoneCode) // 기본은 폰 없이 "제가 탈게요"
    }

    @Test
    fun 기사는_고르기만_하면_바뀌고_저장된다() {
        val s = FaxiStore.sample(clock)
        assertTrue(s.selectDriver("mom"))
        assertEquals("mom", FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock).driverId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun 예비_기사는_기사가_될_수_없다() {
        FaxiStore.sample(clock).selectDriver("kid")
    }

    @Test
    fun 운행_중에는_기사를_바꿀_수_없다() {
        val s = FaxiStore.sample(clock)
        s.startRide("mom")
        assertFalse(s.selectDriver("mom"))
        assertEquals("dad", s.driverId)
    }

    @Test
    fun 가장을_등록하면_바로_기사다() {
        val s = FaxiStore(FaxiStore.emptySnapshot(), clock)
        val head = s.startFamily("아빠")
        assertTrue(s.driverSignedIn)
        assertEquals(head.id, s.driverId)
    }

    @Test
    fun 폰_코드_확인_설정은_저장된다() {
        val s = FaxiStore.sample(clock)
        s.updateSettings(s.settings.copy(phoneCode = true))
        assertTrue(FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock).settings.phoneCode)
    }
}
