package com.sapphire.hawe.model

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.HIcon

/** 기본 프로필 캐릭터 7종 (CLAUDE.md §3-6, DB enum §6) */
enum class ProfileCharacter(val raw: String, val displayName: String, @DrawableRes val drawable: Int) {
    Mongsil("mongsil", "몽실이", R.drawable.character_mongsil),
    Gomdol("gomdol", "곰돌이", R.drawable.character_gomdol),
    Kkotnim("kkotnim", "꽃님", R.drawable.character_kkotnim),
    Ipsagwi("ipsagwi", "잎사귀", R.drawable.character_ipsagwi),
    Mulbangul("mulbangul", "물방울", R.drawable.character_mulbangul),
    Pilleum("pilleum", "필름이", R.drawable.character_pilleum),
    Gamja("gamja", "감자", R.drawable.character_gamja);

    companion object {
        val Default = Mongsil
    }
}

/** 그룹 관계 5종 (CLAUDE.md §3-7, §5-3) */
enum class Relationship(val displayName: String, val decorColor: Color, val decorPhrase: String) {
    Lover("연인", Color(0xFFE5484D), "하트가"),
    Friend("친구", Color(0xFFF0B429), "반짝이가"),
    Family("가족", Color(0xFFD98B63), "작은 집이"),
    Rival("웬수", Color(0xFFE8590C), "번개가"),
    Soso("그저그런사이", Color(0xFF8E8E93), "말줄임표가");

    /** 연인은 2명, 나머지는 최대 6명 */
    val maxMembers: Int get() = if (this == Lover) 2 else 6
}

/** 오늘의 미션 콘텐츠 유형 (CLAUDE.md §1, DB `topics.type`) */
enum class MissionType(val raw: String, val displayName: String, val icon: HIcon, val actionTitle: String) {
    Draw("draw", "그림", HIcon.Pencil, "그림 그리기"),
    Photo("photo", "사진", HIcon.Camera, "사진 찍기"),
    Write("write", "글", HIcon.Text, "글 쓰기"),
    Voice("voice", "음성", HIcon.Mic, "음성 녹음");

    /** "친구들의 ○○ 열려요" 의 주어 (그림이·사진이·글이·음성이) */
    val subjectForm: String get() = "${displayName}이"
}

/** 아바타에 들어갈 내용: 캐릭터 또는 내 사진(원형 크롭) */
sealed interface AvatarContent {
    data class Character(val character: ProfileCharacter) : AvatarContent

    /** 서버에 올라간 사진 */
    data class Photo(val url: String) : AvatarContent

    /** 방금 앨범에서 고른 사진 (아직 올리기 전) */
    data class Local(val uri: Uri) : AvatarContent

    val isPhoto: Boolean get() = this !is Character

    /** 고른 캐릭터 이름, 사진이면 '내 사진' */
    val choiceName: String get() = (this as? Character)?.character?.displayName ?: "내 사진"
}

/** 프로필 한 벌 (캐릭터 또는 사진 + 닉네임) */
data class Profile(val avatar: AvatarContent, val nickname: String) {
    companion object {
        /** 마이페이지 기본 프로필 목업 (Figma 16-1: 몽실이 · 하늘) */
        val MockMine = Profile(AvatarContent.Character(ProfileCharacter.Mongsil), "하늘")
    }
}

/** 로그인 방법 (CLAUDE.md §2) */
enum class LoginProvider(val displayName: String) {
    Email("이메일"), Kakao("카카오"), Apple("Apple"), Google("Google")
}
