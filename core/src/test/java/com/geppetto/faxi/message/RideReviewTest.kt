package com.geppetto.faxi.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideReviewTest {
    private val secret = ByteArray(20) { it.toByte() }
    private val now = 1_800_000_000L

    @Test
    fun `후기는 한 줄로 갔다가 그대로 돌아온다`() {
        val sent = ReviewEnvelope.create("fam", "mom", "r7", 4, listOf(DriverTag.Route, DriverTag.Again), secret, now)
        val got = ReviewEnvelope.parse(sent.toLine())
        assertEquals(sent, got)
        assertTrue(got!!.verify(secret, now))
    }

    @Test
    fun `태그 없는 후기도 된다`() {
        val sent = ReviewEnvelope.create("fam", "mom", "r7", 5, emptyList(), secret, now)
        assertEquals(sent, ReviewEnvelope.parse(sent.toLine()))
    }

    @Test
    fun `별점이나 영수증을 바꾸면 서명이 깨진다`() {
        val sent = ReviewEnvelope.create("fam", "mom", "r7", 1, emptyList(), secret, now)
        assertFalse(sent.copy(stars = 5).verify(secret, now))
        assertFalse(sent.copy(receiptId = "r8").verify(secret, now))
        assertFalse(sent.copy(tags = listOf(DriverTag.Quiet)).verify(secret, now))
    }

    @Test
    fun `모양이 틀린 줄은 읽지 않는다`() {
        assertNull(ReviewEnvelope.parse("FAXIR1|fam|mom|r7|9||$now|sig"))
        assertNull(ReviewEnvelope.parse("FAXIR1|fam|mom|r7|3|NOPE|$now|sig"))
        assertNull(ReviewEnvelope.parse("FAXI1|fam|mom|SLOW|$now|sig"))
    }
}
