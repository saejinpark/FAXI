package com.geppetto.faxi

import com.geppetto.faxi.data.CabinState
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Gear
import com.geppetto.faxi.data.VehicleReading
import com.geppetto.faxi.data.hvacMode
import com.geppetto.faxi.fare.Hvac
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
        reading = reading.copy(odometerKm = odometerKm, speedKmh = speedKmh, gear = gear)
        store.onVehicle(reading)
    }

    /** 옵션 값만 바꿔서 한 번 넣는다 */
    private fun option(change: (VehicleReading) -> VehicleReading) {
        reading = change(reading)
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

    @Test
    fun 옵션_요금은_차량_값이_켜진_분만큼() {
        // 낮 9시, 공조·엉따·창문 모두 꺼진 채 탑승
        option { it.copy(hvacPower = true, ac = false, targetTempC = 22f, cabinTempC = 22f, passengerSeatTemp = 0, wiperOn = false) }
        board()
        feed(gear = Gear.Driving)
        option { it.copy(ac = true) }            // 냉방 2분
        hold(120)
        option { it.copy(ac = false, targetTempC = 26f) } // 에어컨 끄고 설정 온도 올림 → 난방 1분
        hold(60)
        option { it.copy(hvacPower = false, passengerSeatTemp = 2) } // 공조 끔, 조수석 열선 2단 3분
        hold(180)
        option { it.copy(passengerSeatTemp = -1, windowPercent = mapOf("ROW_2_LEFT" to 30)) } // 통풍은 공짜, 뒷창 1분
        hold(60)
        feed(gear = Gear.Parked)

        val lines = store.arrival!!.lines
        fun amount(label: String) = lines.firstOrNull { it.label == label }?.amount
        assertEquals(60, amount("냉방 옵션"))
        assertEquals(30, amount("난방 옵션"))
        assertEquals(60, amount("엉따 옵션"))
        assertEquals(10, amount("환기 서비스"))
    }

    @Test
    fun 옵션_값이_하나도_안_오면_시연_기본값을_둔다() {
        feed(gear = Gear.Parked)
        assertEquals(CabinState.Demo, store.cabin)
        option { it.copy(wiperOn = true) }
        assertEquals(CabinState(Hvac.Off, seatHeat = false, wiper = true, windowOpen = false), store.cabin)
    }

    @Test
    fun 공조_판정() {
        assertEquals(Hvac.Off, hvacMode(power = false, ac = true, targetC = 18f, cabinC = 25f))
        assertEquals(Hvac.Cool, hvacMode(power = true, ac = true, targetC = 18f, cabinC = 25f))
        assertEquals(Hvac.Cool, hvacMode(power = null, ac = true, targetC = null, cabinC = null))
        assertEquals(Hvac.Heat, hvacMode(power = true, ac = false, targetC = 26f, cabinC = 18f))
        assertEquals(Hvac.Off, hvacMode(power = true, ac = false, targetC = 22f, cabinC = 22f))
        // 실내 온도를 모르면 난방으로 치지 않는다(손님에게 불리하게 매기지 않기)
        assertEquals(Hvac.Off, hvacMode(power = true, ac = false, targetC = 26f, cabinC = null))
    }
}
