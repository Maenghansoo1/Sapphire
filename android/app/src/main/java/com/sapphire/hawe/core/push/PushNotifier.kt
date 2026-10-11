package com.sapphire.hawe.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sapphire.hawe.MainActivity
import com.sapphire.hawe.R
import java.util.concurrent.atomic.AtomicInteger

object PushChannels {
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        pushChannelSpecs.forEach { spec ->
            manager.createNotificationChannel(NotificationChannel(spec.id, spec.name,
                NotificationManager.IMPORTANCE_DEFAULT).apply { description = spec.description })
        }
    }
}

object PushNotifier {
    private val nextId = AtomicInteger((System.nanoTime() and Int.MAX_VALUE.toLong()).toInt())

    @SuppressLint("MissingPermission")
    fun show(context: Context, kind: PushKind, title: String, body: String,
        groupId: String? = null, submissionId: String? = null): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val id = nextId.incrementAndGet()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", kind.value)
            putExtra("group_id", groupId)
            putExtra("submission_id", submissionId)
        }
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, kind.value)
            .setSmallIcon(R.drawable.ic_stat_hawe)
            .setColor(0xFF67A0C3.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        return try { manager.notify(id, notification); true } catch (_: SecurityException) { false }
    }
}
