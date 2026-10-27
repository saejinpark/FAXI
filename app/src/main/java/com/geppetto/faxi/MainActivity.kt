package com.geppetto.faxi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.geppetto.faxi.data.AndroidSpeaker
import com.geppetto.faxi.data.Speaker
import com.geppetto.faxi.ui.shell.AppShell
import com.geppetto.faxi.ui.theme.FaxiTheme

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = AndroidSpeaker(this)
        // 장부는 Activity가 다시 만들어져도 살아 있도록 FaxiApp이 들고 있다
        val store = (application as FaxiApp).store
        setContent {
            FaxiTheme {
                AppShell(store, speak = speaker::speak, demo = true)
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
