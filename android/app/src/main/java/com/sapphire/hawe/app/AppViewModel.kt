package com.sapphire.hawe.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sapphire.hawe.BuildConfig
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.dev.DevFaults
import com.sapphire.hawe.core.dev.DevMode
import com.sapphire.hawe.core.net.ApiClient
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.CalendarRepository
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.DevRepository
import com.sapphire.hawe.core.net.InsightRepository
import com.sapphire.hawe.core.net.MissionRepository
import com.sapphire.hawe.core.net.PokeRepository
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.core.net.KeystoreTokenStore
import com.sapphire.hawe.core.push.PushCenter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit.SECONDS

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val dataStore = DataStore(File(application.cacheDir, "hawe-media"))
    private val tokens = KeystoreTokenStore(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var switching = false
    private val api = ApiClient(BuildConfig.API_BASE_URL, tokens,
        http = OkHttpClient.Builder().connectTimeout(10, SECONDS).readTimeout(20, SECONDS)
            .writeTimeout(20, SECONDS).addInterceptor(DevFaults).build(),
        onSessionExpired = { mainHandler.post { if (!switching) appSession.signOut() } })
    val dev = DevRepository(api)
    val auth: AuthRepository = AuthRepository(api, tokens)
    val groups = GroupRepository(api)
    val missions = MissionRepository(api)
    val pokes = PokeRepository(api)
    val submissions = SubmissionRepository(api)
    val calendar = CalendarRepository(api)
    val insights = InsightRepository(api)
    val appSession: AppSession = AppSession(initialState = if (auth.hasSession) AppSession.State.RESTORING else AppSession.State.SIGNED_OUT,
        onCleared = { auth.clearSession(); dataStore.reset() })
    val push = PushCenter()
    var switchBusy by mutableStateOf(false)
        private set
    var switchMessage by mutableStateOf<String?>(null)
        private set

    fun clearSwitchMessage() { switchMessage = null }

    fun switchUser(userId: Long) {
        if (switchBusy || userId == appSession.account.userId) return
        viewModelScope.launch {
            switchBusy = true
            switching = true
            val oldAccess = tokens.accessToken
            val oldRefresh = tokens.refreshToken
            val oldAccount = appSession.account
            try {
                val issued = dev.loginAs(userId)
                tokens.save(issued.accessToken, issued.refreshToken)
                appSession.beginRestoring()
                dataStore.reset()
                val account = auth.restore()
                dataStore.setMyProfile(account.profile)
                appSession.signIn(account)
                switchMessage = "${account.profile.nickname} 계정으로 전환했어요"
            } catch (error: Exception) {
                if (oldAccess != null && oldRefresh != null) tokens.save(oldAccess, oldRefresh)
                else tokens.clear()
                if (appSession.state == AppSession.State.RESTORING) {
                    val restored = try { auth.restore() } catch (_: Exception) { oldAccount }
                    dataStore.setMyProfile(restored.profile)
                    appSession.signIn(restored)
                }
                switchMessage = "유저 전환 실패: ${error.message ?: error.javaClass.simpleName}"
                if (error is CancellationException) throw error
            } finally {
                switching = false
                switchBusy = false
            }
        }
    }

    init {
        DevMode.init(application)
        viewModelScope.launch {
            snapshotFlow { DevMode.enabled }.collect { enabled ->
                if (enabled) try { DevMode.setOffset(withTimeout(5000) { dev.status() }.offsetSeconds) }
                catch (error: Exception) { if (error is CancellationException) throw error }
            }
        }
        if (appSession.state == AppSession.State.RESTORING) viewModelScope.launch {
            try {
                val account = auth.restore()
                dataStore.setMyProfile(account.profile)
                appSession.signIn(account)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                appSession.failRestoring()
            }
        }
    }
}
