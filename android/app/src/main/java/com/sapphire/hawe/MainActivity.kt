package com.sapphire.hawe

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
<<<<<<< HEAD
import androidx.activity.viewModels
import com.sapphire.hawe.app.AppViewModel
import com.sapphire.hawe.app.RootScreen
import com.sapphire.hawe.designsystem.theme.HaweTheme
=======
import androidx.compose.runtime.remember
import com.sapphire.hawe.app.HaweApp
import com.sapphire.hawe.app.AppSession
import com.sapphire.hawe.data.DataStore
>>>>>>> feat/ios-step1

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD
        if (savedInstanceState == null) appViewModel.push.handle(intent)
        enableEdgeToEdge()
        setContent { HaweTheme { RootScreen(appViewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appViewModel.push.handle(intent)
=======
        // 화이트 톤 화면이라 상태 막대·내비게이션 바 글자를 어둡게 (라이트 모드만)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val session = remember { AppSession() }
            val store = remember { DataStore() }
            HaweApp(session, store)
        }
>>>>>>> feat/ios-step1
    }
}
