package com.sapphire.hawe

import android.app.Application
import android.content.pm.ApplicationInfo
import com.sapphire.hawe.core.push.PushChannels
import com.sapphire.hawe.core.push.registerDebugNotificationReceiver

class HaweApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushChannels.ensure(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            registerDebugNotificationReceiver(this)
        }
    }
}
