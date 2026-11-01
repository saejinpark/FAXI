package com.geppetto.faxi.message

import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.qr.Balance
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 동기화 한 통: 프로필을 싣고 서명으로 확인, 차의 답은 줄였다 되살린다 */
class ProfileSyncTest {
    private val secret = Base32.decode(DemoFamily.secretOf("mom"))!!
    private val now = 1_800_000_000L

    @Test
    fun 별명과_사진을_싣고_왕복한다() {
        val photo = ByteArray(30_000) { (it % 251).toByte() }
        val env = SyncEnvelope.create("f1", "mom", "주차 장인|박기사", photo, secret, now)
        val back = SyncEnvelope.parse(env.toLine())!!
        assertEquals("주차 장인|박기사", back.nickname) // 구분자 |가 들어 있어도 Base64라 안전하다
        assertArrayEquals(photo, back.photo)
        assertTrue(back.verify(secret, now + 5))
    }

    @Test
    fun 바뀐_것이_없으면_빈_자리() {
        val env = SyncEnvelope.create("f1", "mom", null, null, secret, now)
        val back = SyncEnvelope.parse(env.toLine())!!
        assertNull(back.nickname)
        assertNull(back.photo)
        assertTrue(back.verify(secret, now))
    }

    @Test
    fun 다른_열쇠나_오래된_통은_통과하지_못한다() {
        val env = SyncEnvelope.parse(SyncEnvelope.create("f1", "mom", "x", null, secret, now).toLine())!!
        assertFalse(env.verify(Base32.decode(DemoFamily.secretOf("dad"))!!, now))
        assertFalse(env.verify(secret, now + 120))
    }

    @Test
    fun 너무_큰_사진이나_긴_별명은_받지_않는다() {
        assertNull(SyncEnvelope.parse(SyncEnvelope.create("f1", "mom", null, ByteArray(SyncEnvelope.MAX_PHOTO_BYTES + 1), secret, now).toLine()))
        assertNull(SyncEnvelope.parse(SyncEnvelope.create("f1", "mom", "가".repeat(15), null, secret, now).toLine()))
    }

    @Test
    fun 답은_줄였다_되살린다() {
        val p = SyncPayload(riding = true, profileApplied = false, balances = listOf(Balance("아빠", false, 3_120)), receipts = listOf("faxi://receipt?d=abc"))
        assertEquals(p, SyncReply.decode(SyncReply.encode(p)))
        assertNull(SyncReply.decode("BAD"))
    }
}
