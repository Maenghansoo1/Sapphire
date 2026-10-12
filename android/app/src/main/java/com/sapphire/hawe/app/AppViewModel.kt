package com.sapphire.hawe.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sapphire.hawe.BuildConfig
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.net.ApiClient
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.KeystoreTokenStore
import com.sapphire.hawe.core.push.PushCenter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val dataStore = DataStore(File(application.cacheDir, "hawe-media"))
    private val tokens = KeystoreTokenStore(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = ApiClient(BuildConfig.API_BASE_URL, tokens,
        onSessionExpired = { mainHandler.post { appSession.signOut() } })
    val auth: AuthRepository = AuthRepository(api, tokens)
    val groups = GroupRepository(api)
    val appSession: AppSession = AppSession(initialState = if (auth.hasSession) AppSession.State.RESTORING else AppSession.State.SIGNED_OUT,
        onCleared = { auth.clearSession(); dataStore.reset() })
    val push = PushCenter()

    init {
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
