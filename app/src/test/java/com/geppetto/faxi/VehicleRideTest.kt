package com.geppetto.faxi

import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Gear
import com.geppetto.faxi.data.VehicleReading
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제 차량 값(VehicleFeed가 넘기는 VehicleReading)으로 운행이 굴러가는지.
 * 시각은 clock으로 움직이고, 값은 에뮬레이터처럼 1초마다 들어온다고 본다.
 */
class VehicleRideTest {
    /** 낮 9시(심야 할증 없음) */
    private var now = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val store = FaxiStore.sample { now }
    private var reading = VehicleReading(odometerKm = 500.0, speedKmh = 0.0, gear = Gear.Parked)

    private fun feed(speedKmh: Double = reading.speedKmh!!, gear: Gear = reading.gear!!, odometerKm: Double = reading.odometerKm!!) {
        reading = VehicleReading(odometerKm, speedKmh, gear)
        store.onVehicle(reading)
    }

    /** 같은 값으로 seconds초 동안 1초마다 */
    private fun hold(seconds: Int) = repeat(seconds) {
        now = now.plusSeconds(1)
        feed()
    }

    private fun board() {
        store.selectDriver("dad")
        feed()
        store.startRide("mom")
    }

    @Test
    fun 속도를_적분한_거리로_거리요금을_매긴다() {
        board()
        feed(gear = Gear.Driving)
        feed(speedKmh = 36.0) // 초당 10m
        hold(132) // 1,320m → 132m당 100원 10번
        feed(speedKmh = 0.0)
        hold(2)
        feed(gear = Gear.Parked)

        val r = store.arrival!!
        val distance = r.lines.first { it.label == "거리요금" }
        assertEquals(1_000, distance.amount)
        assertTrue(r.km in 1..2)
        assertNull(store.passengerId)
    }

    @Test
    fun 주행거리계가_더_많이_가면_그_값을_쓴다() {
        board()
        feed(gear = Gear.Driving)
        feed(speedKmh = 3.6) // 적분으로는 초당 1m
        hold(10)
        feed(odometerKm = 502.0) // 계기판은 2km 갔다
        feed(speedKmh = 0.0, gear = Gear.Parked)

        val distance = store.arrival!!.lines.first { it.label == "거리요금" }
        assertEquals(2_000 / 132 * 100, distance.amount)
    }

    @Test
    fun 정차_시간은_대기요금_급정거는_환급() {
        board()
        feed(gear = Gear.Driving)
        feed(speedKmh = 36.0)
        hold(5)
        now = now.plusSeconds(1)
        feed(speedKmh = 0.0) // 1초에 10m/s 감속 = 급정거
        hold(62) // 31초당 100원 → 2번
        feed(gear = Gear.Parked)

        val lines = store.arrival!!.lines
        assertEquals(200, lines.first { it.label == "대기요금" }.amount)
        assertEquals(-50, lines.first { it.label == "기사 귀책 환급" }.amount)
    }

    @Test
    fun 움직이기_전_P는_하차가_아니다() {
        board()
        hold(30)
        feed(gear = Gear.Parked)
        assertEquals("mom", store.passengerId)
        assertNull(store.arrival)
    }

    @Test
    fun 운행_중이_아닐_때_D면_기어는_Driving() {
        feed(gear = Gear.Driving)
        assertEquals(Gear.Driving, store.gear)
        assertTrue(!store.rideActive)
    }
}
