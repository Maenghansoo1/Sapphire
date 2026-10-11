package com.sapphire.hawe.data

import com.sapphire.hawe.model.AvatarContent
import com.sapphire.hawe.model.GroupDetail
import com.sapphire.hawe.model.GroupMember
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.Profile
import com.sapphire.hawe.model.ProfileCharacter
import com.sapphire.hawe.model.Relationship
import com.sapphire.hawe.model.SEOUL
import com.sapphire.hawe.model.StoredGroup
import com.sapphire.hawe.model.StoredMission
import com.sapphire.hawe.model.Submission
import com.sapphire.hawe.model.newId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 서버 연결 전 체험용 그룹 — 03 초대 코드로 참여할 수 있어요 (iOS 와 같은 코드).
 * K7Q2PX: 컴공 동기(친구 4명, 지난 기록·오늘 올린 친구 있음) · LOVE22: 우리 둘(연인 1명) · FULL00: 정원 초과
 */
object DemoSeed {
    fun group(code: String): StoredGroup? = when (code) {
        "K7Q2PX" -> friends()
        "LOVE22" -> lovers()
        "FULL00" -> full()
        else -> null
    }

    private val nine = LocalTime.of(9, 0)

    private fun member(name: String, c: ProfileCharacter, owner: Boolean = false) =
        GroupMember(newId(), Profile(AvatarContent.Character(c), name), isOwner = owner)

    /** 컴공 동기 — 2026년 6월 2일부터, 오늘은 민준·지우·서연이 이미 올렸어요. */
    private fun friends(): StoredGroup {
        val members = listOf(
            member("민준", ProfileCharacter.Gamja, owner = true),
            member("지우", ProfileCharacter.Kkotnim),
            member("서연", ProfileCharacter.Mulbangul),
            member("현우", ProfileCharacter.Pilleum),
        )
        val created = LocalDate.of(2026, 6, 2)
        val missions = history(created, members, todayDone = 3)
        return StoredGroup(
            GroupDetail(newId(), "컴공 동기", Relationship.Friend, created, missions.size, "K7Q2PX", nine, true, members),
            missions,
        )
    }

    /** 우리 둘 — 연인, 함께한 지 128일. 상대만 있어요. */
    private fun lovers(): StoredGroup {
        val start = LocalDate.now(SEOUL).minusDays(127)
        return StoredGroup(
            GroupDetail(
                newId(), "우리 둘", Relationship.Lover, start, 0, "LOVE22", nine, true,
                listOf(member("도윤", ProfileCharacter.Ipsagwi, owner = true)), startDate = start,
            ),
        )
    }

    /** 정원 초과 확인용 (6명) */
    private fun full(): StoredGroup {
        val names = listOf("가온" to ProfileCharacter.Gamja, "나래" to ProfileCharacter.Gomdol, "다온" to ProfileCharacter.Kkotnim,
            "라온" to ProfileCharacter.Ipsagwi, "마루" to ProfileCharacter.Mulbangul, "바다" to ProfileCharacter.Pilleum)
        return StoredGroup(
            GroupDetail(newId(), "꽉 찬 그룹", Relationship.Friend, LocalDate.now(SEOUL), 0, "FULL00", nine, true, names.map { member(it.first, it.second) }),
        )
    }

    /** 만든 날부터 오늘까지 미션. 지난날은 3~4명이 올렸고, 오늘은 앞 [todayDone] 명이 올렸어요. */
    private fun history(start: LocalDate, members: List<GroupMember>, todayDone: Int): List<StoredMission> {
        val pattern = listOf(MissionType.Draw, MissionType.Photo, MissionType.Write, MissionType.Voice, MissionType.Draw,
            MissionType.Photo, MissionType.Voice, MissionType.Write, MissionType.Draw, MissionType.Photo)
        val (todayOpens, _) = DataStore.missionWindow(nine)
        val missions = mutableListOf<StoredMission>()
        var opens = start.atTime(nine).atZone(SEOUL)
        var index = 0
        while (!opens.toInstant().isAfter(todayOpens)) {
            val type = pattern[index % pattern.size]
            val topics = TopicPool.topics(type)
            val isToday = opens.toInstant() == todayOpens
            val doneCount = if (isToday) todayDone else if (index % 5 == 4) 0 else 3 + index % 2
            val submissions = members.take(doneCount).mapIndexed { offset, m ->
                sample(m, type, opens.toInstant().plusSeconds((offset + 1) * 47L * 60), index + offset)
            }
            missions += StoredMission(newId(), type, topics[index % topics.size], index + 1, opens.toInstant(), opens.plusDays(1).toInstant(), submissions)
            index++
            opens = opens.plusDays(1)
        }
        return missions
    }

    private fun sample(m: GroupMember, type: MissionType, at: Instant, seed: Int): Submission {
        val texts = listOf(
            "편의점 사장님이 \"오늘도 수고 많았어요\" 하면서 영수증을 건네주셨다. 별말 아닌데 하루가 풀렸음.",
            "엄마가 전화로 밥은 잘 먹고 다니냐고 물어봤다.",
            "조별과제 팀원이 네 덕분에 끝났다고 말해줬다. 사실 나도 덕분이었는데.",
            "교수님이 질문 좋다고 하셨다.",
        )
        val base = listOf(6, 10, 16, 22, 14, 20, 24, 12, 8, 18, 22, 16, 10, 6, 12, 20, 24, 18, 12, 8, 14, 20, 16, 10, 6, 10, 14, 8)
        val shift = (seed * 5) % base.size
        return Submission(
            id = newId(),
            authorId = m.id,
            author = m.displayed,
            isMine = false,
            type = type,
            createdAt = at,
            text = if (type == MissionType.Write) texts[seed % texts.size] else null,
            durationSec = if (type == MissionType.Voice) (7 + seed % 6).toDouble() else null,
            waveform = if (type == MissionType.Voice) (base.drop(shift) + base.take(shift)).map { it / 24f } else emptyList(),
            likeCount = seed % 4,
        )
    }
}
