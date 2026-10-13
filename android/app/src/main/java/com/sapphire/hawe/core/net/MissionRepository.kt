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
    else -> null
}

internal fun paramsToSpec(params: JsonObject): MissionSpec = MissionSpec(
    seconds = params["seconds"]?.let { runCatching { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.content.toIntOrNull() }.getOrNull() },
    word = params["word"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() },
    drawFrom = params["a"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() },
    drawTo = params["b"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() },
)

class MissionRepository(private val api: ApiClient) {
    suspend fun current(groupId: Long, myUserId: Long): CurrentMissionState {
        val response: TodayMissionsDto = api.get("/api/groups/$groupId/missions/today")
        val mission = response.missions.firstOrNull()?.let { row ->
            ServerMission(row.missionId, row.number, row.content, mediaToType(row.media), row.template,
                paramsToSpec(row.params), OffsetDateTime.parse(row.publishedAt).toInstant(),
                OffsetDateTime.parse(row.deadlineAt).toInstant(), OffsetDateTime.parse(row.closesAt).toInstant(),
                row.submittedByMe, row.members.associate { it.userId to it.submitted },
                row.members.associate { member -> member.userId to member.pokedByMe.mapNotNull(PokeKind::fromWire).toSet() }
                    .filterValues { it.isNotEmpty() })
        }
        return CurrentMissionState(mission, response.nextPublishAt?.let { OffsetDateTime.parse(it).toInstant() })
    }
}
