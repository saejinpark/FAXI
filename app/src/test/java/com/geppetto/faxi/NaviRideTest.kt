package com.geppetto.faxi

import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.NaviReading
import com.geppetto.faxi.data.WaypointChange
import com.geppetto.faxi.data.shortPlace
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 내비 값(NaviFeed가 넘기는 NaviReading·경유지 변화)이 영수증 장소와 경유지 요금이 되는지 */
class NaviRideTest {
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val store = FaxiStore.sample { now }

    private fun board() {
        store.selectDriver("dad")
        store.startRide("mom")
    }

    private fun waypointLine() = store.arrival!!.lines.firstOrNull { it.label == "경유지 추가" }

    @Test
    fun 출발지는_탑승_때_주소_도착지는_내비가_알린_도착_장소() {
        store.onNavi(NaviReading(currentAddress = "경기도 성남시 분당구 판교역로 166"))
        board()
        store.onNavi(store.navi.copy(destination = "이마트 성수점"))
        store.onNavi(store.navi.copy(currentAddress = "서울 성동구 뚝섬로 379", arrivedAt = "이마트 성수점"))
        val r = store.endRide()
        assertEquals("판교역로 166", r.from)
        assertEquals("이마트 성수점", r.to)
    }

    @Test
    fun 도착을_못_받으면_목적지_목적지도_없으면_지금_주소() {
        store.onNavi(NaviReading(currentAddress = "서울 강남구 테헤란로 152"))
        board()
        store.onNavi(store.navi.copy(currentAddress = "서울 송파구 올림픽로 300"))
        assertEquals("올림픽로 300", store.endRide().to)

        board()
        store.onNavi(store.navi.copy(destination = "할머니댁"))
        assertEquals("할머니댁", store.endRide().to)
    }

    @Test
    fun 내비가_없는_기기는_시연_장소_이름() {
        board()
        val r = store.endRide()
        assertEquals("우리집", r.from)
        assertEquals("이마트 성수점", r.to)
    }

    @Test
    fun 경유지는_한_곳에_500원_빼면_돌려준다() {
        board()
        store.onWaypoint(WaypointChange.Added)
        store.onWaypoint(WaypointChange.Added)
        store.onWaypoint(WaypointChange.Added)
        store.onWaypoint(WaypointChange.Removed)
        store.endRide()
        assertEquals(1_000, waypointLine()!!.amount)
        assertEquals("2곳", waypointLine()!!.detail)
    }

    @Test
    fun 경유지를_모두_빼거나_운행_전에_넣은_것은_요금이_없다() {
        store.onWaypoint(WaypointChange.Added) // 탑승 전
        board()
        store.onWaypoint(WaypointChange.Added)
        store.onWaypoint(WaypointChange.AllRemoved)
        store.endRide()
        assertNull(waypointLine())
    }

    @Test
    fun 주소는_끝의_두_마디만() {
        assertEquals("판교역로 166", shortPlace("경기도 성남시 분당구 판교역로 166"))
        assertEquals("우리집", shortPlace("우리집"))
        assertEquals("성수역 2번출구", shortPlace(" 성수역 2번출구 "))
    }
}
