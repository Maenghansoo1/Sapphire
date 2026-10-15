package com.sapphire.hawe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
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
import com.sapphire.hawe.core.data.GroupsState
import com.sapphire.hawe.core.net.CalendarRepository
import com.sapphire.hawe.core.net.DevRepository
import com.sapphire.hawe.core.dev.DevMode
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.InsightRepository
import com.sapphire.hawe.core.net.MissionRepository
import com.sapphire.hawe.core.net.PokeRepository
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.core.net.SubmitOutcome
import com.sapphire.hawe.core.net.classifySubmitFailure
import com.sapphire.hawe.core.push.PushCenter
import com.sapphire.hawe.core.push.PushGroupSnapshot
import com.sapphire.hawe.core.push.PushTarget
import com.sapphire.hawe.core.push.pushTarget
import com.sapphire.hawe.feature.groups.*
import com.sapphire.hawe.feature.dev.DevToolsScreen
import com.sapphire.hawe.feature.insight.GroupInsightScreen
import com.sapphire.hawe.feature.mission.MissionScreen
import com.sapphire.hawe.feature.mission.MissionPerformScreen
import com.sapphire.hawe.feature.mission.PerformEntryDecision
import com.sapphire.hawe.feature.mission.performEntryDecision
import com.sapphire.hawe.feature.records.RecordCalendarScreen
import com.sapphire.hawe.feature.results.ResultsScreen
import com.sapphire.hawe.feature.results.SubmissionDetailScreen
import com.sapphire.hawe.feature.mypage.*
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.tokens.HColors
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute {
    @Serializable data object Home : MainRoute
    @Serializable data object Start : MainRoute
    @Serializable data object CreateGroup : MainRoute
    @Serializable data class JoinGroup(val code: String? = null) : MainRoute
    @Serializable data class GroupInfo(val groupId: Long) : MainRoute
    @Serializable data class GroupInsight(val groupId: Long) : MainRoute
    @Serializable data class GroupProfile(val groupId: Long) : MainRoute
    @Serializable data class SuccessorPicker(val groupId: Long) : MainRoute
    @Serializable data class Mission(val groupId: Long) : MainRoute
    @Serializable data class Calendar(val groupId: Long) : MainRoute
    @Serializable data class MissionPerform(val groupId: Long, val type: String? = null,
        val missionId: Long? = null) : MainRoute
    @Serializable data class Results(val groupId: Long, val missionId: Long) : MainRoute
    @Serializable data class SubmissionDetail(val groupId: Long, val missionId: Long,
        val submissionId: Long) : MainRoute
    @Serializable data object MyPage : MainRoute
    @Serializable data object ProfileEdit : MainRoute
    @Serializable data object NotificationSettings : MainRoute
    @Serializable data object UserInfo : MainRoute
    @Serializable data object ChangePassword : MainRoute
    @Serializable data object AppInfo : MainRoute
    @Serializable data object DevTools : MainRoute
    @Serializable data object Licenses : MainRoute
    @Serializable data object Withdraw : MainRoute
}

@Composable
fun MainNavHost(session: AppSession, store: DataStore, auth: AuthRepository, push: PushCenter, groups: GroupRepository,
    missions: MissionRepository, pokes: PokeRepository, submissions: SubmissionRepository, calendar: CalendarRepository,
    insights: InsightRepository, dev: DevRepository? = null,
    onSwitchUser: (Long) -> Unit = {}, switchBusy: Boolean = false,
    switchMessage: String? = null, onSwitchMessageShown: () -> Unit = {}) {
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
                val last = group.missions.lastOrNull { it.serverSubmittedByMe }
                    ?: group.missions.lastOrNull()
                PushGroupSnapshot(group.id, last?.id, last?.serverSubmittedByMe == true)
            }
            val target = pushTarget(destination, groups)
            push.consume()
            when (target) {
                PushTarget.None -> Unit
                is PushTarget.Mission -> {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                    nav.navigate(MainRoute.Mission(target.groupId)) { launchSingleTop = true }
                }
                is PushTarget.Results -> {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                    nav.navigate(MainRoute.Mission(target.groupId)) { launchSingleTop = true }
                    nav.navigate(MainRoute.Results(target.groupId, target.missionId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    val create = { nav.navigate(MainRoute.CreateGroup) }
    val join = { nav.navigate(MainRoute.JoinGroup()) }
    val myPage = { nav.navigate(MainRoute.MyPage) { launchSingleTop = true } }
    var retryGroups by remember { mutableStateOf(0) }
    NavHost(navController = nav, startDestination = MainRoute.Home) {
        composable<MainRoute.Home> {
            LaunchedEffect(retryGroups) {
                try {
                    val details = groups.loadAll(session.account.userId)
                    if (session.state == AppSession.State.SIGNED_IN) store.applyServerGroups(details)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (session.state == AppSession.State.SIGNED_IN) store.markGroupsFailed()
                }
            }
            when {
                store.groupsState == GroupsState.NOT_LOADED -> Box(Modifier.fillMaxSize().background(HColors.bg))
                store.groupsState == GroupsState.FAILED && store.groups.isEmpty() ->
                    GroupsLoadFailed { retryGroups++ }
                store.groups.isEmpty() -> StartScreen(onCreateGroup = create, onJoinGroup = join,
                    onMyPage = myPage)
                else -> HomeScreen(store, onAdd = { nav.navigate(MainRoute.Start) },
                    onMyPage = myPage, onGroup = { groupId ->
                        nav.navigate(MainRoute.Mission(groupId)) { launchSingleTop = true }
                    })
            }
        }
        composable<MainRoute.Start> {
            StartScreen(showsBack = true, onBack = { nav.popBackStack() },
                onCreateGroup = create, onJoinGroup = join, onMyPage = myPage)
        }
        composable<MainRoute.CreateGroup> {
            CreateGroupScreen(groups, store, session, onBack = { nav.popBackStack() }, onCreated = { id ->
                nav.navigate(MainRoute.GroupInfo(id)) {
                    popUpTo<MainRoute.Home> { inclusive = false }
                    launchSingleTop = true
                }
            })
        }
        composable<MainRoute.JoinGroup> { entry ->
            JoinGroupScreen(groups, store, session, entry.toRoute<MainRoute.JoinGroup>().code,
                onBack = { nav.popBackStack() }, onJoined = {
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                })
        }
        composable<MainRoute.GroupInfo> { entry ->
            val id = entry.toRoute<MainRoute.GroupInfo>().groupId
            LaunchedEffect(entry) {
                if (store.group(id) == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            GroupInfoScreen(id, store, groups, session, onBack = { nav.popBackStack() },
                onProfile = { nav.navigate(MainRoute.GroupProfile(id)) },
                onPickSuccessor = { nav.navigate(MainRoute.SuccessorPicker(id)) },
                onLeft = { nav.popBackStack<MainRoute.Home>(inclusive = false) },
                onInsight = { nav.navigate(MainRoute.GroupInsight(id)) })
        }
        composable<MainRoute.GroupProfile> { entry ->
            val id = entry.toRoute<MainRoute.GroupProfile>().groupId
            LaunchedEffect(entry) {
                if (store.group(id)?.me == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            GroupProfileScreen(id, store, groups, session, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.SuccessorPicker> { entry ->
            val id = entry.toRoute<MainRoute.SuccessorPicker>().groupId
            SuccessorPickerScreen(id, store, groups, session, onBack = { nav.popBackStack() },
                onLeft = { nav.popBackStack<MainRoute.Home>(inclusive = false) })
        }
        composable<MainRoute.GroupInsight> { entry ->
            val id = entry.toRoute<MainRoute.GroupInsight>().groupId
            LaunchedEffect(entry) {
                if (store.group(id) == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            GroupInsightScreen(id, store, insights, calendar,
                onBack = { nav.popBackStack() },
                onLeft = { nav.popBackStack<MainRoute.Home>(inclusive = false) },
                onResults = { missionId -> nav.navigate(MainRoute.Results(id, missionId)) { launchSingleTop = true } })
        }
        composable<MainRoute.Mission> { entry ->
            val id = entry.toRoute<MainRoute.Mission>().groupId
            val submitted by entry.savedStateHandle.getStateFlow("submitted", false).collectAsState()
            val submittedLate by entry.savedStateHandle.getStateFlow("submittedLate", false).collectAsState()
            val submittedMissionId by entry.savedStateHandle.getStateFlow<Long?>("submittedMissionId", null).collectAsState()
            val refreshMission by entry.savedStateHandle.getStateFlow("refresh", false).collectAsState()
            val notice by entry.savedStateHandle.getStateFlow<String?>("notice", null).collectAsState()
            LaunchedEffect(entry) {
                if (store.group(id) == null) nav.popBackStack<MainRoute.Home>(inclusive = false)
            }
            MissionScreen(id, store, missions, pokes, session, onBack = { nav.popBackStack() },
                submittedSignal = submitted,
                submittedLate = submittedLate,
                submittedMissionId = submittedMissionId,
                onSubmittedHandled = {
                    entry.savedStateHandle["submitted"] = false
                    entry.savedStateHandle["submittedLate"] = false
                    entry.savedStateHandle["submittedMissionId"] = null
                },
                refreshSignal = refreshMission, onRefreshHandled = { entry.savedStateHandle["refresh"] = false },
                notice = notice, onNoticeHandled = { entry.savedStateHandle["notice"] = null },
                onPerform = { missionId, type ->
                    nav.navigate(MainRoute.MissionPerform(id, type?.name, missionId)) { launchSingleTop = true }
                },
                onGroupInfo = {
                    nav.navigate(MainRoute.GroupInfo(id)) { launchSingleTop = true }
                }, onResults = { missionId ->
                    nav.navigate(MainRoute.Results(id, missionId)) { launchSingleTop = true }
                }, onCalendar = {
                    nav.navigate(MainRoute.Calendar(id)) { launchSingleTop = true }
                }, dev = if (DevMode.enabled) dev else null)
        }
        composable<MainRoute.Calendar> { entry ->
            val route = entry.toRoute<MainRoute.Calendar>()
            val id = route.groupId
            LaunchedEffect(entry) {
                if (store.group(id) == null) nav.popBackStack()
            }
            RecordCalendarScreen(id, store, calendar, session, onBack = { nav.popBackStack() },
                onResults = { missionId ->
                    nav.navigate(MainRoute.Results(route.groupId, missionId)) { launchSingleTop = true }
                })
        }
        composable<MainRoute.MissionPerform>(
            enterTransition = { slideInVertically(tween(300)) { it } },
            popExitTransition = { slideOutVertically(tween(300)) { it } },
        ) { entry ->
            val route = entry.toRoute<MainRoute.MissionPerform>()
            val id = route.groupId
            val requestedType = route.type?.let { runCatching { MissionType.valueOf(it) }.getOrNull() }
            val mission = if (route.missionId != null) store.todayMission(id, route.missionId)
                else store.todayMission(id)
            var submitMessage by remember(entry) { mutableStateOf<String?>(null) }
            var uploading by remember(entry) { mutableStateOf(false) }
            val decision = remember(entry) {
                if (route.type != null && requestedType == null) PerformEntryDecision.Unsupported
                else performEntryDecision(mission, requestedType)
            }
            LaunchedEffect(entry) {
                if (decision !is PerformEntryDecision.Allow) nav.popBackStack()
            }
            if (mission != null && decision is PerformEntryDecision.Allow) {
                MissionPerformScreen(decision.type, mission.topic, mission.template, mission.spec,
                    onClose = { nav.popBackStack() },
                    message = submitMessage, onMessageShown = { submitMessage = null },
                    onSubmit = { content ->
                        if (uploading) false else {
                            uploading = true
                            try {
                                val submitted = submissions.submit(mission.id, content, session.account.userId)
                                if (session.state == AppSession.State.SIGNED_IN) store.applySubmitted(id, mission.id, submitted)
                                nav.previousBackStackEntry?.savedStateHandle?.set("submittedMissionId", mission.id)
                                nav.previousBackStackEntry?.savedStateHandle?.set("submittedLate", submitted.isLate)
                                nav.previousBackStackEntry?.savedStateHandle?.set("submitted", true)
                                nav.popBackStack()
                                true
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                when (val outcome = classifySubmitFailure(error)) {
                                    is SubmitOutcome.Stay -> { submitMessage = outcome.message; false }
                                    SubmitOutcome.AlreadySubmitted -> {
                                        nav.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                                        nav.previousBackStackEntry?.savedStateHandle?.set("notice", "이미 올렸어요")
                                        nav.popBackStack(); true
                                    }
                                    SubmitOutcome.MissionClosed -> {
                                        nav.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                                        nav.previousBackStackEntry?.savedStateHandle?.set("notice", "새 미션이 열려서 마감됐어요")
                                        nav.popBackStack(); true
                                    }
                                    SubmitOutcome.ReturnHome -> {
                                        retryGroups++
                                        nav.popBackStack<MainRoute.Home>(inclusive = false); true
                                    }
                                }
                            } finally { uploading = false }
                        }
                    })
            }
        }
        composable<MainRoute.Results> { entry ->
            val route = entry.toRoute<MainRoute.Results>()
            val groupID = route.groupId
            val missionID = route.missionId
            LaunchedEffect(entry) {
                // 현재 미션이면 내가 냈는지, 달력에서 연 지난 미션이면 그날 내가 냈는지 확인해요.
                val current = store.todayMission(groupID, missionID)
                val canOpen = if (current != null) current.didSubmit
                    else store.calendarRecord(groupID, missionID)?.didSubmit == true
                if (!canOpen) nav.popBackStack()
            }
            ResultsScreen(groupID, missionID, store, submissions, missions, session,
                onBack = { nav.popBackStack() }, onDetail = { submissionID ->
                    nav.navigate(MainRoute.SubmissionDetail(route.groupId, route.missionId,
                        submissionID)) { launchSingleTop = true }
                }, onLocked = {
                    nav.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    nav.previousBackStackEntry?.savedStateHandle?.set("notice", "먼저 미션을 올려 주세요")
                    nav.popBackStack()
                }, onHome = {
                    retryGroups++
                    nav.popBackStack<MainRoute.Home>(inclusive = false)
                })
        }
        composable<MainRoute.SubmissionDetail> { entry ->
            val route = entry.toRoute<MainRoute.SubmissionDetail>()
            val groupID = route.groupId
            val missionID = route.missionId
            val submissionID = route.submissionId
            LaunchedEffect(entry) {
                if (store.result(groupID, missionID)?.submissions?.none { it.id == submissionID } != false) {
                    nav.popBackStack()
                }
            }
            SubmissionDetailScreen(groupID, missionID, submissionID, store, submissions,
                onBack = { nav.popBackStack() })
        }
        composable<MainRoute.MyPage> {
            MyPageScreen(session, store, calendar, onBack = { nav.popBackStack() },
                onProfileEdit = { nav.navigate(MainRoute.ProfileEdit) { launchSingleTop = true } },
                onNotifications = { nav.navigate(MainRoute.NotificationSettings) { launchSingleTop = true } },
                onUserInfo = { nav.navigate(MainRoute.UserInfo) { launchSingleTop = true } },
                onAppInfo = { nav.navigate(MainRoute.AppInfo) { launchSingleTop = true } },
                onWithdraw = { nav.navigate(MainRoute.Withdraw) { launchSingleTop = true } },
                dev = dev, onDevTools = { nav.navigate(MainRoute.DevTools) { launchSingleTop = true } },
                onSwitchUser = onSwitchUser, switchBusy = switchBusy,
                switchMessage = switchMessage, onSwitchMessageShown = onSwitchMessageShown)
        }
        composable<MainRoute.DevTools> {
            if (DevMode.enabled && dev != null) DevToolsScreen(session, store, dev, groups, missions,
                onBack = { nav.popBackStack() },
                onInsight = { nav.navigate(MainRoute.GroupInsight(it)) },
                onLogout = { session.signOut() })
            else LaunchedEffect(Unit) { nav.popBackStack() }
        }
        composable<MainRoute.ProfileEdit> {
            ProfileEditScreen(session, store, auth, onBack = { nav.popBackStack() })
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
            ChangePasswordScreen(auth, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.AppInfo> {
            AppInfoScreen(onBack = { nav.popBackStack() },
                onLicenses = { nav.navigate(MainRoute.Licenses) { launchSingleTop = true } })
        }
        composable<MainRoute.Licenses> {
            LicensesScreen(onBack = { nav.popBackStack() })
        }
        composable<MainRoute.Withdraw> {
            WithdrawScreen(session, store, auth, onBack = { nav.popBackStack() })
        }
    }
}
