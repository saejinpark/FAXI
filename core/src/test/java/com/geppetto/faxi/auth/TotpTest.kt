package com.geppetto.faxi.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    /** RFC 6238 부록 B의 SHA-1 예시 */
    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test
    fun RFC_6238_예시와_같은_코드를_만든다() {
        assertEquals("94287082", Totp.code(rfcSecret, 59, digits = 8))
        assertEquals("07081804", Totp.code(rfcSecret, 1_111_111_109, digits = 8))
        assertEquals("14050471", Totp.code(rfcSecret, 1_111_111_111, digits = 8))
        assertEquals("89005924", Totp.code(rfcSecret, 1_234_567_890, digits = 8))
        assertEquals("69279037", Totp.code(rfcSecret, 2_000_000_000, digits = 8))
        // 4자리는 8자리의 끝 4자리와 같다
        assertEquals("7082", Totp.code(rfcSecret, 59))
    }

    @Test
    fun 앞뒤_30초까지는_통과하고_그_밖은_막는다() {
        val now = 1_761_550_000L
        val code = Totp.code(rfcSecret, now)
        assertTrue(Totp.verify(rfcSecret, code, now))
        assertTrue(Totp.verify(rfcSecret, code, now + 30))
        assertTrue(Totp.verify(rfcSecret, code, now - 30))
        assertFalse(Totp.verify(rfcSecret, code, now + 90))
        assertFalse(Totp.verify(rfcSecret, "12a4", now))
        assertFalse(Totp.verify(rfcSecret, "123", now))
    }

    @Test
    fun 남은_초는_1에서_30() {
        assertEquals(30, Totp.secondsLeft(60))
        assertEquals(1, Totp.secondsLeft(89))
    }

    @Test
    fun Base32_왕복() {
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray())) // RFC 4648 예시(패딩 없음)
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
        val secret = Totp.newSecret()
        assertArrayEquals(secret, Base32.decode(Base32.encode(secret)))
        assertNull(Base32.decode("1!"))
    }

    @Test
    fun 초대_링크_왕복과_잘못된_링크() {
        val invite = DemoFamily.inviteOf(DemoFamily.people[1])
        val link = invite.toLink()
        assertTrue(link.startsWith("faxi://join?"))
        assertEquals(invite, FaxiInvite.parse(link))
        assertNull(FaxiInvite.parse("https://example.com"))
        assertNull(FaxiInvite.parse(link.replace("s=", "s=X")))
    }

    @Test
    fun 시연_가족_열쇠는_고정이고_사람마다_다르다() {
        assertEquals(DemoFamily.secretOf("mom"), DemoFamily.secretOf("mom"))
        assertEquals(4, DemoFamily.people.map { DemoFamily.secretOf(it.memberId) }.toSet().size)
        assertEquals(Totp.SECRET_BYTES, Base32.decode(DemoFamily.secretOf("dad"))!!.size)
    }
}
