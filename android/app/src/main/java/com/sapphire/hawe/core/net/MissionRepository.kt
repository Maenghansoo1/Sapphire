package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.PokeKind
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class CurrentMissionState(val mission: ServerMission?, val nextPublishAt: Instant?)
data class ServerMission(val id: Long, val number: Int, val topic: String, val type: MissionType?,
    val template: String, val spec: MissionSpec, val publishedAt: Instant, val deadlineAt: Instant,
    val closesAt: Instant, val submittedByMe: Boolean, val members: Map<Long, Boolean>,
    val pokedByMe: Map<Long, Set<PokeKind>> = emptyMap())

fun mediaToType(media: String): MissionType? = when (media) {
    "PHOTO" -> MissionType.PHOTO
    "TEXT" -> MissionType.WRITE
    "DRAW" -> MissionType.DRAW
    "SLIDER" -> MissionType.SLIDER
    else -> null
}

internal fun paramsToSpec(params: JsonObject): MissionSpec {
    fun text(key: String) = params[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    return MissionSpec(
        seconds = params["seconds"]?.let { runCatching { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.content.toIntOrNull() }.getOrNull() },
        word = text("word"), drawFrom = text("a"), drawTo = text("b"),
        optionA = text("optionA"), optionB = text("optionB"),
        question = text("question"), topic = text("topic"), left = text("left"), right = text("right"),
    )
}

class MissionRepository(private val api: ApiClient) {
    suspend fun current(groupId: Long, myUserId: Long): CurrentMissionState {
        val response: TodayMissionsDto = api.get("/api/groups/$groupId/missions/today")
        val mission = response.missions.firstOrNull()?.toModel()
        return CurrentMissionState(mission, response.nextPublishAt?.let { OffsetDateTime.parse(it).toInstant() })
    }

    /** 미션 한 개(지난 미션 포함). 달력에서 연 지난 미션의 틀·빈칸을 알아낼 때 써요. */
    suspend fun mission(missionId: Long): ServerMission {
        val row: MissionDto = api.get("/api/missions/$missionId")
        return row.toModel()
    }

    private fun MissionDto.toModel() = ServerMission(missionId, number, content, media?.let(::mediaToType), template,
        paramsToSpec(params), OffsetDateTime.parse(publishedAt).toInstant(),
        OffsetDateTime.parse(deadlineAt).toInstant(), OffsetDateTime.parse(closesAt).toInstant(),
        submittedByMe, members.associate { it.userId to it.submitted },
        members.associate { member -> member.userId to member.pokedByMe.mapNotNull(PokeKind::fromWire).toSet() }
            .filterValues { it.isNotEmpty() })
}
