package com.sapphire.hawe.feature.mypage

import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.core.data.StoredGroup

fun accountLines(account: MyAccount): List<String> = buildList {
    account.email?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (account.signUpProvider != LoginProvider.EMAIL) add("${account.signUpProvider.displayName} 로그인")
}

data class StatsDisplay(val completed: String, val streak: String, val groups: String)
/** 서버에서 아직 못 받아왔으면(null) 숫자 대신 "-"를 보여줘요. */
fun statsDisplay(stats: ActivityStats?) = if (stats == null) StatsDisplay("-", "-", "-") else StatsDisplay(
    stats.completedMissions.toString(), "${stats.streakDays}일", "${stats.groupCount}개")
fun byTypeCount(stats: ActivityStats, type: MissionType) = stats.byType[type] ?: 0
fun byTypeText(stats: ActivityStats?, type: MissionType) = stats?.let { byTypeCount(it, type).toString() } ?: "-"

enum class NotificationKey { ALL, TOPIC, POKE, COMMENT }
fun NotificationPrefs.with(key: NotificationKey, enabled: Boolean): NotificationPrefs = when (key) {
    NotificationKey.ALL -> copy(all = enabled)
    NotificationKey.TOPIC -> if (all) copy(topic = enabled) else this
    NotificationKey.POKE -> if (all) copy(poke = enabled) else this
    NotificationKey.COMMENT -> if (all) copy(comment = enabled) else this
}
fun individualEnabled(prefs: NotificationPrefs) = prefs.all

fun canUnlink(account: MyAccount, provider: LoginProvider) =
    provider in account.linkedProviders && account.linkedProviders.size > 1
fun MyAccount.linking(provider: LoginProvider) = copy(linkedProviders = linkedProviders + provider)
fun MyAccount.unlinking(provider: LoginProvider) =
    if (canUnlink(this, provider)) copy(linkedProviders = linkedProviders - provider) else this
sealed interface ProviderRowState {
    data class Linked(val canUnlink: Boolean) : ProviderRowState
    data object NotLinked : ProviderRowState
}
fun providerRowState(account: MyAccount, provider: LoginProvider): ProviderRowState =
    if (provider in account.linkedProviders) ProviderRowState.Linked(canUnlink(account, provider))
    else ProviderRowState.NotLinked

data class ChangePasswordState(val current: String = "", val new: String = "", val confirm: String = "") {
    val isNewValid get() = new.length >= 8 && new.any { it in 'a'..'z' || it in 'A'..'Z' } &&
        new.any { it in '0'..'9' }
    val newError get() = if (new.isNotEmpty() && !isNewValid) "8자 이상, 영문과 숫자를 함께 써 주세요" else null
    val confirmError get() = if (confirm.isNotEmpty() && confirm != new) "비밀번호가 일치하지 않아요" else null
    val canSave get() = current.isNotEmpty() && isNewValid && confirm == new && new != current
}

data class ProfileEditState(val avatar: AvatarContent, val nickname: String) {
    val canSave get() = nickname.trim().length in 2..10
    val profileToSave get() = Profile(avatar, nickname.trim())
}
data class WithdrawState(val agreed: Boolean = false) { val canWithdraw get() = agreed }

fun withdrawBlockers(groups: List<StoredGroup>): List<String> = groups.mapNotNull { group ->
    group.detail.name.takeIf { group.detail.amIOwner && group.detail.members.size > 1 }
}

object AppVersionInfo {
    const val footer = "Made by Sapphire Developers\n© 2026 Sapphire Developers. All rights reserved."
    fun isLatest(current: String, latest: String) = current == latest
    fun versionStatusTitle(current: String, latest: String) =
        if (isLatest(current, latest)) "최신 버전을 사용 중이에요" else "새 버전이 있어요"
}
fun mailtoUri(email: String) = "mailto:$email"
fun playStoreMarketUri(packageName: String) = "market://details?id=$packageName"
fun playStoreWebUri(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
