package com.sapphire.hawe.core

import com.sapphire.hawe.core.model.MyAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSessionTest {
    @Test fun restoreAndClearTransitions() {
        var cleared = 0
        val session = AppSession(onCleared = { cleared++ })
        session.beginRestoring()
        assertEquals(AppSession.State.RESTORING, session.state)
        session.failRestoring()
        assertEquals(AppSession.State.SIGNED_OUT, session.state)
        session.beginRestoring()
        val account = MyAccount.mock
        session.signIn(account)
        assertEquals(account, session.account)
        assertEquals(AppSession.State.SIGNED_IN, session.state)
        session.signOut()
        assertEquals(AppSession.State.SIGNED_OUT, session.state)
        session.withdraw()
        assertEquals(2, cleared)
    }
}
