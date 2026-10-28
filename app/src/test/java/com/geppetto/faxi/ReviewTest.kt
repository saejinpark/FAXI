package com.geppetto.faxi

import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Review
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.reviewLine
import com.geppetto.faxi.data.toJson
import com.geppetto.faxi.message.DriverTag
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.ReviewEnvelope
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 기사 후기(손님 폰에서 와서 다음에 기사를 고를 때 낭독)와 손님 평가(차 도착 화면) */
class ReviewTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }
    private val now = clock().atZone(ZoneId.systemDefault()).toEpochSecond()

    private fun fromPhone(store: FaxiStore, memberId: String, receiptId: String, stars: Int, vararg tags: DriverTag) =
        ReviewEnvelope.create(
            store.familyId, memberId, receiptId, stars, tags.toList(),
            Base32.decode(DemoFamily.secretOf(memberId))!!, now,
        )

    private fun rideMomWithDad(): Pair<FaxiStore, String> {
        val s = FaxiStore.sample(clock)
        s.selectDriver("dad")
        s.startRide("mom")
        return s to s.endRide().id
    }

    @Test
    fun 손님_폰에서_온_후기를_받고_한_운행에_한_번만() {
        val (s, id) = rideMomWithDad()
        assertEquals(MessageReply.Shown, s.receiveReview(fromPhone(s, "mom", id, 4, DriverTag.Route)))
        assertEquals(Review(4, setOf("길 잘 앎")), s.reviews[id])
        assertEquals(MessageReply.AlreadyReviewed, s.receiveReview(fromPhone(s, "mom", id, 1)))
        assertEquals(4, s.reviews[id]!!.stars)
    }

    @Test
    fun 그_운행의_손님이_아니면_후기를_받지_않는다() {
        val (s, id) = rideMomWithDad()
        assertEquals(MessageReply.UnknownRide, s.receiveReview(fromPhone(s, "grandma", id, 1)))
        assertEquals(MessageReply.UnknownRide, s.receiveReview(fromPhone(s, "mom", "r999", 5)))
        assertNull(s.reviews[id])
    }

    @Test
    fun 다른_열쇠로_서명한_후기는_받지_않는다() {
        val (s, id) = rideMomWithDad()
        val forged = ReviewEnvelope.create(s.familyId, "mom", id, 1, emptyList(), ByteArray(20), now)
        assertEquals(MessageReply.Rejected, s.receiveReview(forged))
    }

    @Test
    fun 평균_별점은_받은_후기로_계산한다() {
        val (s, id) = rideMomWithDad()
        assertEquals(2.5, s.driverRating("dad")!!, 0.001) // 시연 데이터 3개, 2개
        s.receiveReview(fromPhone(s, "mom", id, 5))
        assertEquals(10.0 / 3, s.driverRating("dad")!!, 0.001)
    }

    @Test
    fun 가장_최근_후기부터_한_번씩만_읽는다() {
        val (s, id) = rideMomWithDad()
        s.receiveReview(fromPhone(s, "mom", id, 2, DriverTag.Music))

        assertEquals(id, s.takeUnreadReview("dad")!!.first.id) // 방금 받은 후기
        val older = s.takeUnreadReview("dad")!!.first.id      // 그다음은 예전 후기
        assertEquals(true, older != id)
        while (s.takeUnreadReview("dad") != null) Unit
        assertNull(s.takeUnreadReview("dad"))
    }

    @Test
    fun 읽은_후기는_앱을_다시_켜도_다시_읽지_않는다() {
        val s = FaxiStore.sample(clock)
        val first = s.takeUnreadReview("dad")!!.first.id
        val reopened = FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock)
        assertEquals(true, reopened.takeUnreadReview("dad")?.first?.id != first)
    }

    @Test
    fun 기사가_남긴_손님_평가는_한_번만_저장되고_다시_켜도_남는다() {
        val (s, id) = rideMomWithDad()
        s.ratePassenger(id, Review(5, setOf("인사 잘함")))
        s.ratePassenger(id, Review(1, emptySet()))
        assertEquals(5.0, s.passengerRating("mom")!!, 0.001)
        val reopened = FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock)
        assertEquals(Review(5, setOf("인사 잘함")), reopened.passengerReviews[id])
    }

    @Test
    fun 낭독_문장() {
        assertEquals("지난 손님 후기입니다. 엄마 손님, 별 세 개. 선곡 최악.", reviewLine("엄마", 3, listOf("선곡 최악")))
        assertEquals("지난 손님 후기입니다. 지우 손님, 별 한 개.", reviewLine("지우", 1, emptyList()))
    }
}
