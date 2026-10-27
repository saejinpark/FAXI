package com.geppetto.faxi

import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.geppetto.faxi.data.AndroidSpeaker
import com.geppetto.faxi.data.Speaker
import com.geppetto.faxi.data.won
import com.geppetto.faxi.ui.shell.AppShell
import com.geppetto.faxi.ui.theme.FaxiTheme

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker
    private val askSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (application as FaxiApp).vehicleFeed.startSpeed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = AndroidSpeaker(this)
        // 장부는 Activity가 다시 만들어져도 살아 있도록 FaxiApp이 들고 있다
        val app = application as FaxiApp
        val store = app.store
        store.onArrival = { r ->
            speaker.speak(
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
