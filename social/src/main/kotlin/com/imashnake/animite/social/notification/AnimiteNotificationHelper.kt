package com.imashnake.animite.social.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imashnake.animite.social.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimiteNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_SOCIAL = "animeta_social_channel"
        const val CHANNEL_EPISODES = "animeta_episodes_channel"
        private const val SOCIAL_GROUP = "animeta_social_group"
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL,
                "Social & Interactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for likes, comments, replies, and followers"
                enableLights(true)
                enableVibration(true)
            }

            val episodesChannel = NotificationChannel(
                CHANNEL_EPISODES,
                "New Anime Episodes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for newly released anime episodes in your watchlist"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(socialChannel)
            notificationManager.createNotificationChannel(episodesChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun postLocalNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
    ) {
        if (!hasNotificationPermission()) return

        try {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(
                    if (channelId == CHANNEL_EPISODES) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission denied or revoked
        }
    }
}
