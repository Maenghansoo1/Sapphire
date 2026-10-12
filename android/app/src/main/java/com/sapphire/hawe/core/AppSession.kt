package com.sapphire.hawe.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.core.model.NotificationPrefs

class AppSession(initialState: State = State.SIGNED_OUT, initialAccount: MyAccount = MyAccount.mock,
    private val onCleared: () -> Unit = {}) {
    enum class State { SIGNED_OUT, SIGNED_IN, RESTORING }
    var state by mutableStateOf(initialState)
        private set
    var account by mutableStateOf(initialAccount)
    var notificationPrefs by mutableStateOf(NotificationPrefs())
    fun beginRestoring() { state = State.RESTORING }
    fun failRestoring() { state = State.SIGNED_OUT }
    fun signIn(account: MyAccount) { this.account = account; state = State.SIGNED_IN }
    fun signOut() { onCleared(); state = State.SIGNED_OUT }
    fun withdraw() { onCleared(); account = MyAccount.mock; notificationPrefs = NotificationPrefs(); state = State.SIGNED_OUT }
}
