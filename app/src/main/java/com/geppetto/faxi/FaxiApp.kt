package com.geppetto.faxi

import android.app.Application
import com.geppetto.faxi.data.FaxiStorage
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.MessageServer
import com.geppetto.faxi.data.VehicleFeed

/**
 * 앱 프로세스 하나에 장부 하나. Activity는 주간/야간 테마가 바뀔 때 다시 만들어지지만
 * Application은 살아 있으므로 장부를 여기에 둔다(SwiftUI의 App 구조체에 @State로 두는 것과 비슷).
 * 처음 설치 때는 저장 파일이 없으니 시연 데이터로 시작한다.
 */
class FaxiApp : Application() {
    lateinit var store: FaxiStore
        private set
    /** 차량 값 창구. Activity가 다시 만들어져도(주간/야간) 끊기지 않게 앱이 들고 있다. */
    lateinit var vehicleFeed: VehicleFeed
        private set

    override fun onCreate() {
        super.onCreate()
        val storage = FaxiStorage(filesDir)
        val snapshot = storage.load()
        store = if (snapshot != null) FaxiStore(snapshot) else FaxiStore.sample()
        store.onChanged = { storage.save(store.snapshot()) }
        if (snapshot == null) storage.save(store.snapshot())
        // 손님 폰의 한마디를 받는 창구(같은 Wi-Fi). 앱 프로세스가 사는 동안 연다.
        MessageServer(this, store).start()
        // 주행거리·기어(Pleos Vehicle SDK)와 속도(Android Car API). 차가 아니면 아무 값도 오지 않고 시연 운행으로 대신한다.
        vehicleFeed = VehicleFeed(this, store::onVehicle).also { it.start() }
    }
}
