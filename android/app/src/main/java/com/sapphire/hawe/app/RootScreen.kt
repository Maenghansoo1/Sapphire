package com.sapphire.hawe.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.feature.gallery.ComponentGalleryScreen

val LocalDataStore = staticCompositionLocalOf<DataStore> { error("DataStore not provided") }
val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }

@Composable
fun RootScreen(viewModel: AppViewModel) {
    CompositionLocalProvider(LocalDataStore provides viewModel.dataStore,
        LocalAppSession provides viewModel.appSession) {
        // 3단계에서 인증/홈 내비게이션으로 교체해요.
        Box(Modifier.fillMaxSize().statusBarsPadding()) { ComponentGalleryScreen() }
    }
}
