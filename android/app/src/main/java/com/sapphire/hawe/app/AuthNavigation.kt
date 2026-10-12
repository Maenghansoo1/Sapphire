package com.sapphire.hawe.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.feature.auth.*
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthRoute {
    @Serializable data object Login : AuthRoute
    @Serializable data object SignupAccount : AuthRoute
    @Serializable data class SignupProfile(val provider: String, val email: String?) : AuthRoute
    @Serializable data class PasswordReset(val email: String) : AuthRoute
}

@Composable
fun AuthNavHost(session: AppSession, store: DataStore, auth: AuthRepository) {
    val nav = rememberNavController()
    // A2 비밀번호는 저장하지 않고 이 인증 흐름이 살아 있는 동안만 유지해요.
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirm by remember { mutableStateOf("") }
    var signupAgreeTerms by remember { mutableStateOf(false) }
    var signupAgreePrivacy by remember { mutableStateOf(false) }
    NavHost(navController = nav, startDestination = AuthRoute.Login) {
        composable<AuthRoute.Login> {
            LoginScreen(session, store, auth, onSignup = { nav.navigate(AuthRoute.SignupAccount) })
        }
        composable<AuthRoute.SignupAccount> {
            SignupAccountScreen(signupPassword, { signupPassword = it }, signupConfirm, { signupConfirm = it },
                onBack = { nav.popBackStack() },
                onNext = { email, terms, privacy ->
                    signupAgreeTerms = terms
                    signupAgreePrivacy = privacy
                    nav.navigate(AuthRoute.SignupProfile(LoginProvider.EMAIL.value, email))
                })
        }
        composable<AuthRoute.SignupProfile> { entry ->
            val route = entry.toRoute<AuthRoute.SignupProfile>()
            val provider = LoginProvider.entries.first { it.value == route.provider }
            SignupProfileScreen(provider, route.email, session, store, signupPassword,
                signupAgreeTerms, signupAgreePrivacy, auth, onBack = { nav.popBackStack() })
        }
        composable<AuthRoute.PasswordReset> { entry ->
            PasswordResetScreen(entry.toRoute<AuthRoute.PasswordReset>().email, onBack = { nav.popBackStack() })
        }
    }
}
