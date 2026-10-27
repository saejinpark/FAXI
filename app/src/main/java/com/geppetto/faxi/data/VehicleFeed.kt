package com.geppetto.faxi.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.geppetto.faxi.fare.Hvac

/**
 * 차량 값 창구. 요금 엔진이 쓰는 값을 실제 차에서 받는다.
 * - 누적 주행거리(km): Pleos Vehicle SDK `Odometer.registerDistanceDriven` (권한 pleos.car.permission.CAR_DRIVE)
 * - 기어(P/R/N/D): Pleos Vehicle SDK `Gear.registerGearSelection` (CAR_DRIVE)
 * - 속도(m/s): Pleos SDK에는 속도 도메인이 없어서, Pleos FAQ 권장대로 Android Car API의
 *   `PERF_VEHICLE_SPEED`(클러스터와 같은 값)를 CarPropertyManager로 받는다 (android.car.permission.CAR_SPEED, 실행 중 허용).
 * - 옵션 요금(SDK, 모두 읽기만):
 *   - 냉난방: `HVAC` 전원·에어컨(실내 전체 구역), 설정 온도·실내 온도(운전석) (CAR_CLIMATE). 판정은 [hvacMode].
 *   - 엉따: `Seat.registerHvacSeatTemperature` 조수석(손님 자리) 값이 0보다 크면 열선 (CAR_SEATS). 음수는 통풍.
 *   - 와이퍼: `Wiper.registerWiperState` 앞유리 ON (CAR_WINDOWS)
 *   - 창문: `Window.registerWindowPosition` 1·2열 네 창 중 하나라도 0%보다 열리면 (CAR_WINDOWS)
 * - 주행 규제(CarUxRestrictions): Pleos 주행 규제 개발 가이드대로 플랫폼이 "주행 중 최적화 필요"라고 하면 그 상태를 그대로 넘긴다.
 *   기어와 따로 본다(기어는 하차 판정, 규제는 화면 잠금). 권한은 필요 없다.
 * 권한·지원 여부는 칸마다 다를 수 있어서 하나가 실패해도 나머지는 계속 붙인다.
 *
 * 콜백이 어느 스레드에서 오는지 문서에 없어서 모두 메인 스레드로 옮겨 [onUpdate]에 넘긴다(장부는 메인에서만 바꾼다).
 * 폰·폴드처럼 차가 아닌 기기, 또는 Pleos 차량 서비스가 없는 이미지에서는 [start]가 false를 돌려주고 아무 값도 오지 않는다.
 */
class VehicleFeed(private val context: Context, private val onUpdate: (VehicleReading) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    /** 주행 규제는 권한 없이 받을 수 있어서 속도용 Car와 따로 연결한다 */
    private var uxCar: Car? = null
    private var reading = VehicleReading()

    private val distance = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) {
            if (km != null) post { reading.copy(odometerKm = km.toDouble()) }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "주행거리 값 오류", e)
        }
    }
    private val gear = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            post { reading.copy(gear = gear?.toGear() ?: Gear.Unknown) }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 값 오류", e)
        }
    }
    /** stop()에서 풀 등록들 */
    private val unregister = mutableListOf<(Vehicle) -> Unit>()

    private val power = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(on: Boolean?) {
            post { reading.copy(hvacPower = on) }
        }
        override fun onFailed(e: Exception) = warn("공조 전원", e)
    }
    private val ac = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(on: Boolean?) {
            post { reading.copy(ac = on) }
        }
        override fun onFailed(e: Exception) = warn("에어컨", e)
    }
    private val target = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(c: Float?) {
            post { reading.copy(targetTempC = c) }
        }
        override fun onFailed(e: Exception) = warn("설정 온도", e)
    }
    private val cabinTemp = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(c: Float?) {
            post { reading.copy(cabinTempC = c) }
        }
        override fun onFailed(e: Exception) = warn("실내 온도", e)
    }
    private val seat = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(level: Int?) {
            post { reading.copy(passengerSeatTemp = level) }
        }
        override fun onFailed(e: Exception) = warn("시트 온도", e)
    }
    private val wiper = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            post { reading.copy(wiperOn = state?.let { it == WiperState.ON }) }
        }
        override fun onFailed(e: Exception) = warn("와이퍼", e)
    }
    private val windows = Windows.associateWith { w ->
        object : WindowPositionListener {
            override fun onWindowPositionUpdated(percent: Int?) {
                post { reading.copy(windowPercent = reading.windowPercent + (w.name to (percent ?: 0))) }
            }
            override fun onFailed(e: Exception) = warn("창문 ${w.name}", e)
        }
    }

    private val speed = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val ms = (value.value as? Float) ?: return
            post { reading.copy(speedKmh = ms * 3.6) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "속도 값 오류 $propertyId")
        }
    }

    /** 차량 값 받기 시작. 하나라도 붙으면 true. */
    fun start(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        val pleos = runCatching {
            Vehicle(context).also { v ->
                v.initialize()
                v.getOdometer().registerDistanceDriven(distance)
                v.getGear().registerGearSelection(gear)
                vehicle = v
                registerOptions(v)
            }
        }.onFailure { Log.w(TAG, "Pleos Vehicle SDK를 쓸 수 없어요", it) }.isSuccess
        val ux = startUxRestrictions()
        return startSpeed() || pleos || ux
    }

    private fun startUxRestrictions(): Boolean = runCatching {
        val c = Car.createCar(context) ?: return false
        val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        val apply = { r: android.car.drivingstate.CarUxRestrictions? ->
            val restricted = r?.isRequiresDistractionOptimization == true
            post { reading.copy(uxRestricted = restricted) }
        }
        apply(ux.currentCarUxRestrictions)
        ux.registerListener { apply(it) }
        uxCar = c
        true
    }.onFailure { Log.w(TAG, "주행 규제 상태를 받을 수 없어요", it) }.getOrDefault(false)

    /**
     * 속도는 권한(CAR_SPEED)을 허용받은 뒤에야 붙는다. MainActivity가 허용받으면 다시 부른다.
     * @return 붙었으면 true
     */
    fun startSpeed(): Boolean {
        if (car != null) return true
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val c = Car.createCar(context) ?: return false
            val props = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            @Suppress("DEPRECATION") // subscribePropertyEvents는 API 35부터. Pleos 이미지는 API 34라 registerCallback을 쓴다
            props.registerCallback(speed, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            car = c
            true
        }.onFailure { Log.w(TAG, "속도를 받을 수 없어요", it) }.getOrDefault(false)
    }

    /** 옵션 요금 값. 칸마다 따로 붙여서 하나가 막혀도(권한·차종 미지원) 나머지는 받는다. */
    private fun registerOptions(v: Vehicle) {
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val passenger = SeatArea(SeatValue.ROW_1_RIGHT)
        // 공조 전원·에어컨은 좌석 하나가 아니라 실내 전체 구역 값으로 온다(에뮬레이터 로그: areaId 117 = 1열 좌·우 + 2열 좌·중·우).
        // 운전석(1)으로 등록하면 아무 값도 오지 않는다.
        val cabinZone = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        fun attach(name: String, on: () -> Unit, off: (Vehicle) -> Unit) {
            runCatching(on).onSuccess { unregister += off }.onFailure { warn(name, it) }
        }
        attach("공조 전원", { v.getHVAC().registerHvacPowerState(cabinZone, power) }) { it.getHVAC().unregisterHvacPowerState(cabinZone, power) }
        attach("에어컨", { v.getHVAC().registerHvacAcState(cabinZone, ac) }) { it.getHVAC().unregisterHvacAcState(cabinZone, ac) }
        attach("설정 온도", { v.getHVAC().registerHvacTemperature(driver, target) }) { it.getHVAC().unregisterHvacTemperature(driver, target) }
        attach("실내 온도", { v.getHVAC().registerHvacTemperatureCurrent(driver, cabinTemp) }) {
            it.getHVAC().unregisterHvacTemperatureCurrent(driver, cabinTemp)
        }
        attach("시트 온도", { v.getSeat().registerHvacSeatTemperature(passenger, seat) }) { it.getSeat().unregisterHvacSeatTemperature(passenger, seat) }
        attach("와이퍼", { v.getWiper().registerWiperState(front, wiper) }) { it.getWiper().unregisterWiperState(front, wiper) }
        windows.forEach { (w, l) ->
            val area = WindowArea(w)
            attach("창문 ${w.name}", { v.getWindow().registerWindowPosition(area, l) }) { it.getWindow().unregisterWindowPosition(area, l) }
        }
    }

    fun stop() {
        vehicle?.let { v ->
            runCatching {
                v.getOdometer().unregisterDistanceDriven(distance)
                v.getGear().unregisterGearSelection(gear)
                unregister.forEach { off -> runCatching { off(v) } }
                unregister.clear()
                v.release()
            }
        }
        vehicle = null
        car?.let { c ->
            runCatching {
                @Suppress("DEPRECATION")
                (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(speed)
                c.disconnect()
            }
        }
        car = null
        uxCar?.let { c ->
            runCatching {
                (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).unregisterListener()
                c.disconnect()
            }
        }
        uxCar = null
    }

    private fun post(next: () -> VehicleReading) = main.post {
        reading = next()
        onUpdate(reading)
    }

    private fun warn(what: String, e: Throwable) {
        Log.w(TAG, "$what 값 오류", e)
    }

    private companion object {
        const val TAG = "FaxiVehicle"
        /** 손님이 열 수 있는 창(1·2열). 지붕·앞뒤 유리는 뺀다. */
        val Windows = listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
    }
}

/** 지금 차량 값. 아직 안 온 값은 null(요금 엔진은 속도가 와야 쓴다). */
data class VehicleReading(
    val odometerKm: Double? = null,
    val speedKmh: Double? = null,
    val gear: Gear? = null,
    val hvacPower: Boolean? = null,
    val ac: Boolean? = null,
    val targetTempC: Float? = null,
    val cabinTempC: Float? = null,
    /** 조수석 시트 온도 단계. 양수 열선, 음수 통풍, 0 꺼짐 */
    val passengerSeatTemp: Int? = null,
    val wiperOn: Boolean? = null,
    /** 창 이름 → 열린 정도(%) */
    val windowPercent: Map<String, Int> = emptyMap(),
    /** 플랫폼 주행 규제: 주행 중 최적화(DO) 화면만 보여야 하는 상태 */
    val uxRestricted: Boolean = false,
) {
    /** 옵션 상태가 하나도 안 왔으면 null(그때는 시연 기본값을 그대로 둔다). 안 온 칸은 꺼짐으로 본다. */
    fun cabin(): CabinState? {
        val none = hvacPower == null && ac == null && passengerSeatTemp == null && wiperOn == null && windowPercent.isEmpty()
        if (none) return null
        return CabinState(
            hvac = hvacMode(hvacPower, ac, targetTempC, cabinTempC),
            seatHeat = (passengerSeatTemp ?: 0) > 0,
            wiper = wiperOn == true,
            windowOpen = windowPercent.values.any { it > 0 },
        )
    }
}

/**
 * 공조 상태 → 요금 판정. 차량 신호에 "난방 중"이 따로 없어서 이렇게 정한다:
 * - 공조가 꺼져 있으면 꺼짐(전원 값을 모르면 에어컨 값만 본다)
 * - 에어컨이 켜져 있으면 냉방
 * - 에어컨 없이 설정 온도가 실내 온도보다 높으면 난방(실내 온도를 모르면 난방으로 치지 않는다)
 */
fun hvacMode(power: Boolean?, ac: Boolean?, targetC: Float?, cabinC: Float?): Hvac = when {
    power == false -> Hvac.Off
    ac == true -> Hvac.Cool
    power == true && targetC != null && cabinC != null && targetC > cabinC -> Hvac.Heat
    else -> Hvac.Off
}

private fun VehicleGear.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.Parked
    VehicleGear.GEAR_UNKNOWN -> Gear.Unknown
    else -> Gear.Driving
}
