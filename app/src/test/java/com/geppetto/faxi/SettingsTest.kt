package com.geppetto.faxi

import com.geppetto.faxi.data.Dunning
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.ReviewsAloud
import com.geppetto.faxi.data.dunningLine
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 설정: 독촉 말투, 후기 낭독 범위 */
class SettingsTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }

    @Test
    fun 독촉_말투() {
        assertEquals("손님, 미결제 3,120원이 3일째입니다.", dunningLine(3_120, 3, "엄마", Dunning.Firm))
        assertEquals("엄마, 지난번 요금 3,120원이 남아 있어요.", dunningLine(3_120, 3, "엄마", Dunning.Gentle))
        assertEquals("엄마, 지난번 요금 3,120원이 남아 있어요.", dunningLine(3_120, 3, "엄마", Dunning.ScreenOnly))
    }

    @Test
    fun 후기를_읽지_않기로_하면_아무것도_꺼내지_않는다() {
        val s = FaxiStore.sample(clock)
        s.updateSettings(s.settings.copy(reviewsAloud = ReviewsAloud.Off))
        assertNull(s.takeUnreadReview("dad"))
        // 다시 켜면 그대로 읽을 수 있다(읽은 것으로 넘기지 않았다)
        s.updateSettings(s.settings.copy(reviewsAloud = ReviewsAloud.All))
        assertEquals(2, s.takeUnreadReview("dad")!!.second.stars) // 가장 최근 후기(지우, 별 2개)
    }

    @Test
    fun 별_4개_이상만이면_낮은_후기는_건너뛰고_다시_읽지_않는다() {
        val s = FaxiStore.sample(clock)
        s.updateSettings(s.settings.copy(reviewsAloud = ReviewsAloud.GoodOnly))
        assertNull(s.takeUnreadReview("dad")) // 아빠 후기는 별 3개, 2개뿐
        s.updateSettings(s.settings.copy(reviewsAloud = ReviewsAloud.All))
        assertNull(s.takeUnreadReview("dad")) // 건너뛴 후기를 나중에 몰아서 읽지 않는다
        s.updateSettings(s.settings.copy(reviewsAloud = ReviewsAloud.GoodOnly))
        assertEquals(5, s.takeUnreadReview("mom")!!.second.stars)
    }

    @Test
    fun 설정은_장부와_같이_저장된다() {
        val s = FaxiStore.sample(clock)
        s.updateSettings(s.settings.copy(dunning = Dunning.Gentle, greet = false))
        val reopened = FaxiStore(faxiSnapshotFromJson(s.snapshot().toJson()), clock)
        assertEquals(Dunning.Gentle, reopened.settings.dunning)
        assertEquals(false, reopened.settings.greet)
    }
}
