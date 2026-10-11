package com.sapphire.hawe.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sapphire.hawe.R

/**
 * 색상 토큰 (CLAUDE.md §3-2, iOS `Color+HAWE.swift` 와 같은 값)
 * 화면은 화이트 톤, 색은 CTA·로고에만. 선택 상태는 accent 가 아니라 ink 로 표시해요.
 */
object HColors {
    val bg = Color(0xFFFFFFFF)
    val surface = Color(0xFFF6F6F7)
    val surfaceStrong = Color(0xFFF1F1F3)
    val line = Color(0xFFECECEE)
    val border = Color(0xFFE2E2E5)
    val ink = Color(0xFF111111)
    val textSecondary = Color(0xFF6E6E73)
    val textTertiary = Color(0xFF8E8E93)

    /** 사용자 지정 #67A0C3 (2026-10-12) */
    val accent = Color(0xFF67A0C3)

    /** Figma 에 눌림 색이 없어서 accent 를 한 단계 어둡게 잡았어요. */
    val accentPressed = Color(0xFF558BB0)
    val accentSubtle = Color(0xFFEAF1F7)
    val onAccent = Color(0xFFFFFFFF)
    val danger = Color(0xFFC92A2A)
    val disabled = Color(0xFFE5E5EA)

    // Figma 컴포넌트에 변수 없이 들어간 값 — 토큰으로 묶어 둬요.
    /** Checkbox(Off) 테두리 */
    val controlBorder = Color(0xFFC7C7CC)

    /** Toggle(Off) 트랙, 로그인 화면 구분자 */
    val controlOff = Color(0xFFD1D1D6)

    /** 05 잠금 안내 글자처럼 본문보다 한 단계 연한 글자 */
    val textBody = Color(0xFF3A3A3C)

    /** 15 캘린더의 아직 오지 않은 날짜 */
    val textDisabled = Color(0xFFB0B0B5)

    /** 09 음성 파형의 아직 녹음 안 된 막대 */
    val waveformIdle = Color(0xFFD9D9DE)

    // 카카오 로그인 버튼 — 카카오 디자인 가이드 고정 색
    val kakaoContainer = Color(0xFFFEE500)
    val kakaoLabel = Color(0xFF191919)
}

/** 본문: IBM Plex Sans KR (CLAUDE.md §3-3) */
val PlexFamily = FontFamily(
    Font(R.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_kr_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_kr_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
)

/** 워드마크 "HA'WE" 전용 */
val WordmarkFamily = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))

/**
 * Figma 텍스트 크기를 그대로 넣어 쓰세요. 예: `plex(17, FontWeight.SemiBold)`
 * [lineHeight] 는 Figma 줄 높이 배수 (예: 1.35). 없으면 글꼴 기본 줄 높이.
 */
fun plex(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float? = null): TextStyle =
    TextStyle(
        fontFamily = PlexFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** 자주 쓰는 크기 (Figma 화면 기준) */
object HType {
    /** 화면 제목 26 Bold */
    val screenTitle = plex(26, FontWeight.Bold)

    /** 내비게이션 바 가운데 제목 17 Bold */
    val navTitle = plex(17, FontWeight.Bold)

    /** 섹션 제목 16 Bold */
    val sectionTitle = plex(16, FontWeight.Bold)

    /** 입력 라벨 14 SemiBold */
    val fieldLabel = plex(14, FontWeight.SemiBold)

    /** 본문 15 Regular */
    val body15 = plex(15)

    /** 보조 13 Regular */
    val caption13 = plex(13)

    /** 캡션 12 Regular */
    val caption12 = plex(12)
}

/** 모서리 (CLAUDE.md §3-4) */
object HRadius {
    val input = 14.dp
    val button = 16.dp
    val card = 20.dp
    val largeCard = 24.dp
    val checkbox = 6.dp
}

/** 여백·높이 */
object HLayout {
    /** 화면 좌우 여백 (가입·생성 화면 24, 목록·설정 화면 20) */
    val screenPadding = 24.dp
    val screenPaddingCompact = 20.dp

    /** 상단 헤더 높이 */
    val headerHeight = 52.dp

    /** 하단 고정 CTA 높이 */
    val ctaHeight = 56.dp

    /** 하단 고정 CTA 와 화면 아래 끝 사이 (Figma 프레임 pb 36) */
    val ctaBottomInset = 36.dp

    /** 헤더 없는 화면(A1·01)에서 상태 막대 아래 첫 내용까지 (Figma pt 96 − 상태 막대 54) */
    val introTopInset = 42.dp

    /** 최소 터치 영역 */
    val minTouch = 44.dp
}
