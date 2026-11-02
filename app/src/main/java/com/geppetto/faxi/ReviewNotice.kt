package com.geppetto.faxi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 손님 폰에서 후기가 오면 차에 알림 하나(누르면 FAXI가 열린다).
 * 후기 글은 알림에 싣지 않는다: 다음 운행 중에 와도 운전자가 긴 글을 읽지 않게, 누가 남겼는지만 말하고 글은 주차 중 화면과 낭독으로 본다.
 */
object ReviewNotice {
    private const val CHANNEL = "reviews"
    private const val ID = 2

    fun post(context: Context, passenger: String, driver: String, stars: Int) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "손님 후기", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.geppetto.faxi.design.R.drawable.ic_launcher_monochrome)
                .setContentTitle("후기가 등록됐어요")
                .setContentText("$passenger 손님이 $driver 기사에게 별 ${stars}개를 남겼어요. 차를 세우면 볼 수 있어요.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(ID, notification)
        }.onFailure { Log.w("FaxiReview", "후기 알림을 띄우지 못했어요", it) }
    }
}
