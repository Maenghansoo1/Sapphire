package com.sapphire.hawe.core.data

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

object DemoSeed {
    fun group(code: String, now: Instant): StoredGroup? = when (code) {
        "K7Q2PX" -> friends(now)
        "LOVE22" -> lovers(now)
        "FULL00" -> full(now)
        else -> null
    }

    private fun member(name: String, character: ProfileCharacter, owner: Boolean = false) =
        GroupMember(UUID.randomUUID(), Profile(AvatarContent.Character(character), name), isOwner = owner)

    private fun friends(now: Instant): StoredGroup {
        val members = listOf(member("민준", ProfileCharacter.GAMJA, true), member("지우", ProfileCharacter.KKOTNIM),
            member("서연", ProfileCharacter.MULBANGUL), member("현우", ProfileCharacter.PILLEUM))
        val start = LocalDate.of(2026, 6, 2).atStartOfDay(HaweTime.SEOUL).toInstant()
        val pattern = listOf(MissionType.DRAW, MissionType.PHOTO, MissionType.WRITE, MissionType.VOICE,
            MissionType.DRAW, MissionType.PHOTO, MissionType.VOICE, MissionType.WRITE, MissionType.DRAW, MissionType.PHOTO)
        val current = HaweTime.window(LocalTime.of(9, 0), now).first
        var opens = LocalDate.of(2026, 6, 2).atTime(9, 0).atZone(HaweTime.SEOUL).toInstant()
        var index = 0
        val missions = mutableListOf<StoredMission>()
        while (opens <= current) {
            val type = pattern[index % pattern.size]
            val done = if (opens == current) 3 else if (index % 5 == 4) 0 else 3 + index % 2
            val submissions = members.take(done).mapIndexed { offset, person ->
                sample(person, type, opens.plusSeconds((offset + 1L) * 47 * 60), index + offset)
            }
            missions.add(StoredMission(UUID.randomUUID(), type,
                TopicPool.topics(type)[index % TopicPool.topics(type).size], index + 1,
                opens, opens.atZone(HaweTime.SEOUL).plusDays(1).toInstant(), submissions))
            opens = opens.atZone(HaweTime.SEOUL).plusDays(1).toInstant()
            index++
        }
        return StoredGroup(GroupDetail(UUID.randomUUID(), "컴공 동기", Relationship.FRIEND,
            start, missions.size, "K7Q2PX", members = members), missions.toList())
    }

    private fun lovers(now: Instant): StoredGroup {
        val date = now.atZone(HaweTime.SEOUL).toLocalDate().minusDays(127)
        return StoredGroup(GroupDetail(UUID.randomUUID(), "우리 둘", Relationship.LOVER,
            date.atStartOfDay(HaweTime.SEOUL).toInstant(), 0, "LOVE22",
            members = listOf(member("도윤", ProfileCharacter.IPSAGWI, true)), startDate = date))
    }

    private fun full(now: Instant): StoredGroup = StoredGroup(GroupDetail(UUID.randomUUID(), "꽉 찬 그룹",
        Relationship.FRIEND, now, 0, "FULL00", members = listOf(
            member("가온", ProfileCharacter.GAMJA), member("나래", ProfileCharacter.GOMDOL),
            member("다온", ProfileCharacter.KKOTNIM), member("라온", ProfileCharacter.IPSAGWI),
            member("마루", ProfileCharacter.MULBANGUL), member("바다", ProfileCharacter.PILLEUM))))

    private fun sample(person: GroupMember, type: MissionType, at: Instant, seed: Int): Submission {
        val texts = listOf("편의점 사장님이 \"오늘도 수고 많았어요\" 하면서 영수증을 건네주셨다. 별말 아닌데 하루가 풀렸음.",
            "엄마가 전화로 밥은 잘 먹고 다니냐고 물어봤다.", "조별과제 팀원이 네 덕분에 끝났다고 말해줬다. 사실 나도 덕분이었는데.", "교수님이 질문 좋다고 하셨다.")
        val base = listOf(6, 10, 16, 22, 14, 20, 24, 12, 8, 18, 22, 16, 10, 6, 12, 20, 24, 18, 12, 8, 14, 20, 16, 10, 6, 10, 14, 8)
        val shift = (seed * 5) % base.size
        return Submission(UUID.randomUUID(), person.displayed, false, type, at,
            text = if (type == MissionType.WRITE) texts[seed % texts.size] else null,
            duration = if (type == MissionType.VOICE) (7 + seed % 6).toDouble() else null,
            waveform = if (type == MissionType.VOICE) (base.drop(shift) + base.take(shift)).map { it / 24f } else emptyList(),
            likeCount = seed % 4, authorID = person.id)
    }
}
