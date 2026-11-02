package com.geppetto.faxi

import android.Manifest
import android.car.Car
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.geppetto.faxi.data.Speaker
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.shell.AppShell
import com.geppetto.faxi.ui.theme.FaxiTheme

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker
    private val askSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (application as FaxiApp).vehicleFeed.startSpeed()
        askNotifications()
    }
    // 후기 알림(ReviewNotice). 거절해도 후기는 도착 화면·기사 상세·낭독으로 그대로 본다
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = Speaker.create(this)
        // 장부는 Activity가 다시 만들어져도 살아 있도록 FaxiApp이 들고 있다
        val app = application as FaxiApp
        val store = app.store
        // 시연 준비 스크립트(tools/demo.sh, 영상 촬영): 처음 켤 때 가장 등록 대신 시연 가족을 바로 불러온다
        if (intent?.getBooleanExtra("demo", false) == true && store.needsSetup) store.loadDemo()
        store.onArrival = { r ->
            if (store.settings.fareAloud) speaker.speak(
                if (r.toLifetime) {
                    "도착했습니다. 오늘 요금 ${won(r.fareTotal)}원은 평생 장부에 쌓아 둘게요."
                } else {
                    "도착했습니다. 요금은 ${won(r.billed)}원입니다."
                },
            )
        }
        // 속도(CAR_SPEED)는 dangerous 권한이라 처음 한 번 묻는다. 허용하면 바로 속도를 받기 시작한다.
        if (!app.vehicleFeed.startSpeed() && packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            askSpeed.launch(Car.PERMISSION_SPEED)
        } else {
            askNotifications()
        }
        setContent {
            FaxiTheme {
                AppShell(store, speak = speaker::speak, demo = true)
            }
        }
    }

    override fun onDestroy() {
        (application as FaxiApp).store.onArrival = null
        speaker.shutdown()
        super.onDestroy()
    }
}
