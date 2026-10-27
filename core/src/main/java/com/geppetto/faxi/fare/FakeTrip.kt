package com.geppetto.faxi.fare

import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

/**
 * 가짜 운행: 1초 간격 차량 값을 만들어 낸다. 폰·에뮬레이터 시연과 단위 테스트에 쓴다.
 * 차량에서는 Vehicle SDK가 같은 모양의 값을 준다.
 *
 * 예: TripBuilder(start).hvac(Hvac.Cool, 870).cruise(1500.0, 40.0).stop(30).finishAt(12_400.0, 30.0).samples
 */
class TripBuilder(start: LocalDateTime) {
    private var t = start
    private var odometer = 0.0
    private var speed = 0.0

    private var hvac = Hvac.Off
    private var hvacUntil = start
    private var seatHeatUntil = start
    private var wiperUntil = start
    private var windowUntil = start

    private val _samples = mutableListOf(sample())
    val samples: List<VehicleSample> get() = _samples

    /** 지금부터 seconds초 동안 냉난방 */
    fun hvac(mode: Hvac, seconds: Long) = apply {
        hvac = mode
        hvacUntil = t.plusSeconds(seconds)
        _samples[_samples.lastIndex] = sample()
    }

    fun seatHeat(seconds: Long) = apply { seatHeatUntil = t.plusSeconds(seconds); _samples[_samples.lastIndex] = sample() }
    fun wiper(seconds: Long) = apply { wiperUntil = t.plusSeconds(seconds); _samples[_samples.lastIndex] = sample() }
    fun window(seconds: Long) = apply { windowUntil = t.plusSeconds(seconds); _samples[_samples.lastIndex] = sample() }

    /** kmh로 meters만큼 달린다(가속은 바로). 마지막 1초는 남은 거리만큼만 움직인다. */
    fun cruise(meters: Double, kmh: Double) = apply {
        var left = meters
        while (left > 1e-9) {
            val step = min(kmh / 3.6, left)
            tick(step, kmh)
            left -= step
        }
    }

    /**
     * 멈춰서 seconds초 서 있는다(신호 대기). 감속은 보통 2m/s², hard면 6m/s²(급정거).
     * 감속하는 동안 달린 거리도 주행거리에 들어간다.
     */
    fun stop(seconds: Int, hard: Boolean = false) = apply {
        val rate = if (hard) 6.0 else 2.0
        while (speed > 0) {
            val v0 = speed / 3.6
            val v1 = max(0.0, v0 - rate)
            tick((v0 + v1) / 2, v1 * 3.6)
        }
        repeat(seconds) { tick(0.0, 0.0) }
    }

    /** 총 주행거리가 totalMeters가 되도록 남은 거리를 kmh로 달린다 */
    fun finishAt(totalMeters: Double, kmh: Double) = apply {
        require(totalMeters >= odometer) { "이미 ${odometer}m를 달렸어요" }
        cruise(totalMeters - odometer, kmh)
    }

    private fun tick(meters: Double, newSpeedKmh: Double) {
        t = t.plusSeconds(1)
        odometer += meters
        speed = newSpeedKmh
        _samples += sample()
    }

    private fun sample() = VehicleSample(
        at = t,
        odometerM = odometer,
        speedKmh = speed,
        hvac = if (t < hvacUntil) hvac else Hvac.Off,
        seatHeat = t < seatHeatUntil,
        wiper = t < wiperUntil,
        windowOpen = t < windowUntil,
    )
}

object FakeTrip {
    /**
     * 기획서 영수증 예시 운행(우리집 → 이마트 성수점):
     * 12.4km, 신호 7번(정차 합계 200초), 냉방 14분, 엉따 9분, 급정거 1번(다섯 번째 신호), 낮 시간·맑음.
     * → 기본 4,800 + 거리 9,300 + 대기 600 + 냉방 420 + 엉따 180 − 환급 50 = 15,250원
     */
    fun planExample(start: LocalDateTime): List<VehicleSample> =
        TripBuilder(start)
            .hvac(Hvac.Cool, 14 * 60 + 30)
            .seatHeat(9 * 60 + 20)
            .cruise(1_500.0, 40.0).stop(30)
            .cruise(2_000.0, 50.0).stop(25)
            .cruise(1_800.0, 40.0).stop(40)
            .cruise(1_500.0, 40.0).stop(20)
            .cruise(2_000.0, 60.0).stop(35, hard = true)
            .cruise(1_500.0, 40.0).stop(28)
            .cruise(1_000.0, 30.0).stop(22)
            .finishAt(12_400.0, 30.0)
            .samples

    /** 차량 값 전체를 미터기에 흘려 넣고 미터기를 돌려준다 */
    fun run(samples: List<VehicleSample>, tariff: Tariff = Tariff()): FareMeter =
        FareMeter(samples.first(), tariff).apply { samples.drop(1).forEach(::update) }
}
