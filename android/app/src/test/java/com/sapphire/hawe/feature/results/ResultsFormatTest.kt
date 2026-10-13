package com.sapphire.hawe.feature.results

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class ResultsFormatTest {
    private val now = Instant.parse("2026-10-12T00:00:00Z")

    @Test fun seoulTimeIgnoresDeviceSettings() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            assertEquals("오전 9:12", koreanTimeText(Instant.parse("2026-10-12T00:12:00Z")))
            assertEquals("오전 12:05", koreanTimeText(Instant.parse("2026-10-11T15:05:00Z")))
            assertEquals("오후 12:00", koreanTimeText(Instant.parse("2026-10-12T03:00:00Z")))
            assertEquals("오후 11:59", koreanTimeText(Instant.parse("2026-10-12T14:59:00Z")))
            assertEquals("10월 12일", koreanMonthDayText(Instant.parse("2026-10-11T15:00:00Z")))
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
        }
    }

    @Test fun relativeTime() {
        assertEquals("방금", koreanRelativeText(now.minusSeconds(30), now))
        assertEquals("방금", koreanRelativeText(now.minusSeconds(59), now))
        assertEquals("1분 전", koreanRelativeText(now.minusSeconds(60), now))
        assertEquals("59분 전", koreanRelativeText(now.minusSeconds(3540), now))
        assertEquals("2시간 전", koreanRelativeText(now.minusSeconds(7200), now))
        assertEquals("1일 전", koreanRelativeText(now.minusSeconds(90000), now))
        assertEquals("1주 전", koreanRelativeText(now.minusSeconds(8 * 86400), now))
        assertEquals("방금", koreanRelativeText(now.plusSeconds(10), now))
    }

    @Test fun feedAndCommentCopy() {
        assertEquals("3 / 5명 완료", progressText(3, 5))
        assertEquals("5명 모두 완료", progressText(5, 5))
        assertEquals("1명 모두 완료", progressText(1, 1))
        assertEquals("0 / 4명 완료", progressText(0, 4))
        assertEquals("사진을 눌러서 자세히 보기", hintText(MissionType.PHOTO))
        assertEquals("그림을 눌러서 자세히 보기", hintText(MissionType.DRAW))
        assertEquals("글을 눌러서 자세히 보기", hintText(MissionType.WRITE))
        assertEquals("슬라이더를 눌러서 자세히 보기", hintText(MissionType.SLIDER))
        assertNull(likersText(emptyList(), 0))
        assertEquals("지우님이 좋아해요", likersText(listOf("지우"), 1))
        assertEquals("지우, 서연님이 좋아해요", likersText(listOf("지우", "서연"), 2))
        assertEquals("지우, 서연님 외 3명이 좋아해요", likersText(listOf("지우", "서연"), 5))
        assertEquals("지우, 서연님이 좋아해요", likersText(listOf("지우", "서연"), 1))
        assertFalse(commentSendEnabled(""))
        assertFalse(commentSendEnabled("  "))
        assertFalse(commentSendEnabled("\n"))
        assertTrue(commentSendEnabled(" 안녕 "))
        assertEquals("민준님에게 댓글 남기기", commentPlaceholder("민준"))
    }

    @Test fun localImage() {
        val submission = Submission(1L, Profile.mockMine, true, MissionType.PHOTO, now)
        assertNull(localImageFile(submission))
        val file = File.createTempFile("hawe-result-", ".png")
        try {
            assertEquals(file, localImageFile(submission.copy(mediaURL = file.toURI())))
        } finally { file.delete() }
        assertNull(localImageFile(submission.copy(mediaURL = file.toURI())))
        assertNull(localImageFile(submission.copy(mediaURL = java.net.URI("https://example.com/a.png"))))
    }

    @Test fun newTemplateAnswerCopy() {
        assertEquals("★★★★☆", starsText(4))
        assertEquals("☆☆☆☆☆", starsText(0))
        assertEquals("★★★★★", starsText(9))
        assertEquals("☆☆☆☆☆", starsText(-1))
        val spec = MissionSpec(optionA = "짜장면", optionB = "짬뽕")
        assertEquals("A · 짜장면", balanceChoiceText("A", spec))
        assertEquals("B · 짬뽕", balanceChoiceText("B", spec))
        assertEquals("B", balanceChoiceText("B", MissionSpec()))
        assertNull(balanceChoiceText(null, spec))
        assertNull(balanceChoiceText("C", spec))
        assertEquals(setOf("TEXT_ONE_WORD", "TEXT_REVIEW", "TEXT_BALANCE", "SLIDER_POSITION", "SLIDER_TASTE",
            "SLIDER_CONSENSUS"), MissionTemplates.supported.filter(::hasTemplateAnswer).toSet())
    }

    @Test fun balancePercentsAlwaysAddUpTo100() {
        assertEquals(75 to 25, balancePercents(3, 1))
        assertEquals(33 to 67, balancePercents(1, 2))
        assertEquals(67 to 33, balancePercents(2, 1))
        assertEquals(100 to 0, balancePercents(1, 0))
        assertEquals(0 to 100, balancePercents(0, 6))
        assertEquals(0 to 0, balancePercents(0, 0))
        (0..6).forEach { a -> (0..6).forEach { b ->
            if (a + b > 0) assertEquals(100, balancePercents(a, b).let { it.first + it.second })
        } }
    }

    @Test fun averageAndDiffCopyKeepServerRounding() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)  // 소수점이 쉼표인 기기에서도 "62.5"
            assertEquals("62.5", decimalText(62.5))
            assertEquals("60", decimalText(60.0))
            assertEquals("0", decimalText(0.0))
            assertEquals("32.8", decimalText(32.8))
        } finally { Locale.setDefault(locale) }
        val spec = MissionSpec(left = "방전", right = "풀충전")
        assertEquals("내 위치는 평균보다 ‘풀충전’ 쪽으로 7.5", diffFromAverageText(7.5, spec))
        assertEquals("내 위치는 평균보다 ‘방전’ 쪽으로 32.8", diffFromAverageText(-32.8, spec))
        assertEquals("내 위치가 딱 평균이에요", diffFromAverageText(0.0, spec))
        assertEquals("내 위치는 평균보다 오른쪽으로 10", diffFromAverageText(10.0, MissionSpec()))
        assertEquals("내 위치는 평균보다 왼쪽으로 2.5", diffFromAverageText(-2.5, MissionSpec(left = "")))
        assertNull(diffFromAverageText(null, spec))
    }

    @Test fun consensusRanksAndWinners() {
        val ranking = listOf(ConsensusRank(2L, "잎사귀", 60, 2.5), ConsensusRank(3L, "서연", 65, 2.5),
            ConsensusRank(1L, "몽실이", 80, 17.5), ConsensusRank(4L, "지우", 40, 22.5))
        assertEquals(listOf(1, 1, 3, 4), competitionRanks(ranking))
        assertEquals(emptyList<Int>(), competitionRanks(emptyList()))
        assertEquals("잎사귀님, 서연님이 공동으로 가장 가깝게 맞혔어요", consensusWinnerText(ranking, setOf(2L, 3L), 1L))
        assertEquals("나와 서연님이 공동으로 가장 가깝게 맞혔어요", consensusWinnerText(ranking, setOf(2L, 3L), 2L))
        assertEquals("잎사귀님이 가장 가깝게 맞혔어요", consensusWinnerText(ranking.take(1), setOf(2L), 1L))
        assertEquals("내가 가장 가깝게 맞혔어요", consensusWinnerText(ranking.take(1), setOf(2L), 2L))
        assertNull(consensusWinnerText(ranking, emptySet(), 1L))
        // 1명뿐인 결과: 혼자 1등
        val alone = listOf(ConsensusRank(1L, "몽실이", 50, 0.0))
        assertEquals(listOf(1), competitionRanks(alone))
        assertEquals("내가 가장 가깝게 맞혔어요", consensusWinnerText(alone, setOf(1L), 1L))
    }

    @Test fun aiReviewCopyAndPendingCheck() {
        assertEquals("오늘의 주제 찰떡상", aiBadgeLabel("PERFECT_FIT"))
        assertNull(aiBadgeLabel(null))
        assertNull(aiBadgeLabel("GOLDEN_FIT"))
        val mine = Submission(1L, Profile.mockMine, true, MissionType.PHOTO, now.minusSeconds(30))
        assertTrue(awaitsAiReview(listOf(mine), now))
        assertFalse(awaitsAiReview(listOf(mine.copy(aiReview = AiReview("PERFECT_FIT", null))), now))
        assertFalse(awaitsAiReview(listOf(mine.copy(isMine = false)), now))  // 친구 사진은 기다리지 않음
        assertFalse(awaitsAiReview(listOf(mine.copy(type = MissionType.DRAW)), now))
        assertFalse(awaitsAiReview(listOf(mine.copy(createdAt = now.minusSeconds(180))), now))  // 오래전 제출
        assertFalse(awaitsAiReview(listOf(mine.copy(createdAt = now.plusSeconds(60))), now))  // 기기 시계가 늦음
        assertFalse(awaitsAiReview(emptyList(), now))
    }
}
