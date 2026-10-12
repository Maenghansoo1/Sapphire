package com.sapphire.hawe.core.net

interface TokenStore {
    val accessToken: String?
    val refreshToken: String?
    fun save(access: String, refresh: String)
    fun clear()
}

class InMemoryTokenStore : TokenStore {
    @Volatile private var access: String? = null
    @Volatile private var refresh: String? = null

    override val accessToken: String? get() = access
    override val refreshToken: String? get() = refresh

    @Synchronized override fun save(access: String, refresh: String) {
        this.access = access
        this.refresh = refresh
    }

    @Synchronized override fun clear() {
        access = null
        refresh = null
    }
}
