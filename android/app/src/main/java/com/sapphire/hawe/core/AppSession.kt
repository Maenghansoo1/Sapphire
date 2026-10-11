package com.sapphire.hawe.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.core.model.NotificationPrefs

class AppSession(initialState: State = State.SIGNED_OUT, initialAccount: MyAccount = MyAccount.mock) {
    enum class State { SIGNED_OUT, SIGNED_IN }
    var state by mutableStateOf(initialState)
        private set
    var account by mutableStateOf(initialAccount)
    var notificationPrefs by mutableStateOf(NotificationPrefs())
    fun signIn() { state = State.SIGNED_IN }
    fun signOut() { state = State.SIGNED_OUT }
    fun withdraw() { account = MyAccount.mock; notificationPrefs = NotificationPrefs(); state = State.SIGNED_OUT }
}
