package com.sapphire.hawe.designsystem.assets

import androidx.annotation.DrawableRes
import com.sapphire.hawe.R

enum class ProfileCharacter(val value: String, val displayName: String, @DrawableRes val drawable: Int) {
    MONGSIL("mongsil", "몽실이", R.drawable.character_mongsil),
    GOMDOL("gomdol", "곰돌이", R.drawable.character_gomdol),
    KKOTNIM("kkotnim", "꽃님", R.drawable.character_kkotnim),
    IPSAGWI("ipsagwi", "잎사귀", R.drawable.character_ipsagwi),
    MULBANGUL("mulbangul", "물방울", R.drawable.character_mulbangul),
    PILLEUM("pilleum", "필름이", R.drawable.character_pilleum),
    GAMJA("gamja", "감자", R.drawable.character_gamja);
    companion object {
        val DEFAULT = MONGSIL
        fun fromKey(key: String?): ProfileCharacter = entries.firstOrNull { it.value == key } ?: DEFAULT
    }
}
