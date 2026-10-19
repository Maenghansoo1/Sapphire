package com.sapphire.hawe.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.dev.DevMode
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.components.HToastHost
import com.sapphire.hawe.designsystem.components.rememberHToastState
import com.sapphire.hawe.feature.dev.DevUserSwitcher
import com.sapphire.hawe.feature.splash.SplashScreen
import kotlinx.coroutines.delay

val LocalDataStore = staticCompositionLocalOf<DataStore> { error("DataStore not provided") }
val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }

/** 로그인 상태에 따라 첫 화면을 골라요. 앱을 켜면 먼저 00 스플래시를 잠깐 보여줘요. */
@Composable
fun RootScreen(viewModel: AppViewModel) {
    val toast = rememberHToastState()
    var showsSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showsSplash) {
            delay(1200)
            showsSplash = false
        }
    }
    LaunchedEffect(viewModel.switchMessage) {
        viewModel.switchMessage?.let { toast.show(it); viewModel.clearSwitchMessage() }
    }
    CompositionLocalProvider(LocalDataStore provides viewModel.dataStore,
        LocalAppSession provides viewModel.appSession) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (viewModel.appSession.state) {
                AppSession.State.SIGNED_OUT -> AuthNavHost(viewModel.appSession, viewModel.dataStore, viewModel.auth)
                AppSession.State.SIGNED_IN -> MainNavHost(viewModel.appSession, viewModel.dataStore, viewModel.auth, viewModel.push,
                    viewModel.groups, viewModel.missions, viewModel.pokes, viewModel.submissions, viewModel.calendar,
                    viewModel.insights, viewModel.notifications, viewModel.dev, viewModel::switchUser, viewModel.switchBusy,
                    viewModel.switchMessage, viewModel::clearSwitchMessage)
                AppSession.State.RESTORING -> Box(Modifier.fillMaxSize().background(HColors.bg))
            }
            // 전환하는 동안(RESTORING)에도 남겨 둬야 옮겨 둔 위치가 유지돼요.
            if (DevMode.enabled && viewModel.appSession.state != AppSession.State.SIGNED_OUT)
                DevUserSwitcher(viewModel.dev, viewModel.appSession.account.userId, viewModel.switchBusy,
                    viewModel::switchUser)
            HToastHost(toast)
        }
        AnimatedVisibility(showsSplash, enter = EnterTransition.None, exit = fadeOut(tween(300))) {
            SplashScreen()
        }
    }
}
