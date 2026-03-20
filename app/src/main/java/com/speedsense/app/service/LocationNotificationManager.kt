package com.speedsense.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.speedsense.app.R
import com.speedsense.app.ui.MainActivity

object LocationNotificationManager {
    const val CHANNEL_ID = "speed_monitoring_channel"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.subtitle)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun buildNotification(
        context: Context,
        speedLimit: Int? = null,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = speedLimit?.let {
            context.getString(R.string.notification_speed_limit_format, it)
        } ?: context.getString(R.string.notification_text_idle)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateNotification(context: Context, speedLimit: Int?) {
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                buildNotification(context, speedLimit),
            )
        }
    }
}
