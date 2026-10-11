package com.sapphire.hawe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore

val LocalDataStore = staticCompositionLocalOf<DataStore> { error("DataStore not provided") }
val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }

@Composable
fun RootScreen(viewModel: AppViewModel) {
    CompositionLocalProvider(LocalDataStore provides viewModel.dataStore,
        LocalAppSession provides viewModel.appSession) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (viewModel.appSession.state) {
                AppSession.State.SIGNED_OUT -> AuthNavHost(viewModel.appSession, viewModel.dataStore)
                AppSession.State.SIGNED_IN -> MainNavHost(viewModel.appSession)
            }
        }
    }
}
