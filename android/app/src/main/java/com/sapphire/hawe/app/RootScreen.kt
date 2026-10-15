package com.sapphire.hawe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.components.HToastHost
import com.sapphire.hawe.designsystem.components.rememberHToastState

val LocalDataStore = staticCompositionLocalOf<DataStore> { error("DataStore not provided") }
val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }

@Composable
fun RootScreen(viewModel: AppViewModel) {
    val toast = rememberHToastState()
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
                    viewModel.insights, viewModel.dev, viewModel::switchUser, viewModel.switchBusy,
                    viewModel.switchMessage, viewModel::clearSwitchMessage)
                AppSession.State.RESTORING -> Box(Modifier.fillMaxSize().background(HColors.bg))
            }
            HToastHost(toast)
        }
    }
}
