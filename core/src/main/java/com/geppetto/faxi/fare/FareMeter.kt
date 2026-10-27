package com.geppetto.faxi.fare

import java.time.Duration
import kotlin.math.floor

/**
 * 미터기. 탑승 인증 순간의 차량 값으로 켜고, 이후 값이 올 때마다 update로 쌓는다.
 * 안드로이드에 기대지 않는 순수 Kotlin이라 폰 앱과 같이 쓸 수 있고 단위 테스트로 검증한다.
 *
 * 두 값 사이 구간은 "앞 값"의 상태로 센다(에어컨이 켜져 있던 구간, 와이퍼가 돌던 구간 등).
 * 거리·대기 100원은 누적 거리·누적 정차 시간에서 매번 다시 세어서 실수 오차가 쌓이지 않게 한다.
 */
class FareMeter(
    start: VehicleSample,
    val tariff: Tariff = Tariff(),
) {
    private val startOdometer = start.odometerM
    private val nightAtBoarding = tariff.isNight(start.at)
    private val rainAtBoarding = start.wiper
    private var prev = start

    var distanceM = 0.0
        private set
    private var waitSec = 0.0

    private var distanceUnits = 0
    private var waitUnits = 0
    /** 할증이 붙은 100원 개수 */
    private var nightUnits = 0
    private var rainUnits = 0

    /** 정차로 바뀐 횟수(영수증 "신호 N회") */
    var stops = 0
        private set
    var hardBrakes = 0
        private set
    private var braking = false

    private var coolSec = 0.0
    private var heatSec = 0.0
    private var seatHeatSec = 0.0
    private var windowSec = 0.0
    var waypoints = 0
        private set

    fun update(s: VehicleSample) {
        val dt = Duration.between(prev.at, s.at).toMillis() / 1000.0
        require(dt >= 0) { "차량 값은 시간 순서대로 넣어야 해요" }

        // 켜져 있던 구간만큼 옵션 시간
        when (prev.hvac) {
            Hvac.Cool -> coolSec += dt
            Hvac.Heat -> heatSec += dt
            Hvac.Off -> Unit
        }
        if (prev.seatHeat) seatHeatSec += dt
        if (prev.windowOpen) windowSec += dt

        // 정차: 구간 양 끝이 모두 기준 속도 미만일 때만
        val stopped = prev.speedKmh < tariff.stopSpeedKmh && s.speedKmh < tariff.stopSpeedKmh
        if (stopped) waitSec += dt
        if (prev.speedKmh >= tariff.stopSpeedKmh && s.speedKmh < tariff.stopSpeedKmh) stops++

        // 급정거: 감속이 기준 이상이 되는 순간 한 번. 한 번 밟은 것은 다시 속도를 올리거나 멈출 때까지 한 번이다.
        // (차량 값은 속도·주행거리가 따로따로 들어와서, 속도가 그대로인 짧은 구간이 감속 중간에 끼어도 새로 세지 않는다)
        if (dt > 0) {
            val decel = (prev.speedKmh - s.speedKmh) / 3.6 / dt
            if (decel >= tariff.hardBrakeMps2 && !braking) {
                hardBrakes++
                braking = true
            }
            if (s.speedKmh > prev.speedKmh || s.speedKmh < tariff.stopSpeedKmh) braking = false
        }

        distanceM = s.odometerM - startOdometer
        val newDistanceUnits = units(distanceM, tariff.distanceUnitM) - distanceUnits
        val newWaitUnits = units(waitSec, tariff.waitUnitSec) - waitUnits
        val newUnits = newDistanceUnits + newWaitUnits
        distanceUnits += newDistanceUnits
        waitUnits += newWaitUnits
        // 할증도 옵션처럼 구간의 앞 값 기준(와이퍼를 켠 바로 그 순간 끝난 100원에는 붙지 않는다)
        if (tariff.isNight(prev.at)) nightUnits += newUnits
        if (prev.wiper) rainUnits += newUnits

        prev = s
    }

    /** 내비에서 경유지를 추가할 때 */
    fun addWaypoint() {
        waypoints++
    }

    /** 영수증 줄. 0원인 줄은 빼고, 기본요금은 늘 넣는다. 순서는 기획서 영수증 예시를 따른다. */
    fun lines(): List<FareLine> = buildList {
        val t = tariff
        add(FareLine("기본요금", null, t.baseFare))
        if (distanceUnits > 0) add(FareLine("거리요금", km(distanceM), distanceUnits * t.unitFare))
        if (waitUnits > 0) add(FareLine("대기요금", "신호 ${stops}회", waitUnits * t.unitFare))

        val night = (if (nightAtBoarding) pct(t.baseFare, t.nightPct) else 0) + nightUnits * pct(t.unitFare, t.nightPct)
        if (night > 0) add(FareLine("심야 할증", "${t.nightPct}%", night))
        val rain = (if (rainAtBoarding) pct(t.baseFare, t.rainPct) else 0) + rainUnits * pct(t.unitFare, t.rainPct)
        if (rain > 0) add(FareLine("우천 할증", "와이퍼 ${t.rainPct}%", rain))

        option("냉방 옵션", coolSec, t.hvacPerMin)
        option("난방 옵션", heatSec, t.hvacPerMin)
        option("엉따 옵션", seatHeatSec, t.seatHeatPerMin)
        option("환기 서비스", windowSec, t.windowPerMin)
        if (waypoints > 0) add(FareLine("경유지 추가", "${waypoints}곳", waypoints * t.waypointFare))
        if (hardBrakes > 0) add(FareLine("기사 귀책 환급", "급정거 ${hardBrakes}회", -hardBrakes * t.hardBrakeRefund))
    }

    val total: Int get() = lines().sumOf { it.amount }

    private fun MutableList<FareLine>.option(label: String, seconds: Double, perMin: Int) {
        val minutes = units(seconds, 60.0)
        if (minutes > 0) add(FareLine(label, "${minutes}분", minutes * perMin))
    }

    private companion object {
        /** 실수 오차(12399.9999m 같은)로 한 단위를 잃지 않게 아주 작은 여유를 둔다 */
        const val EPS = 1e-6

        fun units(amount: Double, per: Double): Int = floor((amount + EPS) / per).toInt()

        fun pct(amount: Int, pct: Int): Int = amount * pct / 100

        /** 12400m -> "12.4km" (0.1km 아래는 버림) */
        fun km(meters: Double): String {
            val tenths = units(meters, 100.0)
            return "${tenths / 10}.${tenths % 10}km"
        }
    }
}
