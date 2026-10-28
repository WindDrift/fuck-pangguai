package io.github.winddrift.fuckpangguai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.winddrift.fuckpangguai.R
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.WaterSession
import io.github.winddrift.fuckpangguai.water.formatAmountWithWater

object NotificationChannels {
    const val WATER_SESSION = "water_session"
    const val SESSION_NOTIFICATION_ID = 4101

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            WATER_SESSION,
            "接水状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "接水进度与结算状态"
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        session: WaterSession?,
        terminal: Boolean = false,
        conversion: WaterConversion? = null,
    ): android.app.Notification {
        val title = when (session?.phase) {
            SessionPhase.SETTLING -> "正在结算"
            SessionPhase.PAID -> "接水已完成并支付"
            SessionPhase.FINISHED_NO_CHARGE -> "接水已结束"
            SessionPhase.PAY_UNKNOWN -> "支付状态待确认"
            SessionPhase.FAULT -> "设备异常"
            else -> "接水中"
        }
        val text = buildString {
            session?.deviceName?.takeIf { it.isNotBlank() }?.let { append(it) }
            session?.amount?.takeIf { it.isNotBlank() }?.let { amount ->
                if (isNotEmpty()) append(" · ")
                append("用量 ").append(formatAmountWithWater(amount, conversion))
            }
            if (isEmpty()) append("保持前台服务，等待设备结算")
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            android.content.Intent(context, io.github.winddrift.fuckpangguai.MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, WATER_SESSION)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(!terminal)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        session: WaterSession?,
        terminal: Boolean = false,
        conversion: WaterConversion? = null,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            runCatching {
                manager.notify(
                    SESSION_NOTIFICATION_ID,
                    build(context, session, terminal = terminal, conversion = conversion),
                )
            }
        }
    }
}
