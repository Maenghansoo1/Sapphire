package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.PokeKind
import com.sapphire.hawe.core.model.Profile
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class MissionFormatTest {
    @Test fun multiMissionSelectionAndSubmittedMessages() {
        val first = com.sapphire.hawe.core.model.TodayMission(10,
            com.sapphire.hawe.core.model.GroupDetail.mock, MissionType.WRITE, "주제", 1,
            now, now.plusSeconds(3600), emptyList())
        val second = first.copy(id = first.id + 1)
        assertEquals(first, selectedMission(listOf(first, second), null))
        assertEquals(second, selectedMission(listOf(first, second), second.id))
        assertEquals(first, selectedMission(listOf(first, second), 999))
        assertEquals("오늘의 미션을 올렸어요", submittedToastText(0, 1, false))
        assertEquals("지각으로 올렸어요", submittedToastText(0, 1, true))
        assertEquals("2번째 미션을 올렸어요", submittedToastText(1, 2, false))
        assertEquals("2번째 미션을 지각으로 올렸어요", submittedToastText(1, 2, true))
    }
    private val now = Instant.parse("2026-10-12T00:00:00Z")

    @Test fun countdownClampsAndPadsHours() {
        assertEquals("00:00:00", countdownText(now, now))
        assertEquals("00:00:00", countdownText(now.minusSeconds(1), now))
        assertEquals("00:00:59", countdownText(now.plusSeconds(59), now))
        assertEquals("01:01:01", countdownText(now.plusSeconds(3661), now))
        assertEquals("24:00:00", countdownText(now.plusSeconds(86400), now))
        assertEquals("100:00:00", countdownText(now.plusSeconds(360000), now))
        assertEquals("00:00:01", countdownText(now.plusMillis(1900), now))
    }

    @Test fun dateUsesSeoulRegardlessOfDeviceSettings() {
        val originalZone = TimeZone.getDefault()
        val originalLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            assertEquals("10월 11일", missionDateText(Instant.parse("2026-10-11T14:59:59Z")))
            assertEquals("10월 12일", missionDateText(Instant.parse("2026-10-11T15:00:00Z")))
        } finally {
            TimeZone.setDefault(originalZone)
            Locale.setDefault(originalLocale)
        }
    }

    @Test fun memberAndNoticeCopy() {
        val mine = MissionMember(1L, Profile.mockMine, isMe = true)
        assertEquals("하늘 (나)", memberDisplayName(mine))
        assertEquals("하늘", memberDisplayName(mine.copy(isMe = false)))
        assertEquals("내 미션을 올리면 친구들의 그림이 열려요.", lockNoticeText(MissionType.DRAW))
        assertEquals("내 미션을 올리면 친구들의 사진이 열려요.", lockNoticeText(MissionType.PHOTO))
        assertEquals("내 미션을 올리면 친구들의 글이 열려요.", lockNoticeText(MissionType.WRITE))
        assertEquals("내 미션을 올리면 친구들의 글이 열려요.", lockNoticeText(MissionType.WRITE, MissionTemplates.TEXT_ONE_WORD))
        assertEquals("내 답을 올리면 친구들의 답과 그룹 결과가 열려요.",
            lockNoticeText(MissionType.WRITE, MissionTemplates.TEXT_BALANCE))
        assertEquals("내 답을 올리면 친구들의 답과 그룹 결과가 열려요.",
            lockNoticeText(MissionType.SLIDER, MissionTemplates.SLIDER_TASTE))
        assertEquals("내 미션을 올리면 친구들의 슬라이더가 열려요.", lockNoticeText(MissionType.SLIDER))
        assertEquals("민준님을 콕 찔렀어요", pokeToastText("민준"))
        assertEquals("민준님에게 똥을 뿌렸어요", flingToastText("민준"))
        assertEquals("민준님을 콕 찔렀어요", pokeSentText(PokeKind.POKE, "민준"))
        assertEquals("민준님에게 똥을 뿌렸어요", pokeSentText(PokeKind.FLING, "민준"))
        assertEquals(listOf("콕", "똥"), PokeKind.entries.map(::pokeButtonLabel))
        assertEquals("민준님 콕 찌르기", pokeButtonDescription(PokeKind.POKE, "민준", sent = false))
        assertEquals("민준님에게 똥 뿌리기", pokeButtonDescription(PokeKind.FLING, "민준", sent = false))
        assertEquals("민준님을 콕 찔렀어요", pokeButtonDescription(PokeKind.POKE, "민준", sent = true))
        assertEquals("민준님에게 똥을 뿌렸어요", pokeButtonDescription(PokeKind.FLING, "민준", sent = true))
    }

    @Test fun deadlineShowsNormalLateAndClosedStates() {
        val deadline = Instant.parse("2026-10-12T14:00:00Z")
        val closes = Instant.parse("2026-10-13T00:00:00Z")
        assertEquals("마감 오후 11:00", deadlineText(deadline, closes, now))
        assertEquals("마감이 지났어요 · 지금 올리면 지각이에요", deadlineText(deadline, closes, deadline))
        assertNull(deadlineText(deadline, closes, closes))
        val nextDay = Instant.parse("2026-10-13T14:00:00Z")
        assertEquals("마감 내일 오후 11:00", deadlineText(nextDay, nextDay.plusSeconds(3600), now))
    }

    @Test fun templateGuideAndButtonCopy() {
        val balance = MissionSpec(optionA = "짜장면", optionB = "짬뽕")
        val slider = MissionSpec(question = "오늘 컨디션", left = "방전", right = "풀충전")
        assertEquals("띄어쓰기 없이 한 단어로 남겨요", templateGuideText(MissionTemplates.TEXT_ONE_WORD, MissionSpec()))
        assertEquals("별점 1~5개와 한 줄 후기를 남겨요", templateGuideText(MissionTemplates.TEXT_REVIEW, MissionSpec()))
        assertEquals("‘짜장면’ vs ‘짬뽕’ 둘 중 하나를 골라요", templateGuideText(MissionTemplates.TEXT_BALANCE, balance))
        assertEquals("둘 중 하나를 골라요", templateGuideText(MissionTemplates.TEXT_BALANCE, MissionSpec(optionA = "짜장면")))
        assertEquals("‘방전’ ↔ ‘풀충전’ 사이에서 내 위치를 골라요", templateGuideText(MissionTemplates.SLIDER_POSITION, slider))
        assertEquals("‘방전’ ↔ ‘풀충전’ 사이에서 내 위치를 골라요", templateGuideText(MissionTemplates.SLIDER_TASTE, slider))
        assertEquals("내 위치를 골라요", templateGuideText(MissionTemplates.SLIDER_TASTE, MissionSpec(left = " ", right = "최고")))
        assertEquals("내 위치와 우리 그룹 평균을 함께 예측해요", templateGuideText(MissionTemplates.SLIDER_CONSENSUS, slider))
        // 기존 틀은 주제 문장만으로 충분해서 안내가 없어요
        assertNull(templateGuideText(MissionTemplates.TEXT_ACROSTIC, MissionSpec(word = "퇴근길")))
        assertNull(templateGuideText(MissionTemplates.PHOTO_NOW, MissionSpec()))
        // A를 B로는 뜻이 잘 안 전해져서 안내를 둬요(받침에 맞는 조사, 사람이 대상이면 B 만)
        assertEquals("‘구름’을 ‘풍선’처럼 보이게 바꿔서 그려요",
            templateGuideText(MissionTemplates.DRAW_A_TO_B, MissionSpec(drawFrom = "구름", drawTo = "풍선")))
        assertEquals("‘고양이’를 ‘화분’처럼 보이게 바꿔서 그려요",
            templateGuideText(MissionTemplates.DRAW_A_TO_B, MissionSpec(drawFrom = "고양이", drawTo = "화분")))
        assertEquals("‘풍선’처럼 보이게 바꿔서 그려요",
            templateGuideText(MissionTemplates.DRAW_A_TO_B, MissionSpec(drawFrom = "구름", drawTo = "풍선", hasTarget = true)))
        assertEquals("‘집’처럼 보이게 바꿔서 그려요",
            templateGuideText(MissionTemplates.DRAW_A_TO_B, MissionSpec(drawFrom = "[대상]", drawTo = "집")))
        assertEquals("주제를 다른 모습으로 바꿔서 그려요", templateGuideText(MissionTemplates.DRAW_A_TO_B, MissionSpec()))
        assertNull(templateGuideText(MissionTemplates.DRAW_SKETCH, MissionSpec(subject = "고양이")))

        assertEquals("하나 고르기", missionActionTitle(MissionType.WRITE, MissionTemplates.TEXT_BALANCE))
        assertEquals("글 쓰기", missionActionTitle(MissionType.WRITE, MissionTemplates.TEXT_REVIEW))
        assertEquals("슬라이더 정하기", missionActionTitle(MissionType.SLIDER, MissionTemplates.SLIDER_POSITION))
        assertEquals("사진 찍기", missionActionTitle(MissionType.PHOTO, MissionTemplates.PHOTO_NOW))
        assertEquals("갤러리 둘러보기", missionActionTitle(MissionType.PHOTO, MissionTemplates.PHOTO_GALLERY))
        assertEquals("사진 찍기", missionActionTitle(MissionType.PHOTO, MissionTemplates.PHOTO_COLOR))
        assertEquals("결과 보기", resultsActionTitle(MissionTemplates.TEXT_BALANCE))
        assertEquals("결과 보기", resultsActionTitle(MissionTemplates.SLIDER_CONSENSUS))
        assertEquals("작품 보기", resultsActionTitle(MissionTemplates.TEXT_ONE_WORD))
        assertEquals("작품 보기", resultsActionTitle(MissionTemplates.DRAW_SKETCH))
        assertEquals("앱을 업데이트하면 이 미션을 할 수 있어요", UNSUPPORTED_MISSION_TEXT)
    }
}
