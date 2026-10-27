package com.geppetto.faxi.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMessageTest {
    private val secret = ByteArray(20) { it.toByte() }
    private val other = ByteArray(20) { (it + 1).toByte() }
    private val now = 1_800_000_000L

    @Test
    fun `한 줄로 갔다가 그대로 돌아온다`() {
        val sent = MessageEnvelope.create("fam", "mom", RideMessage.Slow, secret, now)
        val got = MessageEnvelope.parse(sent.toLine())
        assertEquals(sent, got)
        assertTrue(got!!.verify(secret, now))
    }

    @Test
    fun `다른 열쇠로 서명하면 통과하지 못한다`() {
        val forged = MessageEnvelope.create("fam", "mom", RideMessage.Slow, other, now)
        assertFalse(forged.verify(secret, now))
    }

    @Test
    fun `내용을 바꾸면 서명이 깨진다`() {
        val sent = MessageEnvelope.create("fam", "mom", RideMessage.Slow, secret, now)
        assertFalse(sent.copy(message = RideMessage.Stop).verify(secret, now))
        assertFalse(sent.copy(memberId = "kid").verify(secret, now))
        assertFalse(sent.copy(sentAt = now + 1).verify(secret, now))
    }

    @Test
    fun `1분 넘게 지난 한마디는 받지 않는다`() {
        val sent = MessageEnvelope.create("fam", "mom", RideMessage.Hot, secret, now)
        assertTrue(sent.verify(secret, now + 60))
        assertFalse(sent.verify(secret, now + 61))
        assertFalse(sent.verify(secret, now - 61))
    }

    @Test
    fun `모양이 틀린 줄은 null`() {
        assertNull(MessageEnvelope.parse(""))
        assertNull(MessageEnvelope.parse("hello"))
        assertNull(MessageEnvelope.parse("FAXI2|fam|mom|SLOW|1|x"))
        assertNull(MessageEnvelope.parse("FAXI1|fam|mom|LOVE|1|x"))
        assertNull(MessageEnvelope.parse("FAXI1|fam|mom|SLOW|soon|x"))
        assertNull(MessageEnvelope.parse("FAXI1||mom|SLOW|1|x"))
    }

    @Test
    fun `문장 코드와 답은 차와 폰이 같은 표를 쓴다`() {
        RideMessage.entries.forEach { assertEquals(it, RideMessage.of(it.code)) }
        MessageReply.entries.forEach { assertEquals(it, MessageReply.of(it.wire)) }
        assertNotNull(MessageReply.of("OK\n"))
    }
}
