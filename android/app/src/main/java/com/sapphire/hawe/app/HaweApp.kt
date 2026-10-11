package com.sapphire.hawe.app

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sapphire.hawe.feature.auth.LoginScreen
import com.sapphire.hawe.feature.auth.PasswordResetScreen
import com.sapphire.hawe.feature.auth.SignupAccountScreen
import com.sapphire.hawe.feature.auth.SignupProfileScreen
import com.sapphire.hawe.data.DataStore
import com.sapphire.hawe.feature.groups.CreateGroupScreen
import com.sapphire.hawe.feature.groups.GroupInfoScreen
import com.sapphire.hawe.feature.groups.GroupProfileScreen
import com.sapphire.hawe.feature.groups.HomeScreen
import com.sapphire.hawe.feature.groups.JoinGroupScreen
import com.sapphire.hawe.feature.groups.StartScreen
import com.sapphire.hawe.feature.mission.MissionScreen
import com.sapphire.hawe.feature.records.RecordCalendarScreen
import com.sapphire.hawe.feature.results.ResultsScreen
import com.sapphire.hawe.feature.results.SubmissionDetailScreen
import com.sapphire.hawe.model.LoginProvider

val LocalSession = staticCompositionLocalOf<AppSession> { error("AppSession 이 없어요") }
val LocalStore = staticCompositionLocalOf<DataStore> { error("DataStore 가 없어요") }

/** 로그인 상태에 따라 첫 화면을 골라요. 로그아웃 → A1 로그인, 로그인 → 그룹이 있으면 04 홈, 없으면 01 시작 */
@Composable
fun HaweApp(session: AppSession, store: DataStore) {
    CompositionLocalProvider(LocalSession provides session, LocalStore provides store) {
        Crossfade(session.isSignedIn, label = "root") { signedIn ->
            if (signedIn) MainFlow() else AuthFlow()
        }
    }
}

/** 로그인·회원가입 흐름: A1 로그인 → A2 계정 정보 → A3 프로필. 간편 로그인 신규 가입자는 A2 를 건너뛰어요. */
@Composable
private fun AuthFlow() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onSignup = { nav.navigate("signupAccount") },
                onPasswordReset = { email -> nav.navigate("passwordReset?email=${Uri.encode(email)}") },
                onSocial = { provider -> nav.navigate("signupProfile/${provider.name}") },
            )
        }
        composable("signupAccount") {
            SignupAccountScreen(
                onBack = { nav.popBackStack() },
                onNext = { email -> nav.navigate("signupProfile/${LoginProvider.Email.name}?email=${Uri.encode(email)}") },
            )
        }
        composable(
            "signupProfile/{provider}?email={email}",
            arguments = listOf(
                navArgument("provider") { type = NavType.StringType },
                navArgument("email") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            SignupProfileScreen(
                provider = LoginProvider.valueOf(entry.arguments?.getString("provider") ?: LoginProvider.Email.name),
                email = entry.arguments?.getString("email"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "passwordReset?email={email}",
            arguments = listOf(navArgument("email") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PasswordResetScreen(initialEmail = entry.arguments?.getString("email") ?: "", onBack = { nav.popBackStack() })
        }
    }
}

/**
 * 로그인 뒤 흐름. 그룹이 하나도 없으면 01 시작, 있으면 04 홈이 첫 화면이에요.
 * 화면 사이에는 그룹 id 만 넘기고, 내용은 DataStore 에서 읽어요.
 */
@Composable
private fun MainFlow() {
    val store = LocalStore.current
    val nav = rememberNavController()
    fun backToRoot() = nav.popBackStack("root", inclusive = false)

    NavHost(nav, startDestination = "root") {
        composable("root") {
            if (store.groups.isEmpty()) {
                StartScreen(
                    onCreateGroup = { nav.navigate("createGroup") },
                    onJoinGroup = { nav.navigate("joinGroup") },
                    // TODO: 17 마이페이지 (5단계)
                    onMyPage = {},
                )
            } else {
                HomeScreen(
                    onAdd = { nav.navigate("start") },
                    // TODO: 17 마이페이지 (5단계)
                    onMyPage = {},
                    onOpenGroup = { id -> nav.navigate("mission/$id") },
                )
            }
        }
        composable("start") {
            StartScreen(
                onBack = { nav.popBackStack() },
                onCreateGroup = { nav.navigate("createGroup") },
                onJoinGroup = { nav.navigate("joinGroup") },
                onMyPage = {},
            )
        }
        composable("createGroup") {
            CreateGroupScreen(onBack = { nav.popBackStack() }) { name, relationship, startDate ->
                val id = store.createGroup(name, relationship, startDate)
                // 그룹을 만든 뒤에는 16 그룹 정보(초대 코드 공유)로 가요. 뒤로 가면 홈이에요. (CLAUDE.md §4)
                nav.navigate("groupInfo/$id") { popUpTo("root") }
            }
        }
        composable(
            "joinGroup?code={code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            JoinGroupScreen(initialCode = entry.arguments?.getString("code") ?: "", onBack = { nav.popBackStack() }) { code ->
                store.join(code)
                backToRoot()
            }
        }
        composable("mission/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            MissionScreen(
                groupId = id,
                onBack = { nav.popBackStack() },
                onCalendar = { nav.navigate("calendar/$id") },
                onGroupInfo = { nav.navigate("groupInfo/$id") },
                onResults = { missionId -> nav.navigate("results/$id/$missionId") },
            )
        }
        composable("results/{id}/{missionId}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val missionId = entry.arguments?.getString("missionId") ?: return@composable
            ResultsScreen(id, missionId, onBack = { nav.popBackStack() }) { submissionId ->
                nav.navigate("submission/$id/$missionId/$submissionId")
            }
        }
        composable("submission/{id}/{missionId}/{submissionId}") { entry ->
            val args = entry.arguments ?: return@composable
            SubmissionDetailScreen(
                groupId = args.getString("id") ?: return@composable,
                missionId = args.getString("missionId") ?: return@composable,
                submissionId = args.getString("submissionId") ?: return@composable,
                onBack = { nav.popBackStack() },
            )
        }
        composable("calendar/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            RecordCalendarScreen(id, onBack = { nav.popBackStack() }) { missionId -> nav.navigate("results/$id/$missionId") }
        }
        composable("groupInfo/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            GroupInfoScreen(
                groupId = id,
                onBack = { nav.popBackStack() },
                onEditProfile = { nav.navigate("groupProfile/$id") },
                onLeft = {
                    backToRoot()
                    store.leave(id)
                },
            )
        }
        composable("groupProfile/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            GroupProfileScreen(groupId = id, onBack = { nav.popBackStack() })
        }
    }
}

/** 앱 밖 웹 문서를 앱 안 브라우저(Custom Tabs)로 열어요 — iOS Safari View 와 같은 역할 */
@Composable
fun rememberOpenWebLink(): (String) -> Unit {
    val context = LocalContext.current
    return { url -> CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
}

/** 앱 밖 웹 문서 링크 (iOS AppLinks 와 같은 주소) */
object AppLinks {
    const val TERMS_OF_SERVICE = "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80ff84a3c2e307d52f65"
    const val PRIVACY_POLICY = "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80f38ca3c3fd1c92a9be"
}
