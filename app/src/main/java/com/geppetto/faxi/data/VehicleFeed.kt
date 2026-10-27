package com.geppetto.faxi.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener

/**
 * 차량 값 창구. 요금 엔진이 쓰는 값 세 가지를 실제 차에서 받는다.
 * - 누적 주행거리(km): Pleos Vehicle SDK `Odometer.registerDistanceDriven` (권한 pleos.car.permission.CAR_DRIVE)
 * - 기어(P/R/N/D): Pleos Vehicle SDK `Gear.registerGearSelection` (CAR_DRIVE)
 * - 속도(m/s): Pleos SDK에는 속도 도메인이 없어서, Pleos FAQ 권장대로 Android Car API의
 *   `PERF_VEHICLE_SPEED`(클러스터와 같은 값)를 CarPropertyManager로 받는다 (android.car.permission.CAR_SPEED, 실행 중 허용).
 *
 * 콜백이 어느 스레드에서 오는지 문서에 없어서 모두 메인 스레드로 옮겨 [onUpdate]에 넘긴다(장부는 메인에서만 바꾼다).
 * 폰·폴드처럼 차가 아닌 기기, 또는 Pleos 차량 서비스가 없는 이미지에서는 [start]가 false를 돌려주고 아무 값도 오지 않는다.
 */
class VehicleFeed(private val context: Context, private val onUpdate: (VehicleReading) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
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
            }
        }.onFailure { Log.w(TAG, "Pleos Vehicle SDK를 쓸 수 없어요", it) }.isSuccess
        return startSpeed() || pleos
    }

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

    fun stop() {
        vehicle?.let { v ->
            runCatching {
                v.getOdometer().unregisterDistanceDriven(distance)
                v.getGear().unregisterGearSelection(gear)
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
    }

    private fun post(next: () -> VehicleReading) = main.post {
        reading = next()
        onUpdate(reading)
    }

    private companion object {
        const val TAG = "FaxiVehicle"
    }
}

/** 지금 차량 값. 아직 안 온 값은 null(요금 엔진은 거리·속도가 다 와야 쓴다). */
data class VehicleReading(
    val odometerKm: Double? = null,
    val speedKmh: Double? = null,
    val gear: Gear? = null,
)

private fun VehicleGear.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.Parked
    VehicleGear.GEAR_UNKNOWN -> Gear.Unknown
    else -> Gear.Driving
}
