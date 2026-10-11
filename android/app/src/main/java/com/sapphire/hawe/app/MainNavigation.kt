package com.sapphire.hawe.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.feature.groups.StartScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute {
    @Serializable data object Start : MainRoute
}

@Composable
fun MainNavHost(session: AppSession) {
    val nav = rememberNavController()
    // TODO: 4단계에서 그룹이 있으면 04 홈으로 시작해요.
    NavHost(navController = nav, startDestination = MainRoute.Start) {
        composable<MainRoute.Start> {
            StartScreen(session, onCreateGroup = { /* TODO: 4단계 */ },
                onJoinGroup = { /* TODO: 4단계 */ }, onMyPage = { /* TODO: 8단계 */ })
        }
    }
}
