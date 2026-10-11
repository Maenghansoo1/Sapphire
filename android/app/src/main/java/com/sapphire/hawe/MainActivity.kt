package com.sapphire.hawe

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sapphire.hawe.app.AppViewModel
import com.sapphire.hawe.app.RootScreen
import com.sapphire.hawe.designsystem.theme.HaweTheme

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) appViewModel.push.handle(intent)
        enableEdgeToEdge()
        setContent { HaweTheme { RootScreen(appViewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appViewModel.push.handle(intent)
    }
}
