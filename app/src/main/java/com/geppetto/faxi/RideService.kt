package com.geppetto.faxi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 운행 중에만 도는 포그라운드 서비스. 하는 일은 "이 앱 프로세스를 살려 두기" 하나다.
 * 실제 운행에서는 운전자가 내비를 앞에 띄워 두니 FAXI는 뒤로 간다. 뒤에 있는 앱은 안드로이드가 정리할 수 있고,
 * 그러면 FaxiApp이 들고 있는 요금 엔진(차량 값)과 내비 값이 사라져 하차 요금이 시연 운행으로 바뀐다.
 * 차량 값 수신은 그대로 FaxiApp(VehicleFeed·NaviFeed)이 한다. 알림은 조용히(소리·팝업 없음).
 */
class RideService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification(intent?.getStringExtra(EXTRA_TEXT) ?: "미터기 작동 중")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
        }.onFailure { Log.w(TAG, "포그라운드로 올리지 못했어요", it) }
        return START_STICKY
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "운행 중", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.geppetto.faxi.design.R.drawable.ic_launcher_monochrome) // 앱 아이콘(:design)의 단색판
            .setContentTitle("FAXI")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "FaxiRide"
        private const val CHANNEL = "ride"
        private const val ID = 1
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            runCatching {
                context.startForegroundService(Intent(context, RideService::class.java).putExtra(EXTRA_TEXT, text))
            }.onFailure { Log.w(TAG, "운행 서비스를 시작하지 못했어요", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RideService::class.java))
        }
    }
}
