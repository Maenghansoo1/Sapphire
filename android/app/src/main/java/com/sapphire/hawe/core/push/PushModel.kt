package com.sapphire.hawe.core.push

import com.sapphire.hawe.core.model.NotificationPrefs

enum class PushKind(val value: String) {
    TOPIC("topic"), POKE("poke"), COMMENT("comment");
    companion object { fun fromValue(value: String?): PushKind? = entries.firstOrNull { it.value == value } }
}

data class PushDestination(val kind: PushKind, val groupId: String?, val submissionId: String?)
fun parsePushDestination(type: String?, groupId: String?, submissionId: String?): PushDestination? =
    PushKind.fromValue(type)?.let { PushDestination(it, groupId?.takeIf(String::isNotBlank),
        submissionId?.takeIf(String::isNotBlank)) }

data class PushGroupSnapshot(val id: Long, val lastMissionId: Long?, val lastMissionSubmittedByMe: Boolean)
sealed interface PushTarget {
    data object None : PushTarget
    data class Mission(val groupId: Long) : PushTarget
    data class Results(val groupId: Long, val missionId: Long) : PushTarget
}

fun pushTarget(destination: PushDestination, groups: List<PushGroupSnapshot>): PushTarget {
    val first = groups.firstOrNull() ?: return PushTarget.None
    val requested = destination.groupId?.toLongOrNull()
    val group = groups.firstOrNull { it.id == requested } ?: first
    return if (destination.kind == PushKind.COMMENT && group.lastMissionSubmittedByMe &&
        group.lastMissionId != null) PushTarget.Results(group.id, group.lastMissionId)
    else PushTarget.Mission(group.id)
}

fun isNotificationEnabled(prefs: NotificationPrefs, kind: PushKind, groupNotify: Boolean? = null): Boolean =
    prefs.all && groupNotify != false && when (kind) {
        PushKind.TOPIC -> prefs.topic
        PushKind.POKE -> prefs.poke
        PushKind.COMMENT -> prefs.comment
    }

data class PushTexts(val title: String, val body: String)
fun defaultPushTexts(kind: PushKind, groupName: String?): PushTexts = PushTexts(
    groupName?.takeIf(String::isNotBlank) ?: "HA'WE", when (kind) {
        PushKind.TOPIC -> "오늘의 주제가 도착했어요"
        PushKind.POKE -> "누군가 콕 찔렀어요"
        PushKind.COMMENT -> "내 작품에 댓글이 달렸어요"
    })

data class PushChannelSpec(val id: String, val name: String, val description: String)
/** 없앤 알림의 채널 — 이미 설치한 기기에서 지워요(그룹원 작품 제출 알림, 2026-10-19). */
val removedPushChannelIds = listOf("friend_done")
val pushChannelSpecs = listOf(
    PushChannelSpec("topic", "오늘의 주제", "그룹의 오늘의 주제가 도착하면 알려드려요"),
    PushChannelSpec("poke", "콕 찌르기", "친구가 콕 찌르면 알려드려요"),
    PushChannelSpec("comment", "댓글", "내 작품에 댓글이 달리면 알려드려요"),
)
