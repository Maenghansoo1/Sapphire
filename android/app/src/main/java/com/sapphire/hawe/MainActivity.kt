package com.sapphire.hawe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sapphire.hawe.app.AppViewModel
import com.sapphire.hawe.app.RootScreen
import com.sapphire.hawe.designsystem.theme.HaweTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HaweTheme { RootScreen(viewModel<AppViewModel>()) } }
    }
}
