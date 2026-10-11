package com.sapphire.hawe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.push.PushCenter
import com.sapphire.hawe.core.push.PushGroupSnapshot
import com.sapphire.hawe.core.push.PushTarget
import com.sapphire.hawe.core.push.pushTarget
import com.sapphire.hawe.feature.groups.*
import com.sapphire.hawe.feature.mission.MissionScreen
import com.sapphire.hawe.feature.mission.MissionPerformScreen
import com.sapphire.hawe.feature.mission.PerformEntryDecision
import com.sapphire.hawe.feature.mission.performEntryDecision
import com.sapphire.hawe.feature.records.RecordCalendarScreen
import com.sapphire.hawe.feature.results.ResultsScreen
import com.sapphire.hawe.feature.results.SubmissionDetailScreen
import com.sapphire.hawe.feature.mypage.*
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
    @Serializable data class Calendar(val groupId: String) : MainRoute
    @Serializable data class MissionPerform(val groupId: String, val type: String? = null) : MainRoute
    @Serializable data class Results(val groupId: String, val missionId: String) : MainRoute
    @Serializable data class SubmissionDetail(val groupId: String, val missionId: String,
        val submissionId: String) : MainRoute
    @Serializable data object MyPage : MainRoute
    @Serializable data object ProfileEdit : MainRoute
    @Serializable data object NotificationSettings : MainRoute
    @Serializable data object UserInfo : MainRoute
    @Serializable data object ChangePassword : MainRoute
    @Serializable data object AppInfo : MainRoute
    @Serializable data object Licenses : MainRoute
    @Serializable data object Withdraw : MainRoute
}

@Composable
fun MainNavHost(session: AppSession, store: DataStore, push: PushCenter) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!permissionRequested && Build.VERSION.SDK_INT >= 33) {
            permissionRequested = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val destination = push.pendingDestination
    LaunchedEffect(destination) {
        if (destination != null) {
            val groups = store.groups.map { group ->
                val last = group.missions.lastOrNull()
                PushGroupSnapshot(group.id, last?.id, last?.submissions?.any { it.isMine } == true)
            }
            val target = pushTarget(destination, groups)
            push.consume()
            when (target) {
                PushTarget.None -> Unit
                is PushTarget.Mission -> {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                    nav.navigate(MainRoute.Mission(target.groupId.toString())) { launchSingleTop = true }
                }
                is PushTarget.Results -> {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                    nav.navigate(MainRoute.Mission(target.groupId.toString())) { launchSingleTop = true }
                    nav.navigate(MainRoute.Results(target.groupId.toString(), target.missionId.toString())) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    val create = { nav.navigate(MainRoute.CreateGroup) }
    val join = { nav.navigate(MainRoute.JoinGroup()) }
    val myPage = { nav.navigate(MainRoute.MyPage) { launchSingleTop = true } }
    NavHost(navController = nav, startDestination = MainRoute.Home) {
        composable<MainRoute.Home> {
            if (store.groups.isEmpty()) StartScreen(onCreateGroup = create, onJoinGroup = join,
                onMyPage = myPage)
            else HomeScreen(store, onAdd = { nav.navigate(MainRoute.Start) },
                onMyPage = myPage, onGroup = { groupId ->
                    nav.navigate(MainRoute.Mission(groupId)) { launchSingleTop = true }
                })
        }
        composable<MainRoute.Start> {
            StartScreen(showsBack = true, onBack = { nav.popBackStack() },
                onCreateGroup = create, onJoinGroup = join, onMyPage = myPage)
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
                }, onCalendar = {
                    nav.navigate(MainRoute.Calendar(id.toString())) { launchSingleTop = true }
                }, onResults = {
                    store.todayMission(id)?.let { mission ->
                        nav.navigate(MainRoute.Results(id.toString(), mission.id.toString())) { launchSingleTop = true }
                    }
                })
        }
        composable<MainRoute.Calendar> { entry ->
            val route = entry.toRoute<MainRoute.Calendar>()
            val id = runCatching { UUID.fromString(route.groupId) }.getOrNull()
            LaunchedEffect(entry) {
                if (id == null || store.group(id) == null) nav.popBackStack()
            }
            if (id != null) RecordCalendarScreen(id, store, onBack = { nav.popBackStack() },
                onResults = { missionId ->
                    nav.navigate(MainRoute.Results(route.groupId, missionId.toString())) { launchSingleTop = true }
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
        composable<MainRoute.Results> { entry ->
            val route = entry.toRoute<MainRoute.Results>()
            val groupID = runCatching { UUID.fromString(route.groupId) }.getOrNull()
            val missionID = runCatching { UUID.fromString(route.missionId) }.getOrNull()
            LaunchedEffect(entry) {
                if (groupID == null || missionID == null || store.result(groupID, missionID) == null) {
                    nav.popBackStack()
                }
            }
            if (groupID != null && missionID != null) ResultsScreen(groupID, missionID, store,
                onBack = { nav.popBackStack() }, onDetail = { submissionID ->
                    nav.navigate(MainRoute.SubmissionDetail(route.groupId, route.missionId,
                        submissionID.toString())) { launchSingleTop = true }
                })
        }
        composable<MainRoute.SubmissionDetail> { entry ->
            val route = entry.toRoute<MainRoute.SubmissionDetail>()
            val groupID = runCatching { UUID.fromString(route.groupId) }.getOrNull()
            val missionID = runCatching { UUID.fromString(route.missionId) }.getOrNull()
            val submissionID = runCatching { UUID.fromString(route.submissionId) }.getOrNull()
            LaunchedEffect(entry) {
                if (groupID == null || missionID == null || submissionID == null ||
                    store.result(groupID, missionID)?.submissions?.none { it.id == submissionID } != false) {
                    nav.popBackStack()
                }
            }
            if (groupID != null && missionID != null && submissionID != null) {
                SubmissionDetailScreen(groupID, missionID, submissionID, store,
                    onBack = { nav.popBackStack() })
            }
        }
        composable<MainRoute.MyPage> {
            MyPageScreen(session, store, onBack = { nav.popBackStack() },
                onProfileEdit = { nav.navigate(MainRoute.ProfileEdit) { launchSingleTop = true } },
                onNotifications = { nav.navigate(MainRoute.NotificationSettings) { launchSingleTop = true } },
                onUserInfo = { nav.navigate(MainRoute.UserInfo) { launchSingleTop = true } },
                onAppInfo = { nav.navigate(MainRoute.AppInfo) { launchSingleTop = true } },
                onWithdraw = { nav.navigate(MainRoute.Withdraw) { launchSingleTop = true } })
        }
        composable<MainRoute.ProfileEdit> {
            ProfileEditScreen(session, store, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.NotificationSettings> {
            NotificationSettingsScreen(session, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.UserInfo> {
            UserInfoScreen(session, onBack = { nav.popBackStack() },
                onProfileEdit = { nav.navigate(MainRoute.ProfileEdit) { launchSingleTop = true } },
                onChangePassword = { nav.navigate(MainRoute.ChangePassword) { launchSingleTop = true } })
        }
        composable<MainRoute.ChangePassword> {
            ChangePasswordScreen(onBack = { nav.popBackStack() })
        }
        composable<MainRoute.AppInfo> {
            AppInfoScreen(onBack = { nav.popBackStack() },
                onLicenses = { nav.navigate(MainRoute.Licenses) { launchSingleTop = true } })
        }
        composable<MainRoute.Licenses> {
            LicensesScreen(onBack = { nav.popBackStack() })
        }
        composable<MainRoute.Withdraw> {
            WithdrawScreen(session, store, onBack = { nav.popBackStack() })
        }
    }
}
