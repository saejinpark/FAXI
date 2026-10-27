package com.geppetto.faxi.fare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** 요금 엔진: 기획서 요금표와 영수증 예시가 1원 단위까지 그대로 나오는지 */
class FareMeterTest {
    private val day = LocalDateTime.of(2026, 10, 27, 14, 0)

    private fun linesOf(b: TripBuilder) = FakeTrip.run(b.samples).lines()
    private fun List<FareLine>.amount(label: String) = firstOrNull { it.label == label }?.amount

    @Test
    fun 기획서_영수증_예시가_그대로_나온다() {
        val meter = FakeTrip.run(FakeTrip.planExample(day))
        assertEquals(
            listOf(
                FareLine("기본요금", null, 4_800),
                FareLine("거리요금", "12.4km", 9_300),
                FareLine("대기요금", "신호 7회", 600),
                FareLine("냉방 옵션", "14분", 420),
                FareLine("엉따 옵션", "9분", 180),
                FareLine("기사 귀책 환급", "급정거 1회", -50),
            ),
            meter.lines(),
        )
        assertEquals(15_250, meter.total)
    }

    @Test
    fun 거리요금은_132m마다_100원_남는_거리는_버린다() {
        assertNull(linesOf(TripBuilder(day).cruise(131.9, 40.0)).amount("거리요금"))
        assertEquals(100, linesOf(TripBuilder(day).cruise(132.0, 40.0)).amount("거리요금"))
        assertEquals(1_000, linesOf(TripBuilder(day).cruise(1_320.0, 40.0)).amount("거리요금"))
        // 1초씩 쪼개 더하면 실수 오차가 생기지만 한 단위를 잃으면 안 된다
        assertEquals(9_300, linesOf(TripBuilder(day).cruise(12_400.0, 37.0)).amount("거리요금"))
    }

    @Test
    fun 대기요금은_정차_31초마다_100원() {
        val start = TripBuilder(day).cruise(100.0, 30.0)
        assertNull(linesOf(TripBuilder(day).cruise(100.0, 30.0).stop(30)).amount("대기요금"))
        assertEquals(100, linesOf(start.stop(31)).amount("대기요금"))
        // 달리는 동안은 대기가 아니다
        assertNull(linesOf(TripBuilder(day).cruise(5_000.0, 10.0)).amount("대기요금"))
    }

    @Test
    fun 심야할증은_기본요금과_밤에_쌓인_100원에_20퍼센트() {
        val night = LocalDateTime.of(2026, 10, 27, 23, 0)
        val lines = linesOf(TripBuilder(night).cruise(1_320.0, 40.0))
        // 4,800 × 20% + 100원 10개 × 20원
        assertEquals(960 + 200, lines.amount("심야 할증"))
        assertEquals(4_800 + 1_000 + 1_160, lines.sumOf { it.amount })
    }

    @Test
    fun 새벽_4시가_지나면_할증이_끝난다() {
        // 03:59:40 탑승, 40km/h(약 11.1m/s)로 1,320m: 첫 100원(132m, 12초째)만 4시 전, 나머지 9개는 4시 뒤
        val lines = linesOf(TripBuilder(LocalDateTime.of(2026, 10, 27, 3, 59, 40)).cruise(1_320.0, 40.0))
        assertEquals(960 + 20, lines.amount("심야 할증"))
    }

    @Test
    fun 우천할증은_와이퍼가_도는_동안_10퍼센트() {
        // 탑승 때부터 와이퍼가 돌았고 1,320m 내내 돌았다
        val all = linesOf(TripBuilder(day).wiper(600).cruise(1_320.0, 40.0))
        assertEquals(480 + 100, all.amount("우천 할증"))
        // 와이퍼 없이 탔다가 중간부터
        val half = linesOf(TripBuilder(day).cruise(660.0, 40.0).wiper(600).cruise(660.0, 40.0))
        assertEquals(50, half.amount("우천 할증"))
    }

    @Test
    fun 급정거는_한_번_밟는_동안_한_번만_센다() {
        val normal = FakeTrip.run(TripBuilder(day).cruise(500.0, 60.0).stop(10).samples)
        assertEquals(0, normal.hardBrakes)
        assertEquals(1, normal.stops)
        val hard = FakeTrip.run(TripBuilder(day).cruise(500.0, 60.0).stop(10, hard = true).cruise(500.0, 60.0).stop(5, hard = true).samples)
        assertEquals(2, hard.hardBrakes)
        assertEquals(-100, hard.lines().amount("기사 귀책 환급"))
    }

    @Test
    fun 옵션은_켜진_분만큼_남는_초는_버린다() {
        val lines = linesOf(
            TripBuilder(day).hvac(Hvac.Heat, 119).seatHeat(59).window(180).cruise(3_000.0, 40.0),
        )
        assertEquals(30, lines.amount("난방 옵션")) // 1분 59초 → 1분
        assertNull(lines.amount("엉따 옵션")) // 59초 → 0분
        assertEquals(30, lines.amount("환기 서비스")) // 3분 × 10원
        assertNull(lines.amount("냉방 옵션"))
    }

    @Test
    fun 경유지는_한_번에_500원() {
        val meter = FakeTrip.run(TripBuilder(day).cruise(1_000.0, 40.0).samples)
        meter.addWaypoint()
        meter.addWaypoint()
        assertEquals(FareLine("경유지 추가", "2곳", 1_000), meter.lines().first { it.label == "경유지 추가" })
    }
}
