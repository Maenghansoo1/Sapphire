package com.sapphire.hawe.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.feature.groups.*
import com.sapphire.hawe.feature.mission.MissionScreen
import com.sapphire.hawe.feature.mission.MissionPerformScreen
import com.sapphire.hawe.feature.mission.PerformEntryDecision
import com.sapphire.hawe.feature.mission.performEntryDecision
import com.sapphire.hawe.core.model.MissionType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface MainRoute {
    @Serializable data object Home : MainRoute
    @Serializable data object Start : MainRoute
    @Serializable data object CreateGroup : MainRoute
    @Serializable data class JoinGroup(val code: String? = null) : MainRoute
    @Serializable data class GroupInfo(val groupId: String) : MainRoute
    @Serializable data class GroupProfile(val groupId: String) : MainRoute
    @Serializable data class Mission(val groupId: String) : MainRoute
    @Serializable data class MissionPerform(val groupId: String, val type: String? = null) : MainRoute
}

@Composable
fun MainNavHost(session: AppSession, store: DataStore) {
    val nav = rememberNavController()
    val create = { nav.navigate(MainRoute.CreateGroup) }
    val join = { nav.navigate(MainRoute.JoinGroup()) }
    NavHost(navController = nav, startDestination = MainRoute.Home) {
        composable<MainRoute.Home> {
            if (store.groups.isEmpty()) StartScreen(session, onCreateGroup = create, onJoinGroup = join,
                onMyPage = {})
            else HomeScreen(store, onAdd = { nav.navigate(MainRoute.Start) },
                onGroup = { groupId ->
                    nav.navigate(MainRoute.Mission(groupId)) { launchSingleTop = true }
                })
        }
        composable<MainRoute.Start> {
            StartScreen(session, showsBack = true, onBack = { nav.popBackStack() },
                onCreateGroup = create, onJoinGroup = join, onMyPage = {})
        }
        composable<MainRoute.CreateGroup> {
            CreateGroupScreen(onBack = { nav.popBackStack() }, onCreated = { name, relationship, date ->
                val id = store.createGroup(name, relationship, date)
                nav.navigate(MainRoute.GroupInfo(id.toString())) {
                    popUpTo<MainRoute.Home> { inclusive = false }
                    launchSingleTop = true
                }
            })
        }
        composable<MainRoute.JoinGroup> { entry ->
            JoinGroupScreen(store, entry.toRoute<MainRoute.JoinGroup>().code,
                onBack = { nav.popBackStack() }, onJoined = { code ->
                    if (store.join(code) != null) {
                        nav.popBackStack<MainRoute.Home>(inclusive = false)
                        true
                    } else false
                })
        }
        composable<MainRoute.GroupInfo> { entry ->
            val id = runCatching { UUID.fromString(entry.toRoute<MainRoute.GroupInfo>().groupId) }.getOrNull()
            LaunchedEffect(entry) {
                if (id == null || store.group(id) == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            if (id != null) GroupInfoScreen(id, store, onBack = { nav.popBackStack() },
                onProfile = { nav.navigate(MainRoute.GroupProfile(id.toString())) },
                onLeft = {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                    store.leave(id)
                })
        }
        composable<MainRoute.GroupProfile> { entry ->
            val id = runCatching { UUID.fromString(entry.toRoute<MainRoute.GroupProfile>().groupId) }.getOrNull()
            LaunchedEffect(entry) {
                if (id == null || store.group(id)?.me == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            if (id != null) GroupProfileScreen(id, store, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.Mission> { entry ->
            val id = runCatching { UUID.fromString(entry.toRoute<MainRoute.Mission>().groupId) }.getOrNull()
            val submitted by entry.savedStateHandle.getStateFlow("submitted", false).collectAsState()
            LaunchedEffect(entry) {
                if (id == null || store.todayMission(id) == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            if (id != null) MissionScreen(id, store, onBack = { nav.popBackStack() },
                submittedSignal = submitted,
                onSubmittedHandled = { entry.savedStateHandle["submitted"] = false },
                onPerform = { type -> nav.navigate(MainRoute.MissionPerform(id.toString(), type?.name)) { launchSingleTop = true } },
                onGroupInfo = {
                    nav.navigate(MainRoute.GroupInfo(id.toString())) { launchSingleTop = true }
                })
        }
        composable<MainRoute.MissionPerform>(
            enterTransition = { slideInVertically(tween(300)) { it } },
            popExitTransition = { slideOutVertically(tween(300)) { it } },
        ) { entry ->
            val route = entry.toRoute<MainRoute.MissionPerform>()
            val id = runCatching { UUID.fromString(route.groupId) }.getOrNull()
            val requestedType = route.type?.let { runCatching { MissionType.valueOf(it) }.getOrNull() }
            val mission = id?.let { store.todayMission(it) }
            val decision = remember(entry) {
                if (route.type != null && requestedType == null) PerformEntryDecision.Unsupported
                else performEntryDecision(mission, requestedType)
            }
            LaunchedEffect(entry) {
                if (id == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
                else if (decision !is PerformEntryDecision.Allow) nav.popBackStack()
            }
            if (id != null && mission != null && decision is PerformEntryDecision.Allow) {
                MissionPerformScreen(decision.type, mission.topic, onClose = { nav.popBackStack() },
                    onSubmit = { content ->
                        store.submit(content, id)
                        nav.previousBackStackEntry?.savedStateHandle?.set("submitted", true)
                        nav.popBackStack()
                    })
            }
        }
    }
}
