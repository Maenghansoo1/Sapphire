package com.sapphire.hawe.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

private const val ACTION_DEBUG_NOTIFY = "com.sapphire.hawe.debug.NOTIFY"

class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_NOTIFY) return
        val kind = PushKind.fromValue(intent.getStringExtra("type")) ?: return
        val defaults = defaultPushTexts(kind, null)
        val shown = PushNotifier.show(context, kind,
            intent.getStringExtra("title")?.takeIf(String::isNotBlank) ?: defaults.title,
            intent.getStringExtra("body")?.takeIf(String::isNotBlank) ?: defaults.body,
            intent.getStringExtra("group_id"), intent.getStringExtra("submission_id"))
        if (!shown) Log.i("HawePush", "알림 권한 또는 채널이 꺼져 있어 표시하지 않았어요")
    }
}

// 개발용(디버그 빌드 전용). FCM 연결 단계에서 삭제해요.
fun registerDebugNotificationReceiver(context: Context): DebugNotificationReceiver {
    val receiver = DebugNotificationReceiver()
    ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_DEBUG_NOTIFY),
        ContextCompat.RECEIVER_EXPORTED)
    return receiver
}
