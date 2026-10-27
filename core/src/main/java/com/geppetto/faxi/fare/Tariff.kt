package com.geppetto.faxi.fare

import java.time.LocalDateTime

/**
 * FAXI 요금표(기획서 "요금표" 그대로). 금액은 모두 원, 1원 단위.
 * 가족마다 바꿀 수 있게 값으로 둔다(시즌 요금표는 나중).
 *
 * 기획서에 없는 세부 규칙은 여기서 정했다:
 * - 정차: 속도가 stopSpeedKmh 미만인 동안. 대기요금과 "신호 N회"(정차 횟수)에 쓴다.
 * - 급정거: 감속이 hardBrakeMps2 이상. 한 번 세게 밟는 동안은 한 번으로 센다.
 * - 할증: 그 순간 조건으로 붙인다. 기본요금은 탑승 시각, 거리·대기 100원은 그 100원이 쌓인 순간 기준.
 * - 남는 거리·시간·분은 버린다(손님에게 유리하게).
 */
data class Tariff(
    val baseFare: Int = 4_800,
    /** 거리요금: distanceUnitM 미터마다 unitFare원 */
    val distanceUnitM: Double = 132.0,
    /** 대기요금: waitUnitSec 초 정차마다 unitFare원 */
    val waitUnitSec: Double = 31.0,
    val unitFare: Int = 100,
    val stopSpeedKmh: Double = 1.0,
    /** 심야할증 nightStartHour시 ~ 다음 날 nightEndHour시, nightPct% */
    val nightPct: Int = 20,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 4,
    /** 우천할증: 와이퍼가 도는 동안 rainPct% */
    val rainPct: Int = 10,
    val hvacPerMin: Int = 30,
    val seatHeatPerMin: Int = 20,
    val windowPerMin: Int = 10,
    val waypointFare: Int = 500,
    /** 기사 귀책 환급: 급정거 1회당 손님에게 돌려주는 금액 */
    val hardBrakeRefund: Int = 50,
    val hardBrakeMps2: Double = 3.0,
) {
    fun isNight(at: LocalDateTime): Boolean = at.hour >= nightStartHour || at.hour < nightEndHour
}

enum class Hvac { Off, Cool, Heat }

/**
 * 차가 주는 값 한 번. 운행 동안 일정 간격(예: 1초)으로 FareMeter에 넣는다.
 * 지금은 FakeTrip이 만들고, 차량에서는 Vehicle SDK 값으로 채운다(Odometer·속도·HVAC·Seat·Wiper·Window).
 * 단위를 SDK와 맞추는 일은 SDK 문서를 확인한 뒤에 한다(추측하지 않는다).
 */
data class VehicleSample(
    val at: LocalDateTime,
    /** 누적 주행거리(m). 운행 시작 값과의 차이만 쓴다. */
    val odometerM: Double,
    val speedKmh: Double,
    val hvac: Hvac = Hvac.Off,
    val seatHeat: Boolean = false,
    val wiper: Boolean = false,
    val windowOpen: Boolean = false,
)
