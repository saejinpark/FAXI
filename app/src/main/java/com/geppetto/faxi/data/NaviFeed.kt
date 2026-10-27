package com.geppetto.faxi.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/**
 * 내비 창구: Pleos NaviHelper SDK(`ai.pleos.playground:NaviHelper`, 권한 pleos.car.permission.NAVI_ROUTE, 읽기만).
 * - 지금 위치 주소: `getCurrentLocationInfo()`를 15초마다 물어본다(출발지, 목적지 없이 내렸을 때의 도착지).
 * - 목적지 이름: `getDestinationInfo()`. 경로 안내가 시작되거나 목적지가 바뀌면 다시 묻는다.
 * - 도착: `onDestinationArrived`의 장소 이름.
 * - 경유지: `onWaypointChanged`의 추가/삭제 → 경유지 추가 요금.
 * 어떤 이벤트가 오는지는 내비 앱 구현과 상태에 따라 다르다(공식 문서). 콜백 스레드는 문서에 없어서 메인으로 옮긴다.
 * 차가 아니거나 내비 서비스가 없으면 아무 값도 오지 않는다(그때 영수증은 시연 장소 이름).
 */
class NaviFeed(
    private val context: Context,
    private val onUpdate: (NaviReading) -> Unit,
    private val onWaypoint: (WaypointChange) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null
    private var reading = NaviReading()

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            post { reading.copy(currentAddress = info.address.ifBlank { null } ?: reading.currentAddress) }
        }
        override fun onDestinationInfo(info: DestinationInfo) {
            post { reading.copy(destination = info.name.ifBlank { info.address }.ifBlank { null }) }
        }
        override fun onRouteStarted(info: RouteStartInfo) {
            post { reading.copy(destination = info.name.ifBlank { null } ?: reading.destination) }
            navi?.getDestinationInfo()
        }
        override fun onDestinationChanged() {
            navi?.getDestinationInfo()
        }
        override fun onRouteCancelled() {
            post { reading.copy(destination = null) }
        }
        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            post { reading.copy(arrivedAt = info.name.ifBlank { info.address }.ifBlank { null }) }
        }
        override fun onWaypointChanged(info: WaypointChangedInfo) {
            val change = when {
                info.cmdType == WaypointCmdType.ADD -> WaypointChange.Added
                info.index == WaypointIndex.ALL -> WaypointChange.AllRemoved
                else -> WaypointChange.Removed
            }
            main.post { onWaypoint(change) }
        }
        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "내비 오류 $code")
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            runCatching { navi?.getCurrentLocationInfo() }
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        return runCatching {
            val n = NaviHelper(context)
            n.initialize(
                {
                    main.post {
                        runCatching { n.getDestinationInfo() }
                        poll.run()
                    }
                },
                { e -> Log.w(TAG, "NaviHelper 초기화 실패", e) },
            )
            n.addListener(listener)
            navi = n
        }.onFailure { Log.w(TAG, "NaviHelper를 쓸 수 없어요", it) }.isSuccess
    }

    fun stop() {
        main.removeCallbacks(poll)
        navi?.let { n -> runCatching { n.removeListener(listener); n.release() } }
        navi = null
    }

    private fun post(next: () -> NaviReading) = main.post {
        reading = next()
        onUpdate(reading)
    }

    private companion object {
        const val TAG = "FaxiNavi"
        const val POLL_MS = 15_000L
    }
}

/** 내비에서 받은 지금 값. 안 온 값은 null */
data class NaviReading(
    val currentAddress: String? = null,
    val destination: String? = null,
    /** 가장 최근에 도착한 목적지 이름 */
    val arrivedAt: String? = null,
) {
    val connected: Boolean get() = currentAddress != null || destination != null || arrivedAt != null
}

enum class WaypointChange { Added, Removed, AllRemoved }

/**
 * 영수증에 쓸 짧은 장소 이름. 주소는 끝의 도로명·번지 두 마디만 남긴다.
 * "경기도 성남시 분당구 판교역로 166" → "판교역로 166"
 */
fun shortPlace(address: String): String {
    val words = address.trim().split(Regex("\\s+"))
    return if (words.size <= 2) address.trim() else words.takeLast(2).joinToString(" ")
}
