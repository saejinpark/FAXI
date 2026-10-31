package com.geppetto.faxi

import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기사 로그인: 자기 폰 코드로 한 번, 차가 기억한다. 손님은 로그인한 기사의 택시에만 탄다. */
class DriverSignInTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }
    private val now = clock().atZone(ZoneId.systemDefault()).toEpochSecond()
    private fun FaxiStore.codeOf(id: String) = Totp.code(Base32.decode(member(id).secret)!!, now)

    @Test
    fun 시연_데이터는_아빠가_로그인해_있다() {
        val s = FaxiStore.sample(clock)
        assertTrue(s.driverSignedIn)
        assertEquals("dad", s.driverId)
    }

    @Test
    fun 자기_코드가_맞아야_기사가_된다() {
        val s = FaxiStore.sample(clock)
        // 남의 코드로는 안 된다(두 사람 코드가 우연히 같은 30초는 건너뛴다)
        if (s.codeOf("dad") != s.codeOf("mom")) assertFalse(s.signInDriver("mom", s.codeOf("dad")))
        assertEquals("dad", s.driverId)
        assertTrue(s.signInDriver("mom", s.codeOf("mom")))
        assertEquals("mom", s.driverId)
        assertTrue(s.driverSignedIn)
    }

    @Test(expected = IllegalArgumentException::class)
    fun 예비_기사는_로그인할_수_없다() {
        val s = FaxiStore.sample(clock)
        s.signInDriver("kid", s.codeOf("kid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun 기사가_로그인하지_않으면_손님이_탈_수_없다() {
        val s = FaxiStore.sample(clock)
        s.signOutDriver()
        s.startRide("mom")
    }

    @Test
    fun 운행_중에는_기사를_바꿀_수_없다() {
        val s = FaxiStore.sample(clock)
        s.startRide("mom")
        s.signOutDriver()
        assertTrue(s.driverSignedIn)
        assertFalse(s.signInDriver("mom", s.codeOf("mom")))
        assertEquals("dad", s.driverId)
    }

    @Test
    fun 로그인_상태는_저장된다() {
        val s = FaxiStore.sample(clock)
        s.signOutDriver()
        assertFalse(faxiSnapshotFromJson(s.snapshot().toJson()).driverSignedIn)
        // 예전 장부(필드 없음)는 그 기사가 로그인해 있던 것으로 읽는다
        val old = s.snapshot().toJson().replace(Regex(""","?driverSignedIn":(true|false)"""), "")
        assertTrue(faxiSnapshotFromJson(old).driverSignedIn)
    }
}
