package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

data class NotificationPreferences(
    val notificationsEnabled: Boolean = true,
    val directNotificationsEnabled: Boolean = true,
    val groupNotificationsEnabled: Boolean = true,
    val channelNotificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val showMessagePreview: Boolean = true
)

object NotificationHelper {

    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val CHANNEL_DIRECT = "barq_direct_messages"
    const val CHANNEL_GROUP = "barq_group_messages"
    const val CHANNEL_CHANNEL = "barq_channel_posts"

    private const val PREFS_NAME = "barq_notification_prefs"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_DIRECT = "direct_enabled"
    private const val KEY_GROUP = "group_enabled"
    private const val KEY_CHANNEL = "channel_enabled"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATE = "vibrate_enabled"
    private const val KEY_PREVIEW = "show_preview"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getPreferences(context: Context): NotificationPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotificationPreferences(
            notificationsEnabled = prefs.getBoolean(KEY_MASTER, true),
            directNotificationsEnabled = prefs.getBoolean(KEY_DIRECT, true),
            groupNotificationsEnabled = prefs.getBoolean(KEY_GROUP, true),
            channelNotificationsEnabled = prefs.getBoolean(KEY_CHANNEL, true),
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, true),
            showMessagePreview = prefs.getBoolean(KEY_PREVIEW, true)
        )
    }

    fun savePreferences(context: Context, updated: NotificationPreferences) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MASTER, updated.notificationsEnabled)
            .putBoolean(KEY_DIRECT, updated.directNotificationsEnabled)
            .putBoolean(KEY_GROUP, updated.groupNotificationsEnabled)
            .putBoolean(KEY_CHANNEL, updated.channelNotificationsEnabled)
            .putBoolean(KEY_SOUND, updated.soundEnabled)
            .putBoolean(KEY_VIBRATE, updated.vibrateEnabled)
            .putBoolean(KEY_PREVIEW, updated.showMessagePreview)
            .apply()
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_DIRECT,
                "الرسائل الخاصة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات المحادثات الفردية الفورية"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_GROUP,
                "المجموعات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات رسائل المجموعات"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_CHANNEL,
                "القنوات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات منشورات القنوات"
            }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun shouldNotifyForType(prefs: NotificationPreferences, chatType: String): Boolean {
        if (!prefs.notificationsEnabled) return false
        return when (chatType) {
            "group" -> prefs.groupNotificationsEnabled
            "channel" -> prefs.channelNotificationsEnabled
            else -> prefs.directNotificationsEnabled
        }
    }

    fun showIncomingMessageNotification(
        context: Context,
        chatId: String,
        chatType: String,
        title: String,
        body: String
    ) {
        val prefs = getPreferences(context)
        if (!shouldNotifyForType(prefs, chatType)) return
        if (!hasNotificationPermission(context)) return

        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channelId = when (chatType) {
            "group" -> CHANNEL_GROUP
            "channel" -> CHANNEL_CHANNEL
            else -> CHANNEL_DIRECT
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (chatId.isNotBlank()) {
                putExtra(EXTRA_CHAT_ID, chatId)
            }
        }
        val requestCode = if (chatId.isNotBlank()) chatId.hashCode() else 1001
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = title.ifBlank { context.getString(R.string.app_name) }
        val displayBody = if (prefs.showMessagePreview) {
            body.ifBlank { "رسالة جديدة" }
        } else {
            "لديك رسالة جديدة"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_barq)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(
                if (chatType == "direct") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (prefs.soundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setSilent(true)
        }

        if (prefs.vibrateEnabled) {
            builder.setVibrate(longArrayOf(0L, 180L, 80L, 180L))
        }

        val notificationId = if (chatId.isNotBlank()) chatId.hashCode() else System.currentTimeMillis().toInt()
        manager.notify(notificationId, builder.build())
    }

    fun cancelChatNotification(context: Context, chatId: String) {
        if (chatId.isBlank()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(chatId.hashCode())
    }
}
