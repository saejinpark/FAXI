package com.geppetto.faxi

import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Review
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.reviewLine
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 후기 낭독: 기사를 고르면 아직 안 읽은 가장 최근 후기를 한 번만 */
class ReviewReadAloudTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }

    @Test
    fun 가장_최근_후기부터_한_번씩만_읽는다() {
        val s = FaxiStore.sample(clock)
        s.selectDriver("dad")
        s.startRide("mom")
        val r = s.endRide()
        s.submitReview(r.id, Review(2, setOf("선곡 최악")))

        assertEquals(r.id, s.takeUnreadReview("dad")!!.first.id) // 방금 받은 후기
        val older = s.takeUnreadReview("dad")!!.first.id           // 그다음은 예전 후기
        assertEquals(true, older != r.id)
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
    fun 낭독_문장() {
        assertEquals("지난 손님 후기입니다. 엄마 손님, 별 세 개. 선곡 최악.", reviewLine("엄마", 3, listOf("선곡 최악")))
        assertEquals("지난 손님 후기입니다. 지우 손님, 별 한 개.", reviewLine("지우", 1, emptyList()))
        assertEquals("지난 손님 후기입니다. 할머니 손님, 별 다섯 개. 길 잘 앎, 다음에 또 탈게요.", reviewLine("할머니", 5, listOf("길 잘 앎", "다음에 또 탈게요")))
    }
}
